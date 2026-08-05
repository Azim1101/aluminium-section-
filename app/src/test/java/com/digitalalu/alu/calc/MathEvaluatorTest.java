package com.digitalalu.alu.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Core math evaluator used by every custom formula. */
public class MathEvaluatorTest {

    private static double eval(String f) {
        // h=50, w=30, q=2, sutterH=48, sutterW=26
        return MathEvaluator.eval(f, 50, 30, 2, 48, 26);
    }

    @Test
    public void basicArithmetic() {
        assertEquals(14.0, eval("2 + 3 * 4"), 1e-9);
        assertEquals(20.0, eval("(2 + 3) * 4"), 1e-9);
        assertEquals(2.5, eval("5 / 2"), 1e-9);
        assertEquals(1.0, eval("5 % 2"), 1e-9);
        assertEquals(-6.0, eval("-H + 44"), 1e-9);
    }

    @Test
    public void windowVariables() {
        assertEquals(48.0, eval("H - 2.0"), 1e-9);
        assertEquals(15.0, eval("W / q"), 1e-9);
        assertEquals(44.0, eval("sutterH - 4.0"), 1e-9);
        assertEquals(22.0, eval("sutterW - 4.0"), 1e-9);
        // case-insensitive variables
        assertEquals(80.0, eval("h + w"), 1e-9);
        // q aliases
        assertEquals(2.0, eval("nos"), 1e-9);
    }

    @Test
    public void unknownVariableIsZero() {
        assertEquals(5.0, eval("x + 5"), 1e-9);
    }

    @Test
    public void functions() {
        assertEquals(50.0, eval("max(H, W)"), 1e-9);
        assertEquals(30.0, eval("min(H, 30)"), 1e-9);
        assertEquals(7.0, eval("sqrt(49)"), 1e-9);
        assertEquals(6.0, eval("abs(-6)"), 1e-9);
    }

    @Test
    public void comparisonsAndLogic() {
        assertEquals(1.0, eval("H > W"), 1e-9);
        assertEquals(0.0, eval("H < W"), 1e-9);
        assertEquals(1.0, eval("H >= 50"), 1e-9);
        assertEquals(1.0, eval("H == 50"), 1e-9);
        assertEquals(0.0, eval("H != 50"), 1e-9);
        assertEquals(1.0, eval("H > 40 && W > 20"), 1e-9);
        assertEquals(1.0, eval("H > 60 || W > 20"), 1e-9);
    }

    @Test(expected = RuntimeException.class)
    public void invalidInputThrows() {
        MathEvaluator.eval("2 +", 0, 0, 0, 0, 0);
    }
}
