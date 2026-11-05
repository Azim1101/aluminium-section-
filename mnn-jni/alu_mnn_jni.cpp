// ALU Window — JNI bridge to Alibaba MNN-LLM (libllm.so / libMNN.so, MNN 3.6.1).
// Java side: com.digitalalu.alu.ai.LocalLlm (same native names as the old
// llama.cpp bridge, so the rest of the app did not change).
//
// Build: see mnn-jni/build.sh (NDK clang++, links prebuilt libllm.so + libMNN.so).

#include <jni.h>
#include <android/log.h>
#include <string>
#include <cstring>
#include <vector>
#include <sstream>
#include <streambuf>
#include <memory>
#include <mutex>
#include <sys/stat.h>
#include <dirent.h>
#include <unistd.h>
#include "llm/llm.hpp"

#define TAG "alu-mnn"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using MNN::Transformer::Llm;
using MNN::Transformer::ChatMessages;

struct AluLlm {
    Llm* llm = nullptr;
    std::string configPath;
    int threads = 4;
    std::mutex mu;
    // Static-prefix KV cache: the system prompt (rules + tool list) is prefilled
    // once; every turn we roll the cache back to prefixLen and only feed the user part.
    std::string prefixText;      // rendered template text up to "<|im_start|>user\n"
    size_t prefixLen = 0;        // KV entries belonging to the prefix (0 = none)
    bool prefixValid = false;
    std::string prefixDir;       // on-disk prefix KV cache (survives app restarts)
};

static std::string fnv1a(const std::string& s) {
    unsigned long long h = 1469598103934665603ULL;
    for (unsigned char c : s) { h ^= c; h *= 1099511628211ULL; }
    char buf[32]; snprintf(buf, sizeof(buf), "%016llx", h);
    return std::string(buf);
}

/** Remove stale prefix caches (older prompt versions) so the dir never grows. */
static void cleanPrefixDir(const std::string& dir, const std::string& keep) {
    DIR* d = opendir(dir.c_str());
    if (!d) return;
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        std::string n = e->d_name;
        if (n == "." || n == "..") continue;
        if (n.compare(0, keep.size(), keep) == 0 && n.size() > keep.size() && n[keep.size()] == '_') continue;
        unlink((dir + "/" + n).c_str());
    }
    closedir(d);
}

static JavaVM* gVm = nullptr;

static std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return "";
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

static std::string jsonEscape(const std::string& s) {
    std::string o;
    o.reserve(s.size() + 8);
    for (char c : s) {
        switch (c) {
            case '"': o += "\\\""; break;
            case '\\': o += "\\\\"; break;
            case '\n': o += "\\n"; break;
            case '\r': break;
            case '\t': o += "\\t"; break;
            default: o += c;
        }
    }
    return o;
}

/** std::streambuf that forwards every chunk to a Java Listener.onToken(String). */
class JniStreamBuf : public std::streambuf {
public:
    JniStreamBuf(JNIEnv* env, jobject listener, jmethodID onToken, jmethodID isCancelled,
                 std::string* sink, volatile bool* cancel, MNN::Transformer::Llm* llm)
        : env_(env), listener_(listener), onToken_(onToken), isCancelled_(isCancelled),
          sink_(sink), cancel_(cancel), llm_(llm) {}

    /** Feed the prefilled answer prefix through the same path (sink + Java + brace tracking). */
    void prime(const char* prefix) { xsputn(prefix, (std::streamsize) strlen(prefix)); }
    bool jsonClosed() const { return closed_; }

protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        std::string piece(s, (size_t) n);
        // Stop as soon as the answer object is balanced — a small model would
        // otherwise keep rambling after the closing brace until max_new_tokens.
        bool addBrace = false;
        for (size_t pi = 0; pi < piece.size(); pi++) {
            char ch = piece[pi];
            if (inStr_) {
                if (esc_) esc_ = false;
                else if (ch == '\\') esc_ = true;
                else if (ch == '"') inStr_ = false;
                continue;
            }
            if (ch == '"') inStr_ = true;
            else if (ch == '{') depth_++;
            else if (ch == '}') {
                if (--depth_ <= 0) { closed_ = true; *cancel_ = true; }
                else if (depth_ == 1 && !argsDone_) {
                    // "args" object just closed. If a real tool was named, the app
                    // phrases the answer from the tool result — skip the "say" ramble.
                    argsDone_ = true;
                    std::string sofar = *sink_ + piece.substr(0, pi + 1);
                    if (sofar.find("\"tool\":null") == std::string::npos
                        && sofar.find("\"tool\": null") == std::string::npos) {
                        piece = piece.substr(0, pi + 1);   // drop what followed in this token
                        addBrace = true;
                        closed_ = true; *cancel_ = true;
                        break;
                    }
                }
            }
        }
        if (addBrace) piece += "}";
        sink_->append(piece);
        if (*cancel_) stopLlm();
        // Buffer partial UTF-8 so Java never sees a broken multi-byte char.
        pending_ += piece;
        size_t cut = utf8SafeLen(pending_);
        if (cut > 0 && listener_ && onToken_) {
            jstring js = env_->NewStringUTF(pending_.substr(0, cut).c_str());
            if (js) {
                env_->CallVoidMethod(listener_, onToken_, js);
                env_->DeleteLocalRef(js);
                if (env_->ExceptionCheck()) env_->ExceptionClear();
            }
            pending_.erase(0, cut);
        }
        if (listener_ && isCancelled_) {
            jboolean c = env_->CallBooleanMethod(listener_, isCancelled_);
            if (env_->ExceptionCheck()) env_->ExceptionClear();
            if (c) { *cancel_ = true; stopLlm(); }
        }
        return n;
    }
    int overflow(int ch) override {
        if (ch != EOF) {
            char c = (char) ch;
            xsputn(&c, 1);
        }
        return ch;
    }

private:
    // MNN's decode loop breaks on USER_CANCEL (nobody inside the library sets it;
    // the context is exposed read-only). reset()/generate_init put it back to RUNNING.
    void stopLlm() {
        if (!llm_) return;
        auto* ctx = const_cast<MNN::Transformer::LlmContext*>(llm_->getContext());
        if (ctx && ctx->status == MNN::Transformer::LlmStatus::RUNNING)
            ctx->status = MNN::Transformer::LlmStatus::USER_CANCEL;
    }
    static size_t utf8SafeLen(const std::string& s) {
        // Return the longest prefix that ends on a complete UTF-8 sequence.
        size_t n = s.size();
        if (n == 0) return 0;
        size_t i = n;
        int back = 0;
        while (i > 0 && back < 4) {
            unsigned char c = (unsigned char) s[i - 1];
            if ((c & 0xC0) != 0x80) {              // lead byte or ASCII
                int need = 1;
                if ((c & 0xE0) == 0xC0) need = 2;
                else if ((c & 0xF0) == 0xE0) need = 3;
                else if ((c & 0xF8) == 0xF0) need = 4;
                if (n - (i - 1) >= (size_t) need) return n;
                return i - 1;
            }
            i--; back++;
        }
        return n;
    }

    int depth_ = 0; bool inStr_ = false, esc_ = false, closed_ = false, argsDone_ = false;
    JNIEnv* env_;
    jobject listener_;
    jmethodID onToken_;
    jmethodID isCancelled_;
    std::string* sink_;
    volatile bool* cancel_;
    MNN::Transformer::Llm* llm_;
    std::string pending_;
};

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    gVm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_digitalalu_alu_ai_LocalLlm_nativeInfo(JNIEnv* env, jclass) {
    return env->NewStringUTF("MNN-LLM 3.6.1 (Alibaba MNN, CPU)");
}

/**
 * @param path    .../<model dir>/config.json
 * @param threads CPU threads
 * @param ctx     unused by MNN (kept for API compatibility)
 * @param batch   unused
 */
JNIEXPORT jlong JNICALL
Java_com_digitalalu_alu_ai_LocalLlm_nativeLoad(JNIEnv* env, jclass, jstring path, jint threads,
                                               jint ctx, jint batch) {
    std::string cfg = jstr(env, path);
    LOGI("load %s threads=%d", cfg.c_str(), (int) threads);
    std::unique_ptr<AluLlm> h(new AluLlm());
    h->configPath = cfg;
    h->threads = threads > 0 ? threads : 4;
    try {
        h->llm = Llm::createLLM(cfg);
        if (!h->llm) {
            LOGE("createLLM failed");
            return 0;
        }
        std::string dir = cfg.substr(0, cfg.find_last_of('/') + 1);
        std::string tmp = dir + "tmp";
        std::string pcache = dir + "prefixcache";
        // v2.9.2: MNN never creates these — without them use_mmap's weight cache
        // and the on-disk prefix KV cache silently fall back to the slow path.
        mkdir(tmp.c_str(), 0700);
        mkdir(pcache.c_str(), 0700);
        h->prefixDir = pcache;
        // Same knobs MNN Chat uses for small models on phones.
        std::string sc = std::string("{")
            + "\"backend_type\":\"cpu\","
            + "\"thread_num\":" + std::to_string(h->threads) + ","
            + "\"precision\":\"low\","
            + "\"memory\":\"low\","
            + "\"use_mmap\":true,"
            + "\"use_cached_mmap\":true,"
            + "\"kvcache_mmap\":true,"
            + "\"tmp_path\":\"" + jsonEscape(tmp) + "\","
            + "\"prefix_cache_path\":\"" + jsonEscape(pcache) + "\","
            + "\"sampler_type\":\"mixed\","
            + "\"mixed_samplers\":[\"penalty\",\"topK\",\"topP\",\"temperature\"],"
            + "\"penalty\":1.05,\"temperature\":0.3,\"topP\":0.9,\"topK\":20,"
            + "\"max_new_tokens\":256,"
            + "\"reuse_kv\":true,"
            + "\"jinja\":{\"context\":{\"enable_thinking\":false}}"
            + "}";
        h->llm->set_config(sc);
        if (!h->llm->load()) {
            LOGE("llm->load() failed");
            Llm::destroy(h->llm);
            h->llm = nullptr;
            return 0;
        }
    } catch (const std::exception& e) {
        LOGE("load exception: %s", e.what());
        return 0;
    } catch (...) {
        LOGE("load exception (unknown)");
        return 0;
    }
    LOGI("loaded OK");
    return (jlong) (intptr_t) h.release();
}

JNIEXPORT jstring JNICALL
Java_com_digitalalu_alu_ai_LocalLlm_nativePath(JNIEnv* env, jclass, jlong hh) {
    AluLlm* h = (AluLlm*) (intptr_t) hh;
    return env->NewStringUTF(h ? h->configPath.c_str() : "");
}

/** v2.9.1 — streams the model's <think> block to Listener.onThink and stops at </think> or budget. */
class ThinkBuf : public std::streambuf {
public:
    ThinkBuf(JNIEnv* env, jobject listener, jmethodID onThink, jmethodID isCancelled,
             int budget, MNN::Transformer::Llm* llm)
        : env_(env), listener_(listener), onThink_(onThink), isCancelled_(isCancelled),
          budget_(budget), llm_(llm) {}
    std::string text;
    bool closed = false;
    bool userCancel = false;
protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        std::string piece(s, (size_t) n);
        text += piece;
        count_++;
        pending_ += piece;
        size_t cut = utf8SafeLen(pending_);
        if (cut > 0 && listener_ && onThink_) {
            jstring js = env_->NewStringUTF(pending_.substr(0, cut).c_str());
            if (js) {
                env_->CallVoidMethod(listener_, onThink_, js);
                env_->DeleteLocalRef(js);
                if (env_->ExceptionCheck()) env_->ExceptionClear();
            }
            pending_.erase(0, cut);
        }
        bool stop = false;
        if (text.find("</think>") != std::string::npos) { closed = true; stop = true; }
        if (count_ >= budget_) stop = true;
        if (listener_ && isCancelled_) {
            jboolean c = env_->CallBooleanMethod(listener_, isCancelled_);
            if (env_->ExceptionCheck()) env_->ExceptionClear();
            if (c) { userCancel = true; stop = true; }
        }
        if (stop) {
            auto* ctx = const_cast<MNN::Transformer::LlmContext*>(llm_->getContext());
            if (ctx) ctx->status = MNN::Transformer::LlmStatus::USER_CANCEL;
        }
        return n;
    }
    int overflow(int ch) override {
        if (ch != EOF) { char c = (char) ch; xsputn(&c, 1); }
        return ch;
    }
private:
    static size_t utf8SafeLen(const std::string& s) {
        size_t n = s.size();
        if (n == 0) return 0;
        size_t i = n;
        int back = 0;
        while (i > 0 && back < 4) {
            unsigned char c = (unsigned char) s[i - 1];
            if ((c & 0xC0) != 0x80) {
                int need = (c & 0x80) == 0 ? 1 : (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : 4;
                return (n - (i - 1)) >= (size_t) need ? n : i - 1;
            }
            i--; back++;
        }
        return n;
    }
    JNIEnv* env_; jobject listener_; jmethodID onThink_; jmethodID isCancelled_;
    int budget_; MNN::Transformer::Llm* llm_;
    int count_ = 0;
    std::string pending_;
};

JNIEXPORT jstring JNICALL
Java_com_digitalalu_alu_ai_LocalLlm_nativeGenerate(JNIEnv* env, jclass, jlong hh, jstring system,
                                                   jstring user, jint maxTokens, jfloat temp,
                                                   jfloat topP, jstring gbnf, jint thinkTokens, jobject listener) {
    AluLlm* h = (AluLlm*) (intptr_t) hh;
    if (!h || !h->llm) return env->NewStringUTF("");
    std::lock_guard<std::mutex> lock(h->mu);

    jmethodID onToken = nullptr, isCancelled = nullptr, onThink = nullptr;
    if (listener) {
        jclass lc = env->GetObjectClass(listener);
        onToken = env->GetMethodID(lc, "onToken", "(Ljava/lang/String;)V");
        isCancelled = env->GetMethodID(lc, "isCancelled", "()Z");
        if (env->ExceptionCheck()) { env->ExceptionClear(); onToken = nullptr; isCancelled = nullptr; }
        onThink = env->GetMethodID(lc, "onThink", "(Ljava/lang/String;)V");
        if (env->ExceptionCheck()) { env->ExceptionClear(); onThink = nullptr; }
    }

    std::string out;
    volatile bool cancel = false;
    JniStreamBuf buf(env, listener, onToken, isCancelled, &out, &cancel, h->llm);
    std::ostream os(&buf);

    std::string sys = jstr(env, system);
    std::string usr = jstr(env, user);

    try {
        // Render the full template once with a marker to find the split point:
        //   [prefix = system ... "<|im_start|>user\n"] [user text] [suffix "<|im_end|>\n<|im_start|>assistant\n<think>\n"]
        static const char* kMark = "@@ALU_USER_MARK@@";
        ChatMessages msgs;
        if (!sys.empty()) msgs.emplace_back("system", sys);
        msgs.emplace_back("user", std::string(kMark));
        std::string full = h->llm->apply_chat_template(msgs);
        size_t mp = full.find(kMark);
        std::string prefix = mp == std::string::npos ? "" : full.substr(0, mp);
        std::string suffix = mp == std::string::npos ? full : full.substr(mp + strlen(kMark));
        const bool think = thinkTokens > 0;
        bool opensThink = suffix.size() >= 8 && suffix.compare(suffix.size() - 8, 8, "<think>\n") == 0;
        if (!think && opensThink) {
            // Qwen3.5 template always opens <think>; close it so the model answers directly.
            suffix += "\n</think>\n\n";
        }
        if (think && !opensThink) suffix += "<think>\n";
        static const char* kPrefix = "{\"tool\":";
        if (!think) suffix += kPrefix;

        // (1) make sure the prefix sits in the KV cache
        if (!prefix.empty() && (!h->prefixValid || h->prefixText != prefix)) {
            h->llm->reset();                                   // drop everything
            std::vector<int> pids = h->llm->tokenizer_encode(prefix);
            // v2.9.2: MNN prefix cache on disk. Name = hash(prompt text)+token count,
            // so a new prompt version never reads an old cache.
            std::string name = "alu_" + fnv1a(prefix) + "_" + std::to_string(pids.size());
            bool onDisk = false;
            try {
                cleanPrefixDir(h->prefixDir, name);
                onDisk = h->llm->setPrefixCacheFile(name, 0);
            } catch (...) { onDisk = false; }
            LOGI("prefill static prefix: %d tokens (disk cache %s)", (int) pids.size(), onDisk ? "HIT" : "miss -> writing");
            h->llm->generate(pids, 0);                         // HIT: instant; miss: prefill + write KV to disk
            // (generate() itself calls completePrefixWrite() once the write finished)
            h->prefixLen = pids.size();
            h->prefixText = prefix;
            h->prefixValid = h->prefixLen > 0;
            LOGI("prefix cached, kv=%d", (int) h->prefixLen);
        } else if (h->prefixValid) {
            size_t cur = h->llm->getCurrentHistory();
            if (cur > h->prefixLen) h->llm->eraseHistory(h->prefixLen, 0);   // roll back to prefix
        }

        std::string turn = h->prefixValid ? (usr + suffix) : (prefix + usr + suffix);
        std::vector<int> ids = h->llm->tokenizer_encode(turn);
        LOGI("turn prefill: %d tokens (prefix cached=%d, think=%d)", (int) ids.size(), (int) h->prefixValid, (int) thinkTokens);
        if (think) {
            // Phase 1: let the model think (streamed live), capped at thinkTokens.
            std::string sc1 = std::string("{\"sampler_type\":\"greedy\",\"max_new_tokens\":")
                + std::to_string((int) thinkTokens) + "}";
            h->llm->set_config(sc1);
            ThinkBuf tb(env, listener, onThink, isCancelled, (int) thinkTokens, h->llm);
            std::ostream tos(&tb);
            h->llm->response(ids, &tos, "", (int) thinkTokens);
            tos.flush();
            {
                auto* ctx = const_cast<MNN::Transformer::LlmContext*>(h->llm->getContext());
                if (ctx && ctx->status == MNN::Transformer::LlmStatus::USER_CANCEL)
                    ctx->status = MNN::Transformer::LlmStatus::RUNNING;
            }
            if (tb.userCancel) { h->prefixValid = false; return env->NewStringUTF(""); }
            // Phase 2: close the think block (if the model did not) and force the JSON answer.
            std::string tail = (tb.closed ? "" : "\n</think>") + std::string("\n\n") + kPrefix;
            ids = h->llm->tokenizer_encode(tail);
        }
        std::string sc = std::string("{\"sampler_type\":\"greedy\",\"max_new_tokens\":")
            + std::to_string(maxTokens > 0 ? maxTokens : 256) + "}";
        h->llm->set_config(sc);
        buf.prime(kPrefix);
        h->llm->response(ids, &os, "", maxTokens > 0 ? maxTokens : 256);
    } catch (const std::exception& e) {
        LOGE("generate exception: %s", e.what());
        h->prefixValid = false;
    } catch (...) {
        LOGE("generate exception (unknown)");
        h->prefixValid = false;
    }
    os.flush();
    {   // leave the engine usable after a cancel / early stop
        auto* ctx = const_cast<MNN::Transformer::LlmContext*>(h->llm->getContext());
        if (ctx && ctx->status == MNN::Transformer::LlmStatus::USER_CANCEL)
            ctx->status = MNN::Transformer::LlmStatus::RUNNING;
    }
    // Strip an (empty or not) think block if the template still emitted one.
    size_t te = out.find("</think>");
    if (te != std::string::npos) out.erase(0, te + 8);
    while (!out.empty() && (out[0] == '\n' || out[0] == ' ')) out.erase(0, 1);
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_com_digitalalu_alu_ai_LocalLlm_nativeFree(JNIEnv*, jclass, jlong hh) {
    AluLlm* h = (AluLlm*) (intptr_t) hh;
    if (!h) return;
    try {
        if (h->llm) Llm::destroy(h->llm);
    } catch (...) { }
    h->llm = nullptr;
    delete h;
}

} // extern "C"
