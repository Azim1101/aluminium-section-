package com.digitalalu.alu.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.digitalalu.alu.R;
import com.digitalalu.alu.calc.Engine;
import com.digitalalu.alu.calc.Settings;
import com.digitalalu.alu.calc.CustomFormulaManager;
import com.digitalalu.alu.model.WindowItem;

import java.util.List;

/** Spreadsheet grid — No | H | W | Sutter | RP | X */
public class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {

    public interface Listener {
        void onChanged();
        void onDelete(WindowItem it);
        void onOpen(WindowItem it);
        void onAutoAddRow();
    }

    private final List<WindowItem> items;
    private final Settings st;
    private final Listener lis;

    public RowAdapter(List<WindowItem> items, Settings st, Listener l) {
        this.items = items; this.st = st; this.lis = l;
        setHasStableIds(true);
    }

    @Override public long getItemId(int p) { return items.get(p).id; }
    @Override public int getItemCount() { return items.size(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_row, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(pos); }

    class VH extends RecyclerView.ViewHolder {
        TextView tvNo, tvSutter, tvSys, btnDel;
        EditText etH, etW, etRp;
        WindowItem cur;
        boolean binding = false;

        VH(View v) {
            super(v);
            tvNo = v.findViewById(R.id.tvNo);
            etH = v.findViewById(R.id.etH);
            etW = v.findViewById(R.id.etW);
            tvSutter = v.findViewById(R.id.tvSutter);
            tvSys = v.findViewById(R.id.tvSys);
            etRp = v.findViewById(R.id.etRp);
            btnDel = v.findViewById(R.id.btnDel);

            /* H change -> auto RP recalculate */
            etH.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
                public void onTextChanged(CharSequence c, int a, int b, int d) {}
                public void afterTextChanged(Editable s) {
                    if (binding || cur == null) return;
                    cur.h = num(s.toString());
                    cur.rpQty = 0;             // height badla -> auto par wapas
                    showAutoRp();
                    mark();
                    lis.onChanged();
                    checkAutoAddRow();
                }
            });

            etW.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
                public void onTextChanged(CharSequence c, int a, int b, int d) {}
                public void afterTextChanged(Editable s) {
                    if (binding || cur == null) return;
                    cur.w = num(s.toString());
                    mark();
                    lis.onChanged();
                    checkAutoAddRow();
                }
            });

            /* user RP override */
            etRp.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
                public void onTextChanged(CharSequence c, int a, int b, int d) {}
                public void afterTextChanged(Editable s) {
                    if (binding || cur == null) return;
                    int q = (int) num(s.toString());
                    cur.rpQty = Math.max(0, q);
                    lis.onChanged();
                }
            });

            tvSys.setOnClickListener(x -> {
                if (cur == null) return;
                PopupMenu popup = new PopupMenu(x.getContext(), x);
                List<CustomFormulaManager.CustomSystem> systems = CustomFormulaManager.getSystems(x.getContext());
                for (int i = 0; i < systems.size(); i++) {
                    popup.getMenu().add(0, i, i, systems.get(i).name);
                }
                popup.setOnMenuItemClickListener(item -> {
                    cur.system = item.getItemId();
                    cur.rpQty = 0;               // recalc auto RP for new system
                    paintSys();
                    showAutoRp();
                    mark();
                    lis.onChanged();
                    return true;
                });
                popup.show();
            });

            View.OnClickListener open = x -> { if (cur != null) lis.onOpen(cur); };
            tvSutter.setOnClickListener(open);
            tvNo.setOnClickListener(open);

            btnDel.setOnClickListener(x -> { if (cur != null) lis.onDelete(cur); });
        }

        private double num(String s) {
            try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
        }

        /** Dynamic cell colour + letter based on custom system names */
        private void paintSys() {
            if (cur == null) return;
            List<CustomFormulaManager.CustomSystem> systems = CustomFormulaManager.getSystems(tvSys.getContext());
            String text = "Z";
            int color = 0xFF3B82F6; // default blue
            if (cur.system >= 0 && cur.system < systems.size()) {
                String name = systems.get(cur.system).name;
                text = name.substring(0, Math.min(name.length(), 3)).toUpperCase();
                color = Engine.colorOf(name.toUpperCase() + "_FRAME");
            }
            tvSys.setText(text);

            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            gd.setColor(color);
            float density = tvSys.getContext().getResources().getDisplayMetrics().density;
            gd.setStroke((int)(0.7f * density), 0xFFD1D5DB);
            tvSys.setBackground(gd);
        }

        private void checkAutoAddRow() {
            if (cur == null) return;
            int position = getAdapterPosition();
            if (position == getItemCount() - 1) {
                if (cur.h > 0 || cur.w > 0) {
                    tvSys.post(() -> {
                        if (position == getItemCount() - 1) {
                            lis.onAutoAddRow();
                        }
                    });
                }
            }
        }

        /** auto RP from height */
        private void showAutoRp() {
            if (cur == null) return;
            Engine.WinResult r = Engine.calc(cur, st);
            binding = true;
            if (cur.isEmpty() || r.rpAutoQty <= 0) etRp.setText("");
            else etRp.setText(String.valueOf(r.rpAutoQty));
            binding = false;
        }

        void bind(int pos) {
            binding = true;
            cur = items.get(pos);
            tvNo.setText(String.valueOf(pos + 1));

            etH.setText(cur.h > 0 ? trim(cur.h) : "");
            etW.setText(cur.w > 0 ? trim(cur.w) : "");
            tvSutter.setText(String.valueOf(cur.sutter));
            paintSys();

            Engine.WinResult r = Engine.calc(cur, st);
            int show = cur.rpQty > 0 ? cur.rpQty : r.rpAutoQty;
            etRp.setText((cur.isEmpty() || show <= 0) ? "" : String.valueOf(show));

            binding = false;
            mark();
        }

        /** invalid size -> red cells */
        private void mark() {
            if (cur == null) return;
            int bg;
            if (cur.isEmpty()) bg = R.drawable.cell;
            else {
                Engine.WinResult r = Engine.calc(cur, st);
                bg = r.ok ? R.drawable.cell : R.drawable.cell_err;
            }
            etH.setBackgroundResource(bg);
            etW.setBackgroundResource(bg);
        }

        private String trim(double d) {
            if (d == Math.floor(d)) return String.valueOf((long) d);
            return String.valueOf(d);
        }
    }
}
