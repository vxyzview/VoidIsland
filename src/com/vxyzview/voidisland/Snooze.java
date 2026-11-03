package com.vxyzview.voidisland;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Apps whose banners are snoozed for a while (long-press a banner). In memory only: a restart of the island ends every snooze,
 * which is the safe way round. The snoozed app's notifications stay in the shade; only the island stays quiet. Pure Java.
 */
final class Snooze {
    private Snooze() {}

    static final long DEFAULT_MS = 60L * 60L * 1000L;
    static final int MAX = 64;

    private static final LinkedHashMap<String, Long> UNTIL = new LinkedHashMap<String, Long>();

    /** Snoozes {@code pkg} for {@link #DEFAULT_MS} from {@code now} (again: the hour restarts). */
    static synchronized void start(String pkg, long now) {
        if (pkg == null) return;
        UNTIL.remove(pkg);   // re-inserting moves it to the newest end
        UNTIL.put(pkg, now + DEFAULT_MS);
        while (UNTIL.size() > MAX) {
            Iterator<Map.Entry<String, Long>> it = UNTIL.entrySet().iterator();
            it.next();
            it.remove();
        }
    }

    static synchronized boolean active(String pkg, long now) { return remainingMs(pkg, now) > 0; }

    static synchronized long remainingMs(String pkg, long now) {
        if (pkg == null) return 0L;
        Long until = UNTIL.get(pkg);
        if (until == null) return 0L;
        if (until - now <= 0L) { UNTIL.remove(pkg); return 0L; }
        return until - now;
    }

    static synchronized int size() { return UNTIL.size(); }

    static synchronized void clear() { UNTIL.clear(); }
}
