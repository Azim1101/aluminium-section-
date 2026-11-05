package com.digitalalu.alu.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.digitalalu.alu.ai.skills.SkillRegistry;

import org.junit.Test;

/** v1.0 FunctionGemma tier: grammar parsing + prompt building, no device needed. */
public class FunctionGemmaProtocolTest {

    @Test
    public void parsesPlainFunctionCall() {
        String raw = "<start_function_call>call:add_window{\"h\":48,\"w\":60,\"nos\":2}<end_function_call>";
        IntentProtocol.ToolCall tc = FunctionGemmaProtocol.parse(raw);
        assertTrue(tc.parsed);
        assertEquals("add_window", tc.tool);
        assertEquals(48.0, tc.args.optDouble("h"), 1e-9);
        assertEquals(60.0, tc.args.optDouble("w"), 1e-9);
        assertEquals(2, tc.args.optInt("nos"));
    }

    @Test
    public void parsesCallEmbeddedInText() {
        String raw = "Theek hai.\n<start_function_call>call:show_cutting_plan{}<end_function_call>";
        IntentProtocol.ToolCall tc = FunctionGemmaProtocol.parse(raw);
        assertEquals("show_cutting_plan", tc.tool);
        assertTrue(tc.parsed);
    }

    @Test
    public void parsesNoArgsCall() {
        String raw = "<start_function_call>call:show_bom{}<end_function_call>";
        IntentProtocol.ToolCall tc = FunctionGemmaProtocol.parse(raw);
        assertEquals("show_bom", tc.tool);
    }

    @Test
    public void escalateMeansNoTool() {
        String raw = "Main ALU Window ka agent hoon — bolo kya karna hai?";
        IntentProtocol.ToolCall tc = FunctionGemmaProtocol.parse(raw);
        assertNull(tc.tool);
        assertTrue(tc.say.contains("agent"));
    }

    @Test
    public void escalateFunctionIsNotATool() {
        String raw = "<start_function_call>call:escalate_to_llm{}<end_function_call>";
        IntentProtocol.ToolCall tc = FunctionGemmaProtocol.parse(raw);
        assertNull(tc.tool);
        assertFalse(tc.parsed);
    }

    @Test
    public void truncatedCallStillYieldsTool() {
        // token cap hit mid-args: tool name must survive, args may be partial
        String raw = "<start_function_call>call:update_rate{\"kind\":\"alu\",\"ra";
        IntentProtocol.ToolCall tc = FunctionGemmaProtocol.parse(raw);
        assertEquals("update_rate", tc.tool);
    }

    @Test
    public void nullAndEmptyAreSafe() {
        assertNull(FunctionGemmaProtocol.parse(null).tool);
        assertNull(FunctionGemmaProtocol.parse("").tool);
    }

    @Test
    public void systemPromptDeclaresToolsAndEscalate() {
        String sys = FunctionGemmaProtocol.systemPrompt(
                SkillRegistry.promptFor("48x60 ki 2 window add karo"));
        assertTrue(sys.startsWith(FunctionGemmaProtocol.FNS_START));
        assertTrue(sys.contains(FunctionGemmaProtocol.FNS_END));
        assertTrue(sys.contains("type add_window"));
        assertTrue(sys.contains("type " + FunctionGemmaProtocol.ESCALATE));
        // tools NOT in the skill subset must not be declared
        assertFalse(sys.contains("type list_models"));
    }
}
