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

/**
 * Material 3 Expressive slider, flat colours only: two 16dp tracks split by a gap, and a slim handle bar between them.
 * Touch it and the bar eases open into a pill that carries the value (so the number is never hidden under your finger),
 * while the tracks make room. The handle glides to wherever you tap, short ranges get step dots and a tick under the finger.
 */
final class M3Slider extends View {
    interface OnChange { void onChange(int value, boolean user); }

    private final M3 m;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final float[] rad = new float[8];
    private final float slop;
    private int min, max = 100, val;
    private OnChange cb;
    private M3.Fmt fmt;
    private String label = "";
    private float labelW;
    private boolean touching, drag, pending, running;
    private float pos, press;          // what is drawn: handle position (0..1) and how far the bar has opened into a pill (0..1)
    private long last;
    private float downX, downY;

    private final Runnable step = new Runnable() {
        @Override public void run() {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            float tp = frac(), tr = touching ? 1f : 0f;
            pos += (tp - pos) * (1f - (float) Math.exp(-dt * 26f));
            press += (tr - press) * (1f - (float) Math.exp(-dt * 20f));
            boolean more = Math.abs(tp - pos) > 0.0004f || Math.abs(tr - press) > 0.004f;
            if (!more) { pos = tp; press = tr; }
            invalidate();
            if (more) postOnAnimation(this); else running = false;
        }
    };
    private final Runnable release = new Runnable() {
        @Override public void run() { touching = false; kick(); }
    };

    M3Slider(M3 m) {
        super(m.c);
        this.m = m;
        slop = android.view.ViewConfiguration.get(m.c).getScaledTouchSlop();   // once, not on every touch event
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextSize(13f * m.c.getResources().getDisplayMetrics().scaledDensity);
    }

    void set(int min, int max, int v) {
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.val = v;
        pos = frac();   // programmatic: no glide
        relabel();
        invalidate();
    }

    int value() { return val; }

    void setOnChange(OnChange c) { cb = c; }

    /** How the value is written inside the pill (the same text the row shows next to its title). */
    void setFormat(M3.Fmt f) {
        fmt = f;
        relabel();
    }

    private void relabel() {
        label = fmt != null ? fmt.f(val) : String.valueOf(val);
        labelW = p.measureText(label);
    }

    private float frac() { return (val - min) / (float) (max - min); }

    private void kick() {
        if (running) return;
        running = true;
        last = System.nanoTime();
        postOnAnimation(step);
    }

    /** A track piece whose outer end is fully round and whose end next to the handle is nearly square. */
    private void piece(Canvas c, float rl, float rr, int color) {
        rad[0] = rad[1] = rad[6] = rad[7] = rl;
        rad[2] = rad[3] = rad[4] = rad[5] = rr;
        path.reset();
        path.addRoundRect(r, rad, Path.Direction.CW);
        p.setColor(color);
        c.drawPath(path, p);
    }

    @Override protected void onDraw(Canvas c) {
        float d = m.d, pad = 14f * d, x0 = pad, x1 = getWidth() - pad, cy = getHeight() / 2f;
        float big = 8f * d, small = 3f * d, gap = 6f * d, hh = 16f * d;
        float idleW = 4f * d, pillW = Math.max(40f * d, labelW + 24f * d);
        float hw = (idleW + (pillW - idleW) * press) / 2f;
        float tx = x0 + (x1 - x0) * pos;
        float hx = Math.max(hw, Math.min(getWidth() - hw, tx));   // the pill never leaves the view, even at the ends
        p.setStyle(Paint.Style.FILL);
        float rs = hx + hw + gap;                  // the part still to go
        if (x1 - rs > 2f * d) {
            r.set(rs, cy - big, x1, cy + big);
            piece(c, small, big, m.secondaryContainer);
        }
        float le = hx - hw - gap;                  // the part already done
        if (le - x0 > 2f * d) {
            r.set(x0, cy - big, le, cy + big);
            piece(c, big, small, m.primary);
        }
        int n = max - min;
        if (n <= 20) {                             // short range: a dot per step
            float rd = 1.7f * d, clear = hw + gap + 3f * d;
            for (int i = 1; i < n; i++) {
                float x = x0 + (x1 - x0) * i / n;
                if (Math.abs(x - hx) < clear) continue;
                p.setColor(x < hx ? Col.alpha(m.onPrimary, 0.75f) : Col.alpha(m.primary, 0.8f));
                c.drawCircle(x, cy, rd, p);
            }
        } else if (x1 - rs > 20f * d) {            // long range: one end stop
            p.setColor(m.primary);
            c.drawCircle(x1 - big, cy, 2f * d, p);
        }
        p.setColor(m.primary);
        r.set(hx - hw, cy - hh, hx + hw, cy + hh);
        float rr = Math.min(hw, hh);
        c.drawRoundRect(r, rr, rr, p);
        if (press > 0.55f) {                       // the value, once the pill is open enough to hold it
            p.setColor(Col.alpha(m.onPrimary, (press - 0.55f) / 0.45f));
            c.drawText(label, hx, cy - (p.ascent() + p.descent()) / 2f, p);
        }
    }

    private void update(float x) {
        float pad = 14f * m.d, f = (x - pad) / Math.max(1f, getWidth() - 2f * pad);
        f = f < 0 ? 0 : (f > 1 ? 1 : f);
        int v = min + Math.round(f * (max - min));
        if (v != val) {
            val = v;
            relabel();
            if (max - min <= 20) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (cb != null) cb.onChange(v, true);
        }
        kick();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Math.abs(pos - frac()) > 0.0005f) kick();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(step);
        removeCallbacks(release);
        running = false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                pending = true;
                drag = false;
                removeCallbacks(release);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (drag) { update(e.getX()); return true; }
                if (pending) {
                    float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
                    if (dx > slop && dx > dy) {            // horizontal drag: this is the slider's
                        pending = false;
                        drag = true;
                        touching = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                        update(e.getX());
                    } else if (dy > slop) {                // vertical: let the page scroll
                        pending = false;
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (pending) {                             // plain tap: set the value, show the pill for a moment
                    update(e.getX());
                    touching = true;
                    postDelayed(release, 280);
                } else {
                    touching = false;
                }
                drag = false;
                pending = false;
                kick();
                return true;
            default:
                drag = false;
                pending = false;
                touching = false;
                kick();
                return true;
        }
    }
}
