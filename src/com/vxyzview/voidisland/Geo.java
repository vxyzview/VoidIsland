package com.vxyzview.voidisland;

/** Island shape geometry (pure Java, shared by the real island and the in-app preview). */
final class Geo {
    private Geo() {}

    static final int HIDDEN = 0, COMPACT = 1, EXPANDED = 2;
    static final int PILL = 0, NOTCH = 1, BOX = 2;

    /** Corner radius for a surface of height {@code h} in the given state. */
    static float radius(int shape, int mode, float h, float cR, float eR, float dot) {
        float r;
        if (mode == HIDDEN) r = dot / 2f;
        else if (shape == PILL) r = mode == COMPACT ? h / 2f : Math.min(h / 2f, eR);
        else if (shape == NOTCH) r = Math.min(mode == COMPACT ? cR : eR, h);
        else r = Math.min(mode == COMPACT ? cR : eR, h / 2f);
        return Math.max(0f, r);
    }

    /**
     * Rounded rect to clip/draw: out = {left, top, right, bottom, radius}, horizontally centred in a window of width winW.
     * For a notch the caller passes {@code glue = radius}: the top corners spring above the screen edge,
     * square on top, round at the bottom, no path needed. Glue is springed so pill/notch morphs stay smooth.
     */
    static void surface(int shape, float winW, float width, float top, float height, float rad, float glue, float[] out) {
        float l = (winW - width) / 2f, r = (winW + width) / 2f, t = top - glue, b = top + height, rr;
        if (shape == NOTCH) {
            rr = Math.min(rad, (b - t) / 2f);
        } else {
            rr = Math.min(rad, Math.min((r - l) / 2f, (b - t) / 2f));
        }
        out[0] = l; out[1] = t; out[2] = r; out[3] = b; out[4] = Math.max(0f, rr);
    }
}
