# Upgrade audit and staged plan — 2026-11-05

## Scope and evidence
Baseline: 8519b2e, Android Java, AGP 8.7.3, API 24–35. Source inventory
covers all Java production/tests, layout IDs and JNI files (SOURCE_INVENTORY.md).
This is a static audit, not device certification or proof that every code path works.

### Existing implementation (source, not README)
- MainActivity: window rows, ZED/DOMAL/custom systems, settings, stock/offcuts,
  cutting graphics, supplier BOM, customer saves and backup document picker.
- AgentActivity: rule/local/cloud hybrid chat, model downloads, FunctionGemma,
  MNN fallback, tool execution and embedded Perchance web bridge.
- CustomerActivity: PIN-gated saved estimate CRM. PriceActivity: rates/extras/PIN.
  UserProfileActivity: business/contact metadata.
- ManualCuttingActivity: standalone bars and sheets, face/grain locking and sharing.
- Engine: inch-based fabrication deductions, RP spacing, per-section stock and
  best-fit packing. ManualCuttingEngine: BFD pipes, multi-pass MaxRects sheets.
  Keep both; shelf FFD is not an automatic improvement over existing MaxRects.
- Costing/PriceBook/BomBuilder calculate existing material/cost/supplier lists.
  MathEvaluator/CustomFormulaManager implement user-defined systems.
- Customer/WindowItem/UserProfile/ChatMessage use JSON and SharedPreferences,
  not a Room/SQL database. BackupManager has an explicit preference allowlist.
- Exporter/XlsxWriter: text, raster cutting plans and XLSX. No general native
  window CAD model, profile-section editor or DXF exporter found.
- UI: row adapters, sheet/pipe/ruler custom Views, inset handling, download ring.
  Existing XML layouts and dark/night palettes must remain intact.
- JNI: MNN bridge (nativeLoad/nativeGenerate/nativeFree), streaming UTF-8 handling,
  mutex-protected generation; prebuilt armv7/arm64 libraries. Do not alter JNI ABI.

### Risks and incomplete areas
1. No validated general window/door CAD document; existing WindowItem is a
   fabrication row, not geometric joinery. Do not interpret elevation face width
   as a fabrication deduction or equate clear openings to ordered glass sizes.
2. Existing PerchanceBrain/AluAppBridge uses WebView and online services. This
   conflicts with a literal app-wide "no WebView" requirement. Preserve working
   legacy AI for now; new designer is native/offline with no WebView dependency.
   Removing the legacy web tier needs a separate migration and regression stage.
3. Latest main APK workflow failed at Android SDK setup (run 41981385203).
   Downloading its detailed logs is blocked in this workspace; root cause is not
   proven. Pin runner to ubuntu-24.04, use its preinstalled SDK explicitly.
4. Imported versionCode 3 is below historic repository versionCode 9. Increment
   beyond published APK codes before distribution; retain permanent release key.
5. Some parsing/storage paths suppress exceptions; backup restore is not fully
   transactional. Formula/optimizer inputs need broader finite-value and size
   limits. Leave existing formulas unchanged in this first module.
6. Existing AI can consume far more memory than drawings. New geometry must be
   bounded and independent of model loading; 6GB hardware still needs profiling.
7. Existing backup excludes AI prefs/models by design of allowlist; do not claim
   backup is a byte-for-byte copy of everything in app storage.

## Reference research (metadata + selected source, not code reuse)
| Repository | License observed | Source studied / architectural lesson | Decision |
|---|---|---|---|
| OpenAEC-Foundation/open-frame-studio | CC-BY-SA-4.0 | ofs-core geometry.rs, export/dxf.rs; shared geometry for views/exports, frame/cell separation | No code copied; share-alike review needed before reuse |
| doublesharp/bin-packing | MIT | one_d/heuristics.rs; FFD/BFD alternatives, explicit unplaced demand and bounded search | Future benchmark, not a replacement yet |
| orgofjs/izCAD | GPL-3.0 | measurementGeometry.ts; unit-aware bounds, snap limits, approximate geometry flags | No code copied; Capacitor/web renderer unsuitable for native-only module |
| pradhankukiran/bend-form | No license reported / no LICENSE in tree | profile-engine.js; centerline/offset geometry, finite-input and self-intersection validation | No reuse permission assumed; bending != extruded aluminium fabrication |
| Kensleyz/cutlist-optimizer | MIT | optimizer.js; shelf FFD, kerf, rotation and explicit rejected parts; renderer/UI separated | Keep stronger existing MaxRects; borrow only architecture concept |

All links are https://github.com/<repository>. These observations apply to the
queried source on audit date, not a guarantee of compatibility of all dependencies.
No third-party implementation is vendored in this change.

## Prioritized architecture / delivery gates
1. **First module, this change:** immutable rectangular elevation document;
   pure Java geometry and unit conversion; native Canvas designer (equal bays,
   window/door selection, frame/mullion face width); common geometry for PDF/DXF;
   local JSON document save/open and bundle state; calculation/export tests.
   Clearly labelled schematic, not a manufacturing cut list.
2. Versioned project/profile repository, independent profile editor with validated
   polygons/holes and undo/redo. Snapshot geometry without modifying legacy JSON.
3. Joinery-aware window/door layout, opening symbols, adjustable/transom divisions;
   verified mapping to existing Engine formulas. Only then emit production BOM.
4. Extract packing interfaces, bounded work on background executor, independent
   conservation/non-overlap/kerf validators and benchmarks before changing solvers.
5. Accessories/rules catalog (configurable supplier data), quotation snapshots
   linked to Customer IDs; keep historical estimates and backup formats readable.
6. Extend export facade for production PDF/Excel/DXF from same approved document.
7. Offline AI tool migration and device tests (6GB arm64), accessibility, PDF/DXF
   interoperability, backup round-trip, release-signature update installation.

### Module boundaries
- `design/`: immutable DesignDocument, unit conversions and geometry. No Android.
- `ui/DesignPreviewView`: Canvas rendering only, no fabrication formulas.
- `DesignerActivity`: input/document lifecycle and SAF orchestration only.
- `export/DesignDxfWriter`: pure Java streaming CAD output.
- existing calc/model/export modules retained; no rewrite or database migration.

### Acceptance and known limits
First module is rectangular equal-bay schematic design, not arbitrary CAD, DWG
import, freeform profile editing or certified glazing/cutting dimensions. It uses
mm internally; legacy fabrication continues to use inches. Saved designer JSON is
an explicit separate portable document, not silently added to customer/backup.
CI must run existing + new unit tests before producing a debug APK. Branch builds
must not publish a production/latest release. Signing secrets never enter source.

## First-module delivery notes
- More → Window & Door Designer. Edit inputs then Apply drawing. Unit toggle
  validates/converts current input; no legacy Settings writes.
- Clear opening area is geometry only, not glass procurement area.
- Open/Save JSON use Android Storage Access Framework (16KB import limit).
  Save JSON explicitly before leaving; no new unbacked-up preference namespace.
- PDF is a vector schematic, not print-to-scale; DXF has only FRAME/OPENING
  line entities, not production dimensions or DWG support.
- JSON dimensions are mm regardless of display units. Invalid units, fractional
  bay counts, unknown versions, excessive/NaN sizes are rejected.
- UI state/export snapshot survives recreation; bounded disk I/O runs on a
  single executor. Preview uses no bitmaps and a maximum of 12 openings.
- Version 1.2.0 / code 20 exceeds known repository code 9. Same release key is
  still necessary for upgrading an existing signed installation; debug APKs
  cannot replace differently signed release builds.
- New branch CI runs all unit tests and builds debug without release secrets;
  uploads reports + APK, does not publish production releases.
