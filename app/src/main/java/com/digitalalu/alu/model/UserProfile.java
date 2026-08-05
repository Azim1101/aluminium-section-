package com.digitalalu.alu.model;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** User's own profile - app owner/worker details */
public class UserProfile {
    
    public String name = "";
    public String mobile = "";
    public String address = "";
    public long updatedAt;
    
    public UserProfile() {
        updatedAt = System.currentTimeMillis();
    }
    
    public boolean isEmpty() {
        return name.isEmpty() && mobile.isEmpty() && address.isEmpty();
    }
    
    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("mobile", mobile);
        o.put("address", address);
        o.put("updatedAt", updatedAt);
        return o;
    }
    
    public static UserProfile fromJson(JSONObject o) throws Exception {
        UserProfile p = new UserProfile();
        p.name = o.optString("name", "");
        p.mobile = o.optString("mobile", "");
        p.address = o.optString("address", "");
        p.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
        return p;
    }
    
    /* ================= storage ================= */
    private static final String PREF_NAME = "alu_user_profile";
    
    public static UserProfile load(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String s = sp.getString("profile", null);
            if (s == null) return new UserProfile();
            return fromJson(new JSONObject(s));
        } catch (Exception e) {
            return new UserProfile();
        }
    }
    
    public static void save(Context ctx, UserProfile profile) {
        try {
            profile.updatedAt = System.currentTimeMillis();
            ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("profile", profile.toJson().toString())
                    .apply();
        } catch (Exception ignored) {}
    }
}
