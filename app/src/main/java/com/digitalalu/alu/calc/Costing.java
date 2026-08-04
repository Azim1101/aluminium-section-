package com.digitalalu.alu.calc;

import com.digitalalu.alu.model.WindowItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns cut lists into money. v2: inch-based glass pricing */
public class Costing {

    public static class Line {
        public String label;
        public double qty;        // pcs or inches
        public double weight;     // kg (pipes only)
        public double amount;
        public int color;
        public Line(String l, double q, double w, double a, int c) {
            label = l; qty = q; weight = w; amount = a; color = c;
        }
    }

    /** cost of one window (x nos) */
    public static class WinCost {
        public List<Line> lines = new ArrayList<>();
        public double pipeAmt, glassAmt, extraAmt, total, weight;
    }

    public static WinCost window(Engine.WinResult r, PriceBook pb, Settings st) {
        WinCost c = new WinCost();
        if (r.empty) return c;

        /* ---- pipes ---- */
        Map<String, double[]> agg = new LinkedHashMap<>();
        for (Engine.Part p : r.parts) {
            double[] a = agg.get(p.type);
            if (a == null) { a = new double[2]; agg.put(p.type, a); }
            a[0] += p.pcs;
            a[1] += p.len * p.pcs;
        }
        for (Map.Entry<String, double[]> e : agg.entrySet()) {
            String type = e.getKey();
            double pcs = e.getValue()[0], len = e.getValue()[1];
            double w = pb.weight(type, len);
            double amt = w * pb.aluRate;
            c.weight += w;
            c.pipeAmt += amt;
            c.lines.add(new Line(Engine.shortName(type), pcs, w, amt, Engine.colorOf(type)));
        }

        /* ---- glass (inch-based: sqft = H*W/144) ---- */
        double sqft = r.glassSqft() * r.glassQty();
        if (sqft > 0) {
            double amt = sqft * pb.glassRate;
            c.glassAmt = amt;
            c.lines.add(new Line("GLASS", sqft, 0, amt, 0xFF0891B2));
        }

        /* ---- extras ---- */
        for (PriceBook.Extra x : pb.extras) {
            if (!x.appliesTo(r.system)) continue;
            double units;
            switch (x.basis) {
                case PriceBook.Extra.PER_SUTTER: units = r.q * r.nos; break;
                case PriceBook.Extra.PER_WINDOW: units = r.nos; break;
                case PriceBook.Extra.PER_INCH:
                    // per inch of frame perimeter
                    units = r.frameLen;
                    break;
                case PriceBook.Extra.PER_RP: units = r.rpQty * r.nos; break;
                default: units = 0;
            }
            if (units <= 0) continue;
            double amt = units * x.rate;
            c.extraAmt += amt;
            c.lines.add(new Line(x.name, units, 0, amt, 0xFF7C3AED));
        }

        c.total = c.pipeAmt + c.glassAmt + c.extraAmt;
        return c;
    }

    /** whole project */
    public static class Total {
        public double pipeAmt, glassAmt, extraAmt, total, weight;
        public double zedAmt, domalAmt;
        public Map<String, double[]> byType = new LinkedHashMap<>();
    }

    public static Total all(List<Engine.WinResult> res, PriceBook pb, Settings st) {
        Total t = new Total();
        for (Engine.WinResult r : res) {
            if (r.empty) continue;
            WinCost c = window(r, pb, st);
            t.pipeAmt += c.pipeAmt;
            t.glassAmt += c.glassAmt;
            t.extraAmt += c.extraAmt;
            t.weight += c.weight;
            if (r.system == WindowItem.DOMAL) t.domalAmt += c.total; else t.zedAmt += c.total;

            for (Engine.Part p : r.parts) {
                double[] a = t.byType.get(p.type);
                if (a == null) { a = new double[4]; t.byType.put(p.type, a); }
                double len = p.len * p.pcs;
                double w = pb.weight(p.type, len);
                a[0] += p.pcs; a[1] += len; a[2] += w; a[3] += w * pb.aluRate;
            }
        }
        t.total = t.pipeAmt + t.glassAmt + t.extraAmt;
        return t;
    }

    public static String rs(double v) {
        return "\u20B9 " + String.format(java.util.Locale.US, "%,.0f", v);
    }
    public static String rs2(double v) {
        return "\u20B9 " + String.format(java.util.Locale.US, "%,.2f", v);
    }
}
