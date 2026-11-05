package com.digitalalu.alu.design;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.StringWriter;
import java.util.Locale;
import com.digitalalu.alu.export.DesignDxfWriter;

public class DesignDocumentTest {
    private DesignDocument design(double w, double h, double f, double m, int n) {
        return new DesignDocument(DesignDocument.Kind.WINDOW, w, h, f, m, n);
    }
    @Test public void equalBaysCloseDimensionChain() {
        DesignDocument d = design(1200, 1500, 50, 40, 3);
        assertEquals(340, d.clearBayWidthMm(), 1e-9);
        assertEquals(1200, 2*d.frameMm + d.bays*d.clearBayWidthMm() + (d.bays-1)*d.mullionMm, 1e-9);
        assertEquals(1.428, d.clearOpeningAreaM2(), 1e-9);
        DesignDocument.Rect last = d.openings().get(2);
        assertEquals(1150, last.x + last.width, 1e-9);
    }
    @Test public void faceAndOpeningAreasConserveArea() {
        for (int n = 1; n <= 12; n++) {
            DesignDocument d = design(5000, 2400, 65, 45, n);
            double area = 0;
            for (DesignDocument.Rect r : d.members()) area += r.width*r.height;
            for (DesignDocument.Rect r : d.openings()) area += r.width*r.height;
            assertEquals(d.widthMm*d.heightMm, area, 1e-7);
            for (DesignDocument.Rect a : d.openings()) for (DesignDocument.Rect b : d.members()) {
                double overlapW = Math.min(a.x+a.width,b.x+b.width)-Math.max(a.x,b.x);
                double overlapH = Math.min(a.y+a.height,b.y+b.height)-Math.max(a.y,b.y);
                assertTrue(overlapW <= 1e-8 || overlapH <= 1e-8);
            }
        }
    }
    @Test public void inchConversionIsReversible() {
        assertEquals(25.4, DesignDocument.Unit.INCH.toMm(1), 0);
        assertEquals(48, DesignDocument.Unit.INCH.fromMm(1219.2), 1e-12);
        double mm = 1234.56789;
        for (int i = 0; i < 100; i++) mm = DesignDocument.Unit.INCH.toMm(DesignDocument.Unit.INCH.fromMm(mm));
        assertEquals(1234.56789, mm, 1e-9);
    }
    @Test public void invalidDimensionsRejected() {
        for (double v : new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,20001}) {
            assertThrows(IllegalArgumentException.class, () -> design(v,1200,50,40,2));
            assertThrows(IllegalArgumentException.class, () -> design(1200,v,50,40,2));
        }
        assertThrows(IllegalArgumentException.class, () -> design(100,100,50,40,1));
        assertThrows(IllegalArgumentException.class, () -> design(100,100,10,100,2));
        assertThrows(IllegalArgumentException.class, () -> design(100,100,10,10,13));
        assertThrows(IllegalArgumentException.class, () -> design(100,100,10,10,0));
        assertThrows(IllegalArgumentException.class, () -> design(100,100,Double.NaN,10,1));
        assertThrows(IllegalArgumentException.class, () -> design(100,100,10,Double.NaN,1));
        assertThrows(IllegalArgumentException.class, () -> new DesignDocument(null,100,100,10,10,1));
    }
    @Test public void minimumClearanceAcceptedAndBelowRejected() {
        assertEquals(1, design(21,21,10,10,1).clearBayWidthMm(), 0);
        assertThrows(IllegalArgumentException.class, () -> design(20.999,21,10,10,1));
    }
    @Test public void geometryIsImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> design(1200,1200,50,40,2).members().clear());
    }
    @Test public void jsonRoundTripAndLegacyIsolation() throws Exception {
        DesignDocument original = new DesignDocument(DesignDocument.Kind.DOOR,900,2100,50,40,1);
        DesignDocument copy = DesignJson.decode(DesignJson.encode(original));
        assertEquals(original.kind, copy.kind); assertEquals(original.widthMm, copy.widthMm, 0);
        assertEquals(original.heightMm, copy.heightMm, 0); assertEquals(original.bays, copy.bays);
        assertThrows(Exception.class, () -> DesignJson.decode("{\"id\":1,\"name\":\"legacy customer\"}"));
        String valid = DesignJson.encode(original);
        assertThrows(Exception.class, () -> DesignJson.decode(valid.replace("\"version\": 1", "\"version\": 2")));
        assertThrows(Exception.class, () -> DesignJson.decode(valid.replace("\"bays\": 1", "\"bays\": 1.5")));
        assertThrows(Exception.class, () -> DesignJson.decode(valid.replace("\"mm\"", "\"in\"")));
        assertThrows(Exception.class, () -> DesignJson.decode(new String(new char[16385])));
    }
    @Test public void dxfIsUnitExplicitAndLocaleIndependent() throws Exception {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            DesignDocument d = design(1200.5,1500,50,40,2);
            StringWriter out = new StringWriter(); DesignDxfWriter.write(d,out); String text = out.toString();
            assertTrue(text.contains("$INSUNITS\n70\n4\n"));
            assertTrue(text.contains("1200.500000000")); assertFalse(text.contains(","));
            assertTrue(text.endsWith("0\nEOF\n"));
            assertEquals((d.members().size()+d.openings().size())*4, text.split("0\nLINE\n",-1).length-1);
            String[] lines = text.split("\n"); assertEquals(0,lines.length%2);
            for (int i=0;i<lines.length;i+=2) {
                int code = Integer.parseInt(lines[i]);
                if (code==10 || code==11 || code==20 || code==21) {
                    double v=Double.parseDouble(lines[i+1]);
                    assertTrue(Double.isFinite(v)); assertTrue(v>=0);
                    assertTrue(v <= (code==10 || code==11 ? d.widthMm : d.heightMm));
                }
            }
        } finally { Locale.setDefault(before); }
    }
}
