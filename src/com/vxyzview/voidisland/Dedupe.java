package com.vxyzview.voidisland;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers the last text each notification showed, so an app that re-posts the same notification over and over does not
 * produce a banner each time. Least recently used first out, so a busy phone never forgets everything at once.
 * Pure Java (the clock is passed in) so the unit checks cover it.
 */
final class Dedupe {
    private Dedupe() {}

    static final int MAX = 300;
    static final long WINDOW_MS = 30000L;

    private static final LinkedHashMap<String, long[]> SEEN = new LinkedHashMap<String, long[]>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, long[]> eldest) { return size() > MAX; }
    };

    /** True when notification {@code key} showed the very same {@code signature} less than {@link #WINDOW_MS} ago; otherwise remembers it. */
    static synchronized boolean repeated(String key, String signature, long now) {
        long[] seen = SEEN.get(key);
        if (seen != null && seen[0] == signature.hashCode() && now - seen[1] >= 0 && now - seen[1] < WINDOW_MS) return true;
        SEEN.put(key, new long[]{signature.hashCode(), now});
        return false;
    }

    static synchronized int size() { return SEEN.size(); }

    static synchronized void clear() { SEEN.clear(); }
}
