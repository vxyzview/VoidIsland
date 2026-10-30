package com.vxyzview.voidisland;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashMap;

/** Turns an Android notification into the right island event: call, timer, navigation, download, payment or plain banner. */
final class Classifier {
    private Classifier() {}

    private static final HashMap<String, Integer> SEEN = new HashMap<String, Integer>();
    private static final HashMap<String, Long> SEEN_AT = new HashMap<String, Long>();

    private static boolean empty(CharSequence s) { return s == null || s.toString().trim().length() == 0; }

    static Bitmap largeIcon(Context c, Notification n) {
        try {
            Icon i = n.getLargeIcon();
            if (i != null) return Img.icon(c, i, 160);
        } catch (Throwable t) { /* icon from a package we can't see */ }
        return null;
    }

    private static CharSequence lastMessage(Bundle x, CharSequence title) {
        try {
            Parcelable[] ms = x.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (ms != null && ms.length > 0 && ms[ms.length - 1] instanceof Bundle) {
                Bundle b = (Bundle) ms[ms.length - 1];
                CharSequence t = b.getCharSequence("text");
                CharSequence who = b.getCharSequence("sender");
                if (!empty(t)) return (!empty(who) && !who.toString().equals(String.valueOf(title))) ? who + ": " + t : t;
            }
        } catch (Throwable t) { /* malformed extras */ }
        return null;
    }

    private static void actions(IslandEvent e, Notification n) {
        if (n.actions == null) return;
        ArrayList<String> ls = new ArrayList<String>();
        ArrayList<PendingIntent> ps = new ArrayList<PendingIntent>();
        for (Notification.Action a : n.actions) {
            if (a == null || a.actionIntent == null || empty(a.title)) continue;
            if (a.getRemoteInputs() != null && a.getRemoteInputs().length > 0) continue; // inline reply needs a keyboard
            ls.add(a.title.toString());
            ps.add(a.actionIntent);
            if (ls.size() == 3) break;
        }
        e.actLabels = ls.toArray(new String[0]);
        e.actIntents = ps.toArray(new PendingIntent[0]);
        e.actRuns = new Runnable[ls.size()];
    }

    private static boolean duplicate(String nkey, String sig) {
        long now = System.currentTimeMillis();
        Integer h = SEEN.get(nkey);
        Long at = SEEN_AT.get(nkey);
        if (h != null && at != null && h == sig.hashCode() && now - at < 30000) return true;
        if (SEEN.size() > 300) { SEEN.clear(); SEEN_AT.clear(); }
        SEEN.put(nkey, sig.hashCode());
        SEEN_AT.put(nkey, now);
        return false;
    }

    /**
     * @param importance NotificationManager.IMPORTANCE_* or -1 if unknown
     * @param initial    true while scanning what was already showing when we connected (only live things count)
     * @return null when the notification should not appear on the island
     */
    static IslandEvent from(Context c, String pkg, Notification n, String nkey, int importance, boolean initial) {
        if (pkg == null || n == null || c.getPackageName().equals(pkg)) return null;
        if (Prefs.blocked(c).contains(pkg)) return null;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return null;
        Bundle x = n.extras != null ? n.extras : new Bundle();
        String cat = n.category;
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;

        // Music is handled through its media session; just remember the artwork.
        if (Notification.CATEGORY_TRANSPORT.equals(cat) || x.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            Bitmap li = largeIcon(c, n);
            if (li != null) MediaTracker.art(pkg, li);
            return null;
        }

        CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = x.getCharSequence(Notification.EXTRA_TEXT);
        if (empty(text)) text = lastMessage(x, title);
        if (empty(text)) text = x.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (empty(text)) text = x.getCharSequence(Notification.EXTRA_SUB_TEXT);
        String app = Img.appLabel(c, pkg);
        boolean chrono = x.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER);

        // ---- phone calls
        boolean callish = Notification.CATEGORY_CALL.equals(cat) || x.containsKey("android.callType");
        if (callish && (ongoing || n.fullScreenIntent != null)) {
            if (!Prefs.b(c, Prefs.K_CALL, true)) return null;
            int ct = x.getInt("android.callType", 0);
            IslandEvent e = new IslandEvent("call", IslandEvent.CALL);
            e.incoming = ct == 1 || (ct == 0 && !chrono);
            e.live = true;
            e.popup = e.incoming;
            e.priority = 100;
            e.pkg = pkg; e.appName = app;
            e.title = empty(title) ? "Unknown" : title;
            e.text = empty(text) ? (e.incoming ? "Incoming call" : "On call") : text;
            e.chrono = chrono && !e.incoming;
            e.when = n.when;
            e.icon = largeIcon(c, n);
            e.contentIntent = n.contentIntent;
            actions(e, n);
            return e;
        }

        // ---- timers / stopwatches
        if (chrono && ongoing) {
            if (!Prefs.b(c, Prefs.K_NOTIF, true)) return null;
            IslandEvent e = new IslandEvent("t:" + nkey, IslandEvent.TIMER);
            e.live = true; e.priority = 80; e.pkg = pkg; e.appName = app;
            e.title = empty(title) ? app : title;
            e.countDown = x.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN);
            e.when = n.when;
            e.contentIntent = n.contentIntent;
            actions(e, n);
            return e;
        }

        // ---- turn-by-turn navigation
        if (Notification.CATEGORY_NAVIGATION.equals(cat) && ongoing) {
            if (!Prefs.b(c, Prefs.K_NOTIF, true)) return null;
            IslandEvent e = new IslandEvent("nav", IslandEvent.NAV);
            e.live = true; e.priority = 70; e.pkg = pkg; e.appName = app;
            e.title = empty(title) ? app : title;
            e.text = text;
            e.icon = Img.appIcon(c, pkg);
            e.contentIntent = n.contentIntent;
            return e;
        }

        // ---- downloads / uploads / anything with a real progress bar
        int max = x.getInt(Notification.EXTRA_PROGRESS_MAX), prog = x.getInt(Notification.EXTRA_PROGRESS);
        if (max > 0 && prog < max && (ongoing || !initial)) {
            if (!Prefs.b(c, Prefs.K_NOTIF, true)) return null;
            IslandEvent e = new IslandEvent("p:" + nkey, IslandEvent.PROGRESS);
            e.live = true; e.priority = 50; e.pkg = pkg; e.appName = app;
            e.title = empty(title) ? app : title;
            e.text = text;
            e.progress = Math.round(prog * 100f / max);
            e.icon = Img.appIcon(c, pkg);
            e.accent = Cards.GREEN;
            e.contentIntent = n.contentIntent;
            return e;
        }

        if (initial || ongoing) return null; // from here on only fresh, dismissible notifications
        if (empty(title) && empty(text)) return null;
        if (importance >= 0 && importance < NotificationManager.IMPORTANCE_DEFAULT) return null;
        if (n.getGroupAlertBehavior() == Notification.GROUP_ALERT_SUMMARY) return null;
        if (Notification.CATEGORY_SERVICE.equals(cat) || Notification.CATEGORY_SYSTEM.equals(cat)
            || Notification.CATEGORY_STATUS.equals(cat) || Notification.CATEGORY_PROGRESS.equals(cat)) return null;
        if (duplicate(nkey, String.valueOf(title) + "|" + text)) return null;

        // ---- payments
        String tt = empty(title) ? "" : title.toString(), bt = empty(text) ? "" : text.toString();
        if (Parse.isPayment(pkg, tt, bt)) {
            if (!Prefs.b(c, Prefs.K_PAY, true)) return null;
            String all = tt + " " + bt;
            IslandEvent e = new IslandEvent("pay", IslandEvent.PAYMENT);
            e.pkg = pkg; e.appName = app; e.nkey = nkey;
            e.success = !Parse.isFailure(all);
            e.incoming = Parse.isIncoming(all);
            e.title = !e.success ? "Payment failed" : (e.incoming ? "Payment received" : "Payment successful");
            e.sub = Parse.amount(all);
            e.text = empty(title) ? text : title;
            e.icon = Img.appIcon(c, pkg);
            e.durationMs = 5200;
            e.contentIntent = n.contentIntent;
            return e;
        }

        // ---- everything else: a banner
        if (!Prefs.b(c, Prefs.K_NOTIF, true)) return null;
        IslandEvent e = new IslandEvent("n:" + nkey, IslandEvent.NOTIF);
        e.pkg = pkg; e.appName = app; e.nkey = nkey;
        e.title = empty(title) ? app : title;
        e.text = text;
        Bitmap li = largeIcon(c, n);
        e.icon = li != null ? li : Img.appIcon(c, pkg);
        e.durationMs = Prefs.i(c, Prefs.K_DUR, 5000);
        e.contentIntent = n.contentIntent;
        actions(e, n);
        return e;
    }
}
