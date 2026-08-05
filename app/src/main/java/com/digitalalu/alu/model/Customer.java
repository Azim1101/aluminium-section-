package com.digitalalu.alu.model;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** A saved customer estimate */
public class Customer {

    public long id;
    public String name = "";
    public String mobile = "";
    public String village = "";
    public String note = "";
    public long savedAt;
    public List<WindowItem> windows = new ArrayList<>();

    public Customer(long id) { this.id = id; savedAt = System.currentTimeMillis(); }

    public String dateStr() {
        return new SimpleDateFormat("dd-MM-yyyy  HH:mm", Locale.US).format(new Date(savedAt));
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("mobile", mobile);
        o.put("village", village);
        o.put("note", note);
        o.put("savedAt", savedAt);
        JSONArray a = new JSONArray();
        for (WindowItem w : windows) a.put(w.toJson());
        o.put("windows", a);
        return o;
    }

    public static Customer fromJson(JSONObject o) throws Exception {
        Customer c = new Customer(o.getLong("id"));
        c.name = o.optString("name", "");
        c.mobile = o.optString("mobile", "");
        c.village = o.optString("village", "");
        c.note = o.optString("note", "");
        c.savedAt = o.optLong("savedAt", System.currentTimeMillis());
        JSONArray a = o.optJSONArray("windows");
        if (a != null) for (int i = 0; i < a.length(); i++)
            c.windows.add(WindowItem.fromJson(a.getJSONObject(i)));
        return c;
    }

    /* ================= storage ================= */
    private static final String P = "alu_customers";

    public static List<Customer> loadAll(Context ctx) {
        List<Customer> out = new ArrayList<>();
        try {
            SharedPreferences sp = ctx.getSharedPreferences(P, Context.MODE_PRIVATE);
            String s = sp.getString("list", null);
            if (s == null) return out;
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) out.add(fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    public static void saveAll(Context ctx, List<Customer> list) {
        try {
            JSONArray a = new JSONArray();
            for (Customer c : list) a.put(c.toJson());
            ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
                    .putString("list", a.toString()).apply();
        } catch (Exception ignored) {}
    }

    /*
     * PIN note (v1.7): the customer section uses the single app PIN from
     * PriceBook (salted hash). The old per-section plain-text "pin" pref is
     * no longer read or written.
     */
}
