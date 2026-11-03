package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for snoozing an app's banners (pure Java). */
final class SnoozeCheck {
    private SnoozeCheck() {}

    static void run() {
        Snooze.clear();
        ok(!Snooze.active("com.chat", 1000L), "snooze: nothing is snoozed to begin with");
        Snooze.start("com.chat", 1000L);
        ok(Snooze.active("com.chat", 1000L + 1), "snooze: a snoozed app is quiet right away");
        ok(Snooze.active("com.chat", 1000L + Snooze.DEFAULT_MS - 1), "snooze: and stays quiet until the hour is up");
        ok(!Snooze.active("com.chat", 1000L + Snooze.DEFAULT_MS), "snooze: the hour ends by itself");
        ok(!Snooze.active("com.chat", 1000L + Snooze.DEFAULT_MS + 5), "snooze: and an ended snooze stays ended");
        Snooze.start("com.a", 5000L);
        ok(Snooze.active("com.a", 6000L) && !Snooze.active("com.b", 6000L), "snooze: only that app is quiet");
        Snooze.start("com.a", 9000L);
        ok(Snooze.active("com.a", 9000L + Snooze.DEFAULT_MS - 1), "snooze: snoozing again restarts the hour");
        ok(Snooze.remainingMs("com.a", 9000L + 1000L) == Snooze.DEFAULT_MS - 1000L, "snooze: says how long is left");
        ok(Snooze.remainingMs("com.zzz", 1L) == 0L, "snooze: no time left for an app that is not snoozed");
        Snooze.clear();
        ok(!Snooze.active("com.a", 9001L), "snooze: clear ends every snooze");
        ok(!Snooze.active("com.a", 1L) && !Snooze.active(null, 1L), "snooze: no package is never snoozed");
        Snooze.start(null, 1L);
        for (int i = 0; i < Snooze.MAX + 20; i++) Snooze.start("com.app" + i, 100L + i);
        ok(Snooze.size() <= Snooze.MAX, "snooze: memory is bounded");
        ok(Snooze.active("com.app" + (Snooze.MAX + 19), 200L), "snooze: the newest snoozes are kept when it is full");
        Snooze.clear();
    }
}
