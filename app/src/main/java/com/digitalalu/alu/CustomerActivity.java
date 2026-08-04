package com.digitalalu.alu;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.digitalalu.alu.calc.Engine;
import com.digitalalu.alu.calc.Settings;
import com.digitalalu.alu.calc.CustomFormulaManager;
import com.digitalalu.alu.export.Exporter;
import com.digitalalu.alu.model.Customer;
import com.digitalalu.alu.model.WindowItem;

import java.io.File;
import java.util.List;
import java.util.Map;

/** Saved customer estimates — PIN locked, with share & pipe cutting details */
public class CustomerActivity extends AppCompatActivity {

    public static final String EXTRA_LOAD_ID = "load_id";

    private List<Customer> list;
    private LinearLayout box;
    private boolean unlocked = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        list = Customer.loadAll(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F6FB);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundColor(0xFF2563EB);
        top.setPadding(dp(16), dp(12), dp(16), dp(12));
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText("CUSTOMERS");
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setTypeface(null, Typeface.BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(t);
        TextView cnt = new TextView(this);
        cnt.setText(list.size() + " saved");
        cnt.setTextColor(0xFFCFE0FF);
        cnt.setTextSize(12);
        top.addView(cnt);
        root.addView(top);

        ScrollView sc = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(24));
        sc.addView(box);
        sc.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(sc);
        setContentView(root);

        askPin();
    }

    private void askPin() {
        View v = getLayoutInflater().inflate(R.layout.dlg_pin, null);
        EditText et = v.findViewById(R.id.etPin);
        ((TextView) ((LinearLayout) v).getChildAt(0))
                .setText("Enter PIN to open saved estimates");

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Customer records")
                .setView(v)
                .setCancelable(false)
                .setPositiveButton("Open", null)
                .setNegativeButton("Back", (x, y) -> finish())
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            if (et.getText().toString().trim().equals(Customer.getPin(this))) {
                unlocked = true;
                d.dismiss();
                render();
            } else {
                et.setText("");
                toast("Wrong PIN");
            }
        });
    }

    private void render() {
        box.removeAllViews();
        if (!unlocked) return;

        if (list.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("No saved estimates yet.\n\nGo back, add windows, then use\nMenu \u2192 Save to customer");
            t.setTextSize(13);
            t.setTextColor(0xFF94A3B8);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(10), dp(40), dp(10), dp(40));
            box.addView(t);
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            final Customer c = list.get(i);
            View v = getLayoutInflater().inflate(R.layout.item_customer, null);
            ((TextView) v.findViewById(R.id.cuName))
                    .setText(c.name.isEmpty() ? "(no name)" : c.name);
            String info = c.mobile;
            if (!c.village.isEmpty()) info += (info.isEmpty() ? "" : "  \u00b7  ") + c.village;
            ((TextView) v.findViewById(R.id.cuInfo)).setText(info);
            ((TextView) v.findViewById(R.id.cuMeta))
                    .setText(c.windows.size() + " windows   \u00b7   " + c.dateStr());

            TextView load = v.findViewById(R.id.cuLoad);
            load.getBackground().setTint(0xFF2563EB);
            load.setOnClickListener(x -> {
                Intent r = new Intent();
                r.putExtra(EXTRA_LOAD_ID, c.id);
                setResult(RESULT_OK, r);
                finish();
            });

            v.findViewById(R.id.cuDel).setOnClickListener(x ->
                    new AlertDialog.Builder(this)
                            .setTitle("Delete " + c.name + "?")
                            .setPositiveButton("Delete", (dd, w) -> {
                                list.remove(c);
                                Customer.saveAll(this, list);
                                render();
                            })
                            .setNegativeButton("Cancel", null).show());

            v.setOnClickListener(x -> showDetail(c));
            box.addView(v);
        }
    }

    private void showDetail(Customer c) {
        Settings st = Settings.load(this);
        CustomFormulaManager.activeSystems = CustomFormulaManager.getSystems(this);

        // Calculate pipe cutting for this customer's windows
        List<Engine.WinResult> results = Engine.calcAll(c.windows, st);
        Map<String, Engine.TypeSummary> summary = Engine.summarize(results, st);
        Engine.Grand grand = Engine.grand(results, summary, st);

        StringBuilder b = new StringBuilder();
        b.append("\uD83D\uDC64 ").append(c.name).append("\n");
        b.append("Mobile : ").append(c.mobile.isEmpty() ? "-" : c.mobile).append("\n");
        b.append("Village: ").append(c.village.isEmpty() ? "-" : c.village).append("\n");
        b.append("Saved  : ").append(c.dateStr()).append("\n");
        b.append("Windows: ").append(c.windows.size()).append("\n\n");

        // Window details
        b.append("\u2500\u2500 WINDOWS \u2500\u2500\n");
        for (Engine.WinResult r : results) {
            if (r.empty) continue;
            b.append(WindowItem.systemName(r.system)).append(" \u00b7 ").append(r.src.name)
             .append("  ").append(st.fmt(r.H)).append(" x ").append(st.fmt(r.W)).append(st.unit())
             .append("  ").append(r.q).append(" sut").append("\n");
            if (r.ok) {
                b.append("  Sutter: ").append(st.fmt(r.sutterH)).append(" x ").append(st.fmt(r.sutterW)).append("\n");
                if (r.muliyaQ > 0)
                    b.append("  ").append(r.muliyaLabel()).append(": ").append(st.fmt(r.muliyaH)).append(" x").append(r.muliyaQ).append("\n");
                if (r.rpQty > 0)
                    b.append("  RP: ").append(r.rpQty).append(" pcs @ ").append(st.fmt(r.rpSpace)).append("\n");
            }
        }

        // Pipe summary
        b.append("\n\u2500\u2500 PIPE CUTTING \u2500\u2500\n");
        b.append("Total pipes: ").append(grand.stockPipes).append(" (ZED ").append(grand.zedPipes)
         .append(" / DOMAL ").append(grand.domalPipes).append(")\n");
        b.append("Pieces: ").append(grand.pcs).append("\n");
        b.append("Waste: ").append(String.format(java.util.Locale.US, "%.1f%%", grand.wastePct())).append("\n");

        for (Map.Entry<String, Engine.TypeSummary> e : summary.entrySet()) {
            Engine.TypeSummary ts = e.getValue();
            b.append("  ").append(Engine.shortName(e.getKey()))
             .append(": ").append(ts.pcs).append(" pcs, ")
             .append(ts.stockNeeded()).append(" pipes").append("\n");
            // Show pipe-level cutting
            if (ts.bins != null) {
                for (int i = 0; i < ts.bins.size(); i++) {
                    Engine.Bin bin = ts.bins.get(i);
                    b.append("    Pipe#").append(i + 1).append(": ");
                    for (int j = 0; j < bin.items.size(); j++) {
                        if (j > 0) b.append(" + ");
                        Engine.Piece pc = bin.items.get(j);
                        if (pc.cutLabel != null) b.append(pc.cutLabel).append(":");
                        b.append(st.fmt(pc.len));
                    }
                    double left = Math.max(0, bin.free(st.stock));
                    b.append("  [left ").append(st.fmt(left)).append("]\n");
                }
            }
        }

        if (!c.note.isEmpty()) b.append("\nNote:\n").append(c.note);

        new AlertDialog.Builder(this)
                .setTitle(c.name + " \u2014 Details")
                .setMessage(b.toString())
                .setPositiveButton("Close", null)
                .setNeutralButton("Share", (d, w) -> shareCustomer(c))
                .show();
    }

    /** Share customer data as JSON for app-to-app transfer */
    private void shareCustomer(Customer c) {
        try {
            File jsonFile = Exporter.exportCustomerJson(this, c);
            Exporter.shareCustomerJson(this, jsonFile);
        } catch (Exception e) {
            toast("Share failed: " + e.getMessage());
        }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
