package com.digitalalu.alu;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.digitalalu.alu.calc.Costing;
import com.digitalalu.alu.calc.Engine;
import com.digitalalu.alu.calc.PriceBook;
import com.digitalalu.alu.model.WindowItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Price manager — view is open, editing needs PIN */
public class PriceActivity extends AppCompatActivity {

    private PriceBook pb;
    private boolean unlocked = false;
    private LinearLayout box;
    private TextView tvLock;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pb = PriceBook.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F6FB);

        /* ---- top bar ---- */
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundColor(0xFF2563EB);
        top.setPadding(dp(16), dp(12), dp(10), dp(12));
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = new TextView(this);
        t.setText("PRICE SYSTEM");
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setTypeface(null, Typeface.BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(t);

        tvLock = new TextView(this);
        tvLock.setText("\uD83D\uDD12  LOCKED");
        tvLock.setTextColor(0xFFFFD966);
        tvLock.setTextSize(12);
        tvLock.setTypeface(null, Typeface.BOLD);
        tvLock.setPadding(dp(10), dp(6), dp(10), dp(6));
        tvLock.setOnClickListener(v -> { if (!unlocked) askPin(); });
        top.addView(tvLock);
        root.addView(top);

        ScrollView sc = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(24));
        sc.addView(box);
        sc.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(sc);

        setContentView(root);
        render();
    }

    /* ================= PIN ================= */
    private void askPin() {
        View v = getLayoutInflater().inflate(R.layout.dlg_pin, null);
        EditText et = v.findViewById(R.id.etPin);
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Unlock price editing")
                .setView(v)
                .setPositiveButton("Unlock", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            if (et.getText().toString().trim().equals(pb.pin)) {
                unlocked = true;
                tvLock.setText("\uD83D\uDD13  UNLOCKED");
                tvLock.setTextColor(0xFF86EFAC);
                toast("Unlocked — you can edit now");
                render();
                d.dismiss();
            } else {
                et.setText("");
                et.setHint("wrong");
                toast("Wrong PIN");
            }
        });
    }

    /* ================= RENDER ================= */
    private void render() {
        box.removeAllViews();

        if (!unlocked) {
            LinearLayout n = card("VIEW ONLY");
            n.addView(note("Prices are shown below. Tap \uD83D\uDD12 LOCKED at top "
                    + "and enter the PIN to change anything."));
            box.addView((View) n.getParent());
        }

        /* ---- base rates ---- */
        LinearLayout r = card("BASE RATES");
        r.addView(rateRow("Aluminium", "Rs / kg", pb.aluRate, v -> {
            pb.aluRate = v; pb.save(this);
        }));
        r.addView(rateRow("Glass", "Rs / sq.ft", pb.glassRate, v -> {
            pb.glassRate = v; pb.save(this);
        }));
        box.addView((View) r.getParent());

        /* ---- ZED pipes ---- */
        LinearLayout z = card("ZED  \u2014  PIPE WEIGHT");
        z.addView(note("kg per 16 ft (192 inch) length"));
        for (String k : new String[]{"Z_FRAME", "Z_SUTTER", "Z_MULIYA", "Z_RP"})
            z.addView(kgRow(k));
        box.addView((View) z.getParent());

        /* ---- DOMAL pipes ---- */
        LinearLayout dm = card("DOMAL  \u2014  PIPE WEIGHT");
        dm.addView(note("kg per 16 ft (192 inch) length"));
        for (String k : new String[]{"D_FRAME", "D_SUTTER", "D_RT", "D_RP"})
            dm.addView(kgRow(k));
        box.addView((View) dm.getParent());

        /* ---- extras ---- */
        LinearLayout ex = card("EXTRA CHARGES");
        if (pb.extras.isEmpty()) ex.addView(note("No extra charges yet."));
        for (int i = 0; i < pb.extras.size(); i++) ex.addView(extraRow(i));
        TextView add = new TextView(this);
        add.setText("+   ADD EXTRA CHARGE");
        add.setTextSize(13);
        add.setTypeface(null, Typeface.BOLD);
        add.setTextColor(unlocked ? 0xFF2563EB : 0xFFB6C2D3);
        add.setGravity(Gravity.CENTER);
        add.setPadding(0, dp(12), 0, dp(6));
        add.setOnClickListener(v -> {
            if (!unlocked) { toast("Unlock first"); askPin(); return; }
            addExtraDialog();
        });
        ex.addView(add);
        box.addView((View) ex.getParent());

        /* ---- example ---- */
        LinearLayout eg = card("EXAMPLE");
        double w = 60.0 / PriceBook.INCH_16FT * pb.kgOf("Z_FRAME");
        eg.addView(note("ZED frame pipe, 60 inch:\n"
                + "  weight = 60 / 192 x " + trim(pb.kgOf("Z_FRAME")) + " = "
                + String.format(Locale.US, "%.3f", w) + " kg\n"
                + "  price  = " + String.format(Locale.US, "%.3f", w) + " x "
                + trim(pb.aluRate) + " = " + Costing.rs2(w * pb.aluRate)));
        box.addView((View) eg.getParent());

        /* ---- change pin ---- */
        if (unlocked) {
            LinearLayout p = card("SECURITY");
            TextView cp = new TextView(this);
            cp.setText("Change PIN");
            cp.setTextSize(13);
            cp.setTypeface(null, Typeface.BOLD);
            cp.setTextColor(0xFFDC2626);
            cp.setPadding(0, dp(8), 0, dp(8));
            cp.setOnClickListener(v -> changePin());
            p.addView(cp);
            box.addView((View) p.getParent());
        }
    }

    /* ================= ROWS ================= */
    private interface OnVal { void set(double v); }

    private View rateRow(String name, String unit, double val, OnVal cb) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(5), 0, dp(5));

        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView a = new TextView(this);
        a.setText(name); a.setTextSize(13); a.setTypeface(null, Typeface.BOLD);
        a.setTextColor(0xFF152236);
        TextView b = new TextView(this);
        b.setText(unit); b.setTextSize(10.5f); b.setTextColor(0xFF66758C);
        tx.addView(a); tx.addView(b);
        l.addView(tx);

        EditText e = mkInput(val);
        e.setEnabled(unlocked);
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int i, int j, int k) {}
            public void onTextChanged(CharSequence c, int i, int j, int k) {}
            public void afterTextChanged(Editable s) {
                try { cb.set(Double.parseDouble(s.toString().trim())); } catch (Exception ignored) {}
            }
        });
        l.addView(e);
        return l;
    }

    private View kgRow(String type) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(4), 0, dp(4));

        TextView chip = new TextView(this);
        chip.setText(Engine.shortName(type));
        chip.setTextSize(10);
        chip.setTypeface(null, Typeface.BOLD);
        chip.setTextColor(Color.WHITE);
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.getBackground().setTint(Engine.colorOf(type));
        chip.setPadding(dp(9), dp(3), dp(9), dp(3));
        LinearLayout w = new LinearLayout(this);
        w.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        w.addView(chip);
        l.addView(w);

        EditText e = mkInput(pb.kgOf(type));
        e.setEnabled(unlocked);
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int i, int j, int k) {}
            public void onTextChanged(CharSequence c, int i, int j, int k) {}
            public void afterTextChanged(Editable s) {
                try {
                    pb.kg.put(type, Double.parseDouble(s.toString().trim()));
                    pb.save(PriceActivity.this);
                } catch (Exception ignored) {}
            }
        });
        l.addView(e);

        TextView u = new TextView(this);
        u.setText("  kg/16ft");
        u.setTextSize(10);
        u.setTextColor(0xFF66758C);
        l.addView(u);
        return l;
    }

    private View extraRow(int idx) {
        PriceBook.Extra x = pb.extras.get(idx);
        View v = getLayoutInflater().inflate(R.layout.item_extra, null);
        ((TextView) v.findViewById(R.id.tvXName)).setText(x.name);
        ((TextView) v.findViewById(R.id.tvXInfo))
                .setText(PriceBook.Extra.basisName(x.basis) + "   \u00b7   " + x.systemName());
        ((TextView) v.findViewById(R.id.tvXRate)).setText(Costing.rs(x.rate));
        TextView del = v.findViewById(R.id.btnXDel);
        del.setVisibility(unlocked ? View.VISIBLE : View.GONE);
        del.setOnClickListener(q -> {
            pb.extras.remove(idx);
            pb.save(this);
            render();
        });
        return v;
    }

    private void addExtraDialog() {
        View v = getLayoutInflater().inflate(R.layout.dlg_extra, null);
        EditText nm = v.findViewById(R.id.xName);
        EditText rt = v.findViewById(R.id.xRate);
        Spinner sb = v.findViewById(R.id.xBasis);
        Spinner ss = v.findViewById(R.id.xSys);

        List<String> basis = new ArrayList<>();
        basis.add("per sutter");
        basis.add("per window");
        basis.add("per sq.ft");
        basis.add("per RP pipe");
        sb.setAdapter(spin(basis));

        List<String> sys = new ArrayList<>();
        sys.add("BOTH");
        sys.add("ZED only");
        sys.add("DOMAL only");
        ss.setAdapter(spin(sys));

        new AlertDialog.Builder(this)
                .setTitle("Add extra charge")
                .setView(v)
                .setPositiveButton("Add", (d, w) -> {
                    String name = nm.getText().toString().trim();
                    if (name.isEmpty()) name = "Extra";
                    double rate = 0;
                    try { rate = Double.parseDouble(rt.getText().toString().trim()); }
                    catch (Exception ignored) {}
                    int sysV = ss.getSelectedItemPosition() == 0 ? -1
                            : (ss.getSelectedItemPosition() == 1 ? WindowItem.ZED : WindowItem.DOMAL);
                    pb.extras.add(new PriceBook.Extra(name,
                            sb.getSelectedItemPosition(), rate, sysV));
                    pb.save(this);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void changePin() {
        View v = getLayoutInflater().inflate(R.layout.dlg_pin, null);
        EditText et = v.findViewById(R.id.etPin);
        et.setHint("new PIN");
        new AlertDialog.Builder(this)
                .setTitle("Set new PIN")
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    String s = et.getText().toString().trim();
                    if (s.length() < 4) { toast("PIN must be at least 4 digits"); return; }
                    pb.pin = s;
                    pb.save(this);
                    toast("PIN changed");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* ================= helpers ================= */
    private ArrayAdapter<String> spin(List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private EditText mkInput(double val) {
        EditText e = new EditText(this);
        e.setText(trim(val));
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setBackgroundResource(R.drawable.bg_input);
        e.setGravity(Gravity.CENTER);
        e.setTextSize(14);
        e.setTypeface(null, Typeface.BOLD);
        e.setPadding(dp(4), dp(6), dp(4), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(92), dp(40));
        e.setLayoutParams(lp);
        return e;
    }

    private String trim(double d) {
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    private LinearLayout card(String title) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundResource(R.drawable.bg_card);
        outer.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        outer.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(12);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(0xFF2563EB);
        t.setLetterSpacing(0.06f);
        t.setPadding(0, 0, 0, dp(8));
        outer.addView(t);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        outer.addView(body);
        return body;
    }

    private View note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(11.5f);
        t.setTextColor(0xFF66758C);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(0, dp(2), 0, dp(6));
        return t;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
