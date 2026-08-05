package com.digitalalu.alu.calc;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Full app backup & restore.
 *
 * Everything the app stores lives in a handful of SharedPreferences files.
 * Export collects them all into one JSON document; import wipes the same
 * files and writes the backed-up values back. This protects prices,
 * customer estimates, custom formulas, settings and work-in-progress
 * windows against "clear app data" / phone change.
 */
public final class BackupManager {

    /** All SharedPreferences files owned by the app. */
    private static final String[] PREF_NAMES = {
            "alu_price",           // PriceBook (rates, extras, PIN hash)
            "alu_settings",        // Settings (deductions, stock, business info)
            "alu_customers",       // saved customer estimates
            "custom_formulas",     // custom pipe systems
            "alu_data",            // MainActivity work-in-progress windows
            "manual_cutting_v1",   // ManualCuttingActivity workspace
    };

    public static final int FORMAT_VERSION = 1;

    private BackupManager() {}

    /* ================= EXPORT ================= */

    public static JSONObject exportAll(Context ctx) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "ALU Window Optimizer");
        root.put("format", FORMAT_VERSION);
        root.put("date", new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
                .format(new Date()));

        JSONObject prefs = new JSONObject();
        for (String name : PREF_NAMES) {
            Map<String, ?> all = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).getAll();
            if (all.isEmpty()) continue;
            JSONObject po = new JSONObject();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                po.put(e.getKey(), encode(e.getValue()));
            }
            prefs.put(name, po);
        }
        root.put("prefs", prefs);
        return root;
    }

    private static JSONObject encode(Object v) throws Exception {
        JSONObject kv = new JSONObject();
        if (v instanceof String) { kv.put("t", "s"); kv.put("v", v); }
        else if (v instanceof Integer) { kv.put("t", "i"); kv.put("v", (int) (Integer) v); }
        else if (v instanceof Long) { kv.put("t", "l"); kv.put("v", (long) (Long) v); }
        else if (v instanceof Float) { kv.put("t", "f"); kv.put("v", (double) (Float) v); }
        else if (v instanceof Boolean) { kv.put("t", "b"); kv.put("v", (boolean) (Boolean) v); }
        else if (v instanceof Set) {
            kv.put("t", "set");
            JSONArray a = new JSONArray();
            for (Object s : (Set<?>) v) a.put(String.valueOf(s));
            kv.put("v", a);
        } else {
            kv.put("t", "s"); kv.put("v", String.valueOf(v));
        }
        return kv;
    }

    /* ================= IMPORT ================= */

    public static class Result {
        public int prefCount;
        public int keyCount;
        public String date = "";
    }

    /** Reads a backup document; throws if it is not an ALU backup. */
    public static void validate(JSONObject root) throws Exception {
        if (!"ALU Window Optimizer".equals(root.optString("app")))
            throw new IllegalArgumentException("Not an ALU Window backup file");
        if (!root.has("prefs"))
            throw new IllegalArgumentException("Backup file has no data section");
    }

    /** Clears current data and restores everything from the backup. */
    public static Result importAll(Context ctx, JSONObject root) throws Exception {
        validate(root);
        Result r = new Result();
        r.date = root.optString("date", "");
        JSONObject prefs = root.getJSONObject("prefs");

        java.util.Iterator<String> names = prefs.keys();
        List<String> allowed = new ArrayList<>();
        for (String n : PREF_NAMES) allowed.add(n);

        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) continue;   // never touch unknown prefs
            JSONObject po = prefs.getJSONObject(name);
            SharedPreferences.Editor ed = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit();
            ed.clear();

            java.util.Iterator<String> keys = po.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject kv = po.getJSONObject(key);
                String t = kv.optString("t", "s");
                switch (t) {
                    case "i": ed.putInt(key, kv.optInt("v")); break;
                    case "l": ed.putLong(key, kv.optLong("v")); break;
                    case "f": ed.putFloat(key, (float) kv.optDouble("v")); break;
                    case "b": ed.putBoolean(key, kv.optBoolean("v")); break;
                    case "set": {
                        JSONArray a = kv.optJSONArray("v");
                        Set<String> set = new HashSet<>();
                        if (a != null) for (int i = 0; i < a.length(); i++) set.add(a.optString(i));
                        ed.putStringSet(key, set);
                        break;
                    }
                    default: ed.putString(key, kv.optString("v", "")); break;
                }
                r.keyCount++;
            }
            ed.commit();   // commit (not apply): data must be on disk before restart
            r.prefCount++;
        }
        return r;
    }
}
