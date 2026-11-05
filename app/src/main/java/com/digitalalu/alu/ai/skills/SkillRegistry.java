package com.digitalalu.alu.ai.skills;

import com.digitalalu.alu.ai.ToolRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * v1.0 — Skill packs (progressive disclosure, Agent-Skills-style).
 *
 * Every turn the agent asks: "which 1-2 skills does THIS message need?"
 * and puts only those tools (+ the always-on base tools) into the prompt.
 * Everything stays deterministic and unit-testable: scoring is keyword
 * based, and when nothing matches we fall back to the full tool list so
 * the agent can never lose a capability.
 */
public class SkillRegistry {

    /** Tools every turn can see regardless of skill (cheap, universal). */
    public static final String[] BASE_TOOLS = { "get_state" };

    /** Tools the user must always be able to reach even mid-conversation. */
    private static final String[] SAFETY_TOOLS = { "show_windows" };

    private static final List<SkillPack> SKILLS = new ArrayList<SkillPack>();

    static {
        SKILLS.add(new SkillPack("windows", "Window entry",
                "window add/edit/delete — sizes aur quantity sheet me daalna",
                new String[] { "add_window", "show_windows", "delete_last_window", "clear_windows" },
                new String[] { "window", "windo", "add", "jod", "hata", "delete", "nikaal",
                        "khali", "clear", "sutter", "shutter", "size", "kad", "nos",
                        "piece", "nag", "zed", "domal", "dom" }));

        SKILLS.add(new SkillPack("plan", "Cutting & BOM",
                "cutting plan, pipe estimate aur material order (BOM) list",
                new String[] { "show_estimate", "show_cutting_plan", "show_bom" },
                new String[] { "cutting", "plan", "estimate", "pipe", "bom", "order",
                        "material", "kitne", "kitna", "tukda", "list", "waste" }));

        SKILLS.add(new SkillPack("cost", "Costing",
                "kharcha aur rate — total cost, alu/glass rate",
                new String[] { "show_costing", "update_rate" },
                new String[] { "kharcha", "cost", "rate", "paisa", "paise", "rupay",
                        "rupaye", "price", "bill", "total", "kitne ka" }));

        SKILLS.add(new SkillPack("stock", "Stock pipes",
                "stock pipe size aur leftover pieces manage karna",
                new String[] { "set_stock_pipe", "add_extra_pipe", "reset_stock_pipes" },
                new String[] { "stock", "leftover", "bacha", "bacha hua", "extra pipe",
                        "16 foot", "192", "196", "reset", "default pipe", "lam" }));

        SKILLS.add(new SkillPack("customers", "Customers",
                "customer save/load/list — CRM",
                new String[] { "list_customers", "save_customer", "load_customer" },
                new String[] { "customer", "grahak", "save", "load", "mobile", "number",
                        "village", "gaam", "naam se", "kholo", "client" }));

        SKILLS.add(new SkillPack("models", "AI models",
                "AI model download aur setup",
                new String[] { "list_models" },
                new String[] { "model", "download", "ai setup", "dimag", "brain" }));
    }

    public static List<SkillPack> all() { return SKILLS; }

    public static SkillPack byId(String id) {
        if (id == null) return null;
        for (SkillPack s : SKILLS) if (s.id.equals(id)) return s;
        return null;
    }

    /**
     * Rank skills for one user message. Empty list = nothing matched
     * (caller should fall back to the full tool list).
     */
    public static List<SkillPack> rank(String userText) {
        List<SkillPack> out = new ArrayList<SkillPack>();
        if (userText == null || userText.trim().length() == 0) return out;
        String lower = userText.toLowerCase(Locale.US);
        List<int[]> scored = new ArrayList<int[]>();
        for (int i = 0; i < SKILLS.size(); i++) {
            int s = SKILLS.get(i).score(lower);
            if (s > 0) scored.add(new int[] { i, s });
        }
        scored.sort((a, b) -> b[1] - a[1]);
        int taken = 0;
        for (int[] p : scored) {
            out.add(SKILLS.get(p[0]));
            if (++taken >= 2) break;   // top-2 skills max — keeps the prompt small
        }
        return out;
    }

    /** Tool names for a set of skills, base + safety tools first. */
    public static List<String> toolNames(List<SkillPack> packs) {
        Set<String> set = new LinkedHashSet<String>();
        for (String t : BASE_TOOLS) set.add(t);
        for (String t : SAFETY_TOOLS) set.add(t);
        if (packs != null) for (SkillPack p : packs) set.addAll(p.tools);
        // only keep names ToolRegistry actually knows (guards typos at compile-adjacent level)
        List<String> out = new ArrayList<String>();
        for (String n : set) if (ToolRegistry.byName(n) != null) out.add(n);
        return out;
    }

    /**
     * The prompt-ready tool list for one user message.
     * Never empty; falls back to the full list when no skill matched.
     */
    public static String promptFor(String userText) {
        List<SkillPack> packs = rank(userText);
        if (packs.isEmpty()) return ToolRegistry.promptList();
        return ToolRegistry.promptSubset(toolNames(packs));
    }

    /** Same, but also tells which skills were picked (for the chat note). */
    public static String[] promptForWithIds(String userText) {
        List<SkillPack> packs = rank(userText);
        if (packs.isEmpty()) return new String[] { ToolRegistry.promptList(), "" };
        StringBuilder ids = new StringBuilder();
        for (SkillPack p : packs) {
            if (ids.length() > 0) ids.append('+');
            ids.append(p.id);
        }
        return new String[] { ToolRegistry.promptSubset(toolNames(packs)), ids.toString() };
    }
}
