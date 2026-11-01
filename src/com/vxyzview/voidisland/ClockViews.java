package com.vxyzview.voidisland;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import java.util.Calendar;

/*
 * Views for the extra idle-clock designs (compact pill + expanded card). Like the rest of the island they only repaint when the
 * value they were given actually changes, and none of them owns a timer: the island's own tick (once a minute on the pill, once
 * a second on the card) feeds them.
 */

/** Which part of the day it is: 0 night, 1 morning, 2 afternoon, 3 evening. Shared by the sky glyph and the horizon card. */
final class DayPart {
    private DayPart() {}

    static final String[] NAMES = {"NIGHT", "MORNING", "AFTERNOON", "EVENING"};

    static int of(int hour) {
        hour = ((hour % 24) + 24) % 24;
        if (hour >= 5 && hour < 12) return 1;
        if (hour >= 12 && hour < 17) return 2;
        if (hour >= 17 && hour < 21) return 3;
        return 0;
    }

    /** True while the sun is up on the stylised 06:00 - 18:00 day arc. */
    static boolean sunUp(float hourFrac) { return hourFrac >= 6f && hourFrac < 18f; }
}

/** Text whose digits fade from one colour to another, left to right (the "Aura" compact clock). */
final class GradientText extends TextView {
    private int from, to;

    GradientText(Context c) { super(c); }

    void colors(int from, int to) {
        this.from = from;
        this.to = to;
        this.setTextColor(to);
        paintShader(getWidth());
    }

    private void paintShader(int w) {
        if (w <= 0) return;
        getPaint().setShader(new LinearGradient(0f, 0f, w, 0f, from, to, Shader.TileMode.CLAMP));
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        paintShader(w);   // the gradient is as wide as the digits, so it has to follow every text change
    }
}

/** Seven dots for the week: past days dim, today a bigger filled accent dot with a soft halo, days to come as outlines. */
final class WeekDots extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private int today = -1, accent = Color.WHITE;

    WeekDots(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    void style(int accent) {
        this.accent = accent;
        invalidate();
    }

    /** Position of today in the week (0 = first day of the locale's week); repaints only on change. */
    void update(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        int back = (cal.get(Calendar.DAY_OF_WEEK) - cal.getFirstDayOfWeek() + 7) % 7;
        if (back == today) return;
        today = back;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        if (today < 0) return;
        float w = getWidth(), h = getHeight(), cell = w / 7f, cy = h / 2f;
        float base = Math.min(cell * 0.2f, 2.4f * d);
        for (int i = 0; i < 7; i++) {
            float cx = cell * (i + 0.5f);
            if (i == today) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Col.alpha(accent, 0.28f));
                c.drawCircle(cx, cy, base * 2.5f, p);
                p.setColor(accent);
                c.drawCircle(cx, cy, base * 1.6f, p);
            } else if (i < today) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Col.alpha(Cards.WHITE, 0.55f));
                c.drawCircle(cx, cy, base, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, 0.9f * d));
                p.setColor(Col.alpha(Cards.WHITE, 0.4f));
                c.drawCircle(cx, cy, base * 0.85f, p);
            }
        }
    }
}

/** A tiny sun (with rays), a low sun on a horizon, or a crescent moon with a star, chosen from the hour. */
final class SkyGlyph extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), cut = new Path();
    private int hour = -1, sun = 0xFFFFD60A, moon = 0xFFD6E2FF;

    SkyGlyph(Context c) { super(c); }

    void style(int sun, int moon) {
        this.sun = sun;
        this.moon = moon;
        invalidate();
    }

    void hour(int h) {
        if (h == this.hour) return;
        this.hour = h;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        if (hour < 0) return;
        float w = getWidth(), h = getHeight(), s = Math.min(w, h), cx = w / 2f, cy = h / 2f;
        int part = DayPart.of(hour);
        p.setStrokeCap(Paint.Cap.ROUND);
        if (part == 0) {   // night: crescent + one star
            float r = s * 0.34f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(moon);
            crescent(c, cx - s * 0.04f, cy + s * 0.02f, r);
            star(c, cx + s * 0.30f, cy - s * 0.30f, s * 0.085f);
            return;
        }
        if (part == 3) {   // evening: half a sun sinking behind a horizon line, clipped by the line
            float r = s * 0.27f, hy = cy + s * 0.16f;
            c.save();
            c.clipRect(0f, 0f, w, hy);
            p.setStyle(Paint.Style.FILL);
            p.setColor(sun);
            c.drawCircle(cx, hy, r, p);
            c.restore();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.2f, s * 0.07f));
            p.setColor(sun);
            c.drawLine(cx - s * 0.42f, hy, cx + s * 0.42f, hy, p);
            for (int i = -1; i <= 1; i++) {   // three short rays above it
                double a = Math.toRadians(i * 48.0);
                float sx = (float) Math.sin(a), sy = -(float) Math.cos(a);
                c.drawLine(cx + sx * (r + s * 0.08f), hy + sy * (r + s * 0.08f), cx + sx * (r + s * 0.2f), hy + sy * (r + s * 0.2f), p);
            }
            return;
        }
        // morning / afternoon: a full sun. The morning one is a little smaller with shorter rays, the afternoon one is at full blaze.
        float r = s * (part == 1 ? 0.2f : 0.23f), in = r + s * 0.09f, out = in + s * (part == 1 ? 0.1f : 0.14f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(sun);
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.2f, s * 0.07f));
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45.0);
            float sx = (float) Math.sin(a), sy = -(float) Math.cos(a);
            c.drawLine(cx + sx * in, cy + sy * in, cx + sx * out, cy + sy * out, p);
        }
    }

    /** A crescent: a disc with a second, offset disc cut out of it (a real cut-out, so it works on any island colour). */
    private void crescent(Canvas c, float x, float y, float r) {
        path.reset();
        path.addCircle(x, y, r, Path.Direction.CW);
        cut.reset();
        cut.addCircle(x + r * 0.46f, y - r * 0.36f, r * 0.84f, Path.Direction.CW);
        path.op(cut, Path.Op.DIFFERENCE);
        c.drawPath(path, p);
    }

    /** A four-point sparkle. */
    private void star(Canvas c, float x, float y, float r) {
        path.reset();
        path.moveTo(x, y - r);
        path.quadTo(x, y, x + r, y);
        path.quadTo(x, y, x, y + r);
        path.quadTo(x, y, x - r, y);
        path.quadTo(x, y, x, y - r);
        path.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
    }
}

/**
 * Three concentric activity rings that read as a clock: the outer one is how much of the day has gone, the middle one how
 * much of the hour, the inner one the seconds. The three colours are tints of the accent so it follows the theme.
 */
final class RingsView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float day, hr, sec;
    private int c1 = Color.WHITE, c2 = Color.WHITE, c3 = Color.WHITE;

    RingsView(Context c) {
        super(c);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void style(int outer, int middle, int inner) {
        c1 = outer;
        c2 = middle;
        c3 = inner;
        invalidate();
    }

    /** Each value 0..1; repaints only when one moves by at least a pixel's worth. */
    void set(float day, float hour, float second) {
        if (Math.abs(day - this.day) < 0.0005f && Math.abs(hour - this.hr) < 0.0005f && Math.abs(second - this.sec) < 0.0005f) return;
        this.day = day;
        this.hr = hour;
        this.sec = second;
        invalidate();
    }

    private void ring(Canvas c, float cx, float cy, float rad, float sw, float frac, int color) {
        p.setStrokeWidth(sw);
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        p.setColor(Col.alpha(color, 0.2f));
        c.drawArc(r, 0f, 360f, false, p);
        p.setColor(color);
        c.drawArc(r, -90f, Math.max(4f, 360f * frac), false, p);   // never empty: a dot marks the start
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.min(w, h), cx = w / 2f, cy = h / 2f;
        float sw = s * 0.095f, gap = s * 0.03f, r1 = s / 2f - sw / 2f;
        ring(c, cx, cy, r1, sw, day, c1);
        ring(c, cx, cy, r1 - sw - gap, sw, hr, c2);
        ring(c, cx, cy, r1 - 2f * (sw + gap), sw, sec, c3);
    }
}

/** A flip-clock tile: two rounded leaves with the digits across both and a thin gap where they meet. */
final class FlipTile extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path half = new Path();
    private final Typeface face = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final float d;
    private String text = "";
    private String tag = "";
    private int fill = 0x24FFFFFF, ink = Color.WHITE, tagInk = 0xB3FFFFFF;
    private float textSp = 54f;

    FlipTile(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        p.setFontFeatureSettings("tnum");
    }

    void style(int fill, int ink, int tagInk, float textSp) {
        this.fill = fill;
        this.ink = ink;
        this.tagInk = tagInk;
        this.textSp = textSp;
        invalidate();
    }

    /** The digits, plus an optional small label in the top-left corner (AM / PM). Repaints only on change. */
    void set(String text, String tag) {
        if (text.equals(this.text) && tag.equals(this.tag)) return;
        this.text = text;
        this.tag = tag;
        invalidate();
    }

    private void half(Canvas c, float top, float bottom, boolean upper, float rad, float w, float baseline, float ts) {
        float[] rr = upper ? new float[]{rad, rad, rad, rad, 0f, 0f, 0f, 0f} : new float[]{0f, 0f, 0f, 0f, rad, rad, rad, rad};
        half.reset();
        r.set(0f, top, w, bottom);
        half.addRoundRect(r, rr, Path.Direction.CW);
        p.setStyle(Paint.Style.FILL);
        p.setColor(upper ? fill : Col.alpha(fill, Color.alpha(fill) / 255f * 0.82f));   // the lower leaf is a touch dimmer, like a real flap
        c.drawPath(half, p);
        c.save();
        c.clipRect(0f, top, w, bottom);
        p.setColor(ink);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(ts);
        c.drawText(text, w / 2f, baseline, p);
        c.restore();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), rad = Math.min(w, h) * 0.2f, cy = h / 2f;
        float gap = Math.max(1.5f * d, h * 0.024f);
        float ts = textSp * d * Cards.TS;
        p.setTypeface(face);
        // two leaves with a real gap between them (a gap, not a painted line, so it works on any island colour)
        float baseline = cy + ts * 0.36f;   // optical centre: cap height, not the font's full line box
        half(c, 0f, cy - gap / 2f, true, rad, w, baseline, ts);
        half(c, cy + gap / 2f, h, false, rad, w, baseline, ts);
        if (tag.length() > 0) {
            p.setColor(tagInk);
            p.setTextSize(10.5f * d * Cards.TS);
            p.setTextAlign(Paint.Align.LEFT);
            c.drawText(tag, rad * 0.8f, rad * 0.8f + 10.5f * d * Cards.TS * 0.7f, p);
        }
    }
}

/**
 * The sun's (or the moon's) path over the day on a stylised 06:00 - 18:00 arc: dashed where it has yet to go, solid where
 * it has been, a glowing sun on it by day and a pale moon by night. It is not a real sunrise / sunset, just the shape of a day.
 */
final class HorizonView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path dash = new Path(), cut = new Path();
    private final android.graphics.DashPathEffect dashFx;
    private final float d;
    private Shader glow;
    private int glowH = -1, glowAcc;
    private float hourF = -1f;
    private int accent = Color.WHITE, sun = 0xFFFFD60A, moon = 0xFFD6E2FF;
    private String left = "6 AM", right = "6 PM";

    HorizonView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        dashFx = new android.graphics.DashPathEffect(new float[]{3f * d, 5f * d}, 0f);
    }

    void style(int accent, int sun, int moon, String left, String right) {
        this.accent = accent;
        this.sun = sun;
        this.moon = moon;
        this.left = left;
        this.right = right;
        invalidate();
    }

    /** The time as fractional hours 0..24; repaints only once the body has moved at least a pixel or so. */
    void time(float hourF) {
        if (Math.abs(hourF - this.hourF) < 0.004f) return;
        this.hourF = hourF;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        if (hourF < 0f) return;
        float w = getWidth(), h = getHeight();
        float pad = 18f * d, baseY = h - 16f * d;
        float rx = (w - 2f * pad) / 2f, ry = baseY - 8f * d, cx = w / 2f;
        // sky glow under the arc
        p.setStyle(Paint.Style.FILL);
        if (glow == null || glowH != (int) h || glowAcc != accent) {
            glow = new LinearGradient(0f, baseY - ry, 0f, baseY, Col.alpha(accent, 0.0f), Col.alpha(accent, 0.13f), Shader.TileMode.CLAMP);
            glowH = (int) h;
            glowAcc = accent;
        }
        p.setShader(glow);
        dash.reset();
        dash.moveTo(cx - rx, baseY);
        oval.set(cx - rx, baseY - ry, cx + rx, baseY + ry);
        dash.arcTo(oval, 180f, 180f);
        dash.close();
        c.drawPath(dash, p);
        p.setShader(null);

        // horizon line
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(1f, 1.2f * d));
        p.setColor(Col.alpha(Cards.WHITE, 0.28f));
        c.drawLine(pad * 0.4f, baseY, w - pad * 0.4f, baseY, p);

        // the path: 0 at the left end of the arc (06:00 / 18:00), 1 at the right end
        boolean day = DayPart.sunUp(hourF);
        float t = day ? (hourF - 6f) / 12f : (((hourF >= 18f ? hourF - 18f : hourF + 6f)) / 12f);
        // dashed remaining path
        p.setStrokeWidth(Math.max(1.4f, 1.6f * d));
        p.setColor(Col.alpha(Cards.WHITE, 0.3f));
        dash.reset();
        dash.addArc(oval, 180f, 180f);
        p.setPathEffect(dashFx);
        c.drawPath(dash, p);
        p.setPathEffect(null);
        // travelled part, solid
        p.setStrokeWidth(Math.max(2f, 2.2f * d));
        p.setColor(day ? accent : Col.alpha(accent, 0.55f));
        c.drawArc(oval, 180f, 180f * t, false, p);

        // the body
        double a = Math.PI * (1.0 - t);   // pi at the left end, 0 at the right end
        float bx = cx + rx * (float) Math.cos(a), by = baseY - ry * (float) Math.sin(a);
        p.setStyle(Paint.Style.FILL);
        if (day) {
            p.setColor(Col.alpha(sun, 0.22f));
            c.drawCircle(bx, by, 12f * d, p);
            p.setColor(Col.alpha(sun, 0.4f));
            c.drawCircle(bx, by, 8.5f * d, p);
            p.setColor(sun);
            c.drawCircle(bx, by, 5.2f * d, p);
        } else {
            p.setColor(Col.alpha(moon, 0.2f));
            c.drawCircle(bx, by, 11f * d, p);
            p.setColor(moon);
            dash.reset();
            dash.addCircle(bx, by, 5.6f * d, Path.Direction.CW);
            cut.reset();
            cut.addCircle(bx + 2.7f * d, by - 2f * d, 4.7f * d, Path.Direction.CW);
            dash.op(cut, Path.Op.DIFFERENCE);   // a real cut-out, so the crescent works on any card colour
            c.drawPath(dash, p);
        }

        // end labels
        p.setTextSize(10.5f * d * Cards.TS);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setColor(Cards.SUB);
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText(day ? left : right, pad * 0.4f, h - 1.5f * d, p);   // at night the moon runs the arc the other way round: 18:00 on the left
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(day ? right : left, w - pad * 0.4f, h - 1.5f * d, p);
    }
}

/** The two dots of a clock colon, as big as the digits next to them need. */
final class ColonDots extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = Color.WHITE;

    ColonDots(Context c) { super(c); }

    void color(int c) {
        color = c;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), r = Math.min(w * 0.22f, h * 0.045f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawCircle(w / 2f, h * 0.38f, r, p);
        c.drawCircle(w / 2f, h * 0.62f, r, p);
    }
}

/**
 * The "Beacon" glyph: a ring that fills with the day (midnight to midnight) around a softly glowing core, like a little
 * lighthouse lamp. The glow is three stacked discs of the accent, so it needs no shader and works on any island colour.
 */
final class BeaconView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private float frac = -1f;
    private int accent = Color.WHITE;

    BeaconView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void style(int accent) {
        this.accent = accent;
        invalidate();
    }

    /** How much of the day has gone, 0..1; repaints only when the ring would move by a pixel or so. */
    void day(float f) {
        f = f < 0f ? 0f : (f > 1f ? 1f : f);
        if (Math.abs(f - frac) < 0.0007f) return;
        frac = f;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        if (frac < 0f) return;
        float w = getWidth(), h = getHeight(), s = Math.min(w, h), cx = w / 2f, cy = h / 2f;
        float sw = Math.max(1.5f * d, s * 0.1f), rad = s / 2f - sw / 2f;
        // track + the part of the day that has gone
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        p.setColor(Col.alpha(accent, 0.22f));
        c.drawArc(r, 0f, 360f, false, p);
        p.setColor(accent);
        c.drawArc(r, -90f, Math.max(4f, 360f * frac), false, p);   // never empty: a dot marks midnight
        // the lamp: a wide faint halo, a brighter one, then the core
        float inner = rad - sw / 2f - 0.6f * d;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Col.alpha(accent, 0.16f));
        c.drawCircle(cx, cy, inner, p);
        p.setColor(Col.alpha(accent, 0.38f));
        c.drawCircle(cx, cy, inner * 0.66f, p);
        p.setColor(accent);
        c.drawCircle(cx, cy, Math.max(1.4f * d, inner * 0.36f), p);
    }
}
