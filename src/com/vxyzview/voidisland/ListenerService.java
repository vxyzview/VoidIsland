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

    @Override public void onNotificationPosted(StatusBarNotification sbn, RankingMap rm) {
        handle(sbn, rm, false);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rm, int reason) {
        // our own absorb hides the original while the island shows it — that removal must not clear the banner
        if (sbn == null || reason == REASON_SNOOZED) return;
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
                Bus.post(e);
                absorb(e, sbn.getKey(), initial);
            }
        } catch (Throwable t) { /* one weird notification must never take the service down */ }
    }

    /** The island is the presentation for text banners: the original rests for a few seconds, then returns to the shade. */
    private void absorb(IslandEvent e, String nkey, boolean initial) {
        if (!Prefs.b(this, Prefs.K_ABSORB, true) || !Absorb.applies(e.type, e.live, initial)) return;
        try {
            snoozeNotification(nkey, Absorb.delayMs(e.durationMs));
        } catch (Throwable t) { /* stale key or access lost: the original simply keeps showing */ }
    }
}
