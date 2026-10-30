package com.vxyzview.voidisland;

import java.util.HashMap;

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
            int i;
            if (synced) {
                int lo = 0, hi = at.length - 1, r = -1;
                while (lo <= hi) {
                    int mid = (lo + hi) >>> 1;
                    if (at[mid] <= ms) { r = mid; lo = mid + 1; } else hi = mid - 1;
                }
                i = r;
            } else {
                if (line.length == 0 || durMs <= 0) return null;
                i = (int) ((ms / (float) durMs) * line.length);
                if (i >= line.length) i = line.length - 1;
                if (i < 0) i = 0;
            }
            return i >= 0 ? line[i] : null;
        }
    }

    private static final HashMap<String, Loaded> CACHE = new HashMap<String, Loaded>();

    static Loaded cached(String track, String artist) { return CACHE.get(key(track, artist)); }

    static void remember(String track, String artist, Loaded l) {
        if (l == null) return;
        if (CACHE.size() > 24) CACHE.clear();   // tiny cap; refetching a forgotten song is cheap
        CACHE.put(key(track, artist), l);
    }

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
