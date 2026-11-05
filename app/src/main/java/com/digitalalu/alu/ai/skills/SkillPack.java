package com.digitalalu.alu.ai.skills;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One "skill" = a small, self-contained bundle of tools + the words that
 * indicate the user is talking about that skill.
 *
 * Modelled on the open Agent Skills standard (agentskills.io / SKILL.md):
 * the agent only LOADS the skill it needs for the current turn instead of
 * carrying every tool in the prompt. On a 0.6-0.8B on-device model a 16-tool
 * prompt is a big share of the context and a big source of wrong-tool picks;
 * 4-6 relevant tools measurably improves tool selection.
 */
public class SkillPack {

    public final String id;
    public final String name;
    /** One-line description (Hinglish) — also what a SKILL.md frontmatter would carry. */
    public final String description;
    /** ToolRegistry tool names this skill owns. */
    public final List<String> tools;
    /** Lowercase trigger words (Hinglish/Gujarati/English mix). */
    public final List<String> triggers;

    public SkillPack(String id, String name, String description,
                     String[] tools, String[] triggers) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.tools = new ArrayList<String>(Arrays.asList(tools));
        this.triggers = new ArrayList<String>(Arrays.asList(triggers));
    }

    /** How strongly a (lowercased) user message points at this skill. */
    public int score(String lowerText) {
        int s = 0;
        for (String t : triggers) {
            if (t.length() >= 4) {
                if (lowerText.contains(t)) s += 2;
            } else {
                // short words only count as whole tokens ("rate" not "rates" issue
                // avoided by simple contains for >=4; for <4 require word bounds)
                if (lowerText.matches(".*\\b" + java.util.regex.Pattern.quote(t) + "\\b.*")) s += 2;
            }
        }
        return s;
    }
}
