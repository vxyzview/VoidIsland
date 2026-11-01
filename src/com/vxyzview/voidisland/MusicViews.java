package com.vxyzview.voidisland;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/*
 * Views for the extra music designs (compact pill + expanded card). Like EqView they animate only while they are visible,
 * the song is playing and the display is on, at about 25 fps, and a paused song draws once and then sleeps.
 */

/**
 * A record that spins while the song plays: dark disc, fine grooves, two soft highlights that turn with it and the album art as
 * its label. With {@link #tonearm} (the big card) a tonearm swings onto the record when the song plays and lifts off when it
 * pauses.
 */
final class VinylView extends View {
    private static final float DEG_PER_MS = 0.06f;   // 60 degrees a second: a turn every six seconds, calm rather than frantic
    private static final long FRAME_MS = 40L;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Matrix m = new Matrix();
    private final float d;
    private Bitmap bmp;
    private BitmapShader sh;
    private boolean playing, dark, tonearm;
    private int accent = Color.WHITE;
    private float angle, armT;            // armT: 0 = lifted, 1 = resting on the record
    private long last;
    // geometry, worked out on size change
    private float cx, cy, rad, labelR, px, py, armLen;

    VinylView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    /** {@code withTonearm}: leave room for the arm (the view is then drawn as a square with the record bottom-left). */
    void config(boolean withTonearm, int accent) {
        this.tonearm = withTonearm;
        this.accent = accent;
        geometry();
        invalidate();
    }

    void setAccent(int accent) {
        if (this.accent == accent) return;
        this.accent = accent;
        invalidate();
    }

    void set(Bitmap b) {
        if (b == bmp && (sh != null || b == null)) return;   // same picture again: no new shader
        bmp = b;
        sh = null;
        shader();
        invalidate();
    }

    void setPlaying(boolean on) {
        if (on == playing) return;
        playing = on;
        last = SystemClock.uptimeMillis();
        invalidate();   // wake the loop
    }

    @Override public void onScreenStateChanged(int state) {
        super.onScreenStateChanged(state);
        dark = state == View.SCREEN_STATE_OFF;
        if (!dark) { last = SystemClock.uptimeMillis(); invalidate(); }
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (visible) { last = SystemClock.uptimeMillis(); invalidate(); }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        geometry();
        shader();
    }

    private void geometry() {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        if (s <= 0f) return;
        if (tonearm) {
            rad = s * 0.43f;
            cx = s * 0.44f;
            cy = s * 0.56f;
            px = s * 0.93f;     // the arm's pivot, top right
            py = s * 0.08f;
            armLen = s * 0.342f;
        } else {
            rad = s / 2f - 0.6f * d;
            cx = w / 2f;
            cy = h / 2f;
        }
        labelR = rad * (tonearm ? 0.42f : 0.46f);
    }

    private void shader() {
        if (bmp == null || labelR <= 0f) return;
        sh = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float side = labelR * 2f, sc = Math.max(side / bmp.getWidth(), side / bmp.getHeight());
        m.setScale(sc, sc);
        m.postTranslate(cx - bmp.getWidth() * sc / 2f, cy - bmp.getHeight() * sc / 2f);
        sh.setLocalMatrix(m);
    }

    @Override protected void onDraw(Canvas c) {
        if (rad <= 0f) return;
        long now = SystemClock.uptimeMillis();
        long dt = Math.min(100L, Math.max(0L, now - last));
        last = now;
        if (playing) angle = (angle + dt * DEG_PER_MS) % 360f;
        float armTarget = playing ? 1f : 0f;
        if (armT != armTarget) {
            float step = dt / 380f;
            armT = armT < armTarget ? Math.min(armTarget, armT + step) : Math.max(armTarget, armT - step);
        }
        int ink = Cards.WHITE;

        c.save();
        c.rotate(angle, cx, cy);
        // the disc
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(0xFF19191C);
        c.drawCircle(cx, cy, rad, p);
        // fine grooves between the label and the rim
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(0.5f, 0.55f * d));
        p.setColor(Col.alpha(0xFFFFFFFF, 0.075f));
        for (int i = 1; i <= 4; i++) c.drawCircle(cx, cy, labelR + (rad * 0.93f - labelR) * i / 5f, p);
        // two soft highlights, opposite each other, that turn with the record
        p.setStyle(Paint.Style.FILL);
        p.setColor(Col.alpha(0xFFFFFFFF, 0.07f));
        r.set(cx - rad * 0.96f, cy - rad * 0.96f, cx + rad * 0.96f, cy + rad * 0.96f);
        c.drawArc(r, -62f, 34f, true, p);
        c.drawArc(r, 118f, 34f, true, p);
        // the label: the album art (or a tile of the accent when there is none)
        if (bmp != null && sh != null) {
            p.setShader(sh);
            p.setColor(Color.WHITE);
            c.drawCircle(cx, cy, labelR, p);
            p.setShader(null);
        } else {
            p.setColor(accent);
            c.drawCircle(cx, cy, labelR, p);
        }
        // the spindle hole
        p.setColor(0xFF0B0B0D);
        c.drawCircle(cx, cy, Math.max(1f * d, rad * 0.055f), p);
        c.restore();

        // a thin rim so the record reads on a black island
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(0.8f, 0.9f * d));
        p.setColor(Col.alpha(ink, 0.32f));
        c.drawCircle(cx, cy, rad, p);

        if (tonearm) drawArm(c, ink);

        boolean moving = playing || armT != armTarget;
        if (moving && !dark && isShown()) postInvalidateDelayed(FRAME_MS);
    }

    private void drawArm(Canvas c, int ink) {
        // rest (84 degrees, beside the record) to playing (133 degrees, the stylus out on the grooves), clockwise from "right"
        double a = Math.toRadians(84.0 + 49.0 * armT);
        float hx = px + armLen * (float) Math.cos(a), hy = py + armLen * (float) Math.sin(a);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.6f * d, rad * 0.05f));
        p.setColor(Col.alpha(ink, 0.88f));
        c.drawLine(px, py, hx, hy, p);
        // the head shell: a short, thicker piece at the end of the arm with an accent stylus
        float ux = (float) Math.cos(a), uy = (float) Math.sin(a), sh0 = armLen * 0.17f;
        p.setStrokeWidth(Math.max(2.8f * d, rad * 0.09f));
        p.setColor(ink);
        c.drawLine(hx, hy, hx + ux * sh0, hy + uy * sh0, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(accent);
        c.drawCircle(hx + ux * sh0, hy + uy * sh0, Math.max(1.3f * d, rad * 0.04f), p);
        // the pivot
        p.setColor(Col.alpha(ink, 0.95f));
        c.drawCircle(px, py, Math.max(3f * d, rad * 0.1f), p);
        p.setColor(accent);
        c.drawCircle(px, py, Math.max(1.4f * d, rad * 0.045f), p);
    }
}

/**
 * A wide, smooth waveform: many thin bars whose heights roll like a sound wave, tallest in the middle and fading toward the
 * ends. The compact "Wave" pill uses it where the other styles have four bars.
 */
final class WaveView extends View {
    private static final float[] SP = {3.1f, 4.4f, 3.7f, 5.2f, 2.8f, 4.9f};
    private static final long FRAME_MS = 50L;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private boolean playing = true, dark;
    private int color = Color.WHITE;

    WaveView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    void setPlaying(boolean b) {
        playing = b;
        invalidate();
    }

    void setColor(int c) {
        color = c;
        invalidate();
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
        float w = getWidth(), h = getHeight();
        if (w <= 0f || h <= 0f) return;
        float bw = 1.8f * d, gap = 1.7f * d;
        int n = Math.max(5, (int) ((w + gap) / (bw + gap)));
        float used = n * bw + (n - 1) * gap, x0 = (w - used) / 2f;
        float t = SystemClock.uptimeMillis() / 1000f;
        for (int i = 0; i < n; i++) {
            float pos = (i + 0.5f) / n;
            float env = 0.30f + 0.70f * (float) Math.sin(Math.PI * pos);          // tallest in the middle
            float f = playing ? 0.22f + 0.78f * (0.5f + 0.5f * (float) Math.sin(t * SP[i % SP.length] - i * 0.62f)) : 0.16f;
            float bh = Math.max(bw, h * env * f);
            float x = x0 + i * (bw + gap);
            p.setColor(Col.alpha(color, 0.45f + 0.55f * env));
            r.set(x, (h - bh) / 2f, x + bw, (h + bh) / 2f);
            c.drawRoundRect(r, bw / 2f, bw / 2f, p);
        }
        if (playing && !dark && isShown()) postInvalidateDelayed(FRAME_MS);
    }
}

/** The album art as a circle with the song's progress drawn around it: a faint track and an accent arc that fills as it plays. */
final class ArtRing extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Matrix m = new Matrix();
    private final float d;
    private Bitmap bmp;
    private BitmapShader sh;
    private float frac;
    private int color = Color.WHITE;

    ArtRing(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void set(Bitmap b) {
        if (b == bmp && (sh != null || b == null)) return;
        bmp = b;
        sh = null;
        shader();
        invalidate();
    }

    /** Song position as 0..1 and the arc colour; repaints only when the arc would move by about a pixel. */
    void progress(float f, int color) {
        f = f < 0f ? 0f : (f > 1f ? 1f : f);
        if (Math.abs(f - frac) < 0.002f && color == this.color) return;
        frac = f;
        this.color = color;
        invalidate();
    }

    private float stroke() { return Math.max(1.5f * d, Math.min(getWidth(), getHeight()) * 0.085f); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        shader();
    }

    private void shader() {
        float s = Math.min(getWidth(), getHeight());
        if (bmp == null || s <= 0f) return;
        float rad = s / 2f - stroke() - 1f * d, side = Math.max(2f, rad * 2f);
        sh = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float sc = Math.max(side / bmp.getWidth(), side / bmp.getHeight());
        m.setScale(sc, sc);
        m.postTranslate(getWidth() / 2f - bmp.getWidth() * sc / 2f, getHeight() / 2f - bmp.getHeight() * sc / 2f);
        sh.setLocalMatrix(m);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h), cx = w / 2f, cy = h / 2f, sw = stroke();
        float art = s / 2f - sw - 1f * d;
        p.setStyle(Paint.Style.FILL);
        if (bmp != null && sh != null) {
            p.setShader(sh);
            p.setColor(Color.WHITE);
            c.drawCircle(cx, cy, art, p);
            p.setShader(null);
        } else {
            p.setColor(Cards.TILE);
            c.drawCircle(cx, cy, art, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        float rr = s / 2f - sw / 2f;
        r.set(cx - rr, cy - rr, cx + rr, cy + rr);
        p.setColor(Cards.track());
        c.drawArc(r, 0f, 360f, false, p);
        p.setColor(color);
        c.drawArc(r, -90f, Math.max(3f, 360f * frac), false, p);   // never empty: a dot marks the start
    }
}

/**
 * A dot-matrix equalizer: a grid of flat dots where each column lights up from the bottom like a level meter. Lit dots take the
 * accent, the rest stay as faint pips. Same rules as the other animated views: it only draws while visible, playing and the
 * display is on, at about 20 fps, and a paused song shows one calm row and sleeps.
 */
final class DotEqView extends View {
    private static final float[] SP = {5.3f, 7.1f, 6.4f, 8.2f, 5.9f, 7.6f};
    private static final float[] PH = {0f, 1.4f, 2.7f, 0.6f, 2.1f, 3.3f};
    private static final long FRAME_MS = 50L;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int cols, rows;
    private boolean playing = true, dark;
    private int color = Color.WHITE;

    DotEqView(Context c, int cols, int rows) {
        super(c);
        this.cols = Math.max(2, cols);
        this.rows = Math.max(2, rows);
    }

    void setPlaying(boolean b) {
        playing = b;
        invalidate();
    }

    void setColor(int c) {
        color = c;
        invalidate();
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
        float w = getWidth(), h = getHeight();
        if (w <= 0f || h <= 0f) return;
        float pitch = Math.min(w / cols, h / rows), dot = pitch * 0.58f;
        float x0 = (w - pitch * cols) / 2f, y0 = (h - pitch * rows) / 2f;
        float t = SystemClock.uptimeMillis() / 1000f;
        int off = Col.alpha(color, 0.22f);
        for (int i = 0; i < cols; i++) {
            float lvl = playing ? 0.2f + 0.8f * (0.5f + 0.5f * (float) Math.sin(t * SP[i % SP.length] + PH[i % PH.length])) : 0f;
            int lit = Math.max(1, Math.round(lvl * rows));
            for (int j = 0; j < rows; j++) {                  // j = 0 is the bottom row
                p.setColor(j < lit ? color : off);
                c.drawCircle(x0 + pitch * (i + 0.5f), y0 + pitch * (rows - j - 0.5f), dot / 2f, p);
            }
        }
        if (playing && !dark && isShown()) postInvalidateDelayed(FRAME_MS);
    }
}

/** A slim, flat progress line (track + accent fill, round ends). It is only for looking at: it never takes a touch, so a tap on the pill still opens the island. */
final class ProgressLine extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float frac;
    private int color = Color.WHITE;

    ProgressLine(Context c) { super(c); }

    /** Song position as 0..1 and the fill colour; repaints only when the line would move by about a pixel. */
    void set(float f, int color) {
        f = f < 0f ? 0f : (f > 1f ? 1f : f);
        if (Math.abs(f - frac) < 0.002f && color == this.color) return;
        frac = f;
        this.color = color;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), rad = h / 2f;
        if (w <= 0f || h <= 0f) return;
        r.set(0f, 0f, w, h);
        p.setColor(Cards.track());
        c.drawRoundRect(r, rad, rad, p);
        r.set(0f, 0f, Math.max(h, w * frac), h);
        p.setColor(color);
        c.drawRoundRect(r, rad, rad, p);
    }
}
