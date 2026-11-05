package com.digitalalu.alu.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Regression cover for the "2 then 5 becomes 52" caret bug on the height cell.
 * Rebinding a row must not rewrite a cell that already shows the same value.
 */
public class CellTextTest {

    @Test
    public void identicalTextIsNeverRewritten() {
        // caret must survive a rebind right after typing "2"
        assertTrue(CellText.keepWhileTyping("2", "2", true));
        assertTrue(CellText.keepWhileTyping("", "", false));
        assertTrue(CellText.keepWhileTyping("25", "25", true));
    }

    @Test
    public void partialInputKeepsItsCaret() {
        // user is mid-typing "2.5" — don't clamp the field back to "2"
        assertTrue(CellText.keepWhileTyping("2.", "2", true));
        assertTrue(CellText.keepWhileTyping("2.50", "2.5", true));
    }

    @Test
    public void aDifferentValueIsStillWritten() {
        assertFalse(CellText.keepWhileTyping("2", "25", true));
        assertFalse(CellText.keepWhileTyping("25", "2", true));
        // not focused -> normalise the display even when the value matches
        assertFalse(CellText.keepWhileTyping("05", "5", false));
    }

    @Test
    public void unreadableTextCountsAsZero() {
        assertEquals(0.0, CellText.parse(""), 1e-9);
        assertEquals(0.0, CellText.parse("abc"), 1e-9);
        assertEquals(25.0, CellText.parse(" 25 "), 1e-9);
        assertEquals(2.5, CellText.parse("2.5"), 1e-9);
    }
}
