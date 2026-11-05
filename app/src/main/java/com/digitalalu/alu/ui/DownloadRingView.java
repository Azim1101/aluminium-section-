package com.digitalalu.alu.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import com.digitalalu.alu.R;

/**
 * Animated progress ring for model downloads and model loading / testing.
 *
 *  • idle          -> quiet track + optional caption (never a misleading "0%")
 *  • determinate   -> smooth arc + big percentage
 *  • indeterminate -> rotating sweep, so the user can SEE work happening
 *  • done          -> green ring + tick
 *
 * No dependencies, no images, and animators are cancelled on detach so it can
 * never leak an Activity or keep spinning in the background.
 */
public class DownloadRingView extends View {

    /*
     * v3.0.0 One UI: pehle ye paanchon rang hard-coded the, isliye dark mode me
     * ring ka track/text galat dikhta tha. Ab sab theme tokens se aate hain.
     */
    private final int colTrack;
    private final int colAccent;
    private final int colDone;
    private final int colText;
    private final int colCaption;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private float shown = 0f;
    private float target = 0f;
    private boolean indeterminate = false;
    private boolean done = false;
    private boolean idle = true;
    private String centerText = "";
    private String caption = "";

    private ValueAnimator stepAnim;
    private ValueAnimator sweepAnim;
    private float sweepAngle = 0f;

    public DownloadRingView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        float stroke = Math.max(5f, 6f * d);

        colTrack = V2.col(c, R.color.card3);
        colAccent = V2.col(c, R.color.acc);
        colDone = V2.col(c, R.color.ok);
        colText = V2.col(c, R.color.txt);
        colCaption = V2.col(c, R.color.mut);

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(colTrack);

        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(colAccent);

        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(stroke);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(colDone);

        text.setColor(colText);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setTextSize(12.5f * d);

        captionPaint.setColor(colCaption);
        captionPaint.setTextAlign(Paint.Align.CENTER);
        captionPaint.setTextSize(8.5f * d);
    }

    /* ------------------------------ API ------------------------------ */

    /** @param percent 0..100 */
    public void setProgress(float percent) {
        target = Math.max(0f, Math.min(100f, percent));
        indeterminate = false;
        idle = false;
        done = false;
        stopSweep();
        animateTo(target);
    }

    public void setIndeterminate(String centreLabel) {
        indeterminate = true;
        idle = false;
        done = false;
        centerText = centreLabel == null ? "" : centreLabel;
        invalidate();
        startSweep();
    }

    public void setDone() {
        indeterminate = false;
        idle = false;
        done = true;
        target = 100f;
        shown = 100f;
        centerText = "";
        stopSweep();
        invalidate();
    }

    /** Quiet state: just the track, with the model size or short text as caption. */
    public void setIdle(String cap) {
        indeterminate = false;
        done = false;
        idle = true;
        shown = 0f;
        target = 0f;
        centerText = "";
        caption = cap == null ? "" : cap;
        stopSweep();
        invalidate();
    }

    public void setCaption(String s) { caption = s == null ? "" : s; invalidate(); }

    public boolean isDone() { return done; }
    public boolean isIndeterminate() { return indeterminate; }

    public void reset() { setIdle(""); }

    /* --------------------------- animation --------------------------- */

    private void animateTo(float to) {
        if (stepAnim != null) stepAnim.cancel();
        stepAnim = ValueAnimator.ofFloat(shown, to);
        stepAnim.setDuration(420L);
        stepAnim.setInterpolator(new DecelerateInterpolator());
        stepAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                shown = (Float) a.getAnimatedValue();
                centerText = Math.round(shown) + "%";
                invalidate();
            }
        });
        stepAnim.start();
    }

    private void startSweep() {
        if (sweepAnim != null && sweepAnim.isRunning()) return;
        sweepAnim = ValueAnimator.ofFloat(0f, 360f);
        sweepAnim.setDuration(1100L);
        sweepAnim.setRepeatCount(ValueAnimator.INFINITE);
        sweepAnim.setInterpolator(new LinearInterpolator());
        sweepAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                sweepAngle = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        sweepAnim.start();
    }

    private void stopSweep() {
        if (sweepAnim != null) {
            sweepAnim.cancel();
            sweepAnim = null;
        }
    }

    @Override protected void onDetachedFromWindow() {
        stopSweep();
        if (stepAnim != null) { stepAnim.cancel(); stepAnim = null; }
        super.onDetachedFromWindow();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (indeterminate) startSweep();
    }

    /* ---------------------------- drawing ---------------------------- */

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int size = (int) (getResources().getDisplayMetrics().density * 58f);
        int w = resolveSize(size, wSpec);
        int h = resolveSize(size, hSpec);
        setMeasuredDimension(Math.min(w, h), Math.min(w, h));
    }

    @Override protected void onDraw(Canvas cv) {
        float pad = arc.getStrokeWidth() + captionPaint.getTextSize() * 0.6f;
        box.set(pad, pad, getWidth() - pad, getHeight() - pad);

        cv.drawArc(box, 0f, 360f, false, track);

        if (done) {
            cv.drawArc(box, 0f, 360f, false, tick);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(getWidth(), getHeight()) / 2f - pad;
            Path p = new Path();
            p.moveTo(cx - r * 0.42f, cy + r * 0.02f);
            p.lineTo(cx - r * 0.10f, cy + r * 0.34f);
            p.lineTo(cx + r * 0.46f, cy - r * 0.34f);
            cv.drawPath(p, tick);
        } else if (indeterminate) {
            cv.drawArc(box, sweepAngle - 90f, 76f, false, arc);
            if (centerText.length() > 0) drawCenter(cv, centerText);
        } else if (!idle) {
            cv.drawArc(box, -90f, 360f * (shown / 100f), false, arc);
            drawCenter(cv, centerText);
        }

        if (caption.length() > 0) {
            cv.drawText(caption, getWidth() / 2f,
                    getHeight() / 2f + text.getTextSize() * 0.95f, captionPaint);
        }
    }

    private void drawCenter(Canvas cv, String s) {
        cv.drawText(s, getWidth() / 2f,
                getHeight() / 2f - (text.descent() + text.ascent()) / 2f, text);
    }
}
