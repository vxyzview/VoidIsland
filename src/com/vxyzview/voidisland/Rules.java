package com.vxyzview.voidisland;

import java.util.HashMap;
import java.util.Set;

/** Island-only rules for text banners (quiet hours, group chats, one banner per chat). Pure Java so the unit checks cover it. */
final class Rules {
    private Rules() {}

    /** How long a chat stays quiet on the island after its first banner. */
    static final long CHAT_WINDOW_MS = 5 * 60 * 1000L;

    /** True when {@code nowMin} (minutes since midnight) is inside the quiet window; the window may wrap past midnight. */
    static boolean inQuiet(int nowMin, int fromMin, int toMin) {
        if (fromMin == toMin) return false;
        if (fromMin < toMin) return nowMin >= fromMin && nowMin < toMin;
        return nowMin >= fromMin || nowMin < toMin;
    }

    /** Quiet hours cover every app when {@code apps} is empty, otherwise only the listed ones. */
    static boolean quietCovers(String pkg, Set<String> apps) {
        return apps == null || apps.isEmpty() || (pkg != null && apps.contains(pkg));
    }

    private static final HashMap<String, Long> LAST = Lru.map(200);

    /**
     * "One banner per chat": true when this chat already had a banner within the window (stay quiet).
     * Otherwise records {@code now} as the chat's banner time and returns false (show it).
     */
    static boolean chatRepeat(String chatKey, long now, long windowMs) {
        Long last = LAST.get(chatKey);
        if (last != null && now - last >= 0 && now - last < windowMs) return true;
        LAST.put(chatKey, now);
        return false;
    }

    static void resetChats() { LAST.clear(); }

    static String hhmm(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        int h = m / 60, mm = m % 60;
        return (h < 10 ? "0" : "") + h + ":" + (mm < 10 ? "0" : "") + mm;
    }
}
