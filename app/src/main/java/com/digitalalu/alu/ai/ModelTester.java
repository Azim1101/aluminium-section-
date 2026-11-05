package com.digitalalu.alu.ai;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MODEL TEST — "jo model maine download kiya, wo sach me kaam karta hai ya nahi?"
 *
 * This runs real inference on the loaded MNN model with a fixed set of workshop-style
 * probes, measures speed, and checks whether the model produced the JSON tool call
 * the agent needs. Nothing is simulated — every probe goes through the same
 * code path the chat uses (LocalLlm → IntentProtocol).
 *
 * Deliberately Android-free (no Context, no View) so the scoring logic is unit
 * testable and the UI can never disagree with the verdict.
 */
public class ModelTester {

    /* ------------------------------- probes --------------------------- */

    public static class Probe {
        public final String id;
        public final String title;
        public final String prompt;
        /** "" = the model must NOT call a tool, it should just talk. */
        public final String expectTool;
        public final String note;

        Probe(String id, String title, String prompt, String expectTool, String note) {
            this.id = id;
            this.title = title;
            this.prompt = prompt;
            this.expectTool = expectTool;
            this.note = note;
        }
    }

    private static final Probe[] PROBES = new Probe[] {
        new Probe("hello", "Simple baat-cheet",
                "Namaste! Tum kaun ho?",
                "", "Model ko Hinglish me jawab dena chahiye, koi tool nahi."),
        new Probe("add", "Window add karna (tool call)",
                "48x60 ki 2 window add karo",
                "add_window", "Sabse zaroori kaam — JSON me tool aana chahiye."),
        new Probe("estimate", "Estimate maangna",
                "kitne pipe lagenge cutting me?",
                "show_estimate", "Tool + sahi args."),
        new Probe("rate", "Rate badalna (number samajhna)",
                "alu ka rate 320 per kg kar do",
                "update_rate", "Number sahi pakadna — 320."),
        new Probe("explain", "Formula samjhana (AI-only kaam)",
                "sutter kya hota hai, chhota sa samjhao",
                "", "Yahan AI ka asli fayda dikhta hai — bina tool, saaf jawab."),
    };

    public static List<Probe> probes() {
        List<Probe> out = new ArrayList<Probe>();
        for (Probe p : PROBES) out.add(p);
        return out;
    }

    /* ------------------------------ results --------------------------- */

    public static class ProbeResult {
        public Probe probe;
        public String raw = "";
        public String say = "";
        public String tool = "";
        public String error = "";
        public boolean jsonOk;          // model produced parseable {"say",...}
        public boolean toolOk;          // called exactly the tool we expected
        public long msTotal;
        public long msFirstToken;
        public int chars;
        public boolean cancelled;

        public String verdict() {
            if (error != null && error.length() > 0) return "ERROR";
            if (!jsonOk) return "NO JSON";
            return toolOk ? "PASS" : "TOOL MISS";
        }

        public boolean pass() {
            return (error == null || error.length() == 0) && jsonOk && toolOk;
        }

        public String oneLine() {
            return (pass() ? "✓ " : "✗ ") + probe.title + " — " + verdict()
                 + " · " + (msTotal / 1000.0) + "s"
                 + (chars > 0 ? " · " + chars + " chars" : "");
        }
    }

    public static class Report {
        public List<ProbeResult> results = new ArrayList<ProbeResult>();
        public boolean runtimeOk = true;
        public boolean cancelled;
        public long startedAt = System.currentTimeMillis();
        public String modelName = "";

        public int total() { return results.size(); }
        public int jsonScore() { int n = 0; for (ProbeResult r : results) if (r.jsonOk) n++; return n; }
        public int toolScore() { int n = 0; for (ProbeResult r : results) if (r.toolOk) n++; return n; }
        public int toolProbes() { int n = 0; for (ProbeResult r : results) if (r.probe.expectTool.length() > 0) n++; return n; }
        public int passCount() { int n = 0; for (ProbeResult r : results) if (r.pass()) n++; return n; }

        public long avgMs() {
            if (results.isEmpty()) return 0;
            long t = 0;
            for (ProbeResult r : results) t += r.msTotal;
            return t / results.size();
        }

        public double avgTokensPerSec() {
            double best = 0;
            int n = 0;
            for (ProbeResult r : results) {
                double v = tokensPerSec(r.chars, r.msTotal);
                if (v > 0) { best += v; n++; }
            }
            return n == 0 ? 0 : best / n;
        }

        public String summaryLine() {
            return "JSON " + jsonScore() + "/" + total()
                 + " · Tool " + toolScore() + "/" + Math.max(1, toolProbes())
                 + " · ~" + String.format(Locale.US, "%.1f", avgTokensPerSec()) + " tok/s"
                 + " · avg " + String.format(Locale.US, "%.1f", avgMs() / 1000.0) + "s";
        }

        /** Can this model be trusted as the agent's brain? */
        public String verdict() {
            if (!runtimeOk) return "Runtime hi nahi hai — cloud key daalein ya rules use karein.";
            if (cancelled) return "Test adhura chala gaya — dobara chalayein.";
            if (total() == 0) return "Koi test nahi chala.";
            double jsonRate = jsonScore() / (double) total();
            double toolRate = toolProbes() == 0 ? 1.0 : toolScore() / (double) toolProbes();
            if (jsonRate >= 0.8 && toolRate >= 0.66) {
                return "USE IT — model kaam kar raha hai. " + summaryLine();
            }
            if (jsonRate >= 0.6) {
                return "THODA KAMZOR — kabhi kabhi galti karega. Cloud AI ya bada model behtar rahega.";
            }
            return "KAAM KA NAHI — ye model JSON tool-call nahi bana pa raha. "
                 + "Bada model (1.7B Q4) ya cloud AI choose karein.";
        }
    }

    /* ---------------------------- pure maths -------------------------- */

    /** Rough token estimate: Hinglish averages ~4 characters per token. */
    public static int estimateTokens(int chars) {
        if (chars <= 0) return 0;
        return Math.max(1, chars / 4);
    }

    public static double tokensPerSec(int chars, long ms) {
        if (ms <= 0 || chars <= 0) return 0;
        return (estimateTokens(chars) * 1000.0) / ms;
    }

    /**
     * Did the model do the right thing?
     * expected "" means: no tool call at all is correct (pure conversation).
     */
    public static boolean toolMatches(String gotTool, String expected) {
        String got = gotTool == null ? "" : gotTool.trim();
        String want = expected == null ? "" : expected.trim();
        if (want.length() == 0) return got.length() == 0;
        if (got.length() == 0) return false;
        return got.equalsIgnoreCase(want);
    }

    /* ------------------------------- runner --------------------------- */

    public interface Progress {
        void onProbeStart(int index, Probe p);
        void onToken(String piece);
        /** @return true to stop the whole test. */
        boolean isCancelled();
    }

    private static final int MAX_TOKENS = 160;

    /**
     * Runs every probe against the loaded model. Blocking — call from a
     * background thread. Cancellation is honoured between and during probes.
     */
    public static Report run(LocalLlm llm, String stateText, String modelName, Progress cb) {
        Report rep = new Report();
        rep.modelName = modelName == null ? "" : modelName;

        if (llm == null || !llm.isLoaded()) {
            rep.runtimeOk = false;
            return rep;
        }

        String tools = ToolRegistry.promptList();
        List<Probe> list = probes();

        for (int i = 0; i < list.size(); i++) {
            if (cb != null && cb.isCancelled()) { rep.cancelled = true; break; }
            Probe p = list.get(i);
            if (cb != null) cb.onProbeStart(i, p);

            ProbeResult r = new ProbeResult();
            r.probe = p;

            final long[] t0 = {System.currentTimeMillis()};
            final long[] firstToken = {0L};
            final int[] tokenCount = {0};

            String user = IntentProtocol.forLocalModel(
                    IntentProtocol.userPrompt(p.prompt, tools, stateText));

            try {
                // Exactly the call the chat makes — same grammar, same temperature.
                // If this passes, the chat path is what was proven.
                String out = llm.generate(IntentProtocol.SYSTEM, user, MAX_TOKENS, 0.2f, 0.9f,
                        JsonGrammar.forTools(), new LocalLlm.Listener() {
                    @Override public void onToken(String piece) {
                        if (firstToken[0] == 0L) firstToken[0] = System.currentTimeMillis();
                        tokenCount[0]++;
                        if (cb != null) cb.onToken(piece);
                    }
                    @Override public boolean isCancelled() {
                        return cb != null && cb.isCancelled();
                    }
                    @Override public void onThink(String piece) { }
                });
                r.raw = out == null ? "" : out;
            } catch (Throwable t) {
                r.error = t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage());
            }

            r.msTotal = System.currentTimeMillis() - t0[0];
            r.msFirstToken = firstToken[0] == 0L ? 0 : firstToken[0] - t0[0];
            r.chars = r.raw.length();

            if (r.error.length() == 0 && r.raw.trim().length() == 0) {
                r.error = "model ne kuch nahi likha (khali jawab)";
            }

            IntentProtocol.ToolCall call = IntentProtocol.parse(r.raw);
            r.say = call.say;
            r.tool = call.hasTool() ? call.tool : "";
            r.jsonOk = call.parsed;
            r.toolOk = call.parsed && toolMatches(r.tool, p.expectTool);

            rep.results.add(r);

            if (cb != null && cb.isCancelled()) { rep.cancelled = true; break; }
        }
        return rep;
    }

    /* ------------------------- formatting for UI ---------------------- */

    /** Detailed, copy-pasteable report the user can send us for debugging. */
    public static String detailedReport(Report rep) {
        StringBuilder sb = new StringBuilder();
        sb.append("MODEL TEST — ").append(rep.modelName).append('\n');
        sb.append(rep.runtimeOk ? "" : "(runtime missing)\n");
        sb.append("VERDICT: ").append(rep.verdict()).append("\n\n");
        for (ProbeResult r : rep.results) {
            sb.append(r.pass() ? "[PASS] " : "[FAIL] ").append(r.probe.title).append('\n');
            sb.append("  expected tool: ")
              .append(r.probe.expectTool.length() == 0 ? "(none)" : r.probe.expectTool)
              .append("   got: ").append(r.tool.length() == 0 ? "(none)" : r.tool).append('\n');
            sb.append("  time: ").append(r.msTotal).append("ms")
              .append("  first token: ").append(r.msFirstToken).append("ms")
              .append("  chars: ").append(r.chars).append('\n');
            if (r.error.length() > 0) sb.append("  error: ").append(r.error).append('\n');
            if (r.raw.length() > 0) {
                String raw = r.raw.replace('\n', ' ');
                sb.append("  raw: ").append(raw.length() > 240 ? raw.substring(0, 240) + "…" : raw).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Short one-liner stored in prefs so the verdict survives an app restart. */
    public static String storedLine(Report rep) {
        if (rep == null || rep.total() == 0) return "";
        return rep.summaryLine() + "\n" + rep.verdict();
    }

    /** Compact args check helper, exposed for tests. */
    public static boolean argsHave(JSONObject args, String key) {
        return args != null && args.has(key);
    }
}
