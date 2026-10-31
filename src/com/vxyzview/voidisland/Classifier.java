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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

/** Turns an Android notification into the right island event: call, timer, navigation, download, payment or plain banner. */
final class Classifier {
    private Classifier() {}

    /** Last banner per notification: {signature hash, time}. Oldest entries fall out one by one (never a mass wipe that re-shows duplicates). */
    private static final HashMap<String, long[]> SEEN = Lru.map(300);

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
                && Rules.quietCovers(pkg, Prefs.live(c, Prefs.K_QAPPS))) return true;
        }
        CharSequence conv = x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        boolean group = x.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) || !empty(conv);
        if (group && Prefs.b(c, Prefs.K_NOGROUP, false)) return true;
        if (Prefs.b(c, Prefs.K_ONEPERCHAT, false)) {
            String chat = pkg + "|" + (empty(conv) ? String.valueOf(title) : conv.toString());
            if (Rules.chatRepeat(chat, System.currentTimeMillis(), Rules.CHAT_WINDOW_MS)) return true;
        }
        return false;
    }

    /** A chronometer read out of a notification's custom layout. */
    private static final class Chrono {
        long when;           // wall-clock base, like Notification.when
        boolean countDown, running;
    }

    /** What a clock app's own notification layout shows: its Chronometer (if it has one) and every piece of text in it. */
    private static final class Probe {
        Chrono k;
        String text = "";
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

    private static void collectText(View v, StringBuilder sb) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.length() > 0) sb.append(t).append('\n');
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectText(g.getChildAt(i), sb);
        }
    }

    /**
     * Many clock apps draw their stopwatch / timer with their own Chronometer inside a custom layout and never set the
     * standard "show chronometer" flag, so the island used to miss them. Inflate the layout and read the base from it;
     * when there is no Chronometer, hand back the layout's text so a plain-text stopwatch can still be read.
     * Best effort: any failure just means "not a timer".
     */
    @SuppressWarnings("deprecation")
    private static Probe probe(Context c, Notification n) {
        Probe out = new Probe();
        RemoteViews[] all = { n.contentView, n.bigContentView, n.headsUpContentView };
        for (RemoteViews rv : all) {
            if (rv == null) continue;
            try {
                View root = rv.apply(c, new FrameLayout(c));
                Chronometer ch = findChrono(root);
                if (ch != null) {
                    Chrono k = new Chrono();
                    k.when = Parse.wallBase(ch.getBase(), SystemClock.elapsedRealtime(), System.currentTimeMillis());
                    k.countDown = ch.isCountDown();
                    k.running = true;
                    try {   // a paused stopwatch keeps its Chronometer but stops it; the flag is not public, so peek (assume running if hidden)
                        java.lang.reflect.Field f = Chronometer.class.getDeclaredField("mStarted");
                        f.setAccessible(true);
                        k.running = f.getBoolean(ch);
                    } catch (Throwable t) { /* hidden: keep the assumption */ }
                    out.k = k;
                    return out;
                }
                StringBuilder sb = new StringBuilder();
                collectText(root, sb);
                if (out.text.length() == 0) out.text = sb.toString();
            } catch (Throwable t) { /* layout from a package we can't inflate */ }
        }
        return out;
    }

    /** A Chronometer found in a clock app's layout, remembered so a once-a-second repost does not inflate the layout again. */
    private static final class Probed {
        String sig;
        long at;
        Probe probe;
    }

    private static final HashMap<String, Probed> PROBES = Lru.map(24);
    private static final long PROBE_TTL_MS = 10000L;   // never trust a cached reading for longer than this

    /**
     * {@link #probe} with a memory. The Chronometer base only changes when the app restarts / pauses / resumes, which also changes
     * its buttons (Pause <-> Resume) or its post time, so the same signature means the same answer. Text-only layouts (no
     * Chronometer) change with every repost and are never cached.
     */
    private static Probe probeCached(Context c, Notification n, String nkey) {
        StringBuilder sg = new StringBuilder().append(n.when);
        if (n.actions != null) for (Notification.Action a : n.actions) if (a != null) sg.append('|').append(a.title);
        String sig = sg.toString();
        long now = SystemClock.elapsedRealtime();
        Probed p = nkey == null ? null : PROBES.get(nkey);
        if (p != null && p.sig.equals(sig) && now - p.at < PROBE_TTL_MS) return p.probe;
        long t0 = Perf.begin();
        Probe fresh = probe(c, n);
        Perf.end(Perf.PROBE, t0);
        if (nkey != null && fresh.k != null) {
            Probed q = new Probed();
            q.sig = sig;
            q.at = now;
            q.probe = fresh;
            PROBES.put(nkey, q);
        } else if (nkey != null) {
            PROBES.remove(nkey);
        }
        return fresh;
    }

    /** Count-down lengths seen so far, by notification: {full length, last remaining}. */
    private static final HashMap<String, long[]> TOTAL = Lru.map(60);   // LRU: a running count-down never loses its full length to a wipe

    private static long total(String nkey, long left, long now) {
        long[] st = TOTAL.get(nkey);
        if (st == null || left > st[1] + 1500L) st = new long[]{Parse.totalMs(0L, left), left};   // first sight, or restarted / extended
        st[0] = Parse.totalMs(st[0], left);
        st[1] = left;
        TOTAL.put(nkey, st);
        return st[0];
    }

    /** A notification is gone: drop what was remembered about it. */
    static void forget(String nkey) {
        TOTAL.remove(nkey);
        SEEN.remove(nkey);
        PROBES.remove(nkey);
    }

    private static boolean duplicate(String nkey, String sig) {
        long now = System.currentTimeMillis();
        long[] last = SEEN.get(nkey);
        int h = sig.hashCode();
        if (last != null && last[0] == h && now - last[1] < 30000) return true;
        SEEN.put(nkey, new long[]{h, now});
        return false;
    }

    /**
     * @param importance NotificationManager.IMPORTANCE_* or -1 if unknown
     * @param initial    true while scanning what was already showing when we connected (only live things count)
     * @return null when the notification should not appear on the island
     */
    static IslandEvent from(Context c, String pkg, Notification n, String nkey, int importance, boolean initial) {
        return from(c, pkg, n, nkey, importance, initial, 0L);
    }

    /** @param postTime when the notification was last posted (wall clock), or 0 if unknown: a paused stopwatch froze at that moment */
    static IslandEvent from(Context c, String pkg, Notification n, String nkey, int importance, boolean initial, long postTime) {
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

        // ---- timers / stopwatches (from the system clock, Google Clock, Samsung / Xiaomi / OnePlus clocks and other timer apps)
        boolean sticky = ongoing || (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
        boolean timerLike = chrono && sticky;
        long tWhen = n.when;
        boolean tCount = x.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN);
        boolean tPaused = false;
        CharSequence tSnap = null;
        boolean clockish = sticky && (Notification.CATEGORY_ALARM.equals(cat) || "stopwatch".equals(cat) || Parse.isClockApp(pkg));
        IslandEvent probeActs = null;   // only to read the button labels
        if (clockish || timerLike) {
            probeActs = new IslandEvent("probe", IslandEvent.TIMER);
            actions(probeActs, n);
        }
        boolean actPaused = probeActs != null && Parse.pausedByActions(probeActs.actLabels);
        if (!chrono && clockish) {
            Probe pr = probeCached(c, n, nkey);   // the clock app's own stopwatch / timer layout
            if (pr.k != null) {
                timerLike = true; tWhen = pr.k.when; tCount = pr.k.countDown;
                tPaused = !pr.k.running || actPaused;
            } else {
                // No Chronometer anywhere: some clock apps print the readout as text and repost it as it changes.
                // Only believe it when the notification says it is a stopwatch / timer.
                StringBuilder all = new StringBuilder();
                if (!empty(title)) all.append(title).append('\n');
                if (!empty(text)) all.append(text).append('\n');
                CharSequence big = x.getCharSequence(Notification.EXTRA_BIG_TEXT), subT = x.getCharSequence(Notification.EXTRA_SUB_TEXT);
                if (!empty(big)) all.append(big).append('\n');
                if (!empty(subT)) all.append(subT).append('\n');
                all.append(pr.text);
                long ms = Parse.findClockMs(all);
                StringBuilder hint = new StringBuilder(all);
                for (String l : probeActs.actLabels) hint.append('\n').append(l);
                boolean said = "stopwatch".equals(cat) || Parse.timerWords(hint);   // not the alarm category alone: "Alarm 7:00" is not a 7 minute timer
                if (ms >= 0 && said) {
                    timerLike = true;
                    tSnap = Parse.clock(ms);
                    tPaused = actPaused;
                    tCount = all.toString().toLowerCase(java.util.Locale.ROOT).contains("timer");
                    tWhen = System.currentTimeMillis();
                }
            }
        } else if (timerLike && actPaused) {
            tPaused = true;   // standard chronometer, but the app offers "Resume": it is stopped
        }
        if (timerLike) {
            if (!Prefs.b(c, Prefs.K_NOTIF, true)) return null;
            long now = System.currentTimeMillis();
            IslandEvent e = new IslandEvent("t:" + nkey, IslandEvent.TIMER);
            e.live = true; e.priority = tPaused ? 55 : 80; e.pkg = pkg; e.appName = app;
            e.title = empty(title) ? app : title;
            e.text = app;
            e.sub = tCount ? "Timer" : "Stopwatch";
            e.countDown = tCount;
            e.when = tWhen;
            e.paused = tPaused;
            e.snap = tSnap;
            if (tPaused) e.frozenMs = Parse.frozenMs(tWhen, tCount, postTime, now);
            if (tCount && tSnap == null) e.totalMs = total(nkey, tPaused ? e.frozenMs : Math.max(0L, tWhen - now), now);
            e.icon = Img.appIcon(c, pkg);
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
        int lk = Live.kind(pkg);
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
        if (ruleQuiet(c, pkg, x, title)) return null;
        IslandEvent e = new IslandEvent("n:" + nkey, IslandEvent.NOTIF);
        e.pkg = pkg; e.appName = app; e.nkey = nkey;
        e.title = empty(title) ? app : title;
        e.text = text;
        Bitmap li = largeIcon(c, n);
        e.icon = li != null ? li : Img.appIcon(c, pkg);
        e.durationMs = Prefs.i(c, Prefs.K_DUR, 5000);
        e.contentIntent = n.contentIntent;
        actions(e, n);
        reply(e, n);
        return e;
    }
}
