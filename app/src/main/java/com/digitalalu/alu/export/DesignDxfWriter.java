package com.digitalalu.alu.export;

import com.digitalalu.alu.design.DesignDocument;
import java.io.IOException;
import java.io.Writer;
import java.util.Locale;

/** ASCII DXF AC1009 LINE geometry. Explicit mm units; Y-up CAD coordinates. */
public final class DesignDxfWriter {
    private DesignDxfWriter() {}
    public static void write(DesignDocument doc, Writer out) throws IOException {
        out.write("0\nSECTION\n2\nHEADER\n9\n$ACADVER\n1\nAC1009\n9\n$INSUNITS\n70\n4\n9\n$MEASUREMENT\n70\n1\n0\nENDSEC\n");
        out.write("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLTYPE\n70\n1\n0\nLTYPE\n2\nCONTINUOUS\n70\n0\n3\nSolid line\n72\n65\n73\n0\n40\n0\n0\nENDTAB\n");
        out.write("0\nTABLE\n2\nLAYER\n70\n3\n");
        layer(out, "0", 7); layer(out, "FRAME", 7); layer(out, "OPENING", 4);
        out.write("0\nENDTAB\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n");
        for (DesignDocument.Rect r : doc.members()) rectangle(out, "FRAME", r, doc.heightMm);
        for (DesignDocument.Rect r : doc.openings()) rectangle(out, "OPENING", r, doc.heightMm);
        out.write("0\nENDSEC\n0\nEOF\n");
    }
    private static void layer(Writer w, String name, int color) throws IOException {
        w.write("0\nLAYER\n2\n" + name + "\n70\n0\n62\n" + color + "\n6\nCONTINUOUS\n");
    }
    private static void rectangle(Writer w, String layer, DesignDocument.Rect r, double h) throws IOException {
        double x = r.x, y = h - r.y, right = x + r.width, bottom = y - r.height;
        line(w, layer, x, y, right, y); line(w, layer, right, y, right, bottom);
        line(w, layer, right, bottom, x, bottom); line(w, layer, x, bottom, x, y);
    }
    private static void line(Writer w, String layer, double x, double y, double xx, double yy) throws IOException {
        w.write(String.format(Locale.US,
                "0\nLINE\n8\n%s\n10\n%.9f\n20\n%.9f\n30\n0\n11\n%.9f\n21\n%.9f\n31\n0\n",
                layer, x, y, xx, yy));
    }
}
