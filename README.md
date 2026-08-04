# ALU Window - Aluminium Window Calculator & Costing Engine

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android)
![Min SDK](https://img.shields.io/badge/Min%20SDK-21%20(Android%205.0)-blue?style=flat-square)
![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-green?style=flat-square)
![Language](https://img.shields.io/badge/Language-Java-orange?style=flat-square&logo=java)

**ALU Window** is a specialized native Android application designed for aluminium window fabricators, contractors, and workshops. It automates cutting size calculations, optimizes pipe/bar usage using advanced bin-packing algorithms, manages rate cards, and generates comprehensive Excel (`.xlsx`) quotations and cutting sheets.

---

## ✨ Key Features

### 1. 🧮 Precision Cutting Engine (`Engine.java`)
- **System Support**: Comprehensive support for **ZED Systems** (`Z_FRAME`, `Z_SUTTER`, `Z_MULIYA`, `Z_RP`) and **DOMAL Systems** (`D_FRAME`, `D_SUTTER`, `D_RT`, `D_RP`).
- **Best Fit Decreasing (BFD) Bin Packing Algorithm (v2)**: Automatically optimizes cutting lists across standard aluminium bar lengths to minimize scrap and wastage.
- **Unit Calculation**: Complete inch-based accuracy for all frame, shutter, glass, and accessory calculations.

### 2. 🛠️ Custom Formula Management (`CustomFormulaManager.java` & `MathEvaluator.java`)
- Define custom aluminium systems with user-defined mathematical formulas.
- Dynamic mathematical expression parsing (`MathEvaluator`) for:
  - **Shutter Height & Width (`sutterH`, `sutterW`)**
  - **Muliya Height (`muliyaH`)**
  - **Interlock / RP Length (`rpLen`)**
  - **Glass Dimensions (`glassH`, `glassW`)**
- Persistent JSON-based serialization and storage for custom system profiles.

### 3. 💰 Costing & Price Book (`Costing.java`, `PriceBook.java`, `PriceActivity.java`)
- Maintain detailed price books for aluminium sections, glass, hardware, and extra accessories.
- Support for multiple rate structures:
  - **Per Kg** (Aluminium sections by weight)
  - **Per Sq.Ft** (Glass, mesh, surface treatments)
  - **Per Piece / Pair** (Locks, rollers, fasteners, bearings)
- Auto-calculation of total project estimates and customer quotations.

### 4. 📊 Excel Quotation & Sheet Export (`Exporter.java`, `XlsxWriter.java`)
- Generate native Microsoft Excel (`.xlsx`) workbooks directly from the app.
- Exports include:
  - Detailed client quotation sheets.
  - Section-wise cutting lists for workshop floor execution.
  - Cost breakdowns and material summary reports.

### 5. 👥 Customer & Project Management (`CustomerActivity.java`, `Customer.java`)
- Store customer details, order items, and site measurements.
- Support for multi-item window projects with individual system selections and extra fittings.

### 6. 🎨 Visual UI Components
- **`PipeBarView.java`**: Interactive visual representation of bar cutting layouts and scrap distribution.
- **`RpRulerView.java`**: Precision ruler view for alignment and visual profile verification.

---

## 📂 Architecture & Package Structure

```
app/src/main/java/com/digitalalu/alu/
├── MainActivity.java         # Primary Dashboard & Quotation Workspace
├── CustomerActivity.java     # Customer & Order Details Management
├── PriceActivity.java        # Price Book & Rate Card Configuration
├── calc/                     # Calculation Engines & Mathematical Parsing
│   ├── Engine.java           # Core Cutting Calculator & BFD Bin-Packing Engine
│   ├── CustomFormulaManager.java # Dynamic Custom Formula Profile Handler
│   ├── MathEvaluator.java    # Mathematical Expression Evaluator
│   ├── Costing.java          # Costing & Margin Engine
│   ├── PriceBook.java        # Price Catalog Management
│   └── Settings.java         # Global Application Settings
├── export/                   # Export Utilities
│   ├── Exporter.java         # Report Formatter & Sheet Builder
│   └── XlsxWriter.java       # Native Excel (.xlsx) Binary Generator
├── model/                    # Data Structures
│   ├── Customer.java         # Customer Entity
│   └── WindowItem.java       # Window Specification Model
└── ui/                       # Custom Android Views & UI Adapters
    ├── PipeBarView.java      # Visual Bar Cutting Layout View
    ├── RpRulerView.java      # Custom Ruler Component
    └── RowAdapter.java       # Dynamic Row Table Adapter
```

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
- **Android Studio** (Koala / Ladybug or newer recommended)
- **JDK 17** (or compatible source/target compatibility)
- **Android SDK 34**

### Command-Line Build
1. Open a terminal in the repository root.
2. Build the Debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Build the Release APK:
   ```bash
   ./gradlew assembleRelease
   ```

### Android Studio Setup
1. Open Android Studio and select **"Open an Existing Project"**.
2. Select the repository root folder (`aluminium-section-`).
3. Let Gradle sync project dependencies.
4. Click **Run** (`Shift + F10`) to launch the app on an emulator or physical Android device.

---

## 📋 Version History
- **v1.3 (v2 Source Update - 2026)**
  - Added `CustomFormulaManager` & `MathEvaluator` for custom system formulas.
  - Integrated Best Fit Decreasing (BFD) bin-packing optimization in `Engine.java`.
  - Enhanced Excel export reporting in `Exporter.java`.
  - Upgraded customer management and UI layout components.
