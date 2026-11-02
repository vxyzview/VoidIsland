package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the Absorb logic (pure Java: runs on a plain JVM, no Android). */
final class AbsorbCheck {
    private AbsorbCheck() {}

    static void run() {
        // --- Absorb opt-in: the original is only removed when the island really shows the banner
        ok(Absorb.shouldRemove(true, false, true, IslandEvent.NOTIF, false, false), "absorb: opted in + island showing removes the original");
        ok(!Absorb.shouldRemove(false, false, true, IslandEvent.NOTIF, false, false), "absorb: off by default means nothing is removed");
        ok(!Absorb.shouldRemove(true, false, false, IslandEvent.NOTIF, false, false), "absorb: island not showing (off, hidden, ignoring the app) keeps the original");
        ok(!Absorb.shouldRemove(true, true, true, IslandEvent.NOTIF, false, false), "absorb: keep-in-shade apps are never touched");
        ok(!Absorb.shouldRemove(true, false, true, IslandEvent.CALL, true, false), "absorb: live things still keep their notification");
        ok(!Prefs.ABSORB_DEF, "absorb: starts off");

        // --- Absorb: one-shot notifications are the island's to show; their original rests, then returns
        ok(Absorb.applies(IslandEvent.NOTIF, false, false) && Absorb.applies(IslandEvent.PAYMENT, false, false), "absorb: text banners are absorbed");
        ok(!Absorb.applies(IslandEvent.CALL, true, false) && !Absorb.applies(IslandEvent.TIMER, true, false)
            && !Absorb.applies(IslandEvent.PROGRESS, true, false), "absorb: live things keep their original notification");
        ok(!Absorb.applies(IslandEvent.NOTIF, false, true), "absorb: never hides notifications that were already up at connect");
    }
}
