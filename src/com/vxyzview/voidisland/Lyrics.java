package com.vxyzview.voidisland;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lyrics text support for the music card: parse an LRC or plain-lyrics body, then look up the line
 * that is playing at a given position. Pure Java so the unit checks cover it (network lives in Cards).
 */
final class Lyrics {
    private Lyrics() {}

    /** One fetched song: timestamped lines (LRC) or plain lines that follow the song's progress. */
    static final class Loaded {
        final long[] at;        // line start times in ms, ascending; empty for plain text
        final String[] line;    // the lines
        final boolean synced;   // false = pick the line by how far into the song we are

        Loaded(long[] at, String[] line, boolean synced) { this.at = at; this.line = line; this.synced = synced; }

        /** The line playing at {@code ms}, or null when nothing should be on screen yet. */
        String textAt(long ms, long durMs) {
            int i = indexAt(ms, durMs);
            return i >= 0 ? line[i] : null;
        }

        /** Milliseconds of song position at which the line after the current one starts, or -1 when there is none. */
        long nextStartMs(long ms, long durMs) {
            int i = indexAt(ms, durMs);
            if (synced) return i + 1 < at.length ? at[i + 1] : -1L;
            if (line.length == 0 || durMs <= 0) return -1L;
            return i + 1 < line.length ? durMs * (i + 1) / line.length + 1 : -1L;
        }

        /** The index of the line playing at {@code ms}, or -1 when nothing is yet (also -1 for "no duration" plain text). */
        int indexAt(long ms, long durMs) {
            if (synced) {
                int lo = 0, hi = at.length - 1, r = -1;
                while (lo <= hi) {
                    int mid = (lo + hi) >>> 1;
                    if (at[mid] <= ms) { r = mid; lo = mid + 1; } else hi = mid - 1;
                }
                return r;
            }
            if (line.length == 0 || durMs <= 0) return -1;
            int i = (int) ((ms / (float) durMs) * line.length);
            if (i >= line.length) i = line.length - 1;
            if (i < 0) i = 0;
            return i;
        }
    }

    /** Parse/LRC plumbing above; below: column scroll math, shared by the pill's lyric window. */

    /** Column offset that centres row {@code i} in a {@code windowH}-tall window, clamped so the song never scrolls past its edges. */
    static float snapOffset(int i, float[] tops, float windowH) {
        float o = tops[i] + (tops[i + 1] - tops[i]) / 2f - windowH / 2f;
        float max = tops[tops.length - 1] - windowH;
        if (o > max) o = max;
        if (o < 0) o = 0;
        return o;
    }

    static final int CACHE_MAX = 40;
    static final long MISS_TTL_MS = 120000L;   // a song with no lyrics (or a failed lookup) is not asked about again for 2 minutes

    // least-recently-used: the songs you keep replaying stay, instead of everything being wiped at the cap
    private static final LinkedHashMap<String, Loaded> CACHE = new LinkedHashMap<String, Loaded>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Loaded> eldest) { return size() > CACHE_MAX; }
    };
    private static final HashMap<String, Long> MISS = new HashMap<String, Long>();

    static synchronized Loaded cached(String track, String artist) { return CACHE.get(key(track, artist)); }

    static synchronized void remember(String track, String artist, Loaded l) {
        if (l == null) return;
        String k = key(track, artist);
        MISS.remove(k);
        CACHE.put(k, l);
    }

    /** Note that a lookup found nothing, so rebuilding the music card does not hit the network again straight away. */
    static synchronized void rememberMiss(String track, String artist, long now) {
        if (MISS.size() > 64) MISS.clear();
        MISS.put(key(track, artist), now);
    }

    static synchronized boolean missedRecently(String track, String artist, long now) {
        Long at = MISS.get(key(track, artist));
        return at != null && now - at < MISS_TTL_MS;
    }

    static synchronized int cacheSize() { return CACHE.size(); }

    static synchronized void clearAll() { CACHE.clear(); MISS.clear(); }

    private static String key(String track, String artist) {
        return (track == null ? "" : track.trim().toLowerCase()) + "|" + (artist == null ? "" : artist.trim().toLowerCase());
    }

    /** Pure line extraction: prefer the LRC body (stamps, possibly several per line), else the plain text. */
    static Loaded parse(String synced, String plain) {
        if (synced != null && synced.indexOf('[') >= 0) {
            long[] st = new long[400];
            String[] tx = new String[400];
            int n = 0;
            for (String row : synced.replace("\r", "").split("\n")) {
                String r0 = row.trim();
                if (n >= st.length) break;
                long[] stamps = new long[8]; int sc = 0;
                int p = 0;
                while (p < r0.length() && r0.charAt(p) == '[' && sc < stamps.length) {
                    int end = r0.indexOf(']', p);
                    if (end < 0) break;
                    long ms = stampMs(r0.substring(p + 1, end));
                    if (ms < 0) break;
                    stamps[sc++] = ms;
                    p = end + 1;
                }
                if (sc == 0) continue;
                String text = r0.substring(p).trim();
                if (text.length() == 0) continue;
                for (int i = 0; i < sc && n < st.length; i++) { st[n] = stamps[i]; tx[n] = text; n++; }
            }
            if (n > 0) {
                for (int i = 1; i < n; i++)          // insertion sort keeps both arrays in lockstep
                    for (int j = i; j > 0 && st[j] < st[j - 1]; j--) {
                        long lt = st[j]; st[j] = st[j - 1]; st[j - 1] = lt;
                        String sm = tx[j]; tx[j] = tx[j - 1]; tx[j - 1] = sm;
                    }
                long[] t2 = new long[n];
                String[] s2 = new String[n];
                System.arraycopy(st, 0, t2, 0, n);
                System.arraycopy(tx, 0, s2, 0, n);
                return new Loaded(t2, s2, true);
            }
        }
        if (plain != null && plain.trim().length() > 0) {
            String[] rows = plain.replace("\r", "").split("\n");
            String[] kept = new String[rows.length];
            int m = 0;
            for (String r0 : rows) {
                String t0 = r0.trim();
                if (t0.length() > 0) kept[m++] = t0;
            }
            if (m > 0) return new Loaded(new long[0], java.util.Arrays.copyOf(kept, m), false);
        }
        return null;
    }

    /** "[mm:ss.xx]" or "[mm:ss]" or "[hh:mm:ss.xx]" → milliseconds, or -1 when the bracket is not a timestamp. */
    private static long stampMs(String c) {
        try {
            c = c.trim();
            int dot = c.indexOf('.');
            long fms = 0;
            if (dot >= 0) {
                String frac = c.substring(dot + 1).trim();
                c = c.substring(0, dot);
                if (frac.length() > 0) fms = Long.parseLong((frac + "000").substring(0, 3));
            }
            String[] seg = c.split(":");
            if (seg.length == 2) return Long.parseLong(seg[0].trim()) * 60000 + Long.parseLong(seg[1].trim()) * 1000 + fms;
            if (seg.length == 3) return Long.parseLong(seg[0].trim()) * 3600000 + Long.parseLong(seg[1].trim()) * 60000 + Long.parseLong(seg[2].trim()) * 1000 + fms;
        } catch (Throwable t) { /* a tag like [ti:...] is not a timestamp */ }
        return -1;
    }
}
