package com.digitalalu.alu.export;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.digitalalu.alu.calc.Engine;
import com.digitalalu.alu.calc.Settings;
import com.digitalalu.alu.model.WindowItem;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Excel export + WhatsApp share + Cutting image share */
public class Exporter {

    /* ================= WHATSAPP TEXT ================= */
    public static String buildText(List<Engine.WinResult> res, Settings s,
                                   Map<String, Engine.TypeSummary> sum, Engine.Grand g) {
        StringBuilder b = new StringBuilder();
        String date = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date());

        b.append("*").append(s.bizHeader()).append("*\n");
        b.append("_Window Estimate_  ").append(date).append("\n");
        b.append("---------------------------\n\n");

        for (Engine.WinResult r : res) {
            if (r.empty) continue;
            b.append("*").append(WindowItem.systemName(r.system)).append(" \u00b7 ")
             .append(r.src.name).append("*  ")
             .append(s.fmt(r.H)).append(" x ").append(s.fmt(r.W))
             .append(s.unit()).append("  |  ").append(r.q).append(" sutter");
            if (r.nos > 1) b.append("  x").append(r.nos).append(" nos");
            b.append("\n");
            if (!r.ok) { b.append("  _size too small_\n\n"); continue; }
            b.append("  Sutter : ").append(s.fmt(r.sutterH)).append(" x ").append(s.fmt(r.sutterW)).append("\n");
            if (r.muliyaQ > 0)
                b.append("  ").append(r.muliyaLabel()).append(r.muliyaLabel().length() < 6 ? "     " : " ")
                 .append(": ").append(s.fmt(r.muliyaH)).append("  x").append(r.muliyaQ).append(" pcs\n");
            if (r.rpQty > 0)
                b.append("  RP     : ").append(r.rpQty).append(" pcs @ ").append(s.fmt(r.rpSpace))
                 .append(" | len ").append(s.fmt(r.rpLen)).append("\n");
            b.append("  Glass  : ").append(s.fmt(r.glassH)).append(" x ").append(s.fmt(r.glassW)).append("\n\n");
        }

        Map<String, Engine.TypeSummary> zed = Engine.filterSystem(sum, WindowItem.ZED);
        Map<String, Engine.TypeSummary> dom = Engine.filterSystem(sum, WindowItem.DOMAL);

        if (!zed.isEmpty()) {
            b.append("*ZED PIPE*\n");
            for (Engine.TypeSummary ts : zed.values())
                b.append("- ").append(Engine.shortName(ts.type)).append(" : ")
                 .append(ts.pcs).append(" pcs, ").append(s.fmt(ts.totalLen)).append(s.unit())
                 .append("  =>  *").append(ts.stockNeeded()).append(" pipe*\n");
            b.append("   Total ZED pipe : *").append(g.zedPipes).append("*\n\n");
        }
        if (!dom.isEmpty()) {
            b.append("*DOMAL PIPE*\n");
            for (Engine.TypeSummary ts : dom.values())
                b.append("- ").append(Engine.shortName(ts.type)).append(" : ")
                 .append(ts.pcs).append(" pcs, ").append(s.fmt(ts.totalLen)).append(s.unit())
                 .append("  =>  *").append(ts.stockNeeded()).append(" pipe*\n");
            b.append("   Total DOMAL pipe : *").append(g.domalPipes).append("*\n\n");
        }
        b.append("Windows      : ").append(g.windows)
         .append("  (ZED ").append(g.zedWindows).append(" | DOMAL ").append(g.domalWindows).append(")\n");
        b.append("Total pieces : ").append(g.pcs).append("\n");
        b.append("Total length : ").append(s.fmt(g.totalLen)).append(s.unit()).append("\n");
        b.append("*Stock pipe  : ").append(g.stockPipes).append(" pcs*  (")
         .append(s.fmt(s.stock)).append(s.unit()).append(" each)\n");
        b.append("Waste        : ").append(s.fmt(g.waste)).append(s.unit())
         .append(" (").append(String.format(Locale.US, "%.1f", g.wastePct())).append("%)\n");
        if (g.glassSqft > 0)
            b.append("Glass total  : ").append(String.format(Locale.US, "%.2f", g.glassSqft)).append(" sq.ft\n");

        String footer = s.bizFooter();
        if (!footer.isEmpty()) b.append("\n_").append(footer).append("_");
        return b.toString();
    }

    public static void shareWhatsApp(Context ctx, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        i.setPackage("com.whatsapp");
        try {
            ctx.startActivity(i);
        } catch (Exception e) {
            try {
                i.setPackage("com.whatsapp.w4b");
                ctx.startActivity(i);
            } catch (Exception e2) {
                Intent g = new Intent(Intent.ACTION_SEND);
                g.setType("text/plain");
                g.putExtra(Intent.EXTRA_TEXT, text);
                ctx.startActivity(Intent.createChooser(g, "Share estimate"));
            }
        }
    }

    /* ================= CUTTING IMAGE ================= */
    /**
     * Generate cutting plan image for a specific pipe type (Frame/Sutter/Muliya/RP).
     * Returns the saved image file, or null on error.
     */
    public static File exportCuttingImage(Context ctx, String pipeType,
                                          Engine.TypeSummary ts, Settings s) {
        if (ts == null || ts.bins == null || ts.bins.isEmpty()) return null;
        try {
            int W = 1080;
            int pad = 40;
            int barH = 56;
            int labelH = 24;
            int headerH = 120;
            int footerH = 80;
            int gap = 16;
            int pipeCount = ts.bins.size();
            int H = headerH + (pipeCount * (labelH + barH + gap)) + footerH + pad * 2;

            Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.drawColor(0xFFF8FAFC);

            Paint pTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
            pTitle.setColor(0xFF1E293B);
            pTitle.setTextSize(48);
            pTitle.setFakeBoldText(true);

            Paint pSub = new Paint(Paint.ANTI_ALIAS_FLAG);
            pSub.setColor(0xFF64748B);
            pSub.setTextSize(28);

            Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
            pLabel.setColor(0xFF475569);
            pLabel.setTextSize(26);
            pLabel.setFakeBoldText(true);

            Paint pPipe = new Paint(Paint.ANTI_ALIAS_FLAG);
            int pipeColor = Engine.colorOf(pipeType);
            pPipe.setColor(pipeColor);

            Paint pWaste = new Paint(Paint.ANTI_ALIAS_FLAG);
            pWaste.setColor(0xFFE2E8F0);

            Paint pWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
            pWhite.setColor(Color.WHITE);
            pWhite.setTextSize(22);
            pWhite.setTextAlign(Paint.Align.CENTER);
            pWhite.setFakeBoldText(true);

            Paint pDark = new Paint(Paint.ANTI_ALIAS_FLAG);
            pDark.setColor(0xFF64748B);
            pDark.setTextSize(20);
            pDark.setTextAlign(Paint.Align.CENTER);

            Paint pFooter = new Paint(Paint.ANTI_ALIAS_FLAG);
            pFooter.setColor(0xFF94A3B8);
            pFooter.setTextSize(24);
            pFooter.setTextAlign(Paint.Align.CENTER);

            // Header
            String title = Engine.nameOf(pipeType) + "  \u2014  " + pipeCount + " pipes";
            c.drawText(title, pad, pad + 50, pTitle);
            String sub = ts.pcs + " pcs  \u2022  " + s.fmtU(ts.totalLen)
                    + "  \u2022  stock " + s.fmtU(s.stock) + "  \u2022  waste " + s.fmtU(ts.waste(s.stock));
            c.drawText(sub, pad, pad + 90, pSub);

            float barLeft = pad;
            float barW = W - pad * 2;
            float y = pad + headerH;
            float rad = 12;
            RectF r = new RectF();

            for (int i = 0; i < ts.bins.size(); i++) {
                Engine.Bin bin = ts.bins.get(i);

                // Label
                double wasteVal = Math.max(0, bin.free(s.stock));
                String lab = "Pipe #" + (i + 1) + "   used " + s.fmt(bin.used) + " / " + s.fmt(s.stock)
                        + "   left " + s.fmt(wasteVal);
                c.drawText(lab, pad, y + labelH - 4, pLabel);
                y += labelH;

                // Waste background
                r.set(barLeft, y, barLeft + barW, y + barH);
                c.drawRoundRect(r, rad, rad, pWaste);

                // Pieces
                float x = barLeft;
                for (int j = 0; j < bin.items.size(); j++) {
                    Engine.Piece pc = bin.items.get(j);
                    float pw = (float) (pc.len / s.stock) * barW;
                    pw = Math.max(pw, 8);

                    r.set(x, y, x + pw, y + barH);
                    c.drawRoundRect(r, j == 0 ? rad : 0, j == 0 ? rad : 0, pPipe);
                    if (j == bin.items.size() - 1) {
                        r.set(x, y, x + pw, y + barH);
                        c.drawRoundRect(r, 0, 0, rad, rad, pPipe);
                    }

                    // Separator
                    if (j > 0) {
                        Paint sep = new Paint();
                        sep.setColor(0x44FFFFFF);
                        sep.setStrokeWidth(2);
                        c.drawLine(x, y, x, y + barH, sep);
                    }

                    // Cut label on top
                    if (pc.cutLabel != null && pw > 40) {
                        pWhite.setTextSize(16);
                        c.drawText(pc.cutLabel, x + pw / 2f, y - 2, pWhite);
                        pWhite.setTextSize(22);
                    }

                    // Size inside bar
                    if (pw > 60) {
                        c.drawText(s.fmt(pc.len), x + pw / 2f, y + barH / 2f + 8, pWhite);
                    }

                    x += pw;
                }

                // Waste text
                float remaining = barLeft + barW - x;
                if (remaining > 50) {
                    c.drawText(s.fmt(wasteVal), x + remaining / 2f, y + barH / 2f + 8, pDark);
                }

                y += barH + gap;
            }

            // Footer
            String footer = s.bizFooter();
            if (!footer.isEmpty()) {
                c.drawText(footer, W / 2f, H - pad - 10, pFooter);
            }

            // Save
            File dir = new File(ctx.getExternalFilesDir(null), "cutting_images");
            if (!dir.exists()) dir.mkdirs();
            String fname = "Cutting_" + Engine.shortName(pipeType).replace(" ", "_") + "_"
                    + new SimpleDateFormat("ddMMyy_HHmmss", Locale.US).format(new Date()) + ".png";
            File out = new File(dir, fname);
            FileOutputStream fos = new FileOutputStream(out);
            bmp.compress(Bitmap.CompressFormat.PNG, 95, fos);
            fos.close();
            bmp.recycle();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** Share a cutting image */
    public static void shareCuttingImage(Context ctx, File imgFile) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx,
                    ctx.getPackageName() + ".fileprovider", imgFile);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/png");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_SUBJECT, imgFile.getName());
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(i, "Share Cutting Plan"));
        } catch (Exception e) {
            Toast.makeText(ctx, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /* ================= EXCEL ================= */
    public static File exportExcel(Context ctx, List<WindowItem> items,
                                   List<Engine.WinResult> res, Settings s,
                                   Map<String, Engine.TypeSummary> sum,
                                   Engine.Grand g) throws Exception {

        XlsxWriter x = new XlsxWriter();
        String date = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).format(new Date());

        x.sheet("Summary").widths(26, 16, 14, 16, 12);
        x.row().text(s.bizHeader() + " - WINDOW ESTIMATE", XlsxWriter.S_TITLE);
        x.row().text("Date: " + date);
        x.row().text("Unit: " + (s.mm ? "MM" : "INCH") + "   |   Stock pipe: " + s.fmt(s.stock) + s.unit());
        x.row();

        x.row().text("PIPE TYPE", XlsxWriter.S_HEAD).text("PCS", XlsxWriter.S_HEAD)
               .text("TOTAL LENGTH", XlsxWriter.S_HEAD).text("STOCK PIPE", XlsxWriter.S_HEAD)
               .text("WASTE", XlsxWriter.S_HEAD);

        Map<String, Engine.TypeSummary> zedS = Engine.filterSystem(sum, WindowItem.ZED);
        Map<String, Engine.TypeSummary> domS = Engine.filterSystem(sum, WindowItem.DOMAL);

        if (!zedS.isEmpty()) {
            x.row().text("ZED", XlsxWriter.S_BOLD);
            for (Engine.TypeSummary ts : zedS.values())
                x.row().text("   " + Engine.shortName(ts.type)).intg(ts.pcs)
                       .num(s.out(ts.totalLen)).intg(ts.stockNeeded())
                       .num(s.out(ts.waste(s.stock)));
            x.row().text("   ZED TOTAL", XlsxWriter.S_BOLD).blank().blank()
                   .intg(g.zedPipes).blank();
        }
        if (!domS.isEmpty()) {
            x.row().text("DOMAL", XlsxWriter.S_BOLD);
            for (Engine.TypeSummary ts : domS.values())
                x.row().text("   " + Engine.shortName(ts.type)).intg(ts.pcs)
                       .num(s.out(ts.totalLen)).intg(ts.stockNeeded())
                       .num(s.out(ts.waste(s.stock)));
            x.row().text("   DOMAL TOTAL", XlsxWriter.S_BOLD).blank().blank()
                   .intg(g.domalPipes).blank();
        }
        x.row().text("TOTAL", XlsxWriter.S_BOLD).intg(g.pcs)
               .num(s.out(g.totalLen), XlsxWriter.S_TOTAL).intg(g.stockPipes)
               .num(s.out(g.waste), XlsxWriter.S_TOTAL);
        x.row();
        x.row().text("Total windows", XlsxWriter.S_BOLD).intg(g.windows);
        x.row().text("Total nos", XlsxWriter.S_BOLD).intg(g.nos);
        x.row().text("Material used %", XlsxWriter.S_BOLD).num(g.usePct());
        x.row().text("Glass total (sq.ft)", XlsxWriter.S_BOLD).num(g.glassSqft);

        /* SHEET 2: WINDOWS */
        x.sheet("Windows").widths(10, 12, 10, 10, 8, 8, 14, 14, 12, 8, 12, 12, 14, 14);
        x.row().text("SYSTEM", XlsxWriter.S_HEAD).text("WINDOW", XlsxWriter.S_HEAD).text("HEIGHT", XlsxWriter.S_HEAD)
               .text("WIDTH", XlsxWriter.S_HEAD).text("SUTTER", XlsxWriter.S_HEAD)
               .text("NOS", XlsxWriter.S_HEAD).text("SUTTER H", XlsxWriter.S_HEAD)
               .text("SUTTER W", XlsxWriter.S_HEAD).text("MID PIPE", XlsxWriter.S_HEAD)
               .text("M QTY", XlsxWriter.S_HEAD).text("RP QTY", XlsxWriter.S_HEAD)
               .text("RP SPACE", XlsxWriter.S_HEAD).text("RP LENGTH", XlsxWriter.S_HEAD)
               .text("GLASS H x W", XlsxWriter.S_HEAD);
        for (Engine.WinResult r : res) {
            if (r.empty) continue;
            x.row().text(WindowItem.systemName(r.system))
                   .text(r.src.name).num(s.out(r.H)).num(s.out(r.W))
                   .intg(r.q).intg(r.nos);
            if (r.ok) {
                x.num(s.out(r.sutterH)).num(s.out(r.sutterW))
                 .num(s.out(r.muliyaH)).intg(r.muliyaQ)
                 .intg(r.rpQty).num(s.out(r.rpSpace)).num(s.out(r.rpLen))
                 .text(s.fmt(r.glassH) + " x " + s.fmt(r.glassW));
            } else {
                x.text("SIZE ERROR");
            }
        }

        /* SHEET 3: CUTTING LIST */
        x.sheet("Cutting List").widths(12, 16, 14, 10, 16);
        x.row().text("WINDOW", XlsxWriter.S_HEAD).text("PIPE TYPE", XlsxWriter.S_HEAD)
               .text("CUT SIZE", XlsxWriter.S_HEAD).text("PCS", XlsxWriter.S_HEAD)
               .text("TOTAL LENGTH", XlsxWriter.S_HEAD);
        for (Engine.WinResult r : res) {
            if (r.empty) continue;
            for (Engine.Part p : r.parts) {
                x.row().text(r.src.name).text(Engine.nameOf(p.type))
                       .num(s.out(p.len)).intg(p.pcs).num(s.out(p.len * p.pcs));
            }
        }

        /* SHEET 4: PIPE CUTTING PLAN */
        x.sheet("Cutting Plan").widths(16, 10, 40, 14, 12);
        x.row().text("PIPE TYPE", XlsxWriter.S_HEAD).text("PIPE #", XlsxWriter.S_HEAD)
               .text("CUT PIECES", XlsxWriter.S_HEAD).text("USED", XlsxWriter.S_HEAD)
               .text("LEFT", XlsxWriter.S_HEAD);
        for (Engine.TypeSummary ts : sum.values()) {
            if (ts.bins == null) continue;
            for (int i = 0; i < ts.bins.size(); i++) {
                Engine.Bin bin = ts.bins.get(i);
                StringBuilder cuts = new StringBuilder();
                for (int j = 0; j < bin.items.size(); j++) {
                    if (j > 0) cuts.append(" + ");
                    Engine.Piece pc = bin.items.get(j);
                    if (pc.cutLabel != null) cuts.append(pc.cutLabel).append(":");
                    cuts.append(s.fmt(pc.len));
                }
                x.row().text(Engine.nameOf(ts.type)).intg(i + 1)
                       .text(cuts.toString())
                       .num(s.out(bin.used)).num(s.out(Math.max(0, bin.free(s.stock))));
            }
        }

        /* SHEET 5: RP MARKING */
        boolean anyRp = false;
        for (Engine.WinResult r : res) if (!r.empty && r.rpQty > 0) { anyRp = true; break; }
        if (anyRp) {
            x.sheet("RP Marking").widths(12, 14, 10, 12, 10, 14);
            x.row().text("WINDOW", XlsxWriter.S_HEAD).text("MID PIPE", XlsxWriter.S_HEAD)
                   .text("RP QTY", XlsxWriter.S_HEAD).text("SPACE", XlsxWriter.S_HEAD)
                   .text("POINT #", XlsxWriter.S_HEAD).text("MARK AT", XlsxWriter.S_HEAD);
            for (Engine.WinResult r : res) {
                if (r.empty || r.rpQty <= 0) continue;
                double[] pts = Engine.rpPoints(r.muliyaH, s, r.rpQty);
                int gapAt = Engine.rpGapIndex(r.rpQty);
                for (int i = 0; i < pts.length; i++) {
                    x.row();
                    if (i == 0) x.text(r.src.name).num(s.out(r.muliyaH)).intg(r.rpQty).num(s.out(r.rpSpace));
                    else x.blank().blank().blank().blank();
                    x.intg(i + 1).num(s.out(pts[i]));
                }
                if (gapAt > 0 && r.rpQty > 1) {
                    x.row().blank().blank().blank().blank()
                     .text("GAP", XlsxWriter.S_BOLD).num(s.out(s.rpGap), XlsxWriter.S_TOTAL);
                }
                x.row();
            }
        }

        /* SHEET 6: GLASS */
        x.sheet("Glass").widths(14, 20, 10, 14, 14);
        x.row().text("WINDOW", XlsxWriter.S_HEAD).text("GLASS H x W", XlsxWriter.S_HEAD)
               .text("QTY", XlsxWriter.S_HEAD).text("SQ.FT EACH", XlsxWriter.S_HEAD)
               .text("TOTAL SQ.FT", XlsxWriter.S_HEAD);
        for (Engine.WinResult r : res) {
            if (!r.ok) continue;
            x.row().text(r.src.name)
                   .text(s.fmt(r.glassH) + " x " + s.fmt(r.glassW))
                   .intg(r.glassQty()).num(r.glassSqft())
                   .num(r.glassSqft() * r.glassQty());
        }
        x.row().text("TOTAL", XlsxWriter.S_BOLD).blank().blank().blank()
               .num(g.glassSqft, XlsxWriter.S_TOTAL);

        /* write */
        File dir = new File(ctx.getExternalFilesDir(null), "estimates");
        if (!dir.exists()) dir.mkdirs();
        String fname = "ALU_Estimate_" +
                new SimpleDateFormat("ddMMyy_HHmm", Locale.US).format(new Date()) + ".xlsx";
        File out = new File(dir, fname);
        x.write(out);
        return out;
    }

    public static void shareFile(Context ctx, File f, String title) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx,
                    ctx.getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_SUBJECT, f.getName());
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(i, title));
        } catch (Exception e) {
            Toast.makeText(ctx, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Share customer data as JSON for app-to-app transfer */
    public static File exportCustomerJson(Context ctx, com.digitalalu.alu.model.Customer cust) throws Exception {
        org.json.JSONObject o = cust.toJson();
        File dir = new File(ctx.getExternalFilesDir(null), "customers");
        if (!dir.exists()) dir.mkdirs();
        String fname = "Customer_" + cust.name.replaceAll("[^a-zA-Z0-9]", "_") + ".json";
        File out = new File(dir, fname);
        FileOutputStream fos = new FileOutputStream(out);
        fos.write(o.toString(2).getBytes("UTF-8"));
        fos.close();
        return out;
    }

    public static void shareCustomerJson(Context ctx, File jsonFile) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx,
                    ctx.getPackageName() + ".fileprovider", jsonFile);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_SUBJECT, jsonFile.getName());
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(i, "Share Customer Data"));
        } catch (Exception e) {
            Toast.makeText(ctx, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
