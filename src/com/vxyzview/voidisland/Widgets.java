package com.vxyzview.voidisland;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/** Small custom views used inside the island. All draw on the render thread (hardware canvas), no layout work per frame. */

/** Rounded / circular image or a coloured tile with a letter. */
final class RoundImage extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Matrix m = new Matrix();
    private Bitmap bmp;
    private BitmapShader sh;
    private float rad = -1f;
    private int fill = 0;
    private String letter;

    RoundImage(Context c) {
        super(c);
        tp.setColor(Color.WHITE);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    /** radiusPx < 0 means a full circle. */
    void set(Bitmap b, float radiusPx) {
        bmp = b;
        rad = radiusPx;
        sh = null;
        rebuild();
        invalidate();
    }

    void setLetter(String l, int fillColor) {
        letter = l;
        fill = fillColor;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    private void rebuild() {
        if (bmp == null || getWidth() == 0 || getHeight() == 0) return;
        sh = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float s = Math.max((float) getWidth() / bmp.getWidth(), (float) getHeight() / bmp.getHeight());
        m.setScale(s, s);
        m.postTranslate((getWidth() - bmp.getWidth() * s) / 2f, (getHeight() - bmp.getHeight() * s) / 2f);
        sh.setLocalMatrix(m);
    }

    @Override protected void onDraw(Canvas c) {
        r.set(0, 0, getWidth(), getHeight());
        float rr = rad < 0 ? Math.min(getWidth(), getHeight()) / 2f : rad;
        if (bmp != null && sh != null) {
            p.setShader(sh);
            p.setColor(Color.WHITE);
            c.drawRoundRect(r, rr, rr, p);
            p.setShader(null);
        } else {
            p.setColor(fill != 0 ? fill : Cards.TILE);
            c.drawRoundRect(r, rr, rr, p);
            if (letter != null) {
                tp.setTextSize(getHeight() * 0.45f);
                c.drawText(letter, getWidth() / 2f, getHeight() / 2f - (tp.ascent() + tp.descent()) / 2f, tp);
            }
        }
    }
}

/** Animated music bars. Runs at ~30fps only while visible and playing. */
final class EqView extends View {
    private static final float[] SP = {5.1f, 7.3f, 6.2f, 8.4f};
    private static final float[] PH = {0f, 1.3f, 2.6f, 0.7f};
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean playing = true;
    private int color = Color.WHITE;

    EqView(Context c) { super(c); }

    void setPlaying(boolean b) { playing = b; invalidate(); }

    void setColor(int c) { color = c; invalidate(); }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (visible) invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        int n = 4;
        float w = getWidth(), h = getHeight(), bw = w / (n * 2f - 1f);
        float t = SystemClock.uptimeMillis() / 1000f;
        p.setColor(color);
        for (int i = 0; i < n; i++) {
            float f = playing ? 0.28f + 0.72f * (0.5f + 0.5f * (float) Math.sin(t * SP[i] + PH[i])) : 0.22f;
            float bh = Math.max(bw, h * f);
            float x = i * bw * 2f;
            r.set(x, (h - bh) / 2f, x + bw, (h + bh) / 2f);
            c.drawRoundRect(r, bw / 2f, bw / 2f, p);
        }
        if (playing && isShown()) postInvalidateDelayed(33);
    }
}

/** Progress / seek bar. Thickens while dragging like the iOS one. */
final class SeekView extends View {
    interface Listener { void onSeek(float frac); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private float frac, dragFrac;
    private boolean dragging, interactive = true;
    private int color = Color.WHITE;
    private Listener l;

    SeekView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    void setColor(int c) { color = c; invalidate(); }

    void setListener(Listener x) { l = x; }

    void setInteractive(boolean b) { interactive = b; }

    boolean isDragging() { return dragging; }

    void setFrac(float f) {
        frac = f < 0 ? 0 : (f > 1 ? 1 : f);
        if (!dragging) invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), th = (dragging ? 9f : 5f) * d, y = h / 2f;
        r.set(0, y - th / 2f, w, y + th / 2f);
        p.setColor(Cards.track());
        c.drawRoundRect(r, th / 2f, th / 2f, p);
        float f = dragging ? dragFrac : frac;
        r.set(0, y - th / 2f, Math.max(th, w * f), y + th / 2f);
        p.setColor(color);
        c.drawRoundRect(r, th / 2f, th / 2f, p);
    }

    private float fx(MotionEvent e) {
        float f = e.getX() / Math.max(1, getWidth());
        return f < 0 ? 0 : (f > 1 ? 1 : f);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!interactive) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                dragFrac = fx(e);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                dragFrac = fx(e);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                frac = dragFrac;
                if (l != null) l.onSeek(dragFrac);
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                invalidate();
                return true;
            default:
                return true;
        }
    }
}

/** Circle that draws itself, then a tick (or a cross when the payment failed). */
final class CheckView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int color;
    private final boolean ok;
    private float t;
    private ValueAnimator va;

    CheckView(Context c, int color, boolean ok) {
        super(c);
        this.color = color;
        this.ok = ok;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        t = 0f;
        va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(820);
        va.setStartDelay(260);
        va.setInterpolator(new DecelerateInterpolator(1.5f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                t = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        va.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (va != null) va.cancel();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h), sw = s * 0.085f;
        p.setStrokeWidth(sw);
        p.setColor(color);
        r.set((w - s) / 2f + sw, (h - s) / 2f + sw, (w + s) / 2f - sw, (h + s) / 2f - sw);
        c.drawArc(r, -90f, 360f * Math.min(1f, t / 0.6f), false, p);
        float b = Math.max(0f, (t - 0.5f) / 0.5f);
        if (b <= 0f) return;
        float cx = w / 2f, cy = h / 2f;
        if (ok) {
            float x0 = cx - s * 0.22f, y0 = cy + s * 0.02f, x1 = cx - s * 0.06f, y1 = cy + s * 0.18f, x2 = cx + s * 0.24f, y2 = cy - s * 0.14f;
            float a = Math.min(1f, b / 0.4f), z = Math.max(0f, (b - 0.4f) / 0.6f);
            c.drawLine(x0, y0, x0 + (x1 - x0) * a, y0 + (y1 - y0) * a, p);
            if (z > 0f) c.drawLine(x1, y1, x1 + (x2 - x1) * z, y1 + (y2 - y1) * z, p);
        } else {
            float k = s * 0.19f;
            c.drawLine(cx - k, cy - k, cx - k + 2 * k * b, cy - k + 2 * k * b, p);
            c.drawLine(cx + k, cy - k, cx + k - 2 * k * b, cy - k + 2 * k * b, p);
        }
    }
}

/** Circular progress (compact download indicator). */
final class RingView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float frac;
    private int color = Color.WHITE;

    RingView(Context c) {
        super(c);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void set(float f, int c) {
        frac = f < 0 ? 0 : (f > 1 ? 1 : f);
        color = c;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h), sw = s * 0.14f;
        p.setStrokeWidth(sw);
        r.set((w - s) / 2f + sw / 2f, (h - s) / 2f + sw / 2f, (w + s) / 2f - sw / 2f, (h + s) / 2f - sw / 2f);
        p.setColor(Cards.track());
        c.drawArc(r, 0f, 360f, false, p);
        p.setColor(color);
        c.drawArc(r, -90f, 360f * frac, false, p);
    }
}

/** Battery that fills up when it appears. */
final class BatteryView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int level, color;
    private boolean charging;
    private float anim;
    private ValueAnimator va;

    BatteryView(Context c, int level, boolean charging, int color) {
        super(c);
        this.level = level;
        this.charging = charging;
        this.color = color;
    }

    void set(int level, boolean charging, int color) {
        this.level = level;
        this.charging = charging;
        this.color = color;
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = 0f;
        va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(900);
        va.setStartDelay(220);
        va.setInterpolator(new DecelerateInterpolator(1.8f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                anim = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        va.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (va != null) va.cancel();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), nub = w * 0.07f, bodyW = w - nub - 2f, st = h * 0.09f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(st);
        p.setColor(Col.alpha(Cards.WHITE, 0.5f));
        r.set(st / 2f, st / 2f, bodyW - st / 2f, h - st / 2f);
        c.drawRoundRect(r, h * 0.28f, h * 0.28f, p);
        p.setStyle(Paint.Style.FILL);
        r.set(bodyW + 2f, h * 0.33f, w, h * 0.67f);
        c.drawRoundRect(r, nub / 2f, nub / 2f, p);
        float in = st * 1.7f, fw = Math.max(h * 0.2f, (bodyW - 2f * in) * (level / 100f) * anim);
        p.setColor(color);
        r.set(in, in, in + fw, h - in);
        c.drawRoundRect(r, h * 0.17f, h * 0.17f, p);
        if (charging) {
            float g = h * 0.62f;
            IconView.glyph(c, p, IconView.BOLT, Col.onColor(color), bodyW / 2f - g / 2f, h / 2f - g / 2f, g);
        }
    }
}

/**
 * The compact pill's lyric window: shows ONE lyric line on a single row. A line that fits just sits there;
 * a line wider than the pill slides sideways (marquee, reading direction) so it can be read to the end.
 * The view keeps its own clock from the last known song position, so the slide moves every frame and the line
 * changes exactly when its timestamp arrives, however rarely the island refreshes it.
 */
final class LyricScroll extends View {
    private static final float LINE = 1.34f;      // the window is roughly this multiple of the font size tall
    private final TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Lyrics.Loaded l;
    private float[] lineW = new float[0];         // measured single-row width of every line
    private int rows, windowH, maxW, viewW, widest, padW, fadeLen;
    private int colHot;
    private int active = -1;                      // line on screen (-1 = nothing playing yet)
    private float off;                            // how far the text has slid to the left, in px
    private float overflow;                       // how far it has to slide in total (0 = the line fits)
    private float baseline;

    // the song clock: position at the moment of the last re-anchor, and how it advances
    private long anchorPos, anchorAt, durMs;
    private boolean playing, anchored;
    private float speed = 1f;
    // the active line's slide: it starts after "wait" ms and takes "scrollMs" ms, counted from lineStart
    private long lineStart, wait, scrollMs;

    private final Runnable frame = new Runnable() {
        @Override public void run() { advance(); }
    };

    LyricScroll(Context c) {
        super(c);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setHorizontalFadingEdgeEnabled(true);
        fadeLen = Math.round(12f * c.getResources().getDisplayMetrics().density);
        setFadingEdgeLength(fadeLen);
    }

    /** Bind to a song (or null to clear); the text box and colours may change any time without a rebuild. */
    void bind(Lyrics.Loaded l, int maxW, int hot, int dim, float sp) {
        colHot = hot;
        float px = sp * getResources().getDisplayMetrics().scaledDensity;
        if (this.l == l && this.maxW == maxW && p.getTextSize() == px) { invalidate(); return; }
        this.l = l;
        this.maxW = maxW;
        padW = Math.round(36f * getResources().getDisplayMetrics().density);
        p.setTextSize(px);
        windowH = Math.round(px * LINE);
        Paint.FontMetrics fm = p.getFontMetrics();
        baseline = (windowH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
        removeCallbacks(frame);
        animate().cancel();
        setAlpha(1f);
        active = -1;
        anchored = false;
        off = 0;
        overflow = 0;
        rows = 0;
        widest = 0;
        lineW = new float[0];
        if (l != null && l.line.length > 0) {
            rows = l.line.length;
            lineW = new float[rows];
            for (int i = 0; i < rows; i++) {
                lineW[i] = p.measureText(l.line[i]);
                int w = (int) Math.ceil(lineW[i]);
                if (w > widest) widest = w;
            }
        }
        viewW = Math.min(Math.max(widest, padW), Math.max(maxW, padW));
        invalidate();
        requestLayout();
    }

    /**
     * The island's periodic report of the song position. The view keeps counting by itself between reports and only
     * re-anchors when the report disagrees with its own clock (a seek, a pause, a different speed).
     */
    void tick(long pos, long durMs, boolean playing, float speed) {
        float sp = speed <= 0f ? 1f : speed;
        boolean same = anchored && playing == this.playing && this.durMs == durMs && sp == this.speed;
        if (!(same && playing && Math.abs(pos - clock()) < 150L)) {
            anchorPos = pos;
            anchorAt = SystemClock.elapsedRealtime();
            this.durMs = durMs;
            this.playing = playing;
            this.speed = sp;
            anchored = true;
        }
        advance();
    }

    /** The song position right now, by our own clock. */
    private long clock() {
        long pos = playing ? anchorPos + (long) ((SystemClock.elapsedRealtime() - anchorAt) * speed) : anchorPos;
        if (durMs > 0 && pos > durMs) pos = durMs;
        return pos < 0 ? 0 : pos;
    }

    /** Re-evaluate the line and the slide at the current clock, then arrange the next wake-up. */
    private void advance() {
        removeCallbacks(frame);
        if (!anchored) return;
        long pos = clock();
        int idx = l == null ? -1 : l.indexAt(pos, durMs);
        if (idx != active) newLine(idx, pos);
        boolean sliding = false;
        if (idx >= 0 && idx < rows && overflow > 0f) {
            float f = fraction(pos);
            float to = overflow * f;
            if (to != off) { off = to; invalidate(); }
            sliding = f < 1f;
        }
        if (!playing || l == null || !isAttachedToWindow()) return;
        if (sliding) { postOnAnimation(frame); return; }
        long next = l.nextStartMs(pos, durMs);
        if (next >= 0) postDelayed(frame, Math.max(16L, (long) ((next - pos) / speed)));
    }

    /** A new line arrives: fade it in and work out how its slide is paced. */
    private void newLine(int idx, long pos) {
        active = idx;
        animate().cancel();
        setAlpha(1f);
        off = 0;
        overflow = 0;
        if (idx < 0 || idx >= rows) { invalidate(); return; }
        int w = getWidth() > 0 ? getWidth() : viewW;
        overflow = Math.max(0f, lineW[idx] - w);
        setAlpha(0f);
        animate().alpha(1f).setDuration(200).start();
        long span = lineSpan(idx);
        lineStart = l.synced ? l.at[idx] : (rows > 0 && durMs > 0 ? durMs * idx / rows : 0L);
        // a line is sung in roughly 75 ms per character, not over the whole gap to the next one: pace the slide to that
        long sing = Math.min(span, Math.max(1800L, l.line[idx].length() * 75L));
        wait = Math.min(450L, sing / 5);
        float dens = getResources().getDisplayMetrics().density;
        float minDur = overflow / (0.35f * dens);                 // never faster than ~350dp/s
        float maxDur = overflow / (0.03f * dens);                 // never slower than ~30dp/s
        float dur = Math.min(Math.max(sing * 0.85f - wait, minDur), maxDur);
        scrollMs = Math.max(400L, (long) dur);
        invalidate();
    }

    /** How far through its slide the line is at song position {@code pos} (0 = start, 1 = end). */
    private float fraction(long pos) {
        float f = (pos - lineStart - wait) / (float) scrollMs;
        return f < 0f ? 0f : (f > 1f ? 1f : f);
    }

    /** How long (ms) the line stays on screen: until the next stamp, or an even share of the song for plain text. */
    private long lineSpan(int idx) {
        long s;
        if (l.synced) {
            long end = idx + 1 < l.at.length ? l.at[idx + 1] : (durMs > l.at[idx] ? durMs : l.at[idx] + 5000);
            s = end - l.at[idx];
        } else {
            s = durMs > 0 && rows > 0 ? durMs / rows : 4000;
        }
        return Math.max(1500L, Math.min(s, 20000L));
    }

    @Override protected float getLeftFadingEdgeStrength() {
        return overflow > 0f && off > 0f ? Math.min(1f, off / fadeLen) : 0f;
    }

    @Override protected float getRightFadingEdgeStrength() {
        float left = overflow - off;
        return overflow > 0f && left > 0f ? Math.min(1f, left / fadeLen) : 0f;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        GradientDrawable g = new GradientDrawable();   // outline shape only: the rounded clip lives in the outline, no per-frame paths
        g.setColor(0);
        g.setCornerRadius(h / 2f);
        setBackground(g);
        setClipToOutline(true);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = Math.max(1, viewW > 0 ? viewW : padW);
        setMeasuredDimension(resolveSizeAndState(w, wSpec, 0), resolveSizeAndState(Math.max(1, windowH), hSpec, 0));
    }

    @Override protected void onDraw(Canvas c) {
        if (active < 0 || active >= rows || getWidth() == 0) return;
        p.setColor(colHot);
        c.drawText(l.line[active], -off, baseline, p);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        advance();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(frame);
        animate().cancel();
        setAlpha(1f);
    }
}

/** Small analog clock for the idle pill and the clock card: a quiet ring, an hour and a minute hand, an accent second hand when asked. */
final class ClockView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private int hour, min, sec;
    private boolean secs;
    private int ring = 0x55FFFFFF, hand = Color.WHITE, accent = Color.WHITE;

    ClockView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void setColors(int ring, int hand, int accent) { this.ring = ring; this.hand = hand; this.accent = accent; invalidate(); }

    /** @param withSeconds draw the second hand (the card ticks every second; the pill only every minute) */
    void set(int h, int m, int s, boolean withSeconds) {
        if (h == hour && m == min && s == sec && withSeconds == secs) return;
        hour = h; min = m; sec = s; secs = withSeconds;
        invalidate();
    }

    private void line(Canvas c, float cx, float cy, float deg, float from, float to, float w, int col) {
        double a = Math.toRadians(deg - 90f);
        float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
        p.setColor(col);
        p.setStrokeWidth(w);
        c.drawLine(cx + cs * from, cy + sn * from, cx + cs * to, cy + sn * to, p);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;
        float rw = Math.max(1.2f * d, r * 0.09f);
        float R = r - rw / 2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(rw);
        p.setColor(ring);
        c.drawCircle(cx, cy, R, p);
        if (r > 18f * d) {   // big enough for the 12 / 3 / 6 / 9 marks
            for (int i = 0; i < 12; i++) {
                boolean q = i % 3 == 0;
                line(c, cx, cy, i * 30f, R - (q ? 0.20f : 0.12f) * R, R - 0.05f * R, q ? rw * 0.9f : rw * 0.5f, q ? hand : ring);
            }
        }
        float mAng = (min + sec / 60f) * 6f, hAng = ((hour % 12) + min / 60f) * 30f;
        line(c, cx, cy, hAng, 0f, R * 0.50f, Math.max(1.6f * d, r * 0.11f), hand);
        line(c, cx, cy, mAng, 0f, R * 0.74f, Math.max(1.3f * d, r * 0.08f), accent);
        if (secs) line(c, cx, cy, sec * 6f, -R * 0.14f, R * 0.84f, Math.max(0.9f * d, r * 0.035f), accent);
        p.setStyle(Paint.Style.FILL);
        p.setColor(accent);
        c.drawCircle(cx, cy, Math.max(1.6f * d, r * 0.075f), p);
    }
}

/** Weather glyph drawn with plain shapes (sun, moon, clouds, rain, snow, bolt): no image files. */
final class WxView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path(), cut = new Path();
    private int kind = Wx.CLOUDY;
    private boolean day = true;
    private int cloud = Color.WHITE;

    WxView(Context c) {
        super(c);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void set(int kind, boolean day, int cloudColor) {
        this.kind = kind; this.day = day; this.cloud = cloudColor;
        invalidate();
    }

    private void cloud(Canvas c, float dx, float dy, int col) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        c.drawCircle(36f + dx, 58f + dy, 15f, p);
        c.drawCircle(55f + dx, 48f + dy, 20f, p);
        c.drawCircle(73f + dx, 60f + dy, 13f, p);
        r.set(22f + dx, 58f + dy, 86f + dx, 74f + dy);
        c.drawRoundRect(r, 8f, 8f, p);
    }

    private void sun(Canvas c, float cx, float cy, float rad) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFC83D);
        c.drawCircle(cx, cy, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(rad * 0.28f);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            c.drawLine(cx + cs * rad * 1.45f, cy + sn * rad * 1.45f, cx + cs * rad * 1.95f, cy + sn * rad * 1.95f, p);
        }
    }

    private void moon(Canvas c, float cx, float cy, float rad) {
        path.reset();
        path.addCircle(cx, cy, rad, Path.Direction.CW);
        cut.reset();
        cut.addCircle(cx + rad * 0.55f, cy - rad * 0.45f, rad * 0.85f, Path.Direction.CW);
        path.op(cut, Path.Op.DIFFERENCE);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFDCE3FF);
        c.drawPath(path, p);
    }

    @Override protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 100f;
        c.save();
        c.translate((getWidth() - 100f * s) / 2f, (getHeight() - 100f * s) / 2f);
        c.scale(s, s);
        switch (kind) {
            case Wx.CLEAR:
                if (day) sun(c, 50f, 50f, 17f); else moon(c, 50f, 50f, 26f);
                break;
            case Wx.PARTLY:
                if (day) sun(c, 36f, 36f, 12f); else moon(c, 36f, 36f, 18f);
                cloud(c, 4f, 4f, cloud);
                break;
            case Wx.CLOUDY:
                cloud(c, -8f, -8f, Col.alpha(cloud, 0.5f));
                cloud(c, 4f, 4f, cloud);
                break;
            case Wx.FOG:
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(7f);
                p.setColor(Col.alpha(cloud, 0.85f));
                c.drawLine(20f, 38f, 80f, 38f, p);
                c.drawLine(28f, 54f, 88f, 54f, p);
                c.drawLine(20f, 70f, 72f, 70f, p);
                break;
            case Wx.DRIZZLE:
            case Wx.RAIN:
                cloud(c, 0f, -12f, cloud);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFF58B8FF);
                p.setStrokeWidth(6f);
                float L = kind == Wx.RAIN ? 14f : 8f;
                for (int i = 0; i < 3; i++) c.drawLine(34f + i * 17f, 70f, 30f + i * 17f, 70f + L, p);
                break;
            case Wx.SNOW:
                cloud(c, 0f, -12f, cloud);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                for (int i = 0; i < 3; i++) c.drawCircle(34f + i * 17f, 78f + (i == 1 ? 6f : 0f), 4.6f, p);
                break;
            default:   // storm
                cloud(c, 0f, -12f, Col.alpha(cloud, 0.8f));
                path.reset();
                path.moveTo(54f, 58f);
                path.lineTo(40f, 80f);
                path.lineTo(51f, 80f);
                path.lineTo(46f, 98f);
                path.lineTo(66f, 72f);
                path.lineTo(55f, 72f);
                path.lineTo(61f, 58f);
                path.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFC83D);
                c.drawPath(path, p);
        }
        c.restore();
    }
}
