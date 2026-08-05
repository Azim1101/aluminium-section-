package com.digitalalu.alu.calc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Optimizers used by the Manual Cutting screen.
 *
 * All dimensions supplied to this class are in the app's internal unit (inches).
 * The pipe optimizer uses Best Fit Decreasing.  The sheet optimizer uses a
 * multi-pass MaxRects packing strategy.  In face-cutting mode a part's rotation
 * is locked, so the pattern / grain / face of the sheet is never turned.
 */
public final class ManualCuttingEngine {

    private ManualCuttingEngine() {}

    private static final double EPS = 0.0000001;

    /* ======================================================================
       MANUAL PIPE CUTTING (PCO)
       ====================================================================== */

    /** A user-entered pipe cut size. */
    public static class PipeCut {
        public String name;
        public double length;
        public int qty;

        public PipeCut(String name, double length, int qty) {
            this.name = name == null ? "" : name;
            this.length = length;
            this.qty = qty;
        }
    }

    /** One physical cut after a quantity has been expanded. */
    public static class PipePiece {
        public String name;
        public double length;
        public int number;

        public PipePiece(String name, double length, int number) {
            this.name = name == null ? "" : name;
            this.length = length;
            this.number = number;
        }

        public String label() {
            String base = name == null || name.trim().isEmpty() ? "Cut" : name.trim();
            return base + " " + number;
        }
    }

    /** One stock pipe and the cuts placed on it. */
    public static class PipeBar {
        public final List<PipePiece> pieces = new ArrayList<>();
        /** Cut lengths + blade kerf between cuts. */
        public double used;

        public double free(double stockLength) {
            return stockLength - used;
        }
    }

    /** Result of manual pipe optimization. */
    public static class PipePlan {
        public final List<PipeBar> bars = new ArrayList<>();
        public final List<PipePiece> oversized = new ArrayList<>();
        /** Sum of every requested cut, including a cut that is too long. */
        public double requestedCutLength;
        /** Sum of cuts that successfully fit a stock pipe. */
        public double fittedCutLength;
        public double kerfLoss;
        public double stockLength;

        public int stockCount() { return bars.size(); }
        public double stockTotal() { return stockCount() * stockLength; }
        public double offcut() { return Math.max(0, stockTotal() - fittedCutLength - kerfLoss); }
        public double utilization() {
            return stockTotal() <= EPS ? 0 : fittedCutLength * 100.0 / stockTotal();
        }
    }

    /**
     * Best Fit Decreasing pipe optimizer.  The first cut on a pipe has no
     * preceding kerf; every later cut reserves one kerf width.
     */
    public static PipePlan optimizePipes(List<PipeCut> cuts, double stockLength, double kerf) {
        PipePlan plan = new PipePlan();
        plan.stockLength = Math.max(0, stockLength);
        kerf = Math.max(0, kerf);
        if (cuts == null || stockLength <= EPS) return plan;

        List<PipePiece> all = new ArrayList<>();
        int serial = 0;
        for (PipeCut cut : cuts) {
            if (cut == null || cut.length <= EPS || cut.qty <= 0) continue;
            String name = cut.name == null || cut.name.trim().isEmpty()
                    ? "Cut" : cut.name.trim();
            for (int i = 0; i < cut.qty; i++) {
                serial++;
                PipePiece piece = new PipePiece(name, cut.length, serial);
                all.add(piece);
                plan.requestedCutLength += piece.length;
            }
        }

        Collections.sort(all, new Comparator<PipePiece>() {
            @Override public int compare(PipePiece a, PipePiece b) {
                return Double.compare(b.length, a.length);
            }
        });

        for (PipePiece piece : all) {
            if (piece.length > stockLength + EPS) {
                plan.oversized.add(piece);
                continue;
            }

            PipeBar best = null;
            double bestRemaining = Double.MAX_VALUE;
            for (PipeBar bar : plan.bars) {
                double add = piece.length + (bar.pieces.isEmpty() ? 0 : kerf);
                double remaining = stockLength - bar.used - add;
                if (remaining >= -EPS && remaining < bestRemaining) {
                    best = bar;
                    bestRemaining = remaining;
                }
            }
            if (best == null) {
                best = new PipeBar();
                plan.bars.add(best);
            }

            best.used += piece.length + (best.pieces.isEmpty() ? 0 : kerf);
            best.pieces.add(piece);
            plan.fittedCutLength += piece.length;
        }

        for (PipeBar bar : plan.bars)
            plan.kerfLoss += Math.max(0, bar.pieces.size() - 1) * kerf;
        return plan;
    }

    /* ======================================================================
       MANUAL SHEET CUTTING
       ====================================================================== */

    /** No face direction; pieces may rotate while nesting. */
    public static final int FACE_NONE = 0;
    /** First size entered by the user follows the sheet's height. */
    public static final int FACE_HEIGHT = 1;
    /** First size entered by the user follows the sheet's width. */
    public static final int FACE_WIDTH = 2;

    /** A user-entered sheet piece.  First / second preserve the entered order. */
    public static class SheetCut {
        public String name;
        public double first;
        public double second;
        public int qty;

        public SheetCut(String name, double first, double second, int qty) {
            this.name = name == null ? "" : name;
            this.first = first;
            this.second = second;
            this.qty = qty;
        }
    }

    /** One piece placed on a sheet. x/y/w/h are always actual, not gap-inflated. */
    public static class SheetPiece {
        public String name;
        public int number;
        public double first;
        public double second;
        public double w;
        public double h;
        public double x;
        public double y;
        public boolean rotated;

        private SheetPiece copy() {
            SheetPiece p = new SheetPiece();
            p.name = name;
            p.number = number;
            p.first = first;
            p.second = second;
            p.w = w;
            p.h = h;
            return p;
        }

        public String label() {
            String base = name == null || name.trim().isEmpty() ? "Piece" : name.trim();
            return base + " " + number;
        }

        public double area() { return w * h; }
    }

    /** A rectangular free region used internally by MaxRects. */
    private static class Rect {
        double x, y, w, h;
        Rect(double x, double y, double w, double h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        double area() { return Math.max(0, w) * Math.max(0, h); }
    }

    private static class Placement {
        Rect free;
        double x, y;
        double actualW, actualH;
        double footprintW, footprintH;
        boolean rotated;
        double shortSide, longSide, areaFit;
    }

    /** One stock sheet and all pieces nested on it. */
    public static class SheetBin {
        public final List<SheetPiece> pieces = new ArrayList<>();
        public final double width;
        public final double height;
        public final double gap;
        public double usedArea;
        private final List<Rect> free = new ArrayList<>();

        private SheetBin(double width, double height, double gap) {
            this.width = width;
            this.height = height;
            this.gap = gap;
            /*
             * Add one gap on the far right and bottom.  Each piece is expanded
             * by a gap, which creates a gap only between cuts while still
             * allowing a piece that exactly matches the sheet edge.
             */
            free.add(new Rect(0, 0, width + gap, height + gap));
        }

        public double area() { return width * height; }
        public double waste() { return Math.max(0, area() - usedArea); }
        public double utilization() { return area() <= EPS ? 0 : usedArea * 100.0 / area(); }
        public int freeRegionCount() { return free.size(); }
        public double largestFreeArea() {
            double out = 0;
            for (Rect r : free) out = Math.max(out, r.area());
            return out;
        }

        private Placement findBest(SheetPiece p, boolean allowRotation) {
            Placement best = null;
            for (Rect r : free) {
                Placement normal = fit(r, p.w, p.h, false);
                if (isBetter(normal, best)) best = normal;
                if (allowRotation && Math.abs(p.w - p.h) > EPS) {
                    Placement turn = fit(r, p.h, p.w, true);
                    if (isBetter(turn, best)) best = turn;
                }
            }
            return best;
        }

        private Placement fit(Rect r, double actualW, double actualH, boolean rotated) {
            double fw = actualW + gap;
            double fh = actualH + gap;
            if (fw > r.w + EPS || fh > r.h + EPS) return null;
            Placement out = new Placement();
            out.free = r;
            out.x = r.x;
            out.y = r.y;
            out.actualW = actualW;
            out.actualH = actualH;
            out.footprintW = fw;
            out.footprintH = fh;
            out.rotated = rotated;
            double leftW = r.w - fw;
            double leftH = r.h - fh;
            out.shortSide = Math.min(leftW, leftH);
            out.longSide = Math.max(leftW, leftH);
            out.areaFit = r.area() - fw * fh;
            return out;
        }

        private void place(SheetPiece piece, Placement p) {
            piece.x = p.x;
            piece.y = p.y;
            piece.w = p.actualW;
            piece.h = p.actualH;
            piece.rotated = p.rotated;
            pieces.add(piece);
            usedArea += piece.area();

            Rect used = new Rect(p.x, p.y, p.footprintW, p.footprintH);
            for (int i = free.size() - 1; i >= 0; i--) {
                Rect r = free.get(i);
                if (!intersects(r, used)) continue;
                splitFreeNode(r, used);
                free.remove(i);
            }
            pruneFreeList();
        }

        private void splitFreeNode(Rect r, Rect used) {
            if (used.x < r.x + r.w - EPS && used.x + used.w > r.x + EPS) {
                if (used.y > r.y + EPS)
                    addFree(r.x, r.y, r.w, used.y - r.y);
                if (used.y + used.h < r.y + r.h - EPS)
                    addFree(r.x, used.y + used.h, r.w, r.y + r.h - (used.y + used.h));
            }
            if (used.y < r.y + r.h - EPS && used.y + used.h > r.y + EPS) {
                if (used.x > r.x + EPS)
                    addFree(r.x, r.y, used.x - r.x, r.h);
                if (used.x + used.w < r.x + r.w - EPS)
                    addFree(used.x + used.w, r.y, r.x + r.w - (used.x + used.w), r.h);
            }
        }

        private void addFree(double x, double y, double w, double h) {
            if (w > EPS && h > EPS) free.add(new Rect(x, y, w, h));
        }

        private void pruneFreeList() {
            for (int i = 0; i < free.size(); i++) {
                Rect a = free.get(i);
                boolean removeA = false;
                for (int j = 0; j < free.size(); j++) {
                    if (i == j) continue;
                    Rect b = free.get(j);
                    if (contains(b, a)) {
                        removeA = true;
                        break;
                    }
                }
                if (removeA) {
                    free.remove(i);
                    i--;
                }
            }
        }
    }

    /** Result of manual sheet optimization. */
    public static class SheetPlan {
        public final List<SheetBin> sheets = new ArrayList<>();
        public final List<SheetPiece> oversized = new ArrayList<>();
        public double sheetWidth;
        public double sheetHeight;
        public double gap;
        public boolean faceCut;
        public int faceAxis;
        public double requestedArea;
        public double placedArea;
        public String algorithm = "MaxRects · Best Short Side Fit";

        public int sheetCount() { return sheets.size(); }
        public double stockArea() { return sheetCount() * sheetWidth * sheetHeight; }
        public double waste() { return Math.max(0, stockArea() - placedArea); }
        public double utilization() {
            return stockArea() <= EPS ? 0 : placedArea * 100.0 / stockArea();
        }
    }

    /**
     * Optimizes a list of rectangular sheet cuts.
     *
     * Normal mode permits rotation for a tighter layout.  Face-cutting mode
     * locks every part direction:
     *  - FACE_HEIGHT: first entered size runs along the stock sheet height
     *  - FACE_WIDTH : first entered size runs along the stock sheet width
     */
    public static SheetPlan optimizeSheets(List<SheetCut> cuts, double sheetWidth,
                                           double sheetHeight, double gap,
                                           boolean faceCut, int faceAxis) {
        SheetPlan empty = makePlan(sheetWidth, sheetHeight, gap, faceCut, faceAxis);
        if (cuts == null || sheetWidth <= EPS || sheetHeight <= EPS) return empty;

        List<SheetPiece> pieces = expandSheetCuts(cuts, faceCut, faceAxis, empty);
        if (pieces.isEmpty()) return empty;

        /* Different sort orders often produce a better nesting for the same
           MaxRects heuristic.  We keep the result with the fewest sheets. */
        SheetPlan best = null;
        for (int mode = 0; mode < 4; mode++) {
            List<SheetPiece> trial = copyPieces(pieces);
            sortPieces(trial, mode);
            SheetPlan candidate = packSheets(trial, sheetWidth, sheetHeight, gap,
                    faceCut, faceAxis, mode);
            if (isPlanBetter(candidate, best)) best = candidate;
        }
        return best == null ? empty : best;
    }

    private static SheetPlan makePlan(double sheetWidth, double sheetHeight, double gap,
                                      boolean faceCut, int faceAxis) {
        SheetPlan plan = new SheetPlan();
        plan.sheetWidth = Math.max(0, sheetWidth);
        plan.sheetHeight = Math.max(0, sheetHeight);
        plan.gap = Math.max(0, gap);
        plan.faceCut = faceCut;
        plan.faceAxis = faceCut
                ? (faceAxis == FACE_WIDTH ? FACE_WIDTH : FACE_HEIGHT)
                : FACE_NONE;
        return plan;
    }

    private static List<SheetPiece> expandSheetCuts(List<SheetCut> cuts, boolean faceCut,
                                                      int faceAxis, SheetPlan info) {
        List<SheetPiece> out = new ArrayList<>();
        int serial = 0;
        boolean faceOnWidth = faceCut && faceAxis == FACE_WIDTH;
        for (SheetCut cut : cuts) {
            if (cut == null || cut.first <= EPS || cut.second <= EPS || cut.qty <= 0) continue;
            String name = cut.name == null || cut.name.trim().isEmpty() ? "Piece" : cut.name.trim();
            for (int i = 0; i < cut.qty; i++) {
                serial++;
                SheetPiece p = new SheetPiece();
                p.name = name;
                p.number = serial;
                p.first = cut.first;
                p.second = cut.second;
                /* First size is the user's height / face-size field. */
                if (faceOnWidth) {
                    p.w = cut.first;
                    p.h = cut.second;
                } else {
                    p.w = cut.second;
                    p.h = cut.first;
                }
                info.requestedArea += p.area();
                out.add(p);
            }
        }
        return out;
    }

    private static List<SheetPiece> copyPieces(List<SheetPiece> src) {
        List<SheetPiece> out = new ArrayList<>();
        for (SheetPiece p : src) out.add(p.copy());
        return out;
    }

    private static void sortPieces(List<SheetPiece> list, final int mode) {
        Collections.sort(list, new Comparator<SheetPiece>() {
            @Override public int compare(SheetPiece a, SheetPiece b) {
                double aa = a.area(), ba = b.area();
                double am = Math.max(a.w, a.h), bm = Math.max(b.w, b.h);
                int cmp;
                if (mode == 1) {
                    cmp = Double.compare(bm, am);                 // longest side
                    if (cmp == 0) cmp = Double.compare(ba, aa);
                } else if (mode == 2) {
                    cmp = Double.compare(b.h, a.h);               // height first
                    if (cmp == 0) cmp = Double.compare(b.w, a.w);
                } else if (mode == 3) {
                    cmp = Double.compare(b.w, a.w);               // width first
                    if (cmp == 0) cmp = Double.compare(b.h, a.h);
                } else {
                    cmp = Double.compare(ba, aa);                 // area first
                    if (cmp == 0) cmp = Double.compare(bm, am);
                }
                if (cmp == 0) cmp = Integer.compare(a.number, b.number);
                return cmp;
            }
        });
    }

    private static SheetPlan packSheets(List<SheetPiece> pieces, double sheetWidth,
                                        double sheetHeight, double gap, boolean faceCut,
                                        int faceAxis, int mode) {
        SheetPlan plan = makePlan(sheetWidth, sheetHeight, gap, faceCut, faceAxis);
        plan.algorithm = "MaxRects · Best Short Side Fit (multi-pass)";
        boolean allowRotation = !faceCut;

        for (SheetPiece piece : pieces) {
            Placement bestPlacement = null;
            SheetBin bestSheet = null;
            for (SheetBin bin : plan.sheets) {
                Placement p = bin.findBest(piece, allowRotation);
                if (isBetter(p, bestPlacement)) {
                    bestPlacement = p;
                    bestSheet = bin;
                }
            }

            if (bestSheet == null) {
                SheetBin fresh = new SheetBin(sheetWidth, sheetHeight, gap);
                Placement p = fresh.findBest(piece, allowRotation);
                if (p == null) {
                    plan.oversized.add(piece);
                    continue;
                }
                fresh.place(piece, p);
                plan.sheets.add(fresh);
            } else {
                bestSheet.place(piece, bestPlacement);
            }
        }

        for (SheetBin bin : plan.sheets) plan.placedArea += bin.usedArea;
        /* copied from expandSheetCuts because the trial starts from copies */
        for (SheetPiece piece : pieces) plan.requestedArea += piece.area();
        return plan;
    }

    private static boolean isPlanBetter(SheetPlan candidate, SheetPlan current) {
        if (current == null) return true;
        if (candidate.oversized.size() != current.oversized.size())
            return candidate.oversized.size() < current.oversized.size();
        if (candidate.sheetCount() != current.sheetCount())
            return candidate.sheetCount() < current.sheetCount();

        /* With the same number of sheets and same cut area, total waste is the
           same. Prefer a plan with a larger reusable leftover rectangle. */
        double ca = largestReusableOffcut(candidate);
        double cb = largestReusableOffcut(current);
        return ca > cb + EPS;
    }

    private static double largestReusableOffcut(SheetPlan plan) {
        double out = 0;
        for (SheetBin bin : plan.sheets) out = Math.max(out, bin.largestFreeArea());
        return out;
    }

    private static boolean isBetter(Placement a, Placement b) {
        if (a == null) return false;
        if (b == null) return true;
        if (a.shortSide < b.shortSide - EPS) return true;
        if (a.shortSide > b.shortSide + EPS) return false;
        if (a.longSide < b.longSide - EPS) return true;
        if (a.longSide > b.longSide + EPS) return false;
        return a.areaFit < b.areaFit - EPS;
    }

    private static boolean intersects(Rect a, Rect b) {
        return b.x < a.x + a.w - EPS && b.x + b.w > a.x + EPS
                && b.y < a.y + a.h - EPS && b.y + b.h > a.y + EPS;
    }

    private static boolean contains(Rect outer, Rect inner) {
        return inner.x >= outer.x - EPS && inner.y >= outer.y - EPS
                && inner.x + inner.w <= outer.x + outer.w + EPS
                && inner.y + inner.h <= outer.y + outer.h + EPS;
    }
}
