package com.digitalalu.alu;

import android.content.Context;
import android.content.SharedPreferences;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.digitalalu.alu.calc.Engine;
import com.digitalalu.alu.calc.Settings;
import com.digitalalu.alu.calc.CustomFormulaManager;
import com.digitalalu.alu.calc.Costing;
import com.digitalalu.alu.calc.PriceBook;
import com.digitalalu.alu.export.Exporter;
import com.digitalalu.alu.calc.BackupManager;
import com.digitalalu.alu.model.Customer;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import com.digitalalu.alu.model.WindowItem;
import com.digitalalu.alu.ui.InsetsHelper;
import com.digitalalu.alu.ui.PipeBarView;
import com.digitalalu.alu.ui.RpRulerView;
import com.digitalalu.alu.ui.RowAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.viewpager2.widget.ViewPager2;
import android.widget.ImageView;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private final List<WindowItem> items = new ArrayList<>();
    private Settings st;
    private long seq = 0;

    private ViewPager2 pager;
    private LinearLayout dots;
    private TextView tvTitle;
    private final ImageView[] navIcons = new ImageView[3];
    private final TextView[] navTexts = new TextView[3];
    private static final String[] PAGE_TITLES = {"Windows", "Estimate", "Cutting Plan"};
    private RecyclerView rv;
    private RowAdapter adapter;
    private View sheetView;
    private TextView tvSub;
    private PriceBook pb;
    private boolean showPrice = false;
    private Costing.Total cost;
    private static final int REQ_CUSTOMER = 101;
    private static final int REQ_BACKUP_EXPORT = 201;
    private static final int REQ_BACKUP_IMPORT = 202;
    private ScrollView scResult, scPlan;
    private LinearLayout boxResult, boxPlan;

    private static final String PREF = "alu_data";
    public static final String EXTRA_PAGE = "page";

    /* cached calculation */
    private List<Engine.WinResult> res = new ArrayList<>();
    private Map<String, Engine.TypeSummary> sum;
    private Engine.Grand grand;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        st = Settings.load(this);
        CustomFormulaManager.activeSystems = CustomFormulaManager.getSystems(this);
        pb = PriceBook.load(this);
        InsetsHelper.apply(((ViewGroup) findViewById(android.R.id.content)).getChildAt(0),
                findViewById(R.id.topBar));
        pager = findViewById(R.id.pager);
        dots = findViewById(R.id.dots);
        tvSub = findViewById(R.id.tvSub);
        tvTitle = findViewById(R.id.tvTitle);

        buildViews();
        loadData();
        // v2.9: start loading the on-device AI now (background), so the agent
        // screen is ready the moment the user opens it.
        try {
            com.digitalalu.alu.ai.LocalModelHost.get().ensureLoaded(this,
                    com.digitalalu.alu.ai.AiSettings.load(this));
        } catch (Throwable ignored) { }
        if (items.isEmpty()) addWindow(0, 0, 2, 1);

        /* ---- swipe pages (Windows / Estimate / Cutting) ---- */
        pager.setOffscreenPageLimit(2);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { showTab(position); }
        });

        /* ---- iOS style bottom tab bar ---- */
        int[] navIds  = {R.id.navWindows, R.id.navEstimate, R.id.navCutting};
        int[] iconIds = {R.id.navWindowsIcon, R.id.navEstimateIcon, R.id.navCuttingIcon};
        int[] textIds = {R.id.navWindowsText, R.id.navEstimateText, R.id.navCuttingText};
        for (int i = 0; i < 3; i++) {
            final int page = i;
            navIcons[i] = findViewById(iconIds[i]);
            navTexts[i] = findViewById(textIds[i]);
            findViewById(navIds[i]).setOnClickListener(v -> selectPage(page));
        }
        buildDots();

        findViewById(R.id.btnShare).setOnClickListener(v -> showShareSheet());
        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettings());
        findViewById(R.id.btnPrice).setOnClickListener(v -> togglePrice());
        findViewById(R.id.btnPrice).setOnLongClickListener(v -> {
            startActivity(new Intent(this, PriceActivity.class));
            return true;
        });
        findViewById(R.id.btnProfile).setOnClickListener(v ->
                startActivityForResult(new Intent(this, CustomerActivity.class), REQ_CUSTOMER));
        findViewById(R.id.btnAiAgent).setOnClickListener(v ->
                startActivity(new Intent(this, AgentActivity.class)));
        findViewById(R.id.btnMore).setOnClickListener(this::showMenu);

        recalc();
        showTab(0);
        handlePageIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handlePageIntent(intent);
    }

    /** Web Chat / agent can ask for a specific page: 0 windows, 1 estimate, 2 cutting. */
    private void handlePageIntent(Intent i) {
        try {
            if (i == null || !i.hasExtra(EXTRA_PAGE)) return;
            final int page = Math.max(0, Math.min(2, i.getIntExtra(EXTRA_PAGE, 0)));
            i.removeExtra(EXTRA_PAGE);
            pager.post(() -> { items.clear(); loadData(); recalc(); if (adapter != null) adapter.notifyDataSetChanged(); selectPage(page); });
        } catch (Throwable ignored) { }
    }

    /* ================= NAVIGATION (pager + bottom bar) ================= */
    private void selectPage(int i) {
        if (pager.getCurrentItem() == i) showTab(i);
        else pager.setCurrentItem(i, true);
    }

    private void buildDots() {
        dots.removeAllViews();
        for (int i = 0; i < 3; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(6), dp(6));
            lp.setMargins(dp(3), 0, dp(3), 0);
            d.setLayoutParams(lp);
            d.setBackgroundResource(R.drawable.dot_off);
            dots.addView(d);
        }
    }

    private void paintNav(int sel) {
        int on = ContextCompat.getColor(this, R.color.acc);
        int off = ContextCompat.getColor(this, R.color.mut);
        for (int i = 0; i < 3; i++) {
            boolean s = i == sel;
            if (navIcons[i] != null) {
                navIcons[i].setImageTintList(ColorStateList.valueOf(s ? on : off));
                navIcons[i].setBackgroundResource(s ? R.drawable.bg_nav_pill : 0);
            }
            if (navTexts[i] != null) navTexts[i].setTextColor(s ? on : off);
            View d = dots.getChildAt(i);
            if (d != null) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
                lp.width = dp(s ? 18 : 6);
                d.setLayoutParams(lp);
                d.setBackgroundResource(s ? R.drawable.dot_on : R.drawable.dot_off);
            }
        }
        // AI / More are action buttons — always muted
        ImageView ai = findViewById(R.id.navAiIcon), more = findViewById(R.id.navMoreIcon);
        if (ai != null) ai.setImageTintList(ColorStateList.valueOf(off));
        if (more != null) more.setImageTintList(ColorStateList.valueOf(off));
        if (tvTitle != null) tvTitle.setText(PAGE_TITLES[sel]);
    }

    /** Pre-built page views hosted in ViewPager2 (no fragments needed). */
    private static final class PageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final View[] pages;
        PageAdapter(View... pages) { this.pages = pages; }
        @Override public int getItemViewType(int position) { return position; }
        @Override public int getItemCount() { return pages.length; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = pages[viewType];
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            v.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new RecyclerView.ViewHolder(v) {};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {}
    }

    /* ================= SHARE SHEET ================= */
    private void showShareSheet() {
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.sheet_share, null);
        v.findViewById(R.id.btnWhats).setOnClickListener(x -> { d.dismiss(); shareWhatsApp(); });
        v.findViewById(R.id.btnExcel).setOnClickListener(x -> { d.dismiss(); exportExcel(); });
        v.findViewById(R.id.btnCutImg).setOnClickListener(x -> { d.dismiss(); shareAllCuttingImages(); });
        v.findViewById(R.id.btnBom).setOnClickListener(x -> { d.dismiss(); shareBom(); });
        d.setContentView(v);
        d.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        items.clear();
        loadData();
        if (items.isEmpty()) addWindow(0, 0, 2, 1);
        pb = PriceBook.load(this);
        recalc();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_CUSTOMER && res == RESULT_OK && data != null) {
            long id = data.getLongExtra(CustomerActivity.EXTRA_LOAD_ID, -1);
            loadCustomer(id);
        } else if (req == REQ_BACKUP_EXPORT && res == RESULT_OK
                && data != null && data.getData() != null) {
            writeBackup(data.getData());
        } else if (req == REQ_BACKUP_IMPORT && res == RESULT_OK
                && data != null && data.getData() != null) {
            confirmRestore(data.getData());
        }
    }

    /* ================= VIEWS ================= */
    private void buildViews() {
        /* ---- TAB 1 : excel jaisa sheet ---- */
        sheetView = getLayoutInflater().inflate(R.layout.frag_sheet, pager, false);
        rv = sheetView.findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(this));
        sheetView.findViewById(R.id.btnAddRow).setOnClickListener(v -> addNextRow());

        // FAB - optimize button
        View fab = sheetView.findViewById(R.id.fabOptimize);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                recalc();
                // Switch to CUTTING page to show results
                selectPage(2);
                toast("⚡ Best Fit optimization applied!");
            });
        }

        adapter = new RowAdapter(items, st, new RowAdapter.Listener() {
            public void onChanged() { recalc(); saveData(); }
            public void onDelete(WindowItem it) { deleteWindow(it); }
            public void onOpen(WindowItem it) { showRowDialog(it); }
            public void onAutoAddRow() { addNextRow(true); }
        });
        rv.setAdapter(adapter);
        int p = dp(10);

        scResult = new ScrollView(this);
        boxResult = new LinearLayout(this);
        boxResult.setOrientation(LinearLayout.VERTICAL);
        boxResult.setPadding(p, p, p, dp(20));
        scResult.addView(boxResult);

        scPlan = new ScrollView(this);
        boxPlan = new LinearLayout(this);
        boxPlan.setOrientation(LinearLayout.VERTICAL);
        boxPlan.setPadding(p, p, p, dp(20));
        scPlan.addView(boxPlan);

        scResult.setClipToPadding(false);
        scPlan.setClipToPadding(false);
        pager.setAdapter(new PageAdapter(sheetView, scResult, scPlan));
    }

    private int curPage = 0;

    private void showTab(int i) {
        curPage = i;
        paintNav(i);
        if (i == 1) renderEstimate();
        if (i == 2) renderPlan();
    }

    /* ================= DATA ================= */
    private void addWindow(double h, double w, int sutter, int nos) {
        addWindow(h, w, sutter, nos, WindowItem.ZED);
    }

    private void addWindow(double h, double w, int sutter, int nos, int sys) {
        seq++;
        items.add(new WindowItem(seq, "W" + seq, sys, h, w, sutter, nos));
        adapter.notifyDataSetChanged();
        rv.scrollToPosition(items.size() - 1);
        recalc(); saveData();
    }

    private void addNextRow() {
        addNextRow(false);
    }

    /**
     * @param quiet true when this row appears automatically while the user is
     *              still typing in the previous one. Then we only announce the
     *              insertion — a full rebind would move the caret to position 0
     *              and typing "2" then "5" would produce "52".
     */
    private void addNextRow(boolean quiet) {
        int sutter = 2, sys = WindowItem.ZED;
        if (!items.isEmpty()) {
            WindowItem last = items.get(items.size() - 1);
            sutter = last.sutter;
            sys = last.system;
        }
        if (quiet && adapter != null) {
            seq++;
            items.add(new WindowItem(seq, "W" + seq, sys, 0, 0, sutter, 1));
            adapter.notifyItemAppended();
            int last = items.size() - 1;
            if (!rowVisible(last)) rv.scrollToPosition(last);
            recalc(); saveData();
        } else {
            addWindow(0, 0, sutter, 1, sys);
        }
    }

    /** Scroll only when the row is off-screen, so typing never jumps the sheet. */
    private boolean rowVisible(int pos) {
        RecyclerView.LayoutManager lm = rv == null ? null : rv.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return false;
        LinearLayoutManager llm = (LinearLayoutManager) lm;
        return pos >= llm.findFirstVisibleItemPosition() && pos <= llm.findLastVisibleItemPosition();
    }

    /** row tap -> full detail dialog */
    private void showRowDialog(WindowItem it) {
        View v = getLayoutInflater().inflate(R.layout.dlg_row, null);
        EditText dH = v.findViewById(R.id.dH), dW = v.findViewById(R.id.dW);
        EditText dNos = v.findViewById(R.id.dNos), dName = v.findViewById(R.id.dName);
        android.widget.Spinner dSutter = v.findViewById(R.id.dSutter);
        android.widget.Spinner dRp = v.findViewById(R.id.dRp);
        TextView dResult = v.findViewById(R.id.dResult);

        dH.setText(trimNum(it.h));
        dW.setText(trimNum(it.w));
        dNos.setText(String.valueOf(it.nos));
        dName.setText(it.name);

        List<String> sq = new ArrayList<>();
        for (int i = 1; i <= 6; i++) sq.add(i + " Sutter");
        android.widget.ArrayAdapter<String> sa = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, sq);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dSutter.setAdapter(sa);
        dSutter.setSelection(Math.max(0, Math.min(5, it.sutter - 1)));

        final WindowItem tmp = new WindowItem(it.id, it.name, it.system, it.h, it.w, it.sutter, it.nos);
        tmp.rpQty = it.rpQty;

        final Runnable refresh = new Runnable() {
            @Override public void run() {
                Engine.WinResult r = Engine.calc(tmp, st);

                List<String> rp = new ArrayList<>();
                int sel = 0;
                rp.add(r.rpAutoQty > 0
                        ? "AUTO  \u2192  " + r.rpAutoQty + " pcs"
                        : "AUTO  \u2192  none");
                for (int i = 0; i < r.rpOpts.size(); i++) {
                    Engine.RpOpt o = r.rpOpts.get(i);
                    rp.add(o.qty + " pcs  @  " + st.fmt(o.space) + st.unit());
                    if (tmp.rpQty > 0 && o.qty == tmp.rpQty) sel = i + 1;
                }
                if (tmp.rpQty > 0 && sel == 0) {
                    rp.add(tmp.rpQty + " pcs  (manual)");
                    sel = rp.size() - 1;
                }
                android.widget.ArrayAdapter<String> ra = new android.widget.ArrayAdapter<>(
                        MainActivity.this, android.R.layout.simple_spinner_item, rp);
                ra.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                Object tag = dRp.getTag();
                dRp.setTag("lock");
                dRp.setAdapter(ra);
                dRp.setSelection(sel);
                dRp.setTag(tag);

                if (!r.ok) { dResult.setText("Frame size too small"); return; }
                StringBuilder b = new StringBuilder();
                b.append("SUTTER  ").append(st.fmt(r.sutterH)).append(" x ").append(st.fmt(r.sutterW))
                 .append("   x").append(r.q * r.nos).append("\n");
                if (r.muliyaQ > 0)
                    b.append(String.format("%-7s ", r.muliyaLabel())).append(st.fmt(r.muliyaH))
                     .append("   x").append(r.muliyaQ * r.nos).append("\n");
                if (r.rpQty > 0)
                    b.append("RP      ").append(st.fmt(r.rpLen))
                     .append("   x").append(r.rpQty * r.nos)
                     .append(r.rpAuto ? "  (auto)" : "  (manual)").append("\n");
                b.append("GLASS   ").append(st.fmt(r.glassH)).append(" x ").append(st.fmt(r.glassW))
                 .append("\n        ").append(String.format(Locale.US, "%.2f",
                        r.glassSqft() * r.glassQty())).append(" sq.ft");
                dResult.setText(b.toString());
            }
        };

        android.text.TextWatcher tw = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            public void onTextChanged(CharSequence c, int a, int b, int d) {}
            public void afterTextChanged(android.text.Editable e) {
                tmp.h = pd(dH.getText().toString(), tmp.h);
                tmp.w = pd(dW.getText().toString(), tmp.w);
                tmp.nos = Math.max(1, (int) pd(dNos.getText().toString(), tmp.nos));
                refresh.run();
            }
        };
        dH.addTextChangedListener(tw);
        dW.addTextChangedListener(tw);
        dNos.addTextChangedListener(tw);

        dSutter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View vv, int i, long id) {
                tmp.sutter = i + 1; tmp.rpQty = 0; refresh.run();
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        dRp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View vv, int i, long id) {
                if ("lock".equals(dRp.getTag())) return;
                Engine.WinResult r = Engine.calc(tmp, st);
                if (i == 0) { tmp.rpQty = 0; refresh.run(); }
                else if (i - 1 < r.rpOpts.size()) {
                    tmp.rpQty = r.rpOpts.get(i - 1).qty; refresh.run();
                }
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        refresh.run();

        new AlertDialog.Builder(this)
                .setTitle(WindowItem.systemName(it.system) + "  \u00b7  Row " + (items.indexOf(it) + 1))
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    it.h = tmp.h; it.w = tmp.w; it.sutter = tmp.sutter;
                    it.nos = tmp.nos; it.rpQty = tmp.rpQty;
                    String nm = dName.getText().toString().trim();
                    if (!nm.isEmpty()) it.name = nm;
                    adapter.notifyDataSetChanged();
                    recalc(); saveData();
                })
                .setNeutralButton("Duplicate", (d, w) -> addWindow(tmp.h, tmp.w, tmp.sutter, tmp.nos))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private double pd(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
    private String trimNum(double d) {
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    private void deleteWindow(WindowItem it) {
        int i = items.indexOf(it);
        if (i < 0) return;
        items.remove(i);
        adapter.notifyDataSetChanged();
        recalc(); saveData();
    }

    private void recalc() {
        res = Engine.calcAll(items, st);
        sum = Engine.summarize(res, st);
        grand = Engine.grand(res, sum, st);
        cost = Costing.all(res, pb, st);
        tvSub.setText(grand.windows + " windows  \u2022  " + grand.pcs + " pcs  \u2022  "
                + grand.stockPipes + " pipes");
        if (curPage == 1) renderEstimate();
        if (curPage == 2) renderPlan();
    }

    private void saveData() {
        try {
            JSONArray a = new JSONArray();
            for (WindowItem it : items) a.put(it.toJson());
            getSharedPreferences(PREF, MODE_PRIVATE).edit()
                    .putString("windows", a.toString())
                    .putLong("seq", seq).apply();
        } catch (Exception ignored) {}
    }

    private void loadData() {
        try {
            SharedPreferences p = getSharedPreferences(PREF, MODE_PRIVATE);
            String s = p.getString("windows", null);
            seq = p.getLong("seq", 0);
            if (s == null) return;
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++)
                items.add(WindowItem.fromJson(a.getJSONObject(i)));
            adapter.notifyDataSetChanged();
        } catch (Exception ignored) {}
    }

    /* ================= TAB 2 : ESTIMATE ================= */
    private void renderEstimate() {
        boxResult.removeAllViews();
        if (items.isEmpty()) { boxResult.addView(hint("No windows yet — tap ADD ROW")); return; }

        Card g = card("SUMMARY");
        g.body.addView(kv("Total windows", grand.windows + "   (ZED " + grand.zedWindows
                + "  |  DOMAL " + grand.domalWindows + ")"));
        g.body.addView(kv("Total pieces", String.valueOf(grand.pcs)));
        g.body.addView(kv("Total material", st.fmtU(grand.totalLen)));
        g.body.addView(kv("Stock pipe length", st.fmtU(st.stock)));
        g.body.addView(bigKv("STOCK PIPE REQUIRED", grand.stockPipes + " pcs", com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.ok)));
        if (grand.zedPipes > 0 && grand.domalPipes > 0)
            g.body.addView(kv("   ZED / DOMAL", grand.zedPipes + " / " + grand.domalPipes));
        g.body.addView(kv("Waste", st.fmtU(grand.waste) + "  ("
                + String.format(Locale.US, "%.1f", grand.wastePct()) + "%)"));
        g.body.addView(kv("Usage", String.format(Locale.US, "%.1f", grand.usePct()) + "%"));
        if (grand.glassSqft > 0)
            g.body.addView(kv("Glass total", String.format(Locale.US, "%.2f", grand.glassSqft) + " sq.ft"));
        boxResult.addView(g.outer);

        if (showPrice && cost != null && cost.total > 0) {
            Card pc = card("\u20B9  PRICE ESTIMATE");
            pc.body.addView(rowHead("ITEM", "QTY", "KG", "AMOUNT"));
            for (java.util.Map.Entry<String, double[]> e : cost.byType.entrySet()) {
                double[] a = e.getValue();
                pc.body.addView(rowType(Engine.shortName(e.getKey()),
                        Engine.colorOf(e.getKey()),
                        String.valueOf((int) a[0]),
                        String.format(Locale.US, "%.2f", a[2]),
                        Costing.rs(a[3])));
            }
            pc.body.addView(divider());
            pc.body.addView(kv("Aluminium  (" + String.format(Locale.US, "%.2f", cost.weight)
                    + " kg @ " + Costing.rs(pb.aluRate) + "/kg)", Costing.rs(cost.pipeAmt)));
            if (cost.glassAmt > 0)
                pc.body.addView(kv("Glass  (" + String.format(Locale.US, "%.2f", grand.glassSqft)
                        + " sq.ft @ " + Costing.rs(pb.glassRate) + ")", Costing.rs(cost.glassAmt)));
            if (cost.extraAmt > 0)
                pc.body.addView(kv("Extra charges", Costing.rs(cost.extraAmt)));
            if (cost.zedAmt > 0 && cost.domalAmt > 0) {
                pc.body.addView(divider());
                pc.body.addView(kv("ZED total", Costing.rs(cost.zedAmt)));
                pc.body.addView(kv("DOMAL total", Costing.rs(cost.domalAmt)));
            }
            pc.body.addView(bigKv("GRAND TOTAL", Costing.rs(cost.total), com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.ok)));
            boxResult.addView(pc.outer);
        }

        java.util.Map<String, Engine.TypeSummary> zed =
                Engine.filterSystem(sum, WindowItem.ZED);
        java.util.Map<String, Engine.TypeSummary> dom =
                Engine.filterSystem(sum, WindowItem.DOMAL);

        if (!zed.isEmpty()) boxResult.addView(pipeCard("ZED  —  PIPE REQUIREMENT", zed,
                grand.zedPipes, com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.acc)));
        if (!dom.isEmpty()) boxResult.addView(pipeCard("DOMAL  —  PIPE REQUIREMENT", dom,
                grand.domalPipes, com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.cFrame)));

        for (Engine.WinResult r : res) {
            if (r.empty) continue;
            Card c = card(WindowItem.systemName(r.system) + "  \u00b7  " + r.src.name + "   "
                    + st.fmt(r.H) + " x " + st.fmt(r.W) + st.unit()
                    + "   |   " + r.q + " sutter"
                    + (r.nos > 1 ? "  x" + r.nos : ""));
            if (!r.ok) {
                c.body.addView(hint("Frame size too small"));
                boxResult.addView(c.outer);
                continue;
            }
            c.body.addView(kvBold("SUTTER", st.fmt(r.sutterH) + "  x  " + st.fmt(r.sutterW),
                    r.system == WindowItem.DOMAL ? com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.cSutter) : com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.ok)));
            if (r.muliyaQ > 0)
                c.body.addView(kvBold(r.muliyaLabel(), st.fmt(r.muliyaH) + "   x" + r.muliyaQ + " pcs",
                        com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.cMuliya)));
            if (r.rpQty > 0)
                c.body.addView(kvBold("RP GRILL", r.rpQty + " pcs @ " + st.fmt(r.rpSpace)
                        + "  |  len " + st.fmt(r.rpLen)
                        + (r.rpAuto ? "   (auto)" : "   (manual)"),
                        com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.cRp)));
            c.body.addView(kvBold("GLASS", st.fmt(r.glassH) + "  x  " + st.fmt(r.glassW)
                    + "   (" + String.format(Locale.US, "%.2f", r.glassSqft() * r.glassQty())
                    + " sq.ft)", com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.cFrame)));

            if (showPrice) {
                Costing.WinCost wc = Costing.window(r, pb, st);
                if (wc.total > 0)
                    c.body.addView(bigKv("PRICE  (" + String.format(Locale.US, "%.2f", wc.weight)
                            + " kg)", Costing.rs(wc.total), com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.ok)));
            }

            c.body.addView(divider());
            c.body.addView(smallNote("FRAME = 2x" + st.fmt(r.H) + " + 2x" + st.fmt(r.W)
                    + " = " + st.fmt(r.frameLen)));

            c.body.addView(rowHead("SECTION", "SIZE", "PCS", "TOTAL"));
            for (Engine.Part p : r.parts) {
                c.body.addView(rowType(Engine.shortName(p.type), Engine.colorOf(p.type),
                        st.fmt(p.len), String.valueOf(p.pcs), st.fmt(p.len * p.pcs)));
            }

            if (r.rpQty > 0) {
                c.body.addView(divider());
                c.body.addView(smallNote("RP MARKING   (" + r.muliyaLabel() + " = " + st.fmt(r.muliyaH) + ")"));
                RpRulerView ruler = new RpRulerView(this);
                ruler.setData(Engine.rpPoints(r.muliyaH, st, r.rpQty), r.muliyaH,
                        Engine.rpGapIndex(r.rpQty), st);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(4);
                ruler.setLayoutParams(lp);
                c.body.addView(ruler);

                double[] pts = Engine.rpPoints(r.muliyaH, st, r.rpQty);
                StringBuilder mk = new StringBuilder();
                int gi = Engine.rpGapIndex(r.rpQty);
                for (int i = 0; i < pts.length; i++) {
                    if (i > 0) mk.append(i == gi ? "   |GAP|   " : " , ");
                    mk.append(st.fmt(pts[i]));
                }
                c.body.addView(smallNote(mk.toString()));
            }
            boxResult.addView(c.outer);
        }
    }

    private View pipeCard(String title, java.util.Map<String, Engine.TypeSummary> map,
                          int totalPipes, int accent) {
        Card c = card(title);
        c.body.addView(rowHead("TYPE", "PCS", "LENGTH", "PIPE"));
        int pcs = 0; double len = 0;
        for (Engine.TypeSummary ts : map.values()) {
            pcs += ts.pcs; len += ts.totalLen;
            c.body.addView(rowType(Engine.shortName(ts.type), Engine.colorOf(ts.type),
                    String.valueOf(ts.pcs), st.fmt(ts.totalLen),
                    String.valueOf(ts.stockNeeded())));
        }
        c.body.addView(divider());
        c.body.addView(rowHead2("TOTAL", String.valueOf(pcs), st.fmt(len),
                String.valueOf(totalPipes)));
        return c.outer;
    }

    /* ================= TAB 3 : CUTTING PLAN ================= */
    private void renderPlan() {
        boxPlan.removeAllViews();
        if (items.isEmpty() || sum == null) { boxPlan.addView(hint("No windows yet — tap ADD ROW")); return; }

        // Header card with overall stats
        Card headerCard = card("BEST FIT CUTTING PLAN");
        headerCard.body.addView(kv("Algorithm", "Best Fit Decreasing (BFD)"));
        headerCard.body.addView(kv("Total pipes", grand.stockPipes + " pcs"));
        headerCard.body.addView(kv("Usage", String.format(Locale.US, "%.1f%%", grand.usePct())));
        headerCard.body.addView(kv("Waste", String.format(Locale.US, "%.1f%%", grand.wastePct())
                + "  (" + st.fmtU(grand.waste) + ")"));

        TextView btnManual = new TextView(this);
        btnManual.setText("✂  OPEN MANUAL PCO & SHEET CUTTING");
        btnManual.setTextSize(12.5f);
        btnManual.setTypeface(null, Typeface.BOLD);
        btnManual.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.ok));
        btnManual.setGravity(Gravity.CENTER);
        btnManual.setPadding(0, dp(9), 0, dp(4));
        btnManual.setOnClickListener(v -> startActivity(new Intent(this, ManualCuttingActivity.class)));
        headerCard.body.addView(btnManual);

        // Share all cutting images button
        TextView btnShareAll = new TextView(this);
        btnShareAll.setText("\uD83D\uDCF7  SHARE ALL CUTTING IMAGES");
        btnShareAll.setTextSize(13);
        btnShareAll.setTypeface(null, Typeface.BOLD);
        btnShareAll.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.acc));
        btnShareAll.setGravity(Gravity.CENTER);
        btnShareAll.setPadding(0, dp(10), 0, dp(4));
        btnShareAll.setOnClickListener(v -> shareAllCuttingImages());
        headerCard.body.addView(btnShareAll);
        boxPlan.addView(headerCard.outer);

        // Editable stock pipes — har section ka apna stock length + bache hue pipe
        boxPlan.addView(stockPipesCard());

        for (Engine.TypeSummary ts : sum.values()) {
            if (ts.bins == null || ts.bins.isEmpty()) continue;
            int col = Engine.colorOf(ts.type);
            Card c = card(Engine.nameOf(ts.type) + "   \u2014   "
                    + ts.stockNeeded() + " pipes");
            c.body.addView(smallNote(ts.pcs + " pcs  \u2022  need " + st.fmtU(ts.totalLen)
                    + "  \u2022  waste " + st.fmtU(ts.waste())));

            // Share individual cutting image button
            final Engine.TypeSummary thisType = ts;
            final String thisTypeKey = ts.type;
            TextView btnShare = new TextView(this);
            btnShareAll.setText("\uD83D\uDCF7  SHARE ALL CUTTING IMAGES");
            btnShare.setText("\uD83D\uDDBC  Share " + Engine.shortName(ts.type) + " image");
            btnShare.setTextSize(11);
            btnShare.setTypeface(null, Typeface.BOLD);
            btnShare.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.acc));
            btnShare.setPadding(0, dp(6), 0, dp(2));
            btnShare.setOnClickListener(v -> shareCuttingImage(thisTypeKey, thisType));
            c.body.addView(btnShare);

            for (int i = 0; i < ts.bins.size(); i++) {
                Engine.Bin b = ts.bins.get(i);
                double barLen = b.length(st.stock);

                TextView lab = new TextView(this);
                lab.setText("Pipe #" + (i + 1) + "   " + st.fmt(barLen) + st.unit()
                        + (b.over ? "  (OVERSIZE)" : (Math.abs(barLen - st.primaryStockFor(ts.type)) > 0.01 ? "  (extra)" : ""))
                        + "   used " + st.fmt(b.used)
                        + "   \u2022   left " + st.fmt(Math.max(0, barLen - b.used)));
                lab.setTextSize(10.5f);
                lab.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.mut));
                LinearLayout.LayoutParams lp0 = new LinearLayout.LayoutParams(-1, -2);
                lp0.topMargin = dp(9);
                lp0.bottomMargin = dp(3);
                lab.setLayoutParams(lp0);
                c.body.addView(lab);

                PipeBarView bar = new PipeBarView(this);
                bar.setData(b, barLen, col, st);
                bar.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                c.body.addView(bar);
            }
            boxPlan.addView(c.outer);
        }
    }

    /* ================= SECTION-WISE STOCK PIPES (EDITABLE) ================= */

    /** Card in the cutting plan listing the stock length of every section. */
    private View stockPipesCard() {
        Card c = card("STOCK PIPES   \u2014   tap to edit");

        LinkedHashSet<String> types = new LinkedHashSet<>();
        if (sum != null) types.addAll(sum.keySet());
        types.addAll(st.stockBars.keySet());

        if (types.isEmpty()) {
            c.body.addView(smallNote("Add a window first, then set the pipe size of each section."));
        }

        int i = 0;
        for (final String type : types) {
            StringBuilder v = new StringBuilder();
            v.append(st.fmt(st.primaryStockFor(type))).append(st.unit());
            List<Engine.StockBar> extras = st.extrasFor(type);
            if (!extras.isEmpty()) {
                v.append("   +");
                for (int k = 0; k < extras.size(); k++) {
                    if (k > 0) v.append(", ");
                    v.append(st.fmt(extras.get(k).len)).append(" x").append(extras.get(k).qty);
                }
            }
            c.body.addView(stockRow(Engine.nameOf(type), Engine.colorOf(type), v.toString(),
                    new View.OnClickListener() {
                        @Override public void onClick(View x) { showStockBarsDialog(type); }
                    }));
            if (++i < types.size()) c.body.addView(divider());
        }

        TextView add = new TextView(this);
        add.setText("+  ADD ANOTHER PIPE / LEFTOVER");
        add.setTextSize(12.5f);
        add.setTypeface(null, Typeface.BOLD);
        add.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.ok));
        add.setGravity(Gravity.CENTER);
        add.setPadding(0, dp(10), 0, dp(2));
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAddStockDialog(); }
        });
        c.body.addView(add);
        c.body.addView(smallNote("Frame, sutter, muliya \u2014 sabka stock pipe alag ho sakta hai. "
                + "Bacha hua pipe add karein to plan usse pehle use karega (kam waste)."));
        return c.outer;
    }

    private View stockRow(String name, int col, String value, View.OnClickListener click) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(7), 0, dp(7));

        TextView a = new TextView(this);
        a.setText(name);
        a.setTextSize(12f);
        a.setTypeface(null, Typeface.BOLD);
        a.setTextColor(col);
        a.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView b = new TextView(this);
        b.setText(value);
        b.setTextSize(12.5f);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.txt));

        TextView e = new TextView(this);
        e.setText("  \u270F");
        e.setTextSize(15f);
        e.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.acc));

        l.addView(a);
        l.addView(b);
        l.addView(e);
        l.setOnClickListener(click);
        return l;
    }

    /** Edit one section: main stock length + leftover pipes. */
    private void showStockBarsDialog(final String type) {
        // work on a copy; commit only on SAVE
        final List<Engine.StockBar> work = st.barsFor(type);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(6), dp(16), 0);

        box.addView(dlgLabel("MAIN STOCK PIPE  (" + st.unit() + ")"));
        final EditText mainLen = numEdit(String.valueOf(st.primaryStockFor(type)));
        box.addView(mainLen);

        box.addView(dlgLabel("BACHA HUA / EXTRA PIPE"));
        final LinearLayout extras = new LinearLayout(this);
        extras.setOrientation(LinearLayout.VERTICAL);
        box.addView(extras);

        final Runnable[] paint = new Runnable[1];
        paint[0] = new Runnable() {
            @Override public void run() {
                extras.removeAllViews();
                boolean any = false;
                for (final Engine.StockBar bar : work) {
                    if (bar.unlimited()) continue;
                    any = true;
                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(3), 0, dp(3));
                    TextView t = new TextView(MainActivity.this);
                    t.setText(st.fmt(bar.len) + st.unit() + "   \u00d7 " + bar.qty + " pcs");
                    t.setTextSize(13f);
                    t.setTypeface(null, Typeface.BOLD);
                    t.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.txt));
                    t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView del = new TextView(MainActivity.this);
                    del.setText("REMOVE");
                    del.setTextSize(11f);
                    del.setTypeface(null, Typeface.BOLD);
                    del.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.red));
                    del.setPadding(dp(8), dp(4), 0, dp(4));
                    del.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { work.remove(bar); paint[0].run(); }
                    });
                    row.addView(t);
                    row.addView(del);
                    extras.addView(row);
                }
                if (!any) extras.addView(dlgNote("Koi extra pipe nahi \u2014 neeche se add karein."));
            }
        };
        paint[0].run();

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        final EditText eLen = numEdit("");
        eLen.setHint("length");
        eLen.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        final EditText eQty = numEdit("");
        eQty.setHint("qty");
        eQty.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 0.7f));
        TextView addBtn = new TextView(this);
        addBtn.setText("  ADD  ");
        addBtn.setTextSize(12f);
        addBtn.setTypeface(null, Typeface.BOLD);
        addBtn.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.ok));
        addBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                double len = d(eLen, 0);
                int qty = (int) Math.max(1, Math.round(d(eQty, 1)));
                if (len <= 0) { toast("Length daalein"); return; }
                work.add(new Engine.StockBar(len, qty));
                eLen.setText("");
                eQty.setText("");
                paint[0].run();
            }
        });
        addRow.addView(eLen);
        addRow.addView(eQty);
        addRow.addView(addBtn);
        box.addView(addRow);

        new AlertDialog.Builder(this)
                .setTitle(Engine.nameOf(type))
                .setView(box)
                .setPositiveButton("SAVE", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        double len = d(mainLen, st.primaryStockFor(type));
                        if (len > 0) {
                            boolean set = false;
                            for (Engine.StockBar b : work) {
                                if (b.unlimited()) { b.len = len; set = true; break; }
                            }
                            if (!set) work.add(0, new Engine.StockBar(len, -1));
                        }
                        List<Engine.StockBar> clean = new ArrayList<>();
                        for (Engine.StockBar b : work) if (b.len > 0) clean.add(b);
                        if (clean.isEmpty()) st.stockBars.remove(type);
                        else st.stockBars.put(type, clean);
                        applyStockChanges();
                    }
                })
                .setNeutralButton("RESET", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        st.stockBars.remove(type);
                        applyStockChanges();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Add a leftover / another pipe size to any section. */
    private void showAddStockDialog() {
        final List<String> types = Engine.getTypes();
        final String[] names = new String[types.size()];
        for (int i = 0; i < types.size(); i++) names[i] = Engine.nameOf(types.get(i));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(6), dp(16), 0);

        box.addView(dlgLabel("SECTION"));
        final android.widget.Spinner sp = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> sa = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, names);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(sa);
        box.addView(sp);

        box.addView(dlgLabel("PIPE LENGTH  (" + st.unit() + ")"));
        final EditText eLen = numEdit("");
        box.addView(eLen);

        box.addView(dlgLabel("KITNE PIPE HAIN  (qty)"));
        final EditText eQty = numEdit("1");
        box.addView(eQty);

        new AlertDialog.Builder(this)
                .setTitle("Add pipe / leftover")
                .setView(box)
                .setPositiveButton("ADD AS EXTRA", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        double len = d(eLen, 0);
                        int qty = (int) Math.max(1, Math.round(d(eQty, 1)));
                        if (len <= 0) { toast("Length daalein"); return; }
                        String type = types.get(Math.max(0, sp.getSelectedItemPosition()));
                        st.addExtraBar(type, len, qty);
                        applyStockChanges();
                        toast(Engine.nameOf(type) + " \u2014 " + st.fmt(len) + st.unit() + " x" + qty + " added");
                    }
                })
                .setNeutralButton("SET AS MAIN", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        double len = d(eLen, 0);
                        if (len <= 0) { toast("Length daalein"); return; }
                        String type = types.get(Math.max(0, sp.getSelectedItemPosition()));
                        st.setPrimaryStock(type, len);
                        applyStockChanges();
                        toast(Engine.nameOf(type) + " main stock = " + st.fmt(len) + st.unit());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyStockChanges() {
        st.save(this);
        recalc();
        if (scPlan.getVisibility() == View.VISIBLE) renderPlan();
        if (scResult.getVisibility() == View.VISIBLE) renderEstimate();
    }

    private View dlgLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(10.5f);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.mut));
        t.setPadding(0, dp(8), 0, dp(2));
        return t;
    }

    private View dlgNote(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(11.5f);
        t.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.mut));
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private EditText numEdit(String value) {
        EditText e = new EditText(this);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setText(value);
        e.setSelectAllOnFocus(true);
        e.setSingleLine(true);
        return e;
    }

    /* ================= CUTTING IMAGE SHARE ================= */
    private void shareCuttingImage(String type, Engine.TypeSummary ts) {
        File img = Exporter.exportCuttingImage(this, type, ts, st);
        if (img != null) {
            Exporter.shareCuttingImage(this, img);
        } else {
            toast("Could not generate image");
        }
    }

    private void shareAllCuttingImages() {
        // Generate all images then share the first (user can share more from share sheet)
        List<File> files = new ArrayList<>();
        for (Map.Entry<String, Engine.TypeSummary> e : sum.entrySet()) {
            Engine.TypeSummary ts = e.getValue();
            if (ts.bins == null || ts.bins.isEmpty()) continue;
            File img = Exporter.exportCuttingImage(this, e.getKey(), ts, st);
            if (img != null) files.add(img);
        }
        if (files.isEmpty()) {
            toast("No cutting data to share");
            return;
        }
        // Share all as multiple images
        try {
            ArrayList<android.net.Uri> uris = new ArrayList<>();
            for (File f : files) {
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        this, getPackageName() + ".fileprovider", f);
                uris.add(uri);
            }
            Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.setType("image/png");
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share Cutting Plans"));
        } catch (Exception e) {
            // Fallback: share first one
            Exporter.shareCuttingImage(this, files.get(0));
        }
    }

    /* ================= PRICE ================= */
    private void togglePrice() {
        showPrice = !showPrice;
        pb = PriceBook.load(this);
        toast(showPrice ? "Prices ON" : "Prices hidden");
        recalc();
        if (showPrice && curPage == 0) selectPage(1);
    }

    /* ================= CUSTOMER ================= */
    private void saveToCustomer() {
        if (items.isEmpty()) { toast("Nothing to save"); return; }
        View v = getLayoutInflater().inflate(R.layout.dlg_customer, null);
        EditText cn = v.findViewById(R.id.cName), cm = v.findViewById(R.id.cMobile);
        EditText cv = v.findViewById(R.id.cVillage), co = v.findViewById(R.id.cNote);

        new AlertDialog.Builder(this)
                .setTitle("Save estimate")
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    String name = cn.getText().toString().trim();
                    if (name.isEmpty()) { toast("Enter customer name"); return; }
                    java.util.List<Customer> all = Customer.loadAll(this);
                    Customer c = new Customer(System.currentTimeMillis());
                    c.name = name;
                    c.mobile = cm.getText().toString().trim();
                    c.village = cv.getText().toString().trim();
                    c.note = co.getText().toString().trim();
                    for (WindowItem it : items)
                        if (!it.isEmpty()) c.windows.add(it);
                    all.add(0, c);
                    Customer.saveAll(this, all);
                    toast("Saved for " + name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadCustomer(long id) {
        for (Customer c : Customer.loadAll(this)) {
            if (c.id != id) continue;
            items.clear();
            long mx = 0;
            for (WindowItem w : c.windows) { items.add(w); mx = Math.max(mx, w.id); }
            seq = Math.max(seq, mx);
            adapter.notifyDataSetChanged();
            recalc(); saveData();
            toast("Loaded: " + c.name);
            return;
        }
    }

    /* ================= SHARE ================= */
    private void shareWhatsApp() {
        if (items.isEmpty()) { toast("Add a window first"); return; }
        String txt = Exporter.buildText(res, st, sum, grand);
        Exporter.shareWhatsApp(this, txt);
    }

    /** v1.0 — supplier order list (BOM): quantities only, no prices. */
    private void shareBom() {
        if (items.isEmpty()) { toast("Add a window first"); return; }
        com.digitalalu.alu.calc.BomBuilder.Bom bom =
                com.digitalalu.alu.calc.BomBuilder.build(res, sum, st);
        if (bom.totalPipes <= 0) { toast("Pehle window ke size bharo"); return; }
        bom.bizName = st != null ? st.bizName : "";
        String txt = new com.digitalalu.alu.calc.BomBuilder().toText(bom, true);
        Exporter.shareWhatsApp(this, txt);
    }

    private void exportExcel() {
        if (items.isEmpty()) { toast("Add a window first"); return; }
        try {
            File f = Exporter.exportExcel(this, items, res, st, sum, grand);
            new AlertDialog.Builder(this)
                    .setTitle("Excel created")
                    .setMessage(f.getName() + "\n\n6 sheets: Summary, Windows, Cutting List, "
                            + "Cutting Plan, RP Marking, Glass")
                    .setPositiveButton("Share", (d, w) ->
                            Exporter.shareFile(this, f, "Share Excel"))
                    .setNegativeButton("Close", null)
                    .show();
        } catch (Exception e) {
            toast("Error: " + e.getMessage());
        }
    }

    /* ================= MENU ================= */
    private void showMenu(View anchor) {
        final BottomSheetDialog d = new BottomSheetDialog(this);
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_sheet);
        box.setPadding(dp(20), dp(10), dp(20), dp(24));
        sc.addView(box);

        View handle = new View(this);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(40), dp(4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        handle.setLayoutParams(hl);
        handle.setBackgroundResource(R.drawable.bg_sheet_handle);
        box.addView(handle);

        TextView title = new TextView(this);
        title.setText("More");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.txt));
        title.setPadding(0, dp(14), 0, dp(12));
        box.addView(title);

        String[][] rows = {
                {"\uD83E\uDD16", "AI Agent (Chat)", "Hinglish me baat karke window add karein"},
                {"\uD83D\uDC64", "My Profile", "Business name, address, mobile"},
                {"\uD83D\uDCBE", "Save to customer", "Current estimate ko customer me save karein"},
                {"\uD83D\uDCC7", "Customer records", "Saved estimates (PIN)"},
                {"\u20B9", "Price system", "Rates & kg per 16 ft (PIN to edit)"},
                {"\uD83D\uDCCF", "Manual PCO & Sheet Cutting", "Standalone pipe / sheet optimizer"},
                {"\u2211", "Formula", "Custom system formulas"},
                {"\u2601\uFE0F", "Backup data", "Sab kuch ek JSON file me"},
                {"\u267B\uFE0F", "Restore data", "Backup file se wapas laayein"},
                {"\uD83D\uDDD1\uFE0F", "Clear all", "Saari windows hata dein"},
                {"\u2139\uFE0F", "About", "Version & info"},
        };
        for (String[] r : rows) {
            View row = getLayoutInflater().inflate(R.layout.sheet_menu_row, box, false);
            ((TextView) row.findViewById(R.id.rowIcon)).setText(r[0]);
            ((TextView) row.findViewById(R.id.rowTitle)).setText(r[1]);
            ((TextView) row.findViewById(R.id.rowSub)).setText(r[2]);
            final String t = r[1];
            row.setOnClickListener(v -> { d.dismiss(); onMenuAction(t); });
            box.addView(row);
        }
        d.setContentView(sc);
        d.show();
    }

    private void onMenuAction(String t) {
        if (t.contains("AI Agent")) startActivity(new Intent(this, AgentActivity.class));
        else if (t.contains("My Profile")) startActivity(new Intent(this, UserProfileActivity.class));
        else if (t.startsWith("Save to")) saveToCustomer();
        else if (t.startsWith("Customer")) startActivityForResult(
                new Intent(this, CustomerActivity.class), REQ_CUSTOMER);
        else if (t.startsWith("Price")) startActivity(new Intent(this, PriceActivity.class));
        else if (t.startsWith("Manual PCO")) startActivity(new Intent(this, ManualCuttingActivity.class));
        else if (t.startsWith("Backup")) backupData();
        else if (t.startsWith("Restore")) restoreData();
        else if (t.startsWith("Clear")) confirmClear();
        else if (t.startsWith("Formula")) showFormula();
        else showAbout();
    }

    /* ================= BACKUP / RESTORE ================= */
    private void backupData() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "alu-window-backup.json");
        startActivityForResult(i, REQ_BACKUP_EXPORT);
    }

    private void restoreData() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "application/octet-stream", "text/plain"});
        startActivityForResult(i, REQ_BACKUP_IMPORT);
    }

    private void writeBackup(Uri uri) {
        try {
            JSONObject doc = BackupManager.exportAll(this);
            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(doc.toString(2).getBytes("UTF-8"));
            os.close();
            toast("Backup saved \u2714  (keep this file safe)");
        } catch (Exception e) {
            toast("Backup failed: " + e.getMessage());
        }
    }

    private void confirmRestore(final Uri uri) {
        final JSONObject doc;
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
            doc = new JSONObject(bo.toString("UTF-8"));
            BackupManager.validate(doc);
        } catch (Exception e) {
            toast("Not a valid ALU backup file");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Restore backup?")
                .setMessage("Backup date: " + doc.optString("date", "?")
                        + "\n\nCurrent data (prices, customers, settings, formulas) "
                        + "will be REPLACED by this backup. Continue?")
                .setPositiveButton("Restore", (d, w) -> {
                    try {
                        BackupManager.Result r = BackupManager.importAll(this, doc);
                        CustomFormulaManager.invalidateCache();
                        toast("Restored " + r.keyCount + " values — restarting app");
                        Intent i = new Intent(this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    } catch (Exception e) {
                        toast("Restore failed: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear all?")
                .setMessage("All windows will be deleted.")
                .setPositiveButton("Yes", (d, w) -> {
                    items.clear(); seq = 0;
                    adapter.notifyDataSetChanged();
                    addWindow(48, 48, 2, 1);
                })
                .setNegativeButton("No", null).show();
    }

    private void showFormula() {
        StringBuilder sb = new StringBuilder();
        sb.append("FRAME  =  2xH + 2xW\n\n");
        List<CustomFormulaManager.CustomSystem> systems = CustomFormulaManager.getSystems(this);
        for (CustomFormulaManager.CustomSystem cs : systems) {
            sb.append("\u2500\u2500 ").append(cs.name).append(" \u2500\u2500\n");
            sb.append("SUTTER H = ").append(cs.sutterH_formula).append("\n");
            sb.append("SUTTER W = ").append(cs.sutterW_formula).append("\n");
            if (!cs.muliyaH_formula.isEmpty()) {
                sb.append(cs.muliyaLabel).append(" H = ").append(cs.muliyaH_formula).append("\n");
            }
            sb.append("RP length = ").append(cs.rpLen_formula).append("\n");
            sb.append("GLASS H = ").append(cs.glassH_formula).append("\n");
            sb.append("GLASS W = ").append(cs.glassW_formula).append("\n\n");
        }
        sb.append("\u2500\u2500 RP GRILL \u2500\u2500\n");
        sb.append("space = ( mid pipe - ").append(st.rpGap).append(" ) / qty\n");
        sb.append("valid range ").append(st.rpMin).append("\" to ").append(st.rpMax).append("\"\n");
        sb.append("auto qty shown in RP column \u2014 editable");

        new AlertDialog.Builder(this)
                .setTitle("Formula Manager")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("Import Custom", (dialog, which) -> showImportFormulaDialog())
                .show();
    }

    private void showImportFormulaDialog() {
        final EditText et = new EditText(this);
        et.setHint("Paste your custom formulas here...\n\nExample:\nMySliding\nSUTTER H = H - 2.0\nSUTTER W = (W + 6) / q\nGLASS = sutter - 4.25");
        et.setMinLines(12);
        et.setGravity(Gravity.TOP | Gravity.LEFT);

        int pDp = dp(14);
        et.setPadding(pDp, pDp, pDp, pDp);

        StringBuilder curText = new StringBuilder();
        List<CustomFormulaManager.CustomSystem> systems = CustomFormulaManager.getSystems(this);
        for (CustomFormulaManager.CustomSystem cs : systems) {
            curText.append(cs.name).append("\n");
            curText.append("SUTTER H = ").append(cs.sutterH_formula).append("\n");
            curText.append("SUTTER W = ").append(cs.sutterW_formula).append("\n");
            if (!cs.muliyaH_formula.isEmpty()) {
                curText.append(cs.muliyaLabel).append(" H = ").append(cs.muliyaH_formula).append("\n");
            }
            curText.append("RP length = ").append(cs.rpLen_formula).append("\n");
            if (cs.glassH_formula.replace("sutterH", "sutter").equals(cs.glassW_formula.replace("sutterW", "sutter"))) {
                curText.append("GLASS = ").append(cs.glassH_formula.replace("sutterH", "sutter")).append("\n");
            } else {
                curText.append("GlassH = ").append(cs.glassH_formula).append("\n");
                curText.append("GlassW = ").append(cs.glassW_formula).append("\n");
            }
            curText.append("\n");
        }
        et.setText(curText.toString().trim());

        ScrollView sv = new ScrollView(this);
        sv.addView(et);

        new AlertDialog.Builder(this)
                .setTitle("Import Custom Formula")
                .setView(sv)
                .setPositiveButton("Save & Apply", (dialog, which) -> {
                    String raw = et.getText().toString().trim();
                    if (raw.isEmpty()) return;
                    try {
                        List<CustomFormulaManager.CustomSystem> parsed = CustomFormulaManager.parseFormulaText(raw);
                        CustomFormulaManager.saveSystems(this, parsed);
                        CustomFormulaManager.activeSystems = parsed;
                        recalc();
                        adapter.notifyDataSetChanged();
                        saveData();
                        Toast.makeText(this, "Custom formulas imported and applied successfully!", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        new AlertDialog.Builder(this)
                                .setTitle("Parsing Error")
                                .setMessage(e.getMessage())
                                .setPositiveButton("OK", (dialog2, which2) -> showImportFormulaDialog())
                                .show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAbout() {
        String bizInfo = st.bizHeader();
        String footer = st.bizFooter();
        String about = "Native Android app\nVersion 1.6\n\n"
                + "Best Fit Decreasing (BFD)\npipe cutting optimization\n\n";
        if (!footer.isEmpty()) {
            about += footer + "\n\n";
        } else {
            about += "Set your business info in\nSettings to customize.\n";
        }
        new AlertDialog.Builder(this).setTitle(bizInfo)
                .setMessage(about)
                .setPositiveButton("OK", null).show();
    }

    /* ================= SETTINGS ================= */
    private void showSettings() {
        View v = getLayoutInflater().inflate(R.layout.dlg_settings, null);
        RadioButton rbIn = v.findViewById(R.id.rbIn), rbMm = v.findViewById(R.id.rbMm);
        EditText sStock = v.findViewById(R.id.sStock), sKerf = v.findViewById(R.id.sKerf);
        EditText zDw = v.findViewById(R.id.zDw), zDh = v.findViewById(R.id.zDh);
        EditText zDm = v.findViewById(R.id.zDm), zDg = v.findViewById(R.id.zDg);
        EditText zRpDed = v.findViewById(R.id.zRpDed);
        EditText dDw = v.findViewById(R.id.dDw), dDh = v.findViewById(R.id.dDh);
        EditText dDm = v.findViewById(R.id.dDm), dDg = v.findViewById(R.id.dDg);
        EditText dRpDed = v.findViewById(R.id.dRpDed);
        EditText sRpGap = v.findViewById(R.id.sRpGap), sRpMin = v.findViewById(R.id.sRpMin);
        EditText sRpMax = v.findViewById(R.id.sRpMax);
        CheckBox cbRp = v.findViewById(R.id.cbRp);

        // Business info fields
        EditText sBizName = v.findViewById(R.id.sBizName);
        EditText sBizAddr = v.findViewById(R.id.sBizAddr);
        EditText sBizMobile = v.findViewById(R.id.sBizMobile);

        if (st.mm) rbMm.setChecked(true); else rbIn.setChecked(true);
        sStock.setText(String.valueOf(st.stock));
        sKerf.setText(String.valueOf(st.kerf));

        zDw.setText(String.valueOf(st.z_dw));
        zDh.setText(String.valueOf(st.z_dh));
        zDm.setText(String.valueOf(st.z_dm));
        zDg.setText(String.valueOf(st.z_dg));
        zRpDed.setText(String.valueOf(st.z_rpDed));

        dDw.setText(String.valueOf(st.d_dw));
        dDh.setText(String.valueOf(st.d_dh));
        dDm.setText(String.valueOf(st.d_dm));
        dDg.setText(String.valueOf(st.d_dg));
        dRpDed.setText(String.valueOf(st.d_rpDed));

        sRpGap.setText(String.valueOf(st.rpGap));
        sRpMin.setText(String.valueOf(st.rpMin));
        sRpMax.setText(String.valueOf(st.rpMax));
        cbRp.setChecked(st.useRp);

        // Business info
        sBizName.setText(st.bizName);
        sBizAddr.setText(st.bizAddress);
        sBizMobile.setText(st.bizMobile);

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(v)
                .setPositiveButton("Save", (dd, w) -> {
                    st.mm = rbMm.isChecked();
                    st.stock = d(sStock, st.stock);
                    st.kerf = d(sKerf, st.kerf);
                    st.z_dw = d(zDw, st.z_dw);
                    st.z_dh = d(zDh, st.z_dh);
                    st.z_dm = d(zDm, st.z_dm);
                    st.z_dg = d(zDg, st.z_dg);
                    st.z_rpDed = d(zRpDed, st.z_rpDed);
                    st.d_dw = d(dDw, st.d_dw);
                    st.d_dh = d(dDh, st.d_dh);
                    st.d_dm = d(dDm, st.d_dm);
                    st.d_dg = d(dDg, st.d_dg);
                    st.d_rpDed = d(dRpDed, st.d_rpDed);
                    st.rpGap = d(sRpGap, st.rpGap);
                    st.rpMin = d(sRpMin, st.rpMin);
                    st.rpMax = d(sRpMax, st.rpMax);
                    st.useRp = cbRp.isChecked();

                    // Save business info
                    st.bizName = sBizName.getText().toString().trim();
                    st.bizAddress = sBizAddr.getText().toString().trim();
                    st.bizMobile = sBizMobile.getText().toString().trim();

                    st.save(this);
                    adapter.notifyDataSetChanged();
                    recalc();
                    toast("Settings saved");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private double d(EditText e, double def) {
        try { return Double.parseDouble(e.getText().toString().trim()); }
        catch (Exception ex) { return def; }
    }

    /* ================= UI HELPERS ================= */
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private static class Card {
        LinearLayout outer, body;
        Card(LinearLayout o, LinearLayout b) { outer = o; body = b; }
    }

    private Card card(String title) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundResource(R.drawable.bg_card);
        // One UI: 18dp card padding, 12dp gap between cards
        outer.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        outer.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(title);
        // One UI card title: 14.5sp bold ink (pehle 12sp accent kicker tha)
        t.setTextSize(14.5f);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.txt));
        t.setLetterSpacing(-0.01f);
        t.setPadding(0, 0, 0, dp(12));
        outer.addView(t);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        outer.addView(body);
        return new Card(outer, body);
    }

    private View kv(String k, String v) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(0, dp(4), 0, dp(4));
        TextView a = new TextView(this);
        a.setText(k); a.setTextSize(12.5f); a.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.mut));
        a.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView b = new TextView(this);
        b.setText(v); b.setTextSize(13f); b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.txt));
        l.addView(a); l.addView(b);
        return l;
    }

    private View bigKv(String k, String v, int col) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackgroundResource(R.drawable.bg_stat);
        l.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6); lp.bottomMargin = dp(6);
        l.setLayoutParams(lp);
        TextView a = new TextView(this);
        a.setText(k); a.setTextSize(11f); a.setTypeface(null, Typeface.BOLD);
        a.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.ok));
        a.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView b = new TextView(this);
        b.setText(v); b.setTextSize(19f); b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(col);
        l.addView(a); l.addView(b);
        return l;
    }

    private View kvBold(String k, String v, int col) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(3), 0, dp(3));
        TextView a = new TextView(this);
        a.setText(k); a.setTextSize(10f); a.setTypeface(null, Typeface.BOLD);
        a.setTextColor(Color.WHITE);
        a.setBackgroundResource(R.drawable.bg_chip);
        a.getBackground().setTint(col);
        a.setPadding(dp(8), dp(3), dp(8), dp(3));
        TextView b = new TextView(this);
        b.setText("  " + v); b.setTextSize(13.5f); b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.txt));
        l.addView(a); l.addView(b);
        return l;
    }

    private View rowHead(String a, String b, String c, String d) {
        return row(a, b, c, d, com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.mut), 10f, true, 0);
    }
    private View rowHead2(String a, String b, String c, String d) {
        return row(a, b, c, d, com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.warn), 12.5f, true, 0);
    }

    private View rowType(String name, int col, String b, String c, String d) {
        LinearLayout l = (LinearLayout) row(name, b, c, d, com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.txt), 12f, false, col);
        return l;
    }

    private View row(String a, String b, String c, String d, int txtCol,
                     float size, boolean bold, int chipCol) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(5), 0, dp(5));

        TextView t1 = new TextView(this);
        t1.setText(a); t1.setTextSize(size); t1.setTextColor(txtCol);
        if (bold) t1.setTypeface(null, Typeface.BOLD);
        if (chipCol != 0) {
            t1.setTextColor(Color.WHITE);
            t1.setTextSize(9.5f);
            t1.setTypeface(null, Typeface.BOLD);
            t1.setBackgroundResource(R.drawable.bg_chip);
            t1.getBackground().setTint(chipCol);
            t1.setPadding(dp(7), dp(2), dp(7), dp(2));
            LinearLayout w = new LinearLayout(this);
            w.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.5f));
            w.addView(t1);
            l.addView(w);
        } else {
            t1.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.5f));
            l.addView(t1);
        }

        String[] rest = {b, c, d};
        for (String s : rest) {
            TextView t = new TextView(this);
            t.setText(s); t.setTextSize(size); t.setTextColor(txtCol);
            t.setGravity(Gravity.END);
            if (bold) t.setTypeface(null, Typeface.BOLD);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            l.addView(t);
        }
        return l;
    }

    private View divider() {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(7); lp.bottomMargin = dp(7);
        v.setLayoutParams(lp);
        v.setBackgroundColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.line));
        return v;
    }

    private View smallNote(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(11f); t.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.mut));
        t.setPadding(0, dp(2), 0, dp(4));
        return t;
    }

    private View hint(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(13f); t.setTextColor(com.digitalalu.alu.ui.V2.col(MainActivity.this, com.digitalalu.alu.R.color.mut));
        t.setPadding(dp(6), dp(20), dp(6), dp(20));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
