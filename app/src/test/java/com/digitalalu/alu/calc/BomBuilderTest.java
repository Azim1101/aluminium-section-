package com.digitalalu.alu.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.digitalalu.alu.model.WindowItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** v1.0 BOM: quantities must match what the cutting engine already computed. */
public class BomBuilderTest {

    private static List<WindowItem> twoWindows() {
        List<WindowItem> items = new ArrayList<WindowItem>();
        items.add(new WindowItem(1, "W1", WindowItem.ZED, 48, 60, 2, 1));
        items.add(new WindowItem(2, "W2", WindowItem.ZED, 48, 60, 2, 2));
        return items;
    }

    @Test
    public void bomMatchesEngineSummary() {
        Settings s = new Settings();
        List<Engine.WinResult> res = Engine.calcAll(twoWindows(), s);
        Map<String, Engine.TypeSummary> sum = Engine.summarize(res, s);

        BomBuilder.Bom b = BomBuilder.build(res, sum, s);

        Engine.Grand g = Engine.grand(res, sum, s);
        assertEquals("BOM pipe count must equal the cutting plan's pipe count",
                g.stockPipes, b.totalPipes);

        int cuts = 0;
        for (BomBuilder.Row r : b.rows) cuts += r.cuts;
        assertEquals("BOM cut count must equal grand total pcs", g.pcs, cuts);
    }

    @Test
    public void bomRowsCarryStockLengthAndWaste() {
        Settings s = new Settings();
        List<Engine.WinResult> res = Engine.calcAll(twoWindows(), s);
        Map<String, Engine.TypeSummary> sum = Engine.summarize(res, s);
        BomBuilder.Bom b = BomBuilder.build(res, sum, s);

        assertTrue(b.rows.size() > 0);
        for (BomBuilder.Row r : b.rows) {
            assertTrue("stock length must be positive for " + r.section, r.stockLen > 0);
            assertTrue("pipes must be positive for " + r.section, r.pipes > 0);
            assertTrue("waste% sane", r.wastePct >= 0 && r.wastePct <= 100);
        }
    }

    @Test
    public void supplierTextHasNoRupeeSigns() {
        Settings s = new Settings();
        List<Engine.WinResult> res = Engine.calcAll(twoWindows(), s);
        Map<String, Engine.TypeSummary> sum = Engine.summarize(res, s);
        BomBuilder.Bom b = BomBuilder.build(res, sum, s);
        String text = new BomBuilder().toText(b, true);

        assertFalse(text, text.contains("Rs"));
        assertTrue(text.contains("MATERIAL ORDER (BOM)"));
        assertTrue(text.contains("ALUMINIUM PIPES"));
        assertTrue(text.contains("pipe"));
    }

    private static void assertFalse(String msg, boolean cond) {
        assertTrue(msg, !cond);
    }

    @Test
    public void glassAreaComesFromEngine() {
        Settings s = new Settings();
        List<Engine.WinResult> res = Engine.calcAll(twoWindows(), s);
        Map<String, Engine.TypeSummary> sum = Engine.summarize(res, s);
        BomBuilder.Bom b = BomBuilder.build(res, sum, s);
        Engine.Grand g = Engine.grand(res, sum, s);

        assertEquals(g.glassSqft, b.glassSqft, 1e-9);
        assertTrue("glass should be present for real windows", b.glassSqft > 0);
    }

    @Test
    public void emptySheetMakesEmptyBom() {
        Settings s = new Settings();
        List<Engine.WinResult> res = Engine.calcAll(new ArrayList<WindowItem>(), s);
        Map<String, Engine.TypeSummary> sum = Engine.summarize(res, s);
        BomBuilder.Bom b = BomBuilder.build(res, sum, s);
        assertEquals(0, b.totalPipes);
        assertEquals(0, b.rows.size());
    }

    @Test
    public void sectionLabelsAreReadable() {
        assertEquals("ZED FRAME", BomBuilder.sectionLabel("Z_FRAME"));
        assertEquals("DOMAL SUTTER", BomBuilder.sectionLabel("D_SUTTER"));
        assertEquals("DOMAL RT", BomBuilder.sectionLabel("D_RT"));
    }
}
