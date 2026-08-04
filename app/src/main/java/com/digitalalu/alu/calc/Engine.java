package com.digitalalu.alu.calc;

import com.digitalalu.alu.model.WindowItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All calculation in INCH.
 * ZED   pipes: Z-FRAME / Z-SUTTER / Z-MULIYA / Z-RP
 * DOMAL pipes: D-FRAME / D-SUTTER / D-RT     / D-RP
 *
 * v2: Best Fit Decreasing (BFD) bin packing for better optimization.
 */
public class Engine {

    public static List<String> getTypes() {
        List<String> list = new ArrayList<>();
        list.add("Z_FRAME"); list.add("Z_SUTTER"); list.add("Z_MULIYA"); list.add("Z_RP");
        list.add("D_FRAME"); list.add("D_SUTTER"); list.add("D_RT"); list.add("D_RP");

        if (CustomFormulaManager.activeSystems != null) {
            for (int i = 2; i < CustomFormulaManager.activeSystems.size(); i++) {
                CustomFormulaManager.CustomSystem cs = CustomFormulaManager.activeSystems.get(i);
                String p = cs.name.toUpperCase() + "_";
                list.add(p + "FRAME");
                list.add(p + "SUTTER");
                if (!cs.muliyaH_formula.isEmpty()) {
                    list.add(p + cs.muliyaLabel.toUpperCase());
                }
                list.add(p + "RP");
            }
        }
        return list;
    }

    public static int colorOf(String type) {
        if (type.startsWith("Z_")) {
            if (type.endsWith("FRAME")) return 0xFF3B82F6;
            if (type.endsWith("SUTTER")) return 0xFF22C55E;
            if (type.endsWith("MULIYA")) return 0xFF8B5CF6;
            if (type.endsWith("RP")) return 0xFFEC4899;
        } else if (type.startsWith("D_")) {
            if (type.endsWith("FRAME")) return 0xFF0EA5E9;
            if (type.endsWith("SUTTER")) return 0xFF14B8A6;
            if (type.endsWith("RT")) return 0xFFF59E0B;
            if (type.endsWith("RP")) return 0xFFF43F5E;
        } else {
            int hash = type.hashCode();
            return 0xFF000000 | (hash & 0x00FFFFFF);
        }
        return 0xFF64748B;
    }

    public static String nameOf(String type) {
        if (type.startsWith("Z_")) {
            if (type.endsWith("FRAME")) return "ZED FRAME";
            if (type.endsWith("SUTTER")) return "ZED SUTTER";
            if (type.endsWith("MULIYA")) return "ZED MULIYA";
            if (type.endsWith("RP")) return "ZED RP GRILL";
        } else if (type.startsWith("D_")) {
            if (type.endsWith("FRAME")) return "DOMAL FRAME";
            if (type.endsWith("SUTTER")) return "DOMAL SUTTER";
            if (type.endsWith("RT")) return "DOMAL RT";
            if (type.endsWith("RP")) return "DOMAL RP GRILL";
        } else {
            int idx = type.indexOf('_');
            if (idx > 0) {
                String sysName = type.substring(0, idx);
                String partName = type.substring(idx + 1);
                if (partName.equals("RP")) partName = "RP GRILL";
                return sysName + " " + partName;
            }
        }
        return type;
    }

    /** short label without system prefix */
    public static String shortName(String type) {
        String n = nameOf(type);
        if (n.startsWith("ZED ")) return n.substring(4);
        if (n.startsWith("DOMAL ")) return n.substring(6);
        int idx = n.indexOf(' ');
        if (idx > 0) return n.substring(idx + 1);
        return n;
    }

    /** Short part name: FRAME, SUTTER, MULIYA, RT, RP */
    public static String partShort(String type) {
        if (type.endsWith("FRAME")) return "F";
        if (type.endsWith("SUTTER")) return "S";
        if (type.endsWith("MULIYA")) return "M";
        if (type.endsWith("RT")) return "RT";
        if (type.endsWith("RP")) return "RP";
        return "?";
    }

    public static boolean isDomal(String type) { return type.startsWith("D_"); }

    private static String pfx(int system) {
        if (system == 0) return "Z_";
        if (system == 1) return "D_";
        if (CustomFormulaManager.activeSystems != null && system >= 0 && system < CustomFormulaManager.activeSystems.size()) {
            return CustomFormulaManager.activeSystems.get(system).name.toUpperCase() + "_";
        }
        return system == WindowItem.DOMAL ? "D_" : "Z_";
    }

    /* ---------------- data holders ---------------- */
    public static class Part {
        public String type;
        public double len;
        public int pcs;
        public String label;
        public String windowName;  // e.g. "W1"
        public String cutLabel;    // e.g. "1H", "1W", "1S-H", "1S-W", "1M", "1RP"

        public Part(String t, double l, int p, String lab) {
            type = t; len = l; pcs = p; label = lab;
        }
        public Part(String t, double l, int p, String lab, String winName, String cLabel) {
            type = t; len = l; pcs = p; label = lab;
            windowName = winName;
            cutLabel = cLabel;
        }
    }

    public static class RpOpt {
        public int qty;
        public double space;
        public RpOpt(int q, double s) { qty = q; space = s; }
    }

    public static class WinResult {
        public WindowItem src;
        public int system;
        public double H, W;
        public int q, nos;
        public double sutterH, sutterW;
        public double muliyaH; public int muliyaQ;
        public double glassH, glassW;
        public double rpLen, rpSpace; public int rpQty;
        public boolean rpAuto;
        public int rpAutoQty;
        public List<RpOpt> rpOpts = new ArrayList<>();
        public List<Part> parts = new ArrayList<>();
        public boolean ok;
        public boolean empty;
        public double frameLen, sutterLen;

        public String muliyaLabel() {
            if (CustomFormulaManager.activeSystems != null && system >= 0 && system < CustomFormulaManager.activeSystems.size()) {
                return CustomFormulaManager.activeSystems.get(system).muliyaLabel;
            }
            return system == WindowItem.DOMAL ? "RT" : "MULIYA";
        }
        public double glassSqft() {
            if (glassH <= 0 || glassW <= 0) return 0;
            return glassH * glassW / 144.0;
        }
        public int glassQty() { return q * nos; }
    }

    /* ---------------- RP options ---------------- */
    public static List<RpOpt> rpOptions(double M, Settings s) {
        List<RpOpt> out = new ArrayList<>();
        double eff = M - s.rpGap;
        if (eff <= 0) return out;
        for (int q = 1; q <= 100; q++) {
            double sp = eff / q;
            if (sp >= s.rpMin && sp <= s.rpMax) out.add(new RpOpt(q, sp));
        }
        return out;
    }

    public static int autoRpQty(double M, Settings s) {
        List<RpOpt> o = rpOptions(M, s);
        return o.isEmpty() ? 0 : o.get(0).qty;
    }

    public static double[] rpPoints(double M, Settings s, int qty) {
        if (qty <= 0) return new double[0];
        double step = (M - s.rpGap) / qty;
        int half = qty / 2;
        double[] p = new double[qty];
        for (int i = 1; i <= qty; i++)
            p[i - 1] = (i <= half) ? i * step : (i - 1) * step + s.rpGap;
        return p;
    }
    public static int rpGapIndex(int qty) { return qty / 2; }

    /* ---------------- one window ---------------- */
    public static WinResult calc(WindowItem it, Settings s) {
        WinResult r = new WinResult();
        r.src = it;
        r.system = it.system;
        r.H = s.toIn(it.h);
        r.W = s.toIn(it.w);
        r.q = Math.max(1, it.sutter);
        r.nos = Math.max(1, it.nos);
        r.empty = it.isEmpty();

        int sys = it.system;
        String P = pfx(sys);

        CustomFormulaManager.CustomSystem cs = null;
        if (CustomFormulaManager.activeSystems != null && sys >= 0 && sys < CustomFormulaManager.activeSystems.size()) {
            cs = CustomFormulaManager.activeSystems.get(sys);
        }

        if (cs != null) {
            try {
                r.sutterH = MathEvaluator.eval(cs.sutterH_formula, r.H, r.W, r.q, 0, 0);
            } catch (Exception e) {
                r.sutterH = r.H - 2.0;
            }
            try {
                r.sutterW = MathEvaluator.eval(cs.sutterW_formula, r.H, r.W, r.q, r.sutterH, 0);
            } catch (Exception e) {
                r.sutterW = r.W / r.q;
            }
            if (!cs.muliyaH_formula.isEmpty()) {
                try {
                    r.muliyaH = MathEvaluator.eval(cs.muliyaH_formula, r.H, r.W, r.q, r.sutterH, r.sutterW);
                    r.muliyaQ = r.q - 1;
                } catch (Exception e) {
                    r.muliyaH = 0;
                    r.muliyaQ = 0;
                }
            } else {
                r.muliyaH = 0;
                r.muliyaQ = 0;
            }
            try {
                r.glassH = MathEvaluator.eval(cs.glassH_formula, r.H, r.W, r.q, r.sutterH, r.sutterW);
            } catch (Exception e) {
                r.glassH = r.sutterH - 4.0;
            }
            try {
                r.glassW = MathEvaluator.eval(cs.glassW_formula, r.H, r.W, r.q, r.sutterH, r.sutterW);
            } catch (Exception e) {
                r.glassW = r.sutterW - 4.0;
            }
        } else {
            r.sutterH = r.H - s.dh(sys);
            if (sys == WindowItem.DOMAL) {
                double add = (r.q >= 3) ? (r.q - 2) * s.d_trackAdd : 0;
                r.sutterW = (r.W + add) / r.q;
            } else {
                r.sutterW = (r.W - (r.q + 1) * s.z_dw) / r.q;
            }
            r.muliyaH = r.H - s.dm(sys);
            r.muliyaQ = r.q - 1;
            r.glassH = r.sutterH - s.dg(sys);
            r.glassW = r.sutterW - s.dg(sys);
        }

        r.ok = !r.empty && r.sutterH > 0 && r.sutterW > 0;

        r.frameLen = (2 * r.H + 2 * r.W) * r.nos;
        r.sutterLen = r.ok ? (2 * r.sutterH + 2 * r.sutterW) * r.q * r.nos : 0;

        /* ---- RP ---- */
        r.rpOpts = rpOptions(r.muliyaH, s);
        r.rpAutoQty = r.rpOpts.isEmpty() ? 0 : r.rpOpts.get(0).qty;
        if (it.rpQty > 0) { r.rpQty = it.rpQty; r.rpAuto = false; }
        else { r.rpQty = r.rpAutoQty; r.rpAuto = true; }

        if (cs != null) {
            try {
                r.rpLen = MathEvaluator.eval(cs.rpLen_formula, r.H, r.W, r.q, r.sutterH, r.sutterW);
            } catch (Exception e) {
                r.rpLen = r.W;
            }
        } else {
            r.rpLen = r.W - s.rpDed(sys);
        }
        r.rpSpace = r.rpQty > 0 ? (r.muliyaH - s.rpGap) / r.rpQty : 0;

        if (r.empty) return r;

        // Build window number from name (e.g. "W1" -> "1")
        String wn = it.name.replaceAll("[^0-9]", "");
        if (wn.isEmpty()) wn = String.valueOf(it.id);

        r.parts.add(new Part(P + "FRAME", r.H, 2 * r.nos, wn + " F-H", it.name, wn + "H"));
        r.parts.add(new Part(P + "FRAME", r.W, 2 * r.nos, wn + " F-W", it.name, wn + "W"));
        if (r.ok) {
            r.parts.add(new Part(P + "SUTTER", r.sutterH, 2 * r.q * r.nos, wn + " S-H", it.name, wn + "S-H"));
            r.parts.add(new Part(P + "SUTTER", r.sutterW, 2 * r.q * r.nos, wn + " S-W", it.name, wn + "S-W"));
        }
        String midType = (cs != null) ? P + cs.muliyaLabel.toUpperCase() : ((sys == WindowItem.DOMAL) ? P + "RT" : P + "MULIYA");
        String midLabel = (cs != null) ? cs.muliyaLabel : r.muliyaLabel();
        if (r.muliyaQ > 0 && r.muliyaH > 0) {
            r.parts.add(new Part(midType, r.muliyaH, r.muliyaQ * r.nos,
                    wn + " " + midLabel, it.name, wn + midLabel.substring(0, 1)));
        }
        if (s.useRp && r.rpQty > 0 && r.rpLen > 0)
            r.parts.add(new Part(P + "RP", r.rpLen, r.rpQty * r.nos, wn + " RP", it.name, wn + "RP"));

        return r;
    }

    public static List<WinResult> calcAll(List<WindowItem> items, Settings s) {
        List<WinResult> out = new ArrayList<>();
        for (WindowItem it : items) out.add(calc(it, s));
        return out;
    }

    /* ================= BEST FIT DECREASING BIN PACKING ================= */
    public static class Piece {
        public double len;
        public String label;
        public String windowName;
        public String cutLabel;   // e.g. "1H", "2S-W", "3RP"
        public Piece(double l, String lab) { len = l; label = lab; }
        public Piece(double l, String lab, String winName, String cLabel) {
            len = l; label = lab; windowName = winName; cutLabel = cLabel;
        }
    }
    public static class Bin {
        public List<Piece> items = new ArrayList<>();
        public double used = 0;
        public boolean over = false;
        public double free(double stock) { return stock - used; }
    }

    /**
     * Best Fit Decreasing (BFD) — better than FFD.
     * Sort descending, then for each piece find the bin with
     * the LEAST remaining space that can still fit the piece.
     * This minimizes waste per bin.
     */
    public static List<Bin> pack(List<Part> parts, double stock, double kerf) {
        List<Piece> list = new ArrayList<>();
        for (Part p : parts)
            for (int i = 0; i < p.pcs; i++)
                list.add(new Piece(p.len, p.label, p.windowName, p.cutLabel));

        // Sort: longest first
        Collections.sort(list, (a, b) -> Double.compare(b.len, a.len));

        List<Bin> bins = new ArrayList<>();
        for (Piece pc : list) {
            // Oversized piece
            if (pc.len > stock) {
                Bin b = new Bin(); b.items.add(pc); b.used = pc.len; b.over = true;
                bins.add(b);
                continue;
            }

            // Best Fit: find bin with minimum remaining space after adding this piece
            Bin bestFit = null;
            double bestRemaining = Double.MAX_VALUE;

            for (Bin b : bins) {
                if (b.over) continue;
                double addLen = pc.len + (b.items.isEmpty() ? 0 : kerf);
                double remaining = stock - b.used - addLen;
                if (remaining >= -1e-9 && remaining < bestRemaining) {
                    bestRemaining = remaining;
                    bestFit = b;
                }
            }

            if (bestFit == null) {
                bestFit = new Bin();
                bins.add(bestFit);
            }

            double addLen = pc.len + (bestFit.items.isEmpty() ? 0 : kerf);
            bestFit.used += addLen;
            bestFit.items.add(pc);
        }
        return bins;
    }

    /* ================= summary ================= */
    public static class TypeSummary {
        public String type;
        public int pcs;
        public double totalLen;
        public List<Bin> bins;
        public int stockNeeded() { return bins == null ? 0 : bins.size(); }
        public double capacity(double stock) { return stockNeeded() * stock; }
        public double waste(double stock) { return capacity(stock) - totalLen; }
    }

    public static Map<String, TypeSummary> summarize(List<WinResult> res, Settings s) {
        Map<String, List<Part>> byType = new LinkedHashMap<>();
        for (String t : getTypes()) byType.put(t, new ArrayList<>());
        for (WinResult r : res)
            for (Part p : r.parts) {
                List<Part> l = byType.get(p.type);
                if (l != null) l.add(p);
            }

        Map<String, TypeSummary> out = new LinkedHashMap<>();
        for (String t : getTypes()) {
            List<Part> l = byType.get(t);
            if (l == null || l.isEmpty()) continue;
            TypeSummary ts = new TypeSummary();
            ts.type = t;
            for (Part p : l) { ts.pcs += p.pcs; ts.totalLen += p.len * p.pcs; }
            ts.bins = s.stock > 0 ? pack(l, s.stock, s.kerf) : new ArrayList<>();
            out.put(t, ts);
        }
        return out;
    }

    /** system-wise split of summary */
    public static Map<String, TypeSummary> filterSystem(Map<String, TypeSummary> all, int system) {
        Map<String, TypeSummary> out = new LinkedHashMap<>();
        String p = pfx(system);
        for (Map.Entry<String, TypeSummary> e : all.entrySet())
            if (e.getKey().startsWith(p)) out.put(e.getKey(), e.getValue());
        return out;
    }

    /* ================= grand ================= */
    public static class Grand {
        public int windows, nos, pcs, stockPipes;
        public double totalLen, capacity, waste, glassSqft;
        public int zedWindows, domalWindows;
        public int zedPipes, domalPipes;
        public double usePct() { return capacity > 0 ? totalLen / capacity * 100 : 0; }
        public double wastePct() { return capacity > 0 ? waste / capacity * 100 : 0; }
    }

    public static Grand grand(List<WinResult> res, Map<String, TypeSummary> sum, Settings s) {
        Grand g = new Grand();
        for (WinResult r : res) {
            if (r.empty) continue;
            g.windows++;
            g.nos += r.nos;
            if (r.system == WindowItem.DOMAL) g.domalWindows++; else g.zedWindows++;
            if (r.ok) g.glassSqft += r.glassSqft() * r.glassQty();
        }
        for (Map.Entry<String, TypeSummary> e : sum.entrySet()) {
            TypeSummary ts = e.getValue();
            g.pcs += ts.pcs;
            g.totalLen += ts.totalLen;
            g.stockPipes += ts.stockNeeded();
            if (isDomal(e.getKey())) g.domalPipes += ts.stockNeeded();
            else g.zedPipes += ts.stockNeeded();
        }
        g.capacity = g.stockPipes * s.stock;
        g.waste = g.capacity - g.totalLen;
        return g;
    }
}
