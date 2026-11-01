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
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Follows every active media session (Spotify, YouTube Music, ...) and mirrors the best one to the island. */
final class MediaTracker {
    /** Artwork lifted from players' notifications: a handful of players at most, bounded by size so it can never creep up. */
    private static final android.util.LruCache<String, Bitmap> ART = new android.util.LruCache<String, Bitmap>(1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return Math.max(1, b.getByteCount()); }
    };
    private static MediaTracker inst;

    private static final LinkedHashMap<String, String> ART_SIG = new LinkedHashMap<String, String>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > 8; }
    };

    /** Memory is tight: let go of the cached artwork (the picture on screen is held by its own event and stays). */
    static void trim() {
        ART.evictAll();
        ART_SIG.clear();
    }

    /** The demo song went away: put the real player (if any) back on the island right now. */
    static void restore() {
        MediaTracker t = inst;
        if (t != null) t.soon();
    }

    /** The display came on: catch up with whatever the player did while it was off. */
    static void screenOn() {
        MediaTracker t = inst;
        if (t != null) t.darkAt = 0;   // the cached answer is out of date now
        if (t != null && t.stale) {   // straight to the refresh: the power state may still read "off" a moment after the broadcast
            t.stale = false;
            t.h.removeCallbacks(t.refresh);
            t.h.postDelayed(t.refresh, 90);
        }
    }

    /** True when artwork for this exact track was already taken from {@code pkg}'s notification (players repost it every second or so). */
    static boolean artFresh(String pkg, String sig) {
        return sig.equals(ART_SIG.get(pkg)) && ART.get(pkg) != null;
    }

    static void art(String pkg, String sig, Bitmap b) {
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
    private boolean stale;   // a player changed while the display was off: refreshed when it comes back on
    private String artKey = "";
    private Bitmap artBmp;
    private int accent = Color.WHITE;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            // a media session can vanish between two calls (null playback state...): skip this round, never crash the process
            try { refreshNow(); } catch (Throwable t) { Dbg.e("media", t); /* the next session callback refreshes again */ }
        }
    };
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
        if (inst == this) inst = null;
        try { if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions); } catch (Throwable t) { /* ignore */ }
        for (Holder x : holders) { try { x.mc.unregisterCallback(x.cb); } catch (Throwable t) { /* ignore */ } }
        holders.clear();
        h.removeCallbacksAndMessages(null);
        artBmp = null;
        artKey = "";
        trim();
        Bus.remove("media");
    }

    private long darkAt;          // when the display state was last asked (uptime ms; 0 = ask again)
    private boolean darkNow;

    /**
     * No point rebuilding the island's music card for a dark screen. The system is asked directly rather than trusting a
     * flag that could get stuck, but at most once a second: chatty players fire several callbacks a second.
     */
    private boolean dark() {
        long now = SystemClock.uptimeMillis();
        if (darkAt != 0 && now - darkAt < 1000L) return darkNow;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            darkNow = pm != null && !pm.isInteractive();
        } catch (Throwable t) { darkNow = false; }
        darkAt = now;
        return darkNow;
    }

    void soon() {
        if (dark()) { stale = true; return; }
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
        stale = false;
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
        boolean own = false;   // true = the picture is our cached copy (one stable instance), false = fresh from the session
        if (art == null) { art = ART.get(pkg); own = art != null; }
        // getMetadata() is a binder call: every refresh hands back a NEW Bitmap object for the same picture, so the object's
        // identity says nothing. Re-scaling the art and re-reading its colour on every refresh was pure churn; size + track is enough.
        int asig = art == null ? 0 : (own ? System.identityHashCode(art) : art.getWidth() * 31 + art.getHeight());
        String key = pkg + "|" + e.title + "|" + e.text + "|" + own + asig;
        if (!key.equals(artKey)) {
            artKey = key;
            artBmp = Img.scaled(art, 256);
            accent = Img.dominant(artBmp, Cards.PURPLE);
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
