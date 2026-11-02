package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for which setting changes re-apply the island (pure Java: runs on a plain JVM, no Android). */
final class RestyleCheck {
    private RestyleCheck() {}

    static void run() {
        // things that change what the island shows or how it looks must always re-apply
        String[] mustApply = {Prefs.K_ON, Prefs.K_FULL, Prefs.K_INAPP, Prefs.K_HIDEAPP, Prefs.K_CLOCK, Prefs.K_MEDIA, Prefs.K_NOTIF,
            Prefs.K_CALL, Prefs.K_PAY, Prefs.K_CHG, Prefs.K_SYS, Prefs.K_LIVEACT, Prefs.K_QS, Prefs.K_LOCK, Prefs.K_HAPTIC,
            Prefs.K_SHAPE, Prefs.K_CW, Prefs.K_BG, Prefs.K_SPEED, Prefs.K_CLOCKSTYLE, Prefs.K_MSTYLE, Prefs.K_LYRICS, Prefs.K_LYRSIZE,
            Prefs.K_LYROFF, Prefs.K_BLOCK, Prefs.K_THEME};
        boolean all = true;
        for (String k : mustApply) all &= Restyle.needed(k);
        ok(all, "restyle: every look / visibility setting still re-applies the island");
        ok(Restyle.needed(Prefs.K_POKE), "restyle: the poke written when the overlay permission changes must re-apply");
        ok(Restyle.needed(null), "restyle: a change of everything (restore) re-applies");
        ok(Restyle.needed("some_future_key"), "restyle: an unknown key re-applies rather than risk a setting that does nothing");
        // read only when an event arrives: no reason to morph the island or close a reply box
        String[] eventTime = {Prefs.K_ABSORB, Prefs.K_KEEP, Prefs.K_QUIET, Prefs.K_QFROM, Prefs.K_QTO, Prefs.K_QAPPS, Prefs.K_NOGROUP,
            Prefs.K_ONEPERCHAT, Prefs.K_LIVE_RIDE, Prefs.K_LIVE_FOOD, Prefs.K_LIVE_SPORT, Prefs.K_DUR, Prefs.K_KEEPALIVE, Prefs.K_LYRICS_NOTICE};
        boolean none = true;
        for (String k : eventTime) none &= !Restyle.needed(k);
        ok(none, "restyle: settings only read when something arrives do not re-apply the island");
    }
}
