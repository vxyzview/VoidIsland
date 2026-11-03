package com.vxyzview.voidisland;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Which setting changes make the island re-apply its style and re-evaluate (pure Java, so the unit checks cover it).
 * The key constants are compile-time strings, so this class never loads {@link Prefs} (and with it Android) at run time.
 */
final class Restyle {
    private Restyle() {}

    /**
     * Keys that are only read at the moment something happens (a notification arrives, a banner is built, a message is
     * classified): nothing about how the island looks or what it is showing depends on them, so a change needs no re-apply.
     * Everything else, including keys not listed here, still re-applies: a missing entry costs a redraw, never a setting
     * that fails to take effect. K_POKE is deliberately absent: it is written just to force a re-apply.
     * K_BLOCK is absent too: it changes what may be shown, and the service re-evaluates for it separately.
     */
    private static final Set<String> EVENT_TIME_ONLY = new HashSet<String>(Arrays.asList(
        Prefs.K_ABSORB, Prefs.K_KEEP, Prefs.K_QUIET, Prefs.K_QFROM, Prefs.K_QTO, Prefs.K_QAPPS, Prefs.K_NOGROUP, Prefs.K_ONEPERCHAT, Prefs.K_SNOOZE,
        Prefs.K_LIVE_RIDE, Prefs.K_LIVE_FOOD, Prefs.K_LIVE_SPORT, Prefs.K_DUR, Prefs.K_KEEPALIVE, Prefs.K_LYRICS_NOTICE));

    /** Should the island re-apply after this key changed? {@code null} (everything changed, e.g. a restore) always does. */
    static boolean needed(String key) { return key == null || !EVENT_TIME_ONLY.contains(key); }
}
