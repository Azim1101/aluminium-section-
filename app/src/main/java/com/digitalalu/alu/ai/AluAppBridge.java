package com.digitalalu.alu.ai;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.digitalalu.alu.agent.AgentAppController;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * ALU-PERCHANCE-V4 — "ALU" JavaScript interface: the page-side AI controls the app.
 *
 * Injected into every frame of the Perchance WebView as window.ALU. All methods
 * are synchronous and return JSON strings, so page JS can do:
 *
 *   var tools = ALU.toolsPrompt();               // what the model may call
 *   var state = ALU.state();                     // real numbers for the prompt
 *   var r = JSON.parse(ALU.run("add_window", JSON.stringify({h:48,w:60,nos:2})));
 *   if (r.needsConfirm) ... ALU.confirm(r.confirmTool, JSON.stringify(r.confirmArgs));
 *   ALU.openScreen("cutting");                   // jump to an app screen
 *
 * Tools are the same deterministic ToolRegistry the native agent uses — the
 * model never computes a pipe count itself. Destructive tools always need a
 * second explicit confirm() call (the page asks the user first).
 *
 * Threading: JS-interface calls arrive on a WebView worker thread. Tool
 * execution is thread-safe (controller is synchronized); anything that touches
 * UI is posted to the main thread.
 */
public final class AluAppBridge {

    public interface Host {
        /** page wants to open an app screen; return false if unknown */
        boolean openScreen(String name);
        /** page changed app data (window added etc.) */
        void onDataChanged();
    }

    private final Context app;
    private final AgentAppController controller;
    private final boolean wantChatUi;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile Host host;

    public AluAppBridge(Context ctx, boolean wantChatUi, Host host) {
        this.app = ctx.getApplicationContext();
        this.controller = new AgentAppController(app);
        this.wantChatUi = wantChatUi;
        this.host = host;
    }

    public void setHost(Host h) { this.host = h; }

    @JavascriptInterface public int version() { return PerchanceBrain.BRIDGE_VERSION; }

    /** true => the injected script should draw the full chat UI on the page. */
    @JavascriptInterface public boolean wantChatUi() { return wantChatUi; }

    @JavascriptInterface public boolean isDark() {
        try {
            int m = app.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return m == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) { return false; }
    }

    @JavascriptInterface public String systemPrompt() { return IntentProtocol.SYSTEM; }

    @JavascriptInterface public String toolsPrompt() { return ToolRegistry.promptList(); }

    @JavascriptInterface public String tools() {
        JSONArray a = new JSONArray();
        try {
            for (ToolRegistry.Tool t : ToolRegistry.all()) {
                JSONObject o = new JSONObject();
                o.put("name", t.name); o.put("signature", t.signature);
                o.put("help", t.help); o.put("destructive", t.destructive);
                a.put(o);
            }
        } catch (Throwable ignored) { }
        return a.toString();
    }

    @JavascriptInterface public String state() {
        try { return ToolRegistry.stateText(controller); }
        catch (Throwable t) { return "state unavailable: " + t.getClass().getSimpleName(); }
    }

    /** Run a tool. Destructive tools come back with needsConfirm=true. */
    @JavascriptInterface public String run(String tool, String argsJson) {
        JSONObject args = parseArgs(argsJson);
        ToolRegistry.ExecResult er;
        try { er = ToolRegistry.execute(controller, tool, args); }
        catch (Throwable t) { er = new ToolRegistry.ExecResult(); er.ok = false; er.text = "error: " + t; }
        if (er.ok && !er.needsConfirm) changed(tool);
        return toJson(er, tool);
    }

    /** Run a destructive tool after the user said yes on the page. */
    @JavascriptInterface public String confirm(String tool, String argsJson) {
        JSONObject args = parseArgs(argsJson);
        ToolRegistry.ExecResult er;
        try { er = ToolRegistry.executeConfirmed(controller, tool, args); }
        catch (Throwable t) { er = new ToolRegistry.ExecResult(); er.ok = false; er.text = "error: " + t; }
        if (er.ok) changed(tool);
        return toJson(er, tool);
    }

    @JavascriptInterface public boolean openScreen(final String name) {
        final Host h = host;
        if (h == null) return false;
        ui.post(() -> { try { h.openScreen(name == null ? "" : name.trim().toLowerCase()); } catch (Throwable ignored) { } });
        return true;
    }

    @JavascriptInterface public void notifyChanged() { changed(""); }

    @JavascriptInterface public void toast(final String msg) {
        ui.post(() -> { try { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { } });
    }

    @JavascriptInterface public void log(String msg) {
        try { android.util.Log.d("ALU-WEB", msg == null ? "" : msg); } catch (Throwable ignored) { }
    }

    /* ----------------------------------------------------------------- */

    private void changed(String tool) {
        final Host h = host;
        if (h == null) return;
        ui.post(() -> { try { h.onDataChanged(); } catch (Throwable ignored) { } });
    }

    private static JSONObject parseArgs(String s) {
        if (s == null || s.trim().length() == 0) return new JSONObject();
        try { return new JSONObject(s); } catch (Throwable t) { return new JSONObject(); }
    }

    private static String toJson(ToolRegistry.ExecResult er, String tool) {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", er.ok);
            o.put("text", er.text == null ? "" : er.text);
            o.put("needsConfirm", er.needsConfirm);
            if (er.needsConfirm) {
                o.put("confirmTool", er.confirmTool);
                o.put("confirmArgs", er.confirmArgs == null ? new JSONObject() : er.confirmArgs);
                o.put("describe", describe(er.confirmTool));
            }
            o.put("tool", tool == null ? "" : tool);
        } catch (Throwable ignored) { }
        return o.toString();
    }

    static String describe(String tool) {
        if ("clear_windows".equals(tool)) return "Puri sheet khali ho jayegi (saari windows hat jayengi).";
        if ("delete_last_window".equals(tool)) return "Aakhri window hat jayegi.";
        return "\"" + tool + "\" chalega.";
    }
}
