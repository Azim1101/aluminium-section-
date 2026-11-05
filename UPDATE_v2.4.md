# ALU Window v2.4 — Agent ↔ Perchance connectivity

## Asli problem kya thi
1. **Web panel layout me add hi nahi tha** (`buildWebPanel()` kabhi call nahi hua) → `startWebBrain()` turant return kar deta tha → Perchance page kabhi khul hi nahi sakti thi ("page band").
2. **Perchance page (d4yeutq0b1) par bridge code paste nahi hai** — live page abhi bhi default template "AI Text + Image + Message Box" hai. App "ready" event ka intezaar karta rehta.

## Fix
- **Auto-bridge (v3)**: app ab generator ka document (`<hash>.perchance.org/<name>`) khud fetch karke usme bridge `<script>` inject karta hai (`shouldInterceptRequest`). Page par **kuch paste karne ki zaroorat nahi** — bas generator me `ai-text-plugin` import hona chahiye (default template me pehle se hai: `generateText = {import:ai-text-plugin}`).
- Bridge khud plugin dhoondta hai: `ai`, `generateText`, `aiText`… ya `root.*` me koi ai-text function.
- Web panel ab layout me hai; **web brain ON hone par page agent screen khulte hi hidden load hoti hai**.
- **Web brain default ON** (free, koi key/download nahi). Offline-only mode me skip.
- Perchance fail ho to ab **rules engine ka jawab** + note "🌐 Perchance: <wajah>" + one-tap "PERCHANCE PAGE" button (pehle sirf error aata tha).
- Readiness: web brain ON = setup complete (ab "setup baaki" nahi dikhega); setup hint me "🌐 PERCHANCE ON (FREE)" button.
- Status me "bridge inject hua — plugin ka intezaar" / "READY ✓ … auto-bridge" dikhta hai.
- Unit tests: `PerchanceBridgeTest` (URL→name, document match, inject-once).

## Agar phir bhi READY na aaye
🌐 panel kholo (header ka globe): agar Cloudflare "Verifying…" ya login dikhe to ek baar pass karo; phir se ping aata hai. Bridge-inject fail hone par page normal load hoti hai aur purana pasted-code tarika bhi kaam karta hai.

---

# v2.5 (versionCode 27) — "Web Chat": Perchance page = app ka chatbot

- **Nayi screen: Web Chat** (Agent header me 🌐, ya menu → "💬 Web Chat"). Aapka Perchance page
  full-screen khulta hai, upar app ki apni chat UI overlay hoti hai (dark/light auto).
- **App control bridge (`window.ALU`)**: page ka ai-text-plugin ab app ke saare tools chala sakta hai —
  window add/edit/delete, estimate, cutting, price, customers, screen open karna (ALU.openScreen), etc.
  Destructive kaam par pehle confirm poochta hai.
- **Paste zaroori nahi**: bridge v4 app khud generator iframe me inject karta hai. `perchance-page-code-v4.html`
  optional hai (prompt customise karne / browser me chat dekhne ke liye).
- Chat overlay me 🌐 button → asli Perchance page dikhao (ad / "Verifying" pass karne ke liye), 💬 se wapas.
- Fix: confirmed destructive tool dobara "confirm" nahi maangta (executeConfirmed).
- Unit tests: 108/108 pass.
