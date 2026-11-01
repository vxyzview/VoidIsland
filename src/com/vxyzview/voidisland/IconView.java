package com.vxyzview.voidisland;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Crisp vector glyphs drawn from Material path data. Hardware-accelerated, no bitmaps. */
final class IconView extends View {
    static final int PLAY = 0, PAUSE = 1, NEXT = 2, PREV = 3, PHONE = 4, HANGUP = 5, BOLT = 6, CHECK = 7, CARD = 8,
        BELL = 9, BELL_OFF = 10, MOON = 11, BT = 12, HEADSET = 13, TIMER = 14, NAV = 15, DOWNLOAD = 16, NOTE = 17,
        HOME = 18, PALETTE = 19, TUNE = 20, APPS = 21, LAYERS = 22, ACCESS = 23, CLOCK = 24, BACK = 25, SEND = 26, CLOSE = 27;

    private static final String PHONE_D = "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z";
    private static final String BELL_D = "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z";
    private static final String[] D = {
        "M8 5v14l11-7z",
        "M6 19h4V5H6v14zm8-14v14h4V5h-4z",
        "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z",
        "M6 6h2v12H6zm3.5 6l8.5 6V6z",
        PHONE_D,
        PHONE_D,
        "M7 2v11h3v9l7-12h-4l4-8z",
        "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
        "M20 4H4c-1.11 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V6c0-1.11-.89-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z",
        BELL_D,
        BELL_D,
        "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z",
        "M17.71 7.71L12 2h-1v7.59L6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 11 14.41V22h1l5.71-5.71-4.3-4.29 4.3-4.29zM13 5.83l1.88 1.88L13 9.59V5.83zm1.88 10.46L13 18.17v-3.76l1.88 1.88z",
        "M12 1c-4.97 0-9 4.03-9 9v7c0 1.66 1.34 3 3 3h3v-8H5v-2c0-3.87 3.13-7 7-7s7 3.13 7 7v2h-4v8h3c1.66 0 3-1.34 3-3v-7c0-4.97-4.03-9-9-9z",
        "M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z",
        "M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z",
        "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z",
        "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z",
        "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
        "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.38-.61-.38-.99 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z",
        "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
        "M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z",
        "M11.99 18.54l-7.37-5.73L3 14.07l9 7 9-7-1.63-1.27-7.38 5.74zM12 16l7.36-5.73L21 9l-9-7-9 7 1.63 1.27L12 16z",
        "M20.5 6c-2.61.7-5.67 1-8.5 1s-5.89-.3-8.5-1L3 8c1.86.5 4 .83 6 1v13h2v-6h2v6h2V9c2-.17 4.14-.5 6-1l-.5-2zM12 6c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2z",
        "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
        "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z",
        "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z",
        "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"
    };
    private static final Path[] P = new Path[D.length];

    private static Path path(int t) {
        if (P[t] == null) P[t] = Svg.parse(D[t]);
        return P[t];
    }

    /** Draws glyph {@code type} into the square at (left, top) of the given size. */
    static void glyph(Canvas c, Paint p, int type, int color, float left, float top, float size) {
        float s = size / 24f;
        c.save();
        c.translate(left, top);
        c.scale(s, s);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        if (type == HANGUP) c.rotate(135f, 12f, 12f);
        c.drawPath(path(type), p);
        if (type == BELL_OFF) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.BLACK);
            p.setStrokeWidth(4.6f);
            c.drawLine(4f, 3.5f, 20f, 20.5f, p);
            p.setColor(color);
            p.setStrokeWidth(2.2f);
            c.drawLine(4f, 3.5f, 20f, 20.5f, p);
            p.setStyle(Paint.Style.FILL);
        }
        c.restore();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int type, color;

    IconView(Context c, int type, int color) {
        super(c);
        this.type = type;
        this.color = color;
    }

    int type() { return type; }

    void set(int type, int color) {
        this.type = type;
        this.color = color;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        if (type < 0 || type >= D.length) return;
        float aw = getWidth() - getPaddingLeft() - getPaddingRight(), ah = getHeight() - getPaddingTop() - getPaddingBottom();
        float s = Math.min(aw, ah);
        glyph(c, paint, type, color, getPaddingLeft() + (aw - s) / 2f, getPaddingTop() + (ah - s) / 2f, s);
    }
}
