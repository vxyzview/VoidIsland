package com.vxyzview.voidisland;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

/** Turns an Android notification into the right island event: call, timer, navigation, download, payment or plain banner. */
final class Classifier {
    private Classifier() {}

    /** Recently seen notifications (key -> {signature hash, time}), least recently used first out, so a busy phone never forgets everything at once. */
    private static final java.util.LinkedHashMap<String, long[]> SEEN = new java.util.LinkedHashMap<String, long[]>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, long[]> eldest) { return size() > 300; }
    };

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

    /** The first inline-reply action that takes free text, so the island can offer a "Reply" button. */
    private static void reply(IslandEvent e, Notification n) {
        if (n.actions == null) return;
        for (Notification.Action a : n.actions) {
            if (a == null || a.actionIntent == null) continue;
            RemoteInput[] ri = a.getRemoteInputs();
            if (ri == null) continue;
            for (RemoteInput r : ri) {
                if (r.getAllowFreeFormInput()) { e.replyIntent = a.actionIntent; e.replyInput = r; return; }
            }
        }
    }

    /** Island-only rules for text banners: quiet hours, group chats, one banner per chat. True = keep this one off the island. */
    private static boolean ruleQuiet(Context c, String pkg, Bundle x, CharSequence title) {
        if (Prefs.b(c, Prefs.K_QUIET, false)) {
            Calendar cal = Calendar.getInstance();
            int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            if (Rules.inQuiet(now, Prefs.i(c, Prefs.K_QFROM, 22 * 60), Prefs.i(c, Prefs.K_QTO, 7 * 60))
                && Rules.quietCovers(pkg, Prefs.view(c, Prefs.K_QAPPS))) return true;
        }
        CharSequence conv = x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        boolean group = x.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) || !empty(conv);
        if (group && Prefs.b(c, Prefs.K_NOGROUP, false)) return true;
        if (Prefs.b(c, Prefs.K_ONEPERCHAT, false)
            && Rules.chatQuiet(chatKey(pkg, x, title), System.currentTimeMillis(), Rules.CHAT_WINDOW_MS)) return true;
        return false;
    }

    /** Identifies a chat for the "one banner per chat" rule. */
    private static String chatKey(String pkg, Bundle x, CharSequence title) {
        CharSequence conv = x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        return pkg + "|" + (empty(conv) ? String.valueOf(title) : conv.toString());
    }

    /** A chronometer read out of a notification's custom layout. */
    private static final class Chrono {
        long when;           // wall-clock base, like Notification.when
        boolean countDown, running;
    }

    private static Chronometer findChrono(View v) {
        if (v instanceof Chronometer) return (Chronometer) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                Chronometer c = findChrono(g.getChildAt(i));
                if (c != null) return c;
            }
        }
        return null;
    }

    /**
     * Many clock apps draw their stopwatch / timer with their own Chronometer inside a custom layout and never set the
     * standard "show chronometer" flag, so the island used to miss them. Inflate the layout and read the base from it.
     * Best effort: any failure just means "not a timer".
     */
    @SuppressWarnings("deprecation")
    private static Chrono customChrono(Context c, Notification n) {
        RemoteViews[] all = { n.contentView, n.bigContentView, n.headsUpContentView };
        for (RemoteViews rv : all) {
            if (rv == null) continue;
            try {
                Chronometer ch = findChrono(rv.apply(c, new FrameLayout(c)));
                if (ch == null) continue;
                Chrono k = new Chrono();
                k.when = Parse.wallBase(ch.getBase(), SystemClock.elapsedRealtime(), System.currentTimeMillis());
                k.countDown = ch.isCountDown();
                k.running = true;
                try {   // a paused stopwatch keeps its Chronometer but stops it; the flag is not public, so peek (assume running if hidden)
                    java.lang.reflect.Field f = Chronometer.class.getDeclaredField("mStarted");
                    f.setAccessible(true);
                    k.running = f.getBoolean(ch);
                } catch (Throwable t) { /* hidden: keep the assumption */ }
                return k;
            } catch (Throwable t) { /* layout from a package we can't inflate */ }
        }
        return null;
    }

    private static boolean duplicate(String nkey, String sig) {
        long now = System.currentTimeMillis();
        long[] seen = SEEN.get(nkey);
        if (seen != null && seen[0] == sig.hashCode() && now - seen[1] < 30000) return true;
        SEEN.put(nkey, new long[]{sig.hashCode(), now});
        return false;
    }

    /**
     * @param importance NotificationManager.IMPORTANCE_* or -1 if unknown
     * @param initial    true while scanning what was already showing when we connected (only live things count)
     * @return null when the notification should not appear on the island
     */
    static IslandEvent from(Context c, String pkg, Notification n, String nkey, int importance, boolean initial) {
        if (pkg == null || n == null || c.getPackageName().equals(pkg)) return null;
        if (Prefs.has(c, Prefs.K_BLOCK, pkg)) return null;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return null;
        Bundle x = n.extras != null ? n.extras : new Bundle();
        String cat = n.category;
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;

        // Music is handled through its media session; just remember the artwork.
        if (Notification.CATEGORY_TRANSPORT.equals(cat) || x.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            String sig = x.getCharSequence(Notification.EXTRA_TITLE) + "|" + x.getCharSequence(Notification.EXTRA_TEXT);
            if (MediaTracker.artFresh(pkg, sig)) return null;   // same song: skip the icon decode + refresh
            Bitmap li = largeIcon(c, n);
            if (li != null) MediaTracker.art(pkg, sig, li);
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
        boolean timerLike = chrono && ongoing;
        long tWhen = n.when;
        boolean tCount = x.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN);
        if (!chrono && ongoing && (Notification.CATEGORY_ALARM.equals(cat) || Parse.isClockApp(pkg))) {
            Chrono k = customChrono(c, n);   // the clock app's own stopwatch / timer layout
            if (k != null && k.running) { timerLike = true; tWhen = k.when; tCount = k.countDown; }
        }
        if (timerLike) {
            if (!Prefs.b(c, Prefs.K_NOTIF, true)) return null;
            IslandEvent e = new IslandEvent("t:" + nkey, IslandEvent.TIMER);
            e.live = true; e.priority = 80; e.pkg = pkg; e.appName = app;
            e.title = empty(title) ? app : title;
            e.countDown = tCount;
            e.when = tWhen;
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

        // ---- live activities: rides, food delivery, live scores (judged by the app that posted it)
        int lk = Live.kind(pkg, Prefs.view(c, Prefs.K_LIVE_RIDE), Prefs.view(c, Prefs.K_LIVE_FOOD), Prefs.view(c, Prefs.K_LIVE_SPORT));
        if (lk != Live.NONE && ongoing && (!empty(title) || !empty(text)) && Prefs.b(c, Prefs.K_LIVEACT, true)) {
            IslandEvent e = new IslandEvent("l:" + nkey, IslandEvent.LIVE);
            e.live = true; e.priority = 60; e.pkg = pkg; e.appName = app;
            e.liveKind = lk;
            e.accent = Live.accent(lk);
            e.title = empty(title) ? app : title;
            e.text = text;
            e.icon = Img.appIcon(c, pkg);
            int lmax = x.getInt(Notification.EXTRA_PROGRESS_MAX), lprog = x.getInt(Notification.EXTRA_PROGRESS);
            boolean indet = x.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE);
            e.progress = lmax > 0 && !indet ? Math.max(0, Math.min(100, Math.round(lprog * 100f / lmax))) : -1;
            e.contentIntent = n.contentIntent;
            actions(e, n);
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
        boolean messaging = Notification.CATEGORY_MESSAGE.equals(cat) || x.containsKey(Notification.EXTRA_MESSAGES)
            || x.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) || !empty(x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))
            || String.valueOf(x.getCharSequence(Notification.EXTRA_TEMPLATE)).contains("MessagingStyle");
        if (Parse.isPaymentFrom(messaging, pkg, tt, bt)) {
            if (!Prefs.b(c, Prefs.K_PAY, true)) return null;
            String all = tt + " " + bt;
            IslandEvent e = new IslandEvent("pay:" + nkey, IslandEvent.PAYMENT);
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
        if (ruleQuiet(c, pkg, x, title)) return null;
        IslandEvent e = new IslandEvent("n:" + nkey, IslandEvent.NOTIF);
        e.pkg = pkg; e.appName = app; e.nkey = nkey;
        e.title = empty(title) ? app : title;
        e.text = text;
        Bitmap li = largeIcon(c, n);
        e.icon = li != null ? li : Img.appIcon(c, pkg);
        e.durationMs = Prefs.i(c, Prefs.K_DUR, 5000);
        e.contentIntent = n.contentIntent;
        if (Prefs.b(c, Prefs.K_ONEPERCHAT, false)) e.chat = chatKey(pkg, x, title);
        actions(e, n);
        reply(e, n);
        return e;
    }
}
