package com.digitalalu.alu.ai;

import android.content.Context;

import com.digitalalu.alu.agent.AgentAppController;
import com.digitalalu.alu.agent.AluNeuralNLP;
import com.digitalalu.alu.agent.OnDeviceTextGenerator;
import com.digitalalu.alu.model.ChatMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OPTION C — Model = brain, Agent hands.
 *
 *   Tier 0  rules      AluNeuralNLP + AgentAppController   free, offline, instant
 *   Tier 1  on-device  MNN model via LocalModelHost        offline, needs a download
 *   Tier 2  cloud      Gemini Interactions / OpenAI-compat  best quality, needs a key
 *
 * The loop is always the same, on every tier:
 *
 *     user  ->  MODEL decides        (tool + args, grammar-forced on-device)
 *           ->  AGENT does the work  (ToolRegistry, deterministic maths)
 *           ->  MODEL sees the result and phrases the answer
 *
 * The model never computes a number itself; the agent never guesses intent.
 */
public class HybridAgent {

    public static final String SRC_OFFLINE = "OFFLINE";
    public static final String SRC_DEVICE = "ON-DEVICE";
    /** v1.1 — FunctionGemma 270M via LiteRT-LM (tool-calling specialist) */
    public static final String SRC_FGEMMA = "FGEMMA";
    public static final String SRC_CLOUD = "CLOUD";
    /** v2.1 — text came from the Perchance page in the agent screen. */
    public static final String SRC_WEB = "PERCHANCE";

    public static class Result {
        public String text = "";
        public String source = SRC_OFFLINE;
        public List<ChatMessage.Action> actions = new ArrayList<ChatMessage.Action>();
        public String note = "";
        /** true when the cloud tier was tried and failed (chat shows the reason) */
        public boolean failed;
    }

    /** why the Perchance tier could not answer (shown to the user verbatim) */
    private String lastWebError = "";

    private static class Pending {
        String tool;
        JSONObject args;
    }

    private final Context appCtx;
    private final AgentAppController controller;
    private final OnDeviceTextGenerator rules;
    private AiSettings cfg;
    private Pending pending;
    private String lastCloudError = "";

    /** How many tool rounds we allow in one cloud turn before we stop. */
    private static final int MAX_CLOUD_ROUNDS = 3;

    /** v2.7 — live progress for the chat screen (called on the worker thread). */
    public interface Progress {
        void onToken(String piece, int count);
        /** v2.9.1 — live thinking text (may be called many times per second). */
        void onThink(String piece);
    }
    private volatile Progress progress;
    private volatile boolean cancelled = false;
    public static final long LOCAL_TIMEOUT_MS = 60000L;
    public void setProgress(Progress p) { this.progress = p; }
    public void cancel() { cancelled = true; }

    public HybridAgent(Context c) {
        this.appCtx = c.getApplicationContext();
        this.controller = new AgentAppController(c);
        this.rules = new OnDeviceTextGenerator(controller);
        this.cfg = AiSettings.load(c);
    }

    public AgentAppController controller() { return controller; }
    public AiSettings config() { return cfg; }
    public void reloadConfig() { cfg = AiSettings.load(appCtx); }
    public OnDeviceTextGenerator.ModelResponse greeting() { return rules.generateResponse(""); }
    public boolean hasPending() { return pending != null; }

    /* =================================================================== */

    public Result handle(final String input) {
        Result r = new Result();
        if (input == null || input.trim().isEmpty()) {
            r.text = rules.generateResponse("").text;
            return r;
        }
        String text = input.trim();

        // 1) waiting for a yes/no on a destructive action?
        if (pending != null) {
            if (isYes(text)) {
                ToolRegistry.ExecResult er = ToolRegistry.executeConfirmed(controller, pending.tool, pending.args);
                pending = null;
                r.text = er.text;
                r.note = "action";
                return r;
            }
            if (isNo(text)) {
                pending = null;
                r.text = "Theek hai, cancel kar diya.";
                return r;
            }
            pending = null;
        }

        // 2) Tier 0 — what does the user actually want?
        AluNeuralNLP.ParsedEntity entity = AluNeuralNLP.parse(text);
        boolean rulesHit = entity.intent != AluNeuralNLP.Intent.UNKNOWN;
        boolean isExplain = entity.intent == AluNeuralNLP.Intent.EXPLAIN_SYSTEM
                         || entity.intent == AluNeuralNLP.Intent.EXPLAIN_FORMULA;

        // Local permitted OR cloud enabled => an AI brain may be used.
        boolean canAi = (cfg.localPermitted() && LocalModelHost.get().isReady())
                || cfg.cloudEnabled() || cfg.webBrainEnabled();
        boolean wantAi = canAi && (cfg.mode == AiSettings.MODE_AI_FIRST || !rulesHit || isExplain);

        // v2.9 FAST PATH: when the rules parser is sure (clear dimensions, "hata do",
        // "estimate dikhao", rate set…) the agent acts in 0 s — same tools, same
        // maths, no 5-10 s model round-trip. The model is reserved for everything
        // the rules can't read: free-form questions, broken Hinglish, explanations.
        if (wantAi && cfg.fastPath && rulesHit && !isExplain && isConfidentRule(entity)) {
            Result fast = new Result();
            fast.source = SRC_OFFLINE;
            OnDeviceTextGenerator.ModelResponse mr = rules.generateResponse(text);
            fast.text = mr.text;
            if (mr.actions != null) fast.actions.addAll(mr.actions);
            fast.note = "\u26a1 turant";
            return fast;
        }

        if (wantAi) {
            try {
                Result ai = askAi(text);
                if (ai != null) return ai;
            } catch (Throwable t) {
                // An AI tier must never take the app down with it.
                Result err = new Result();
                err.source = SRC_OFFLINE;
                err.text = rules.generateResponse(text).text;
                err.note = "AI tier error: " + t.getClass().getSimpleName();
                return err;
            }
        }

        // 3) Tier 0 answer (also the safety net when AI is unavailable/failed)
        Result off = new Result();
        off.text = rules.generateResponse(text).text;
        off.source = SRC_OFFLINE;

        // Nothing is set up AND the user asked something the rules can't do:
        // say exactly what the next step is instead of a vague "samajh nahi aaya".
        if (!canAi) {
            AiReadiness.Step step = readiness();
            if (step != null && !step.ready) {
                off.text = AiReadiness.shortHint(step) + "\n\n" + off.text;
                off.actions.add(new ChatMessage.Action("⬇ MODEL DOWNLOAD KARO", "DOWNLOAD_MODEL"));
                off.note = "";
                return off;
            }
        }
        if (wantAi && cfg.mode == AiSettings.MODE_AI_FIRST) {
            off.note = aiUnavailableReason();
        }
        return off;
    }

    /** Rule intents we trust enough to skip the model entirely. */
    static boolean isConfidentRule(AluNeuralNLP.ParsedEntity e) {
        if (e == null) return false;
        switch (e.intent) {
            case ADD_WINDOW:
            case CALCULATE_SINGLE:
                return e.hasDimensions && e.height > 0 && e.width > 0;
            case UPDATE_PRICE:
                return e.targetRate > 0;
            case VIEW_ESTIMATE:
            case VIEW_CUTTING:
            case VIEW_COSTING:
            case SHOW_WINDOWS:
            case DELETE_LAST:
            case CLEAR_SHEET:
            case LIST_CUSTOMERS:
            case MANUAL_CUTTING:
            case SHARE_EXPORT:
            case HELP:
                return true;
            default:
                return false;
        }
    }

    /** Current logical next step for the user (also drives the setup screen). */
    public AiReadiness.Step readiness() {
        try {
            // Perchance web brain counts as a configured brain: nothing to set up.
            if (cfg.webBrainEnabled() && !cfg.internetOff()) {
                return new AiReadiness.Step(AiReadiness.CODE_READY, "Perchance Web brain ON",
                        "Agent screen me page khulti hai, wahi jawab banata hai.", "", "", true);
            }
            String path = cfg.localModelPath == null ? "" : cfg.localModelPath;
            ModelCatalog.ModelSpec configured = path.length() > 0
                    ? ModelCatalog.byFileName(LocalModelHost.fileName(path)) : null;
            boolean modelConfigured = path.length() > 0;
            boolean downloaded = configured != null && ModelStore.isInstalled(appCtx, configured);
            boolean downloading = false;
            for (ModelCatalog.ModelSpec m : ModelCatalog.list()) {
                if (ModelStore.isInstalled(appCtx, m)) continue;
                if (ModelStore.bytesPresent(appCtx, m) > 0
                        || ModelStore.downloadIdFor(appCtx, m.id) > 0) { downloading = true; break; }
            }
            return AiReadiness.of(cfg.mode, cfg.localPermitted(), LocalLlm.available(),
                    modelConfigured, downloaded, LocalModelHost.get().state(),
                    cfg.cloudEnabled(), cfg.underCap(), downloading);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * One-line truth for the chat header, e.g.
     * "On-Device AI · Qwen3-0.6B-Hindi" or "Cloud AI · gemini-3.8-flash" or "Rules only".
     */
    public String statusLine() {
        try {
            if (cfg.webBrainEnabled()) {
                PerchanceBrain pb = PerchanceBrain.active();
                if (pb != null && pb.ready()) return "Perchance Web \u00b7 READY";
                if (pb != null) return "Perchance Web \u00b7 page load ho raha hai";
                return "Perchance Web \u00b7 page band (menu \u2192 \ud83c\udf10)";
            }
            LocalModelHost h = LocalModelHost.get();
            if (cfg.localPermitted() && h.isReady()) {
                return "On-Device AI · " + LocalModelHost.shortName(cfg.localModelPath);
            }
            if (cfg.localPermitted() && cfg.localConfigured()
                    && h.state() == AiReadiness.STATE_LOADING) {
                return "model load ho raha hai…";
            }
            if (cfg.cloudEnabled()) return "Cloud AI · " + cfg.effectiveModel();
            if (cfg.localPermitted() && cfg.localConfigured()) return "model off (AI Setup)";
            return "Rules only";
        } catch (Throwable t) {
            return "Rules only";
        }
    }

    /* ============================ AI path ============================== */

    /** @return null when no AI tier could answer (caller falls back to rules). */
    private Result askAi(String text) {
        LocalModelHost host = LocalModelHost.get();
        // v1.1: FunctionGemma installed ho to 470 MB MNN model pehle se load mat
        // karo — FGemma tier chalta hai; MNN sirf fallback par lazy load hoga.
        if (!(cfg.localPermitted() && FunctionGemmaHost.get().isReady(appCtx))) {
            host.ensureLoaded(appCtx, cfg);
        }

        String stateText = ToolRegistry.stateText(controller);

        // ---- Tier 0: Perchance web brain (page pehle se khula hai to wahi brain) ----
        if (cfg.webBrainEnabled() && !cfg.internetOff()) {
            Result wr = webJsonTurn(text, stateText);
            if (wr != null) return wr;
            boolean fallback = cfg.cloudEnabled()
                    || (cfg.localPermitted() && host.isReady());
            if (!fallback) {
                // No other brain: answer with the rules engine, but say WHY the
                // web brain did not answer and give a one-tap fix.
                Result err = new Result();
                err.source = SRC_OFFLINE;
                err.failed = true;
                err.text = rules.generateResponse(text).text;
                err.note = "🌐 Perchance: " + lastWebError;
                err.actions.add(new ChatMessage.Action("🌐 PERCHANCE PAGE", "WEB_PAGE"));
                return err;
            }
        }

        // ---- Tier 1a: FunctionGemma 270M (v1.1, tool-calling specialist) ----
        // Downloaded hona chahiye; warna chup-chaap MNN tier par girega.
        if (cfg.localPermitted() && FunctionGemmaHost.get().isReady(appCtx)) {
            final long t0 = System.currentTimeMillis();
            try {
                String sys = FunctionGemmaProtocol.systemPrompt(
                        com.digitalalu.alu.ai.skills.SkillRegistry.promptFor(text));
                String user = FunctionGemmaProtocol.userPrompt(text)
                        + "STATE: " + stateText + "\n";
                String raw = FunctionGemmaHost.get().generate(appCtx, sys, user, 160);
                if (raw != null && raw.trim().length() > 0) {
                    IntentProtocol.ToolCall call = FunctionGemmaProtocol.parse(raw);
                    if (call.hasTool() || (call.say != null && call.say.trim().length() > 0)) {
                        Result r = applyParsedCall(text, call, SRC_FGEMMA, true);
                        if (r != null) {
                            r.note = (r.note == null || r.note.length() == 0 ? "" : r.note + " · ")
                                    + "⚡ FGemma " + ((System.currentTimeMillis() - t0) / 1000) + "s";
                            return r;
                        }
                    }
                }
            } catch (Throwable t) {
                // FGemma gir gaya → MNN tier try hoga (neeche), rules hamesha backup.
            }
        }

        // ---- Tier 1: on-device model ----
        // v1.1: FGemma-priority phones par MNN ab tak load nahi hua — FGemma fail
        // hone par yahan lazy load karo ( warna ye line no-op hai, pehle se loaded).
        if (cfg.localPermitted() && !host.isReady()) host.ensureLoaded(appCtx, cfg);
        if (cfg.localPermitted() && host.isReady()) {
            // v1.0 skill packs: only the tools this message plausibly needs go
            // into the prompt — smaller prompt, better picks on a small model.
            final String sysPrompt = IntentProtocol.localSystem(
                    com.digitalalu.alu.ai.skills.SkillRegistry.promptFor(text));
            String userPrompt = IntentProtocol.localUser(text, stateText);
            final StringBuilder got = new StringBuilder();
            final int[] n = new int[]{0};
            final long t0 = System.currentTimeMillis();
            cancelled = false;
            final int thinkBudget = cfg.thinking ? Math.max(40, cfg.thinkTokens) : 0;
            String raw = host.llm().generate(sysPrompt, userPrompt, 120, 0.2f, 0.9f,
                    JsonGrammar.forTools(), thinkBudget, new LocalLlm.Listener() {
                @Override public void onToken(String piece) {
                    got.append(piece); n[0]++;
                    Progress pr = progress;
                    if (pr != null) try { pr.onToken(piece, n[0]); } catch (Throwable ignored) { }
                }
                @Override public void onThink(String piece) {
                    Progress pr = progress;
                    if (pr != null) try { pr.onThink(piece); } catch (Throwable ignored) { }
                }
                @Override public boolean isCancelled() {
                    return cancelled || System.currentTimeMillis() - t0 > LOCAL_TIMEOUT_MS;
                }
            });
            if (raw == null || raw.trim().length() == 0) raw = got.toString();
            boolean timedOut = System.currentTimeMillis() - t0 > LOCAL_TIMEOUT_MS;
            Result r = applyModelOutput(text, raw, SRC_DEVICE, true);
            if (r != null) {
                r.note = (r.note == null || r.note.length() == 0 ? "" : r.note + " · ")
                        + "🧠 " + ((System.currentTimeMillis() - t0) / 1000) + "s" + (thinkBudget > 0 ? " · 💭" : "");
                return r;
            }
            if (timedOut || cancelled) {
                Result slow = new Result();
                slow.source = SRC_OFFLINE;
                slow.text = rules.generateResponse(text).text;
                slow.note = cancelled ? "model cancel kiya" : "model 60s me jawab nahi de paya — rules se jawab";
                return slow;
            }
        }

        // ---- Tier 2: cloud ----
        if (cfg.cloudEnabled() && cfg.underCap()) {
            Result r = cloudTurn(text, stateText);
            if (r != null) return r;
            // Nothing came back: say WHY, loudly, instead of silently dropping to rules.
            Result err = new Result();
            err.source = SRC_CLOUD;
            err.failed = true;
            err.text = "☁️ Cloud AI se jawab nahi aaya.\n"
                     + "Wajah: " + lastCloudError + "\n\n"
                     + "AI Setup → TEST CONNECTION dabayein — wahan poora detail milega.";
            err.actions.add(new ChatMessage.Action("🧠 AI SETUP", "AI_SETUP"));
            err.actions.add(new ChatMessage.Action("☁️ CLOUD TEST", "CLOUD_KEY"));
            return err;
        }
        return null;
    }

    /**
     * Cloud turn — the model picks tools, we run them, we hand the results back,
     * and the model writes the final answer having seen the real numbers.
     */
    private Result cloudTurn(String text, String stateText) {
        lastCloudError = "";

        // OpenAI-compatible endpoints and "generateContent" both speak our JSON
        // protocol — one prompt, one parser, no tool registration needed.
        if (cfg.isOpenAi() || CloudLlm.wireFor(cfg) == CloudLlm.WIRE_GENERATE) {
            return cloudJsonTurn(text, stateText);
        }

        // Gemini Interactions API: the model calls our tools natively.
        cfg.countCloudCall(appCtx);
        String firstInput = CloudLlm.systemPrompt()
                + "\n\nAPP KA CURRENT STATE:\n" + stateText
                + "\n\nUSER NE KAHA: " + text
                + "\n\nAb tool chuno ya seedha jawab do.";

        CloudLlm.Interaction it = CloudLlm.create(cfg, firstInput, null, null);

        if (!it.ok) {
            // The Interactions endpoint refused (404 / unsupported for this key) —
            // fall back to the JSON protocol once, and remember it for next time.
            lastCloudError = "Interactions API: " + it.error;
            Result r = cloudJsonTurn(text, stateText);
            if (r != null) {
                cfg.cloudWire = CloudLlm.WIRE_GENERATE;
                cfg.save(appCtx);
                return r;
            }
            return null;
        }

        StringBuilder lastToolText = new StringBuilder();
        int round = 0;
        while (it.ok && !it.calls.isEmpty() && round < MAX_CLOUD_ROUNDS) {
            JSONArray results = new JSONArray();
            for (CloudLlm.ToolCallStep call : it.calls) {
                JSONObject args = sanitizeArgs(call.name, mergeArgs(text, call.name, call.args));
                ToolRegistry.ExecResult er = ToolRegistry.execute(controller, call.name, args);
                if (er.needsConfirm) {
                    pending = new Pending();
                    pending.tool = er.confirmTool;
                    pending.args = er.confirmArgs;
                    Result r = new Result();
                    r.source = SRC_CLOUD;
                    r.text = "⚠️ " + describeDestructive(er.confirmTool)
                            + "\nConfirm karein? \"haan\" ya \"nahi\" likhein.";
                    return r;
                }
                lastToolText.append(er.text).append('\n');
                results.put(CloudLlm.functionResult(call, er.text));
            }
            cfg.countCloudCall(appCtx);
            it = CloudLlm.create(cfg, null, results, it.id);
            round++;
        }

        if (!it.ok) {
            // The loop broke at some point — if a tool already ran, report it.
            if (lastToolText.length() > 0) {
                Result r = new Result();
                r.source = SRC_CLOUD;
                r.text = lastToolText.toString().trim();
                r.note = "cloud error: " + it.error;
                return r;
            }
            return null;
        }

        Result r = new Result();
        r.source = SRC_CLOUD;
        String say = it.text == null ? "" : it.text.trim();
        String toolText = lastToolText.toString().trim();
        if (say.length() > 0 && toolText.length() > 0) r.text = say + "\n\n" + toolText;
        else if (say.length() > 0) r.text = say;
        else r.text = toolText;
        return r.text.trim().length() > 0 ? r : null;
    }

    /**
     * The portable path: ask → JSON tool call → run it → ask again so the model
     * phrases the result. Works on generateContent, OpenAI, OpenRouter, Groq…
     */
    /**
     * Perchance turn: the page's ai-text-plugin is asked for ONE JSON order
     * (startWith "{" keeps it honest), the agent runs the tool, and the
     * follow-up lets the same web model phrase the answer — same loop as cloud.
     */
    private Result webJsonTurn(String text, String stateText) {
        PerchanceBrain pb = PerchanceBrain.active();
        if (pb == null) { lastWebError = "page band hai (⋮ → 🌐 Perchance Page)"; return null; }
        // v2: instruction page ke andar banti hai (ALU_SYSTEM_PROMPT) — wahan
        // edit karo to agent ka behaviour wahin se badlega.
        PerchanceBrain.Answer a = pb.askOrder(text, stateText,
                com.digitalalu.alu.ai.skills.SkillRegistry.promptFor(text), 120000L);
        if (!a.ok) { lastWebError = a.error; return null; }
        String raw = a.text == null ? "" : a.text.trim();
        Result r = applyModelOutput(text, raw, SRC_WEB, true);
        if (r == null) lastWebError = "model ka jawab samajh nahi aaya";
        return r;
    }

    private Result cloudJsonTurn(String text, String stateText) {
        cfg.countCloudCall(appCtx);

        CloudLlm.Reply reply = CloudLlm.ask(cfg, CloudLlm.systemPrompt(),
                CloudLlm.userPrompt(text, stateText));
        if (!reply.ok) {
            lastCloudError = reply.error.length() > 0 ? reply.error : "HTTP " + reply.httpCode;
            return null;
        }
        Result r = applyModelOutput(text, reply.text, SRC_CLOUD, true);
        if (r == null) lastCloudError = "model ka jawab samajh nahi aaya";
        return r;
    }

    /* ------------------------------------------------------------------- */

    /** A 0.8B model rambles: keep the first line / sentence, max ~160 chars. */
    static String capSay(String say) {
        if (say == null) return null;
        String t = say.trim();
        int nl = t.indexOf('\n');
        if (nl > 0) t = t.substring(0, nl).trim();
        if (t.length() > 160) {
            int cut = -1;
            for (String p : new String[]{". ", "! ", "? ", "। "}) {
                int k = t.indexOf(p, 40);
                if (k > 0 && k < 160 && (cut < 0 || k < cut)) cut = k + 1;
            }
            t = (cut > 0 ? t.substring(0, cut) : t.substring(0, 157) + "...").trim();
        }
        return t;
    }

    /** @param allowFollowUp when true the model also phrases the tool result. */
    private Result applyModelOutput(String userText, String raw, String source, boolean allowFollowUp) {
        if (raw == null || raw.trim().length() == 0) return null;
        IntentProtocol.ToolCall call = IntentProtocol.parse(raw);
        return applyParsedCall(userText, call, source, allowFollowUp);
    }

    /**
     * v1.1 — shared tail for every tier: takes an ALREADY-PARSED ToolCall so
     * tiers with their own wire grammar (FunctionGemma) reuse the exact same
     * resolve → execute → confirm → phrase pipeline.
     */
    private Result applyParsedCall(String userText, IntentProtocol.ToolCall call,
                                   String source, boolean allowFollowUp) {
        if (call == null) return null;
        if (SRC_DEVICE.equals(source) || SRC_FGEMMA.equals(source)) call.say = capSay(call.say);

        Result r = new Result();
        r.source = source;

        if (call.hasTool()) {
            String resolved = ToolRegistry.resolve(call.tool);
            if (resolved != null) call.tool = resolved;
            if (!isKnownTool(call.tool)) {
                // The model invented a tool — treat the text as a plain answer.
                if (call.say != null && call.say.trim().length() > 0) {
                    r.text = call.say.trim();
                    return r;
                }
                return null;
            }
            JSONObject args = sanitizeArgs(call.tool, mergeArgs(userText, call.tool, call.args));
            ToolRegistry.ExecResult er = ToolRegistry.execute(controller, call.tool, args);
            if (er.needsConfirm) {
                pending = new Pending();
                pending.tool = er.confirmTool;
                pending.args = er.confirmArgs;
                r.text = (call.say == null || call.say.length() == 0 ? "" : call.say.trim() + "\n\n")
                        + "⚠️ " + describeDestructive(er.confirmTool)
                        + "\nConfirm karein? \"haan\" ya \"nahi\" likhein.";
                return r;
            }

            // ---- the model sees what the agent did, then phrases the answer ----
            String toolText = er.text;
            String say = call.say == null ? "" : call.say.trim();
            if (allowFollowUp && cfg.followUp) {
                String spoken = describeResult(source, userText, call.tool, toolText);
                if (spoken.length() > 0) say = spoken;
            }

            StringBuilder sb = new StringBuilder();
            if (say.length() > 0 && !isEcho(say, toolText)) sb.append(say).append("\n\n");
            sb.append(toolText);
            r.text = sb.toString().trim();
            r.note = "tool:" + call.tool;
            return r;
        }

        if (call.say != null && call.say.trim().length() > 0) {
            r.text = call.say.trim();
            return r;
        }
        return null;
    }

    /**
     * Second half of the loop: give the model the tool result and let it write the
     * reply. On the on-device tier the grammar only permits {"say": "..."}, so a
     * weak model cannot drift back into calling tools or rambling.
     */
    private String describeResult(String source, String userText, String tool, String resultText) {
        try {
            String prompt = "USER NE POOCHA: " + userText
                    + "\nAGENT NE TOOL CHALAYA: " + tool
                    + "\nTOOL KA RESULT (yahi sach hai, isse bahar kuch mat bolo):\n" + resultText
                    + "\n\nAb user ko 1-2 line Hinglish me jawab do. Number sirf upar wale result se lo."
                    + " Koi tool mat chalao. Sirf JSON do.";

            if (SRC_WEB.equals(source)) {
                PerchanceBrain pb = PerchanceBrain.active();
                if (pb == null) return "";
                PerchanceBrain.Answer a = pb.askSay(userText, tool, resultText, 60000L);
                if (!a.ok) return "";
                IntentProtocol.ToolCall c = IntentProtocol.parse(a.text);
                return c.hasTool() ? "" : c.say.trim();
            }

            if (SRC_DEVICE.equals(source)) {
                // v2.9: no second model round on-device. The tool text is already
                // exact Hinglish, and a follow-up with a different system prompt
                // would throw away the cached prefix (= 25-50 s re-prefill).
                if (!cfg.deviceFollowUp) return "";
                LocalModelHost host = LocalModelHost.get();
                if (!host.isReady()) return "";
                String raw = host.llm().generate(IntentProtocol.SYSTEM,
                        IntentProtocol.forLocalModel(prompt), 70, 0.2f, 0.9f,
                        JsonGrammar.sayOnly(), null);
                IntentProtocol.ToolCall c = IntentProtocol.parse(raw);
                return c.hasTool() ? "" : c.say.trim();
            }

            if (SRC_CLOUD.equals(source)
                    && CloudLlm.wireFor(cfg) == CloudLlm.WIRE_INTERACTIONS) {
                CloudLlm.Interaction it = CloudLlm.create(cfg, CloudLlm.systemPrompt() + "\n\n" + prompt,
                        null, null);
                return it.ok && it.text != null ? it.text.trim() : "";
            }
            if (SRC_CLOUD.equals(source)) {
                CloudLlm.Reply rep = CloudLlm.ask(cfg, CloudLlm.systemPrompt(), prompt);
                IntentProtocol.ToolCall c = IntentProtocol.parse(rep.ok ? rep.text : "");
                return c.hasTool() ? "" : c.say.trim();
            }
        } catch (Throwable ignored) { }
        return "";
    }

    /* ========================= arg handling ============================ */

    /**
     * The agent fills gaps the model left. A 0.6B model gets "48x60 ki 2 window"
     * wrong surprisingly often, while the rules parser gets it right every time —
     * so the model decides WHAT to do and the rules supply the numbers it missed.
     */
    private JSONObject mergeArgs(String userText, String toolName, JSONObject fromModel) {
        JSONObject out = new JSONObject();
        if (fromModel != null) {
            java.util.Iterator<String> keys = fromModel.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                Object v = fromModel.opt(k);
                if (v == null || JSONObject.NULL.equals(v)) continue;
                try { out.put(k, v); } catch (Throwable ignored) { }
            }
        }
        try {
            // Numbers/names the user literally typed beat what the model echoed back
            // (a 0.8B model happily returns "4 by 5" as h=4,w=5 and invents nos).
            AluNeuralNLP.ParsedEntity e = AluNeuralNLP.parse(userText);
            if (e.hasDimensions) {
                putNum(out, "h", e.height);
                putNum(out, "w", e.width);
                if (e.nos > 0) putNum(out, "nos", e.nos);
                if (e.sutter > 0) putNum(out, "sutter", e.sutter);
            }
            if (e.targetRate > 0) putNum(out, "rate", e.targetRate);
            if (e.rateType != null && e.rateType.length() > 0) {
                putStr(out, "kind", e.rateType.toLowerCase(Locale.US));
            }
            if (e.customerName != null && e.customerName.length() > 0) {
                putStr(out, "name", e.customerName);
            }
            if (e.mobileNumber != null && e.mobileNumber.length() > 0) {
                putStr(out, "mobile", e.mobileNumber);
            }
            if (e.village != null && e.village.length() > 0) {
                putStr(out, "village", e.village);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static void putNum(JSONObject o, String k, double v) {
        if (v <= 0) return;
        try { o.put(k, v); } catch (Throwable ignored) { }
    }
    private static void putStr(JSONObject o, String k, String v) {
        if (v == null || v.trim().length() == 0) return;
        try { o.put(k, v.trim()); } catch (Throwable ignored) { }
    }

    private static void putIfMissingNum(JSONObject o, String k, double v) {
        if (v <= 0) return;
        if (o.has(k) && o.optDouble(k, 0) > 0) return;      // model already gave it
        try { o.put(k, v); } catch (Throwable ignored) { }
    }

    private static void putIfMissingStr(JSONObject o, String k, String v) {
        String cur = o.optString(k, "").trim();
        if (cur.length() > 0) return;
        try { o.put(k, v); } catch (Throwable ignored) { }
    }

    /** Drops argument names the tool does not understand (small models add junk). */
    private static JSONObject sanitizeArgs(String toolName, JSONObject raw) {
        JSONObject out = new JSONObject();
        ToolRegistry.Tool t = ToolRegistry.byName(toolName);
        if (t == null) return raw == null ? out : raw;
        String[] allowed = JsonGrammar.argKeysOf(t.signature);
        if (allowed.length == 0 || raw == null) return out;
        java.util.Iterator<String> keys = raw.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            for (String a : allowed) {
                if (a.equalsIgnoreCase(k)) {
                    try { out.put(a, raw.opt(k)); } catch (Throwable ignored) { }
                    break;
                }
            }
        }
        return out;
    }

    private static boolean isKnownTool(String name) {
        return ToolRegistry.byName(name) != null;
    }

    /** True when the model just repeated the tool output — no point showing it twice. */
    private static boolean isEcho(String say, String toolText) {
        String a = say.replaceAll("\\s+", " ").trim().toLowerCase(Locale.US);
        String b = toolText.replaceAll("\\s+", " ").trim().toLowerCase(Locale.US);
        if (a.length() < 3) return true;
        return b.contains(a) || a.contains(b.substring(0, Math.min(40, b.length())));
    }

    private static String describeDestructive(String tool) {
        if ("clear_windows".equals(tool)) return "Ye puri sheet ke saare window delete kar dega.";
        if ("delete_last_window".equals(tool)) return "Ye aakhri window delete karega.";
        return "Ye " + tool + " chalayega.";
    }

    private String aiUnavailableReason() {
        LocalModelHost h = LocalModelHost.get();
        if (!h.runtimeAvailable()) return "On-device AI is build me available nahi hai.";
        if (!cfg.localPermitted() && !cfg.cloudEnabled())
            return "AI off hai — AI Setup me model on karein ya cloud key daalein.";
        if (cfg.localConfigured() && !h.isReady()) return "AI: " + h.status();
        if (!cfg.cloudEnabled()) return "Cloud AI off hai (key nahi hai ya NO INTERNET mode hai).";
        if (!cfg.underCap()) return "Aaj ka AI limit khatam ho gaya.";
        return "";
    }

    /* =================================================================== */

    public static boolean isYes(String t) {
        String s = t.trim().toLowerCase(Locale.US);
        return s.equals("haan") || s.equals("han") || s.equals("ha") || s.equals("yes")
                || s.equals("ok") || s.equals("okay") || s.equals("theek hai") || s.equals("thik hai")
                || s.equals("karo") || s.equals("kar do") || s.equals("kardo") || s.equals("haji")
                || s.equals("haan karo") || s.equals("haan kar do") || s.startsWith("haan ")
                || s.equals("y") || s.equals("sure");
    }

    public static boolean isNo(String t) {
        String s = t.trim().toLowerCase(Locale.US);
        return s.equals("nahi") || s.equals("nahin") || s.equals("no") || s.equals("na")
                || s.equals("cancel") || s.equals("rehne do") || s.equals("mat karo")
                || s.equals("n");
    }

    public static String sourceLabel(String source) {
        if (SRC_CLOUD.equals(source)) return "CLOUD AI";
        if (SRC_WEB.equals(source)) return "PERCHANCE WEB";
        if (SRC_DEVICE.equals(source)) return "ON-DEVICE AI";
        return "AGENT";
    }
}
