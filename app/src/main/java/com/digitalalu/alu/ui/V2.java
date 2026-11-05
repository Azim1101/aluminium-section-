package com.digitalalu.alu.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.digitalalu.alu.R;

/**
 * ALU-UI-V2 - helpers for the screens that build their views in Java.
 *
 * Everything reads the same colour tokens as the XML screens, so light and
 * dark mode stay in sync.
 */
public final class V2 {

    private V2() {}

    public static int col(Context c, int res) {
        return c.getResources().getColor(res);
    }

    public static float dpi(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    /** Rounded rectangle filled with a token colour. */
    public static GradientDrawable round(Context c, int fillRes, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(col(c, fillRes));
        d.setCornerRadius(radiusDp * dpi(c));
        return d;
    }

    /** Same, but with a raw colour (used for legacy one-off tints). */
    public static GradientDrawable roundColor(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radiusDp * dpi(c));
        return d;
    }

    /* ===================== One UI shape metrics ===================== */
    /** One UI card / dialog / sheet radius (26dp per One UI guidelines). */
    public static final float R_CARD = 26f;
    /** One UI medium radius (chips, tiles, small boxes). */
    public static final float R_MID = 20f;
    /** One UI small radius (text fields). */
    public static final float R_SMALL = 12f;
    /** Full pill — One UI buttons and the composer. */
    public static final float R_PILL = 999f;

    /**
     * One UI large-title bar: page-coloured background, first TextView becomes
     * the big 28sp ink title, trailing views keep their own styling.
     */
    public static void topBar(Context c, LinearLayout top) {
        top.setBackgroundColor(col(c, R.color.bg));
        for (int i = 0; i < top.getChildCount(); i++) {
            View v = top.getChildAt(i);
            if (!(v instanceof TextView)) continue;
            TextView tv = (TextView) v;
            tv.setTextColor(col(c, R.color.txt));
            tv.setTextSize(28f);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setLetterSpacing(-0.02f);
            tv.setIncludeFontPadding(false);
            break;
        }
    }

    /** One UI pill filled with a token colour (buttons, chips, composer). */
    public static GradientDrawable pill(Context c, int fillRes) {
        return round(c, fillRes, R_PILL);
    }

    /** One UI pill with a hairline token stroke. */
    public static GradientDrawable pillStroke(Context c, int fillRes, int lineRes) {
        GradientDrawable d = round(c, fillRes, R_PILL);
        d.setStroke(Math.max(1, (int) (1f * dpi(c))), col(c, lineRes));
        return d;
    }

    /** One UI circular icon tile (40dp settings/list icons). */
    public static GradientDrawable tile(Context c, int fillRes) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(col(c, fillRes));
        return d;
    }

    /** One UI card: 26dp radius, borderless. */
    public static GradientDrawable card(Context c) {
        return round(c, R.color.card, R_CARD);
    }

    /** One UI text field: 12dp radius, sunken fill. */
    public static GradientDrawable field(Context c) {
        return round(c, R.color.card2, R_SMALL);
    }
}
