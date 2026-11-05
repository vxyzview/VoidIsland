package com.vxyzview.voidisland;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Looks lyrics up on LRCLIB (open, no key) for the music card: a cache-first request that runs on two daemon workers and
 * answers on the main thread. Split out of Cards so the only place that talks to the network is one small file.
 * What leaves the phone: the song title, artist and length, and the app name in the User-Agent. Nothing else.
 */
final class LyricsNet {
    private LyricsNet() {}

    /** Callback for {@link #fetch}: always called on the main thread (even on an instant cache hit). */
    interface GotLyrics { void got(String track, String artist, Lyrics.Loaded l); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * Two daemon workers at most: skipping through songs queues lookups instead of spawning a thread (and two HTTP calls) each.
     * Idle workers exit after 20 s (a fixed pool kept both threads, and their stacks, alive for the life of the process).
     */
    private static final java.util.concurrent.ExecutorService NET = lyricPool();

    private static java.util.concurrent.ExecutorService lyricPool() {
        java.util.concurrent.ThreadPoolExecutor ex = new java.util.concurrent.ThreadPoolExecutor(2, 2, 20L,
            java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<Runnable>(),
            new java.util.concurrent.ThreadFactory() {
                @Override public Thread newThread(Runnable r) { Thread t = new Thread(r, "vi-lyrics"); t.setDaemon(true); return t; }
            });
        ex.allowCoreThreadTimeOut(true);
        return ex;
    }
    /** Set by the worker that ran a lookup when it failed to reach the service (as opposed to being told there are no lyrics). */
    private static final ThreadLocal<Boolean> NET_ERR = new ThreadLocal<Boolean>();

    /** Lookups already running, by song: a second view asking for the same song waits for that answer. Main thread only. */
    /** The song the most recent request was for: lookups still queued for an older song (skipped past) are not worth the network. */
    private static volatile String wantKey = "";

    private static final java.util.HashMap<String, java.util.ArrayList<GotLyrics>> INFLIGHT = new java.util.HashMap<String, java.util.ArrayList<GotLyrics>>();

    /** Cache-first lyrics for the playing song; slow network work sits on a daemon thread, results land on main. */
    static void fetch(final String track, final String artist, final long durS, final GotLyrics done) {
        Lyrics.Loaded hit = Lyrics.cached(track, artist);
        if (hit != null) { done.got(track, artist, hit); return; }
        if (Lyrics.missedRecently(track, artist, System.currentTimeMillis())) { done.got(track, artist, null); return; }
        final String fk = track.toLowerCase() + "|" + artist.toLowerCase();
        java.util.ArrayList<GotLyrics> waiting = INFLIGHT.get(fk);
        if (waiting != null) {
            wantKey = fk;   // asked for again (skipped back to it): the running lookup must not treat itself as skipped past
            waiting.add(done);
            return;
        }
        waiting = new java.util.ArrayList<GotLyrics>();
        waiting.add(done);
        INFLIGHT.put(fk, waiting);
        wantKey = fk;
        try {
            NET.execute(new Runnable() {
                @Override public void run() {
                    // An exception on this worker thread would kill the whole process (and with it the accessibility service).
                    Lyrics.Loaded found = null;
                    NET_ERR.set(Boolean.FALSE);
                    final boolean skipped = !fk.equals(wantKey);   // the person already skipped past this song: no requests at all
                    try { if (!skipped) found = lyricsNet(track, artist, durS, fk); } catch (Throwable t) { NET_ERR.set(Boolean.TRUE); Dbg.e("lyrics", t); /* odd lyric sheet / network hiccup: no lyrics */ }
                    final Lyrics.Loaded l = found;
                    // Re-read AFTER the lookup: skipping past the song mid-search makes lyricsNet stop early, and that is not an answer.
                    final boolean stale = !fk.equals(wantKey);
                    final boolean none = found == null && !Boolean.TRUE.equals(NET_ERR.get());   // answered "nothing here" vs. could not ask
                    MAIN.post(new Runnable() {
                        @Override public void run() {
                            if (l == null && !stale) Lyrics.rememberMiss(track, artist, System.currentTimeMillis(), none ? Lyrics.NONE_TTL_MS : Lyrics.MISS_TTL_MS);
                            java.util.ArrayList<GotLyrics> all = INFLIGHT.remove(fk);
                            if (all != null) for (GotLyrics g : all) {
                                try { g.got(track, artist, l); } catch (Throwable t) { Dbg.e("lyrics.card", t); /* that card is gone or broken: the rest still get their answer */ }
                            }
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
    static void load(final Context c, final IslandEvent e, final String[] key, final Lyrics.Loaded[] now, final Runnable on) {
        if (!Prefs.b(c, Prefs.K_LYRICS, true)) { now[0] = null; key[0] = ""; on.run(); return; }
        final String track = e.title == null ? "" : e.title.toString().trim();
        final String artist = e.text == null ? "" : e.text.toString().trim();
        if (track.length() == 0) { now[0] = null; key[0] = ""; on.run(); return; }
        if (!Lyrics.worthLookup(e.pkg, e.durMs)) { now[0] = null; key[0] = ""; on.run(); return; }   // video / podcast apps: no lookup
        final String k = track + "|" + artist + "|" + (e.durMs / 1000);
        if (k.equals(key[0])) return;   // already working on this exact song (found or not)
        key[0] = k;
        Lyrics.Loaded hit = Lyrics.cached(track, artist);
        if (hit != null) { now[0] = hit; on.run(); return; }
        fetch(track, artist, e.durMs / 1000, new GotLyrics() {
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

    /**
     * LRCLIB (no key): /api/get first, then up to three searches, each looser than the last (exact title + artist, a free-text
     * "artist title", the cleaned-up title alone). The looser ones only accept a result whose length matches the song.
     */
    private static Lyrics.Loaded lyricsNet(String track, String artist, long durS, String fk) {
        JSONObject best = null;
        try {
            String q = "https://lrclib.net/api/get?track_name=" + URLEncoder.encode(track, "UTF-8")
                + (artist.length() > 0 ? "&artist_name=" + URLEncoder.encode(artist, "UTF-8") : "")
                + (durS > 0 ? "&duration=" + durS : "");
            best = httpJson(q);   // null = no exact record (404) or a busy-server 503: the searches below still run
            if (best != null && !hasLyrics(best)) best = null;   // instrumental / empty record
        } catch (Throwable t) { Dbg.e("lyrics.get", t); /* fall through to search */ }
        if (best == null) {
            NET_ERR.set(Boolean.FALSE);   // only the searches decide whether this miss is believable (the exact lookup 404s for most songs)
            try {
                String cleanTrack = Lyrics.cleanTitle(track), cleanArtist = Lyrics.cleanArtist(artist);
                String[] urls = {
                    "https://lrclib.net/api/search?track_name=" + URLEncoder.encode(track, "UTF-8")
                        + (artist.length() > 0 ? "&artist_name=" + URLEncoder.encode(artist, "UTF-8") : ""),
                    "https://lrclib.net/api/search?q=" + URLEncoder.encode((cleanArtist + " " + cleanTrack).trim(), "UTF-8"),
                    "https://lrclib.net/api/search?track_name=" + URLEncoder.encode(cleanTrack, "UTF-8")
                };
                for (int u = 0; u < urls.length && best == null && fk.equals(wantKey); u++) {   // stop early once the song was skipped
                    JSONArray arr = httpArray(urls[u]);
                    if (arr != null) best = pickBest(arr, cleanArtist, durS, u > 0);
                }
            } catch (Throwable t) { Dbg.e("lyrics.search", t); /* the HTTP layer already absorbs network errors, so only odd data gets here */ }
        }
        if (best == null) return null;
        String s0 = jstr(best, "syncedLyrics"), p0 = jstr(best, "plainLyrics");
        return Lyrics.parse(s0.length() > 0 ? s0 : null, p0.length() > 0 ? p0 : null);
    }

    /** A string field, or "" when it is missing or JSON null (Android's optString turns JSON null into the text "null"). */
    private static String jstr(JSONObject o, String key) {
        return o == null || o.isNull(key) ? "" : o.optString(key, "");
    }

    private static boolean hasLyrics(JSONObject o) { return jstr(o, "syncedLyrics").length() > 0 || jstr(o, "plainLyrics").length() > 0; }

    /** The search hit with lyrics that fits best: synced beats plain, a matching length and artist add weight. {@code strict}: length must fit. */
    private static JSONObject pickBest(JSONArray arr, String artist, long durS, boolean strict) {
        JSONObject best = null;
        int score = -1;
        String want = artist.toLowerCase();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || !hasLyrics(o)) continue;
            double gap = Math.abs(o.optDouble("duration", 0) - durS);
            boolean artistOk = want.length() > 0 && jstr(o, "artistName").toLowerCase().contains(want);
            // a loose search may only return the right song: by its length, or (when the player gives none) by its artist
            if (strict && (durS > 0 ? gap > 3 : !artistOk)) continue;
            int sc = (jstr(o, "syncedLyrics").length() > 0 ? 500 : 0) + (jstr(o, "plainLyrics").length() > 0 ? 1 : 0);
            if (durS > 0 && gap <= 2) sc += 200;
            if (artistOk) sc += 50;
            if (sc > score) { score = sc; best = o; }
        }
        return best;
    }

    private static JSONObject httpJson(String url) {
        String b = httpText(url);
        try { return b == null ? null : new JSONObject(b); } catch (Throwable t) { NET_ERR.set(Boolean.TRUE); return null; }
    }

    private static JSONArray httpArray(String url) {
        String b = httpText(url);
        try { return b == null ? null : new JSONArray(b); } catch (Throwable t) { NET_ERR.set(Boolean.TRUE); return null; }
    }

    /**
     * One GET. The connection is only torn down when something went wrong or the body was not read to its end: a clean finish
     * (including a small 404 body, which is drained) lets the platform keep the socket for the next request, so the exact lookup
     * plus up to three searches for one song share one TLS handshake instead of paying for each.
     */
    private static String httpText(String url) {
        HttpURLConnection con = null;
        boolean reusable = false;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(6000);
            con.setRequestProperty("User-Agent", "VoidIsland/" + Cards.appVersion + " (https://github.com/vxyzview/VoidIsland)");
            con.setRequestProperty("Accept", "application/json");
            int code = con.getResponseCode();
            if (code != 200) {   // 404 = the song is simply not there
                if (code != 404) NET_ERR.set(Boolean.TRUE);
                reusable = drain(con.getErrorStream());
                return null;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"), 8192);
            try {
                StringBuilder b = new StringBuilder(16384);
                char[] buf = new char[8192];
                for (int n; (n = r.read(buf)) > 0;) {
                    b.append(buf, 0, n);
                    if (b.length() > 600000) { NET_ERR.set(Boolean.TRUE); return null; }   // far bigger than any lyric sheet: don't buffer it (and not a believable "no lyrics")
                }
                reusable = true;   // read to the end: the socket can go back to the pool
                return b.length() > 0 ? b.toString() : null;
            } finally {
                try { r.close(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            NET_ERR.set(Boolean.TRUE);
            return null;
        } finally {
            if (con != null && !reusable) try { con.disconnect(); } catch (Throwable ignored) {}
        }
    }

    /** Reads (and closes) a small error body so the connection stays reusable. False when there was nothing to read or it was large. */
    private static boolean drain(java.io.InputStream es) {
        if (es == null) return false;
        try {
            byte[] buf = new byte[2048];
            int total = 0;
            for (int n; (n = es.read(buf)) > 0;) {
                total += n;
                if (total > 16384) return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try { es.close(); } catch (Throwable ignored) {}
        }
    }
}
