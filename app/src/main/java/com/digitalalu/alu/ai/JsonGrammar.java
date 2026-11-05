package com.digitalalu.alu.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * GBNF grammar that FORCES the on-device model to answer with exactly
 * <pre>{"say":"...", "tool":"name|null", "args":{...}}</pre>
 *
 * Why this exists — measured on a real Qwen3-0.6B-Hindi (Q4_K_M) with our prompt,
 * on a host build of this exact native code:
 *
 *   plain prompt : JSON 0/5  — the model answered with loose prose every time
 *   + this       : JSON 5/5  — and correct tool calls for estimate / rate / chat
 *
 * A sub-1B model does not obey "reply with JSON only". Grammar-constrained
 * sampling masks the logits, so the format and the tool name stop being a hope.
 *
 * Three things are locked down, each one earned from a real failure:
 *  1. the JSON shape                     (model otherwise writes paragraphs)
 *  2. the tool name                      (otherwise it invents tools)
 *  3. the argument NAMES + flat values   (otherwise it kept adding junk keys like
 *     "type"/"no" after the real ones and ran past the token limit mid-JSON)
 *
 * The grammar is built from {@link ToolRegistry} at runtime, so adding a tool
 * teaches the model about it automatically. {@code null} is always allowed, so
 * plain chit-chat still works.
 */
public final class JsonGrammar {

    private JsonGrammar() { }

    /** Hard ceiling on args pairs — keeps the model from rambling past the budget. */
    private static final int MAX_ARGS = 6;

    public static String forProtocol(String[] toolNames, String[] argKeys) {
        StringBuilder sb = new StringBuilder();

        sb.append("root   ::= \"{\" ws \"\\\"say\\\"\" ws \":\" ws string ws \",\" ws ")
          .append("\"\\\"tool\\\"\" ws \":\" ws tool ws \",\" ws ")
          .append("\"\\\"args\\\"\" ws \":\" ws object ws \"}\"\n");

        // tool ::= null | "add_window" | "update_rate" | ...
        sb.append("tool   ::= \"null\"");
        if (toolNames != null) {
            for (String t : toolNames) {
                if (t == null || t.trim().length() == 0) continue;
                sb.append(" | \"\\\"").append(t.trim()).append("\\\"\"");
            }
        }
        sb.append('\n');

        // object ::= {} | {"h":48, "w":60}   — flat, known keys, bounded count
        sb.append("object ::= \"{\" ws ( pair ( ws \",\" ws pair )");
        if (MAX_ARGS > 1) sb.append("{0,").append(MAX_ARGS - 1).append('}');
        sb.append(" )? \"}\"\n");

        sb.append("pair   ::= key ws \":\" ws value\n");

        sb.append("key    ::= ");
        if (argKeys == null || argKeys.length == 0) {
            sb.append("string");                 // nothing known: stay permissive
        } else {
            for (int i = 0; i < argKeys.length; i++) {
                if (i > 0) sb.append(" | ");
                sb.append("\"\\\"").append(argKeys[i]).append("\\\"\"");
            }
        }
        sb.append('\n');

        // values stay flat: our tools only ever take scalars
        sb.append("value  ::= string | number | \"true\" | \"false\" | \"null\"\n");
        sb.append("string ::= \"\\\"\" ( [^\"\\\\\\x7F\\x00-\\x1F] | \"\\\\\" ( [\"\\\\/bfnrt] | \"u\" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] ) )* \"\\\"\"\n");
        sb.append("number ::= \"-\"? [0-9]+ ( \".\" [0-9]+ )?\n");
        sb.append("ws     ::= [ \\t\\n]*\n");
        return sb.toString();
    }

    /**
     * Tight grammar for the follow-up turn ("model dekhega, batayega"): the only
     * thing the model may produce is a spoken line. tool is pinned to null and args
     * to {}, so a weak model cannot restart the tool loop or drift off.
     */
    public static String sayOnly() {
        return "root   ::= \"{\" ws \"\\\"say\\\"\" ws \":\" ws string ws \",\" ws "
             + "\"\\\"tool\\\"\" ws \":\" ws \"null\" ws \",\" ws "
             + "\"\\\"args\\\"\" ws \":\" ws \"{\" ws \"}\" ws \"}\"\n"
             + "string ::= \"\\\"\" ( [^\"\\\\\\x7F\\x00-\\x1F] | \"\\\\\" "
             + "( [\"\\\\/bfnrt] | \"u\" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] ) )* "
             + "\"\\\"\"\n"
             + "ws     ::= [ \\t\\n]*\n";
    }

    /** Grammar straight from the live tool registry (names + all argument keys). */
    public static String forTools() {
        try {
            List<String> names = new ArrayList<String>();
            List<String> keys  = new ArrayList<String>();
            for (ToolRegistry.Tool t : ToolRegistry.all()) {
                names.add(t.name);
                for (String k : argKeysOf(t.signature)) {
                    if (!keys.contains(k)) keys.add(k);
                }
            }
            return forProtocol(names.toArray(new String[0]), keys.toArray(new String[0]));
        } catch (Throwable t) {
            return forProtocol(null, null);
        }
    }

    /** "(h,w,sutter,nos,system,name)" -> ["h","w","sutter","nos","system","name"] */
    public static String[] argKeysOf(String signature) {
        List<String> out = new ArrayList<String>();
        if (signature == null) return new String[0];
        int a = signature.indexOf('('), b = signature.lastIndexOf(')');
        if (a < 0 || b <= a + 1) return new String[0];
        for (String part : signature.substring(a + 1, b).split(",")) {
            String k = part.trim();
            if (k.length() > 0) out.add(k);
        }
        return out.toArray(new String[0]);
    }
}
