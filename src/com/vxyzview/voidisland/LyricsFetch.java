package com.vxyzview.voidisland;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.net.URLEncoder;

import org.json.JSONArray;
import org.json.JSONObject;

/** Looks the playing song up on LRCLIB (cache first, two daemon workers, results delivered on the main thread). Split out of Cards. */
final class LyricsFetch {
    private LyricsFetch() {}


    /** Callback for {@link #lyricsFetch}: always called on the main thread (even on an instant cache hit). */
    interface GotLyrics { void got(String track, String artist, Lyrics.Loaded l); }

    /** Far bigger than any lyric sheet: never buffer more. */
    private static final int MAX_CHARS = 600000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Two daemon workers at most: skipping through songs queues lookups instead of spawning a thread (and two HTTP calls) each. */
    private static final java.util.concurrent.ExecutorService NET = java.util.concurrent.Executors.newFixedThreadPool(2,
        new java.util.concurrent.ThreadFactory() {
            @Override public Thread newThread(Runnable r) { Thread t = new Thread(r, "vi-lyrics"); t.setDaemon(true); return t; }
        });
    /** Lookups already running, by song: a second view asking for the same song waits for that answer. Main thread only. */
    private static final java.util.HashMap<String, java.util.ArrayList<GotLyrics>> INFLIGHT = new java.util.HashMap<String, java.util.ArrayList<GotLyrics>>();

    /** Cache-first lyrics for the playing song; slow network work sits on a daemon thread, results land on main. */
    static void lyricsFetch(final String track, final String artist, final long durS, final GotLyrics done) {
        Lyrics.Loaded hit = Lyrics.cached(track, artist);
        if (hit != null) { done.got(track, artist, hit); return; }
        if (Lyrics.missedRecently(track, artist, System.currentTimeMillis())) { done.got(track, artist, null); return; }
        final String fk = track.toLowerCase() + "|" + artist.toLowerCase();
        java.util.ArrayList<GotLyrics> waiting = INFLIGHT.get(fk);
        if (waiting != null) { waiting.add(done); return; }
        waiting = new java.util.ArrayList<GotLyrics>();
        waiting.add(done);
        INFLIGHT.put(fk, waiting);
        try {
            NET.execute(new Runnable() {
                @Override public void run() {
                    final Lyrics.Loaded l = lyricsNet(track, artist, durS);
                    MAIN.post(new Runnable() {
                        @Override public void run() {
                            if (l == null) Lyrics.rememberMiss(track, artist, System.currentTimeMillis());
                            java.util.ArrayList<GotLyrics> all = INFLIGHT.remove(fk);
                            if (all != null) for (GotLyrics g : all) g.got(track, artist, l);
                        }
                    });
                }
            });
        } catch (Throwable t) {
            INFLIGHT.remove(fk);
            done.got(track, artist, null);
        }
    }

    /** The caller's small holders (key + loaded) plus the callback that should repaint its lyric row. */
    static void lyricsLoad(final Context c, final IslandEvent e, final String[] key, final Lyrics.Loaded[] now, final Runnable on) {
        if (!Prefs.b(c, Prefs.K_LYRICS, true)) { now[0] = null; key[0] = ""; on.run(); return; }
        final String track = e.title == null ? "" : e.title.toString().trim();
        final String artist = e.text == null ? "" : e.text.toString().trim();
        if (track.length() == 0) { now[0] = null; key[0] = ""; on.run(); return; }
        final String k = track + "|" + artist + "|" + (e.durMs / 1000);
        if (k.equals(key[0])) return;   // already working on this exact song (found or not)
        key[0] = k;
        Lyrics.Loaded hit = Lyrics.cached(track, artist);
        if (hit != null) { now[0] = hit; on.run(); return; }
        lyricsFetch(track, artist, e.durMs / 1000, new GotLyrics() {
            @Override public void got(String t0, String a0, Lyrics.Loaded l) {
                if (l != null) Lyrics.remember(t0, a0, l);
                String n0 = e.title == null ? "" : e.title.toString().trim();
                String n1 = e.text == null ? "" : e.text.toString().trim();
                if (!n0.equals(t0) || !n1.equals(a0)) return;   // the song changed while we were fetching
                now[0] = l;
                on.run();
            }
        });
    }

    /** LRCLIB (no key): /api/get first, then /api/search picking the entry whose duration fits best. */
    private static Lyrics.Loaded lyricsNet(String track, String artist, long durS) {
        JSONObject best = null;
        try {
            String q = "https://lrclib.net/api/get?track_name=" + URLEncoder.encode(track, "UTF-8")
                + (artist.length() > 0 ? "&artist_name=" + URLEncoder.encode(artist, "UTF-8") : "")
                + (durS > 0 ? "&duration=" + durS : "");
            best = Http.object(q, 5000, 6000, MAX_CHARS);   // null = no exact record (404) or a busy-server 503 — the search fallback still runs
        } catch (Throwable t) { /* fall through to search */ }
        if (best == null) {
            try {
                String q = "https://lrclib.net/api/search?track_name=" + URLEncoder.encode(track, "UTF-8")
                    + (artist.length() > 0 ? "&artist_name=" + URLEncoder.encode(artist, "UTF-8") : "");
                JSONArray arr = Http.array(q, 5000, 6000, MAX_CHARS);
                if (arr != null) {
                    int ai = -1, score = -1000000;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        String s0 = o.optString("syncedLyrics", ""), p0 = o.optString("plainLyrics", "");
                        if (s0.length() == 0 && p0.length() == 0) continue;
                        int sc = (s0.length() > 0 ? 500 : 0) + (p0.length() > 0 ? 1 : 0);
                        double d = o.optDouble("duration", 0);
                        if (durS > 0 && Math.abs(d - durS) <= 2) sc += 200;
                        if (sc > score) { score = sc; ai = i; }
                    }
                    if (ai >= 0) best = arr.optJSONObject(ai);
                }
            } catch (Throwable t) { /* offline or rate-limited: no lyrics this round, no caching of failures */ }
        }
        if (best == null) return null;
        String s0 = best.optString("syncedLyrics", ""), p0 = best.optString("plainLyrics", "");
        return Lyrics.parse(s0.length() > 0 ? s0 : null, p0.length() > 0 ? p0 : null);
    }
}
