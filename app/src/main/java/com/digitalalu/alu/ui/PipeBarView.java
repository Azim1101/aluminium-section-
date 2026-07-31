package com.digitalalu.alu.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.digitalalu.alu.calc.Engine;
import com.digitalalu.alu.calc.Settings;

/** Ek stock pipe — uske andar kaunse piece kahan katenge, colored bar. */
public class PipeBarView extends View {

    private Engine.Bin bin;
    private double stock;
    private int color = 0xFF3B82F6;
    private Settings st;

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pWaste = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public PipeBarView(Context c) { super(c); init(); }
    public PipeBarView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        pWaste.setColor(0xFFE2E8F0);
        pText.setColor(Color.WHITE);
        pText.setTextAlign(Paint.Align.CENTER);
        pText.setFakeBoldText(true);
        pEdge.setStyle(Paint.Style.STROKE);
        pEdge.setColor(0xFFFFFFFF);
        pEdge.setStrokeWidth(dp(2));
    }

    public void setData(Engine.Bin b, double stockLen, int col, Settings s) {
        bin = b; stock = stockLen; color = col; st = s;
        invalidate();
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onMeasure(int w, int h) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec((int) dp(34), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bin == null || stock <= 0) return;
        float W = getWidth(), H = getHeight();
        float rad = dp(5);

        // background = waste
        r.set(0, 0, W, H);
        c.drawRoundRect(r, rad, rad, pWaste);

        pText.setTextSize(dp(10));
        float x = 0;
        for (int i = 0; i < bin.items.size(); i++) {
            Engine.Piece p = bin.items.get(i);
            float w = (float) (p.len / stock) * W;
            pFill.setColor(color);
            r.set(x, 0, x + w, H);
            c.drawRoundRect(r, i == 0 ? rad : 0, i == 0 ? rad : 0, pFill);
            if (i > 0) c.drawLine(x, 0, x, H, pEdge);

            if (w > dp(34) && st != null) {
                String t = st.fmt(p.len);
                c.drawText(t, x + w / 2f, H / 2f + dp(3.5f), pText);
            }
            x += w;
        }
    }
}
