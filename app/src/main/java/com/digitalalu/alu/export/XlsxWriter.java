package com.digitalalu.alu.export;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal XLSX writer — no external library.
 * Produces a real .xlsx (OOXML) that opens in Excel / Google Sheets / WPS.
 */
public class XlsxWriter {

    /* ---- style ids (styles.xml me define) ---- */
    public static final int S_NORMAL = 0;
    public static final int S_TITLE  = 1;
    public static final int S_HEAD   = 2;
    public static final int S_BOLD   = 3;
    public static final int S_NUM    = 4;   // 0.00
    public static final int S_TOTAL  = 5;

    private static class Cell {
        String text; Double num; int style;
        Cell(String t, Double n, int s) { text = t; num = n; style = s; }
    }
    private static class Row { List<Cell> cells = new ArrayList<>(); }
    private static class Sheet {
        String name; List<Row> rows = new ArrayList<>();
        double[] colWidths;
        Sheet(String n) { name = n; }
    }

    private final List<Sheet> sheets = new ArrayList<>();
    private Sheet cur;
    private Row curRow;

    public XlsxWriter sheet(String name) {
        cur = new Sheet(name);
        sheets.add(cur);
        return this;
    }
    public XlsxWriter widths(double... w) { if (cur != null) cur.colWidths = w; return this; }

    public XlsxWriter row() { curRow = new Row(); cur.rows.add(curRow); return this; }

    public XlsxWriter text(String v) { return text(v, S_NORMAL); }
    public XlsxWriter text(String v, int style) {
        curRow.cells.add(new Cell(v == null ? "" : v, null, style)); return this;
    }
    public XlsxWriter num(double v) { return num(v, S_NUM); }
    public XlsxWriter num(double v, int style) {
        curRow.cells.add(new Cell(null, v, style)); return this;
    }
    public XlsxWriter intg(int v) { curRow.cells.add(new Cell(null, (double) v, S_NORMAL)); return this; }
    public XlsxWriter blank() { curRow.cells.add(new Cell("", null, S_NORMAL)); return this; }

    /* ---------------- write ---------------- */
    public void write(File out) throws IOException {
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out));
        put(z, "[Content_Types].xml", contentTypes());
        put(z, "_rels/.rels",
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>");
        put(z, "xl/workbook.xml", workbook());
        put(z, "xl/_rels/workbook.xml.rels", workbookRels());
        put(z, "xl/styles.xml", styles());
        for (int i = 0; i < sheets.size(); i++)
            put(z, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(sheets.get(i)));
        z.close();
    }

    private void put(ZipOutputStream z, String path, String data) throws IOException {
        z.putNextEntry(new ZipEntry(path));
        Writer w = new OutputStreamWriter(z, "UTF-8");
        w.write(data);
        w.flush();
        z.closeEntry();
    }

    private String contentTypes() {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
         .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
         .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
         .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
         .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
         .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 0; i < sheets.size(); i++)
            b.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
             .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        return b.append("</Types>").toString();
    }

    private String workbook() {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
         .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
         .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < sheets.size(); i++)
            b.append("<sheet name=\"").append(esc(sheets.get(i).name))
             .append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        return b.append("</sheets></workbook>").toString();
    }

    private String workbookRels() {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
         .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheets.size(); i++)
            b.append("<Relationship Id=\"rId").append(i + 1)
             .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
             .append(i + 1).append(".xml\"/>");
        b.append("<Relationship Id=\"rId").append(sheets.size() + 1)
         .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return b.append("</Relationships>").toString();
    }

    private String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"0.00\"/></numFmts>" +
            "<fonts count=\"4\">" +
              "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
              "<font><b/><sz val=\"16\"/><color rgb=\"FF1D4ED8\"/><name val=\"Calibri\"/></font>" +
              "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>" +
              "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "</fonts>" +
            "<fills count=\"4\">" +
              "<fill><patternFill patternType=\"none\"/></fill>" +
              "<fill><patternFill patternType=\"gray125\"/></fill>" +
              "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF2563EB\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
              "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFEF3C7\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "</fills>" +
            "<borders count=\"2\">" +
              "<border><left/><right/><top/><bottom/><diagonal/></border>" +
              "<border><left style=\"thin\"><color rgb=\"FFD1D5DB\"/></left>" +
                "<right style=\"thin\"><color rgb=\"FFD1D5DB\"/></right>" +
                "<top style=\"thin\"><color rgb=\"FFD1D5DB\"/></top>" +
                "<bottom style=\"thin\"><color rgb=\"FFD1D5DB\"/></bottom><diagonal/></border>" +
            "</borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"6\">" +
              "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" applyBorder=\"1\"/>" +
              "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" applyFont=\"1\"/>" +
              "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>" +
              "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"1\" applyFont=\"1\" applyBorder=\"1\"/>" +
              "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"1\" applyNumberFormat=\"1\" applyBorder=\"1\"/>" +
              "<xf numFmtId=\"164\" fontId=\"3\" fillId=\"3\" borderId=\"1\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>" +
            "</cellXfs></styleSheet>";
    }

    private String sheetXml(Sheet s) {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
         .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        if (s.colWidths != null && s.colWidths.length > 0) {
            b.append("<cols>");
            for (int i = 0; i < s.colWidths.length; i++)
                b.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                 .append("\" width=\"").append(s.colWidths[i]).append("\" customWidth=\"1\"/>");
            b.append("</cols>");
        }
        b.append("<sheetData>");
        for (int r = 0; r < s.rows.size(); r++) {
            Row row = s.rows.get(r);
            b.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < row.cells.size(); c++) {
                Cell cell = row.cells.get(c);
                String ref = colName(c) + (r + 1);
                if (cell.num != null) {
                    b.append("<c r=\"").append(ref).append("\" s=\"").append(cell.style)
                     .append("\"><v>").append(trim(cell.num)).append("</v></c>");
                } else {
                    b.append("<c r=\"").append(ref).append("\" s=\"").append(cell.style)
                     .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                     .append(esc(cell.text)).append("</t></is></c>");
                }
            }
            b.append("</row>");
        }
        return b.append("</sheetData></worksheet>").toString();
    }

    private static String trim(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.valueOf(Math.round(d * 10000.0) / 10000.0);
    }

    private static String colName(int i) {
        StringBuilder s = new StringBuilder();
        i++;
        while (i > 0) { int m = (i - 1) % 26; s.insert(0, (char) ('A' + m)); i = (i - 1) / 26; }
        return s.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
