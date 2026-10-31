package com.vxyzview.voidisland;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Mock of the top of a phone that draws the island with the CURRENT style (same geometry code as the real island). */
final class PreviewView extends View {
    private final M3 m;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float[] g = new float[5];
    private Style st = new Style();
    private float t;
    private ValueAnimator va;

    PreviewView(M3 m) {
        super(m.c);
        this.m = m;
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    void set(Style s) {
        st = s;
        invalidate();
    }

    /** Jumps straight to the compact / expanded pose (page rebuilds), no animation. */
    void snapExpanded(boolean e) {
        if (va != null) va.cancel();
        t = e ? 1f : 0f;
        invalidate();
    }

    void setExpanded(boolean e) {
        if (va != null) va.cancel();
        va = ValueAnimator.ofFloat(t, e ? 1f : 0f);
        va.setDuration(620);
        va.setInterpolator(new OvershootInterpolator(e ? 1.6f : 0.9f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                t = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        va.start();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), m.dp(212));
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float smooth(float a, float b, float x) {
        float k = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return k * k * (3f - 2f * k);
    }

    @Override protected void onDraw(Canvas c) {
        float W = getWidth(), H = getHeight(), d = m.d;
        float left = 20f * d, mockW = W - 2f * left, u = mockW / 392f, top0 = 14f * d;
        c.save();
        c.clipRect(0, 0, W, H);
        // phone body with a faint wallpaper glow
        // phone body, one flat tone (a gradient would fight the flat tonal surfaces around it)
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF15171D);
        r.set(left, top0, left + mockW, H + 80f * d);
        c.drawRoundRect(r, 30f * u, 30f * u, p);
        // status bar + camera hole
        p.setColor(0xE6FFFFFF);
        p.setTextSize(13f * u);
        c.drawText("9:41", left + 24f * u, top0 + 19f * u, p);
        r.set(left + mockW - 58f * u, top0 + 10f * u, left + mockW - 34f * u, top0 + 20f * u);
        c.drawRoundRect(r, 3f * u, 3f * u, p);
        for (int i = 0; i < 3; i++) c.drawRect(left + mockW - 84f * u + i * 6f * u, top0 + (16f - 3f * i) * u, left + mockW - 81f * u + i * 6f * u, top0 + 20f * u, p);
        p.setColor(0xFF000000);
        c.drawCircle(left + mockW / 2f, top0 + 14f * u, 6f * u, p);

        // ---- the island, in device-dp units scaled by u (compact and expanded each carry their own shape)
        int cShape = st.cShape, eShape = st.eShape;
        float cW = st.cW, cH = st.cH, eW = Math.min(st.eW, 392f - 2f * Math.max(12, st.mSide)), eH = 150f;
        float topC = cShape == Style.NOTCH ? 0f : Math.max(2f, 14f - cH / 2f) + st.mTop;
        float drop = eShape == Style.NOTCH ? 0f : (st.mDrop < 0 ? 8f : st.mDrop);
        float tt = Math.max(0f, t);
        float w = lerp(cW, eW, tt), h = lerp(cH, eH, tt), tp = topC + drop * Math.min(1f, tt);
        float tt1 = Math.min(1f, tt);
        float radC = Geo.radius(cShape, Geo.COMPACT, cH, st.cR, st.eR, cH), radE = Geo.radius(eShape, Geo.EXPANDED, eH, st.cR, st.eR, cH);
        float rad = lerp(radC, radE, tt1);
        float glue = lerp(cShape == Style.NOTCH ? radC : 0f, eShape == Style.NOTCH ? radE : 0f, tt1);   // the analogue of the real island's springed sg
        Geo.surface(tt < 0.5f ? cShape : eShape, mockW, w * u, tp * u, h * u, rad * u, glue * u, g);
        float ox = left + st.mX * u, oy = top0;
        c.save();
        c.clipRect(left, top0, left + mockW, H);   // a notch stays glued to the top edge of the phone
        p.setColor(st.bg);
        r.set(ox + g[0], oy + g[1], ox + g[2], oy + g[3]);
        c.drawRoundRect(r, g[4], g[4], p);
        if (st.borderW > 0 && (st.borderCol >>> 24) != 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(st.borderW * d);
            p.setColor(st.borderCol);
            r.inset(st.borderW * d / 2f, st.borderW * d / 2f);
            c.drawRoundRect(r, g[4], g[4], p);
            p.setStyle(Paint.Style.FILL);
        }
        int acc = st.accentOverride ? st.accent : 0xFFB388FF;
        float ca = 1f - smooth(0f, 0.45f, tt), ea = smooth(0.5f, 1f, tt);
        if (ca > 0.02f) {
            float gs = Math.max(10f, (cH - 12f) * st.glyph / 100f), cl = (mockW - cW * u) / 2f, cy = oy + (tp + cH / 2f) * u;
            p.setColor(Col.alpha(acc, ca));
            r.set(ox + cl + st.cPad * u, cy - gs * u / 2f, ox + cl + (st.cPad + gs) * u, cy + gs * u / 2f);
            c.drawRoundRect(r, gs * 0.3f * u, gs * 0.3f * u, p);
            float bw = gs * 0.9f * u / 7f, bx = ox + cl + (cW - st.cPad) * u - gs * 0.9f * u;
            float[] bh = {0.5f, 0.95f, 0.7f, 0.85f};
            for (int i = 0; i < 4; i++) {
                float hh = gs * 0.75f * u * bh[i];
                r.set(bx + i * bw * 2f, cy - hh / 2f, bx + i * bw * 2f + bw, cy + hh / 2f);
                c.drawRoundRect(r, bw / 2f, bw / 2f, p);
            }
        }
        if (ea > 0.02f) {
            float el = (mockW - eW * u) / 2f, x0 = ox + el + st.padX * u, y0 = oy + (tp + st.padY) * u, cw = (eW - 2f * st.padX) * u;
            p.setColor(Col.alpha(acc, ea));
            r.set(x0, y0, x0 + 56f * u, y0 + 56f * u);
            c.drawRoundRect(r, 13f * u, 13f * u, p);
            p.setColor(Col.alpha(st.text, ea));
            r.set(x0 + 70f * u, y0 + 12f * u, x0 + 70f * u + 118f * u, y0 + 22f * u);
            c.drawRoundRect(r, 5f * u, 5f * u, p);
            p.setColor(Col.alpha(st.sub, ea * 0.8f));
            r.set(x0 + 70f * u, y0 + 30f * u, x0 + 70f * u + 80f * u, y0 + 38f * u);
            c.drawRoundRect(r, 4f * u, 4f * u, p);
            p.setColor(st.m3 ? Col.alpha(st.secCont, ea) : Col.alpha(st.text, ea * 0.22f));
            r.set(x0, y0 + 70f * u, x0 + cw, y0 + 74f * u);
            c.drawRoundRect(r, 2f * u, 2f * u, p);
            p.setColor(Col.alpha(st.accentOverride ? st.accent : st.text, ea));
            r.set(x0, y0 + 70f * u, x0 + cw * 0.4f, y0 + 74f * u);
            c.drawRoundRect(r, 2f * u, 2f * u, p);
            float mid = x0 + cw / 2f, cyy = y0 + 98f * u;
            if (st.m3) {   // filled primary play pill, tonal skip buttons
                r.set(mid - 19f * u, cyy - 12f * u, mid + 19f * u, cyy + 12f * u);
                c.drawRoundRect(r, 12f * u, 12f * u, p);
                p.setColor(Col.alpha(st.secCont, ea));
            } else {
                p.setColor(Col.alpha(st.text, ea));
                c.drawCircle(mid, cyy, 12f * u, p);
                p.setColor(Col.alpha(st.sub, ea));
            }
            c.drawCircle(mid - 46f * u, cyy, 7f * u, p);
            c.drawCircle(mid + 46f * u, cyy, 7f * u, p);
        }
        c.restore();
        c.restore();
    }
}
