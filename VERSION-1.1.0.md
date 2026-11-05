# ALU Window — v1.1.0 (versionCode 3)

## ⚡ FunctionGemma 270M — REAL on-device tool-calling

v1.0.1 me FunctionGemma sirf protocol/parser tha — runtime bundle nahi tha,
model UI me dikhta bhi nahi tha. **v1.1.0 me wo actually chalta hai:**

| Kya | v1.0.1 | v1.1.0 |
|---|---|---|
| LiteRT-LM runtime | ❌ AAR gate ke peeche chhupa | ✅ `litertlm-android:0.18.0` bundled (`liblitertlm_jni.so` arm64, 22 MB) |
| Model download | ❌ UI me invisible | ✅ Model chooser me row — tap = download (283 MB, `soniqo/FunctionGemma-270M-LiteRT-LM`) |
| Tool-calling | ❌ sirf MNN + cloud | ✅ **Tier 1a:** FGemma → parse `<start_function_call>` → ToolRegistry → follow-up |
| Fallback | — | MNN Qwen (Tier 1) + cloud (Tier 2) as-is |

### Agent tier order (HybridAgent.askAi)
1. **Rules** (regex intent — instant)
2. **Tier 1a — FunctionGemma 270M** (agar runtime bundled + model downloaded):
   - system prompt = `FunctionGemmaProtocol.systemPrompt(SkillRegistry.promptFor(text))`
   - `Engine` (CPU backend) ek baar load, har turn fresh `Conversation`
   - maxTokens 160, parse → `applyParsedCall(..., SRC_FGEMMA, allowFollowUp=true)`
   - status chip: "⚡ FGemma Xs"
3. **Tier 1 — MNN Qwen** (FGemma na ho to; FGemma ready ho to MNN preload SKIP — ~470 MB RAM bachta hai)
4. **Tier 2 — Cloud** (Perchance)

### Model UI
`🧠 AI Model` tap → ab **chooser** khulta hai (pehle hardcoded MNN-only dialog tha):
- ⚡ FunctionGemma 270M — 283 MB — status (download baaki / downloading % / ACTIVE)
- 🧠 Qwen2.5-1.5B (MNN) — 515 MB — status
- Installed model tap = delete & re-download confirm (FGemma delete se engine close hota hai)

## Toolchain upgrade (zaroori tha)
LiteRT-LM ke class files Java 21 bytecode (major 65) hain — JDK 17 unhe padh
hi nahi sakta. Isliye:
- AGP 8.3.2 → **8.7.3** · Gradle 8.7 → **8.11.1** · JDK 17 → **JDK 21**
- minSdk 21 → **24** (LiteRT-LM ki requirement; Android 7.0+, <1% devices drop)
- `ndk { abiFilters 'arm64-v8a', 'armeabi-v7a' }` — litertlm ka 26 MB x86_64
  lib (sirf emulator) APK se nikala. **APK 73 MB → 44.7 MB.**
- armv7 devices pe FGemma auto-hide (runtime lib nahi hai → `available()`
  false → MNN fallback chalta rehta hai)

## Build verification (sab actual commands se check kiya)
- `testDebugUnitTest`: **183 tests, 0 failures** (2 catalog tests v1.1 semantics
  pe update — ab assert karte hain ki FGemma `ModelCatalog.list()` me dikhta hai)
- APK: **46,881,959 B (44.7 MB)** · 19 `.so` (18 MNN + liblitertlm_jni arm64)
- `aapt2 dump badging`: versionCode 3, versionName 1.1.0, minSdk 24, targetSdk 35
- `classes.dex` me `com/google/ai/edge/litertlm/Engine` present (260 refs)

## Notes
- FGemma model download in-app hota hai (DownloadManager, background) — APK
  me model nahi hai, isliye APK 45 MB hi rehta hai.
- FunctionGemma 270M ~1 GB RAM me chalta hai — 3 GB minRam gate catalog me hai.
- `describeResult()` FGemma ke liye "" deta hai → model ka apna `say` text use hota hai.
