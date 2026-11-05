package com.vxyzview.voidisland;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A solid scalloped "cookie" badge (M3 Expressive shape) with a glyph in the middle. Flat colours only. */
final class CookieView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int icon, bg, fg, lobes;

    CookieView(android.content.Context c, int icon, int bg, int fg, int lobes) {
        super(c);
        this.icon = icon;
        this.bg = bg;
        this.fg = fg;
        this.lobes = lobes;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        path.reset();
        float cx = w / 2f, cy = h / 2f, big = Math.min(w, h) / 2f;
        int n = 240;
        for (int i = 0; i <= n; i++) {
            double a = 2.0 * Math.PI * i / n;
            float rr = (float) (big * (0.915 + 0.085 * Math.cos(lobes * a)));
            float x = cx + rr * (float) Math.cos(a), y = cy + rr * (float) Math.sin(a);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
    }

    @Override protected void onDraw(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(bg);
        c.drawPath(path, p);
        float s = Math.min(getWidth(), getHeight()) * 0.46f;
        IconView.glyph(c, p, icon, fg, (getWidth() - s) / 2f, (getHeight() - s) / 2f, s);
    }
}
