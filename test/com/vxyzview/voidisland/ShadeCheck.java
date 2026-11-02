package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the Shade logic (pure Java: runs on a plain JVM, no Android). */
final class ShadeCheck {
    private ShadeCheck() {}

    static void run() {
        // --- ShadeMatch
        ok(ShadeMatch.panel("Notification shade.", "android.widget.FrameLayout"), "shade: en notification shade");
        ok(ShadeMatch.panel("Quick settings.", ""), "shade: en quick settings");
        ok(ShadeMatch.panel("", "com.android.systemui.statusbar.phone.NotificationPanelView"), "shade: legacy class name");
        ok(ShadeMatch.panel("Setelan cepat", ""), "shade: id quick settings");
        ok(!ShadeMatch.panel("Volume", "android.widget.FrameLayout"), "shade: volume is not the shade");
        ok(!ShadeMatch.panel("Power menu", "android.widget.LinearLayout"), "shade: power menu is not the shade");
        ok(!ShadeMatch.panel("Lock screen.", "android.widget.FrameLayout"), "shade: lock screen is not the shade");
        // --- Fullscreen: hide only while an app is fullscreen
        ok(Fullscreen.hidden(true, true), "fullscreen: a fullscreen app hides the island");
        ok(!Fullscreen.hidden(true, false), "fullscreen: normal apps (landscape too) keep the island");
        ok(!Fullscreen.hidden(false, true), "fullscreen: switched off means never hide");
        ok(Fullscreen.delay(true) > Fullscreen.delay(false), "fullscreen: going fullscreen waits longer than coming back");
        ok(Fullscreen.legacyFlag(0x4) && Fullscreen.legacyFlag(0x1 | 0x4 | 0x400) && !Fullscreen.legacyFlag(0x400), "fullscreen: Android 10 flag test");
    }
}
