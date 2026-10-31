package com.vxyzview.voidisland;

/** Colour maths (pure Java so it can be unit-tested on a JVM). */
final class Col {
    private Col() {}

    /** Same colour, new alpha 0..1. */
    static int alpha(int c, float a) {
        int v = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (v << 24) | (c & 0xFFFFFF);
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int x = (a >>> s) & 255, y = (b >>> s) & 255;
            out |= Math.round(x + (y - x) * t) << s;
        }
        return out;
    }

    /** Rough perceived brightness 0..1 of the RGB part. */
    static float luma(int c) {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    /** Readable text colour on top of {@code bg}. */
    static int onColor(int bg) { return luma(bg) > 0.58f ? 0xFF111114 : 0xFFFFFFFF; }

    /** Dark tonal container for a custom accent (the role Material's "primary container" plays): the accent pulled toward black. */
    static int container(int accent) { return Col.mix(accent | 0xFF000000, 0xFF000000, 0.62f); }

    /** Text / icon colour that sits on {@link #container}: the accent pushed toward white. */
    static int onContainer(int accent) { return Col.mix(accent | 0xFF000000, 0xFFFFFFFF, 0.78f); }
}
