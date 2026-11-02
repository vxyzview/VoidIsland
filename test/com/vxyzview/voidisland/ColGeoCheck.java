package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the ColGeo logic (pure Java: runs on a plain JVM, no Android). */
final class ColGeoCheck {
    private ColGeoCheck() {}

    static void run() {
        // --- Col
        ok(Col.alpha(0xFF336699, 0.5f) == 0x80336699, "Col.alpha");
        ok(Col.mix(0xFF000000, 0xFFFFFFFF, 0.5f) == 0xFF808080, "Col.mix midpoint");
        ok(Col.onColor(0xFF000000) == 0xFFFFFFFF && Col.onColor(0xFFF2F2F7) == 0xFF111114, "Col.onColor contrast");
        ok(Col.onColor(0xFF0B1B3A) == 0xFFFFFFFF, "Col.onColor navy -> white text");
        // custom accent family: container is dark, on-container is light, both opaque and still the accent's hue
        int cont = Col.container(0xFFB388FF), onCont = Col.onContainer(0xFFB388FF);
        ok((cont >>> 24) == 255 && (onCont >>> 24) == 255, "Col.container / onContainer are opaque");
        ok(Col.luma(cont) < 0.25f && Col.luma(onCont) > 0.8f, "Col.container is dark, Col.onContainer is light");
        ok(Col.luma(onCont) - Col.luma(cont) > 0.5f, "accent container text keeps strong contrast");

        // --- Geo: pill / box / notch
        float[] g = new float[5];
        ok(Geo.radius(Geo.PILL, Geo.COMPACT, 32f, 16f, 38f, 32f) == 16f, "pill compact = fully round");
        ok(Geo.radius(Geo.PILL, Geo.EXPANDED, 120f, 16f, 38f, 32f) == 38f, "pill expanded uses card radius");
        ok(Geo.radius(Geo.PILL, Geo.EXPANDED, 50f, 16f, 38f, 32f) == 25f, "pill expanded never exceeds half height");
        ok(Geo.radius(Geo.BOX, Geo.COMPACT, 32f, 10f, 22f, 32f) == 10f, "box compact uses own radius");
        ok(Geo.radius(Geo.BOX, Geo.COMPACT, 12f, 30f, 22f, 32f) == 6f, "box radius clamps to half height");
        ok(Geo.radius(Geo.NOTCH, Geo.COMPACT, 28f, 14f, 28f, 32f) == 14f, "notch compact bottom radius");
        ok(Geo.radius(Geo.PILL, Geo.HIDDEN, 32f, 16f, 38f, 30f) == 15f, "hidden = dot");
        Geo.surface(Geo.PILL, 400f, 150f, 0f, 32f, 16f, 0f, g);
        ok(g[0] == 125f && g[2] == 275f && g[1] == 0f && g[3] == 32f && g[4] == 16f, "pill rect centred in window");
        Geo.surface(Geo.NOTCH, 400f, 130f, 0f, 28f, 14f, 14f, g);
        ok(g[1] == -14f && g[3] == 28f && g[4] == 14f, "notch pushed up so top corners are off-screen");
        ok(g[4] * 2f <= g[3] - g[1], "notch radius fits its height");
        Geo.surface(Geo.NOTCH, 400f, 130f, 0f, 28f, 14f, 7f, g);   // pill->notch morph: glue springs, the top follows it
        ok(g[1] == -7f && g[3] == 28f && g[4] == 14f, "notch glue springs smoothly");
        Geo.surface(Geo.BOX, 400f, 20f, 0f, 100f, 38f, 0f, g);
        ok(g[4] == 10f, "box radius clamps to half the width");
    }
}
