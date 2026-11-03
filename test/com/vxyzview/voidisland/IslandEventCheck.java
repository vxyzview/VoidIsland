package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/**
 * Unit checks for what an island event does with its own content. IslandEvent holds Android types (PendingIntent, Bitmap), so
 * these need android.jar on the classpath; without it they are skipped, never failed.
 */
final class IslandEventCheck {
    private IslandEventCheck() {}

    static void run() {
        try {
            body();
        } catch (NoClassDefFoundError missing) {
            System.out.println("skip: IslandEvent checks need android.jar on the classpath (" + missing.getMessage() + ")");
        }
    }

    private static void body() {
        IslandEvent e = new IslandEvent("l:1", IslandEvent.NAV);
        e.live = true;
        e.title = "Home";
        e.text = "Turn left on Elm Street";
        e.sub = "4 min";
        e.actLabels = new String[]{"Stop"};
        e.actRuns = new Runnable[1];

        e.hideContent("Maps", "Content hidden", null);
        ok(e.contentHidden() && "Maps".equals(e.title) && "Content hidden".equals(e.text) && e.sub == null, "event: hiding swaps the personal text for the generic line");
        ok(e.actLabels.length == 0 && e.actRuns.length == 0, "event: hiding removes the buttons");

        e.hideContent("again", "again", null);
        ok("Maps".equals(e.title), "event: hiding twice keeps the first stash (the real text is never overwritten)");

        e.restoreContent();
        ok(!e.contentHidden() && "Home".equals(e.title) && "Turn left on Elm Street".equals(e.text) && "4 min".equals(e.sub), "event: restoring brings the text back");
        ok(e.actLabels.length == 1 && "Stop".equals(e.actLabels[0]), "event: restoring brings the buttons back");

        e.restoreContent();
        ok("Home".equals(e.title), "event: restoring when nothing is hidden changes nothing");

        // the app posts an update while the pill is hidden: the fresh content wins and is looked at by the lock rules again
        e.hideContent("Maps", "Content hidden", null);
        IslandEvent fresh = new IslandEvent("l:1", IslandEvent.NAV);
        fresh.title = "Work";
        fresh.text = "Arriving";
        e.copyFrom(fresh);
        ok(!e.contentHidden() && "Work".equals(e.title), "event: an update from the app replaces hidden content and clears the flag");
        e.restoreContent();
        ok("Work".equals(e.title), "event: after an update, restoring does not bring the old text back");

        // an update brings the new charging numbers, but not what the island itself learned while showing the event
        IslandEvent chg = new IslandEvent("charge_live", IslandEvent.CHARGE);
        chg.level = 50; chg.born = 1234L; chg.p0 = 7; chg.total0 = 99L;
        IslandEvent upd = new IslandEvent("charge_live", IslandEvent.CHARGE);
        upd.level = 51; upd.plugged = 1; upd.tempC10 = 312; upd.voltMv = 4100; upd.currentMa = 2800; upd.etaMin = 33;
        chg.copyFrom(upd);
        ok(chg.level == 51 && chg.plugged == 1 && chg.tempC10 == 312 && chg.voltMv == 4100 && chg.currentMa == 2800 && chg.etaMin == 33,
            "event: a live charging update carries the charger, temperature, voltage, current and time to full");
        ok(chg.born == 1234L && chg.p0 == 7 && chg.total0 == 99L, "event: an update keeps what the island learned (download pace start, timer length)");
    }
}
