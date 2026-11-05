package com.digitalalu.alu.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests for the MODEL TEST feature — the probes it asks, the scoring it gives,
 * and the verdict it prints. All pure logic, no model needed.
 */
public class ModelTesterTest {

    private static ModelTester.ProbeResult make(String expectTool, String gotTool,
                                                boolean jsonOk, long ms, int chars) {
        ModelTester.Report rep = new ModelTester.Report();
        ModelTester.ProbeResult r = new ModelTester.ProbeResult();
        // a stand-in probe with the same expectation
        ModelTester.Probe p = null;
        for (ModelTester.Probe pr : ModelTester.probes()) {
            if (pr.expectTool.equals(expectTool)) { p = pr; break; }
        }
        assertNotNull("no probe expects tool " + expectTool, p);
        r.probe = p;
        r.tool = gotTool;
        r.jsonOk = jsonOk;
        r.toolOk = jsonOk && ModelTester.toolMatches(gotTool, expectTool);
        r.msTotal = ms;
        r.chars = chars;
        rep.results.add(r);
        return r;
    }

    /* --------------------------- probes ------------------------------ */

    @Test public void thereAreEnoughProbesToBeMeaningful() {
        List<ModelTester.Probe> p = ModelTester.probes();
        assertTrue("need at least 4 probes, got " + p.size(), p.size() >= 4);
        for (ModelTester.Probe pr : p) {
            assertNotNull(pr.id);
            assertTrue(pr.prompt.trim().length() > 5);
            assertTrue(pr.title.trim().length() > 2);
        }
    }

    @Test public void probesCoverBothToolAndNoToolCases() {
        int withTool = 0, withoutTool = 0;
        for (ModelTester.Probe p : ModelTester.probes()) {
            if (p.expectTool.length() == 0) withoutTool++;
            else withTool++;
        }
        assertTrue("must test real tool calling", withTool >= 2);
        assertTrue("must also test plain conversation", withoutTool >= 1);
    }

    @Test public void everyExpectedToolActuallyExists() {
        for (ModelTester.Probe p : ModelTester.probes()) {
            if (p.expectTool.length() == 0) continue;
            assertNotNull("probe expects a tool the app does not have: " + p.expectTool,
                    ToolRegistry.byName(p.expectTool));
        }
    }

    @Test public void probeIdsAreUnique() {
        Set<String> seen = new HashSet<String>();
        for (ModelTester.Probe p : ModelTester.probes()) {
            assertTrue("duplicate probe id " + p.id, seen.add(p.id));
        }
    }

    /* --------------------------- matching ---------------------------- */

    @Test public void toolMatchingIsForgivingButNotSloppy() {
        assertTrue(ModelTester.toolMatches("add_window", "add_window"));
        assertTrue(ModelTester.toolMatches(" Add_Window ", "add_window"));
        assertTrue(ModelTester.toolMatches("SHOW_ESTIMATE", "show_estimate"));
        assertFalse(ModelTester.toolMatches("show_windows", "show_estimate"));
        assertFalse(ModelTester.toolMatches(null, "add_window"));
        assertFalse(ModelTester.toolMatches("add_window", null));   // expected "" -> none allowed
    }

    @Test public void whenNoToolIsExpectedAnyToolCallIsAWrongAnswer() {
        assertTrue(ModelTester.toolMatches("", ""));
        assertTrue(ModelTester.toolMatches(null, ""));
        assertFalse("model must not fire a tool for plain chat",
                ModelTester.toolMatches("add_window", ""));
    }

    /* --------------------------- maths ------------------------------- */

    @Test public void tokenEstimateIsSaneForHinglish() {
        assertEquals(0, ModelTester.estimateTokens(0));
        assertEquals(1, ModelTester.estimateTokens(3));
        assertEquals(25, ModelTester.estimateTokens(100));
        assertEquals(0.0, ModelTester.tokensPerSec(100, 0), 0.001);
        assertEquals(0.0, ModelTester.tokensPerSec(0, 1000), 0.001);
        // 200 chars = ~50 tokens in 5s => 10 tok/s
        assertEquals(10.0, ModelTester.tokensPerSec(200, 5000), 0.5);
    }

    /* --------------------------- scoring ----------------------------- */

    @Test public void goodModelIsToldToUseIt() {
        ModelTester.Report rep = new ModelTester.Report();
        rep.modelName = "test";
        for (ModelTester.Probe p : ModelTester.probes()) {
            ModelTester.ProbeResult r = new ModelTester.ProbeResult();
            r.probe = p;
            r.jsonOk = true;
            r.tool = p.expectTool;                 // perfectly right
            r.toolOk = ModelTester.toolMatches(r.tool, p.expectTool);
            r.msTotal = 2000;
            r.chars = 200;
            rep.results.add(r);
        }
        assertEquals(rep.total(), rep.jsonScore());
        assertEquals(rep.total(), rep.passCount());
        assertTrue(rep.verdict().startsWith("USE IT"));
        assertTrue(rep.summaryLine().contains("tok/s"));
        assertFalse(rep.cancelled);
    }

    @Test public void hopelessModelIsToldToUpgrade() {
        ModelTester.Report rep = new ModelTester.Report();
        for (ModelTester.Probe p : ModelTester.probes()) {
            ModelTester.ProbeResult r = new ModelTester.ProbeResult();
            r.probe = p;
            r.jsonOk = false;                      // rambling prose, no JSON at all
            r.raw = "Haan ji, main aapki madad karunga…";
            r.msTotal = 9000;
            r.chars = 300;
            rep.results.add(r);
        }
        assertEquals(0, rep.jsonScore());
        assertTrue(rep.verdict().startsWith("KAAM KA NAHI"));
    }

    @Test public void partlyWorkingModelGetsAHonestMiddleVerdict() {
        ModelTester.Report rep = new ModelTester.Report();
        List<ModelTester.Probe> probes = ModelTester.probes();
        for (int i = 0; i < probes.size(); i++) {
            ModelTester.Probe p = probes.get(i);
            ModelTester.ProbeResult r = new ModelTester.ProbeResult();
            r.probe = p;
            r.jsonOk = i < probes.size() - 1;      // one probe fails to produce JSON
            r.tool = p.expectTool;
            r.toolOk = ModelTester.toolMatches(r.tool, p.expectTool);
            r.msTotal = 4000;
            r.chars = 150;
            rep.results.add(r);
        }
        assertTrue(rep.jsonScore() > 0);
        assertTrue(rep.jsonScore() < rep.total());
        String v = rep.verdict();
        assertTrue("middle verdict expected but got: " + v,
                v.startsWith("THODA KAMZOR") || v.startsWith("USE IT"));
    }

    @Test public void errorAndCancelledAreReportedHonestly() {
        ModelTester.Report rep = new ModelTester.Report();
        rep.cancelled = true;
        assertTrue(rep.verdict().contains("adhura"));

        ModelTester.Report noRuntime = new ModelTester.Report();
        noRuntime.runtimeOk = false;
        assertTrue(noRuntime.verdict().contains("Runtime"));

        ModelTester.Report empty = new ModelTester.Report();
        assertTrue(empty.verdict().contains("Koi test nahi"));
    }

    @Test public void probeResultLineIsReadable() {
        ModelTester.ProbeResult r = make("add_window", "add_window", true, 1500, 80);
        assertTrue(r.oneLine().startsWith("✓"));
        assertTrue(r.oneLine().contains("1.5s"));
        assertTrue(r.pass());

        ModelTester.ProbeResult bad = make("add_window", "", true, 3000, 40);
        assertTrue(bad.oneLine().startsWith("✗"));
        assertEquals("TOOL MISS", bad.verdict());
        assertFalse(bad.pass());

        ModelTester.ProbeResult err = new ModelTester.ProbeResult();
        err.probe = ModelTester.probes().get(0);
        err.error = "OutOfMemoryError";
        assertEquals("ERROR", err.verdict());
        assertFalse(err.pass());
    }

    @Test public void runWithoutAModelIsSafeAndReportsTheTruth() {
        ModelTester.Report rep = ModelTester.run(null, "state", "none", null);
        assertFalse(rep.runtimeOk);
        assertEquals(0, rep.total());
        assertTrue(rep.verdict().contains("Runtime"));
    }

    @Test public void reportTextIsGeneratedSafely() {
        ModelTester.Report rep = new ModelTester.Report();
        rep.modelName = "Qwen3-0.6B-Hindi";
        ModelTester.ProbeResult r = new ModelTester.ProbeResult();
        r.probe = ModelTester.probes().get(1);
        r.jsonOk = true;
        r.tool = "add_window";
        r.toolOk = true;
        r.raw = "{\"say\":\"ok\",\"tool\":\"add_window\",\"args\":{\"h\":48}}";
        r.msTotal = 1234;
        r.msFirstToken = 300;
        r.chars = 44;
        rep.results.add(r);

        String text = ModelTester.detailedReport(rep);
        assertTrue(text.contains("MODEL TEST"));
        assertTrue(text.contains("Qwen3-0.6B-Hindi"));
        assertTrue(text.contains("[PASS]"));
        assertTrue(text.contains("expected tool: add_window"));
        assertTrue(text.contains("1234ms"));

        String line = ModelTester.storedLine(rep);
        assertTrue(line.contains("JSON 1/1"));

        assertEquals("", ModelTester.storedLine(null));
        assertEquals("", ModelTester.storedLine(new ModelTester.Report()));
    }
}
