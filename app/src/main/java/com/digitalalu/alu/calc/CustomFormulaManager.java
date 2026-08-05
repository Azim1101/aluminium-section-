package com.digitalalu.alu.calc;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class CustomFormulaManager {

    public static class CustomSystem {
        public String name = "";
        public String sutterH_formula = "H - 2.0";
        public String sutterW_formula = "W / q";
        public String muliyaH_formula = "";
        public String muliyaLabel = "MULIYA"; // default
        public String rpLen_formula = "W";
        public String glassH_formula = "sutterH - 4.0";
        public String glassW_formula = "sutterW - 4.0";

        public JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("sutterH", sutterH_formula);
                o.put("sutterW", sutterW_formula);
                o.put("muliyaH", muliyaH_formula);
                o.put("muliyaLabel", muliyaLabel);
                o.put("rpLen", rpLen_formula);
                o.put("glassH", glassH_formula);
                o.put("glassW", glassW_formula);
                return o;
            } catch (Exception e) {
                return null;
            }
        }

        public static CustomSystem fromJson(JSONObject o) {
            try {
                CustomSystem cs = new CustomSystem();
                cs.name = o.getString("name");
                cs.sutterH_formula = o.getString("sutterH");
                cs.sutterW_formula = o.getString("sutterW");
                cs.muliyaH_formula = o.optString("muliyaH", "");
                cs.muliyaLabel = o.optString("muliyaLabel", "MULIYA");
                cs.rpLen_formula = o.getString("rpLen");
                cs.glassH_formula = o.getString("glassH");
                cs.glassW_formula = o.getString("glassW");
                return cs;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static final String PREF_NAME = "custom_formulas";
    private static List<CustomSystem> cachedSystems = null;
    public static List<CustomSystem> activeSystems = null;

    public static List<CustomSystem> getSystems(Context ctx) {
        if (cachedSystems != null) return cachedSystems;
        cachedSystems = loadSystems(ctx);
        return cachedSystems;
    }

    /** Clears the in-memory cache (used after restoring a backup). */
    public static void invalidateCache() {
        cachedSystems = null;
        activeSystems = null;
    }

    public static void saveSystems(Context ctx, List<CustomSystem> systems) {
        cachedSystems = systems;
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        for (CustomSystem cs : systems) {
            arr.put(cs.toJson());
        }
        prefs.edit().putString("systems_json", arr.toString()).apply();
    }

    private static List<CustomSystem> loadSystems(Context ctx) {
        List<CustomSystem> list = new ArrayList<>();
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString("systems_json", "");
        if (json.isEmpty()) {
            list.add(createDefaultZed());
            list.add(createDefaultDomal());
            return list;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                CustomSystem cs = CustomSystem.fromJson(arr.getJSONObject(i));
                if (cs != null) list.add(cs);
            }
        } catch (Exception e) {
            list.add(createDefaultZed());
            list.add(createDefaultDomal());
        }
        return list;
    }

    public static CustomSystem createDefaultZed() {
        CustomSystem cs = new CustomSystem();
        cs.name = "ZED";
        cs.sutterH_formula = "H - 2.36";
        cs.sutterW_formula = "( W - (q+1) * 1.20 ) / q";
        cs.muliyaH_formula = "H - 2.43";
        cs.muliyaLabel = "MULIYA";
        cs.rpLen_formula = "W - 0.20";
        cs.glassH_formula = "sutterH - 4.18";
        cs.glassW_formula = "sutterW - 4.18";
        return cs;
    }

    public static CustomSystem createDefaultDomal() {
        CustomSystem cs = new CustomSystem();
        cs.name = "DOMAL";
        cs.sutterH_formula = "H - 2.87";
        cs.sutterW_formula = "(W + ((q >= 3) * (q - 2) * 2.0)) / q";
        cs.muliyaH_formula = "H - 2.30";
        cs.muliyaLabel = "RT";
        cs.rpLen_formula = "W - 1.0";
        cs.glassH_formula = "sutterH - 4.00";
        cs.glassW_formula = "sutterW - 4.00";
        return cs;
    }

    public static List<CustomSystem> parseFormulaText(String text) throws Exception {
        List<CustomSystem> list = new ArrayList<>();
        String[] lines = text.split("\n");
        CustomSystem current = null;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            // Check if this is a header (starts a new system)
            if (!line.contains("=")) {
                current = new CustomSystem();
                current.name = line;
                list.add(current);
                continue;
            }

            if (current == null) {
                throw new Exception("Formula found before System Name!");
            }

            // Split into key and formula
            String[] parts = line.split("=", 2);
            String key = parts[0].trim().toUpperCase().replace(" ", "").replace("_", "");
            String formula = parts[1].trim();

            // Clean unicode characters
            formula = formula.replace("x", "*").replace("X", "*").replace("÷", "/");

            if (key.equals("SUTTERH") || key.equals("SHUTTERH")) {
                current.sutterH_formula = formula;
            } else if (key.equals("SUTTERW") || key.equals("SHUTTERW")) {
                current.sutterW_formula = formula;
            } else if (key.equals("MULIYAH") || key.equals("RTH") || key.equals("MIDH") || key.equals("MIDPIPEH")) {
                current.muliyaH_formula = formula;
                if (key.equals("RTH")) {
                    current.muliyaLabel = "RT";
                } else if (key.equals("MULIYAH")) {
                    current.muliyaLabel = "MULIYA";
                } else {
                    current.muliyaLabel = "MID";
                }
            } else if (key.equals("RPLENGTH") || key.equals("RPLEN")) {
                current.rpLen_formula = formula;
            } else if (key.equals("GLASS")) {
                // Apply to both glassH and glassW by context
                current.glassH_formula = formula.replace("sutter", "sutterH").replace("SUTTER", "sutterH").replace("shutter", "sutterH").replace("SHUTTER", "sutterH");
                current.glassW_formula = formula.replace("sutter", "sutterW").replace("SUTTER", "sutterW").replace("shutter", "sutterW").replace("SHUTTER", "sutterW");
            } else if (key.equals("GLASSH") || key.equals("GLASSESHEIGHT") || key.equals("GLASSHIGHT")) {
                current.glassH_formula = formula;
            } else if (key.equals("GLASSW") || key.equals("GLASSESWIDTH")) {
                current.glassW_formula = formula;
            }
        }
        if (list.isEmpty()) {
            throw new Exception("No valid custom systems parsed!");
        }
        return list;
    }
}
