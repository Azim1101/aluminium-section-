package com.digitalalu.alu.design;

import org.json.JSONObject;

/** Explicit separate document; never writes legacy Customer or WindowItem JSON. */
public final class DesignJson {
    private DesignJson() {}
    public static String encode(DesignDocument d) throws Exception {
        return new JSONObject().put("format", "alu-elevation").put("version", 1)
                .put("units", "mm").put("kind", d.kind.name()).put("width", d.widthMm)
                .put("height", d.heightMm).put("frame", d.frameMm)
                .put("mullion", d.mullionMm).put("bays", d.bays).toString(2);
    }
    public static DesignDocument decode(String text) throws Exception {
        if (text.length() > 16384) throw new IllegalArgumentException("Design file too large");
        JSONObject o = new JSONObject(text);
        if (!"alu-elevation".equals(o.getString("format")) || o.getDouble("version") != 1
                || !"mm".equals(o.getString("units")))
            throw new IllegalArgumentException("Unsupported elevation format/version/units");
        double count = o.getDouble("bays");
        if (count != Math.rint(count) || count < 1 || count > 12)
            throw new IllegalArgumentException("Bays must be an integer from 1 to 12");
        return new DesignDocument(DesignDocument.Kind.valueOf(o.getString("kind")),
                o.getDouble("width"), o.getDouble("height"), o.getDouble("frame"),
                o.getDouble("mullion"), (int) count);
    }
}
