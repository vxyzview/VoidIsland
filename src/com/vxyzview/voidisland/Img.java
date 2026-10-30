package com.vxyzview.voidisland;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.util.LruCache;

/** Bitmap helpers: app icons, notification icons, dominant colour. */
final class Img {
    private Img() {}

    private static final LruCache<String, Bitmap> ICONS = new LruCache<String, Bitmap>(48);
    private static final LruCache<String, String> LABELS = new LruCache<String, String>(96);

    static Bitmap appIcon(Context c, String pkg) {
        if (pkg == null) return null;
        Bitmap b = ICONS.get(pkg);
        if (b != null) return b;
        try {
            b = bmp(c.getPackageManager().getApplicationIcon(pkg), 128);
            if (b != null) ICONS.put(pkg, b);
        } catch (Throwable t) { /* package not visible */ }
        return b;
    }

    static String appLabel(Context c, String pkg) {
        if (pkg == null) return "";
        String l = LABELS.get(pkg);
        if (l != null) return l;
        try {
            PackageManager pm = c.getPackageManager();
            l = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Throwable t) { l = pkg; }
        LABELS.put(pkg, l);
        return l;
    }

    static Bitmap bmp(Drawable d, int size) {
        if (d == null) return null;
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            return scaled(((BitmapDrawable) d).getBitmap(), size);
        }
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        d.setBounds(0, 0, size, size);
        d.draw(cv);
        return out;
    }

    static Bitmap icon(Context c, Icon ic, int size) {
        if (ic == null) return null;
        try { return bmp(ic.loadDrawable(c), size); } catch (Throwable t) { return null; }
    }

    static Bitmap scaled(Bitmap b, int max) {
        if (b == null) return null;
        if (b.getConfig() == Bitmap.Config.HARDWARE) b = b.copy(Bitmap.Config.ARGB_8888, false);
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight(), m = Math.max(w, h);
        if (m <= max) return b;
        float s = (float) max / m;
        return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)), true);
    }

    /** Vivid accent colour pulled from artwork; falls back to {@code fb} for grey art. */
    static int dominant(Bitmap b, int fb) {
        if (b == null) return fb;
        try {
            Bitmap s = Bitmap.createScaledBitmap(scaled(b, 64), 12, 12, true);
            int[] px = new int[144];
            s.getPixels(px, 0, 12, 0, 0, 12, 12);
            float[] hsv = new float[3];
            double sx = 0, sy = 0, wsum = 0, sat = 0, val = 0;
            for (int p : px) {
                Color.colorToHSV(p, hsv);
                double w = hsv[1] * hsv[2] + 0.02;
                double a = Math.toRadians(hsv[0]);
                sx += Math.cos(a) * w; sy += Math.sin(a) * w;
                sat += hsv[1] * w; val += hsv[2] * w; wsum += w;
            }
            if (wsum <= 0 || sat / wsum < 0.12) return fb;
            float hue = (float) Math.toDegrees(Math.atan2(sy, sx));
            if (hue < 0) hue += 360f;
            return Color.HSVToColor(new float[]{hue, Math.max(0.45f, Math.min(0.9f, (float) (sat / wsum))), Math.max(0.85f, (float) (val / wsum))});
        } catch (Throwable t) {
            return fb;
        }
    }
}
