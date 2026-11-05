package com.digitalalu.alu.ai;

import org.json.JSONObject;

/**
 * v1.0 — FunctionGemma tier protocol.
 *
 * FunctionGemma (Gemma 3 270M, ~283 MB, LiteRT-LM) does not emit the app's
 * JSON wire format — it emits its own trained grammar:
 *
 *   <start_function_call>call:TOOL_NAME{"arg":1}<end_function_call>
 *
 * or plain text when it decides to escalate (no tool). This class builds the
 * FunctionGemma-style prompt (declarations inside <start_functions> blocks)
 * and parses its replies back into the same IntentProtocol.ToolCall the rest
 * of the agent already understands, so no other layer changes.
 *
 * Pure Java + org.json: fully unit-testable without a device or model.
 */
public class FunctionGemmaProtocol {

    public static final String FN_START = "<start_function_call>";
    public static final String FN_END = "<end_function_call>";
    public static final String FNS_START = "<start_functions>";
    public static final String FNS_END = "<end_functions>";

    /** FunctionGemma's trained "no tool, just talk / escalate" function. */
    public static final String ESCALATE = "escalate_to_llm";

    /**
     * Builds the FunctionGemma system prompt from ToolRegistry prompt lines.
     * Each tool "name(signature) — help" becomes a declaration block.
     */
    public static String systemPrompt(String toolPromptList) {
        StringBuilder sb = new StringBuilder();
        sb.append(FNS_START).append('\n');
        if (toolPromptList != null) {
            for (String line : toolPromptList.split("\n")) {
                String l = line.trim();
                if (l.startsWith("- ")) l = l.substring(2);
                if (l.length() == 0) continue;
                int par = l.indexOf('(');
                if (par < 0) continue;
                String name = l.substring(0, par).trim();
                if (!IntentProtocol.isPlausibleToolName(name)) continue;
                String rest = l.substring(par + 1);
                int close = rest.indexOf(')');
                String args = close >= 0 ? rest.substring(0, close).trim() : "";
                String help = close >= 0 ? rest.substring(close + 1).trim() : "";
                if (help.startsWith("—")) help = help.substring(1).trim();
                sb.append("type ").append(name).append(" = (");
                String[] parts = args.length() == 0 ? new String[0] : args.split(",");
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(parts[i].trim()).append(": any");
                }
                sb.append(") -> any  {").append(help).append("}\n");
            }
        }
        sb.append("type ").append(ESCALATE).append(" = () -> any  "
                + "{chit-chat ya aisa kaam jiske liye koi tool nahi}\n");
        sb.append(FNS_END).append('\n');
        return sb.toString();
    }

    public static String userPrompt(String userInput) {
        return "USER: " + (userInput == null ? "" : userInput) + "\n";
    }

    /** Parses one FunctionGemma reply into the shared ToolCall. */
    public static IntentProtocol.ToolCall parse(String raw) {
        IntentProtocol.ToolCall tc = new IntentProtocol.ToolCall();
        if (raw == null) return tc;
        String s = IntentProtocol.stripFence(raw.trim());
        int start = s.indexOf(FN_START);
        if (start >= 0) {
            int from = start + FN_START.length();
            int end = s.indexOf(FN_END, from);
            String body = (end > from) ? s.substring(from, end) : s.substring(from);
            // tolerate truncated output (token cap): drop the missing sentinel
            int ci = body.indexOf("call:");
            if (ci >= 0) {
                String call = body.substring(ci + 5);
                int brace = call.indexOf('{');
                String name = (brace >= 0 ? call.substring(0, brace) : call).trim();
                if (IntentProtocol.isPlausibleToolName(name) && !ESCALATE.equals(name)) {
                    tc.tool = name;
                    if (brace >= 0) {
                        String json = IntentProtocol.firstJsonObject(call.substring(brace));
                        if (json == null) json = IntentProtocol.closeTruncatedJson(call.substring(brace));
                        if (json != null) {
                            try {
                                tc.args = new JSONObject(json);
                            } catch (Throwable ignored) { }
                        }
                    }
                    tc.parsed = true;
                    return tc;
                }
                // escalate_to_llm => no tool; the text (if any) before the call is the "say"
            }
        }
        // No function call: FunctionGemma escalated — treat remaining text as chat.
        String say = s.replace(FN_START, " ").replace(FN_END, " ")
                .replaceAll("\\s+", " ").trim();
        tc.say = say;
        tc.raw = say.length() > 0;
        return tc;
    }
}
