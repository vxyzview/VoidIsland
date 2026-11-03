package com.vxyzview.voidisland;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * Small gauges for the activity designs ({@link Acts}). Strictly flat: solid fills and faint pips, no gradients and no blur.
 * Like every other animated view here they only draw while visible and the display is on, and at a low frame rate.
 */

/** A row of flat cells that fill from the left, like a battery or a segmented progress bar. The cell the value is inside of shows half lit. */
final class CellsView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int cells = 5, color = Color.WHITE;
    private float frac;
    private boolean vertical;

    CellsView(Context c, int cells) {
        super(c);
        this.cells = Math.max(2, cells);
    }

    void set(float f, int color) {
        f = f < 0f ? 0f : (f > 1f ? 1f : f);
        if (Math.abs(f - frac) < 0.004f && color == this.color) return;
        frac = f;
        this.color = color;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0f || h <= 0f) return;
        float gap = Math.max(1.5f, Math.min(w, h) * 0.14f);
        float cw = (w - gap * (cells - 1)) / cells, rad = Math.min(cw, h) * 0.32f;
        float lit = frac * cells;
        int off = Col.alpha(color, 0.22f);
        for (int i = 0; i < cells; i++) {
            float x = i * (cw + gap);
            r.set(x, 0f, x + cw, h);
            if (lit >= i + 1f) p.setColor(color);
            else if (lit > i) p.setColor(Col.mix(off, color, 0.5f));
            else p.setColor(off);
            c.drawRoundRect(r, rad, rad, p);
        }
    }
}

/** Three flat dots that take turns lighting up: "connected, working". It sleeps while hidden, while the display is off and when told to hold still. */
final class PulseDots extends View {
    private static final long STEP_MS = 320L;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = Color.WHITE;
    private boolean on = true, dark;

    PulseDots(Context c) { super(c); }

    void set(int color, boolean animate) {
        this.color = color;
        this.on = animate;
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
        float d = Math.min(h, w / 3f) * 0.62f, step = (w - d) / 2f;
        int at = on ? (int) ((SystemClock.uptimeMillis() / STEP_MS) % 3) : 1;
        for (int i = 0; i < 3; i++) {
            p.setColor(i == at ? color : Col.alpha(color, on ? 0.30f : 0.55f));
            float rr = i == at && on ? d * 0.56f : d * 0.46f;
            c.drawCircle(d / 2f + step * i, h / 2f, rr, p);
        }
        if (on && !dark && isShown()) postInvalidateDelayed(STEP_MS);
    }
}
