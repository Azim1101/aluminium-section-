package com.digitalalu.alu.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.digitalalu.alu.calc.Engine.Bin;
import com.digitalalu.alu.calc.Engine.Part;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Bin-packing (BFD) + RP grill option logic — the money-critical code. */
public class EngineTest {

    private static Part part(double len, int pcs) {
        return new Part("Z_FRAME", len, pcs, "T");
    }

    @Test
    public void packPairsPiecesIntoFewestBins() {
        List<Part> parts = new ArrayList<>();
        parts.add(part(100, 2));
        parts.add(part(90, 2));

        List<Bin> bins = Engine.pack(parts, 196, 1);

        // 100 + kerf + 90 = 191 fits one bar; two bars total.
        assertEquals(2, bins.size());
        for (Bin b : bins) {
            assertEquals(2, b.items.size());
            assertEquals(191.0, b.used, 1e-9);
            assertFalse(b.over);
        }
    }

    @Test
    public void packHandlesOversizedPiece() {
        List<Part> parts = new ArrayList<>();
        parts.add(part(250, 1));

        List<Bin> bins = Engine.pack(parts, 196, 1);
        assertEquals(1, bins.size());
        assertTrue(bins.get(0).over);
    }

    @Test
    public void packEmptyInput() {
        assertTrue(Engine.pack(new ArrayList<Part>(), 196, 1).isEmpty());
    }

    @Test
    public void rpOptionsRespectMinMaxSpacing() {
        Settings s = new Settings();   // rpGap=6, rpMin=4, rpMax=4.85
        // effective = 26 - 6 = 20 → only q=5 gives spacing 4.0 inside [4, 4.85]
        List<Engine.RpOpt> opts = Engine.rpOptions(26, s);
        assertEquals(1, opts.size());
        assertEquals(5, opts.get(0).qty);
        assertEquals(4.0, opts.get(0).space, 1e-9);
        assertEquals(5, Engine.autoRpQty(26, s));
    }

    @Test
    public void rpOptionsMultipleValidQuantities() {
        Settings s = new Settings();
        // effective = 30 - 6 = 24 → q=5 (4.8) and q=6 (4.0)
        List<Engine.RpOpt> opts = Engine.rpOptions(30, s);
        assertEquals(2, opts.size());
        assertEquals(5, Engine.autoRpQty(30, s));   // first valid option wins
    }

    @Test
    public void rpOptionsEmptyWhenTooSmall() {
        Settings s = new Settings();
        assertTrue(Engine.rpOptions(8, s).isEmpty());   // eff = 2 → nothing fits
        assertEquals(0, Engine.autoRpQty(8, s));
    }

    @Test
    public void typeNames() {
        assertEquals("ZED FRAME", Engine.nameOf("Z_FRAME"));
        assertEquals("DOMAL RT", Engine.nameOf("D_RT"));
        assertTrue(Engine.isDomal("D_SUTTER"));
        assertFalse(Engine.isDomal("Z_SUTTER"));
    }
}
