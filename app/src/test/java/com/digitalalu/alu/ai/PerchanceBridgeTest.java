package com.digitalalu.alu.ai;

import static org.junit.Assert.*;
import org.junit.Test;

public class PerchanceBridgeTest {
    @Test public void generatorName() {
        assertEquals("d4yeutq0b1", PerchanceBrain.generatorNameOf("https://perchance.org/d4yeutq0b1"));
        assertEquals("d4yeutq0b1", PerchanceBrain.generatorNameOf("https://perchance.org/d4yeutq0b1/?x=1#y"));
        assertEquals("abc", PerchanceBrain.generatorNameOf("https://7d41.perchance.org/abc?__generatorLastEditTime=1"));
        assertEquals("", PerchanceBrain.generatorNameOf("perchance.org"));
    }
    @Test public void generatorDocumentMatch() {
        assertTrue(PerchanceBrain.isGeneratorDocument("7d41a65.perchance.org", "/d4yeutq0b1", "d4yeutq0b1", "text/html,*/*"));
        assertFalse(PerchanceBrain.isGeneratorDocument("perchance.org", "/d4yeutq0b1", "d4yeutq0b1", "text/html"));
        assertFalse(PerchanceBrain.isGeneratorDocument("7d41a65.perchance.org", "/lib/x.js", "d4yeutq0b1", "*/*"));
        assertFalse(PerchanceBrain.isGeneratorDocument("evil.com", "/d4yeutq0b1", "d4yeutq0b1", "text/html"));
    }
    @Test public void injectOnce() {
        String html = "<html><body><p>hi</p></body></html>";
        String out = PerchanceBrain.injectBridge(html, "window.__aluBridgeV2=1;", true);
        assertTrue(out.contains("__aluBridgeV2"));
        assertTrue(out.indexOf("<script>") < out.indexOf("</body>"));
        assertSame(out, PerchanceBrain.injectBridge(out, "x", false));
    }
}
