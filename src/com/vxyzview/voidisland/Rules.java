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

    private static final HashMap<String, Long> LAST = new HashMap<String, Long>();

    /**
     * "One banner per chat": true when this chat already had a banner within the window (stay quiet).
     * Otherwise records {@code now} as the chat's banner time and returns false (show it).
     */
    static boolean chatRepeat(String chatKey, long now, long windowMs) {
        if (chatQuiet(chatKey, now, windowMs)) return true;
        chatShown(chatKey, now);
        return false;
    }

    /** True when this chat already had a banner within the window. Only looks: nothing is recorded. */
    static boolean chatQuiet(String chatKey, long now, long windowMs) {
        Long last = LAST.get(chatKey);
        return last != null && now - last >= 0 && now - last < windowMs;
    }

    /** Record that a banner for this chat was really put on the island (not merely accepted while the island was hidden). */
    static void chatShown(String chatKey, long now) {
        if (LAST.size() > 200) LAST.clear();
        LAST.put(chatKey, now);
    }

    static void resetChats() { LAST.clear(); }

    static String hhmm(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        int h = m / 60, mm = m % 60;
        return (h < 10 ? "0" : "") + h + ":" + (mm < 10 ? "0" : "") + mm;
    }
}
