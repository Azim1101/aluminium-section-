# UX Audit Report — ALU Window v1.0.1

**Skill:** `mobile-app-ux-auditor` (github.com/AjnasNB/mobile-app-ux-auditor-skill) — installed at `skills/mobile-app-ux-auditor/`
**Method:** static scan (`mobile_ux_static_scan.py`) → har finding code me verify → expert flow review → fixes
**Scan result:** P0=0 · P1=7 · P2=51 · P3=0 (172 files)

---

## 1. Design read (skill workflow step 1)

- **Category:** Trade/business tool — aluminium fabrication estimator
- **Audience:** Carpenters/mistri + workshop owners, Gujarat. Hinglish/Gujarati mix, dust+gloves context, low patience for typing
- **Job-to-be-done:** Window sizes → cutting plan + cost + material order, fast
- **Platform:** Android 5.0+ (Views, One UI design language since v3)
- **Risk:** Money-critical numbers; wrong cut = wasted aluminium

## 2. Findings table (verified — skill step 6)

| Sev | Screen/Flow | Evidence | Verdict | Impact | Fix |
|---|---|---|---|---|---|
| P1 | Bottom nav (5 icons) | scan `activity_main.xml:114-138` | **False positive** — `contentDescription` sab par maujood (scanner same-line regex hai, attribute next line par) | — | none needed |
| P1 | PIN dialog lock icon | scan `dlg_pin.xml:12` | **False positive** — `contentDescription="PIN"` hai | — | none needed |
| P1 | themes.xml:288 | scan | **False positive** — XML comment line | — | none needed |
| **P1** | **Windows table (H/W/RP fields)** | `item_row.xml` hints = "—"; TalkBack sirf "48" padhta — kaunsa column hai pata nahi chalta | **REAL** — core-task input fields bina label | Screen-reader user estimate hi nahi bana sakta | ✅ `RowAdapter.bind()` me contentDescription: "Row 3 Height 48" + delete button label |
| **P2** | Customer / row-edit / extra / settings dialogs (31 fields) | Labels maujood par fields se associated nahi (`labelFor` missing) | **REAL** — TalkBack field par label nahi bolta | Form bharna mushkil | ✅ 30 `labelFor` links (script se) + `dName` manual |
| **P2** | Price editor (`item_price.xml`) | Section name label rate field se linked nahi | **REAL** | Wrong section ka rate edit hone ka risk | ✅ `tvName → etKg` labelFor |
| **P2** | PIN dialog input | Prompt text field se linked nahi | **REAL** | minor | ✅ `tvPinPrompt → etPin` labelFor |
| P2 | POST_NOTIFICATIONS / RECORD_AUDIO asks | scan retention signals | **Verified OK** — dono value-timed hain (notification: model download shuru hone par; mic: mic button dabane par) — ye sahi pattern hai | — | none needed |

**Post-fix verification (multiline-aware check, scanner se zyada accurate):**
- EditText without hint/label: **51 → 0**
- ImageView without contentDescription: **0**

> Note: skill ka scanner single-line regex use karta hai — hamare multiline attributes use
> `labelFor`/next-line `contentDescription` fixes wo count nahi karta. Isliye upar wala
> multiline-aware verification script alag chalaya. Scanner khud bolta hai: "static scan
> output is a triage signal — confirm every finding before changing behavior." Humne kiya.

## 3. Flow audit notes (skill step 4-5, expert review)

| Flow | State | Note |
|---|---|---|
| First launch | ✅ | Koi onboarding nahi — trade tool ke liye theek; main table turant khulti hai, auto empty row |
| Primary task (window entry) | ✅ fixed | Table direct-edit; TalkBack labels ab hain. Touch targets 56dp+ (One UI) |
| Voice input (v1.0) | ✅ | Mic permission value-timed; partial results dikhte hain; error messages actionable ("offline pack download karo") |
| Destructive actions | ✅ | `clear_windows`/`delete` agent tools confirm-first (needsConfirm flow) — data-loss safe |
| PIN flows | ✅ fixed | Prompt ab field se linked; PIN salted-hash stored |
| Empty/error states | ✅ | Engine empty rows skip karta hai; invalid size → red cells; cloud fail → reason + fix buttons |
| Offline | ✅ | Tier 0 rules 100% offline; model download resumable + notification |
| Permission-denied | ✅ fixed | Mic denied → clear toast with recovery path |

## 4. Changed files (skill step 7 — smallest high-impact first)

| File | Change |
|---|---|
| `ui/RowAdapter.java` | bind() me H/W/RP/delete contentDescription ("Row N Height 48") |
| `layout/dlg_settings.xml` | 18 labelFor links |
| `layout/dlg_customer.xml` | 4 labelFor links |
| `layout/dlg_row.xml` | 4 labelFor links + dName SecHead link |
| `layout/dlg_extra.xml` | 4 labelFor links |
| `layout/item_price.xml` | tvName → etKg labelFor |
| `layout/dlg_pin.xml` | tvPinPrompt → etPin labelFor |
| `app/build.gradle` | versionName 1.0.1 / versionCode 2 |
| `skills/mobile-app-ux-auditor/` | **skill installed** (SKILL.md + scanner + reference) |

## 5. Verification performed / remaining risks (skill step 8)

**Performed:**
- `testDebugUnitTest` — full suite (results below in build log)
- `assembleDebug` — APK built
- Multiline-aware relabel check: 0 unlabeled fields, 0 unlabeled ImageViews

**Could NOT run here (no device/emulator):**
- TalkBack actual walkthrough (labels code-verified, spoken output not heard)
- Large-font / small-screen reflow
- Mic on real hardware (Android SpeechRecognizer needs a device)

**Remaining known issues (deliberate, documented):**
- Scanner ke 7 P1 + ~20 P2 = false positives (multiline regex limitation) — code verified clean
- `dlg_settings.xml` me 20 numeric fields par visible hints nahi (labels upar hain, labelFor se TalkBack cover ho gaya; hints add karna visual clutter karta)
