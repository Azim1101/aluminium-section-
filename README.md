# ALU Window Optimizer (Native v2)

Native Android application for aluminium window/door fabricators. Calculates cut lengths,
optimizes pipe usage (bin-packing), generates cutting plans, estimates costs,
and exports estimates via WhatsApp or Excel.

---

## Features

- **Two built-in pipe systems**: ZED (Frame, Sutter, Muliya, RP Grill) and DOMAL (Frame, Sutter, RT, RP Grill)
- **Custom systems with formulas** — define your own system using formulas like `H - 2.0`, `W / q`, `sutterH - 4.0` (built-in math evaluator)
- **Automatic cut calculations** with configurable deductions per system
- **Bin-packing optimizer** — minimizes waste via first-fit packing into stock pipes (default 196")
- **Manual Cutting workspace** — enter any stock-pipe size, kerf and cut-size quantities for standalone PCO / pipe-cut optimization
- **Manual Sheet Cutting** — enter stock sheet width × height and piece sizes one-by-one; MaxRects nesting minimizes the number of sheets and scrap
- **Face Cutting / grain lock** — choose whether the first entered size follows sheet Height or Width; pattern-facing pieces never rotate
- **RP Grill marking** — auto-suggests grill quantity and spacing with a visual ruler
- **Costing engine** — weight-based pricing (kg per 16 ft × aluminium rate), glass cost, extra charges (per sutter / per window / per inch / per RP)
- **Customer CRM** — save/load customer estimates with search, PIN locked
- **Price manager** — viewing is open for everyone, **editing requires PIN**
- **Excel export** — real `.xlsx` (opens in Excel / Google Sheets / WPS), shared via FileProvider
- **WhatsApp sharing** for quick estimates
- **Unit toggle** — inches ↔ millimeters
- **Configurable business header/footer** for estimates (set in Settings — name, address, mobile)

---

## PIN

| Item | Detail |
|------|--------|
| **Default PIN** | **`1101`** |
| Used for | Editing prices & rates, opening saved customer estimates |
| Change it | **Price screen → "Change PIN"** (minimum 4 digits) |
| Forgot PIN | Clear app data — it resets to the default `1101` |

> Note: Price viewing is open; the PIN only protects *editing* and saved customer records.

---

## Tech Stack

- **Language**: Java 17
- **Min SDK**: 21 (Android 5.0) · **Target/Compile SDK**: 34 (Android 14)
- **Version**: 1.6 (versionCode 6)
- **UI**: XML layouts + programmatic views, Material components
- **Persistence**: SharedPreferences (JSON-serialized)
- **Dependencies**: AndroidX AppCompat 1.6.1, Material 1.11.0, RecyclerView 1.3.2, ConstraintLayout 2.1.4, Core 1.12.0

---

## Project Structure

```
app/src/main/java/com/digitalalu/alu/
├── MainActivity.java        # Main estimate entry, cutting plan, share/export
├── ManualCuttingActivity.java # Standalone PCO + sheet / face-cutting workspace
├── PriceActivity.java       # Price book viewer/editor (PIN locked editing)
├── CustomerActivity.java    # Saved customer estimates (PIN locked)
├── calc/
│   ├── Engine.java          # Cut-length engine + bin-packing optimizer
│   ├── ManualCuttingEngine.java # Manual pipe BFD + multi-pass MaxRects sheet nesting
│   ├── Costing.java         # Cost calculation
│   ├── PriceBook.java       # Rates, extras, PIN (DEFAULT_PIN = "1101")
│   ├── Settings.java        # Deductions, units, stock, business info
│   ├── CustomFormulaManager.java  # Custom system formulas
│   └── MathEvaluator.java   # Formula expression evaluator
├── export/
│   ├── Exporter.java        # WhatsApp text + Excel export
│   └── XlsxWriter.java      # Minimal real .xlsx (OOXML) writer
├── model/
│   ├── Customer.java        # Customer record (name, mobile, village, note)
│   └── WindowItem.java      # Window row (sizes, system, qty)
└── ui/
    ├── PipeBarView.java     # Visual pipe cutting bars
    ├── SheetLayoutView.java # Visual manual-sheet nesting plan
    ├── RowAdapter.java      # Window rows list
    └── RpRulerView.java     # RP grill spacing ruler
```

---

## Manual PCO & Sheet Cutting

Open **⋮ → Manual PCO & Sheet Cutting** (or the link on the existing Cutting tab).

- **Pipe (PCO):** set the stock-pipe length and blade kerf, then add every cut length and quantity. The app uses Best Fit Decreasing and shows each pipe's cuts, kerf loss, and leftover.
- **Sheet Cutting:** set stock sheet **Width × Height**, optional cut gap, and add every piece's **first size × second size** plus quantity. Normal mode can rotate pieces to reduce the sheet count.
- **Face Cutting:** enable it for patterned / grain / designed sheets. Select **Face on Sheet Height** when the first entered size must follow the sheet height (for example, 4 × 8 with the 8 side as Height), or **Face on Sheet Width** when it must follow Width. Face-mode pieces are never rotated.

---

## Default Rates & Settings

All editable inside the app (Price screen / Settings):

| Setting | Default |
|---------|---------|
| Aluminium rate | ₹450 / kg |
| Glass rate | ₹80 / sq.ft |
| Stock pipe length | 196 inch |
| Blade / kerf | 0.12 inch |
| Pipe price | `weight(kg) = length(inch) / 192 × kgPer16ft`, then `price = weight × aluRate` |
| Glass price | inch-based (H × W → sq.ft) |

---

## Build

Open the project in **Android Studio**, let Gradle sync, and run on a device/emulator with Android 5.0+.

```bash
# with a local Gradle install (wrapper not committed in this repo)
gradle assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Permanent release signing

The release APK is now signed with one permanent key, so future versions install as updates instead of requiring an uninstall. Keep the generated keystore private and configure the four GitHub Actions secrets described in [`SIGNING_SETUP.md`](SIGNING_SETUP.md).

### Automatic APK via GitHub Actions

Har push par workflow (`.github/workflows/build-apk.yml`) automatically:

1. **Debug + Release APK build** karta hai
2. **GitHub Release** banata hai with APK attached (Releases page se download)

Manual build ke liye: **Actions tab → "Build & Release APK" → Run workflow**
