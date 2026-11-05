package com.digitalalu.alu.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import com.digitalalu.alu.design.DesignDocument;
import java.util.Locale;

/** Native vector preview; bounded geometry, no bitmap allocation or model loading. */
public final class DesignPreviewView extends View {
    private DesignDocument document;
    private DesignDocument.Unit unit = DesignDocument.Unit.MM;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public DesignPreviewView(Context context) {
        super(context); setContentDescription("Window and door elevation preview");
    }
    public void setDocument(DesignDocument d, DesignDocument.Unit u) {
        document = d; unit = u;
        setContentDescription(d.kind + " elevation, " + label(d.widthMm, u) + " wide, "
                + label(d.heightMm, u) + " high, " + d.bays + " equal bays");
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.WHITE);
        if (document != null) render(canvas, document, unit, getWidth(), getHeight(), paint);
    }
    public static String label(double mm, DesignDocument.Unit unit) {
        return String.format(Locale.US, "%.2f %s", unit.fromMm(mm),
                unit == DesignDocument.Unit.MM ? "mm" : "in");
    }
    /** Shared by screen and PDF; coordinates measured from the top-left. */
    public static void render(Canvas c, DesignDocument d, DesignDocument.Unit u,
                              float width, float height, Paint p) {
        float margin = Math.min(width, height) * .16f;
        float scale = (float) Math.min((width - 2 * margin) / d.widthMm,
                (height - 2 * margin) / d.heightMm);
        float x = (width - (float) d.widthMm * scale) / 2;
        float y = (height - (float) d.heightMm * scale) / 2;
        c.save(); c.translate(x, y); c.scale(scale, scale);
        p.setStyle(Paint.Style.FILL); p.setColor(0xffe3f3ff);
        c.drawRect(0, 0, (float)d.widthMm, (float)d.heightMm, p);
        p.setColor(0xff576579);
        for (DesignDocument.Rect r : d.members()) c.drawRect((float)r.x, (float)r.y,
                (float)(r.x + r.width), (float)(r.y + r.height), p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1 / scale); p.setColor(0xff172334);
        for (DesignDocument.Rect r : d.openings()) c.drawRect((float)r.x, (float)r.y,
                (float)(r.x + r.width), (float)(r.y + r.height), p);
        c.restore();
        p.setStyle(Paint.Style.FILL); p.setColor(Color.BLACK);
        p.setTextSize(Math.max(10, Math.min(width, height) * .035f));
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(label(d.widthMm, u), width / 2, y - 12, p);
        c.drawText("H " + label(d.heightMm, u), width / 2,
                y + (float)d.heightMm * scale + p.getTextSize() * 1.7f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }
}
