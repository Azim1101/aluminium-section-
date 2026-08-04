package com.digitalalu.alu.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.digitalalu.alu.calc.Settings;

/** Muliya pipe pe RP grill ki marking — vertical lines + beech me gap. */
public class RpRulerView extends View {

    private double[] pts;
    private double total;
    private int gapIndex = -1;
    private Settings st;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGap = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public RpRulerView(Context c) { super(c); init(); }
    public RpRulerView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        pBg.setColor(0xFFF8FAFC);
        pLine.setColor(0xFFEC4899);
        pGap.setColor(0x33EC4899);
        pTxt.setColor(0xFF9CA3AF);
        pTxt.setTextAlign(Paint.Align.CENTER);
        pTxt.setFakeBoldText(true);
    }

    public void setData(double[] points, double totalLen, int gapIdx, Settings s) {
        pts = points; total = totalLen; gapIndex = gapIdx; st = s;
        invalidate();
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onMeasure(int w, int h) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec((int) dp(46), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (pts == null || pts.length == 0 || total <= 0) return;
        float W = getWidth(), H = getHeight();
        float barTop = dp(4), barBot = H - dp(16);

        r.set(0, barTop, W, barBot);
        c.drawRoundRect(r, dp(4), dp(4), pBg);

        // gap band
        if (gapIndex > 0 && gapIndex < pts.length) {
            float x1 = (float) (pts[gapIndex - 1] / total) * W;
            float x2 = (float) (pts[gapIndex] / total) * W;
            r.set(x1, barTop, x2, barBot);
            c.drawRect(r, pGap);
        }

        pTxt.setTextSize(dp(8));
        for (int i = 0; i < pts.length; i++) {
            float x = (float) (pts[i] / total) * W;
            r.set(x - dp(1.4f), barTop, x + dp(1.4f), barBot);
            c.drawRoundRect(r, dp(2), dp(2), pLine);
            if (pts.length <= 12 && st != null) {
                c.drawText(st.fmt(pts[i]), x, H - dp(4), pTxt);
            }
        }
    }
}
