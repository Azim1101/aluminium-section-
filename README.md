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
- **Version**: 1.3 (versionCode 3)
- **UI**: XML layouts + programmatic views, Material components
- **Persistence**: SharedPreferences (JSON-serialized)
- **Dependencies**: AndroidX AppCompat 1.6.1, Material 1.11.0, RecyclerView 1.3.2, ConstraintLayout 2.1.4, Core 1.12.0

---

## Project Structure

```
app/src/main/java/com/digitalalu/alu/
├── MainActivity.java        # Main estimate entry, cutting plan, share/export
├── PriceActivity.java       # Price book viewer/editor (PIN locked editing)
├── CustomerActivity.java    # Saved customer estimates (PIN locked)
├── calc/
│   ├── Engine.java          # Cut-length engine + bin-packing optimizer
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
    ├── RowAdapter.java      # Window rows list
    └── RpRulerView.java     # RP grill spacing ruler
```

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
