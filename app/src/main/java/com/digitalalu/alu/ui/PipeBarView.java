package com.digitalalu.alu.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.digitalalu.alu.calc.Engine;
import com.digitalalu.alu.calc.Settings;

/**
 * Compact pipe bar — shows each piece with:
 *   - cutLabel on top (e.g. "1H", "2S-W")
 *   - size inside the colored segment
 *   - waste area in grey
 * Height reduced to 28dp for compact display.
 */
public class PipeBarView extends View {

    private Engine.Bin bin;
    private double stock;
    private int color = 0xFF3B82F6;
    private Settings st;

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pWaste = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pKerf = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTextWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTextDark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTextLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public PipeBarView(Context c) { super(c); init(); }
    public PipeBarView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        pWaste.setColor(0xFFE2E8F0);
        // Blade kerf is a thin white gap between coloured cut pieces.
        pKerf.setColor(0xFFFFFFFF);
        pTextWhite.setColor(Color.WHITE);
        pTextWhite.setTextAlign(Paint.Align.CENTER);
        pTextWhite.setFakeBoldText(true);
        pTextDark.setColor(0xFF334155);
        pTextDark.setTextAlign(Paint.Align.CENTER);
        pTextDark.setFakeBoldText(true);
        pTextLabel.setColor(Color.WHITE);
        pTextLabel.setTextAlign(Paint.Align.CENTER);
        pTextLabel.setFakeBoldText(true);
        pEdge.setStyle(Paint.Style.STROKE);
        pEdge.setColor(0x44FFFFFF);
        pEdge.setStrokeWidth(dp(1));
        pLabelBg.setColor(0x55000000);
    }

    public void setData(Engine.Bin b, double stockLen, int col, Settings s) {
        bin = b; stock = stockLen; color = col; st = s;
        invalidate();
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    /** Round rect with different left/right corner radii (Canvas has no such method). */
    private static void drawRoundRectSides(Canvas c, RectF r, float leftRad, float rightRad, Paint p) {
        if (leftRad <= 0 && rightRad <= 0) { c.drawRect(r, p); return; }
        float[] radii = {
                leftRad, leftRad,     // top-left
                rightRad, rightRad,   // top-right
                rightRad, rightRad,   // bottom-right
                leftRad, leftRad      // bottom-left
        };
        Path path = new Path();
        path.addRoundRect(r, radii, Path.Direction.CW);
        c.drawPath(path, p);
    }

    @Override
    protected void onMeasure(int w, int h) {
        // Compact: 28dp bar + 12dp label area = 40dp total
        super.onMeasure(w, MeasureSpec.makeMeasureSpec((int) dp(40), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bin == null || stock <= 0) return;
        float W = getWidth(), H = getHeight();
        float barTop = dp(12);   // space for labels above
        float barH = dp(26);     // bar height
        float rad = dp(4);

        // background = waste area
        r.set(0, barTop, W, barTop + barH);
        c.drawRoundRect(r, rad, rad, pWaste);

        pTextWhite.setTextSize(dp(9));
        pTextLabel.setTextSize(dp(7));

        // bin.used includes blade kerf. Derive the kerf from the packed data so
        // it appears as a real gap instead of being mistaken for leftover waste.
        double rawTotal = 0;
        for (Engine.Piece p : bin.items) rawTotal += p.len;
        double kerfTotal = Math.max(0, bin.used - rawTotal);
        float kerfGap = bin.items.size() > 1
                ? (float) ((kerfTotal / (bin.items.size() - 1)) / stock) * W : 0;

        float x = 0;
        for (int i = 0; i < bin.items.size(); i++) {
            Engine.Piece p = bin.items.get(i);
            float pw = (float) (p.len / stock) * W;
            float pwActual = Math.max(pw, dp(2)); // min width

            // Draw colored segment
            pFill.setColor(color);
            r.set(x, barTop, x + pwActual, barTop + barH);
            c.drawRoundRect(r, i == 0 ? rad : 0, i == 0 ? rad : 0, pFill);
            // Right round for last
            if (i == bin.items.size() - 1) {
                r.set(x, barTop, x + pwActual, barTop + barH);
                drawRoundRectSides(c, r, 0, rad, pFill);
            }

            // Separator line
            if (i > 0) c.drawLine(x, barTop, x, barTop + barH, pEdge);

            // Draw cut label above bar (e.g. "1H", "2S-W")
            if (p.cutLabel != null && pw > dp(18)) {
                float labelX = x + pwActual / 2f;
                float labelY = barTop - dp(2);
                // Small background pill
                float tw = pTextLabel.measureText(p.cutLabel);
                float pillW = tw + dp(6);
                float pillH = dp(10);
                r.set(labelX - pillW / 2f, labelY - pillH + dp(2),
                       labelX + pillW / 2f, labelY + dp(2));
                c.drawRoundRect(r, dp(3), dp(3), pLabelBg);
                c.drawText(p.cutLabel, labelX, labelY, pTextLabel);
            }

            // Draw size inside bar
            if (st != null && pw > dp(28)) {
                String sizeText = st.fmt(p.len);
                float textY = barTop + barH / 2f + dp(3);
                c.drawText(sizeText, x + pwActual / 2f, textY, pTextWhite);
            }

            x += pwActual;
            if (i < bin.items.size() - 1 && kerfGap > 0) {
                c.drawRect(x, barTop, x + kerfGap, barTop + barH, pKerf);
                x += kerfGap;
            }
        }

        // Draw waste label on remaining area
        float remaining = W - x;
        if (remaining > dp(20)) {
            double wasteVal = stock - bin.used;
            if (wasteVal > 0 && st != null) {
                pTextDark.setTextSize(dp(8));
                String wasteText = st.fmt(wasteVal);
                c.drawText(wasteText, x + remaining / 2f, barTop + barH / 2f + dp(3), pTextDark);
            }
        }
    }
}
