package com.digitalalu.alu.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.digitalalu.alu.calc.Engine.Bin;
import com.digitalalu.alu.calc.Engine.Part;
import com.digitalalu.alu.calc.Engine.StockBar;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Section-wise stock lengths + leftover pipes:
 * frame 192" / sutter 180", plus "I have two 96" pieces lying around".
 */
public class StockBarTest {

    private static Part part(double len, int pcs) {
        return new Part("Z_FRAME", len, pcs, "T");
    }

    private static List<StockBar> bars(double... spec) {
        // spec = len, qty, len, qty ...   (qty < 0 = unlimited)
        List<StockBar> out = new ArrayList<>();
        for (int i = 0; i + 1 < spec.length; i += 2)
            out.add(new StockBar(spec[i], (int) spec[i + 1]));
        return out;
    }

    @Test
    public void singleUnlimitedBarMatchesClassicPack() {
        List<Part> parts = new ArrayList<>();
        parts.add(part(100, 2));
        parts.add(part(90, 2));

        List<Bin> classic = Engine.pack(parts, 196, 1);
        List<Bin> multi = Engine.packMulti(parts, bars(196, -1), 1);

        assertEquals(classic.size(), multi.size());
        for (int i = 0; i < classic.size(); i++) {
            assertEquals(classic.get(i).used, multi.get(i).used, 1e-9);
            assertEquals(196.0, multi.get(i).stockLen, 1e-9);
        }
    }

    @Test
    public void shorterSectionStockNeedsMorePipes() {
        List<Part> parts = new ArrayList<>();
        parts.add(part(100, 4));

        // 196" stock -> 100 + kerf + 100 = 201 > 196, so one cut per pipe
        assertEquals(4, Engine.packMulti(parts, bars(196, -1), 1).size());
        // 210" stock -> two cuts fit per pipe
        assertEquals(2, Engine.packMulti(parts, bars(210, -1), 1).size());
        // exactly 201" -> two cuts fit
        assertEquals(2, Engine.packMulti(parts, bars(201, -1), 1).size());
    }

    @Test
    public void leftoverPipeIsConsumedBeforeFreshStock() {
        List<Part> parts = new ArrayList<>();
        parts.add(part(60, 1));
        parts.add(part(30, 1));

        List<Bin> bins = Engine.packMulti(parts, bars(192, -1, 96, 1), 0);

        assertEquals(1, bins.size());
        // 60 + 30 = 90 fits the 96" leftover, leaving 6" — better than a 192" bar
        assertEquals(96.0, bins.get(0).stockLen, 1e-9);
    }

    @Test
    public void leftoverQuantityIsLimited() {
        List<Part> parts = new ArrayList<>();
        parts.add(part(60, 3));   // three 60" cuts

        // only two 96" leftovers: 2 x (60 + 60 does not fit) -> each takes one
        List<Bin> bins = Engine.packMulti(parts, bars(192, -1, 96, 2), 0);

        assertEquals(3, bins.size());
        int leftoverUsed = 0;
        for (Bin b : bins) if (Math.abs(b.stockLen - 96) < 1e-9) leftoverUsed++;
        assertEquals(2, leftoverUsed);   // never opens a third leftover
    }

    @Test
    public void pieceLongerThanEveryPipeIsOversized() {
        List<Part> parts = new ArrayList<>();
        parts.add(part(200, 1));

        List<Bin> bins = Engine.packMulti(parts, bars(192, -1, 180, -1), 0);

        assertEquals(1, bins.size());
        assertTrue(bins.get(0).over);
        assertEquals(200.0, bins.get(0).stockLen, 1e-9);
    }

    @Test
    public void capacityCountsActualBarLengths() {
        List<Part> parts = new ArrayList<>();
        parts.add(part(60, 2));

        Engine.TypeSummary ts = new Engine.TypeSummary();
        ts.type = "Z_FRAME";
        ts.bins = Engine.packMulti(parts, bars(192, -1, 96, 2), 0);
        for (Part p : parts) { ts.pcs += p.pcs; ts.totalLen += p.len * p.pcs; }

        // two 60" cuts -> one 96" leftover + one 96" leftover
        assertEquals(192.0, ts.capacity(), 1e-9);
        assertEquals(72.0, ts.waste(), 1e-9);
        assertFalse(ts.bins.isEmpty());
    }
}
