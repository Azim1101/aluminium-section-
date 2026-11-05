package com.digitalalu.alu.ai;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Persisted configuration for the hybrid AI agent.
 *
 *   Tier 0  rules      AluNeuralNLP + AgentAppController   always on, free, offline
 *   Tier 1  on-device  MNN model (Qwen3.5-0.8B) via Alibaba MNN            offline, needs a download
 *   Tier 2  cloud      Gemini / OpenAI-compatible          needs key + internet
 *
 * IMPORTANT: the on-device model IS offline, so "NO INTERNET" mode (value 2) only
 * switches off the cloud tier — it never disables the local model. An earlier build
 * got this wrong and made a perfectly loaded model look broken.
 */
public class AiSettings {

    public static final String PREFS = "alu_ai";

    public static final int MODE_OFFLINE_FIRST = 0;  // rules first, AI only when rules fail (default)
    public static final int MODE_AI_FIRST      = 1;  // AI first, rules as fallback
    public static final int MODE_OFFLINE_ONLY  = 2;  // NO INTERNET: cloud off, local model still used

    public static final int PROVIDER_GEMINI = 0;
    public static final int PROVIDER_OPENAI = 1;     // any OpenAI-compatible /chat/completions

    public int mode = MODE_OFFLINE_FIRST;
    public int provider = PROVIDER_GEMINI;
    public String apiKey = "";
    public String baseUrl = "";        // "" -> provider default
    public String cloudModel = "";     // "" -> provider default

    /**
     * Which Gemini wire worked for this key: CloudLlm.WIRE_AUTO / WIRE_INTERACTIONS
     * / WIRE_GENERATE. CLOUD TEST sets it automatically, so the chat stops guessing.
     */
    public int cloudWire = 0;

    public String localModelPath = ""; // absolute path of the selected model config.json
    public String localModelName = "";
    public int localThreads = 0;       // 0 = auto (cores-1, clamped 2..4)
    public int localCtx = 2048;

    /** User's ON/OFF switch for the on-device model. */
    public boolean localHandlesTools = true;

    /**
     * "Model dekhega, batayega" — after the agent runs a tool, hand the result
     * back to the model so IT writes the answer. Costs one extra model call.
     */
    public boolean followUp = true;
    /** v2.9 — rules act instantly when they are sure; model only for the rest. */
    public boolean fastPath = true;
    /** v2.9 — second on-device model round after a tool (slow; off by default). */
    public boolean deviceFollowUp = false;
    /** v2.9.1 — model thinks before answering (shown live in chat; toggle in header). */
    public boolean thinking = true;
    public int thinkTokens = 120;

    /** Safety cap so a runaway agent can't burn the user's data / quota. */
    public int dailyCap = 300;
    public int usedToday = 0;
    public String usedDate = "";

    /* v2.1 — Perchance web brain (WebView inside the agent screen) */
    public boolean webBrain = true;   // v2.4: free web brain is ON by default
    public String webUrl = "https://perchance.org/d4yeutq0b1";

    /* ------------------------------------------------------------------ */

    public static AiSettings load(Context c) {
        AiSettings s = new AiSettings();
        SharedPreferences p = c.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        s.mode = p.getInt("mode", MODE_OFFLINE_FIRST);
        s.provider = p.getInt("provider", PROVIDER_GEMINI);
        s.apiKey = p.getString("api_key", "");
        s.baseUrl = p.getString("base_url", "");
        s.cloudModel = p.getString("cloud_model", "");
        s.cloudWire = p.getInt("cloud_wire", 0);
        s.localModelPath = p.getString("local_model_path", "");
        s.localModelName = p.getString("local_model_name", "");
        s.localThreads = p.getInt("local_threads", 0);
        s.localCtx = p.getInt("local_ctx", 2048);
        s.localHandlesTools = p.getBoolean("local_tools", true);
        s.followUp = p.getBoolean("follow_up", true);
        s.fastPath = p.getBoolean("fast_path", true);
        s.deviceFollowUp = p.getBoolean("device_follow_up", false);
        s.thinking = p.getBoolean("thinking", true);
        s.thinkTokens = p.getInt("think_tokens", 120);
        s.dailyCap = p.getInt("daily_cap", 300);
        s.usedToday = p.getInt("used_today", 0);
        s.usedDate = p.getString("used_date", "");
        s.webBrain = p.getBoolean("web_brain", true);
        s.webUrl = p.getString("web_url", "https://perchance.org/d4yeutq0b1");
        s.rollDate();
        forceSingleModel(c, s);
        return s;
    }

    /**
     * v2.6 — the app has exactly one brain: the bundled-catalog on-device model.
     * No cloud key, no web brain, no mode switch. Every message goes to the model
     * (rules are only the safety net while the model is not loaded yet).
     */
    static void forceSingleModel(Context c, AiSettings s) {
        try {
            ModelCatalog.ModelSpec m = ModelCatalog.DEFAULT();
            s.webBrain = false;
            s.apiKey = "";
            s.mode = MODE_AI_FIRST;
            s.localHandlesTools = true;
            s.localModelPath = ModelStore.fileFor(c, m).getAbsolutePath();
            s.localModelName = m.title;
            if (s.localCtx < 1024) s.localCtx = 2048;
        } catch (Throwable ignored) { }
    }

    public void save(Context c) {
        SharedPreferences.Editor e = c.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putInt("mode", mode);
        e.putInt("provider", provider);
        e.putString("api_key", apiKey);
        e.putBoolean("follow_up", followUp);
        e.putBoolean("fast_path", fastPath);
        e.putBoolean("device_follow_up", deviceFollowUp);
        e.putBoolean("thinking", thinking);
        e.putInt("think_tokens", thinkTokens);
        e.putInt("cloud_wire", cloudWire);
        e.putString("base_url", baseUrl);
        e.putString("cloud_model", cloudModel);
        e.putString("local_model_path", localModelPath);
        e.putString("local_model_name", localModelName);
        e.putInt("local_threads", localThreads);
        e.putInt("local_ctx", localCtx);
        e.putBoolean("local_tools", localHandlesTools);
        e.putInt("daily_cap", dailyCap);
        e.putInt("used_today", usedToday);
        e.putString("used_date", usedDate);
        e.putBoolean("web_brain", webBrain);
        e.putString("web_url", webUrl);
        e.apply();
    }

    public void resetUsage(Context c) {
        usedToday = 0;
        usedDate = today();
        save(c);
    }

    /* --------------------------- capabilities ------------------------- */

    /** Cloud is usable only if a key is set and internet mode allows it. */
    public boolean cloudEnabled() {
        return mode != MODE_OFFLINE_ONLY && apiKey != null && apiKey.trim().length() > 8;
    }

    /** User permits the on-device model. */
    public boolean localPermitted() { return localHandlesTools; }

    /** A model has been chosen (downloaded or not). */
    public boolean localConfigured() {
        return localModelPath != null && localModelPath.length() > 0;
    }

    /** v2.1 — user turned the Perchance web brain on. */
    public boolean webBrainEnabled() { return webBrain; }

    /** Internet is switched off in this mode. */
    public boolean internetOff() { return mode == MODE_OFFLINE_ONLY; }

    /** True when these settings can engage any AI tier at all. */
    public boolean anyAiEnabled() {
        return (localPermitted() && localConfigured()) || cloudEnabled() || webBrain;
    }

    public boolean underCap() { return usedToday < dailyCap; }

    public String cloudWireName() {
        if (cloudWire == 1) return "Interactions API";
        if (cloudWire == 2) return "generateContent";
        return "auto";
    }

    /** OpenAI-compatible endpoints speak JSON-in-text; Gemini speaks Interactions. */
    public boolean isOpenAi() { return provider == PROVIDER_OPENAI; }

    public void countCloudCall(Context c) {
        rollDate();
        usedToday++;
        save(c);
    }

    public void rollDate() {
        String t = today();
        if (!t.equals(usedDate)) {
            usedDate = t;
            usedToday = 0;
        }
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /* --------------------------- defaults ----------------------------- */

    public String providerName() {
        return provider == PROVIDER_OPENAI ? "OpenAI-compatible" : "Gemini";
    }

    public String effectiveBaseUrl() {
        if (baseUrl != null && baseUrl.trim().length() > 0) return baseUrl.trim();
        return provider == PROVIDER_OPENAI
                ? "https://api.openai.com/v1"
                : "https://generativelanguage.googleapis.com/v1beta";
    }

    public String effectiveModel() {
        if (cloudModel != null && cloudModel.trim().length() > 0) return cloudModel.trim();
        return provider == PROVIDER_OPENAI ? "gpt-4o-mini" : "gemini-3.8-flash";
    }

    public int effectiveThreads(int cores) {
        if (localThreads > 0) return localThreads;
        return Math.max(2, Math.min(4, cores - 1));
    }

    public String modeName() {
        switch (mode) {
            case MODE_AI_FIRST: return "AI FIRST";
            case MODE_OFFLINE_ONLY: return "NO INTERNET";
            default: return "OFFLINE FIRST";
        }
    }

    public String modeDetail() {
        switch (mode) {
            case MODE_AI_FIRST:
                return "Har sawal AI se — on-device pehle, phir cloud. Sabse behtar samajh, thoda slow.";
            case MODE_OFFLINE_ONLY:
                return "Internet band — cloud AI off. Rules + on-device model chalega.";
            default:
                return "Rules pehle (tez, muft). Jo rule na samjhe wahi AI ko jaata hai.";
        }
    }
}
