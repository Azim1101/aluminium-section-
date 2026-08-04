package com.digitalalu.alu.model;

import org.json.JSONException;
import org.json.JSONObject;

/** One window row */
public class WindowItem {

    public static final int ZED = 0;
    public static final int DOMAL = 1;

    public long id;
    public String name;
    public int system;    // ZED / DOMAL
    public double h;      // frame height (0 = empty)
    public double w;      // frame width  (0 = empty)
    public int sutter;
    public int nos;
    public int rpQty;     // 0 = auto, >0 = user set

    public WindowItem(long id, String name, int system,
                      double h, double w, int sutter, int nos) {
        this.id = id; this.name = name; this.system = system;
        this.h = h; this.w = w; this.sutter = sutter; this.nos = nos;
        this.rpQty = 0;
    }

    public boolean isEmpty() { return h <= 0 || w <= 0; }

    public static String systemName(int s) { return s == DOMAL ? "DOMAL" : "ZED"; }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id); o.put("name", name); o.put("system", system);
        o.put("h", h); o.put("w", w);
        o.put("sutter", sutter); o.put("nos", nos); o.put("rpQty", rpQty);
        return o;
    }

    public static WindowItem fromJson(JSONObject o) throws JSONException {
        WindowItem it = new WindowItem(
                o.getLong("id"), o.getString("name"), o.optInt("system", ZED),
                o.getDouble("h"), o.getDouble("w"),
                o.getInt("sutter"), o.optInt("nos", 1));
        it.rpQty = o.optInt("rpQty", 0);
        return it;
    }
}
