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

/** Material 3 switch: 52x32dp track, thumb grows from 16 to 24dp. */
final class M3Switch extends View {
    private final M3 m;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private M3.OnSel cb;
    private boolean on;
    private float t;
    private ValueAnimator va;

    M3Switch(M3 m, boolean on, M3.OnSel cb) {
        super(m.c);
        this.m = m;
        this.on = on;
        this.t = on ? 1f : 0f;
        this.cb = cb;
        setClickable(true);
        setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggle(); } });
    }

    boolean isOn() { return on; }

    /** Re-binds a recycled switch: jumps to {@code on} with no animation and no callback. */
    void bind(boolean on, M3.OnSel cb) {
        if (va != null) { va.cancel(); va = null; }
        this.on = on;
        this.t = on ? 1f : 0f;
        this.cb = cb;
        invalidate();
    }

    /** Moves the switch to {@code v} with the usual animation but without calling the callback (undoing a refused change). */
    void revert(boolean v) {
        if (on == v) return;
        on = v;
        if (va != null) va.cancel();
        va = ValueAnimator.ofFloat(t, on ? 1f : 0f);
        va.setDuration(220);
        va.setInterpolator(M3.STANDARD);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { t = (Float) a.getAnimatedValue(); invalidate(); }
        });
        va.start();
    }

    void toggle() {
        on = !on;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (va != null) va.cancel();
        va = ValueAnimator.ofFloat(t, on ? 1f : 0f);
        va.setDuration(220);
        va.setInterpolator(M3.STANDARD);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { t = (Float) a.getAnimatedValue(); invalidate(); }
        });
        va.start();
        if (cb != null) cb.on(on ? 1 : 0);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), d = m.d;
        r.set(0, 0, w, h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Col.mix(m.containerHigh, m.primary, t));
        c.drawRoundRect(r, h / 2f, h / 2f, p);
        if (t < 1f) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * d);
            p.setColor(Col.alpha(m.outline, 1f - t));
            r.set(d, d, w - d, h - d);
            c.drawRoundRect(r, h / 2f, h / 2f, p);
            p.setStyle(Paint.Style.FILL);
        }
        float rad = (8f + 4f * t) * d, cx = (16f + 20f * t) * d;
        p.setColor(Col.mix(m.outline, m.onPrimary, t));
        c.drawCircle(cx, h / 2f, rad, p);
        if (t > 0.35f) {   // the check fades in as the thumb grows
            float s = 14f * d;
            IconView.glyph(c, p, IconView.CHECK, Col.alpha(m.primary, (t - 0.35f) / 0.65f), cx - s / 2f, h / 2f - s / 2f, s);
        }
    }
}
