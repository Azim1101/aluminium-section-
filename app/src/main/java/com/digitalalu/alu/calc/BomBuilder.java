package com.digitalalu.alu.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v1.0 — BOM / supplier order list (inspired by pro aluminium cutting apps:
 * the fabricator hands this to the supplier, NOT the customer).
 *
 * Groups everything the workshop must BUY for the current sheet:
 *   - full-length pipes per section (from the bin-packing result — the same
 *     number the cutting plan already uses, never recomputed here)
 *   - glass area
 *   - shutter / RP counts for hardware shopping
 *
 * Two modes:
 *   supplierMode = true  → quantities only (safe to forward to a supplier)
 *   supplierMode = false → adds rates from the price book (internal copy)
 *
 * Pure calc layer — unit-testable on the JVM.
 */
public class BomBuilder {

    public static class Row {
        public String section;     // "ZED FRAME", "DOMAL SUTTER", ...
        public int pipes;          // full stock pipes to buy
        public double stockLen;    // length of one pipe (inches)
        public int cuts;           // number of cut pieces
        public double cutLen;      // total cut length (inches)
        public double wastePct;    // waste on those pipes
    }

    public static class Bom {
        public final List<Row> rows = new ArrayList<Row>();
        public int totalPipes;
        public double glassSqft;
        public int shutterPcs;
        public int rpPcs;
        public int windows;
        public int nos;
        /** supplier mode = no money figures in toText() */
        public String bizName = "";
    }

    public static Bom build(List<Engine.WinResult> res,
                            Map<String, Engine.TypeSummary> sum,
                            Settings s) {
        Bom b = new Bom();
        if (sum != null) {
            for (Map.Entry<String, Engine.TypeSummary> e : sum.entrySet()) {
                Engine.TypeSummary ts = e.getValue();
                if (ts == null || ts.pcs <= 0) continue;
                Row r = new Row();
                r.section = sectionLabel(e.getKey());
                r.pipes = ts.stockNeeded();
                r.cuts = ts.pcs;
                r.cutLen = ts.totalLen;
                double cap = ts.capacity();
                r.stockLen = dominantStock(ts, s);
                r.wastePct = cap > 0 ? ts.waste() / cap * 100.0 : 0;
                b.rows.add(r);
                b.totalPipes += r.pipes;
                if (r.section.contains("SUTTER")) b.shutterPcs += r.cuts;
                if (r.section.endsWith("RP") || r.section.endsWith(" RP")) b.rpPcs += r.cuts;
            }
        }
        Engine.Grand g = res == null ? null : Engine.grand(res, sum, s);
        if (g != null) {
            b.glassSqft = g.glassSqft;
            b.windows = g.windows;
            b.nos = g.nos;
        }
        return b;
    }

    /** The stock length that appears most across the section's bins. */
    private static double dominantStock(Engine.TypeSummary ts, Settings s) {
        double best = 0; int bestN = 0;
        java.util.Map<Double, Integer> count = new java.util.LinkedHashMap<Double, Integer>();
        if (ts.bins != null) for (Engine.Bin bin : ts.bins) {
            if (bin.stockLen <= 0) continue;
            Integer n = count.get(bin.stockLen);
            count.put(bin.stockLen, n == null ? 1 : n + 1);
        }
        for (Map.Entry<Double, Integer> e : count.entrySet())
            if (e.getValue() > bestN) { bestN = e.getValue(); best = e.getKey(); }
        if (best <= 0 && s != null) best = s.stock;
        return best;
    }

    public static String sectionLabel(String type) {
        String sys = type.startsWith("D_") ? "DOMAL" : "ZED";
        int us = type.indexOf('_');
        String part = us >= 0 ? type.substring(us + 1) : type;
        if ("MULIYA".equals(part)) part = "MULIYA";
        if ("RT".equals(part)) part = "RT";
        return sys + " " + part;
    }

    private static String fmt(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.format(Locale.US, "%.2f", d);
    }

    /** The share-ready text. supplierMode hides nothing that costs — there are
     *  no prices in a BOM anyway; prices live on the ESTIMATE, not the order. */
    public String toText(Bom b, boolean supplierMode) {
        StringBuilder sb = new StringBuilder();
        sb.append("*MATERIAL ORDER (BOM)*\n");
        if (b.bizName != null && b.bizName.length() > 0 && !supplierMode)
            sb.append(b.bizName).append('\n');
        sb.append("Windows: ").append(b.windows).append(" row / ").append(b.nos).append(" nos\n");
        sb.append("──────────────\n");
        sb.append("ALUMINIUM PIPES\n");
        for (Row r : b.rows) {
            sb.append("• ").append(r.section)
              .append(": *").append(r.pipes).append(" pipe* (").append(fmt(r.stockLen)).append("\")")
              .append("  [").append(r.cuts).append(" cut, waste ").append(fmt(r.wastePct)).append("%]");
            sb.append('\n');
        }
        sb.append("──────────────\n");
        if (b.glassSqft > 0)
            sb.append("GLASS: ").append(fmt(b.glassSqft)).append(" sqft\n");
        if (b.shutterPcs > 0)
            sb.append("SUTTER hardware: ").append(b.shutterPcs).append(" pcs\n");
        if (b.rpPcs > 0)
            sb.append("RP grill: ").append(b.rpPcs).append(" pcs\n");
        sb.append("──────────────\n");
        sb.append("Total pipes: ").append(b.totalPipes).append(" · ALU Window");
        return sb.toString();
    }
}
