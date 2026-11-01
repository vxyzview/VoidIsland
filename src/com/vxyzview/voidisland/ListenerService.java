package com.vxyzview.voidisland;

import android.app.Notification;
import android.content.ComponentName;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Receives every notification on the phone and every media session. Needs "Notification access". */
public final class ListenerService extends NotificationListenerService {
    static volatile boolean connected;
    private MediaTracker media;

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        media = new MediaTracker(this, new ComponentName(this, ListenerService.class));
        media.start();
        try {
            StatusBarNotification[] now = getActiveNotifications();
            if (now != null) for (StatusBarNotification s : now) handle(s, null, true);
        } catch (Throwable t) { /* ignore */ }
    }

    @Override public void onListenerDisconnected() {
        connected = false;
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
        // our own absorb removes the original while the island shows it — that removal must not clear the banner
        if (sbn == null || reason == REASON_SNOOZED || reason == REASON_LISTENER_CANCEL) return;
        Bus.removeByNkey(sbn.getKey(), false);
    }

    private void handle(StatusBarNotification sbn, RankingMap rm, boolean initial) {
        try {
            int importance = -1;
            if (rm != null) {
                Ranking r = new Ranking();
                if (rm.getRanking(sbn.getKey(), r)) importance = r.matchesInterruptionFilter() ? r.getImportance() : 0;
            }
            Notification n = sbn.getNotification();
            IslandEvent e = Classifier.from(this, sbn.getPackageName(), n, sbn.getKey(), importance, initial);
            if (e == null) {
                Bus.removeByNkey(sbn.getKey(), true); // e.g. progress notification turned into "download complete"
            } else {
                e.nkey = sbn.getKey();
                if (!e.live) Bus.removeByNkey(sbn.getKey(), true);
                boolean showing = Bus.post(e);
                if (showing && e.chat != null) Rules.chatShown(e.chat, System.currentTimeMillis());
                absorb(e, sbn.getKey(), initial, showing);
            }
        } catch (Throwable t) { /* one weird notification must never take the service down */ }
    }

    /**
     * Opt-in ("Only show notifications on the island"): the original is removed so nothing shows twice, but only when the
     * island really took the banner (see {@link Absorb#shouldRemove}). Otherwise the notification is left alone.
     */
    private void absorb(IslandEvent e, String nkey, boolean initial, boolean islandShowing) {
        boolean kept = Prefs.has(this, Prefs.K_KEEP, e.pkg);   // "keep in shade" apps stay in the shade too
        if (!Absorb.shouldRemove(Prefs.b(this, Prefs.K_ABSORB, Prefs.ABSORB_DEF) && !Prefs.absorbUnconfirmed, kept, islandShowing, e.type, e.live, initial)) return;
        try {
            cancelNotification(nkey);
        } catch (Throwable t) { /* stale key or access lost: the original simply keeps showing */ }
    }
}
