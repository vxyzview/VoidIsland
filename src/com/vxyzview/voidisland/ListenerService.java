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

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) Bus.removeByNkey(sbn.getKey(), false);
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
            }
        } catch (Throwable t) { /* one weird notification must never take the service down */ }
    }
}
