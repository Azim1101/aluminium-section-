# v2.8 (versionCode 30) — Alibaba MNN runtime, GGUF/llama.cpp hataya

- **AI runtime badla: llama.cpp (GGUF) → Alibaba MNN-LLM 3.6.1** (prebuilt libMNN/libllm + hamara JNI bridge
  `mnn-jni/alu_mnn_jni.cpp`, build script `mnn-jni/build.sh`). libaluai.so hata di.
- **Model: taobao-mnn/Qwen3.5-0.8B-Claude-4.6-Opus-Reasoning-Distilled-MNN** (4-bit) — bilkul wahi jo MNN Chat app
  me chalta hai. 8 files (~520 MB) `models/mnn_qwen35_08b_claude/` me DownloadManager se aati hain.
- Thinking OFF (`jinja.context.enable_thinking=false`) — seedha jawab, warna har message par lamba <think>.
- Chat UI fix: dark mode me bubble ka text invisible tha (values-night wa_text_dark) — ab hamesha dikhta hai.
- "Thinking..." ab live: seconds + tokens, 30s baad tap = cancel, 120s par rules fallback.
- Purani GGUF files / folders auto-delete (jagah free).
- Tests 156/156.

## MNN model ko asli engine par test karke ye hardening add ki (Linux x64 par MNN 3.6.1 source build + wahi 520 MB model)
- Qwen3.5 ka chat template `enable_thinking` flag ko IGNORE karta hai — hamesha `<think>` kholta hai (pehle test me 2550 token sochta raha!).
  Fix: JNI prompt ke end me khud `</think>` band karke bhejta hai → model seedha JSON deta hai (prefill ~1050 token, answer 10–50 token).
- Answer `{"tool":` se prefill hota hai (tool pehle, say aakhir me) — test me tool-choice 2/6 se 10/12 ho gaya.
- Greedy decoding (deterministic), brace-balance par turant stop, aur tool mil jaye to "say" ki rambling skip (app khud tool-result se bolta hai).
- Near-miss tool names auto-map (`estimate`→show_estimate, `cutting_plan`→show_cutting_plan, `list_customer`→list_customers).
- Truncated JSON repair; user ke text ke number/naam (4 by 5, 2 window, rate 310, mobile) model ke args ko override karte hain.
- Cancel ab sach me engine rokta hai (LlmStatus::USER_CANCEL) — pehle sirf flag set hota tha.
- Tests 159/159.

# v2.8.1 (versionCode 31)
- **Download fix:** system DownloadManager hata diya (bahut phones par band hota hai → "Download shuru nahi hua").
  Ab app ka apna foreground downloader: 8 files, `.part` resume (net kat jaye to wahin se), notification me progress,
  app kill ho jaye to dobara kholte hi auto-resume. 4G par bhi chalta hai.
- **Naya chat UI (ChatGPT/Gemini style):** WhatsApp look poora hata diya — flat header "ALU AI", Namaste hero + suggestion
  cards, assistant ke jawab full-width (bubble nahi), user ke message right side pill, floating composer (+ / send),
  "＋ New" se nayi chat. Light + dark dono me app ke apne theme colors.

# v2.8.2 (versionCode 32)
- **"AI runtime nahi hai" fix:** MNN ki prebuilt `libllm.so` ko `libMNN_CL / libMNN_Vulkan / libMNNOpenCV / libMNNAudio` bhi chahiye
  (readelf NEEDED) — ye 4 libs APK me nahi thi, isliye dlopen fail hota tha. Ab dono ABI me bundled + load order sahi.
  Banner ab asli load error dikhata hai (sirf "runtime nahi" nahi).

# v2.8.3 (versionCode 33) — speed (MNN Chat jitna)
- User ke phone par MNN Chat: prefill 21 tok/s, decode 11.5 tok/s. Hamara prompt ~1050 token tha → sirf padhne me ~50 s.
- Ab: rules + tool list (static) **ek baar KV cache me prefill** (load ke waqt warm-up, `reuse_kv` + `eraseHistory`),
  har turn sirf `STATE + USER` (~60-80 token) → prefill ~4 s + jawab 10-50 token ~3-5 s. Pehli baar load ~1 min, phir 5-10 s/jawab.

## v2.9.0 (versionCode 34) — "agent ko sahi use karo"
- ⚡ FAST PATH: jab rules parser sure ho (saaf size "48x60 ki 2 window", "hata do",
  "estimate dikhao", "rate 320 karo", "cutting kholo"…) to agent 0 s me kaam karta hai —
  model round-trip nahi. Model sirf un baaton ke liye jo rules nahi padh paate
  (free-form sawal, tooti Hinglish, explain). Chat me "⚡ turant" note dikhta hai.
- On-device follow-up round (tool ke baad dusra model call) band — yeh alag system
  prompt se cached prefix ko tod deta tha (har tool turn par 25–50 s extra prefill).
- Model max tokens 160 → 120, timeout 120 s → 60 s.
- Model ab app kholte hi (MainActivity) background me load hota hai — Agent screen
  kholte hi ready.
- "MODEL DOWNLOAD KARO" double-tap guard (do baar message nahi aata).
- Settings: fast_path (default on), device_follow_up (default off).

## v2.9.1 (versionCode 35) — live streaming + 💭 thinking toggle
- Jawab ab LIVE aata hai (shabd-dar-shabd), ek saath nahi. Tool chalte waqt "⚙ add window chala raha hoon…" dikhta hai.
- Header me 💭/⚡ button: Thinking ON → model pehle sochta hai, soch live grey card me dikhti hai,
  jawab ke saath "💭 Soch" collapsed rehti hai (tap = expand). OFF → seedha jawab (tez).
- JNI: nativeGenerate(thinkTokens) — phase 1 think (budget 120 tokens, </think> par ruk), phase 2
  forced JSON ({"tool": prefill). Cached prefix dono me reuse.
- Prompt: 5 naye udaharan (delete_last, cutting, stock pipe, customer, clear) + "SAMAJHNE KA TARIKA"
  (maksad → tool → args; adhoora ho to sawal poochho; soch 2-3 line).
- Settings: thinking (default ON), think_tokens 120.

## v2.9.2 (versionCode 36) — model load tez (MNN source study)
- BUG: `tmp_path` folder kabhi banaya hi nahi tha → MNN ka `use_mmap`/`use_cached_mmap` weight cache
  silently fail → har load par 450 MB weight poora padh/convert hota tha. Ab JNI `mkdir(tmp)` karta hai:
  pehli load weight cache likhti hai, agli loads mmap se (seconds me).
- NAYA: MNN 3.6.1 ka on-disk PREFIX KV CACHE (`setPrefixCacheFile` + `completePrefixWrite`,
  `kvcache_mmap:true`, `prefix_cache_path`). System prompt (~1000 tokens, 40-60 s prefill) ab sirf
  PEHLI baar compute hota hai; KV `prefixcache/alu_<hash>_<n>_<layer>.k/.v` me save → agli app start par
  disk se load (~1-2 s). Cache ka naam prompt-text hash + token count = prompt badla to auto-rebuild;
  purani cache files auto-delete.
- Loading banner text update.
