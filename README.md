# ALU Window Optimizer

**Digital Alu. Section** — Wankaner (363621) | Mo. 99799 71170

Native Android application for aluminium window/door fabricators. Calculates cut lengths,
optimizes pipe usage (bin-packing), generates cutting plans, estimates costs,
and exports estimates via WhatsApp or Excel.

---

## Features

- **Two pipe systems**: ZED (Frame, Sutter, Muliya, RP Grill) and DOMAL (Frame, Sutter, RT, RP Grill)
- **Automatic cut calculations** with configurable deductions per system
- **Bin-packing optimizer** — minimize waste via first-fit packing into stock pipes (default 196")
- **RP Grill marking** — auto-suggests grill quantity and spacing with a visual ruler
- **Costing engine** — weight-based pricing (kg per 16 ft x aluminium rate), glass cost, extra charges
- **6-sheet Excel export**: Summary, Windows, Cutting List, Cutting Plan, RP Marking, Glass
- **WhatsApp sharing** for quick estimates
- **Customer CRM** — save/load customer records with search
- **PIN-locked price editor** (default: `1101`)
- **Unit toggle** — inches ↔ millimeters

---

## Tech Stack

- **Language**: Java 8
- **Min SDK**: 24 (Android 7.0), **Target SDK**: 33 (Android 13.0)
- **UI**: Programmatic views + XML, Material Design components
- **Persistence**: SharedPreferences (JSON-serialized)
- **Dependencies**: AndroidX AppCompat, Material, ConstraintLayout, RecyclerView

---

## Build

Open in **Android Studio** (Arctic Fox or newer), sync Gradle, and run on a device/emulator with API 24+.

```bash
./gradlew assembleDebug
```
