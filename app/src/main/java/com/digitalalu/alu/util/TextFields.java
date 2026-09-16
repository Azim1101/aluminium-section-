package com.digitalalu.alu.util;

import android.widget.EditText;

/**
 * Small helpers for the numeric cells used all over the app.
 *
 * <p>Both exist because of one Android detail: {@code EditText.setText()} puts the
 * caret at index 0. Whenever a field is pre-filled (or rebound) and the user then
 * types, the first character lands in FRONT of what is already there — which is how
 * a Height of 45 came out as 54.
 */
public final class TextFields {

    private TextFields() {}

    /** Move the caret to the end of the current text. Call right after setText(). */
    public static void caretToEnd(EditText et) {
        if (et == null || et.getText() == null) return;
        et.setSelection(et.getText().length());
    }

    /**
     * Write {@code value} into a cell without disturbing the user.
     *
     * <ul>
     *   <li>identical text  -> nothing is written, so the caret does not move</li>
     *   <li>focused and the same number in a different shape ("45.50" vs "45.5",
     *       "0" vs "") -> left alone, the keyboard is the source of truth</li>
     *   <li>otherwise -> written, and the caret is restored to where it was</li>
     * </ul>
     */
    public static void setTextKeepCaret(EditText et, String value) {
        if (et == null) return;
        String text = value == null ? "" : value;
        CharSequence old = et.getText();
        String now = old == null ? "" : old.toString();
        if (now.equals(text)) return;

        boolean focused = et.hasFocus();
        if (focused && parse(now, 0) == parse(text, 0)) return;

        int caret = et.getSelectionStart();
        et.setText(text);
        if (focused) {
            int pos = caret < 0 ? text.length() : Math.min(caret, text.length());
            et.setSelection(pos);
        }
    }

    /**
     * Parse a measurement cell. Empty or half typed text ("", ".", "-") falls back to
     * {@code fallback} instead of throwing, and "," is accepted as a decimal separator
     * because several soft keyboards offer it on the numeric pad.
     */
    public static double parse(String s, double fallback) {
        if (s == null) return fallback;
        String v = s.trim().replace(',', '.');
        if (v.isEmpty() || v.equals(".") || v.equals("-")) return fallback;
        try {
            double d = Double.parseDouble(v);
            return Double.isNaN(d) || Double.isInfinite(d) ? fallback : d;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Same as {@link #parse(String, double)} but blank means 0. */
    public static double parseOrZero(String s) {
        return parse(s, 0);
    }
}
