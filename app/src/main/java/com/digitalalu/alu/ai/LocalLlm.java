package com.digitalalu.alu.ai;

/**
 * Thin JNI bridge to the bundled Alibaba MNN-LLM runtime (libalumnn.so + libllm/libMNN).
 *
 * The library is optional: if it is not packaged for this device ABI,
 * {@link #available()} returns false and the agent skips the on-device tier and
 * falls back to rules / cloud. Every call is wrapped, because a JNI mistake is a
 * process-killing crash — we would rather lose the AI tier than the user's sheet.
 */
public final class LocalLlm {

    /** Streaming callback — invoked on the native worker thread. */
    public interface Listener {
        void onToken(String piece);
        boolean isCancelled();
        /** v2.9.1 — live text from the model's &lt;think&gt; block (thinking mode only). */
        void onThink(String piece);
    }

    private static Boolean LIB_OK = null;
    private static String loadError = "";

    private long handle = 0L;

    public static synchronized boolean available() {
        if (LIB_OK == null) {
            try {
                // Alibaba MNN runtime (prebuilt 3.6.1) + our thin JNI bridge.
                System.loadLibrary("c++_shared");
                System.loadLibrary("MNN");
                System.loadLibrary("MNN_Express");
                // libllm.so (MNN prebuilt) is linked against these four as well —
                // v2.8.1 shipped without them and dlopen failed on every phone.
                System.loadLibrary("MNN_CL");
                System.loadLibrary("MNN_Vulkan");
                System.loadLibrary("MNNOpenCV");
                System.loadLibrary("MNNAudio");
                System.loadLibrary("llm");
                System.loadLibrary("alumnn");
                LIB_OK = Boolean.TRUE;
            } catch (Throwable t) {
                LIB_OK = Boolean.FALSE;
                loadError = t.getClass().getSimpleName() + ": " + t.getMessage();
            }
        }
        return LIB_OK.booleanValue();
    }

    public static String unavailableReason() {
        available();
        return loadError;
    }

    public static String runtimeInfo() {
        if (!available()) return "runtime not packaged (" + loadError + ")";
        try {
            return nativeInfo();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    public boolean isLoaded() { return handle != 0L; }

    public String loadedPath() {
        if (!isLoaded()) return "";
        try {
            return nativePath(handle);
        } catch (Throwable t) {
            return "";
        }
    }

    /** Loads an MNN model folder (config.json). Blocking — call from a background thread. */
    public boolean load(String path, int threads, int ctx, int batch) {
        if (!available() || path == null || path.length() == 0) return false;
        close();
        long h = 0L;
        try {
            h = nativeLoad(path, threads, ctx, batch);
        } catch (Throwable t) {
            h = 0L;
        }
        handle = h;
        return handle != 0L;
    }

    /**
     * Blocking generation. The native side applies the model's own chat template,
     * so system and user are passed separately. Returns "" on cancel/error.
     */
    public String generate(String system, String user, int maxTokens,
                           float temp, float topP, Listener listener) {
        return generate(system, user, maxTokens, temp, topP, null, listener);
    }

    public String generate(String system, String user, int maxTokens,
                           float temp, float topP, String gbnf, Listener listener) {
        return generate(system, user, maxTokens, temp, topP, gbnf, 0, listener);
    }

    /**
     * Grammar-constrained generation. When {@code gbnf} is non-null the sampler
     * can only emit strings the grammar allows — that is how a 0.6B model is
     * forced to produce a real tool call instead of chatty prose.
     */
    /** @param thinkTokens 0 = answer directly; &gt;0 = think first (streamed via onThink), capped at this many tokens. */
    public String generate(String system, String user, int maxTokens,
                           float temp, float topP, String gbnf, int thinkTokens, Listener listener) {
        if (!isLoaded()) return "";
        try {
            return nativeGenerate(handle, system, user, maxTokens, temp, topP, gbnf, thinkTokens, listener);
        } catch (Throwable t) {
            return "";
        }
    }

    public void close() {
        if (handle != 0L) {
            try { nativeFree(handle); } catch (Throwable ignored) { }
            handle = 0L;
        }
    }

    /* ------------------------- native --------------------------------- */

    private static native long nativeLoad(String path, int threads, int ctx, int batch);
    private static native String nativeGenerate(long h, String system, String user,
                                                int maxTokens, float temp, float topP,
                                                String gbnf, int thinkTokens, Listener listener);
    private static native String nativePath(long h);
    private static native void nativeFree(long h);
    private static native String nativeInfo();
}
