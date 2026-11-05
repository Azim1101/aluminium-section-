package com.digitalalu.alu.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Perchance bridge speaks JSON over a JS interface, and that interface is
 * called from a WebView thread with data we do not control. A crash there would
 * take the agent screen down, so every event goes through parseEvent() first —
 * and these tests pin that behaviour.
 */
public class PerchanceEventTest {

    @Test
    public void parsesDoneEvent() {
        PerchanceBrain.Event ev = PerchanceBrain.parseEvent(
                "{\"__alu\":1,\"kind\":\"done\",\"id\":7,\"text\":\"{\\\"say\\\":\\\"hi\\\"}\"}");
        assertNotNull(ev);
        assertEquals("done", ev.kind);
        assertEquals(7, ev.id);
        assertEquals("{\"say\":\"hi\"}", ev.text);
    }

    @Test
    public void parsesChunkAndReadyEvents() {
        PerchanceBrain.Event c = PerchanceBrain.parseEvent(
                "{\"__alu\":1,\"kind\":\"chunk\",\"id\":7,\"text\":\"Hel\"}");
        assertNotNull(c);
        assertEquals("chunk", c.kind);
        assertEquals("Hel", c.text);

        PerchanceBrain.Event r = PerchanceBrain.parseEvent(
                "{\"__alu\":1,\"kind\":\"ready\",\"id\":0,\"text\":\"bridge v1\"}");
        assertNotNull(r);
        assertEquals("ready", r.kind);
    }

    @Test
    public void rejectsForeignAndBrokenPayloads() {
        // an unrelated message that merely passed through the page
        assertNull(PerchanceBrain.parseEvent("{\"hello\":\"world\"}"));
        // no kind
        assertNull(PerchanceBrain.parseEvent("{\"__alu\":1}"));
        // straight-up garbage from a hostile/broken page
        assertNull(PerchanceBrain.parseEvent("<html>not json"));
        assertNull(PerchanceBrain.parseEvent(""));
        assertNull(PerchanceBrain.parseEvent(null));
    }

    @Test
    public void orderAskCarriesModeTextStateAndTools() {
        org.json.JSONObject f = new org.json.JSONObject();
        try {
            f.put("text", "48x60 ki 2 window add karo");
            f.put("state", "windows=0");
            f.put("tools", "add_window(...)");
        } catch (Exception e) { throw new AssertionError(e); }
        String json = PerchanceBrain.buildAskJson(9, "order", f, "{");
        PerchanceBrain.Event ev = PerchanceBrain.parseEvent(json);
        assertNotNull(ev);
        assertEquals("ask", ev.kind);
        assertEquals(9, ev.id);
        assertTrue(json.contains("\"mode\":\"order\""));
        assertTrue(json.contains("startWith"));
        assertTrue(json.contains("48x60 ki 2 window add karo"));
    }

    @Test
    public void sayAskCarriesToolResult() {
        org.json.JSONObject f = new org.json.JSONObject();
        try {
            f.put("text", "kitna waste hua");
            f.put("tool", "show_cutting_plan");
            f.put("result", "waste 6.4%");
        } catch (Exception e) { throw new AssertionError(e); }
        String json = PerchanceBrain.buildAskJson(10, "say", f, "{");
        assertNotNull(PerchanceBrain.parseEvent(json));
        assertTrue(json.contains("show_cutting_plan"));
        assertTrue(json.contains("waste 6.4%"));
    }

    @Test
    public void missingFieldsBecomeSafeDefaults() {
        PerchanceBrain.Event ev = PerchanceBrain.parseEvent("{\"__alu\":1,\"kind\":\"error\"}");
        assertNotNull(ev);
        assertEquals("error", ev.kind);
        assertEquals(0, ev.id);
        assertEquals("", ev.text);
        assertFalse(ev.kind.equals("done"));
        assertTrue(PerchanceBrain.parseEvent("{\"__alu\":0,\"kind\":\"done\"}") == null);
    }
}
