package com.vxyzview.voidisland;

import android.app.Notification;
import android.content.ComponentName;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Receives every notification on the phone and every media session. Needs "Notification access". */
public final class ListenerService extends NotificationListenerService {
    static volatile boolean connected;
    private static ListenerService inst;
    /** Keys this app removed itself (absorbing a banner): their removal callback must not clear the banner. Main thread only. */
    private static final java.util.LinkedHashSet<String> SELF = new java.util.LinkedHashSet<String>();
    private MediaTracker media;

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        inst = this;
        media = new MediaTracker(this, new ComponentName(this, ListenerService.class));
        media.start();
        try {
            StatusBarNotification[] now = getActiveNotifications();
            if (now != null && now.length > 0) scan(now, 0);
        } catch (Throwable t) { Dbg.e("initial-scan", t); }
    }

    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    /** What was already showing is read in small steps, so a phone with a hundred notifications never freezes while we connect. */
    private void scan(final StatusBarNotification[] all, final int from) {
        if (!connected) return;
        int to = Math.min(all.length, from + 8);
        for (int i = from; i < to; i++) handle(all[i], null, true);
        if (to < all.length) {
            main.post(new Runnable() { @Override public void run() { scan(all, to2(all, from)); } });
        }
    }

    private static int to2(StatusBarNotification[] all, int from) { return Math.min(all.length, from + 8); }

    @Override public void onListenerDisconnected() {
        connected = false;
        if (inst == this) inst = null;
        if (media != null) media.stop();
        media = null;
        super.onListenerDisconnected();
        // some OEMs kill listeners in the background: ask the system to bind us again (no-op if the user turned it off)
        try { NotificationListenerService.requestRebind(new ComponentName(this, ListenerService.class)); } catch (Throwable t) { /* ignore */ }
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        try {
            if (Trim.shortage(level)) { Img.trim(); MediaTracker.trim(); }
        } catch (Throwable t) { /* never crash over a cache */ }
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn, RankingMap rm) {
        handle(sbn, rm, false);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rm, int reason) {
        if (sbn == null || reason == REASON_SNOOZED) return;
        // our own absorb removes the original while the island shows it: that removal must not clear the banner.
        // A removal by ANOTHER listener (watch, Android Auto) is a real one and must also clear live pills (call, timer...).
        if (reason == REASON_LISTENER_CANCEL && SELF.remove(sbn.getKey())) return;
        Bus.removeByNkey(sbn.getKey(), false);
    }

    private void handle(StatusBarNotification sbn, RankingMap rm, boolean initial) {
        try {
            int importance = -1;
            if (rm != null) {
                Ranking r = new Ranking();
                if (rm.getRanking(sbn.getKey(), r)) importance = r.matchesInterruptionFilter() ? r.getImportance() : 0;
            }
            SELF.remove(sbn.getKey());   // posted again: a later removal is not ours
            Notification n = sbn.getNotification();
            IslandEvent e = Classifier.from(this, sbn.getPackageName(), n, sbn.getKey(), importance, initial);
            if (e == null) {
                Bus.removeByNkey(sbn.getKey(), true); // e.g. progress notification turned into "download complete"
            } else {
                e.nkey = sbn.getKey();
                if (!e.live) Bus.removeByNkey(sbn.getKey(), true);
                boolean showing = Bus.post(e);
                if (showing && e.chat != null) Rules.chatShown(e.chat, System.currentTimeMillis());
                // the original is removed only once the island has really put this banner on screen (see onBannerShown)
                e.absorbOk = showing && Absorb.applies(e.type, e.live, initial);
            }
        } catch (Throwable t) { Dbg.e("notification", t); /* one weird notification must never take the service down */ }
    }

    /** The island just put {@code e} on screen: now (and only now) its original may leave the shade. */
    static void onBannerShown(IslandEvent e) {
        ListenerService s = inst;
        if (s == null || e == null || !e.absorbOk || e.nkey == null) return;
        e.absorbOk = false;
        s.absorb(e, e.nkey, false, true);
    }

    /**
     * Opt-in ("Only show notifications on the island"): the original is removed so nothing shows twice, but only when the
     * island really took the banner (see {@link Absorb#shouldRemove}). Otherwise the notification is left alone.
     */
    private void absorb(IslandEvent e, String nkey, boolean initial, boolean islandShowing) {
        boolean kept = Prefs.has(this, Prefs.K_KEEP, e.pkg);   // "keep in shade" apps stay in the shade too
        if (!Absorb.shouldRemove(Prefs.b(this, Prefs.K_ABSORB, Prefs.ABSORB_DEF) && !Prefs.absorbUnconfirmed, kept, islandShowing, e.type, e.live, initial)) return;
        try {
            if (SELF.size() > 64) SELF.remove(SELF.iterator().next());
            SELF.add(nkey);
            cancelNotification(nkey);
        } catch (Throwable t) { SELF.remove(nkey); /* stale key or access lost: the original simply keeps showing */ }
    }
}
