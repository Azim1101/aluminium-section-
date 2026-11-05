package com.digitalalu.alu.design;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Rectangular elevation only. Dimensions are mm, not fabrication deductions. */
public final class DesignDocument {
    public enum Kind { WINDOW, DOOR }
    public enum Unit {
        MM(1), INCH(25.4);
        private final double factor;
        Unit(double factor) { this.factor = factor; }
        public double toMm(double value) { return value * factor; }
        public double fromMm(double value) { return value / factor; }
    }
    public static final class Rect {
        public final double x, y, width, height;
        Rect(double x, double y, double width, double height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }
    public final Kind kind;
    public final double widthMm, heightMm, frameMm, mullionMm;
    public final int bays;

    public DesignDocument(Kind kind, double width, double height, double frame,
                          double mullion, int bays) {
        if (kind == null) throw new IllegalArgumentException("Choose window or door");
        positive(width, "Width"); positive(height, "Height");
        positive(frame, "Frame face"); positive(mullion, "Mullion face");
        if (width > 20000 || height > 20000)
            throw new IllegalArgumentException("Maximum elevation size is 20,000 mm");
        if (bays < 1 || bays > 12) throw new IllegalArgumentException("Bays must be 1–12");
        if (height - 2 * frame < 1 || (width - 2 * frame - (bays - 1) * mullion) / bays < 1)
            throw new IllegalArgumentException("Face widths leave less than 1 mm clear opening");
        this.kind = kind; widthMm = width; heightMm = height;
        frameMm = frame; mullionMm = mullion; this.bays = bays;
    }
    private static void positive(double v, String label) {
        if (!Double.isFinite(v) || v <= 0)
            throw new IllegalArgumentException(label + " must be finite and greater than zero");
    }
    public double clearBayWidthMm() {
        return (widthMm - 2 * frameMm - (bays - 1) * mullionMm) / bays;
    }
    public List<Rect> openings() {
        List<Rect> result = new ArrayList<>(bays);
        for (int i = 0; i < bays; i++) result.add(new Rect(
                frameMm + i * (clearBayWidthMm() + mullionMm), frameMm,
                clearBayWidthMm(), heightMm - 2 * frameMm));
        return Collections.unmodifiableList(result);
    }
    /** Non-overlapping face rectangles, same source for preview and CAD export. */
    public List<Rect> members() {
        List<Rect> result = new ArrayList<>(bays + 3);
        result.add(new Rect(0, 0, widthMm, frameMm));
        result.add(new Rect(0, heightMm - frameMm, widthMm, frameMm));
        result.add(new Rect(0, frameMm, frameMm, heightMm - 2 * frameMm));
        result.add(new Rect(widthMm - frameMm, frameMm, frameMm, heightMm - 2 * frameMm));
        for (int i = 1; i < bays; i++) result.add(new Rect(
                frameMm + i * clearBayWidthMm() + (i - 1) * mullionMm,
                frameMm, mullionMm, heightMm - 2 * frameMm));
        return Collections.unmodifiableList(result);
    }
    public double clearOpeningAreaM2() {
        return bays * clearBayWidthMm() * (heightMm - 2 * frameMm) / 1_000_000;
    }
}
