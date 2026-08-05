package com.digitalalu.alu.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.digitalalu.alu.calc.ManualCuttingEngine;
import com.digitalalu.alu.calc.Settings;

/** Visual layout preview for one manually optimized stock sheet. */
public class SheetLayoutView extends View {

    private static final int[] COLORS = {
            0xFF2563EB, 0xFF16A34A, 0xFFF59E0B, 0xFF8B5CF6,
            0xFFEC4899, 0xFF0EA5E9, 0xFFEA580C, 0xFF64748B
    };

    private ManualCuttingEngine.SheetBin bin;
    private Settings settings;
    private boolean faceCut;
    private int faceAxis;
    private int sheetNo;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public SheetLayoutView(Context context) { super(context); init(); }
    public SheetLayoutView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0xFF334155);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD));
        facePaint.setColor(0xFF2563EB);
        facePaint.setStrokeWidth(dp(1.5f));
        facePaint.setTextSize(dp(9));
        facePaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD));
    }

    public void setData(ManualCuttingEngine.SheetBin sheet, Settings s,
                        boolean isFaceCut, int axis, int number) {
        bin = sheet;
        settings = s;
        faceCut = isFaceCut;
        faceAxis = axis;
        sheetNo = number;
        requestLayout();
        invalidate();
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int available = MeasureSpec.getSize(widthSpec);
        if (available <= 0) available = (int) dp(320);
        float aspect = 1f;
        if (bin != null && bin.width > 0 && bin.height > 0)
            aspect = (float) (bin.height / bin.width);
        int desired = (int) ((available - dp(36)) * aspect + dp(44));
        desired = Math.max((int) dp(150), Math.min((int) dp(620), desired));
        int height = resolveSize(desired, heightSpec);
        setMeasuredDimension(resolveSize(available, widthSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bin == null || bin.width <= 0 || bin.height <= 0) return;

        float viewW = getWidth();
        float viewH = getHeight();
        float top = dp(faceCut ? 30 : 18);
        float leftPad = dp(faceCut && faceAxis == ManualCuttingEngine.FACE_HEIGHT ? 30 : 12);
        float rightPad = dp(12);
        float bottom = dp(12);

        float scale = Math.min((viewW - leftPad - rightPad) / (float) bin.width,
                (viewH - top - bottom) / (float) bin.height);
        if (scale <= 0) return;
        float sw = (float) bin.width * scale;
        float sh = (float) bin.height * scale;
        float sx = leftPad + ((viewW - leftPad - rightPad) - sw) / 2f;
        float sy = top + ((viewH - top - bottom) - sh) / 2f;

        if (faceCut) drawFaceDirection(canvas, sx, sy, sw, sh);

        fill.setColor(Color.WHITE);
        rect.set(sx, sy, sx + sw, sy + sh);
        canvas.drawRoundRect(rect, dp(3), dp(3), fill);
        canvas.drawRoundRect(rect, dp(3), dp(3), stroke);

        for (int i = 0; i < bin.pieces.size(); i++) {
            ManualCuttingEngine.SheetPiece p = bin.pieces.get(i);
            float x = sx + (float) p.x * scale;
            float y = sy + (float) p.y * scale;
            float w = (float) p.w * scale;
            float h = (float) p.h * scale;
            if (w <= 0 || h <= 0) continue;

            fill.setColor(COLORS[Math.abs(p.number - 1) % COLORS.length]);
            rect.set(x, y, x + w, y + h);
            canvas.drawRoundRect(rect, dp(2), dp(2), fill);
            stroke.setColor(0x99FFFFFF);
            canvas.drawRoundRect(rect, dp(2), dp(2), stroke);
            stroke.setColor(0xFF334155);

            if (w >= dp(34) && h >= dp(22)) {
                text.setColor(Color.WHITE);
                float size = Math.min(dp(10), Math.max(dp(6.5f), Math.min(w / 5f, h / 3.5f)));
                text.setTextSize(size);
                String line1 = shortLabel(p.name, p.number);
                float midX = x + w / 2f;
                float midY = y + h / 2f;
                canvas.drawText(line1, midX, midY - size * .18f, text);
                if (w >= dp(52) && h >= dp(34) && settings != null) {
                    text.setTextSize(Math.max(dp(6), size * .78f));
                    String dims = settings.fmt(p.first) + " x " + settings.fmt(p.second)
                            + (p.rotated ? "  R" : "");
                    canvas.drawText(dims, midX, midY + size * 1.05f, text);
                }
            }
        }

        text.setTextSize(dp(9));
        text.setColor(0xFF475569);
        text.setTextAlign(Paint.Align.LEFT);
        String title = "Sheet #" + sheetNo;
        canvas.drawText(title, sx + dp(4), sy + dp(12), text);
        text.setTextAlign(Paint.Align.CENTER);
    }

    private void drawFaceDirection(Canvas canvas, float sx, float sy, float sw, float sh) {
        String first = "FACE / PATTERN";
        String second = faceAxis == ManualCuttingEngine.FACE_HEIGHT
                ? "HEIGHT - FIRST SIZE" : "WIDTH - FIRST SIZE";
        facePaint.setTextAlign(Paint.Align.LEFT);
        if (faceAxis == ManualCuttingEngine.FACE_HEIGHT) {
            float x = Math.max(dp(4), sx - dp(18));
            float y1 = sy + sh - dp(5);
            float y2 = sy + dp(5);
            canvas.drawLine(x, y1, x, y2, facePaint);
            canvas.drawLine(x, y2, x - dp(3), y2 + dp(6), facePaint);
            canvas.drawLine(x, y2, x + dp(3), y2 + dp(6), facePaint);
            canvas.save();
            canvas.rotate(-90, x - dp(4), sy + sh / 2f);
            canvas.drawText("FACE - HEIGHT", x - dp(4), sy + sh / 2f, facePaint);
            canvas.restore();
        } else {
            float y = Math.max(dp(18), sy - dp(10));
            float x1 = sx + dp(4);
            float x2 = sx + sw - dp(4);
            canvas.drawLine(x1, y, x2, y, facePaint);
            canvas.drawLine(x2, y, x2 - dp(6), y - dp(3), facePaint);
            canvas.drawLine(x2, y, x2 - dp(6), y + dp(3), facePaint);
            canvas.drawText(first + " - " + second, sx, Math.max(dp(11), y - dp(5)), facePaint);
        }
        facePaint.setTextAlign(Paint.Align.LEFT);
    }

    private String shortLabel(String name, int n) {
        String base = name == null || name.trim().isEmpty() ? "P" : name.trim();
        if (base.length() > 8) base = base.substring(0, 8);
        return base + " " + n;
    }
}
