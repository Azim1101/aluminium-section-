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
 */
public class Engine {

    /* ---------------- pipe types ---------------- */
    public static final String[] TYPES = {
            "Z_FRAME", "Z_SUTTER", "Z_MULIYA", "Z_RP",
            "D_FRAME", "D_SUTTER", "D_RT", "D_RP"
    };
    public static final String[] TYPE_NAMES = {
            "ZED FRAME", "ZED SUTTER", "ZED MULIYA", "ZED RP GRILL",
            "DOMAL FRAME", "DOMAL SUTTER", "DOMAL RT", "DOMAL RP GRILL"
    };
    public static final int[] TYPE_COLORS = {
            0xFF3B82F6, 0xFF22C55E, 0xFF8B5CF6, 0xFFEC4899,
            0xFF0EA5E9, 0xFF14B8A6, 0xFFF59E0B, 0xFFF43F5E
    };

    public static int colorOf(String type) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(type)) return TYPE_COLORS[i];
        return 0xFF64748B;
    }
    public static String nameOf(String type) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(type)) return TYPE_NAMES[i];
        return type;
    }
    /** short label without system prefix */
    public static String shortName(String type) {
        String n = nameOf(type);
        if (n.startsWith("ZED ")) return n.substring(4);
        if (n.startsWith("DOMAL ")) return n.substring(6);
        return n;
    }
    public static boolean isDomal(String type) { return type.startsWith("D_"); }

    private static String pfx(int system) {
        return system == WindowItem.DOMAL ? "D_" : "Z_";
    }

    /* ---------------- data holders ---------------- */
    public static class Part {
        public String type;
        public double len;
        public int pcs;
        public String label;
        public Part(String t, double l, int p, String lab) {
            type = t; len = l; pcs = p; label = lab;
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
        public double muliyaH; public int muliyaQ;   // DOMAL: RT
        public double glassH, glassW;
        public double rpLen, rpSpace; public int rpQty;
        public boolean rpAuto;                       // auto suggested?
        public int rpAutoQty;                        // algorithm ka suggestion
        public List<RpOpt> rpOpts = new ArrayList<>();
        public List<Part> parts = new ArrayList<>();
        public boolean ok;
        public boolean empty;
        public double frameLen, sutterLen;

        public String muliyaLabel() {
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

    /** algorithm suggestion — first (widest spacing) valid option */
    public static int autoRpQty(double M, Settings s) {
        List<RpOpt> o = rpOptions(M, s);
        return o.isEmpty() ? 0 : o.get(0).qty;
    }

    /** marking points with middle gap */
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

        r.sutterH = r.H - s.dh(sys);
        if (sys == WindowItem.DOMAL) {
            /* DOMAL:
             *   2 track ->  W / 2
             *   3 track -> (W + 2) / 3
             *   n track -> (W + (n-2)*2) / n     (extra overlap per extra track)
             */
            double add = (r.q >= 3) ? (r.q - 2) * s.d_trackAdd : 0;
            r.sutterW = (r.W + add) / r.q;
        } else {
            /* ZED: (W - (q+1) * 30mm) / q */
            r.sutterW = (r.W - (r.q + 1) * s.z_dw) / r.q;
        }
        r.muliyaH = r.H - s.dm(sys);
        r.muliyaQ = r.q - 1;
        r.glassH = r.sutterH - s.dg(sys);
        r.glassW = r.sutterW - s.dg(sys);
        r.ok = !r.empty && r.sutterH > 0 && r.sutterW > 0;

        r.frameLen = (2 * r.H + 2 * r.W) * r.nos;
        r.sutterLen = r.ok ? (2 * r.sutterH + 2 * r.sutterW) * r.q * r.nos : 0;

        /* ---- RP ---- */
        r.rpOpts = rpOptions(r.muliyaH, s);
        r.rpAutoQty = r.rpOpts.isEmpty() ? 0 : r.rpOpts.get(0).qty;
        if (it.rpQty > 0) { r.rpQty = it.rpQty; r.rpAuto = false; }
        else { r.rpQty = r.rpAutoQty; r.rpAuto = true; }
        r.rpLen = r.W - s.rpDed(sys);
        r.rpSpace = r.rpQty > 0 ? (r.muliyaH - s.rpGap) / r.rpQty : 0;

        if (r.empty) return r;

        String n = it.name;
        r.parts.add(new Part(P + "FRAME", r.H, 2 * r.nos, n + " F-H"));
        r.parts.add(new Part(P + "FRAME", r.W, 2 * r.nos, n + " F-W"));
        if (r.ok) {
            r.parts.add(new Part(P + "SUTTER", r.sutterH, 2 * r.q * r.nos, n + " S-H"));
            r.parts.add(new Part(P + "SUTTER", r.sutterW, 2 * r.q * r.nos, n + " S-W"));
        }
        String midType = (sys == WindowItem.DOMAL) ? P + "RT" : P + "MULIYA";
        if (r.muliyaQ > 0 && r.muliyaH > 0)
            r.parts.add(new Part(midType, r.muliyaH, r.muliyaQ * r.nos,
                    n + " " + r.muliyaLabel()));
        if (s.useRp && r.rpQty > 0 && r.rpLen > 0)
            r.parts.add(new Part(P + "RP", r.rpLen, r.rpQty * r.nos, n + " RP"));

        return r;
    }

    public static List<WinResult> calcAll(List<WindowItem> items, Settings s) {
        List<WinResult> out = new ArrayList<>();
        for (WindowItem it : items) out.add(calc(it, s));
        return out;
    }

    /* ================= BIN PACKING ================= */
    public static class Piece {
        public double len; public String label;
        public Piece(double l, String lab) { len = l; label = lab; }
    }
    public static class Bin {
        public List<Piece> items = new ArrayList<>();
        public double used = 0;
        public boolean over = false;
        public double free(double stock) { return stock - used; }
    }

    public static List<Bin> pack(List<Part> parts, double stock, double kerf) {
        List<Piece> list = new ArrayList<>();
        for (Part p : parts)
            for (int i = 0; i < p.pcs; i++) list.add(new Piece(p.len, p.label));
        Collections.sort(list, (a, b) -> Double.compare(b.len, a.len));

        List<Bin> bins = new ArrayList<>();
        for (Piece pc : list) {
            if (pc.len > stock) {
                Bin b = new Bin(); b.items.add(pc); b.used = pc.len; b.over = true;
                bins.add(b); continue;
            }
            Bin fit = null;
            for (Bin b : bins) {
                if (b.over) continue;
                double add = pc.len + (b.items.isEmpty() ? 0 : kerf);
                if (b.used + add <= stock + 1e-9) { fit = b; break; }
            }
            if (fit == null) { fit = new Bin(); bins.add(fit); }
            fit.used += pc.len + (fit.items.isEmpty() ? 0 : kerf);
            fit.items.add(pc);
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
        for (String t : TYPES) byType.put(t, new ArrayList<>());
        for (WinResult r : res)
            for (Part p : r.parts) {
                List<Part> l = byType.get(p.type);
                if (l != null) l.add(p);
            }

        Map<String, TypeSummary> out = new LinkedHashMap<>();
        for (String t : TYPES) {
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
