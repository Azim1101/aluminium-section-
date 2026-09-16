package com.digitalalu.alu.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Numeric cells are typed one digit at a time, so the parser has to survive
 * half typed text without throwing and without inventing a size.
 */
public class TextFieldsTest {

    @Test
    public void parsesPlainNumbers() {
        assertEquals(45.0, TextFields.parse("45", 0), 0.0001);
        assertEquals(45.5, TextFields.parse("45.5", 0), 0.0001);
        assertEquals(45.0, TextFields.parse("  45  ", 0), 0.0001);
    }

    @Test
    public void acceptsCommaAsDecimalSeparator() {
        // several soft keyboards offer "," on the numeric pad
        assertEquals(45.5, TextFields.parse("45,5", 0), 0.0001);
    }

    @Test
    public void halfTypedTextFallsBackInsteadOfThrowing() {
        assertEquals(0.0, TextFields.parseOrZero(""), 0.0001);
        assertEquals(0.0, TextFields.parseOrZero("   "), 0.0001);
        assertEquals(0.0, TextFields.parseOrZero("."), 0.0001);
        assertEquals(0.0, TextFields.parseOrZero("-"), 0.0001);
        assertEquals(0.0, TextFields.parseOrZero(null), 0.0001);
        assertEquals(12.0, TextFields.parse("", 12), 0.0001);
        assertEquals(12.0, TextFields.parse("abc", 12), 0.0001);
    }

    @Test
    public void rejectsNaNAndInfinity() {
        assertEquals(7.0, TextFields.parse("NaN", 7), 0.0001);
        assertEquals(7.0, TextFields.parse("Infinity", 7), 0.0001);
    }
}
