package com.digitalalu.alu.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.CookieManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ALU-PERCHANCE-V2 — the web brain, with real connection management.
 *
 * The agent screen hosts the user's Perchance page (default d4yeutq0b1). The
 * page's JS does the thinking bridge:
 *
 *   app  --ask(mode, fields)-->  page: builds the instruction (ALU_SYSTEM_PROMPT,
 *        editable right on the page!) -> ai-text-plugin -> JSON reply
 *   app  <--chunk/done/error--   page: streams every chunk back
 *
 * What v2 adds over v1 (the "connectivity" work):
 *   • handshake  : page announces {"kind":"ready","v":2}; the app only trusts
 *                  "READY" after that, and shows the bridge version.
 *   • heartbeat  : page pings every 5 s; if pings stop while the user is asking,
 *                  the app says "page atak gaya" instead of hanging forever.
 *   • queue      : the page serialises asks (plugin allows a few, but slow);
 *                  the app can ask for order, say and raw modes.
 *   • retry      : a timeout or a "busy" answer is retried once automatically.
 *   • modes      : ORDER (tool JSON), SAY (phrase a tool result), RAW (plain Q).
 *
 * Threading: attach/detach/load = UI thread. ask() blocks -> worker thread.
 * onPerchanceEvent() runs on the WebView JS thread: it never touches UI and is
 * wrapped in try/catch (a hostile page must not be able to crash the app).
 */
public final class PerchanceBrain {

    public static final String DEFAULT_URL = "https://perchance.org/d4yeutq0b1";
    public static final int BRIDGE_VERSION = 4;

    public interface Listener {
        void onChunk(String fullSoFar);
        void onState(String state);
    }

    public static class Answer {
        public boolean ok;
        public String text = "";
        public String error = "";
    }

    /** One parsed bridge event. Pure data so unit tests can exercise parsing. */
    public static class Event {
        public String kind = "";
        public int id = 0;
        public String text = "";
        public int version = 0;
    }

    /** Parse a bridge event; null when the JSON is not one of ours / is broken. */
    public static Event parseEvent(String json) {
        if (json == null) return null;
        Event ev;
        try {
            JSONObject o = new JSONObject(json);
            if (o.optInt("__alu", 0) != 1) return null;
            ev = new Event();
            ev.kind = o.optString("kind", "");
            ev.id = o.optInt("id", 0);
            ev.text = o.optString("text", "");
            ev.version = o.optInt("v", 0);
        } catch (Throwable t) {
            return null;
        }
        if (ev.kind.length() == 0) return null;
        return ev;
    }

    /**
     * Build the JS message that carries one ask. Pure function — unit tested
     * without a WebView, because a broken payload here would silently break
     * every agent answer.
     */
    public static String buildAskJson(int id, String mode, JSONObject fields, String startWith) {
        try {
            JSONObject m = new JSONObject();
            m.put("__alu", 1);
            m.put("kind", "ask");
            m.put("id", id);
            m.put("mode", mode == null ? "raw" : mode);
            if (startWith != null && startWith.length() > 0) m.put("startWith", startWith);
            if (fields != null) {
                java.util.Iterator<String> it = fields.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    m.put(k, fields.get(k));
                }
            }
            return m.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /* ------------------------------------------------------------------ */

    private static PerchanceBrain ACTIVE;

    public static PerchanceBrain active() { return ACTIVE; }
    public static boolean isReady() { PerchanceBrain b = ACTIVE; return b != null && b.ready(); }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final WebView web;
    private final AtomicInteger seq = new AtomicInteger(1);

    private volatile boolean pageLoaded;
    private volatile boolean bridgeReady;
    private volatile int bridgeVersion;
    private volatile long lastPingAt;
    private volatile boolean destroyed;
    private volatile String lastError = "";
    private volatile String lastState = "shuru nahi hua";
    private volatile int askCount, okCount, failCount;

    private volatile Listener listener;
    /** generator name from the configured URL, e.g. "d4yeutq0b1" */
    private volatile String generatorName = "";
    private volatile String userAgent = "";
    private volatile boolean injected;
    private volatile String bridgeJs = "";
    private volatile boolean chatUi;
    private volatile AluAppBridge appBridge;

    private static class Pending {
        final int id;
        final CountDownLatch latch = new CountDownLatch(1);
        volatile String text = "";
        volatile boolean ok;
        volatile boolean busy;
        volatile String error = "";
        Pending(int id) { this.id = id; }
    }

    private volatile Pending pending;
    private final Object askLock = new Object();

    private PerchanceBrain(WebView web, Context ctx) { this.web = web; }

    /** Wire a WebView (created by the activity) as the web brain. UI thread only. */
    public static PerchanceBrain attach(Context ctx, WebView web, Listener l) {
        return attach(ctx, web, l, false, null);
    }

    /**
     * @param chatUi true => the injected bridge draws the full chatbot UI on the
     *               page (Web Chat screen); false => page stays as-is (panel).
     */
    public static PerchanceBrain attach(Context ctx, WebView web, Listener l, boolean chatUi, AluAppBridge.Host host) {
        PerchanceBrain b = new PerchanceBrain(web, ctx);
        b.listener = l;
        b.chatUi = chatUi;
        b.bridgeJs = readAsset(ctx, "alu_bridge.js");
        b.appBridge = new AluAppBridge(ctx, chatUi, host);
        try {
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            s.setBuiltInZoomControls(false);
            s.setMediaPlaybackRequiresUserGesture(true);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            try { s.setUserAgentString(s.getUserAgentString().replace("; wv", "")); } catch (Throwable ignored) {}
            try { b.userAgent = s.getUserAgentString(); } catch (Throwable ignored) {}
            try { CookieManager.getInstance().setAcceptCookie(true);
                  CookieManager.getInstance().setAcceptThirdPartyCookies(web, true); } catch (Throwable ignored) {}
        } catch (Throwable ignored) { }

        try { web.addJavascriptInterface(b, "Android"); } catch (Throwable ignored) { }
        try { web.addJavascriptInterface(b.appBridge, "ALU"); } catch (Throwable ignored) { }

        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView v, String u, Bitmap f) {
                b.bridgeReady = false;
                b.bridgeVersion = 0;
                b.pageLoaded = false;
                b.injected = false;
                b.state("page khul rahi hai…");
            }
            /**
             * v3 — AUTO-BRIDGE. Perchance renders the generator in a cross-origin
             * iframe (<hash>.perchance.org/<name>). We fetch that document ourselves
             * and append the bridge <script> — so the page needs NO pasted code; any
             * generator that imports ai-text-plugin works out of the box.
             */
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                try {
                    WebResourceResponse r = b.maybeInjectBridge(req);
                    if (r != null) return r;
                } catch (Throwable ignored) { }
                return super.shouldInterceptRequest(v, req);
            }
            @Override public void onPageFinished(WebView v, String u) {
                b.pageLoaded = true;
                b.installRelay();
                b.state("page khuli — bridge ka intezaar");
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                try {
                    if (req != null && req.isForMainFrame()) {
                        b.lastError = "page khuli nahi (internet ya URL check karo)";
                        b.state(b.lastError);
                    }
                } catch (Throwable ignored) { }
            }
        });

        ACTIVE = b;
        return b;
    }

    public void detach() {
        destroyed = true;
        Pending p = pending;
        if (p != null) { p.error = "page band ho gayi"; p.latch.countDown(); }
        try { web.removeJavascriptInterface("Android"); } catch (Throwable ignored) { }
        try { web.removeJavascriptInterface("ALU"); } catch (Throwable ignored) { }
        if (ACTIVE == this) ACTIVE = null;
    }

    public void load(final String url) {
        final String u = (url == null || url.trim().length() == 0) ? DEFAULT_URL : url.trim();
        generatorName = generatorNameOf(u);
        post(() -> { try { web.loadUrl(u); } catch (Throwable t) { lastError = "load fail"; } });
    }

    public void reload() {
        post(() -> { try { web.reload(); } catch (Throwable ignored) { } });
    }

    public boolean ready() { return bridgeReady && !destroyed; }
    public boolean injected() { return injected; }
    public AluAppBridge appBridge() { return appBridge; }
    public void setHost(AluAppBridge.Host h) { if (appBridge != null) appBridge.setHost(h); }

    static String readAsset(Context ctx, String name) {
        try {
            InputStream in = ctx.getAssets().open(name);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) { return ""; }
    }
    public String generatorName() { return generatorName; }

    /** "https://perchance.org/d4yeutq0b1?x" -> "d4yeutq0b1". Pure, unit-testable. */
    public static String generatorNameOf(String url) {
        if (url == null) return "";
        String u = url.trim();
        int q = u.indexOf('?'); if (q >= 0) u = u.substring(0, q);
        int h = u.indexOf('#'); if (h >= 0) u = u.substring(0, h);
        int i = u.indexOf("://"); if (i >= 0) u = u.substring(i + 3);
        int sl = u.indexOf('/');
        if (sl < 0) return "";
        String path = u.substring(sl + 1);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.contains("/")) path = path.substring(path.lastIndexOf('/') + 1);
        return path;
    }

    /** Is this request the generator document we must patch? Pure, unit-testable. */
    public static boolean isGeneratorDocument(String host, String path, String name, String accept) {
        if (host == null || path == null || name == null || name.length() == 0) return false;
        if (!host.endsWith(".perchance.org") && !host.equals("perchance.org")) return false;
        if (host.equals("perchance.org") || host.equals("www.perchance.org")) return false; // outer shell
        String p = path; while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (!p.equals("/" + name)) return false;
        return accept == null || accept.length() == 0 || accept.contains("text/html") || accept.contains("*/*");
    }

    /** Append the bridge before &lt;/body&gt; (or at the end). Pure, unit-testable. */
    public static String injectBridge(String html) { return injectBridge(html, "", false); }

    public static String injectBridge(String html, String js, boolean chatUi) {
        if (html == null) return null;
        if (html.contains("__aluBridgeV2")) return html;         // page already has pasted code
        String tag = "<script>" + (chatUi ? "window.__ALU_CHAT_UI=true;" : "") + js + "</script>";
        int i = html.toLowerCase().lastIndexOf("</body>");
        if (i < 0) return html + tag;
        return html.substring(0, i) + tag + html.substring(i);
    }

    private WebResourceResponse maybeInjectBridge(WebResourceRequest req) {
        if (req == null || destroyed) return null;
        if (!"GET".equalsIgnoreCase(req.getMethod())) return null;
        android.net.Uri u = req.getUrl();
        if (u == null) return null;
        Map<String, String> hs = req.getRequestHeaders();
        String accept = hs == null ? "" : hs.get("Accept");
        if (!isGeneratorDocument(u.getHost(), u.getPath(), generatorName, accept)) return null;

        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(u.toString()).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", userAgent == null || userAgent.length() == 0
                    ? "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36" : userAgent);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8");
            c.setRequestProperty("Accept-Language", "en-IN,en;q=0.9,hi;q=0.8");
            c.setRequestProperty("Referer", "https://perchance.org/");
            if (hs != null) {
                for (Map.Entry<String, String> e : hs.entrySet()) {
                    String k = e.getKey();
                    if (k == null) continue;
                    String kl = k.toLowerCase();
                    if (kl.startsWith("sec-fetch") || kl.equals("upgrade-insecure-requests")) c.setRequestProperty(k, e.getValue());
                }
            }
            try {
                String ck = CookieManager.getInstance().getCookie(u.toString());
                if (ck != null && ck.length() > 0) c.setRequestProperty("Cookie", ck);
            } catch (Throwable ignored) { }

            int code = c.getResponseCode();
            if (code != 200) return null;                      // let WebView handle challenges / errors
            String ct = c.getContentType();
            if (ct != null && !ct.toLowerCase().contains("html")) return null;
            try {
                Map<String, List<String>> rh = c.getHeaderFields();
                List<String> sc = rh == null ? null : rh.get("Set-Cookie");
                if (sc != null) for (String v : sc) CookieManager.getInstance().setCookie(u.toString(), v);
            } catch (Throwable ignored) { }

            InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            String html = new String(bo.toByteArray(), "UTF-8");
            if (!html.toLowerCase().contains("<html")) return null;
            String patched = injectBridge(html, bridgeJs, chatUi);
            injected = true;
            state("bridge inject ho gaya — plugin ka intezaar");
            return new WebResourceResponse("text/html", "utf-8",
                    new ByteArrayInputStream(patched.getBytes("UTF-8")));
        } catch (Throwable t) {
            lastError = "inject fail: " + t.getClass().getSimpleName();
            return null;
        } finally {
            try { if (c != null) c.disconnect(); } catch (Throwable ignored) { }
        }
    }



    public boolean pageLoaded() { return pageLoaded; }
    public String lastError() { return lastError; }
    public String stateText() { return lastState; }
    public int bridgeVersion() { return bridgeVersion; }
    public void setListener(Listener l) { this.listener = l; }

    /** Human-readable link health for the header / panel: "READY • ping 0.4s". */
    public String healthText() {
        if (destroyed) return "band";
        if (!pageLoaded) return "page load ho raha hai";
        if (!bridgeReady) return injected ? "bridge inject hua — plugin ka intezaar" : "bridge ka intezaar";
        long age = lastPingAt == 0 ? -1 : (System.currentTimeMillis() - lastPingAt);
        if (age < 0) return "READY v" + bridgeVersion;
        if (age < 15000) return "READY v" + bridgeVersion + " • ping " + (age / 1000) + "s";
        return "READY par ping ruka (" + (age / 1000) + "s) — page check karo";
    }

    /** Small stats line for the setup/report UI. */
    public String statsText() {
        return "asks " + askCount + ", ok " + okCount + ", fail " + failCount;
    }

    /* ------------------------------ ask -------------------------------- */

    /**
     * ORDER mode — the model decides: answer directly or call one tool.
     * The page builds the instruction (its ALU_SYSTEM_PROMPT + tools + state).
     */
    public Answer askOrder(String userText, String stateText, String toolsText, long timeoutMs) {
        JSONObject f = new JSONObject();
        try {
            f.put("text", userText == null ? "" : userText);
            f.put("state", stateText == null ? "" : stateText);
            f.put("tools", toolsText == null ? "" : toolsText);
        } catch (Throwable ignored) { }
        return ask("order", f, "{", timeoutMs);
    }

    /** SAY mode — the tool already ran; the model just phrases the result. */
    public Answer askSay(String userText, String tool, String resultText, long timeoutMs) {
        JSONObject f = new JSONObject();
        try {
            f.put("text", userText == null ? "" : userText);
            f.put("tool", tool == null ? "" : tool);
            f.put("result", resultText == null ? "" : resultText);
        } catch (Throwable ignored) { }
        return ask("say", f, "{", timeoutMs);
    }

    /** RAW mode — plain question, no JSON forcing (TEST button uses this). */
    public Answer askText(String instruction, long timeoutMs) {
        JSONObject f = new JSONObject();
        try { f.put("text", instruction == null ? "" : instruction); } catch (Throwable ignored) { }
        return ask("raw", f, "", timeoutMs);
    }

    /** Blocking ask with one automatic retry. Call from a worker thread. */
    public Answer ask(String mode, JSONObject fields, String startWith, long timeoutMs) {
        Answer first = askOnce(mode, fields, startWith, timeoutMs);
        if (first.ok) return first;
        // A timeout or a "busy" page is worth one more try — the plugin queues.
        if (first.error != null && (first.error.contains("busy") || first.error.contains("timeout")
                || first.error.contains("jawab nahi"))) {
            if (!sleep(2500L)) { first.error = "page band ho gayi"; return first; }
            Answer second = askOnce(mode, fields, startWith, timeoutMs);
            if (second.ok) return second;
            if (second.error != null && second.error.length() > 0) return second;
        }
        return first;
    }

    private Answer askOnce(String mode, JSONObject fields, String startWith, long timeoutMs) {
        Answer a = new Answer();
        askCount++;
        if (destroyed) { a.error = "page band ho gayi"; failCount++; return a; }

        synchronized (askLock) {
            if (!waitReady(8000L)) {
                if (destroyed) a.error = "page band ho gayi";
                else if (!pageLoaded) a.error = "Perchance page khuli nahi (internet ya URL check karo)";
                else a.error = "bridge ready nahi hua — page me bridge code paste kiya hai?";
                lastError = a.error;
                failCount++;
                return a;
            }

            Pending p = new Pending(seq.getAndIncrement());
            pending = p;

            final String json = buildAskJson(p.id, mode, fields, startWith);
            if (json == null) { a.error = "payload banane me dikkat"; failCount++; return a; }
            final String js = sendJs(json);
            post(() -> {
                try {
                    web.evaluateJavascript(js, null);
                } catch (Throwable t) {
                    p.error = "evaluate fail";
                    p.latch.countDown();
                }
            });

            boolean done;
            try {
                done = p.latch.await(Math.max(5000L, timeoutMs), TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                done = false;
                p.error = "interrupted";
            }

            if (!done) {
                a.error = "timeout — jawab nahi aaya. Agar page par \"Verifying\" ya ad dikh raha hai to "
                        + "🌐 panel kholo (aur zaroorat pade to Perchance me login karo), phir dobara bhejo.";
            } else if (p.busy) {
                a.error = "busy — page pehla jawab bana raha hai";
            } else {
                a.ok = p.ok;
                a.text = p.text;
                a.error = p.error;
            }
            if (pending == p) pending = null;
        }

        if (a.ok) { okCount++; lastError = ""; }
        else {
            failCount++;
            if (a.error == null || a.error.length() == 0) a.error = "unknown error";
            lastError = a.error;
        }
        return a;
    }

    private static boolean sleep(long ms) {
        try { Thread.sleep(ms); return true; }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false; }
    }

    private boolean waitReady(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (destroyed) return false;
            if (bridgeReady) return true;
            if (!sleep(200L)) return false;
        }
        return bridgeReady;
    }

    /**
     * The JS that hands one ask to the page. It is posted to every iframe
     * (Perchance renders the generator inside one) and to the top window too.
     * The page de-dupes by id, so double delivery is harmless.
     */
    private String sendJs(String askJson) {
        return "(function(){var m=" + askJson + ";try{if(window.__aluAsk){window.__aluAsk(m);}}catch(e){}try{var fs=document.querySelectorAll('iframe');"
                + "for(var i=0;i<fs.length;i++){try{if(fs[i].contentWindow)fs[i].contentWindow.postMessage(m,'*');}catch(e){}}}catch(e){}"
                + "try{window.postMessage(m,'*');}catch(e){}})();";
    }

    /**
     * Lives in the TOP frame. Forwards page -> app events that arrive through
     * postMessage (the generator iframe cannot always reach the JS interface
     * directly, so this relay is the guaranteed path).
     */
    private static final String RELAY_JS = "(function(){try{"
            + "if(window.__aluRelay)return;window.__aluRelay=1;"
            + "window.addEventListener('message',function(e){var d=e.data;"
            + "if(!d||d.__alu!==1)return;if(d.kind==='ask')return;"
            + "try{Android.onPerchanceEvent(JSON.stringify(d));}catch(err){}});"
            + "}catch(err){}})();";

    private void installRelay() {
        post(() -> { try { web.evaluateJavascript(RELAY_JS, null); } catch (Throwable ignored) { } });
    }

    private void post(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else ui.post(r);
    }

    private void state(final String s) {
        lastState = s;
        final Listener l = listener;
        if (l == null) return;
        ui.post(() -> { try { l.onState(s); } catch (Throwable ignored) { } });
    }

    /* -------------------------- from the page -------------------------- */

    @JavascriptInterface
    public void onPerchanceEvent(String json) {
        try {
            Event ev = parseEvent(json);
            if (ev == null) return;

            if ("ping".equals(ev.kind)) {
                lastPingAt = System.currentTimeMillis();
                if (!bridgeReady) bridgeReady = true;
                return;
            }
            if ("ready".equals(ev.kind)) {
                bridgeReady = true;
                if (ev.version > 0) bridgeVersion = ev.version;
                lastPingAt = System.currentTimeMillis();
                lastError = "";
                state("READY ✓ — app aur page connected" + (ev.version > 0 ? " (bridge v" + ev.version + ")" : "")
                        + (injected ? " • auto-bridge" : ""));
                return;
            }
            if ("info".equals(ev.kind)) {
                state(ev.text);
                return;
            }
            if ("start".equals(ev.kind)) {
                state("jawab ban raha hai…");
                return;
            }
            if ("chunk".equals(ev.kind)) {
                final Pending p = pending;
                final Listener l = listener;
                if (l == null || p == null || p.id != ev.id) return;
                final String so = ev.text;
                ui.post(() -> { try { l.onChunk(so); } catch (Throwable ignored) { } });
                return;
            }
            if ("done".equals(ev.kind) || "error".equals(ev.kind) || "busy".equals(ev.kind)) {
                final Pending p = pending;
                if (p == null || p.id != ev.id) return;   // stale or duplicate
                if ("done".equals(ev.kind)) { p.ok = true; p.text = ev.text; }
                else if ("busy".equals(ev.kind)) { p.ok = false; p.busy = true; p.error = "busy"; }
                else { p.ok = false; p.error = ev.text; }
                p.latch.countDown();
            }
        } catch (Throwable ignored) {
            // never let a broken page event crash the app
        }
    }
}
