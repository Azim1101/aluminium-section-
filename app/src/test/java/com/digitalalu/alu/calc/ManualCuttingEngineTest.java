package com.digitalalu.alu.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.digitalalu.alu.calc.ManualCuttingEngine.PipeCut;
import com.digitalalu.alu.calc.ManualCuttingEngine.PipePlan;
import com.digitalalu.alu.calc.ManualCuttingEngine.SheetCut;
import com.digitalalu.alu.calc.ManualCuttingEngine.SheetPiece;
import com.digitalalu.alu.calc.ManualCuttingEngine.SheetPlan;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Standalone PCO + MaxRects sheet nesting. */
public class ManualCuttingEngineTest {

    /* ---------------- pipes ---------------- */

    @Test
    public void pipeOptimizerPairsCutsIntoFewestBars() {
        List<PipeCut> cuts = Arrays.asList(
                new PipeCut("A", 40, 2),
                new PipeCut("B", 58, 2));

        PipePlan plan = ManualCuttingEngine.optimizePipes(cuts, 100, 1);

        // 58 + kerf + 40 = 99 fits one bar → 2 bars total
        assertEquals(2, plan.stockCount());
        assertTrue(plan.oversized.isEmpty());
        assertEquals(196.0, plan.fittedCutLength, 1e-9);
        assertEquals(196.0, plan.requestedCutLength, 1e-9);
        assertEquals(2.0, plan.kerfLoss, 1e-9);   // one kerf per bar
    }

    @Test
    public void pipeOptimizerFlagsOversized() {
        PipePlan plan = ManualCuttingEngine.optimizePipes(
                Arrays.asList(new PipeCut("L", 120, 1)), 100, 1);
        assertEquals(1, plan.oversized.size());
        assertEquals(0, plan.stockCount());
        assertEquals(0.0, plan.fittedCutLength, 1e-9);
    }

    @Test
    public void pipeOptimizerEmptyInput() {
        PipePlan plan = ManualCuttingEngine.optimizePipes(new ArrayList<PipeCut>(), 100, 1);
        assertEquals(0, plan.stockCount());
    }

    /* ---------------- sheets ---------------- */

    @Test
    public void sheetNestingFitsPiecesOnOneSheet() {
        SheetPlan plan = ManualCuttingEngine.optimizeSheets(
                Arrays.asList(new SheetCut("P", 24, 48, 2)),
                48, 96, 0, false, ManualCuttingEngine.FACE_NONE);

        assertEquals(1, plan.sheetCount());
        assertTrue(plan.oversized.isEmpty());
        assertEquals(2 * 24.0 * 48.0, plan.placedArea, 1e-6);
    }

    @Test
    public void sheetNestingFlagsOversizedPiece() {
        SheetPlan plan = ManualCuttingEngine.optimizeSheets(
                Arrays.asList(new SheetCut("BIG", 100, 100, 1)),
                48, 96, 0, false, ManualCuttingEngine.FACE_NONE);

        assertEquals(1, plan.oversized.size());
        assertEquals(0, plan.sheetCount());
    }

    @Test
    public void faceHeightLocksFirstSizeToSheetHeight() {
        SheetPlan plan = ManualCuttingEngine.optimizeSheets(
                Arrays.asList(new SheetCut("F", 4, 6, 1)),
                8, 8, 0, true, ManualCuttingEngine.FACE_HEIGHT);

        assertEquals(1, plan.sheetCount());
        SheetPiece p = plan.sheets.get(0).pieces.get(0);
        assertFalse("face pieces must never rotate", p.rotated);
        assertEquals(4.0, p.h, 1e-9);   // first size along sheet height
        assertEquals(6.0, p.w, 1e-9);
    }

    @Test
    public void faceWidthLocksFirstSizeToSheetWidth() {
        SheetPlan plan = ManualCuttingEngine.optimizeSheets(
                Arrays.asList(new SheetCut("F", 4, 6, 1)),
                8, 8, 0, true, ManualCuttingEngine.FACE_WIDTH);

        assertEquals(1, plan.sheetCount());
        SheetPiece p = plan.sheets.get(0).pieces.get(0);
        assertFalse("face pieces must never rotate", p.rotated);
        assertEquals(4.0, p.w, 1e-9);   // first size along sheet width
        assertEquals(6.0, p.h, 1e-9);
    }
}
