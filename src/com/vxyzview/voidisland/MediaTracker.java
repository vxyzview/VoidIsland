package com.vxyzview.voidisland;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/** Follows every active media session (Spotify, YouTube Music, ...) and mirrors the best one to the island. */
final class MediaTracker {
    private static final android.util.LruCache<String, Bitmap> ART = new android.util.LruCache<String, Bitmap>(6);
    private static MediaTracker inst;

    private static final HashMap<String, String> ART_SIG = Lru.map(12);

    /** True when artwork for this exact track was already taken from {@code pkg}'s notification (players repost it every second or so). */
    static boolean artFresh(String pkg, String sig) {
        if (pkg == null || sig == null) return false;
        return sig.equals(ART_SIG.get(pkg)) && ART.get(pkg) != null;
    }

    static void art(String pkg, String sig, Bitmap b) {
        if (pkg == null || b == null) return;   // LruCache refuses nulls
        ART.put(pkg, b);
        ART_SIG.put(pkg, sig);
        if (inst != null) inst.soon();
    }

    private static final class Holder {
        MediaController mc;
        MediaController.Callback cb;
    }

    private final Context c;
    private final ComponentName cn;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ArrayList<Holder> holders = new ArrayList<Holder>();
    private MediaSessionManager msm;
    private String artKey = "";
    private Bitmap artBmp;
    private int accent = Color.WHITE;
    private String pendingKey = "";   // artwork of this song is being prepared on the worker thread

    private final Runnable refresh = new Runnable() { @Override public void run() { refreshNow(); } };
    private boolean expiring, gaveUp;
    private final Runnable expire = new Runnable() {
        @Override public void run() { expiring = false; gaveUp = true; Bus.remove("media"); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override public void onActiveSessionsChanged(List<MediaController> list) { sync(list); }
    };

    MediaTracker(Context c, ComponentName cn) {
        this.c = c;
        this.cn = cn;
    }

    void start() {
        inst = this;
        try {
            msm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            msm.addOnActiveSessionsChangedListener(sessions, cn, h);
            sync(msm.getActiveSessions(cn));
        } catch (Throwable t) { /* notification access not granted yet */ }
    }

    void stop() {
        if (inst == this) { inst = null; ART.evictAll(); ART_SIG.clear(); }   // nothing can show this artwork any more
        pendingKey = "";
        try { if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions); } catch (Throwable t) { /* ignore */ }
        for (Holder x : holders) { try { x.mc.unregisterCallback(x.cb); } catch (Throwable t) { /* ignore */ } }
        holders.clear();
        h.removeCallbacksAndMessages(null);
        Bus.remove("media");
    }

    void soon() {
        h.removeCallbacks(refresh);
        h.postDelayed(refresh, 90);
    }

    private void sync(List<MediaController> list) {
        if (list == null) list = Collections.emptyList();
        for (Iterator<Holder> it = holders.iterator(); it.hasNext();) {
            Holder x = it.next();
            boolean keep = false;
            for (MediaController m : list) if (m.getSessionToken().equals(x.mc.getSessionToken())) keep = true;
            if (!keep) { try { x.mc.unregisterCallback(x.cb); } catch (Throwable t) { /* ignore */ } it.remove(); }
        }
        for (MediaController m : list) {
            boolean have = false;
            for (Holder x : holders) if (x.mc.getSessionToken().equals(m.getSessionToken())) have = true;
            if (have) continue;
            Holder x = new Holder();
            x.mc = m;
            x.cb = new MediaController.Callback() {
                @Override public void onPlaybackStateChanged(PlaybackState s) { soon(); }
                @Override public void onMetadataChanged(MediaMetadata md) { soon(); }
                @Override public void onSessionDestroyed() { soon(); }
            };
            try { m.registerCallback(x.cb, h); holders.add(x); } catch (Throwable t) { /* ignore */ }
        }
        soon();
    }

    private static int score(MediaController mc) {
        PlaybackState s = mc.getPlaybackState();
        int st = s == null ? PlaybackState.STATE_NONE : s.getState();
        if (st == PlaybackState.STATE_PLAYING) return 3;
        if (st == PlaybackState.STATE_BUFFERING) return 2;
        if (st == PlaybackState.STATE_PAUSED) return 1;
        return 0;
    }

    private static String str(MediaMetadata md, String... keys) {
        if (md == null) return null;
        for (String k : keys) {
            String v = md.getString(k);
            if (v != null && v.trim().length() > 0) return v;
        }
        return null;
    }

    private void refreshNow() {
        Holder best = null;
        int bestScore = 0;
        for (Holder x : holders) {
            int s = score(x.mc);
            if (s > bestScore) { best = x; bestScore = s; }
        }
        if (best == null || !Prefs.b(c, Prefs.K_MEDIA, true)) {
            h.removeCallbacks(expire);
            Bus.remove("media");
            return;
        }
        MediaController mc = best.mc;
        PlaybackState ps = mc.getPlaybackState();
        MediaMetadata md = mc.getMetadata();
        String pkg = mc.getPackageName();

        IslandEvent e = new IslandEvent("media", IslandEvent.MEDIA);
        e.live = true;
        e.priority = 60;
        e.pkg = pkg;
        e.mc = mc;
        e.appName = Img.appLabel(c, pkg);
        e.title = str(md, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (e.title == null) e.title = e.appName;
        String artist = str(md, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        e.text = artist == null ? "" : artist;
        e.durMs = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        int st = ps.getState();
        e.playing = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING;
        e.posMs = ps.getPosition();
        e.posAt = ps.getLastPositionUpdateTime() > 0 ? ps.getLastPositionUpdateTime() : SystemClock.elapsedRealtime();
        e.speed = ps.getPlaybackSpeed() > 0 ? ps.getPlaybackSpeed() : 1f;
        e.actions = ps.getActions();
        e.icon = Img.appIcon(c, pkg);
        try { e.contentIntent = mc.getSessionActivity(); } catch (Throwable t) { /* ignore */ }

        Bitmap art = null;
        if (md != null) {
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        if (art == null) art = ART.get(pkg);
        final String key = pkg + "|" + e.title + "|" + e.text + "|" + (art == null ? 0 : System.identityHashCode(art));
        if (!key.equals(artKey)) {
            if (art == null) {                       // nothing to scale: settle at once
                artKey = key;
                artBmp = null;
                accent = Img.dominant(null, Cards.PURPLE);
            } else if (!key.equals(pendingKey)) {
                // Scaling and the accent colour are the slow part of a track change: do them on the worker and
                // post the song once they are ready, rather than blocking the main thread (or showing it twice).
                pendingKey = key;
                final Bitmap src = art;
                try {
                    Img.BG.execute(new Runnable() {
                        @Override public void run() {
                            long t0 = System.nanoTime();
                            Bitmap sc = null;
                            int ac = Cards.PURPLE;
                            try { sc = Img.scaled(src, 256); ac = Img.dominant(sc, Cards.PURPLE); } catch (Throwable t) { /* keep the fallbacks */ }
                            final Bitmap fsc = sc;
                            final int fac = ac;
                            final long took = System.nanoTime() - t0;
                            h.post(new Runnable() {
                                @Override public void run() {
                                    if (!key.equals(pendingKey)) return;   // the song moved on, or we stopped
                                    pendingKey = "";
                                    artKey = key;
                                    artBmp = fsc;
                                    accent = fac;
                                    Perf.record(Perf.ART, took);
                                    refreshNow();
                                }
                            });
                        }
                    });
                } catch (Throwable t) {                // worker refused the job: do it here, as before
                    pendingKey = "";
                    artKey = key;
                    artBmp = Img.scaled(art, 256);
                    accent = Img.dominant(artBmp, Cards.PURPLE);
                }
            }
            if (!key.equals(artKey)) return;         // artwork still on its way: refreshNow() runs again when it lands
        }
        e.art = artBmp;
        e.accent = accent;

        if (e.playing) {
            gaveUp = false;
            expiring = false;
            h.removeCallbacks(expire);
        } else if (gaveUp) {
            Bus.remove("media"); // paused for a long time: stay out of the way until it plays again
            return;
        } else if (!expiring) {
            expiring = true;
            h.postDelayed(expire, 12000); // paused: island lets go after 12s
        }
        Bus.post(e);
    }
}
