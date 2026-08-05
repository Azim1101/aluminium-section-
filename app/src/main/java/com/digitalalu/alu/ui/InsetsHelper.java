package com.digitalalu.alu.ui;

import android.os.Build;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge support for Android 15+ (targetSdk 35 enforces edge-to-edge).
 *
 * - On API 35+ the status bar and navigation bar overlay the window: the top
 *   bar gets extra top padding (so the coloured app bar extends behind the
 *   status bar) and the root gets bottom padding for the navigation bar.
 * - On API 30+ ADJUST_RESIZE no longer resizes the window for the keyboard,
 *   so the root also takes the IME bottom inset — this keeps the old
 *   "scroll away from the keyboard" behaviour.
 * - On API &lt; 30 nothing changes; the system already insets the window.
 */
public final class InsetsHelper {

    private InsetsHelper() {}

    public static void apply(final View root, final View topBar) {
        final int[] baseTop = topBar == null ? null : new int[]{
                topBar.getPaddingLeft(), topBar.getPaddingTop(),
                topBar.getPaddingRight(), topBar.getPaddingBottom()};
        final int baseBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, wi) -> {
            int bottom = baseBottom;

            if (Build.VERSION.SDK_INT >= 30) {
                Insets ime = wi.getInsets(WindowInsetsCompat.Type.ime());
                bottom = Math.max(bottom, baseBottom + ime.bottom);
            }
            if (Build.VERSION.SDK_INT >= 35) {
                Insets sb = wi.getInsets(WindowInsetsCompat.Type.systemBars());
                bottom = Math.max(bottom, baseBottom + sb.bottom);
                if (topBar != null) {
                    topBar.setPadding(baseTop[0], baseTop[1] + sb.top,
                            baseTop[2], baseTop[3]);
                }
            }
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
