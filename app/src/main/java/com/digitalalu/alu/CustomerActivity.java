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

import com.digitalalu.alu.model.Customer;

import java.util.List;

/** Saved customer estimates — PIN locked */
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

            v.setOnClickListener(x -> showNote(c));
            box.addView(v);
        }
    }

    private void showNote(Customer c) {
        StringBuilder b = new StringBuilder();
        b.append("Mobile : ").append(c.mobile.isEmpty() ? "-" : c.mobile).append("\n");
        b.append("Village: ").append(c.village.isEmpty() ? "-" : c.village).append("\n");
        b.append("Saved  : ").append(c.dateStr()).append("\n");
        b.append("Windows: ").append(c.windows.size()).append("\n");
        if (!c.note.isEmpty()) b.append("\nNote:\n").append(c.note);
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setMessage(b.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
