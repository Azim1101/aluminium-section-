package com.digitalalu.alu.calc;

import android.content.Context;
import android.content.SharedPreferences;

import com.digitalalu.alu.model.WindowItem;
import com.digitalalu.alu.util.SecurityUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Price system — PIN locked (default 1101).
 *
 * Since v1.7 the PIN is stored only as a salted SHA-256 hash. Old installs
 * that still carry a plain-text PIN keep working and are migrated
 * automatically the first time the correct PIN is entered (call save()
 * after a successful checkPin to persist the migration).
 *
 * Pipe price:
 *   weight(kg) = length(inch) / 192 * kgPer16ft
 *   price      = weight * aluRate
 *
 * Glass price: inch-based (H x W in inches -> sqft)
 * Extras: per sutter / per window / per inch / per RP
 */
public class PriceBook {

    public static final String DEFAULT_PIN = "1101";
    public static final double INCH_16FT = 192.0;   // 16 feet = 192 inch

    private static final String P = "alu_price";

    /** Salted SHA-256 of the PIN (empty = legacy plain-text mode). */
    public String pinHash = "";
    public String pinSalt = "";

    /** Plain-text PIN from a pre-v1.7 install; never saved again once used. */
    private String legacyPin = null;

    public double aluRate = 450;      // Rs per kg
    public double glassRate = 80;     // Rs per sq.ft

    /** kg per 16 ft, key = pipe type (Z_FRAME etc.) */
    public final Map<String, Double> kg = new LinkedHashMap<>();

    /** user added extra charges */
    public static class Extra {
        public static final int PER_SUTTER = 0;
        public static final int PER_WINDOW = 1;
        public static final int PER_INCH   = 2;   // per inch (replaced per sqft)
        public static final int PER_RP     = 3;

        public String name;
        public int basis;
        public double rate;
        public int system;   // -1 = both, 0 = ZED, 1 = DOMAL

        public Extra(String n, int b, double r, int sys) {
            name = n; basis = b; rate = r; system = sys;
        }

        public static String basisName(int b) {
            switch (b) {
                case PER_SUTTER: return "per sutter";
                case PER_WINDOW: return "per window";
                case PER_INCH:   return "per inch";
                case PER_RP:     return "per RP pipe";
            }
            return "";
        }
        public String systemName() {
            if (system == WindowItem.ZED) return "ZED";
            if (system == WindowItem.DOMAL) return "DOMAL";
            return "BOTH";
        }
        public boolean appliesTo(int sys) { return system < 0 || system == sys; }
    }

    public final List<Extra> extras = new ArrayList<>();

    /* ---------------- defaults ---------------- */
    public static PriceBook defaults() {
        PriceBook p = new PriceBook();
        p.kg.put("Z_FRAME",  5.8);
        p.kg.put("Z_SUTTER", 3.5);
        p.kg.put("Z_MULIYA", 5.0);
        p.kg.put("Z_RP",     0.8);
        p.kg.put("D_FRAME",  4.5);
        p.kg.put("D_SUTTER", 2.5);
        p.kg.put("D_RT",     1.8);
        p.kg.put("D_RP",     0.8);
        return p;
    }

    public double kgOf(String type) {
        Double d = kg.get(type);
        return d == null ? 0 : d;
    }

    /** weight of one pipe piece */
    public double weight(String type, double lengthInch) {
        return lengthInch / INCH_16FT * kgOf(type);
    }
    public double pipePrice(String type, double lengthInch) {
        return weight(type, lengthInch) * aluRate;
    }

    /* ---------------- PIN (salted hash since v1.7) ---------------- */

    /** True when the PIN is already stored as a hash (v1.7+ format). */
    public boolean hasHashedPin() { return pinHash != null && !pinHash.isEmpty(); }

    /**
     * Checks the entered PIN. In legacy mode a successful check upgrades the
     * PIN to the hashed format in memory — call save() to persist it.
     */
    public boolean checkPin(String input) {
        if (input == null) return false;
        String s = input.trim();
        if (hasHashedPin()) {
            return SecurityUtil.equals(SecurityUtil.sha256(pinSalt + s), pinHash);
        }
        String legacy = legacyPin != null ? legacyPin : DEFAULT_PIN;
        if (SecurityUtil.equals(s, legacy)) {
            setPin(s);                 // auto-migrate away from plain text
            return true;
        }
        return false;
    }

    /** Sets a new PIN (stored only as salt + SHA-256 hash). */
    public void setPin(String newPin) {
        String s = newPin == null ? "" : newPin.trim();
        pinSalt = SecurityUtil.randomSalt();
        pinHash = SecurityUtil.sha256(pinSalt + s);
        legacyPin = null;
    }

    /* ---------------- persistence ---------------- */
    public void save(Context c) {
        try {
            JSONObject o = new JSONObject();
            // v1.7+: only the salted hash is persisted — never the PIN itself.
            o.put("pinHash", pinHash);
            o.put("pinSalt", pinSalt);
            o.put("aluRate", aluRate);
            o.put("glassRate", glassRate);
            JSONObject k = new JSONObject();
            for (Map.Entry<String, Double> e : kg.entrySet()) k.put(e.getKey(), e.getValue());
            o.put("kg", k);
            JSONArray ex = new JSONArray();
            for (Extra x : extras) {
                JSONObject j = new JSONObject();
                j.put("name", x.name); j.put("basis", x.basis);
                j.put("rate", x.rate); j.put("system", x.system);
                ex.put(j);
            }
            o.put("extras", ex);
            c.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
                    .putString("data", o.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static PriceBook load(Context c) {
        PriceBook p = defaults();
        try {
            SharedPreferences sp = c.getSharedPreferences(P, Context.MODE_PRIVATE);
            String s = sp.getString("data", null);
            if (s == null) return p;
            JSONObject o = new JSONObject(s);
            p.pinHash = o.optString("pinHash", "");
            p.pinSalt = o.optString("pinSalt", "");
            if (!p.hasHashedPin()) {
                // Pre-v1.7 data: keep the old plain-text PIN in memory only.
                // It disappears from storage after the next successful unlock.
                p.legacyPin = o.optString("pin", DEFAULT_PIN);
            }
            p.aluRate = o.optDouble("aluRate", 450);
            p.glassRate = o.optDouble("glassRate", 80);
            JSONObject k = o.optJSONObject("kg");
            if (k != null) {
                java.util.Iterator<String> it = k.keys();
                while (it.hasNext()) { String key = it.next(); p.kg.put(key, k.optDouble(key, 0)); }
            }
            p.extras.clear();
            JSONArray ex = o.optJSONArray("extras");
            if (ex != null) for (int i = 0; i < ex.length(); i++) {
                JSONObject j = ex.getJSONObject(i);
                p.extras.add(new Extra(j.optString("name", "Extra"),
                        j.optInt("basis", 1), j.optDouble("rate", 0), j.optInt("system", -1)));
            }
        } catch (Exception ignored) {}
        return p;
    }
}
