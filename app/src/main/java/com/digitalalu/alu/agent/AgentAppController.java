package com.digitalalu.alu.agent;

import android.content.Context;
import android.content.SharedPreferences;

import com.digitalalu.alu.calc.Costing;
import com.digitalalu.alu.calc.CustomFormulaManager;
import com.digitalalu.alu.calc.Engine;
import com.digitalalu.alu.calc.ManualCuttingEngine;
import com.digitalalu.alu.calc.PriceBook;
import com.digitalalu.alu.calc.Settings;
import com.digitalalu.alu.model.Customer;
import com.digitalalu.alu.model.WindowItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full application controller for AI Agent.
 * Gives the agent direct read/write control over:
 * - Window entries sheet
 * - Calculations & BFD cutting optimization
 * - Price book & costing
 * - Customer records
 * - Manual PCO cutting
 */
public class AgentAppController {

    private static final String PREF_DATA = "alu_data";
    private final Context context;

    public AgentAppController(Context context) {
        this.context = context.getApplicationContext();
        if (CustomFormulaManager.activeSystems == null) {
            CustomFormulaManager.activeSystems = CustomFormulaManager.getSystems(context);
        }
    }

    public Context getContext() {
        return context;
    }

    public Settings getSettings() {
        return Settings.load(context);
    }

    public PriceBook getPriceBook() {
        return PriceBook.load(context);
    }

    /* ================= WINDOWS / SHEET CONTROL ================= */

    public synchronized List<WindowItem> loadWindows() {
        List<WindowItem> list = new ArrayList<>();
        try {
            SharedPreferences p = context.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE);
            String s = p.getString("windows", null);
            if (s != null) {
                JSONArray a = new JSONArray(s);
                for (int i = 0; i < a.length(); i++) {
                    list.add(WindowItem.fromJson(a.getJSONObject(i)));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public synchronized void saveWindows(List<WindowItem> items) {
        try {
            long maxSeq = 0;
            JSONArray a = new JSONArray();
            for (WindowItem it : items) {
                a.put(it.toJson());
                if (it.id > maxSeq) maxSeq = it.id;
            }
            context.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit()
                    .putString("windows", a.toString())
                    .putLong("seq", maxSeq)
                    .apply();
        } catch (Exception ignored) {}
    }

    public synchronized WindowItem addWindow(double h, double w, int sutter, int nos, int sys, String customName) {
        List<WindowItem> items = loadWindows();
        SharedPreferences p = context.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE);
        long seq = p.getLong("seq", 0) + 1;
        String name = (customName != null && !customName.trim().isEmpty())
                ? customName.trim()
                : "W" + seq;

        WindowItem item = new WindowItem(seq, name, sys, h, w, sutter, nos);
        items.add(item);
        saveWindows(items);
        return item;
    }

    public synchronized boolean deleteLastWindow() {
        List<WindowItem> items = loadWindows();
        if (items.isEmpty()) return false;
        items.remove(items.size() - 1);
        saveWindows(items);
        return true;
    }

    public synchronized boolean deleteWindowByIndex(int index) {
        List<WindowItem> items = loadWindows();
        if (index < 0 || index >= items.size()) return false;
        items.remove(index);
        saveWindows(items);
        return true;
    }

    public synchronized int clearAllWindows() {
        List<WindowItem> items = loadWindows();
        int count = items.size();
        items.clear();
        saveWindows(items);
        return count;
    }

    /* ================= CALCULATION & ESTIMATE ================= */

    public static class AppCalculationState {
        public List<WindowItem> items;
        public List<Engine.WinResult> results;
        public Map<String, Engine.TypeSummary> summaries;
        public Engine.Grand grand;
        public Costing.Total cost;
        public Settings settings;
        public PriceBook priceBook;
    }

    public AppCalculationState getLiveState() {
        AppCalculationState state = new AppCalculationState();
        state.settings = getSettings();
        state.priceBook = getPriceBook();
        state.items = loadWindows();
        state.results = Engine.calcAll(state.items, state.settings);
        state.summaries = Engine.summarize(state.results, state.settings);
        state.grand = Engine.grand(state.results, state.summaries, state.settings);
        state.cost = Costing.all(state.results, state.priceBook, state.settings);
        return state;
    }

    /** Compute single window hypothetical result without altering sheet */
    public Engine.WinResult calculateWindow(double h, double w, int sutter, int nos, int sys) {
        Settings st = getSettings();
        WindowItem item = new WindowItem(1, "Hypo", sys, h, w, sutter, nos);
        return Engine.calc(item, st);
    }

    /* ================= PRICE BOOK MANAGEMENT ================= */

    public synchronized void updateAluRate(double rate) {
        PriceBook pb = getPriceBook();
        pb.aluRate = rate;
        pb.save(context);
    }

    public synchronized void updateGlassRate(double rate) {
        PriceBook pb = getPriceBook();
        pb.glassRate = rate;
        pb.save(context);
    }

    /* ================= CUSTOMERS MANAGEMENT ================= */

    public synchronized List<Customer> getCustomers() {
        return Customer.loadAll(context);
    }

    public synchronized Customer saveCurrentSheetToCustomer(String name, String mobile, String village, String note) {
        List<WindowItem> items = loadWindows();
        List<Customer> all = Customer.loadAll(context);
        Customer c = new Customer(System.currentTimeMillis());
        c.name = (name == null || name.trim().isEmpty()) ? "Client " + (all.size() + 1) : name.trim();
        c.mobile = (mobile == null) ? "" : mobile.trim();
        c.village = (village == null) ? "" : village.trim();
        c.note = (note == null) ? "" : note.trim();

        for (WindowItem it : items) {
            if (!it.isEmpty()) c.windows.add(it);
        }
        all.add(0, c);
        Customer.saveAll(context, all);
        return c;
    }

    public synchronized boolean loadCustomerToSheet(long customerId) {
        List<Customer> all = Customer.loadAll(context);
        for (Customer c : all) {
            if (c.id == customerId) {
                saveWindows(c.windows);
                return true;
            }
        }
        return false;
    }

    /* ================= MANUAL PCO OPTIMIZATION ================= */

    public ManualCuttingEngine.PipePlan runManualPipeCutting(List<ManualCuttingEngine.PipeCut> cuts, double stockLength) {
        Settings st = getSettings();
        double stock = stockLength > 0 ? stockLength : st.stock;
        return ManualCuttingEngine.optimizePipes(cuts, stock, 0.125);
    }
}
