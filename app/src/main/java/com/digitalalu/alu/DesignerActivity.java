package com.digitalalu.alu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.digitalalu.alu.design.DesignDocument;
import com.digitalalu.alu.design.DesignJson;
import com.digitalalu.alu.export.DesignDxfWriter;
import com.digitalalu.alu.ui.DesignPreviewView;
import com.digitalalu.alu.ui.InsetsHelper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Isolated offline schematic workspace; no changes to legacy fabrication data. */
public final class DesignerActivity extends AppCompatActivity {
    private static final int SAVE_JSON = 801, OPEN_JSON = 802, SAVE_DXF = 803, SAVE_PDF = 804;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private EditText width, height, frame, mullion, bays;
    private Spinner kind;
    private TextView status;
    private DesignPreviewView preview;
    private DesignDocument document;
    private DesignDocument.Unit unit = DesignDocument.Unit.MM;
    private DesignDocument pending;
    private DesignDocument.Unit pendingUnit;
    private Button units;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad); sc.addView(root); setContentView(sc);
        TextView title = new TextView(this); title.setText(R.string.designer_title);
        title.setTextSize(22); root.addView(title); InsetsHelper.apply(root, title);
        TextView note = new TextView(this); note.setText(R.string.designer_notice); root.addView(note);
        kind = new Spinner(this); kind.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"Window", "Door"}));
        kind.setContentDescription("Design type"); root.addView(kind);
        units = button(root, "Units: mm", this::switchUnits);
        width = field(root, "Overall width", "1200", false);
        height = field(root, "Overall height", "1200", false);
        frame = field(root, "Frame face width", "50", false);
        mullion = field(root, "Mullion face width", "40", false);
        bays = field(root, "Equal bays (1–12)", "2", true);
        button(root, "Apply drawing", v -> apply());
        preview = new DesignPreviewView(this);
        root.addView(preview, new LinearLayout.LayoutParams(-1, pad * 22));
        status = new TextView(this); root.addView(status);
        button(root, "Open design JSON", v -> open());
        button(root, "Save design JSON", v -> create(SAVE_JSON, "application/json", "alu-elevation.json"));
        button(root, "Export DXF (mm)", v -> create(SAVE_DXF, "application/dxf", "alu-elevation.dxf"));
        button(root, "Export PDF drawing", v -> create(SAVE_PDF, "application/pdf", "alu-elevation.pdf"));
        button(root, "Back to estimate", v -> finish());
        if (state != null) {
            try {
                unit = DesignDocument.Unit.valueOf(state.getString("unit", "MM"));
                document = DesignJson.decode(state.getString("document", ""));
                showDocument();
                String[] raw = state.getStringArray("inputs");
                if (raw != null && raw.length == 5) {
                    EditText[] fields = fields();
                    for (int i = 0; i < raw.length; i++) fields[i].setText(raw[i]);
                }
                kind.setSelection(state.getInt("kind", 0));
                String queued = state.getString("pending");
                if (queued != null) {
                    pending = DesignJson.decode(queued);
                    pendingUnit = DesignDocument.Unit.valueOf(state.getString("pendingUnit", "MM"));
                }
            } catch (Exception e) { message("Unable to restore drawing: " + e.getMessage()); }
        }
        if (document == null) apply();
    }
    private EditText[] fields() { return new EditText[]{width, height, frame, mullion, bays}; }
    private EditText field(LinearLayout root, String label, String value, boolean integer) {
        TextView text = new TextView(this); text.setText(label); root.addView(text);
        EditText input = new EditText(this); input.setText(value); input.setSingleLine(true);
        input.setContentDescription(label);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | (integer ? 0 : InputType.TYPE_NUMBER_FLAG_DECIMAL));
        input.setId(View.generateViewId()); input.setSaveEnabled(false); root.addView(input); return input;
    }
    private Button button(LinearLayout root, String title, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(title); b.setOnClickListener(listener); root.addView(b); return b;
    }
    private double mm(EditText field) { return unit.toMm(Double.parseDouble(field.getText().toString().trim())); }
    private boolean apply() {
        try {
            document = new DesignDocument(kind.getSelectedItemPosition() == 0 ?
                    DesignDocument.Kind.WINDOW : DesignDocument.Kind.DOOR,
                    mm(width), mm(height), mm(frame), mm(mullion),
                    Integer.parseInt(bays.getText().toString().trim()));
            updatePreview(); return true;
        } catch (IllegalArgumentException e) { message("Check dimensions: " + e.getMessage()); return false; }
    }
    private void updatePreview() {
        preview.setDocument(document, unit);
        status.setText(String.format(Locale.US,
                "%s · %d equal bays\nClear bay: %s × %s\nClear opening area: %.3f m²\nSchematic only — not glass order size or cutting list.",
                document.kind, document.bays, DesignPreviewView.label(document.clearBayWidthMm(), unit),
                DesignPreviewView.label(document.heightMm - 2 * document.frameMm, unit), document.clearOpeningAreaM2()));
        units.setText(unit == DesignDocument.Unit.MM ? "Units: mm" : "Units: inches");
    }
    private void showDocument() {
        double[] values = {document.widthMm, document.heightMm, document.frameMm, document.mullionMm};
        EditText[] inputs = fields();
        for (int i = 0; i < 4; i++) inputs[i].setText(Double.toString(unit.fromMm(values[i])));
        bays.setText(Integer.toString(document.bays));
        kind.setSelection(document.kind == DesignDocument.Kind.WINDOW ? 0 : 1);
        updatePreview();
    }
    private void switchUnits(View v) {
        if (!apply()) return;
        unit = unit == DesignDocument.Unit.MM ? DesignDocument.Unit.INCH : DesignDocument.Unit.MM;
        showDocument();
    }
    private void open() {
        if (pending != null) { message("Finish current export first"); return; }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json");
        i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, OPEN_JSON);
    }
    private void create(int code, String mime, String name) {
        if (pending != null || !apply()) return;
        pending = document; pendingUnit = unit;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, code);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        try {
            state.putString("document", DesignJson.encode(document)); state.putString("unit", unit.name());
            EditText[] inputs = fields(); String[] raw = new String[5];
            for (int i = 0; i < 5; i++) raw[i] = inputs[i].getText().toString();
            state.putStringArray("inputs", raw); state.putInt("kind", kind.getSelectedItemPosition());
            if (pending != null) {
                state.putString("pending", DesignJson.encode(pending)); state.putString("pendingUnit", pendingUnit.name());
            }
        } catch (Exception e) { message("Drawing state could not be saved"); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request < SAVE_JSON || request > SAVE_PDF) return;
        DesignDocument snapshot = pending; DesignDocument.Unit snapshotUnit = pendingUnit;
        pending = null; pendingUnit = null;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        io.execute(() -> {
            try {
                if (request == OPEN_JSON) {
                    String text;
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        if (in == null) throw new IOException("Cannot open document");
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[2048]; int n;
                        while ((n = in.read(buffer)) != -1) {
                            if (bytes.size() + n > 16384) throw new IOException("Design file exceeds 16 KB");
                            bytes.write(buffer, 0, n);
                        }
                        text = bytes.toString("UTF-8");
                    }
                    DesignDocument opened = DesignJson.decode(text);
                    runOnUiThread(() -> { if (!isDestroyed()) { document = opened; showDocument(); } });
                } else {
                    if (snapshot == null) throw new IOException("Export state missing; please retry");
                    try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                        if (out == null) throw new IOException("Cannot write document");
                        if (request == SAVE_PDF) writePdf(snapshot, snapshotUnit, out);
                        else {
                            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                            if (request == SAVE_JSON) writer.write(DesignJson.encode(snapshot));
                            else DesignDxfWriter.write(snapshot, writer);
                            writer.flush();
                        }
                    }
                    runOnUiThread(() -> { if (!isDestroyed()) message("Document exported"); });
                }
            } catch (Exception e) {
                runOnUiThread(() -> { if (!isDestroyed()) message("Document error: " + e.getMessage()); });
            }
        });
    }
    private static void writePdf(DesignDocument d, DesignDocument.Unit unit, OutputStream out) throws IOException {
        PdfDocument pdf = new PdfDocument();
        try {
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, 1).create());
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setTextSize(18);
            page.getCanvas().drawText("ALU Window - " + d.kind + " elevation", 32, 38, p);
            page.getCanvas().save(); page.getCanvas().translate(0, 65);
            DesignPreviewView.render(page.getCanvas(), d, unit, 595, 620, p);
            page.getCanvas().restore(); p.setTextSize(11);
            page.getCanvas().drawText("Schematic only. Not a fabrication cut list or glass order.", 32, 760, p);
            page.getCanvas().drawText("Not to printed scale. DXF coordinates are millimeters.", 32, 780, p);
            pdf.finishPage(page); pdf.writeTo(out);
        } finally { pdf.close(); }
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { io.shutdown(); super.onDestroy(); }
}
