package com.digitalalu.alu.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal cloud client — zero third-party dependencies (no OkHttp/Retrofit).
 *
 * Gemini can be reached two ways, and which one works depends on the key:
 *
 *   1. Interactions API    POST /v1beta/interactions        (new, agentic, tool steps)
 *   2. generateContent     POST /v1beta/models/X:generateContent   (legacy, always there)
 *
 * …plus any OpenAI-compatible /chat/completions endpoint.
 *
 * "Key daali hai par kaam nahi kar raha" almost always means one of two things:
 *   • the model name in the app does not exist for that key, or
 *   • the key cannot use the newer Interactions endpoint.
 *
 * So this class can diagnose itself: {@link #diagnose} lists the models the key
 * can actually use, then tries both wires against the real API and reports the
 * exact HTTP status and message for each. AI Setup shows that report, lets the
 * user tap APPLY BEST, and the chat path then uses the combination that worked.
 */
public class CloudLlm {

    /* ------------------------------ wires ------------------------------ */

    public static final int WIRE_AUTO = 0;          // try interactions, fall back
    public static final int WIRE_INTERACTIONS = 1;
    public static final int WIRE_GENERATE = 2;      // generateContent + JSON protocol

    /** Gemini models we would like, best first, when we have to guess. */
    public static final String[] PREFERRED_MODELS = {
        "gemini-3.8-flash", "gemini-3-flash", "gemini-flash-latest",
        "gemini-3.1-flash", "gemini-2.5-flash", "gemini-2.0-flash",
        "gemini-3.8-pro", "gemini-2.5-pro",
    };

    public static class Reply {
        public boolean ok;
        public String text = "";
        public String error = "";
        public int httpCode;
    }

    /** One tool the model asked us to run. */
    public static class ToolCallStep {
        public String name = "";
        public String callId = "";
        public JSONObject args = new JSONObject();
    }

    /** One full interaction (a turn + the steps the model took). */
    public static class Interaction {
        public boolean ok;
        public String error = "";
        public int httpCode;
        public String id = "";
        public String text = "";
        public final List<ToolCallStep> calls = new ArrayList<ToolCallStep>();
    }

    /* ==================================================================== */
    /*  Wire resolution                                                      */
    /* ==================================================================== */

    /** Which wire the chat should use right now. */
    public static int wireFor(AiSettings s) {
        if (s == null) return WIRE_GENERATE;
        if (s.provider == AiSettings.PROVIDER_OPENAI) return -1;   // handled separately
        return s.cloudWire == WIRE_INTERACTIONS ? WIRE_INTERACTIONS : WIRE_GENERATE;
    }

    /* ==================================================================== */
    /*  Model discovery — what can this key actually use?                    */
    /* ==================================================================== */

    /** GET /v1beta/models — every model this API key is allowed to call. */
    public static List<String> listModels(AiSettings s) {
        List<String> out = new ArrayList<String>();
        try {
            String url = trimSlash(s.effectiveBaseUrl()) + "/models?pageSize=200";
            String raw = get(url, s);
            if (raw == null) return out;
            return parseModelNames(raw);
        } catch (Throwable t) {
            return out;
        }
    }

    /** Pure parser so it can be unit tested without a network. */
    public static List<String> parseModelNames(String json) {
        List<String> out = new ArrayList<String>();
        try {
            JSONObject o = new JSONObject(json);
            JSONArray arr = o.optJSONArray("models");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.optJSONObject(i);
                if (m == null) continue;
                String name = m.optString("name", "");
                if (name.startsWith("models/")) name = name.substring(7);
                if (name.length() == 0) continue;
                JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                boolean canGenerate = true;
                if (methods != null) {
                    canGenerate = false;
                    for (int j = 0; j < methods.length(); j++) {
                        String mm = methods.optString(j, "");
                        if ("generateContent".equals(mm) || "interactions".equals(mm)
                                || "createInteraction".equals(mm)) {
                            canGenerate = true;
                            break;
                        }
                    }
                }
                if (canGenerate) out.add(name);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /**
     * Best usable model out of what the key offers: prefer our shortlist order,
     * then any flash model, then anything that is not an embedding/image model.
     */
    public static String pickBestModel(List<String> available) {
        if (available == null || available.isEmpty()) return "";
        for (String want : PREFERRED_MODELS) {
            for (String have : available) {
                if (want.equalsIgnoreCase(have)) return have;
            }
        }
        for (String have : available) {
            if (isChatModel(have) && have.toLowerCase(java.util.Locale.US).contains("flash")) {
                return have;
            }
        }
        for (String have : available) {
            if (isChatModel(have)) return have;
        }
        return "";
    }

    /** Filters out embedding / image / tts / video / robotics models. */
    public static boolean isChatModel(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(java.util.Locale.US);
        if (!n.startsWith("gemini")) return false;
        String[] junk = {"embedding", "aqa", "imagen", "image", "tts", "veo", "robotics",
                         "learnlm", "gemma", "computer-use", "thinking-exp"};
        for (String j : junk) {
            if (n.contains(j)) return false;
        }
        return true;
    }

    /* ==================================================================== */
    /*  Plain single-shot ask (generateContent / OpenAI)                     */
    /* ==================================================================== */

    public static Reply ask(AiSettings s, String system, String user) {
        Reply r = new Reply();
        if (s == null || s.apiKey == null || s.apiKey.trim().length() == 0) {
            r.error = "API key set nahi hai";
            return r;
        }
        try {
            JSONObject body = new JSONObject();
            String endpoint;
            boolean openai = s.provider == AiSettings.PROVIDER_OPENAI;
            String base = trimSlash(s.effectiveBaseUrl());
            String model = s.effectiveModel();

            if (openai) {
                endpoint = base + "/chat/completions";
                body.put("model", model);
                JSONArray msgs = new JSONArray();
                msgs.put(new JSONObject().put("role", "system").put("content", system));
                msgs.put(new JSONObject().put("role", "user").put("content", user));
                body.put("messages", msgs);
                body.put("temperature", 0.2);
                body.put("max_tokens", 600);
            } else {
                endpoint = base + "/models/" + model + ":generateContent";
                body.put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject().put("text", system))));
                body.put("contents", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", user)))));
                body.put("generationConfig", new JSONObject()
                        .put("temperature", 0.2)
                        .put("maxOutputTokens", 900));
            }

            String raw = post(endpoint, s, openai, body, r);
            if (raw == null) {
                if (r.error.length() == 0) r.error = "network error";
                return r;
            }
            r.text = extractText(raw, openai);
            r.ok = r.text != null && r.text.trim().length() > 0;
            if (!r.ok) r.error = "Model ne khali jawab diya";
            return r;
        } catch (Throwable t) {
            r.error = t.getClass().getSimpleName() + ": " + t.getMessage();
            return r;
        }
    }

    /** Pulls the assistant text out of either response shape. Pure. */
    public static String extractText(String raw, boolean openai) {
        try {
            JSONObject o = new JSONObject(raw);
            if (o.has("error")) return "";
            if (openai) {
                JSONArray choices = o.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject msg = choices.optJSONObject(0) == null ? null
                            : choices.optJSONObject(0).optJSONObject("message");
                    if (msg != null) return msg.optString("content", "");
                }
                return "";
            }
            // generateContent
            JSONArray cands = o.optJSONArray("candidates");
            if (cands != null && cands.length() > 0) {
                JSONObject c0 = cands.optJSONObject(0);
                JSONObject content = c0 == null ? null : c0.optJSONObject("content");
                if (content != null) {
                    JSONArray parts = content.optJSONArray("parts");
                    StringBuilder sb = new StringBuilder();
                    if (parts != null) {
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject p = parts.optJSONObject(i);
                            if (p != null) sb.append(p.optString("text", ""));
                        }
                    }
                    return sb.toString();
                }
            }
            // interactions (in case a caller feeds one in)
            String t = o.optString("output_text", "");
            if (t.length() > 0) return t;
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** Tiny connectivity probe used by the setup screen TEST button. */
    public static Reply test(AiSettings s) {
        return ask(s, "Reply with exactly this JSON and nothing else: {\"say\":\"ok\",\"tool\":null,\"args\":{}}",
                "ping");
    }

    /** Human-readable message from any Google/OpenAI error body. Pure. */
    public static String errorMessage(String raw) {
        if (raw == null || raw.trim().length() == 0) return "(khali jawab)";
        String t = raw.trim();
        if (t.startsWith("[")) {
            // live API shape: [{"error":{"code":400,"message":"…"}}]
            try {
                JSONArray arr = new JSONArray(t);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject ev = arr.optJSONObject(i);
                    if (ev != null && ev.has("error")) return errorMessage(ev.toString());
                }
            } catch (Throwable ignored) { }
            return shorten(t);
        }
        try {
            JSONObject o = new JSONObject(t);
            JSONObject e = o.optJSONObject("error");
            if (e != null) {
                String msg = e.optString("message", "");
                String status = e.optString("status", "");
                if (msg.length() > 0) return status.length() > 0 ? msg + " [" + status + "]" : msg;
            }
            if (o.has("message")) return o.optString("message");
        } catch (Throwable ignored) { }
        return shorten(raw);
    }

    /* ==================================================================== */
    /*  Interactions API — the agentic path                                 */
    /* ==================================================================== */

    public static JSONArray toolSchema() {
        JSONArray tools = new JSONArray();
        try {
            for (ToolRegistry.Tool t : ToolRegistry.all()) {
                JSONObject props = new JSONObject();
                JSONArray required = new JSONArray();
                for (String k : JsonGrammar.argKeysOf(t.signature)) {
                    props.put(k, new JSONObject().put("type", "string")
                            .put("description", "number/naam/text"));
                    required.put(k);
                }
                JSONObject params = new JSONObject();
                params.put("type", "object");
                params.put("properties", props);
                params.put("required", required);
                JSONObject tool = new JSONObject();
                tool.put("type", "function");
                tool.put("name", t.name);
                tool.put("description", t.help);
                tool.put("parameters", params);
                tools.put(tool);
            }
        } catch (Throwable ignored) { }
        return tools;
    }

    public static Interaction create(AiSettings s, String inputText,
                                     JSONArray functionResults, String previousId) {
        Interaction out = new Interaction();
        if (s == null || s.apiKey == null || s.apiKey.trim().length() == 0) {
            out.error = "API key set nahi hai";
            return out;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("model", s.effectiveModel());
            if (functionResults != null && functionResults.length() > 0) {
                body.put("input", functionResults);
            } else {
                // REST expects the typed form; the SDK builds this for you.
                JSONArray input = new JSONArray();
                JSONObject step = new JSONObject();
                step.put("type", "user_input");
                step.put("content", inputText == null ? "" : inputText);
                input.put(step);
                body.put("input", input);
            }
            JSONArray tools = toolSchema();
            if (tools.length() > 0) body.put("tools", tools);
            body.put("store", false);
            if (previousId != null && previousId.length() > 0) {
                body.put("previous_interaction_id", previousId);
            }

            Reply err = new Reply();
            String raw = post(trimSlash(s.effectiveBaseUrl()) + "/interactions", s, false, body, err);
            if (raw == null) {
                out.httpCode = err.httpCode;
                out.error = err.error.length() > 0 ? err.error : "network error";
                return out;
            }
            parseResponse(raw, out);
            return out;
        } catch (Throwable t) {
            out.error = t.getClass().getSimpleName() + ": " + t.getMessage();
            return out;
        }
    }

    /**
     * Parses whatever the endpoint actually sent.
     *
     * Verified against the live API on 2026-10-07: when the key is rejected the
     * Interactions endpoint answers with a JSON *array* — `[{"error":{...}}]` —
     * not an object, and a successful streamed turn can also come back as an
     * array of events. Code that assumes `{"id":…,"steps":[…]}` throws on both,
     * treats a perfectly good answer as a failure, and quietly falls back to
     * rules. So: accept an object, an array of steps, or an array of events.
     */
    public static void parseResponse(String raw, Interaction out) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() == 0) {
            out.error = "(khali jawab)";
            return;
        }
        if (t.startsWith("[")) {
            JSONArray arr;
            try {
                arr = new JSONArray(t);
            } catch (Throwable e) {
                out.error = shorten(t);
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ev = arr.optJSONObject(i);
                if (ev == null) continue;
                if (ev.has("error")) { out.error = errorMessage(ev.toString()); return; }
                if (ev.has("interaction")) {
                    JSONObject inner = ev.optJSONObject("interaction");
                    if (inner != null) parseInto(inner, out);
                    continue;
                }
                String type = ev.optString("type", "");
                if (ev.has("steps")) {
                    parseInto(ev, out);
                } else if ("function_call".equals(type)) {
                    addCall(ev, out);
                } else if ("model_output".equals(type)) {
                    String txt = textOfStep(ev);
                    if (txt.trim().length() > 0) out.text = txt;
                } else if (ev.has("delta")) {
                    JSONObject d = ev.optJSONObject("delta");
                    if (d != null && "text".equals(d.optString("type", ""))) {
                        out.text = out.text + d.optString("text", "");
                    }
                }
            }
            finish(out);
            return;
        }
        JSONObject o;
        try {
            o = new JSONObject(t);
        } catch (Throwable e) {
            out.error = shorten(t);
            return;
        }
        if (o.has("error")) { out.error = errorMessage(t); return; }
        if (o.has("interaction")) {
            JSONObject inner = o.optJSONObject("interaction");
            if (inner != null) { parseInto(inner, out); finish(out); return; }
        }
        parseInto(o, out);
        finish(out);
    }

    /** Usable = we got text or at least one tool call, and no error. */
    private static void finish(Interaction out) {
        if (out.error.length() > 0) return;
        if (out.text != null && out.text.trim().length() > 0) { out.ok = true; return; }
        if (!out.calls.isEmpty()) { out.ok = true; return; }
        out.ok = out.id != null && out.id.length() > 0;
    }

    public static void parseInto(JSONObject o, Interaction out) {
        out.id = o.optString("id", "");
        out.text = o.optString("output_text", "");
        JSONArray steps = o.optJSONArray("steps");
        if (steps == null) return;
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step == null) continue;
            String type = step.optString("type", "");
            if ("function_call".equals(type)) {
                addCall(step, out);
            } else if ("model_output".equals(type)) {
                String t = textOfStep(step);
                if (t.trim().length() > 0) out.text = t;
            }
        }
    }

    private static void addCall(JSONObject step, Interaction out) {
        ToolCallStep c = new ToolCallStep();
        c.name = step.optString("name", "");
        c.callId = step.optString("id", step.optString("call_id", ""));
        JSONObject a = step.optJSONObject("arguments");
        if (a != null) c.args = a;
        if (c.name.length() > 0) out.calls.add(c);
    }

    private static String textOfStep(JSONObject step) {
        StringBuilder sb = new StringBuilder();
        JSONArray content = step.optJSONArray("content");
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject c = content.optJSONObject(i);
                if (c == null) continue;
                if ("text".equals(c.optString("type", ""))) sb.append(c.optString("text", ""));
            }
        }
        if (sb.length() == 0 && step.has("text")) sb.append(step.optString("text", ""));
        return sb.toString();
    }

    public static JSONObject functionResult(ToolCallStep call, String resultText) {
        JSONObject out = new JSONObject();
        try {
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("type", "text").put("text",
                    resultText == null ? "" : resultText));
            out.put("type", "function_result");
            out.put("name", call.name);
            out.put("call_id", call.callId);
            out.put("result", parts);
        } catch (Throwable ignored) { }
        return out;
    }

    /* ==================================================================== */
    /*  Self-diagnosis                                                       */
    /* ==================================================================== */

    public static class Diag {
        public boolean keyPresent;
        public String modelsError = "";
        public List<String> models = new ArrayList<String>();
        public String bestModel = "";
        public Reply generateTest;      // generateContent against effectiveModel()
        public Reply generateTestBest;  // generateContent against bestModel
        public Interaction interactionsTest;
        public String recommendModel = "";
        public int recommendWire = WIRE_GENERATE;
        public boolean somethingWorks;

        /** Copy-pasteable report — the user can send this to me. */
        public String report(AiSettings s) {
            StringBuilder sb = new StringBuilder();
            sb.append("ALU CLOUD TEST\n");
            sb.append("provider   : ").append(s.providerName()).append('\n');
            sb.append("base url   : ").append(s.effectiveBaseUrl()).append('\n');
            sb.append("app model  : ").append(s.effectiveModel()).append('\n');
            sb.append("key        : ").append(keyPresent ? fingerprint(s.apiKey) : "NOT SET").append('\n');
            sb.append("wire       : ").append(s.cloudWireName()).append('\n');
            sb.append('\n');
            sb.append("MODELS (").append(models.size()).append(")");
            if (modelsError.length() > 0) sb.append("  ERROR: ").append(modelsError);
            sb.append('\n');
            for (int i = 0; i < models.size() && i < 12; i++) {
                sb.append("  - ").append(models.get(i)).append('\n');
            }
            if (models.size() > 12) sb.append("  ... ").append(models.size() - 12).append(" more\n");
            if (bestModel.length() > 0) sb.append("best model : ").append(bestModel).append('\n');
            sb.append('\n');
            sb.append("generateContent(app model) : ").append(line(generateTest)).append('\n');
            if (generateTestBest != null) {
                sb.append("generateContent(best)      : ").append(line(generateTestBest)).append('\n');
            }
            sb.append("interactions API           : ")
              .append(interactionsTest == null ? "(skipped)"
                      : (interactionsTest.ok ? "OK (id " + interactionsTest.id + ", "
                              + interactionsTest.calls.size() + " tool step)"
                              : "FAIL " + interactionsTest.error)).append('\n');
            sb.append('\n');
            sb.append(somethingWorks
                    ? "=> CHALEGA: model " + recommendModel + " / "
                      + (recommendWire == WIRE_INTERACTIONS ? "Interactions API" : "generateContent")
                    : "=> KOI BHI WIRE CHALA NAHI — upar ka error dekhein");
            return sb.toString();
        }

        private String line(Reply r) {
            if (r == null) return "(skipped)";
            return r.ok ? "OK" : "FAIL " + (r.error.length() > 0 ? r.error : "HTTP " + r.httpCode);
        }
    }

    /** Shows the key without ever printing the whole thing. */
    public static String fingerprint(String key) {
        if (key == null || key.trim().length() == 0) return "not set";
        String k = key.trim();
        if (k.length() <= 6) return "***";
        return k.substring(0, 4) + "…" + k.substring(k.length() - 3)
                + " (" + k.length() + " chars)";
    }

    /**
     * Runs the full check: which models exist, which wire works, and which
     * combination to keep. Every call has a short timeout so the screen can't hang.
     */
    public static Diag diagnose(AiSettings s, boolean deep) {
        Diag d = new Diag();
        d.keyPresent = s != null && s.apiKey != null && s.apiKey.trim().length() > 6;
        if (!d.keyPresent) return d;

        try {
            String url = trimSlash(s.effectiveBaseUrl()) + "/models?pageSize=200";
            Reply err = new Reply();
            String raw = getWith(url, s, err);
            if (raw == null) {
                d.modelsError = err.error.length() > 0 ? err.error : "HTTP " + err.httpCode;
            } else {
                List<String> all = parseModelNames(raw);
                for (String m : all) if (isChatModel(m)) d.models.add(m);
                if (d.models.isEmpty()) d.modelsError = "key me koi chat model nahi mila";
                d.bestModel = pickBestModel(d.models);
            }
        } catch (Throwable t) {
            d.modelsError = t.getClass().getSimpleName() + ": " + t.getMessage();
        }

        // 1) the model the app currently has
        d.generateTest = quickProbe(s, s.effectiveModel());
        if (d.generateTest.ok) {
            d.recommendModel = s.effectiveModel();
            d.recommendWire = WIRE_GENERATE;
            d.somethingWorks = true;
        }

        // 2) the best model the key offers
        if (d.bestModel.length() > 0
                && !d.bestModel.equalsIgnoreCase(s.effectiveModel())) {
            d.generateTestBest = quickProbe(s, d.bestModel);
            if (d.generateTestBest.ok && !d.somethingWorks) {
                d.recommendModel = d.bestModel;
                d.recommendWire = WIRE_GENERATE;
                d.somethingWorks = true;
            }
        }

        // 3) the newer Interactions endpoint (only when it might actually change things)
        if (deep && d.somethingWorks) {
            try {
                AiSettings tmp = cloneForModel(s, d.recommendModel);
                d.interactionsTest = create(tmp, "ping", null, null);
                if (d.interactionsTest.ok) d.recommendWire = WIRE_INTERACTIONS;
            } catch (Throwable t) {
                d.interactionsTest = new Interaction();
                d.interactionsTest.error = t.getClass().getSimpleName();
            }
        }
        return d;
    }

    /** One cheap call to confirm a model name works. */
    public static Reply quickProbe(AiSettings s, String model) {
        if (model == null || model.trim().length() == 0) return new Reply();
        return test(cloneForModel(s, model));
    }

    private static AiSettings cloneForModel(AiSettings s, String model) {
        AiSettings c = new AiSettings();
        c.mode = s.mode;
        c.provider = s.provider;
        c.apiKey = s.apiKey;
        c.baseUrl = s.baseUrl;
        c.cloudModel = model;
        c.cloudWire = s.cloudWire;
        c.dailyCap = s.dailyCap;
        c.usedToday = s.usedToday;
        return c;
    }

    /* ==================================================================== */

    private static String post(String endpoint, AiSettings s, boolean openai,
                               JSONObject body, Reply fillError) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (openai) {
                conn.setRequestProperty("Authorization", "Bearer " + s.apiKey.trim());
            } else {
                conn.setRequestProperty("x-goog-api-key", s.apiKey.trim());
            }
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String raw = readAll(is);
            if (code < 200 || code >= 300) {
                if (fillError != null) {
                    fillError.httpCode = code;
                    String msg = errorMessage(raw);
                    fillError.error = "HTTP " + code + ": " + msg;
                }
                return null;
            }
            return raw;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) { }
            }
        }
    }

    private static String get(String url, AiSettings s) {
        return getWith(url, s, new Reply());
    }

    private static String getWith(String url, AiSettings s, Reply err) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            if (s.provider == AiSettings.PROVIDER_OPENAI) {
                conn.setRequestProperty("Authorization", "Bearer " + s.apiKey.trim());
            } else {
                conn.setRequestProperty("x-goog-api-key", s.apiKey.trim());
            }
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String raw = readAll(is);
            if (code < 200 || code >= 300) {
                err.httpCode = code;
                err.error = "HTTP " + code + ": " + errorMessage(raw);
                return null;
            }
            return raw;
        } catch (Throwable t) {
            err.error = t.getClass().getSimpleName() + ": " + t.getMessage();
            return null;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) { }
            }
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private static String trimSlash(String s) {
        String b = s == null ? "" : s;
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    private static String shorten(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }

    public static String systemPrompt() { return IntentProtocol.SYSTEM; }

    public static String userPrompt(String input, String stateText) {
        return IntentProtocol.userPrompt(input, ToolRegistry.promptList(), stateText);
    }
}
