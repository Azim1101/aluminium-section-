# ALU Window v1.0.0 — Nayi Generation

**Base:** v3.0.0 production source (One UI design, Drive 31 Oct 2026)
**Version:** `versionName 1.0.0` / `versionCode 1` — fresh start, nayi generation
**Tests:** 183 unit tests, sab pass (24 naye tests is version me)

> v4.0.0-test (WebView Studio wala experimental edition) is base me NAHI hai —
> wo debug-only test build tha. Ye v1.0.0 production line ka successor hai.

---

## Kya naya hai (research report ke saare recommendations)

### 1. 🎤 Voice Input — sabse bada gap fix (RESEARCH #1)

Mic icons UI me the lekin **engine ka code hi nahi tha**. Ab hai:

| File | Kya |
|---|---|
| `voice/VoiceEngine.java` (naya) | Android SpeechRecognizer backend — hi-IN / gu-IN / en-IN, partial results, workshop-friendly Hinglish error messages. Sherpa-onnx offline backend ka reflection hook (`filesDir/sherpa/` me model rakho + AAR add karo to auto-switch) |
| `AgentActivity.java` | Composer me **mic button** (attach ke bagal me) — bolo, partial text input me dikhta hai, final par auto-send. RECORD_AUDIO runtime permission flow. onPause par mic release |
| `AndroidManifest.xml` | `RECORD_AUDIO` + `<queries>` (Android 11+ recognizer detection) |

**Use:** Agent screen → mic → bolo *"aath chalis by saat ki do window"* → estimate ready.
Language phone locale se auto-pick hoti hai (Hindi phone → hi-IN).

### 2. 📦 Skill Packs — chhote model ki accuracy (RESEARCH #2+#3)

Agent Skills standard (agentskills.io) ka **progressive disclosure** pattern apnaya:

| File | Kya |
|---|---|
| `ai/skills/SkillPack.java` (naya) | Skill = tools + Hinglish trigger words |
| `ai/skills/SkillRegistry.java` (naya) | 6 skills: `windows`, `plan` (cutting+BOM), `cost`, `stock`, `customers`, `models`. Har turn message ke keywords se top-2 skills pick → sirf unke tools prompt me. `get_state` + `show_windows` hamesha rehte hain. Kuch match na ho → full list fallback (koi capability kabhi lost nahi) |
| `ai/ToolRegistry.java` | `promptSubset()` — filtered prompt list |
| `ai/HybridAgent.java` | Tier-1 (on-device) aur Perchance tier ab skill-filtered tools dete hain. Cloud tier (Gemini — bada model) ko full list milti rahegi |

**Fayda:** 16 tools → 4-6 tools per prompt. 0.8B model ka tool-pick accuracy badhega
(wrong-tool picks ka main reason chhote models me overloaded prompt hota hai).

### 3. 🧠 FunctionGemma tier — ready protocol, gated runtime (RESEARCH #2)

Google ka 270M function-calling model (~283 MB — aadha MNN se) support:

| File | Kya |
|---|---|
| `ai/FunctionGemmaProtocol.java` (naya) | `<start_function_call>call:NAME{...}` grammar parser → same `IntentProtocol.ToolCall`. Truncated output tolerate karta hai. `escalate_to_llm` = no-tool chat. System prompt builder (`<start_functions>` declarations) |
| `ai/FunctionGemmaHost.java` (naya) | LiteRT-LM runtime hook (reflection). AAR bundle nahi hai to tier silently skip |
| `ai/ModelCatalog.java` | FunctionGemma entry — **sirf tab dikhta hai jab LiteRT-LM AAR bundled ho** (warna user 283MB download karke use na kar paye) |

**Enable karne ke liye (future):** `implementation 'com.google.ai.edge.litert:litert-lm:…'`
+ `FunctionGemmaHost.generate()` me LiteRT-LM call. Protocol tests already pass.

### 4. 📋 BOM / Supplier Order List (RESEARCH #5)

Cutting-optimizer (Thailand) se inspiration — supplier ko bhejne layak material list:

| File | Kya |
|---|---|
| `calc/BomBuilder.java` (naya) | Engine ke bin-packing result se BOM: section-wise pipes (kharidne layak), cut count, waste%, glass sqft, sutter/RP hardware count. **Quantities engine se aati hain — kabhi dobara calculate nahi hoti** (cutting plan se kabhi mismatch nahi) |
| `ai/ToolRegistry.java` | Naya agent tool: `show_bom` |
| `MainActivity.java` + `sheet_share.xml` | Share sheet me **"📦 BOM order (WhatsApp)"** — supplier ko seedha WhatsApp |
| `AgentActivity.java` | Suggestion chip: "Material order (BOM) list dikhao" |

BOM me prices nahi hoti (wo estimate ka kaam hai) — isliye supplier ko forward karna safe.

### 5. ⚙️ Baaki

- `versionCode 1` / `versionName 1.0.0` — fresh generation
- 24 naye unit tests: `SkillRegistryTest` (10), `FunctionGemmaProtocolTest` (8), `BomBuilderTest` (6)
- Sab purane tests (159) bhi pass — koi regression nahi

---

## Jo JAAN-BOOJH kar nahi kiya

| Cheez | Wajah |
|---|---|
| Sherpa-onnx AAR bundle | ~50-200MB model download flow alag feature hai; VoiceEngine me hook ready hai, Android recognizer abhi default hai |
| LiteRT-LM AAR bundle | FunctionGemma protocol ready; runtime 1 gradle line + generate() hookup se on hoga |
| v4.0.0 Studio WebView | Wo experimental test edition tha; production line me nahi |
| packingsolver integration | C++ library — Android port alag kaam; abhi BOM/estimate engine wala BFD hi use hota hai |

## Device test checklist (install ke baad)

1. Agent screen → mic → permission allow → Hindi me bolo "48 by 60 ki 2 window" → row add ho
2. Mic dobara → "cutting plan dikhao" → plan screen khule
3. Agent me likho/bolo "BOM dikhao" → material list aaye
4. Main screen → share → 📦 BOM order → WhatsApp par pipe list (bina rate)
5. Purane features: estimate, Excel, customer save/load, PIN — sab waise hi chalenge

## Build

```bash
export JAVA_HOME=<jdk17> && export ANDROID_HOME=<sdk>
gradle testDebugUnitTest   # 183 tests
gradle assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```
