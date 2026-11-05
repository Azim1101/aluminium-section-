package com.digitalalu.alu.ai.skills;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.digitalalu.alu.ai.ToolRegistry;

import org.junit.Test;

import java.util.List;

/** v1.0 skill packs: routing must be deterministic and never lose tools. */
public class SkillRegistryTest {

    @Test
    public void windowMessagePicksWindowsSkill() {
        List<SkillPack> r = SkillRegistry.rank("48x60 ki 2 window add karo");
        assertFalse(r.isEmpty());
        assertEquals("windows", r.get(0).id);
    }

    @Test
    public void cuttingMessagePicksPlanSkill() {
        List<SkillPack> r = SkillRegistry.rank("cutting plan dikhao");
        assertFalse(r.isEmpty());
        assertEquals("plan", r.get(0).id);
    }

    @Test
    public void bomMessagePicksPlanSkill() {
        List<SkillPack> r = SkillRegistry.rank("material order list bhejo");
        boolean hasPlan = false;
        for (SkillPack p : r) if (p.id.equals("plan")) hasPlan = true;
        assertTrue("BOM/order should route to plan skill", hasPlan);
    }

    @Test
    public void customerMessagePicksCustomersSkill() {
        List<SkillPack> r = SkillRegistry.rank("Ramesh bhai customer save karo");
        assertFalse(r.isEmpty());
        assertEquals("customers", r.get(0).id);
    }

    @Test
    public void rateMessagePicksCostSkill() {
        List<SkillPack> r = SkillRegistry.rank("alu ka rate 320 kar do");
        assertFalse(r.isEmpty());
        assertEquals("cost", r.get(0).id);
    }

    @Test
    public void gibberishFallsBackToFullToolList() {
        // nothing matches → promptFor must return the FULL list, never empty
        String p = SkillRegistry.promptFor("asdf qwerty zzz");
        assertEquals(ToolRegistry.promptList(), p);
    }

    @Test
    public void matchedPromptAlwaysKeepsBaseTools() {
        String p = SkillRegistry.promptFor("cutting plan dikhao");
        assertTrue(p.contains("get_state"));      // base tool
        assertTrue(p.contains("show_windows"));   // safety tool
        assertTrue(p.contains("show_cutting_plan"));
    }

    @Test
    public void matchedPromptIsSmallerThanFullList() {
        String full = ToolRegistry.promptList();
        String small = SkillRegistry.promptFor("48x60 ki 2 window add karo");
        assertTrue("skill-filtered prompt must be smaller than the full one",
                small.length() < full.length());
        assertTrue(small.contains("add_window"));
        assertFalse("unrelated tools must not be in the filtered prompt",
                small.contains("list_models"));
    }

    @Test
    public void atMostTwoSkillsPicked() {
        List<SkillPack> r = SkillRegistry.rank(
                "window add karo, rate bhi badalna hai, customer bhi save karna hai");
        assertTrue(r.size() <= 2);
    }

    @Test
    public void everySkillToolExistsInRegistry() {
        for (SkillPack p : SkillRegistry.all())
            for (String t : p.tools)
                assertNotNull("skill " + p.id + " references unknown tool " + t,
                        ToolRegistry.byName(t));
    }
}
