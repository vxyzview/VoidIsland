package com.vxyzview.voidisland;

import android.content.Context;
import android.os.Build;

/** Material You: reads the system tonal palette (Android 12+), falls back to the given colour elsewhere. */
final class Pal {
    private Pal() {}

    static boolean dynamic() { return Build.VERSION.SDK_INT >= 31; }

    /** @param name accent1 | accent2 | accent3 | neutral1 | neutral2   @param tone 0,10,50,100,200..900,1000 */
    static int get(Context c, String name, int tone, int fallback) {
        if (!dynamic()) return fallback;
        try {
            int id = c.getResources().getIdentifier("system_" + name + "_" + tone, "color", "android");
            return id == 0 ? fallback : c.getColor(id);
        } catch (Throwable t) {
            return fallback;
        }
    }
}
