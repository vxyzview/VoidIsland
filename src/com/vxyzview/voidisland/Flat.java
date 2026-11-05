package com.vxyzview.voidisland;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The flat kit the activity cards are built from: solid fills and plain strokes only. No gradients, no glows, no shadows.
 * Chips and badges are the theme colour at low strength behind the same colour; bars are rows of solid cells.
 */
final class Flat {
    private Flat() {}

    /** A tonal capsule label: {@code color} at low strength behind the same colour as text. */
    static TextView chip(Context c, CharSequence text, int color, float sp) {
        TextView t = Cards.tv(c, text, sp, color, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Cards.bg(Col.alpha(color, 0.18f), Cards.dp(c, 20)));
        t.setPadding(Cards.dp(c, 10), Cards.dp(c, 4), Cards.dp(c, 10), Cards.dp(c, 4));
        return t;
    }

    /** A quiet capsule: neutral fill, dim text. For facts that need no colour (a plug type, an app name). */
    static TextView tag(Context c, CharSequence text, float sp) {
        TextView t = Cards.tv(c, text, sp, Cards.SUB, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Cards.bg(Cards.TILE, Cards.dp(c, 20)));
        t.setPadding(Cards.dp(c, 10), Cards.dp(c, 4), Cards.dp(c, 10), Cards.dp(c, 4));
        return t;
    }

    /** A tonal capsule with a small solid dot in front: connected, on, off. Child 1 is the label ({@link #chipText}). */
    static LinearLayout dotChip(Context c, CharSequence text, int color, float sp) {
        LinearLayout r = Cards.row(c);
        r.setBackground(Cards.bg(Col.alpha(color, 0.18f), Cards.dp(c, 20)));
        r.setPadding(Cards.dp(c, 10), Cards.dp(c, 4), Cards.dp(c, 12), Cards.dp(c, 4));
        View dot = new View(c);
        dot.setBackground(Cards.bg(color, Cards.dp(c, 6)));
        r.addView(dot, Cards.lp(Cards.dp(c, 6), Cards.dp(c, 6)));
        TextView t = Cards.tv(c, text, sp, color, true);
        t.setPadding(Cards.dp(c, 6), 0, 0, 0);
        r.addView(t);
        return r;
    }

    static TextView chipText(LinearLayout dotChip) { return (TextView) dotChip.getChildAt(1); }

    /** A round badge holding one glyph: tonal (colour at low strength, coloured glyph) or solid (colour, on-colour glyph). */
    static IconView badge(Context c, int glyph, int color, int sizePx, boolean solid) {
        IconView ic = new IconView(c, glyph, solid ? Col.onColor(color) : color);
        int pad = Math.round(sizePx * 0.26f);
        ic.setPadding(pad, pad, pad, pad);
        ic.setBackground(Cards.bg(solid ? color : Col.alpha(color, 0.20f), sizePx));
        return ic;
    }
}

/** A flat segmented bar: round solid cells that light up one by one; the cell being reached shows part of the way. */
final class SegBar extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float gap;
    private final int n;
    private float frac, grow = 1f;
    private int color = Color.WHITE;
    private ValueAnimator intro;
    private boolean introPending;

    SegBar(Context c, int segments) {
        super(c);
        n = Math.max(2, segments);
        gap = 2f * c.getResources().getDisplayMetrics().density;
    }

    /** Fill the cells from empty up to the level once, when the card first appears. */
    SegBar intro() {
        grow = 0f;
        introPending = true;
        return this;
    }

    void set(float f, int color) {
        f = f < 0f ? 0f : (f > 1f ? 1f : f);
        if (f == frac && color == this.color) return;
        frac = f;
        this.color = color;
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!introPending) return;
        introPending = false;
        intro = ValueAnimator.ofFloat(0f, 1f);
        intro.setStartDelay(150L);
        intro.setDuration(750L);
        intro.setInterpolator(new DecelerateInterpolator(1.6f));
        intro.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                grow = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        intro.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (intro != null) {
            intro.cancel();
            intro = null;
        }
        grow = 1f;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float seg = (w - gap * (n - 1)) / n;
        if (w <= 0f || h <= 0f || seg <= 0f) return;
        float rad = Math.min(seg, h) / 2f, lit = frac * grow * n;
        int track = Cards.track();
        for (int i = 0; i < n; i++) {
            float part = Math.max(0f, Math.min(1f, lit - i)), x = i * (seg + gap);
            r.set(x, 0f, x + seg, h);
            p.setColor(part <= 0f ? track : (part >= 1f ? color : Col.mix(track, color, part)));
            c.drawRoundRect(r, rad, rad, p);
        }
    }
}

/** A flat ring that fills (or drains) with a glyph in its middle: timers. */
final class GlyphRing extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), gp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float frac;
    private int color = Color.WHITE, glyph = -1;

    GlyphRing(Context c) {
        super(c);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void set(float f, int color, int glyph) {
        f = f < 0f ? 0f : (f > 1f ? 1f : f);
        if (Math.abs(f - frac) < 0.002f && color == this.color && glyph == this.glyph) return;
        frac = f;
        this.color = color;
        this.glyph = glyph;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        if (s <= 0f) return;
        float sw = s * 0.13f, left = (w - s) / 2f, top = (h - s) / 2f;
        p.setStrokeWidth(sw);
        r.set(left + sw / 2f, top + sw / 2f, left + s - sw / 2f, top + s - sw / 2f);
        p.setColor(Cards.track());
        c.drawArc(r, 0f, 360f, false, p);
        if (frac > 0f) {
            p.setColor(color);
            c.drawArc(r, -90f, 360f * frac, false, p);
        }
        if (glyph >= 0) {
            float g = s * 0.42f;
            IconView.glyph(c, gp, glyph, color, left + (s - g) / 2f, top + (s - g) / 2f, g);
        }
    }
}

/**
 * Rings that swell out of an avatar and fade while a call rings (or one still ring while on a call). Draws only while the
 * view is shown with the display on, at about 30 frames a second.
 */
final class PulseRing extends View {
    private static final long PERIOD_MS = 1800L, FRAME_MS = 33L;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d, inner;
    private final int color;
    private final boolean animate;
    private boolean dark;

    /** @param inner the avatar's share of this view's width (0..1): the rings start at its edge */
    PulseRing(Context c, int color, boolean animate, float inner) {
        super(c);
        this.color = color;
        this.animate = animate;
        this.inner = inner;
        d = c.getResources().getDisplayMetrics().density;
        p.setStyle(Paint.Style.STROKE);
    }

    @Override public void onScreenStateChanged(int state) {
        super.onScreenStateChanged(state);
        dark = state == View.SCREEN_STATE_OFF;
        if (!dark) invalidate();
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (visible) invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h), cx = w / 2f, cy = h / 2f;
        if (s <= 0f) return;
        float r0 = s * inner / 2f, r1 = s / 2f, sw = 2f * d;
        p.setStrokeWidth(sw);
        if (!animate) {
            p.setColor(color);
            c.drawCircle(cx, cy, r1 - sw / 2f, p);
            return;
        }
        float t0 = (SystemClock.uptimeMillis() % PERIOD_MS) / (float) PERIOD_MS;
        for (int k = 0; k < 2; k++) {
            float t = (t0 + k * 0.5f) % 1f;
            p.setColor(Col.alpha(color, 0.6f * (1f - t)));
            c.drawCircle(cx, cy, r0 + (r1 - r0 - sw / 2f) * t, p);
        }
        if (!dark && isShown()) postInvalidateDelayed(FRAME_MS);
    }
}

/** A few pips with one lit: the ringer mode (ring, vibrate, silent). */
final class PipsView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int n;
    private int on, color = Color.WHITE;

    PipsView(Context c, int n) {
        super(c);
        this.n = Math.max(2, n);
    }

    void set(int on, int color) {
        if (on == this.on && color == this.color) return;
        this.on = on;
        this.color = color;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0f || h <= 0f) return;
        float rad = h / 2f, step = (w - h) / (n - 1);
        for (int i = 0; i < n; i++) {
            boolean lit = i == on;
            p.setColor(lit ? color : Cards.track());
            c.drawCircle(rad + i * step, rad, lit ? rad : rad * 0.62f, p);
        }
    }
}
