package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the activity designs' pure parts: the preference table, the event mapping, and the download / charging arithmetic. */
final class ActStyleCheck {
    private ActStyleCheck() {}

    static void run() {
        ok(ActStyle.CKEY.length == ActStyle.COUNT && ActStyle.EKEY.length == ActStyle.COUNT && ActStyle.NAMES.length == ActStyle.COUNT,
            "acts: one name and one pair of preferences per activity");
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        boolean uniq = true;
        for (int i = 0; i < ActStyle.COUNT; i++) { uniq &= seen.add(ActStyle.CKEY[i]); uniq &= seen.add(ActStyle.EKEY[i]); }
        ok(uniq, "acts: no two activities share a preference");
        ok(ActStyle.CNAMES.length == ActStyle.COMPACT && ActStyle.CDESC.length == ActStyle.COMPACT
            && ActStyle.ENAMES.length == ActStyle.EXPANDED && ActStyle.EDESC.length == ActStyle.EXPANDED, "acts: every design has a name and a description");

        ok(ActStyle.of(IslandEvent.CALL) == ActStyle.CALL && ActStyle.of(IslandEvent.PROGRESS) == ActStyle.DOWNLOAD
            && ActStyle.of(IslandEvent.SYSTEM) == ActStyle.DEVICE && ActStyle.of(IslandEvent.NOTIF) == ActStyle.MESSAGE, "acts: events map to their activity");
        ok(ActStyle.of(IslandEvent.MEDIA) == -1 && ActStyle.of(IslandEvent.CLOCK) == -1, "acts: music and the clock keep their own designs");
        ok(!ActStyle.hasCompact(ActStyle.PAYMENT) && !ActStyle.hasCompact(ActStyle.MESSAGE) && ActStyle.hasCompact(ActStyle.CALL),
            "acts: banners have no compact design, live activities do");
        ok(ActStyle.clamp(-3, 5) == 0 && ActStyle.clamp(9, 5) == 4 && ActStyle.clamp(2, 5) == 2, "acts: a stored design out of range is clamped");

        ok(ActStyle.span(45000).equals("45 s") && ActStyle.span(12 * 60000L).equals("12 min") && ActStyle.span(65 * 60000L).equals("1h 05m"), "acts: spans read short");
        ok(ActStyle.span(-5) .equals("0 s"), "acts: a negative span is zero");

        ok(ActStyle.eta(0, 50, 60000L) == 60000L, "acts: half done in a minute -> a minute left");
        ok(ActStyle.eta(20, 60, 40000L) == 40000L, "acts: the pace counts from the first percentage seen");
        ok(ActStyle.eta(-1, 50, 60000L) == -1L && ActStyle.eta(10, 10, 60000L) == -1L && ActStyle.eta(0, 100, 60000L) == -1L && ActStyle.eta(0, 50, 1000L) == -1L,
            "acts: no estimate without progress, when finished, or in the first seconds");

        ok(ActStyle.plug(4, 0, 0).equals("Wireless") && ActStyle.plug(2, 0, 0).equals("USB") && ActStyle.plug(0, 0, 0).equals(""), "acts: charger kinds");
        ok(ActStyle.plug(1, 3000, 4200).equals("Fast") && ActStyle.plug(1, 500, 4200).equals("AC") && ActStyle.plug(1, Integer.MIN_VALUE, 4200).equals("AC"),
            "acts: an AC charger is Fast from 10 W (battery side) up");
        ok("13 W".equals(ActStyle.watts(3000, 4200)) && "9.5 W".equals(ActStyle.watts(2250, 4200)) && "2.1 W".equals(ActStyle.watts(-500, 4200)) && ActStyle.watts(Integer.MIN_VALUE, 4200) == null && ActStyle.watts(0, 4200) == null,
            "acts: watts from current and voltage, unknown stays unknown");
        ok("31.5\u00B0C".equals(ActStyle.temp(315)) && ActStyle.temp(-1) == null, "acts: temperature in tenths of a degree");
    }
}
