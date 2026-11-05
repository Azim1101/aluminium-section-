# ALU Window v3.0.0 — Samsung One UI redesign

**Base:** v2.9.2 source (latest in Drive, 8 Oct 2026)
**Version:** `versionName 3.0.0` / `versionCode 37`
**Scope:** UI only. Calculation engine, AI agent, export, signing — sab logic bilkul untouched.

---

## 1. Kya badla (ek line me)

Poora app ab **Samsung One UI 6/7** design language follow karta hai, accent colour
**Galaxy Violet** hai, aur AI chat screen WhatsApp-style se **Samsung Messages**-style
ban gayi hai.

| Area | v2.9.2 | v3.0.0 (One UI) |
|---|---|---|
| Accent | Blue `#2563EB` | **Galaxy Violet** `#6C4CF1` (dark `#9C86FF`) |
| Page bg (light) | `#F4F6FB` | `#F4F5F7` One UI grey |
| Page bg (dark) | `#0E0F12` | **`#000000` true black** (One UI 6 AMOLED) |
| Cards | 16dp + hairline border | **26dp, borderless** (One UI guideline) |
| Buttons | 10-12dp rounded rect | **full pill, 50dp** |
| Text fields | 10dp, white + border | **12dp, sunken grey, violet focus ring** |
| Bottom nav | rounded-top floating bar, 14dp elevation | **flat bar + hairline + violet pill indicator** |
| Table header | saturated blue bar, white text | **quiet grey band, muted text** |
| Bottom sheet | 28dp | **26dp + 36x4 grab handle** |
| Dialogs | platform default square-ish | **26dp One UI dialog, violet text buttons** |
| AI chat | WhatsApp (green bubbles) | **Samsung Messages** (violet outgoing bubble, white label) |
| Icons | 4 Android framework defaults | **sab apne One UI vector icons** |
| List rows | 44-46dp | **56dp+ touch target** |

---

## 2. Design tokens (naya)

Sab kuch `res/values/colors.xml` + `res/values-night/colors.xml` ke tokens se chalta
hai. Koi screen ya custom view ab hard-coded hex use nahi karti — isliye dark mode
apne aap sahi rehta hai.

Naye tokens: `card3`, `lineSoft`, `txt2`, `hint`, `onAcc`, `onDark`, `accRipple`,
`accText`, `okLight`, `okStroke`, `warnLight`, `warnStroke`, `redLight`, `redStroke`,
`secBiz`, `secZed`, `secDomal`, `secRp`, `cSky`, `kerf`, `scrim`.

Naya `res/values/dimens.xml` — One UI metrics:
`ou_radius_card 26dp`, `ou_radius_mid 20dp`, `ou_radius_small 12dp`,
`ou_radius_pill 999dp`, `ou_page_margin 16dp`, `ou_row_min 56dp`,
`ou_btn_h 50dp`, `ou_field_h 48dp`, `ou_icon_tile 40dp`, `ou_header_icon 44dp`,
`ou_sheet_radius 26dp`, `ou_title 28sp`.

Purane `wa_*` (WhatsApp) token names **delete nahi kiye** — wo One UI surfaces par
re-point kar diye gaye hain, taaki koi purana reference na toote.

---

## 3. Naye styles

`res/values/themes.xml`:

- `AppTheme` — One UI: page-coloured status + navigation bar, violet accent,
  One UI dialog + bottom-sheet themes wired in.
- `OuTitle` (28sp bold), `OuSubtitle` (17sp), `OuBody` (15sp), `OuCaption`,
  `OuKicker`, `OuTabText`
- `OuButton`, `OuButton.Secondary`, `OuButton.Tonal` — 50dp pills
- `HeaderIcon` — 44dp borderless app-bar action with violet ripple
- `CellHeadText` — quiet table header label
- `AluDialogTheme` / `AluDialogStyle` — 26dp dialog, violet text buttons
- `AluBottomSheet` / `OuSheetShape` — 26dp top corners, 42% dim
- Purane `FieldBox`, `FieldLabel`, `FieldInput`, `SecHead`, `NavItem`, `NavIcon`,
  `NavLabel`, `SheetRow*` — **naam same, values One UI** (isliye koi layout nahi toota)

`android:windowLightStatusBar` ek `@bool/ou_light_status` se drive hota hai:
`values/` + `values-night/` + `values-night-v23/` me `false`, `values-v23/` me `true`
(light mode me dark status-bar icons, dark mode me white).

---

## 4. Naye / badle drawables

Naye: `bg_btn_primary`, `bg_btn_secondary`, `bg_btn_tonal`, `bg_btn_danger`,
`bg_ripple_round`, `bg_ripple_list`, `bg_divider`, `bg_icon_tile`, `bg_badge_ok`,
`bg_badge_warn`, `bg_badge_red`, `bg_card_small`, `bg_search`, `bg_seg_tab`,
`cell_ok`.

Badle (sab token-driven, radius One UI): `bg_card`, `bg_card2`, `bg_chip`,
`bg_chip_soft`, `bg_input` (ab **selector** — focus par violet ring), `bg_stat`,
`bg_bottom_nav` (ab **layer-list**: 1dp hairline + flat card), `bg_icon_chip`
(borderless violet ripple), `bg_nav_pill`, `bg_fab` (ripple), `bg_seg_on`,
`bg_seg_track`, `bg_sheet`, `bg_sheet_handle`, `bg_sheet_row`, `bg_date_badge`,
`bg_action_chip`, `bg_model_btn`, `cell`, `cell_head`, `cell_err`, `cell_no`,
`dot_on`, `dot_off`, `sys_z`, `sys_d`, `bg_wa_*` (chat bubbles ab 22dp).

21 icon vectors ke `#FF000000` / `#8696A0` fills ab `@color/ic` hain aur
WhatsApp-tick blue ab `@color/acc` — icons bhi theme ke saath badalte hain.

---

## 5. Java changes

| File | Change |
|---|---|
| `calc/Engine.java` | `colorOf()` → One UI / Galaxy Violet data-viz palette (chips, pipe bars **aur** share hone wale cutting-plan images sab par lagu) |
| `ui/V2.java` | `topBar()` 24sp → **28sp** One UI large title; naye helpers `pill()`, `pillStroke()`, `tile()`, `card()`, `field()` + `R_CARD/R_MID/R_SMALL/R_PILL` constants |
| `ui/SheetLayoutView.java` | series palette + stroke/ink colours → One UI |
| `ui/DownloadRingView.java` | 5 `static final` hard-coded colours → **theme tokens** (dark mode me track/text pehle galat tha) |
| `ui/PipeBarView.java` | waste / kerf / ink → tokens (`kerf` dark mode me black, light me white) |
| `ui/RpRulerView.java` | bg / line / gap / text → tokens |
| `MainActivity.java` | 3 hard-coded hex → `cMuliya` / `cRp` / `acc` tokens; card padding 14/13 → **18/16**; card title 12sp accent kicker → **14.5sp bold ink** |
| `PriceActivity.java` | "UNLOCKED" tint hard-coded green → `@color/ok` |
| `ManualCuttingActivity.java` | warning bg `#FFF7ED` → `@color/warnLight`; action buttons 10dp radius/44dp → **24dp pill / 48dp** |
| `AgentActivity.java` | 4 Android framework icons → apne vectors; composer 26dp box → **full pill, sunken, flat**; outgoing bubble → **violet + white text** (Samsung Messages); chips/actions → tonal pills; cards 16dp → 26dp; AI badge gradient violet→pink |

**Koi bhi `android:id` nahi badla** — saare 16 layouts ke IDs exactly waise hi hain,
isliye `findViewById()` / ViewBinding / Robolectric smoke tests safe hain.

---

## 6. Build

```bash
# sandbox / Linux (JDK 17 + SDK 35 + Gradle 8.7 install karta hai)
bash build-tools/sync-oneui.sh      # UI layer regenerate (idempotent)
bash build-tools/build-apk.sh debug # APK
```

Android Studio: `app-project` kholo → Gradle sync → `assembleDebug`.
JDK 17 zaroori hai (AGP 8.3.2 requirement).

CI (`.github/workflows/build-apk.yml`) waise hi chalega — sirf version badla hai.

---

## 7. Rollback-proof UI layer

Poora One UI look teen generated files se aata hai (`build-tools/alu-ui-src/`):

1. `gen_oneui.py` — tokens, theme, styles, dimens, saare shapes + icon theming
2. `gen_oneui_layouts.py` — saare 16 layouts + strings
3. `patch_oneui_java.py` — Java layer (44 targeted patches, har ek par assertion)

Teeno **idempotent** hain aur ek saath `res/` template + `app-project/` dono jagah
likhte hain. Workspace rollback ho jaye to:

```bash
python3 build-tools/alu-ui-src/gen_oneui.py
python3 build-tools/alu-ui-src/gen_oneui_layouts.py
python3 build-tools/alu-ui-src/patch_oneui_java.py
```

`build-tools/sync-oneui.sh` ye teeno ek saath chala deta hai.

> Note: purani `sync-ui.sh` / `patch_ui.py` (v1 blue look) ab **deprecated** hain.
> `sync-oneui.sh` unke baad One UI layer dobara apply kar deta hai, isliye
> galti se purani script chal jaye to bhi One UI hi jeet-ta hai.
