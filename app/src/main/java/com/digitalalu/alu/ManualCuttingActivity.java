package com.digitalalu.alu;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.digitalalu.alu.calc.Engine;
import com.digitalalu.alu.calc.ManualCuttingEngine;
import com.digitalalu.alu.calc.Settings;
import com.digitalalu.alu.ui.PipeBarView;
import com.digitalalu.alu.ui.SheetLayoutView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Standalone manual optimizers.  It deliberately does not change the existing
 * window quotation data: fabricators can use it for any loose pipe or sheet job.
 */
public class ManualCuttingActivity extends AppCompatActivity {

    private static final String PREF = "manual_cutting_v1";

    private Settings st;
    private TabLayout tabs;
    private ScrollView pipePage, sheetPage;

    private final List<ManualCuttingEngine.PipeCut> pipeCuts = new ArrayList<>();
    private final List<ManualCuttingEngine.SheetCut> sheetCuts = new ArrayList<>();

    /* Values are persisted internally in inches; fields show the selected app unit. */
    private double pipeStock;
    private double pipeKerf;
    private double sheetWidth;
    private double sheetHeight;
    private double sheetGap;
    private boolean faceCut;
    private int faceAxis = ManualCuttingEngine.FACE_HEIGHT;

    private EditText etPipeStock, etPipeKerf;
    private EditText etSheetWidth, etSheetHeight, etSheetGap;
    private CheckBox cbFace;
    private LinearLayout faceOptions;
    private RadioButton rbFaceHeight, rbFaceWidth;

    private LinearLayout pipeRows, pipeResults;
    private LinearLayout sheetRows, sheetResults;

    private ManualCuttingEngine.PipePlan pipePlan;
    private ManualCuttingEngine.SheetPlan sheetPlan;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        st = Settings.load(this);
        loadData();
        setContentView(R.layout.activity_manual_cutting);

        ((TextView) findViewById(R.id.tvManualUnit)).setText(
                "Enter all sizes in " + (st.mm ? "millimeters (mm)" : "inches (\")"));
        findViewById(R.id.btnManualBack).setOnClickListener(v -> finish());

        tabs = findViewById(R.id.manualTabs);
        tabs.addTab(tabs.newTab().setText("PIPE (PCO)"));
        tabs.addTab(tabs.newTab().setText("SHEET CUTTING"));

        buildPipePage();
        buildSheetPage();

        FrameLayout holder = findViewById(R.id.manualContainer);
        holder.addView(pipePage);
        holder.addView(sheetPage);
        showPage(0);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showPage(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        readPipeSettings(false);
        readSheetSettings(false);
        saveData();
    }

    private void showPage(int index) {
        if (pipePage == null || sheetPage == null) return;
        pipePage.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        sheetPage.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
    }

    /* ======================================================================
       PIPE (PCO) PAGE
       ====================================================================== */

    private void buildPipePage() {
        pipePage = makeScrollPage();
        LinearLayout body = (LinearLayout) pipePage.getChildAt(0);

        Card setup = card("MANUAL PIPE CUTTING (PCO)");
        setup.body.addView(note("Enter your stock pipe and every cut size. Best Fit Decreasing "
                + "packs the cuts into the fewest practical pipes."));
        LinearLayout fields = fieldRow();
        etPipeStock = numberInput(dim(pipeStock));
        etPipeKerf = numberInput(dim(pipeKerf));
        addField(fields, "STOCK PIPE", etPipeStock, 1f);
        addField(fields, "BLADE / KERF", etPipeKerf, 1f);
        setup.body.addView(fields);
        setup.body.addView(note("Kerf is added between cuts only. Unit: " + unitName()));
        body.addView(setup.outer);

        Card cuts = card("CUT SIZES");
        pipeRows = vertical();
        cuts.body.addView(pipeRows);
        MaterialButton add = actionButton("+  ADD CUT SIZE", 0xFF2563EB);
        add.setOnClickListener(v -> {
            readPipeSettings(false);
            showPipeCutDialog(null);
        });
        cuts.body.addView(add);
        body.addView(cuts.outer);

        MaterialButton optimize = actionButton("OPTIMIZE PIPE CUTTING", 0xFF16A34A);
        optimize.setOnClickListener(v -> optimizePipes());
        LinearLayout.LayoutParams optimizeLp = new LinearLayout.LayoutParams(-1, dp(48));
        optimizeLp.bottomMargin = dp(10);
        body.addView(optimize, optimizeLp);

        pipeResults = vertical();
        body.addView(pipeResults);
        renderPipeRows();
        renderPipeResults();
    }

    private void showPipeCutDialog(final ManualCuttingEngine.PipeCut current) {
        final boolean editing = current != null;
        LinearLayout form = dialogForm();
        final EditText name = textInput(editing ? current.name : "");
        final EditText length = numberInput(editing ? dim(current.length) : "");
        final EditText qty = integerInput(editing ? String.valueOf(current.qty) : "1");
        addDialogField(form, "CUT NAME (optional)", name);
        addDialogField(form, "CUT LENGTH (" + unitName() + ")", length);
        addDialogField(form, "QUANTITY", qty);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "Edit pipe cut" : "Add pipe cut")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(editing ? "Update" : "Add", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    double len = readDimension(length);
                    int count = readQuantity(qty);
                    if (Double.isNaN(len) || len <= 0) {
                        length.setError("Enter a valid cut length");
                        return;
                    }
                    if (count <= 0) {
                        qty.setError("Quantity must be at least 1");
                        return;
                    }
                    String cutName = name.getText().toString().trim();
                    if (editing) {
                        current.name = cutName;
                        current.length = len;
                        current.qty = count;
                    } else {
                        pipeCuts.add(new ManualCuttingEngine.PipeCut(cutName, len, count));
                    }
                    pipePlan = null;
                    renderPipeRows();
                    renderPipeResults();
                    saveData();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void renderPipeRows() {
        if (pipeRows == null) return;
        pipeRows.removeAllViews();
        if (pipeCuts.isEmpty()) {
            pipeRows.addView(emptyHint("No cut sizes added yet."));
            return;
        }
        for (final ManualCuttingEngine.PipeCut cut : pipeCuts) {
            LinearLayout row = listRow();
            LinearLayout text = vertical();
            text.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            String title = cut.name == null || cut.name.trim().isEmpty() ? "Pipe cut" : cut.name.trim();
            text.addView(rowTitle(title));
            text.addView(rowSub(dimU(cut.length) + "  x  " + cut.qty + " pcs"));
            row.addView(text);
            TextView delete = deleteButton();
            delete.setOnClickListener(v -> confirmDeletePipe(cut));
            row.addView(delete);
            row.setOnClickListener(v -> showPipeCutDialog(cut));
            pipeRows.addView(row);
        }
    }

    private void confirmDeletePipe(final ManualCuttingEngine.PipeCut cut) {
        new AlertDialog.Builder(this)
                .setTitle("Remove cut size?")
                .setMessage("This cut size will be removed from the pipe plan.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> {
                    pipeCuts.remove(cut);
                    pipePlan = null;
                    renderPipeRows();
                    renderPipeResults();
                    saveData();
                }).show();
    }

    private void optimizePipes() {
        if (!readPipeSettings(true)) return;
        if (pipeCuts.isEmpty()) {
            toast("Add at least one pipe cut size");
            return;
        }
        pipePlan = ManualCuttingEngine.optimizePipes(pipeCuts, pipeStock, pipeKerf);
        renderPipeResults();
        saveData();
        pipePage.post(() -> pipePage.fullScroll(View.FOCUS_DOWN));
    }

    private void renderPipeResults() {
        if (pipeResults == null) return;
        pipeResults.removeAllViews();
        if (pipePlan == null) {
            pipeResults.addView(emptyHint("Add cuts, then tap OPTIMIZE PIPE CUTTING."));
            return;
        }

        Card summary = card("OPTIMIZED PIPE PLAN");
        summary.body.addView(kv("Algorithm", "Best Fit Decreasing"));
        summary.body.addView(kv("Stock pipes required", pipePlan.stockCount() + " pcs"));
        summary.body.addView(kv("Cut material", dimU(pipePlan.fittedCutLength)));
        summary.body.addView(kv("Usage", pct(pipePlan.utilization())));
        summary.body.addView(kv("Blade / kerf loss", dimU(pipePlan.kerfLoss)));
        summary.body.addView(bigKv("LEFTOVER / OFFCUT", dimU(pipePlan.offcut()), 0xFFEA580C));
        if (!pipePlan.oversized.isEmpty()) {
            summary.body.addView(warning("" + pipePlan.oversized.size()
                    + " cut(s) are longer than the selected stock pipe."));
        }
        pipeResults.addView(summary.outer);

        if (!pipePlan.oversized.isEmpty()) {
            Card over = card("CUTS THAT DO NOT FIT");
            for (ManualCuttingEngine.PipePiece p : pipePlan.oversized)
                over.body.addView(note((p.name == null || p.name.isEmpty() ? "Cut" : p.name)
                        + "  -  " + dimU(p.length)));
            pipeResults.addView(over.outer);
        }

        for (int i = 0; i < pipePlan.bars.size(); i++) {
            ManualCuttingEngine.PipeBar bar = pipePlan.bars.get(i);
            Card c = card("PIPE #" + (i + 1));
            c.body.addView(kv("Used", dimU(bar.used) + " / " + dimU(pipeStock)));
            c.body.addView(kv("Left", dimU(Math.max(0, bar.free(pipeStock)))));
            PipeBarView visual = new PipeBarView(this);
            visual.setData(toEngineBin(bar), pipeStock, 0xFF2563EB, st);
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2);
            vp.topMargin = dp(4);
            c.body.addView(visual, vp);
            c.body.addView(note(pipeLabels(bar)));
            pipeResults.addView(c.outer);
        }
    }

    private Engine.Bin toEngineBin(ManualCuttingEngine.PipeBar bar) {
        Engine.Bin output = new Engine.Bin();
        output.used = bar.used;
        for (ManualCuttingEngine.PipePiece p : bar.pieces) {
            String shortName = p.name == null || p.name.trim().isEmpty() ? "CUT" : p.name.trim();
            if (shortName.length() > 8) shortName = shortName.substring(0, 8);
            output.items.add(new Engine.Piece(p.length, shortName, null, shortName));
        }
        return output;
    }

    private String pipeLabels(ManualCuttingEngine.PipeBar bar) {
        StringBuilder out = new StringBuilder("Cuts: ");
        for (int i = 0; i < bar.pieces.size(); i++) {
            if (i > 0) out.append("  |  ");
            ManualCuttingEngine.PipePiece p = bar.pieces.get(i);
            String name = p.name == null || p.name.trim().isEmpty() ? "Cut" : p.name.trim();
            out.append(name).append(" ").append(dim(p.length));
        }
        return out.toString();
    }

    /* ======================================================================
       SHEET CUTTING PAGE
       ====================================================================== */

    private void buildSheetPage() {
        sheetPage = makeScrollPage();
        LinearLayout body = (LinearLayout) sheetPage.getChildAt(0);

        Card setup = card("SHEET SETTINGS");
        setup.body.addView(note("Enter one stock sheet size. Normal mode may rotate pieces for "
                + "less waste; Face Cutting locks the pattern direction."));
        LinearLayout fields = fieldRow();
        etSheetWidth = numberInput(dim(sheetWidth));
        etSheetHeight = numberInput(dim(sheetHeight));
        etSheetGap = numberInput(dim(sheetGap));
        addField(fields, "SHEET WIDTH", etSheetWidth, 1f);
        addField(fields, "SHEET HEIGHT", etSheetHeight, 1f);
        addField(fields, "CUT GAP", etSheetGap, .8f);
        setup.body.addView(fields);
        setup.body.addView(note("Stock sheet is Width x Height. Cut gap reserves space between pieces."));

        cbFace = new CheckBox(this);
        cbFace.setText("FACE CUTTING - lock sheet face / pattern (NO ROTATION)");
        cbFace.setTextSize(12.5f);
        cbFace.setTextColor(0xFF152236);
        cbFace.setTypeface(null, Typeface.BOLD);
        cbFace.setPadding(0, dp(6), 0, 0);
        cbFace.setChecked(faceCut);
        setup.body.addView(cbFace);

        faceOptions = vertical();
        RadioGroup radio = new RadioGroup(this);
        radio.setOrientation(RadioGroup.VERTICAL);
        rbFaceHeight = new RadioButton(this);
        rbFaceHeight.setId(View.generateViewId());
        rbFaceHeight.setText("Face is on SHEET HEIGHT - first size follows Height");
        rbFaceHeight.setTextSize(12f);
        rbFaceHeight.setTextColor(0xFF334155);
        rbFaceWidth = new RadioButton(this);
        rbFaceWidth.setId(View.generateViewId());
        rbFaceWidth.setText("Face is on SHEET WIDTH - first size follows Width");
        rbFaceWidth.setTextSize(12f);
        rbFaceWidth.setTextColor(0xFF334155);
        radio.addView(rbFaceHeight);
        radio.addView(rbFaceWidth);
        if (faceAxis == ManualCuttingEngine.FACE_WIDTH) rbFaceWidth.setChecked(true);
        else rbFaceHeight.setChecked(true);
        faceOptions.addView(radio);
        faceOptions.addView(note("Example: for a 4 x 8 sheet where the 8 side is Height, "
                + "choose HEIGHT. The first size entered will always run on the 8 side."));
        faceOptions.setVisibility(faceCut ? View.VISIBLE : View.GONE);
        setup.body.addView(faceOptions);

        cbFace.setOnCheckedChangeListener((button, checked) -> {
            faceCut = checked;
            faceOptions.setVisibility(checked ? View.VISIBLE : View.GONE);
            sheetPlan = null;
            renderSheetRows();
            renderSheetResults();
            saveData();
        });
        radio.setOnCheckedChangeListener((group, checkedId) -> {
            faceAxis = checkedId == rbFaceWidth.getId()
                    ? ManualCuttingEngine.FACE_WIDTH : ManualCuttingEngine.FACE_HEIGHT;
            if (faceCut) {
                sheetPlan = null;
                renderSheetRows();
                renderSheetResults();
                saveData();
            }
        });
        body.addView(setup.outer);

        Card cuts = card("SHEET PIECE SIZES");
        sheetRows = vertical();
        cuts.body.addView(sheetRows);
        MaterialButton add = actionButton("+  ADD SHEET PIECE", 0xFF2563EB);
        add.setOnClickListener(v -> {
            readSheetSettings(false);
            showSheetCutDialog(null);
        });
        cuts.body.addView(add);
        body.addView(cuts.outer);

        MaterialButton optimize = actionButton("OPTIMIZE SHEET CUTTING", 0xFF16A34A);
        optimize.setOnClickListener(v -> optimizeSheets());
        LinearLayout.LayoutParams optimizeLp = new LinearLayout.LayoutParams(-1, dp(48));
        optimizeLp.bottomMargin = dp(10);
        body.addView(optimize, optimizeLp);

        sheetResults = vertical();
        body.addView(sheetResults);
        renderSheetRows();
        renderSheetResults();
    }

    private void showSheetCutDialog(final ManualCuttingEngine.SheetCut current) {
        final boolean editing = current != null;
        LinearLayout form = dialogForm();
        TextView help = note(faceCut
                ? "FACE MODE: first size is fixed to the selected "
                    + (faceAxis == ManualCuttingEngine.FACE_HEIGHT ? "SHEET HEIGHT" : "SHEET WIDTH")
                    + ". The piece will not rotate."
                : "NORMAL MODE: the optimizer may rotate a piece to reduce waste.");
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-1, -2);
        hLp.bottomMargin = dp(8);
        form.addView(help, hLp);

        final EditText name = textInput(editing ? current.name : "");
        final EditText first = numberInput(editing ? dim(current.first) : "");
        final EditText second = numberInput(editing ? dim(current.second) : "");
        final EditText qty = integerInput(editing ? String.valueOf(current.qty) : "1");
        String firstField = faceCut
                ? "FIRST SIZE (-> SHEET " + (faceAxis == ManualCuttingEngine.FACE_HEIGHT
                    ? "HEIGHT" : "WIDTH") + ")"
                : "FIRST SIZE / HEIGHT";
        String secondField = faceCut ? "SECOND SIZE (CROSS DIRECTION)" : "SECOND SIZE / WIDTH";
        addDialogField(form, "PIECE NAME (optional)", name);
        addDialogField(form, firstField + " (" + unitName() + ")", first);
        addDialogField(form, secondField + " (" + unitName() + ")", second);
        addDialogField(form, "QUANTITY", qty);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "Edit sheet piece" : "Add sheet piece")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(editing ? "Update" : "Add", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    double a = readDimension(first);
                    double b = readDimension(second);
                    int count = readQuantity(qty);
                    if (Double.isNaN(a) || a <= 0) {
                        first.setError("Enter the first size");
                        return;
                    }
                    if (Double.isNaN(b) || b <= 0) {
                        second.setError("Enter the second size");
                        return;
                    }
                    if (count <= 0) {
                        qty.setError("Quantity must be at least 1");
                        return;
                    }
                    String pieceName = name.getText().toString().trim();
                    if (editing) {
                        current.name = pieceName;
                        current.first = a;
                        current.second = b;
                        current.qty = count;
                    } else {
                        sheetCuts.add(new ManualCuttingEngine.SheetCut(pieceName, a, b, count));
                    }
                    sheetPlan = null;
                    renderSheetRows();
                    renderSheetResults();
                    saveData();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void renderSheetRows() {
        if (sheetRows == null) return;
        sheetRows.removeAllViews();
        if (sheetCuts.isEmpty()) {
            sheetRows.addView(emptyHint("No sheet piece sizes added yet."));
            return;
        }
        String mode = faceCut
                ? (faceAxis == ManualCuttingEngine.FACE_HEIGHT ? "First -> sheet Height" : "First -> sheet Width")
                : "Rotation allowed";
        for (final ManualCuttingEngine.SheetCut cut : sheetCuts) {
            LinearLayout row = listRow();
            LinearLayout text = vertical();
            text.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            String title = cut.name == null || cut.name.trim().isEmpty() ? "Sheet piece" : cut.name.trim();
            text.addView(rowTitle(title));
            text.addView(rowSub(dim(cut.first) + " x " + dim(cut.second) + unitSuffix()
                    + "  x  " + cut.qty + " pcs\n" + mode));
            row.addView(text);
            TextView delete = deleteButton();
            delete.setOnClickListener(v -> confirmDeleteSheet(cut));
            row.addView(delete);
            row.setOnClickListener(v -> showSheetCutDialog(cut));
            sheetRows.addView(row);
        }
    }

    private void confirmDeleteSheet(final ManualCuttingEngine.SheetCut cut) {
        new AlertDialog.Builder(this)
                .setTitle("Remove sheet piece?")
                .setMessage("This size will be removed from the sheet plan.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> {
                    sheetCuts.remove(cut);
                    sheetPlan = null;
                    renderSheetRows();
                    renderSheetResults();
                    saveData();
                }).show();
    }

    private void optimizeSheets() {
        if (!readSheetSettings(true)) return;
        if (sheetCuts.isEmpty()) {
            toast("Add at least one sheet piece size");
            return;
        }
        sheetPlan = ManualCuttingEngine.optimizeSheets(sheetCuts, sheetWidth, sheetHeight,
                sheetGap, faceCut, faceAxis);
        renderSheetResults();
        saveData();
        sheetPage.post(() -> sheetPage.fullScroll(View.FOCUS_DOWN));
    }

    private void renderSheetResults() {
        if (sheetResults == null) return;
        sheetResults.removeAllViews();
        if (sheetPlan == null) {
            sheetResults.addView(emptyHint("Add pieces, then tap OPTIMIZE SHEET CUTTING."));
            return;
        }

        Card summary = card("OPTIMIZED SHEET PLAN");
        summary.body.addView(kv("Algorithm", sheetPlan.algorithm));
        summary.body.addView(kv("Stock sheet", dim(sheetPlan.sheetWidth) + " x "
                + dim(sheetPlan.sheetHeight) + unitSuffix()));
        summary.body.addView(kv("Sheets required", sheetPlan.sheetCount() + " pcs"));
        summary.body.addView(kv("Piece material", area(sheetPlan.placedArea)));
        summary.body.addView(kv("Usage", pct(sheetPlan.utilization())));
        summary.body.addView(bigKv("WASTE / SCRAP", area(sheetPlan.waste()), 0xFFEA580C));
        if (faceCut) summary.body.addView(note(faceDirectionText() + " - all pieces stay in this direction."));
        else summary.body.addView(note("Normal optimization: pieces may rotate for a tighter layout."));
        if (sheetGap > 0) summary.body.addView(note("Waste includes the reserved " + dimU(sheetGap)
                + " cut gap between pieces."));
        if (!sheetPlan.oversized.isEmpty()) summary.body.addView(warning(sheetPlan.oversized.size()
                + " piece(s) cannot fit on this sheet in the selected direction."));
        sheetResults.addView(summary.outer);

        if (!sheetPlan.oversized.isEmpty()) {
            Card oversized = card("PIECES THAT DO NOT FIT");
            for (ManualCuttingEngine.SheetPiece p : sheetPlan.oversized) {
                String name = p.name == null || p.name.trim().isEmpty() ? "Piece" : p.name.trim();
                oversized.body.addView(note(name + "  -  " + dim(p.first) + " x "
                        + dim(p.second) + unitSuffix()));
            }
            sheetResults.addView(oversized.outer);
        }

        for (int i = 0; i < sheetPlan.sheets.size(); i++) {
            ManualCuttingEngine.SheetBin bin = sheetPlan.sheets.get(i);
            Card c = card("SHEET #" + (i + 1));
            c.body.addView(kv("Pieces", String.valueOf(bin.pieces.size())));
            c.body.addView(kv("Material use", pct(bin.utilization())));
            c.body.addView(kv("Waste / scrap", area(bin.waste())));
            SheetLayoutView view = new SheetLayoutView(this);
            view.setData(bin, st, sheetPlan.faceCut, sheetPlan.faceAxis, i + 1);
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2);
            vp.topMargin = dp(5);
            c.body.addView(view, vp);
            c.body.addView(note(sheetPieceList(bin)));
            sheetResults.addView(c.outer);
        }
    }

    private String sheetPieceList(ManualCuttingEngine.SheetBin bin) {
        StringBuilder out = new StringBuilder("Pieces: ");
        for (int i = 0; i < bin.pieces.size(); i++) {
            if (i > 0) out.append("  |  ");
            ManualCuttingEngine.SheetPiece p = bin.pieces.get(i);
            String name = p.name == null || p.name.trim().isEmpty() ? "Piece" : p.name.trim();
            out.append(name).append(" ").append(dim(p.first)).append("x").append(dim(p.second));
            if (p.rotated) out.append(" (rotated)");
        }
        return out.toString();
    }

    /* ======================================================================
       SETTINGS AND PERSISTENCE
       ====================================================================== */

    private boolean readPipeSettings(boolean showErrors) {
        if (etPipeStock == null || etPipeKerf == null) return true;
        double stock = readDimension(etPipeStock);
        double kerf = readDimension(etPipeKerf);
        if (Double.isNaN(stock) || stock <= 0) {
            if (showErrors) etPipeStock.setError("Enter stock pipe length");
            return false;
        }
        if (Double.isNaN(kerf) || kerf < 0) {
            if (showErrors) etPipeKerf.setError("Enter 0 or a positive kerf");
            return false;
        }
        pipeStock = stock;
        pipeKerf = kerf;
        return true;
    }

    private boolean readSheetSettings(boolean showErrors) {
        if (etSheetWidth == null || etSheetHeight == null || etSheetGap == null) return true;
        double w = readDimension(etSheetWidth);
        double h = readDimension(etSheetHeight);
        double gap = readDimension(etSheetGap);
        if (Double.isNaN(w) || w <= 0) {
            if (showErrors) etSheetWidth.setError("Enter sheet width");
            return false;
        }
        if (Double.isNaN(h) || h <= 0) {
            if (showErrors) etSheetHeight.setError("Enter sheet height");
            return false;
        }
        if (Double.isNaN(gap) || gap < 0) {
            if (showErrors) etSheetGap.setError("Enter 0 or a positive cut gap");
            return false;
        }
        sheetWidth = w;
        sheetHeight = h;
        sheetGap = gap;
        return true;
    }

    private void loadData() {
        pipeStock = st.stock;
        pipeKerf = st.kerf;
        sheetWidth = 48.0;
        sheetHeight = 96.0;
        sheetGap = 0.0;
        faceCut = false;
        faceAxis = ManualCuttingEngine.FACE_HEIGHT;
        try {
            SharedPreferences p = getSharedPreferences(PREF, MODE_PRIVATE);
            String raw = p.getString("data", null);
            if (raw == null) return;
            JSONObject root = new JSONObject(raw);
            pipeStock = root.optDouble("pipeStock", pipeStock);
            pipeKerf = root.optDouble("pipeKerf", pipeKerf);
            sheetWidth = root.optDouble("sheetWidth", sheetWidth);
            sheetHeight = root.optDouble("sheetHeight", sheetHeight);
            sheetGap = root.optDouble("sheetGap", sheetGap);
            faceCut = root.optBoolean("faceCut", false);
            faceAxis = root.optInt("faceAxis", ManualCuttingEngine.FACE_HEIGHT);
            if (faceAxis != ManualCuttingEngine.FACE_WIDTH) faceAxis = ManualCuttingEngine.FACE_HEIGHT;

            JSONArray pipes = root.optJSONArray("pipeCuts");
            if (pipes != null) for (int i = 0; i < pipes.length(); i++) {
                JSONObject x = pipes.optJSONObject(i);
                if (x == null) continue;
                pipeCuts.add(new ManualCuttingEngine.PipeCut(x.optString("name", ""),
                        x.optDouble("length", 0), x.optInt("qty", 1)));
            }
            JSONArray sheets = root.optJSONArray("sheetCuts");
            if (sheets != null) for (int i = 0; i < sheets.length(); i++) {
                JSONObject x = sheets.optJSONObject(i);
                if (x == null) continue;
                sheetCuts.add(new ManualCuttingEngine.SheetCut(x.optString("name", ""),
                        x.optDouble("first", 0), x.optDouble("second", 0), x.optInt("qty", 1)));
            }
        } catch (Exception ignored) {
            /* Invalid saved data should never prevent opening the manual tool. */
        }
    }

    private void saveData() {
        try {
            JSONObject root = new JSONObject();
            root.put("pipeStock", pipeStock);
            root.put("pipeKerf", pipeKerf);
            root.put("sheetWidth", sheetWidth);
            root.put("sheetHeight", sheetHeight);
            root.put("sheetGap", sheetGap);
            root.put("faceCut", faceCut);
            root.put("faceAxis", faceAxis);

            JSONArray pipes = new JSONArray();
            for (ManualCuttingEngine.PipeCut cut : pipeCuts) {
                JSONObject x = new JSONObject();
                x.put("name", cut.name);
                x.put("length", cut.length);
                x.put("qty", cut.qty);
                pipes.put(x);
            }
            root.put("pipeCuts", pipes);

            JSONArray sheets = new JSONArray();
            for (ManualCuttingEngine.SheetCut cut : sheetCuts) {
                JSONObject x = new JSONObject();
                x.put("name", cut.name);
                x.put("first", cut.first);
                x.put("second", cut.second);
                x.put("qty", cut.qty);
                sheets.put(x);
            }
            root.put("sheetCuts", sheets);
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("data", root.toString()).apply();
        } catch (Exception ignored) { }
    }

    /* ======================================================================
       UI HELPERS
       ====================================================================== */

    private ScrollView makeScrollPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout body = vertical();
        body.setPadding(dp(10), dp(10), dp(10), dp(22));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    private static class Card {
        LinearLayout outer, body;
        Card(LinearLayout outer, LinearLayout body) { this.outer = outer; this.body = body; }
    }

    private Card card(String title) {
        LinearLayout outer = vertical();
        outer.setBackgroundResource(R.drawable.bg_card);
        outer.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        outer.setLayoutParams(lp);

        TextView head = new TextView(this);
        head.setText(title);
        head.setTextSize(12f);
        head.setTypeface(null, Typeface.BOLD);
        head.setLetterSpacing(.05f);
        head.setTextColor(0xFF2563EB);
        head.setPadding(0, 0, 0, dp(7));
        outer.addView(head);

        LinearLayout content = vertical();
        outer.addView(content, new LinearLayout.LayoutParams(-1, -2));
        return new Card(outer, content);
    }

    private LinearLayout vertical() {
        LinearLayout out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        return out;
    }

    private LinearLayout fieldRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setPadding(0, dp(7), 0, dp(4));
        return row;
    }

    private void addField(LinearLayout row, String label, EditText field, float weight) {
        LinearLayout box = vertical();
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(0, -2, weight);
        boxLp.rightMargin = dp(5);
        box.setLayoutParams(boxLp);
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(9.5f);
        text.setTypeface(null, Typeface.BOLD);
        text.setTextColor(0xFF66758C);
        text.setLetterSpacing(.04f);
        text.setPadding(dp(2), 0, 0, dp(2));
        box.addView(text);
        box.addView(field, new LinearLayout.LayoutParams(-1, dp(42)));
        row.addView(box);
    }

    private MaterialButton actionButton(String label, int color) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12.5f);
        button.setTypeface(null, Typeface.BOLD);
        button.setLetterSpacing(.03f);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setCornerRadius(dp(10));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.topMargin = dp(8);
        button.setLayoutParams(lp);
        return button;
    }

    private EditText numberInput(String value) {
        EditText field = new EditText(this);
        field.setText(value);
        field.setSelectAllOnFocus(false);
        field.setSingleLine(true);
        field.setTextSize(14f);
        field.setTypeface(null, Typeface.BOLD);
        field.setTextColor(0xFF152236);
        field.setGravity(Gravity.CENTER);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setBackgroundResource(R.drawable.bg_input);
        field.setPadding(dp(4), 0, dp(4), 0);
        return field;
    }

    private EditText integerInput(String value) {
        EditText field = numberInput(value);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        return field;
    }

    private EditText textInput(String value) {
        EditText field = new EditText(this);
        field.setText(value == null ? "" : value);
        field.setSingleLine(true);
        field.setTextSize(14f);
        field.setTextColor(0xFF152236);
        field.setBackgroundResource(R.drawable.bg_input);
        field.setPadding(dp(10), 0, dp(10), 0);
        return field;
    }

    private LinearLayout dialogForm() {
        LinearLayout form = vertical();
        int p = dp(6);
        form.setPadding(p, 0, p, 0);
        return form;
    }

    private void addDialogField(LinearLayout form, String label, EditText field) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(10f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF66758C);
        title.setLetterSpacing(.04f);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
        tLp.topMargin = dp(6);
        tLp.bottomMargin = dp(2);
        form.addView(title, tLp);
        form.addView(field, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private LinearLayout listRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_input);
        row.setPadding(dp(10), dp(8), dp(7), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView rowTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13f);
        view.setTextColor(0xFF152236);
        view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private TextView rowSub(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(11.5f);
        view.setTextColor(0xFF66758C);
        view.setPadding(0, dp(2), 0, 0);
        return view;
    }

    private TextView deleteButton() {
        TextView view = new TextView(this);
        view.setText("×");
        view.setTextColor(0xFFDC2626);
        view.setTextSize(28f);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription("Remove");
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(38)));
        return view;
    }

    private TextView note(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextSize(11.5f);
        view.setTextColor(0xFF66758C);
        view.setPadding(0, dp(2), 0, dp(3));
        return view;
    }

    private TextView emptyHint(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextSize(12.5f);
        view.setTextColor(0xFF94A3B8);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(6), dp(14), dp(6), dp(14));
        return view;
    }

    private TextView warning(String message) {
        TextView view = new TextView(this);
        view.setText("!  " + message);
        view.setTextSize(11.5f);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(0xFFB45309);
        view.setPadding(dp(8), dp(7), dp(8), dp(7));
        view.setBackgroundColor(0xFFFFF7ED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        view.setLayoutParams(lp);
        return view;
    }

    private View kv(String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        TextView left = new TextView(this);
        left.setText(key);
        left.setTextSize(12f);
        left.setTextColor(0xFF66758C);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView right = new TextView(this);
        right.setText(value);
        right.setTextSize(12.5f);
        right.setTextColor(0xFF152236);
        right.setTypeface(null, Typeface.BOLD);
        right.setGravity(Gravity.END);
        row.addView(left);
        row.addView(right);
        return row;
    }

    private View bigKv(String key, String value, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9), dp(7), dp(9), dp(7));
        row.setBackgroundColor(0xFFF1F5F9);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(5);
        lp.bottomMargin = dp(3);
        row.setLayoutParams(lp);
        TextView left = new TextView(this);
        left.setText(key);
        left.setTextSize(10.5f);
        left.setTextColor(0xFF475569);
        left.setTypeface(null, Typeface.BOLD);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView right = new TextView(this);
        right.setText(value);
        right.setTextSize(16f);
        right.setTextColor(color);
        right.setTypeface(null, Typeface.BOLD);
        row.addView(left);
        row.addView(right);
        return row;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private double readDimension(EditText field) {
        try {
            return st.toIn(Double.parseDouble(field.getText().toString().trim()));
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private int readQuantity(EditText field) {
        try { return Integer.parseInt(field.getText().toString().trim()); }
        catch (Exception e) { return 0; }
    }

    private String dim(double inches) { return st.fmt(inches); }
    private String dimU(double inches) { return st.fmtU(inches); }
    private String unitSuffix() { return st.unit(); }
    private String unitName() { return st.mm ? "mm" : "inch"; }

    private String area(double squareInches) {
        if (st.mm) return String.format(Locale.US, "%.0f mm²", squareInches * Settings.MM * Settings.MM);
        return String.format(Locale.US, "%.2f in²", squareInches);
    }

    private String pct(double value) { return String.format(Locale.US, "%.1f%%", value); }

    private String faceDirectionText() {
        return faceAxis == ManualCuttingEngine.FACE_WIDTH
                ? "Face on sheet Width (first size -> Width)"
                : "Face on sheet Height (first size -> Height)";
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}
