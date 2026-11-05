package com.digitalalu.alu.ui;

/**
 * Grid cell text helpers — pure Java so they can be unit tested on the JVM.
 *
 * <p>Problem this solves: while the user is typing a height like "25" in a row,
 * the adapter can rebind that row (a new blank row is appended as soon as the
 * first digit is typed). A plain {@code setText()} moves the caret back to
 * position 0, so the next digit lands in front: "2" + "5" becomes "52".
 *
 * <p>Rule: never rewrite a cell whose text already shows the value we want.
 */
public final class CellText {

    private CellText() {}

    /** Parses a cell; anything unreadable is treated as 0 (matches grid behaviour). */
    public static double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * @param current text already in the EditText
     * @param next    text we are about to write into it
     * @param focused true when the user's caret is inside this field
     * @return true when writing {@code next} would only disturb the caret and
     *         change nothing visible, so the caller should skip {@code setText}.
     */
    public static boolean keepWhileTyping(String current, String next, boolean focused) {
        if (current == null) return false;
        if (current.equals(next)) return true;                    // identical — don't touch it
        if (focused && parse(current) == parse(next)) return true; // mid-typing: "2." vs "2"
        return false;
    }
}
