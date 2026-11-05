package com.digitalalu.alu.ai;

import org.json.JSONObject;

/**
 * The single wire protocol shared by every AI tier (on-device and cloud).
 *
 * We deliberately avoid provider-specific "function calling" APIs: the model is
 * asked to answer with ONE strict JSON object:
 *
 *   {"say":"Hinglish jawab", "tool":"tool_name" | null, "args":{...}}
 *
 * This works on a 0.6B on-device model, on Gemini, and on any OpenAI-compatible
 * endpoint — one prompt, one parser, fully testable without a network.
 */
public class IntentProtocol {

    public static class ToolCall {
        public String say = "";
        public String tool = null;
        public JSONObject args = new JSONObject();
        public boolean parsed = false;
        /** true when we had to fall back to raw text (model ignored the format) */
        public boolean raw = false;

        public String toolName() { return tool == null ? "" : tool; }
        public boolean hasTool() { return tool != null && tool.trim().length() > 0; }
    }

    public static final String SYSTEM = ""
        + "Tum \"ALU Window\" app ke andar ka AI agent ho. User aluminium fabrication workshop "
        + "chalata hai (Hinglish/Gujarati/English mix). Tumhara kaam: user ki baat samajhna aur "
        + "app ka sahi tool chalana.\n"
        + "\n"
        + "SAKHT NIYAM (zaroori):\n"
        + "1. Tu KABHI khud hisaab/kad nahi nikalega. Number sirf tool ke result se aayega.\n"
        + "2. Jawab sirf ek JSON object hoga — kuch aur text nahi, markdown fence nahi.\n"
        + "3. Format: {\"tool\":\"naam ya null\",\"args\":{...},\"say\":\"...\"} — tool PEHLE, say AAKHIR me.\n"
        + "4. Agar kaam ke liye tool chahiye to tool naam + args bharo. Simple baat-cheet ho to "
        + "tool null rakho aur sirf \"say\" me jawab do.\n"
        + "5. \"say\" Hinglish me EK chhoti line (max 12 shabd) — jaise workshop ka munshi bolta hai.\n"
        + "6. Jo state neeche diya hai wahi sach hai; usse bahar kuch mat maano.\n"
        + "\n"
        + "UDAHARAN (examples — inhi ka pattern follow karo):\n"
        + "User: 48x60 ki 2 window add karo\n"
        + "{\"tool\":\"add_window\",\"args\":{\"h\":48,\"w\":60,\"nos\":2},\"say\":\"Theek hai, add kar raha hoon.\"}\n"
        + "User: kitne pipe lagenge\n"
        + "{\"tool\":\"show_estimate\",\"args\":{},\"say\":\"\"}\n"
        + "User: alu ka rate 320 kar do\n"
        + "{\"tool\":\"update_rate\",\"args\":{\"kind\":\"alu\",\"rate\":320},\"say\":\"Rate set kar raha hoon.\"}\n"
        + "User: sutter kya hota hai\n"
        + "{\"tool\":null,\"args\":{},\"say\":\"Sutter window ke beech ka patla section hota hai — usse parda/sliding hissa chalta hai.\"}\n"
        + "User: tum kaun ho\n"
        + "{\"tool\":null,\"args\":{},\"say\":\"Main ALU Window ka agent hoon — window add, cutting plan, kharcha sab bata sakta hoon.\"}\n"
        + "User: aakhri wali nikaal do\n"
        + "{\"tool\":\"delete_last_window\",\"args\":{},\"say\":\"Aakhri window hata raha hoon.\"}\n"
        + "User: cutting dikhao\n"
        + "{\"tool\":\"show_cutting_plan\",\"args\":{},\"say\":\"\"}\n"
        + "User: 16 foot ka pipe use karo\n"
        + "{\"tool\":\"set_stock_pipe\",\"args\":{\"section\":\"frame\",\"length\":192},\"say\":\"Stock pipe 16 ft set kar raha hoon.\"}\n"
        + "User: Ramesh bhai 9876543210 Gondal\n"
        + "{\"tool\":\"save_customer\",\"args\":{\"name\":\"Ramesh bhai\",\"mobile\":\"9876543210\",\"village\":\"Gondal\"},\"say\":\"Customer save kar raha hoon.\"}\n"
        + "User: sab hata do\n"
        + "{\"tool\":\"clear_windows\",\"args\":{},\"say\":\"Poori sheet khali kar raha hoon.\"}\n"
        + "\n"
        + "SAMAJHNE KA TARIKA: pehle socho user ka MAKSAD kya hai (add? dekhna? badalna? hataana? sirf sawal?). "
        + "Phir us maksad ka sabse seedha tool chuno — ek baar me ek tool. Jo number user ne bola wahi args me daalo, "
        + "khud mat banao. Agar size/number adhoora ho to tool null rakho aur \"say\" me ek chhota sawal poochho.\n"
        + "SOCH (think) chhoti rakho: 2-3 line — maksad, tool, args. Phir turant JSON.\n";

    public static String userPrompt(String userInput, String toolList, String stateText) {
        StringBuilder sb = new StringBuilder();
        sb.append("UPALABDH TOOLS:\n").append(toolList).append('\n');
        sb.append("\nAPP KA CURRENT STATE:\n").append(stateText).append('\n');
        sb.append("\nHINGLISH HINTS: pehli=1, doosri=2, teesri=3, chauthi=4, paanchvi=5 (index). "
                + "'4 by 5' / '4x5' = width 4 ft, height 5 ft. 'piece'/'nag'/'window' ke aage ka number = qty. "
                + "'hata do'/'delete'/'nikaal do' = delete. 'kholo'/'dikhao' = screen open.\n");
        sb.append("\nUSER NE KAHA: ").append(userInput).append('\n');
        sb.append("\nAb sirf JSON do: ");
        return sb.toString();
    }

    /* ------------------------------------------------------------------ */

    /** Tolerant parser: finds the first balanced {...} block and reads it. */
    public static ToolCall parse(String raw) {
        ToolCall tc = new ToolCall();
        if (raw == null) return tc;
        String s = stripFence(raw.trim());
        if (s.length() == 0) return tc;

        String json = firstJsonObject(s);
        if (json == null || s.indexOf(json) != s.indexOf('{')) {
            // The outer object never balanced (token cap hit mid-"say") — the scan
            // either found nothing or only an inner object such as args. Repair it.
            String repaired = closeTruncatedJson(s);
            if (repaired != null) json = repaired;
        }
        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);
                if (o.has("say")) tc.say = o.optString("say", "");
                if (o.has("tool") && !o.isNull("tool")) {
                    String t = o.optString("tool", "").trim();
                    if (isPlausibleToolName(t)) {
                        tc.tool = t;
                    }
                }
                if (o.has("args") && o.opt("args") instanceof JSONObject) {
                    tc.args = o.optJSONObject("args");
                }
                // A bare "{}" (or an object with only unknown keys) tells us nothing —
                // fall through to the salvage path instead.
                if (tc.say.length() > 0 || tc.hasTool()) {
                    tc.parsed = true;
                    return tc;
                }
            } catch (Throwable ignored) { }
        }

        String t = salvageToolName(s);
        if (t != null) tc.tool = t;
        tc.say = stripFence(s).trim();
        tc.raw = true;
        return tc;
    }

    /**
     * Small on-device models often hit the token cap mid-object. If the text has an
     * opening brace but never balances, close the open string / objects so the
     * JSON parser can still read tool + args (say may be cut short — acceptable).
     */
    public static String closeTruncatedJson(String s) {
        int start = s.indexOf('{');
        if (start < 0) return null;
        StringBuilder sb = new StringBuilder();
        int depth = 0; boolean inStr = false, esc = false;
        for (int i = start; i < s.length(); i++) {
            char ch = s.charAt(i);
            sb.append(ch);
            if (inStr) {
                if (esc) esc = false;
                else if (ch == '\\') esc = true;
                else if (ch == '"') inStr = false;
                continue;
            }
            if (ch == '"') inStr = true;
            else if (ch == '{') depth++;
            else if (ch == '}') depth--;
        }
        if (depth <= 0) return null;
        if (esc) sb.setLength(sb.length() - 1);
        if (inStr) sb.append('"');
        // drop a dangling key / comma like  ,"say":   or  ,
        String t = sb.toString().replaceAll(",\\s*(\"[^\"]*\"\\s*:?\\s*)?$", "");
        sb = new StringBuilder(t);
        for (int i = 0; i < depth; i++) sb.append('}');
        return sb.toString();
    }

    public static String stripFence(String s) {
        if (s == null) return "";
        String out = s.trim();
        if (out.startsWith("```")) {
            int nl = out.indexOf('\n');
            if (nl > 0) out = out.substring(nl + 1);
            int end = out.lastIndexOf("```");
            if (end >= 0) out = out.substring(0, end);
        }
        return out.trim();
    }

    /** Same brace-balancing scan used by the chat bubble for JSON blocks. */
    public static String firstJsonObject(String s) {
        int start = s.indexOf('{');
        while (start >= 0) {
            int depth = 0;
            boolean inStr = false;
            boolean esc = false;
            for (int i = start; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (inStr) {
                    if (esc) esc = false;
                    else if (ch == '\\') esc = true;
                    else if (ch == '"') inStr = false;
                    continue;
                }
                if (ch == '"') inStr = true;
                else if (ch == '{') depth++;
                else if (ch == '}') {
                    depth--;
                    if (depth == 0) return s.substring(start, i + 1);
                }
            }
            start = s.indexOf('{', start + 1);
        }
        return null;
    }

    /**
     * Last-resort tool name extraction from a reply that is not valid JSON
     * (usually truncated at the token limit).
     *
     * History: this used to start scanning at {@code i + 5}, which is the closing
     * quote of the KEY — so the "name" it read was everything between the key and
     * the value's opening quote, i.e. literally {@code ":"}. The agent then looked
     * up a tool called ":" and failed. The offsets below skip the whole key.
     */
    private static String salvageToolName(String s) {
        int from;
        int i = s.indexOf("\"tool\"");
        if (i >= 0) {
            from = i + 6;                       // past "tool"
        } else {
            i = s.indexOf("tool:");
            if (i < 0) return null;
            from = i + 5;                       // past tool:
        }
        int q1 = s.indexOf('"', from);
        if (q1 < 0) return null;
        int q2 = s.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        String n = s.substring(q1 + 1, q2).trim();
        if (!isPlausibleToolName(n)) return null;
        return n;
    }

    /**
     * Tool names are lowercase identifiers in this app. Anything else ("", ":",
     * "null", stray punctuation from a malformed reply) is treated as "no tool"
     * so a bad reply can never trigger a bogus lookup.
     */
    public static boolean isPlausibleToolName(String n) {
        if (n == null) return false;
        String t = n.trim();
        if (t.length() == 0 || t.length() > 40) return false;
        if ("null".equalsIgnoreCase(t) || "none".equalsIgnoreCase(t)) return false;
        if (!Character.isLetter(t.charAt(0))) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) return false;
        }
        return true;
    }

    /** v2.8 MNN: prompt is passed through unchanged (thinking is closed natively). */
    public static String forLocalModel(String prompt) {
        return prompt;
    }

    /**
     * On-device split (v2.8.3): everything that never changes — rules, examples,
     * tool list, Hinglish hints — goes into the system message. The JNI side
     * prefills that once into the KV cache and reuses it, so each turn only has
     * to read the ~60 tokens of {@link #localUser}. Prompt went 1050 → ~70 tokens
     * per turn, i.e. ~50 s → ~4 s of prefill on a budget phone.
     */
    public static String localSystem(String toolList) {
        return SYSTEM
                + "\nUPALABDH TOOLS:\n" + toolList + "\n"
                + "\nHINGLISH HINTS: pehli=1, doosri=2, teesri=3 (index). '4 by 5' / '4x5' = width 4 ft, "
                + "height 5 ft. 'piece'/'nag'/'window' ke aage ka number = qty. 'hata do'/'delete'/'nikaal do' = delete. "
                + "'kholo'/'dikhao' = screen open.\n";
    }

    public static String localUser(String userInput, String stateText) {
        return "STATE: " + stateText + "\nUSER: " + userInput + "\nJSON:";
    }
}
