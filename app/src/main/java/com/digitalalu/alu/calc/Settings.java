package com.digitalalu.alu.calc;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.digitalalu.alu.model.WindowItem;

/** All deduction values + stock settings (everything in INCH) + business info */
public class Settings {

    public boolean mm = false;
    public double stock = 196;
    public double kerf  = 0.12;

    /* ---------------- ZED ---------------- */
    public double z_dw  = 1.20;
    public double z_dh  = 2.36;
    public double z_dm  = 2.43;
    public double z_dg  = 4.18;
    public double z_rpDed = 0.20;

    /* ---------------- DOMAL ---------------- */
    public double d_dw  = 0.00;
    public double d_trackAdd = 2.00;
    public double d_dh  = 2.87;
    public double d_dm  = 2.30;
    public double d_dg  = 4.00;
    public double d_rpDed = 1.00;

    /* ---------------- RP (both) ---------------- */
    public double rpGap = 6.00;
    public double rpMin = 4.00;
    public double rpMax = 4.85;
    public boolean useRp = true;

    /* ---------------- BUSINESS INFO ---------------- */
    public String bizName = "";
    public String bizAddress = "";
    public String bizMobile = "";

    /* ---------------- SECTION-WISE STOCK PIPES ----------------
     * Key = Engine type ("Z_FRAME", "Z_SUTTER", ...). Each section has its own
     * standard stock length (unlimited) plus any leftover pieces (limited qty)
     * the workshop is holding. Empty = fall back to the global `stock` length.
     */
    public final Map<String, List<Engine.StockBar>> stockBars = new LinkedHashMap<>();

    private static final String P = "alu_settings";
    private static final String K_BARS = "stock_bars_json";

    /** Available pipes for one section — never empty. */
    public List<Engine.StockBar> barsFor(String type) {
        List<Engine.StockBar> src = type == null ? null : stockBars.get(type);
        List<Engine.StockBar> out = new ArrayList<>();
        if (src != null) for (Engine.StockBar b : src) if (b != null && b.len > 0) out.add(b.copy());
        if (out.isEmpty()) out.add(new Engine.StockBar(stock, -1));
        return out;
    }

    /** The standard (unlimited) stock length for a section. */
    public double primaryStockFor(String type) {
        List<Engine.StockBar> src = type == null ? null : stockBars.get(type);
        if (src != null) {
            for (Engine.StockBar b : src) if (b != null && b.unlimited()) return b.len;
            for (Engine.StockBar b : src) if (b != null) return b.len;
        }
        return stock;
    }

    public boolean hasCustomStock(String type) {
        return type != null && stockBars.containsKey(type) && !stockBars.get(type).isEmpty();
    }

    /** Extra (leftover / limited-qty) pipes of a section. */
    public List<Engine.StockBar> extrasFor(String type) {
        List<Engine.StockBar> out = new ArrayList<>();
        List<Engine.StockBar> src = type == null ? null : stockBars.get(type);
        if (src != null) for (Engine.StockBar b : src) if (b != null && !b.unlimited()) out.add(b);
        return out;
    }

    public void setPrimaryStock(String type, double len) {
        if (type == null || len <= 0) return;
        for (Engine.StockBar b : edit(type)) {
            if (b.unlimited()) { b.len = len; return; }
        }
        edit(type).add(0, new Engine.StockBar(len, -1));
    }

    public void addExtraBar(String type, double len, int qty) {
        if (type == null || len <= 0) return;
        edit(type).add(new Engine.StockBar(len, Math.max(1, qty)));
    }

    public void removeExtraBar(String type, Engine.StockBar bar) {
        List<Engine.StockBar> src = type == null ? null : stockBars.get(type);
        if (src == null) return;
        src.remove(bar);
        if (src.isEmpty()) stockBars.remove(type);
    }

    /** Forget every per-section override (back to one global stock length). */
    public void resetStockBars() { stockBars.clear(); }

    private List<Engine.StockBar> edit(String type) {
        List<Engine.StockBar> l = stockBars.get(type);
        if (l == null) {
            l = new ArrayList<>();
            stockBars.put(type, l);
        }
        return l;
    }

    /* ---- persistence of the section pipe list ---- */
    private String stockBarsJson() {
        try {
            JSONArray arr = new JSONArray();
            for (Map.Entry<String, List<Engine.StockBar>> e : stockBars.entrySet()) {
                JSONArray bars = new JSONArray();
                for (Engine.StockBar b : e.getValue()) {
                    JSONObject o = new JSONObject();
                    o.put("len", b.len);
                    o.put("qty", b.qty);
                    bars.put(o);
                }
                JSONObject t = new JSONObject();
                t.put("type", e.getKey());
                t.put("bars", bars);
                arr.put(t);
            }
            return arr.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void loadStockBars(String json) {
        stockBars.clear();
        if (json == null || json.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject t = arr.getJSONObject(i);
                String type = t.optString("type", "");
                if (type.isEmpty()) continue;
                JSONArray bars = t.optJSONArray("bars");
                if (bars == null) continue;
                List<Engine.StockBar> l = new ArrayList<>();
                for (int j = 0; j < bars.length(); j++) {
                    JSONObject o = bars.getJSONObject(j);
                    l.add(new Engine.StockBar(o.optDouble("len", 0), o.optInt("qty", -1)));
                }
                if (!l.isEmpty()) stockBars.put(type, l);
            }
        } catch (Exception ignored) {
            stockBars.clear();
        }
    }

    /* per-system getters */
    public double dw(int sys) { return sys == WindowItem.DOMAL ? d_dw : z_dw; }
    public double dh(int sys) { return sys == WindowItem.DOMAL ? d_dh : z_dh; }
    public double dm(int sys) { return sys == WindowItem.DOMAL ? d_dm : z_dm; }
    public double dg(int sys) { return sys == WindowItem.DOMAL ? d_dg : z_dg; }
    public double rpDed(int sys) { return sys == WindowItem.DOMAL ? d_rpDed : z_rpDed; }

    /** Business line for export — uses bizName if set, else default */
    public String bizHeader() {
        if (bizName != null && !bizName.isEmpty()) return bizName;
        return "ALU WINDOW ESTIMATE";
    }
    public String bizFooter() {
        StringBuilder sb = new StringBuilder();
        if (bizAddress != null && !bizAddress.isEmpty()) sb.append(bizAddress);
        if (bizMobile != null && !bizMobile.isEmpty()) {
            if (sb.length() > 0) sb.append("  |  ");
            sb.append("MO. ").append(bizMobile);
        }
        return sb.toString();
    }

    public void save(Context c) {
        SharedPreferences.Editor e = c.getSharedPreferences(P, Context.MODE_PRIVATE).edit();
        e.putBoolean("mm", mm).putFloat("stock", (float) stock).putFloat("kerf", (float) kerf)
         .putFloat("z_dw", (float) z_dw).putFloat("z_dh", (float) z_dh)
         .putFloat("z_dm", (float) z_dm).putFloat("z_dg", (float) z_dg)
         .putFloat("z_rpDed", (float) z_rpDed)
         .putFloat("d_dw", (float) d_dw).putFloat("d_trackAdd", (float) d_trackAdd).putFloat("d_dh", (float) d_dh)
         .putFloat("d_dm", (float) d_dm).putFloat("d_dg", (float) d_dg)
         .putFloat("d_rpDed", (float) d_rpDed)
         .putFloat("rpGap", (float) rpGap).putFloat("rpMin", (float) rpMin)
         .putFloat("rpMax", (float) rpMax)
         .putBoolean("useRp", useRp)
         .putString("bizName", bizName)
         .putString("bizAddress", bizAddress)
         .putString("bizMobile", bizMobile)
         .putString(K_BARS, stockBarsJson())
         .apply();
    }

    public static Settings load(Context c) {
        SharedPreferences p = c.getSharedPreferences(P, Context.MODE_PRIVATE);
        Settings s = new Settings();
        s.mm    = p.getBoolean("mm", false);
        s.stock = p.getFloat("stock", 196f);
        s.kerf  = p.getFloat("kerf", 0.12f);

        s.z_dw = p.getFloat("z_dw", 1.20f);
        s.z_dh = p.getFloat("z_dh", 2.36f);
        s.z_dm = p.getFloat("z_dm", 2.43f);
        s.z_dg = p.getFloat("z_dg", 4.18f);
        s.z_rpDed = p.getFloat("z_rpDed", 0.20f);

        s.d_dw = p.getFloat("d_dw", 0.00f);
        s.d_trackAdd = p.getFloat("d_trackAdd", 2.00f);
        s.d_dh = p.getFloat("d_dh", 2.87f);
        s.d_dm = p.getFloat("d_dm", 2.30f);
        s.d_dg = p.getFloat("d_dg", 4.00f);
        s.d_rpDed = p.getFloat("d_rpDed", 1.00f);

        s.rpGap = p.getFloat("rpGap", 6.00f);
        s.rpMin = p.getFloat("rpMin", 4.00f);
        s.rpMax = p.getFloat("rpMax", 4.85f);
        s.useRp = p.getBoolean("useRp", true);

        s.bizName = p.getString("bizName", "");
        s.bizAddress = p.getString("bizAddress", "");
        s.bizMobile = p.getString("bizMobile", "");
        s.loadStockBars(p.getString(K_BARS, ""));
        return s;
    }

    /* ---- unit helpers ---- */
    public static final double MM = 25.4;

    public double toIn(double v) { return mm ? v / MM : v; }
    public double out(double in) { return mm ? in * MM : in; }
    public String unit() { return mm ? "mm" : "\""; }

    public String fmt(double in) {
        double v = out(in);
        return mm ? String.valueOf(Math.round(v)) : String.format("%.2f", v);
    }
    public String fmtU(double in) { return fmt(in) + unit(); }
}
