# v2.6 (versionCode 28) — EK model, baaki setup gayab

- App ab **sirf ek AI model** use karti hai: **Qwen3-0.6B Q4_K_M (378 MB, official/unsloth GGUF)**.
  Test (2026-10-07): 100 MB-class models (Vertex-100M, SmolLM2-135M) tool-calling me fail;
  Qwen3-0.6B Q4 sabse chhota jo Hinglish samajh kar sahi tool + numbers deta hai (5.5/7).
- **AI Setup screen, Cloud key, Perchance Web Brain / Web Chat — sab hata diye.**
  (AiSetupActivity, WebChatActivity delete; CloudLlm/PerchanceBrain code unused.)
- Agent screen me upar ek **model banner**: "⬇ DOWNLOAD (378 MB)" → progress % → "load ho raha hai" → gayab.
  Download DownloadManager se (background, notification), poora hote hi auto-load.
- Settings force: mode = AI-first → **har message seedha model ko**; rules sirf tab jab model loaded nahi.
  Model app ke saare tools chala sakta hai (window add/edit/delete, estimate, cutting, price, customers, screens).
- Prompt me Hinglish hints (teesri=3, "4 by 5", piece=qty, hata do=delete).
- ⋮ menu → "🧠 AI Model status" (loaded?, load time, delete & re-download).
- Unit tests 156/156 pass.
