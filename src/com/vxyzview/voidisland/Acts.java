package com.vxyzview.voidisland;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The flat designs for every activity (Style > Activities). Style 0 is the classic pill / card that {@link Cards} still builds,
 * the rest live here and are shared by all nine activities: each activity only tells {@link M} what its title, number, progress and
 * facts are, and the designs draw that.
 *
 * Compact pill: 1 Capsule (tonal icon tile + a filled accent capsule with the number), 2 Track (icon, title over a slim progress line,
 * number), 3 Focus (the number centred), 4 Signal (icon + a live gauge: voice bars, battery cells, a ring or pulsing dots).
 * Expanded card: 1 Bento (flat tonal tiles), 2 Hero (one big centred number), 3 Strip (one slim row), 4 Stats (tiles of facts).
 *
 * Strictly flat: solid fills, tonal containers and thin lines, no gradients, no blur, no glow. Every design follows the accent
 * (Material You or custom), the text scale and the lock-screen rules (a hidden banner shows no facts and no buttons).
 */
final class Acts {
    private Acts() {}

    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT, MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    // ================================================================== what every design draws

    /** Reads the live numbers of one event for the designs. Cheap: every call looks at the event again, nothing is cached. */
    static final class M {
        final Context c;
        final IslandEvent e;
        final int act;

        M(Context c, IslandEvent e) {
            this.c = c;
            this.e = e;
            this.act = ActStyle.of(e.type);
            if (e.born == 0L) e.born = SystemClock.elapsedRealtime();
        }

        /** Time left of a download judged from its own pace, or -1. A download that starts over (progress fell) starts the stopwatch over too. */
        long eta() {
            long now = SystemClock.elapsedRealtime();
            if (e.progress >= 0 && (e.p0 < 0 || e.progress < e.p0)) { e.p0 = e.progress; e.born = now; }
            return ActStyle.eta(e.p0, e.progress, now - e.born);
        }

        boolean hidden() { return e.contentHidden() || e.redacted; }

        String s(String key, String en) { return Str.t(c, key, en); }

        static CharSequence orEmpty(CharSequence x) { return x == null ? "" : x; }

        boolean has(CharSequence x) { return x != null && x.length() > 0; }

        int accent() {
            switch (act) {
                case ActStyle.CALL: return Cards.pos();
                case ActStyle.PAYMENT: return e.success ? Cards.pos() : Cards.neg();
                case ActStyle.CHARGE:
                case ActStyle.DEVICE: return Cards.tone(e);
                case ActStyle.TIMER: return Cards.accOr(Cards.ORANGE);
                case ActStyle.NAV: return Cards.accOr(Cards.BLUE);
                case ActStyle.MESSAGE: return Cards.accOr(Cards.PURPLE);
                default: return Cards.acc(e);
            }
        }

        /** The text colour that reads on top of a fill of {@code a}. */
        static int onAcc(int a) {
            Style S = Cards.S;
            if (S.accentOverride) {
                if (a == S.accent) return S.accentOn;
                if (a == S.tert) return S.tertOn;
                if (a == S.err) return S.errOn;
            }
            return Col.onColor(a);
        }

        int glyph() {
            switch (act) {
                case ActStyle.CALL: return IconView.PHONE;
                case ActStyle.PAYMENT: return e.success ? IconView.CHECK : IconView.CLOSE;
                case ActStyle.CHARGE: return IconView.BOLT;
                case ActStyle.DOWNLOAD: return IconView.DOWNLOAD;
                case ActStyle.TIMER: return IconView.TIMER;
                case ActStyle.DEVICE: return e.iconType >= 0 ? e.iconType : IconView.BELL;
                case ActStyle.NAV: return IconView.NAV;
                case ActStyle.MESSAGE: return IconView.BELL;
                default: return -1;
            }
        }

        /** The picture that belongs to the activity (a caller's photo, the app's icon), or null when a glyph says it better. */
        Bitmap pic() {
            switch (act) {
                case ActStyle.CALL: return e.icon;
                case ActStyle.DOWNLOAD:
                case ActStyle.NAV:
                case ActStyle.LIVE:
                case ActStyle.MESSAGE: return e.icon != null ? e.icon : e.art;
                default: return null;
            }
        }

        String over() {
            String o;
            switch (act) {
                case ActStyle.CALL: o = e.incoming ? s("incoming_call", "Incoming call") : s("on_call", "On call"); break;
                case ActStyle.PAYMENT: o = has(e.appName) ? e.appName : s("payment", "Payment"); break;
                case ActStyle.CHARGE: o = s("act_battery", "Battery"); break;
                case ActStyle.DOWNLOAD: o = has(e.appName) ? e.appName : s("act_download", "Download"); break;
                case ActStyle.TIMER: o = e.countDown ? s("act_timer", "Timer") : s("act_stopwatch", "Stopwatch"); break;
                case ActStyle.DEVICE: o = deviceLabel(); break;
                case ActStyle.NAV: o = s("act_navigation", "Navigation"); break;
                case ActStyle.LIVE: {
                    String k = Live.label(e.liveKind);
                    o = has(e.appName) ? (k.length() > 0 ? e.appName + " \u00b7 " + k : e.appName) : (k.length() > 0 ? k : s("act_live", "Live"));
                    break;
                }
                default: o = has(e.appName) ? e.appName : s("message", "Message");
            }
            return o.toUpperCase(Locale.getDefault());
        }

        private String deviceLabel() {
            String k = e.key == null ? "" : e.key;
            if (k.startsWith("audio") || k.startsWith("headset")) return s("dev_audio", "Audio");
            if (k.startsWith("bt")) return s("dev_bluetooth", "Bluetooth");
            if (k.startsWith("ringer")) return s("dev_ringer", "Ringer");
            if (k.startsWith("dnd")) return s("dnd", "Do Not Disturb");
            if (k.startsWith("torch")) return s("flashlight", "Flashlight");
            if (k.startsWith("airplane")) return s("airplane", "Airplane mode");
            if (k.startsWith("volume")) return s("volume", "Volume");
            return s("dev_phone", "Phone");
        }

        CharSequence title() { return orEmpty(e.title); }

        CharSequence sub() {
            switch (act) {
                case ActStyle.CHARGE: return chargeSub();
                case ActStyle.DOWNLOAD: {
                    long eta = eta();
                    String left = eta > 0 ? Str.t(c, "act_left_fmt", "%s left", ActStyle.span(eta)) : null;
                    if (has(e.text)) return left == null ? e.text : e.text + " \u00b7 " + left;
                    return left == null ? "" : left;
                }
                case ActStyle.TIMER: return has(e.text) ? e.text : (e.countDown ? s("act_counting", "Counting down") : s("act_running", "Running"));
                case ActStyle.DEVICE: return orEmpty(e.sub);
                default: return orEmpty(e.text);
            }
        }

        private String chargeSub() {
            if (!e.charging) return e.level <= 15 ? s("act_plug_in", "Plug in a charger") : "";
            if (e.level >= 100) return s("act_full", "Full");
            StringBuilder b = new StringBuilder();
            String kind = ActStyle.plug(e.plugged, e.currentMa, e.voltMv), w = ActStyle.watts(e.currentMa, e.voltMv);
            if (kind.length() > 0) b.append(kind);
            if (w != null) b.append(b.length() > 0 ? " \u00b7 " : "").append(w);
            if (e.etaMin > 0) b.append(b.length() > 0 ? " \u00b7 " : "").append(Str.t(c, "act_to_full", "%s to full", ActStyle.span(e.etaMin * 60000L)));
            return b.toString();
        }

        /** The big number: the time, the percentage, the amount. Null when the activity has none. */
        String value() {
            switch (act) {
                case ActStyle.CALL: return e.chrono && !e.incoming ? Cards.timerText(e) : null;
                case ActStyle.PAYMENT: return has(e.sub) ? e.sub.toString() : null;
                case ActStyle.CHARGE: return e.level + "%";
                case ActStyle.DOWNLOAD: return Math.max(0, e.progress) + "%";
                case ActStyle.TIMER: return Cards.timerText(e);
                case ActStyle.DEVICE:
                case ActStyle.LIVE: return e.progress >= 0 ? e.progress + "%" : null;
                default: return null;
            }
        }

        /** 0..1, or -1 when the activity has no progress to draw. */
        float frac() {
            switch (act) {
                case ActStyle.CHARGE: return Math.max(0, Math.min(100, e.level)) / 100f;
                case ActStyle.DOWNLOAD:
                case ActStyle.DEVICE:
                case ActStyle.LIVE: return e.progress < 0 ? -1f : Math.min(100, e.progress) / 100f;
                case ActStyle.TIMER: {
                    long now = System.currentTimeMillis();
                    if (e.countDown) {
                        long rem = Math.max(0L, e.when - now);
                        if (rem > e.total0) e.total0 = rem;   // a timer that was extended (or the first look): the longest remaining time is its length
                        return e.total0 > 0 ? rem / (float) e.total0 : 0f;
                    }
                    long el = Math.max(0L, now - e.when);
                    return (el % 60000L) / 60000f;          // a stopwatch: a lap ring that goes round once a minute
                }
                default: return -1f;
            }
        }

        boolean ticking() { return act == ActStyle.TIMER || (act == ActStyle.CALL && e.chrono && !e.incoming); }

        private String clockAt(long ms) { return DateFormat.format(DateFormat.is24HourFormat(c) ? "HH:mm" : "h:mm a", ms).toString(); }

        private String[] fact(String key, String en, String value) { return new String[]{s(key, en), value == null || value.length() == 0 ? "--" : value}; }

        /** Up to four {label, value} facts for the Stats card. Empty when there is nothing worth a tile (and always while the content is hidden). */
        String[][] stats() {
            if (hidden()) return new String[0][];
            long now = System.currentTimeMillis();
            switch (act) {
                case ActStyle.CALL:
                    return new String[][]{
                        fact("act_status", "Status", e.incoming ? s("act_ringing", "Ringing") : (e.chrono ? s("connected", "Connected") : s("act_dialing", "Dialing"))),
                        fact("act_duration", "Duration", e.chrono && !e.incoming ? Cards.timerText(e) : null),
                        fact("act_app", "App", has(e.appName) ? e.appName.toString() : null),
                        fact("act_time", "Time", clockAt(now))};
                case ActStyle.PAYMENT:
                    return new String[][]{
                        fact("act_amount", "Amount", has(e.sub) ? e.sub.toString() : null),
                        fact("act_status", "Status", !e.success ? s("act_failed", "Failed") : (e.incoming ? s("act_received", "Received") : s("act_sent", "Sent"))),
                        fact("act_merchant", "Merchant", has(e.text) ? e.text.toString() : null),
                        fact("act_time", "Time", clockAt(now))};
                case ActStyle.CHARGE: {
                    java.util.ArrayList<String[]> l = new java.util.ArrayList<String[]>();
                    l.add(fact("act_level", "Level", e.level + "%"));
                    String kind = ActStyle.plug(e.plugged, e.currentMa, e.voltMv), w = ActStyle.watts(e.currentMa, e.voltMv), t = ActStyle.temp(e.tempC10);
                    if (e.charging && kind.length() > 0) l.add(fact("act_charger", "Charger", kind));
                    if (e.charging && w != null) l.add(fact("act_power", "Power", w));
                    if (t != null) l.add(fact("act_temp", "Temp", t));
                    if (e.charging && e.etaMin > 0 && l.size() < 4) l.add(fact("act_left", "Left", ActStyle.span(e.etaMin * 60000L)));
                    return l.toArray(new String[0][]);
                }
                case ActStyle.DOWNLOAD: {
                    long eta = eta();
                    return new String[][]{
                        fact("act_done", "Done", Math.max(0, e.progress) + "%"),
                        fact("act_left", "Left", eta > 0 ? ActStyle.span(eta) : null),
                        fact("act_elapsed", "Elapsed", ActStyle.span(SystemClock.elapsedRealtime() - e.born)),
                        fact("act_app", "App", has(e.appName) ? e.appName.toString() : null)};
                }
                case ActStyle.TIMER:
                    if (e.countDown) return new String[][]{
                        fact("act_left", "Left", Cards.timerText(e)),
                        fact("act_total", "Total", e.total0 > 0 ? ActStyle.span(e.total0) : null),
                        fact("act_ends", "Ends at", clockAt(e.when)),
                        fact("act_app", "App", has(e.appName) ? e.appName.toString() : null)};
                    return new String[][]{
                        fact("act_elapsed", "Elapsed", Cards.timerText(e)),
                        fact("act_started", "Started", clockAt(e.when)),
                        fact("act_app", "App", has(e.appName) ? e.appName.toString() : null),
                        fact("act_time", "Time", clockAt(now))};
                case ActStyle.DEVICE:
                    if (!has(e.sub) || e.progress >= 0) return new String[0][];   // a volume card has its bar and number: Bento shows those
                    return new String[][]{fact("act_status", "Status", e.sub.toString()), fact("act_time", "Time", clockAt(now))};
                case ActStyle.LIVE:
                    if (e.progress < 0) return new String[0][];
                    return new String[][]{
                        fact("act_progress", "Progress", e.progress + "%"),
                        fact("act_app", "App", has(e.appName) ? e.appName.toString() : null)};
                default: return new String[0][];
            }
        }
    }

    // ================================================================== small shared pieces

    private static final int PRIMARY = 0, TONAL = 1, POS = 2, NEG = 3;

    /** One flat key of a card's button row. */
    private static View key(Context c, String label, int kind, int accent, int icon, Runnable run) {
        Style S = Cards.S;
        int bg, fg;
        if (kind == PRIMARY) { bg = accent; fg = M.onAcc(accent); }
        else if (kind == POS) { bg = Cards.pos(); fg = S.accentOverride ? S.tertOn : 0xFFFFFFFF; }
        else if (kind == NEG) { bg = Cards.neg(); fg = S.accentOverride ? S.errOn : 0xFFFFFFFF; }
        else { bg = Cards.BTN; fg = Cards.BTN_ON; }
        return Cards.button(c, label, bg, icon, fg, run);
    }

    /** The button row under a card: the app's own actions (a ringing call gets Decline / Accept, an ongoing call up to three), Reply on a message, Copy amount on a payment. Null when there is nothing to press. */
    private static View keys(final Context c, final IslandEvent e, M md, int heightDp, int topDp) {
        final int ac = md.accent(), hp = Cards.dp(c, heightDp);
        LinearLayout r = Cards.row(c);
        r.setPadding(0, Cards.dp(c, topDp), 0, 0);
        if (md.act == ActStyle.PAYMENT) {
            if (md.hidden()) return null;
            final Context app = c.getApplicationContext();
            final String amount = md.has(e.sub) ? e.sub.toString() : null;
            if (amount != null) r.addView(key(c, md.s("copy_amount", "Copy amount"), PRIMARY, ac, -1, new Runnable() {
                @Override public void run() { Clip.copyText(app, "amount", amount, Str.t(app, "amount_copied", "Amount copied")); }
            }), Cards.lpw(0, hp, 1f));
            LinearLayout.LayoutParams op = Cards.lpw(0, hp, 1f);
            if (amount != null) op.setMarginStart(Cards.dp(c, 8));
            r.addView(key(c, md.s("open", "Open"), amount != null ? TONAL : PRIMARY, ac, -1, new Runnable() { @Override public void run() { Cards.open(c, e); } }), op);
            return r;
        }
        if (md.act == ActStyle.MESSAGE && e.replyIntent != null && e.replyInput != null && !Cards.keyguarded(c)) {
            r.addView(key(c, md.s("reply", "Reply"), PRIMARY, ac, -1, new Runnable() { @Override public void run() { ReplyActivity.start(c, e); } }), Cards.lpw(0, hp, 1f));
            if (e.actLabels.length > 0) {
                LinearLayout.LayoutParams p = Cards.lpw(0, hp, 1f);
                p.setMarginStart(Cards.dp(c, 8));
                r.addView(key(c, e.actLabels[0], TONAL, ac, -1, new Runnable() { @Override public void run() { Cards.fire(c, e, 0); Cards.done(e); } }), p);
            }
            return r;
        }
        final boolean call = md.act == ActStyle.CALL;
        int max = call ? (e.incoming ? 2 : 3) : 2;
        int n = Math.min(max, e.actLabels.length);
        if (n == 0) {
            if (!call || md.hidden()) return null;   // nothing to press, and no extra "Open" on a locked screen
            r.addView(key(c, md.s("open", "Open"), TONAL, ac, -1, new Runnable() { @Override public void run() { Cards.open(c, e); } }), Cards.lpw(0, hp, 1f));
            return r;
        }
        int[] kind = new int[n];
        if (call) {
            for (int i = 0; i < n; i++) kind[i] = Parse.actionKind(e.actLabels[i]);
            if (e.incoming && n >= 2 && kind[0] == 0 && kind[1] == 0) { kind[0] = -1; kind[1] = 1; }   // two unnamed buttons on a ringing call: decline, then accept
        }
        for (int i = 0; i < n; i++) {
            final int idx = i;
            String label = e.actLabels[i];
            int k = i == 0 ? PRIMARY : TONAL, icon = -1;
            if (call) {
                if (label == null || label.length() == 0) label = kind[i] < 0 ? md.s("decline", "Decline") : md.s("accept", "Accept");
                if (kind[i] < 0) { k = NEG; icon = IconView.HANGUP; }
                else if (kind[i] > 0) { k = POS; icon = IconView.PHONE; }
                else k = TONAL;
            }
            LinearLayout.LayoutParams p = Cards.lpw(0, hp, 1f);
            if (i > 0) p.setMarginStart(Cards.dp(c, 8));
            r.addView(key(c, label, k, ac, icon, new Runnable() { @Override public void run() { Cards.fire(c, e, idx); Cards.done(e); } }), p);
        }
        return r;
    }

    /** The icon / photo of the activity as a square tile (a glyph sits on a tonal accent container; a caller without a photo gets a letter circle). */
    private static View tile(Context c, M md, int sizeDp, float radiusDp) {
        Bitmap pic = md.pic();
        if (pic != null) {
            RoundImage ri = new RoundImage(c);
            ri.set(pic, Cards.dp(c, radiusDp));
            return ri;
        }
        if (md.act == ActStyle.CALL) {
            RoundImage ri = new RoundImage(c);
            ri.setLetter(Parse.initial(md.title()), Cards.TILE);
            return ri;
        }
        int gl = md.glyph();
        IconView iv = new IconView(c, gl >= 0 ? gl : IconView.BELL, md.accent());
        int pad = Math.round(Cards.dp(c, sizeDp) * 0.26f);
        iv.setPadding(pad, pad, pad, pad);
        iv.setBackground(Cards.bg(Col.alpha(md.accent(), 0.22f), Cards.dp(c, radiusDp)));
        return iv;
    }

    private static TextView overline(Context c, M md, float sp) {
        TextView t = Cards.tv(c, md.over(), sp, md.accent(), true);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private static void setText(TextView t, CharSequence s) {
        if (s == null) s = "";
        if (!s.toString().contentEquals(t.getText())) t.setText(s);
    }

    private static void describe(View root, M md) {
        String v = md.value();
        root.setContentDescription(md.over() + ". " + md.title() + (v == null ? "" : ". " + v) + (md.sub().length() == 0 ? "" : ". " + md.sub()));
        if (md.act == ActStyle.MESSAGE || md.act == ActStyle.PAYMENT || md.act == ActStyle.CALL) root.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    // ================================================================== compact pill

    /** @return true when a flat design drew the pill (style 1..4 for this activity), false when the classic pill must be built. */
    static boolean compact(Context c, IslandEvent e, FrameLayout out, int h, int g, int m) {
        int act = ActStyle.of(e.type);
        if (act < 0 || !ActStyle.hasCompact(act)) return false;
        int st = Cards.S.cAct[act];
        if (st <= 0) return false;
        M md = new M(c, e);
        switch (st) {
            case 1: capsule(c, e, md, out, h, g, m); break;
            case 2: track(c, e, md, out, h, g, m); break;
            case 3: focus(c, e, md, out, h, g, m); break;
            default: signal(c, e, md, out, h, g, m);
        }
        return true;
    }

    private static View leftGlyph(Context c, M md, int g) {
        Bitmap pic = md.pic();
        if (pic != null) {
            RoundImage ri = new RoundImage(c);
            ri.set(pic, g * 0.3f);
            return ri;
        }
        int gl = md.glyph();
        IconView iv = new IconView(c, gl >= 0 ? gl : IconView.BELL, md.accent());
        int pad = Math.round(g * 0.2f);
        iv.setPadding(pad, pad, pad, pad);
        iv.setBackground(Cards.bg(Col.alpha(md.accent(), 0.22f), g * 0.3f));
        return iv;
    }

    /** Runs {@code refresh} now, keeps it ticking when the number moves, and rebuilds the pill only when its shape changed. */
    private static void wireC(final IslandEvent e, final M md, final Runnable refresh) {
        final boolean hadV = md.value() != null, hadF = md.frac() >= 0f, inc = e.incoming, chr = e.chrono;
        final Bitmap ic = e.icon;
        final int it = e.iconType;
        refresh.run();
        e.tC = md.ticking() ? refresh : null;
        e.uC = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (hadV != (md.value() != null) || hadF != (md.frac() >= 0f) || inc != e.incoming || chr != e.chrono || ic != e.icon || it != e.iconType) return false;
                refresh.run();
                return true;
            }
        };
    }

    /** 1 Capsule: a tonal icon tile on the left, a filled accent capsule with the number (or the title) on the right. */
    private static void capsule(Context c, IslandEvent e, final M md, FrameLayout out, int h, int g, int m) {
        final int side = Gravity.CENTER_VERTICAL, ac = md.accent();
        out.addView(leftGlyph(c, md, g), Cards.fl(g, g, Gravity.START | side, m));
        final int cap = Math.max(Cards.dp(c, 14), Math.min(h - Cards.dp(c, 8), Cards.dp(c, 24)));
        final TextView t = Cards.tv(c, "", 11.5f, M.onAcc(ac), true);
        t.setFontFeatureSettings("tnum");
        t.setGravity(Gravity.CENTER);
        t.setPadding(Cards.dp(c, 9), 0, Cards.dp(c, 9), 0);
        t.setBackground(Cards.bg(ac, cap / 2f));
        t.setMaxWidth(Math.max(Cards.dp(c, 40), Cards.dp(c, Cards.S.cW) - g - 2 * m - Cards.dp(c, 8)));
        out.addView(t, Cards.fl(WRAP, cap, Gravity.END | side, m));
        wireC(e, md, new Runnable() {
            @Override public void run() {
                String v = md.value();
                setText(t, v != null ? v : md.title());
            }
        });
    }

    /** 2 Track: icon, the title with a slim progress line (or the subtitle) under it, the number at the end. */
    private static void track(Context c, IslandEvent e, final M md, FrameLayout out, int h, int g, int m) {
        final int side = Gravity.CENTER_VERTICAL, ac = md.accent(), pillW = Cards.dp(c, Cards.S.cW);
        out.addView(leftGlyph(c, md, g), Cards.fl(g, g, Gravity.START | side, m));
        final boolean hasV = md.value() != null, hasF = md.frac() >= 0f;
        final TextView v = Cards.tv(c, "", 12.5f, ac, true);
        v.setFontFeatureSettings("tnum");
        int vw = 0;
        if (hasV) {
            String sample = md.act == ActStyle.TIMER || md.act == ActStyle.CALL ? "00:00:00" : "100%";
            vw = Math.round(v.getPaint().measureText(sample)) + Cards.dp(c, 2);
            out.addView(v, Cards.fl(WRAP, WRAP, Gravity.END | side, m));
        }
        final int leftEdge = m + g + Cards.dp(c, 8), rightEdge = m + (hasV ? vw + Cards.dp(c, 8) : 0);
        final int avail = Math.max(Cards.dp(c, 24), pillW - leftEdge - rightEdge);
        final boolean line = hasF && h >= Cards.dp(c, 24);
        final boolean tall = !line && h >= Cards.dp(c, (13f + 9.5f) * Cards.TS * 1.2f + 4f);
        final TextView title = Cards.tv(c, md.title(), line ? 12f : 13f, Cards.WHITE, true);
        title.setMaxWidth(avail);
        LinearLayout box = Cards.col(c);
        box.setGravity(Gravity.START);
        box.addView(title, Cards.lp(WRAP, WRAP));
        final ProgressLine pl = line ? new ProgressLine(c) : null;
        final TextView sub = tall ? Cards.tv(c, md.sub(), 9.5f, Cards.SUB, false) : null;
        if (pl != null) {
            LinearLayout.LayoutParams pp = Cards.lp(avail, Math.max(2, Cards.dp(c, 3)));
            pp.topMargin = Cards.dp(c, 3);
            box.addView(pl, pp);
        } else if (sub != null) {
            sub.setMaxWidth(avail);
            LinearLayout.LayoutParams sl = Cards.lp(WRAP, WRAP);
            sl.topMargin = Cards.dp(c, 1);
            box.addView(sub, sl);
        }
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START | side);
        bl.setMargins(leftEdge, 0, rightEdge, 0);
        out.addView(box, bl);
        wireC(e, md, new Runnable() {
            @Override public void run() {
                setText(title, md.title());
                if (hasV) { String val = md.value(); setText(v, val == null ? "" : val); }
                if (pl != null) pl.set(md.frac(), md.accent());
                if (sub != null) setText(sub, md.sub());
            }
        });
    }

    /** 3 Focus: just the number (or the title), centred and bold, with its small icon. */
    private static void focus(Context c, IslandEvent e, final M md, FrameLayout out, int h, int g, int m) {
        final int ac = md.accent();
        LinearLayout box = Cards.row(c);
        box.setGravity(Gravity.CENTER);
        final int gl = md.glyph(), gs = Math.round(g * 0.72f);
        if (gl >= 0) {
            box.addView(new IconView(c, gl, ac), Cards.lp(gs, gs));
        } else {
            View dot = new View(c);
            dot.setBackground(Cards.bg(ac, Cards.dp(c, 3)));
            box.addView(dot, Cards.lp(Cards.dp(c, 6), Cards.dp(c, 6)));
        }
        final TextView t = Cards.tv(c, "", 15f, Cards.WHITE, true);
        t.setFontFeatureSettings("tnum");
        t.setLetterSpacing(0.02f);
        t.setMaxWidth(Math.max(Cards.dp(c, 40), Cards.dp(c, Cards.S.cW) - 2 * m - gs - Cards.dp(c, 14)));
        LinearLayout.LayoutParams tl = Cards.lp(WRAP, WRAP);
        tl.setMarginStart(Cards.dp(c, 7));
        box.addView(t, tl);
        out.addView(box, Cards.fl(WRAP, WRAP, Gravity.CENTER, 0));
        wireC(e, md, new Runnable() {
            @Override public void run() {
                String v = md.value();
                setText(t, v != null ? v : md.title());
            }
        });
    }

    /** 4 Signal: the icon on the left and a live gauge on the right (voice bars on a call, battery cells while charging, a ring for progress, pulsing dots otherwise). */
    private static void signal(Context c, IslandEvent e, final M md, FrameLayout out, int h, int g, int m) {
        final int side = Gravity.CENTER_VERTICAL, ac = md.accent(), pillW = Cards.dp(c, Cards.S.cW);
        out.addView(leftGlyph(c, md, g), Cards.fl(g, g, Gravity.START | side, m));
        final boolean hasF = md.frac() >= 0f;
        final EqView eq;
        final CellsView cells;
        final RingView ring;
        final PulseDots dots;
        final View gauge;
        int gw, gh;
        if (md.act == ActStyle.CALL) {
            eq = new EqView(c); cells = null; ring = null; dots = null; gauge = eq;
            eq.setColor(ac);
            eq.setPlaying(true);
            gw = Math.round(g * 0.9f); gh = Math.round(g * 0.75f);
        } else if (hasF && md.act == ActStyle.CHARGE) {
            cells = new CellsView(c, 5); eq = null; ring = null; dots = null; gauge = cells;
            gw = Math.round(g * 1.5f); gh = Math.max(Cards.dp(c, 8), Math.round(g * 0.42f));
        } else if (hasF) {
            ring = new RingView(c); eq = null; cells = null; dots = null; gauge = ring;
            gw = Math.round(g * 0.92f); gh = gw;
        } else {
            dots = new PulseDots(c); eq = null; cells = null; ring = null; gauge = dots;
            gw = Math.round(g * 1.1f); gh = Math.max(Cards.dp(c, 8), Math.round(g * 0.5f));
        }
        LinearLayout grp = Cards.row(c);
        final TextView t = Cards.tv(c, "", 12f, Cards.WHITE, true);
        t.setFontFeatureSettings("tnum");
        final int room = pillW - g - 2 * m - gw - Cards.dp(c, 16);
        final boolean text = room >= Cards.dp(c, 26);
        if (text) {
            t.setMaxWidth(room);
            grp.addView(t, Cards.lp(WRAP, WRAP));
        }
        LinearLayout.LayoutParams gp = Cards.lp(gw, gh);
        if (text) gp.setMarginStart(Cards.dp(c, 6));
        grp.addView(gauge, gp);
        out.addView(grp, Cards.fl(WRAP, WRAP, Gravity.END | side, m));
        wireC(e, md, new Runnable() {
            @Override public void run() {
                int a = md.accent();
                String v = md.value();
                if (text) setText(t, v != null ? v : md.title());
                if (eq != null) { eq.setColor(a); eq.setPlaying(true); }
                if (cells != null) cells.set(md.frac(), a);
                if (ring != null) ring.set(md.frac(), a);
                if (dots != null) dots.set(a, true);
            }
        });
    }

    // ================================================================== expanded card

    /** @return the flat card (style 1..4 for this activity), or null when the classic card must be built. */
    static View expanded(Context c, IslandEvent e, int width) {
        int act = ActStyle.of(e.type);
        if (act < 0) return null;
        int st = Cards.S.eAct[act];
        if (st <= 0) return null;
        M md = new M(c, e);
        switch (st) {
            case 1: return bento(c, e, md);
            case 2: return hero(c, e, md);
            case 3: return strip(c, e, md);
            default: return md.stats().length == 0 ? bento(c, e, md) : stats(c, e, md);   // nothing worth a tile: the Bento card says it all
        }
    }

    private static LinearLayout root(Context c, float padScale) {
        LinearLayout r = Cards.col(c);
        r.setPadding(Cards.px(c), Math.round(Cards.py(c) * padScale), Cards.px(c), Math.round(Cards.py(c) * padScale));
        return r;
    }

    /** Runs {@code refresh} now, ticks it while the numbers move, and rebuilds the card only when its shape changed (buttons, number, progress, tiles). */
    private static void wireE(final IslandEvent e, final M md, final Runnable refresh, final boolean statsCard) {
        final boolean hadV = md.value() != null, hadF = md.frac() >= 0f, inc = e.incoming, chr = e.chrono;
        final int nStats = statsCard ? md.stats().length : 0;
        final String[] labels = e.actLabels;
        final Bitmap ic = e.icon;
        refresh.run();
        e.tE = md.ticking() || md.act == ActStyle.DOWNLOAD ? refresh : null;
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (hadV != (md.value() != null) || hadF != (md.frac() >= 0f) || inc != e.incoming || chr != e.chrono || ic != e.icon) return false;
                if (statsCard && nStats != md.stats().length) return false;
                if (!Parse.sameLabels(labels, e.actLabels)) return false;
                refresh.run();
                return true;
            }
        };
    }

    private static void addKeys(Context c, IslandEvent e, M md, LinearLayout root, int heightDp, int topDp) {
        View k = keys(c, e, md, heightDp, topDp);
        if (k != null) root.addView(k);
    }

    /** 1 Bento: a big icon tile beside a title tile, a number tile with a slim progress line, then roomy keys. */
    private static View bento(final Context c, final IslandEvent e, final M md) {
        LinearLayout root = root(c, 1f);
        final int as = Cards.dp(c, 68);
        LinearLayout top = Cards.row(c);
        top.addView(tile(c, md, 68, 22f), Cards.lp(as, as));
        LinearLayout info = Cards.col(c);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setBackground(Cards.bg(Cards.TILE, Cards.dp(c, 22)));
        info.setPadding(Cards.dp(c, 14), Cards.dp(c, 8), Cards.dp(c, 12), Cards.dp(c, 8));
        info.setMinimumHeight(Math.round(as * Math.max(1f, Cards.TS)));
        final TextView over = overline(c, md, 10.5f);
        final TextView title = Cards.tv(c, md.title(), 17f, Cards.WHITE, true);
        final TextView sub = Cards.tv(c, md.sub(), 13.5f, Cards.SUB, false);
        sub.setMaxLines(2);
        info.addView(over, Cards.lp(MATCH, WRAP));
        LinearLayout.LayoutParams tl = Cards.lp(MATCH, WRAP);
        tl.topMargin = Cards.dp(c, 3);
        info.addView(title, tl);
        if (md.sub().length() > 0) {
            LinearLayout.LayoutParams sl = Cards.lp(MATCH, WRAP);
            sl.topMargin = Cards.dp(c, 2);
            info.addView(sub, sl);
        }
        LinearLayout.LayoutParams ip = Cards.lpw(0, WRAP, 1f);
        ip.setMarginStart(Cards.dp(c, 8));
        top.addView(info, ip);
        root.addView(top);

        final boolean hasV = md.value() != null, hasF = md.frac() >= 0f;
        final TextView val = Cards.tv(c, "", 26f, Cards.WHITE, true);
        val.setFontFeatureSettings("tnum");
        final ProgressLine pl = hasF ? new ProgressLine(c) : null;
        if (hasV || hasF) {
            LinearLayout vt = Cards.col(c);
            vt.setBackground(Cards.bg(Cards.TILE, Cards.dp(c, 22)));
            vt.setPadding(Cards.dp(c, 16), Cards.dp(c, 10), Cards.dp(c, 16), Cards.dp(c, 12));
            if (hasV) vt.addView(val, Cards.lp(MATCH, WRAP));
            if (pl != null) {
                LinearLayout.LayoutParams pp = Cards.lp(MATCH, Cards.dp(c, 6));
                pp.topMargin = Cards.dp(c, hasV ? 8 : 2);
                vt.addView(pl, pp);
            }
            LinearLayout.LayoutParams vp = Cards.lp(MATCH, WRAP);
            vp.topMargin = Cards.dp(c, 8);
            root.addView(vt, vp);
        }
        addKeys(c, e, md, root, 46, 10);
        describe(root, md);
        wireE(e, md, new Runnable() {
            @Override public void run() {
                int a = md.accent();
                over.setTextColor(a);
                setText(title, md.title());
                setText(sub, md.sub());
                if (hasV) { String v = md.value(); setText(val, v == null ? "" : v); }
                if (pl != null) pl.set(md.frac(), a);
            }
        }, false);
        return root;
    }

    /** 2 Hero: one big centred number (a battery, a check or a photo above it), the title, the progress line and the keys. */
    private static View hero(final Context c, final IslandEvent e, final M md) {
        LinearLayout root = root(c, 1f);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        final int ac = md.accent();
        if (md.act == ActStyle.PAYMENT) {
            root.addView(new CheckView(c, ac, e.success), Cards.lp(Cards.dp(c, 54), Cards.dp(c, 54)));
        } else if (md.act == ActStyle.CHARGE) {
            root.addView(new BatteryView(c, e.level, e.charging, ac), Cards.lp(Cards.dp(c, 78), Cards.dp(c, 38)));
        } else {
            int s = Cards.dp(c, 48);
            View t = tile(c, md, 48, 24f);
            root.addView(t, Cards.lp(s, s));
        }
        final TextView over = overline(c, md, 10.5f);
        over.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ol = Cards.lp(MATCH, WRAP);
        ol.topMargin = Cards.dp(c, 10);
        root.addView(over, ol);
        final boolean hasV = md.value() != null, hasF = md.frac() >= 0f;
        final TextView big = Cards.tv(c, "", hasV ? 44f : 24f, Cards.WHITE, true);
        big.setFontFeatureSettings("tnum");
        big.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bl = Cards.lp(MATCH, WRAP);
        bl.topMargin = Cards.dp(c, 2);
        root.addView(big, bl);
        final TextView title = Cards.tv(c, md.title(), 15f, Cards.WHITE, true);
        title.setGravity(Gravity.CENTER);
        if (hasV) {
            LinearLayout.LayoutParams tl = Cards.lp(MATCH, WRAP);
            tl.topMargin = Cards.dp(c, 4);
            root.addView(title, tl);
        }
        final TextView sub = Cards.tv(c, md.sub(), 13f, Cards.SUB, false);
        sub.setGravity(Gravity.CENTER);
        sub.setMaxLines(2);
        if (md.sub().length() > 0) {
            LinearLayout.LayoutParams sl = Cards.lp(MATCH, WRAP);
            sl.topMargin = Cards.dp(c, 3);
            root.addView(sub, sl);
        }
        final ProgressLine pl = hasF ? new ProgressLine(c) : null;
        if (pl != null) {
            LinearLayout.LayoutParams pp = Cards.lp(MATCH, Cards.dp(c, 6));
            pp.topMargin = Cards.dp(c, 14);
            root.addView(pl, pp);
        }
        addKeys(c, e, md, root, 46, 14);
        describe(root, md);
        wireE(e, md, new Runnable() {
            @Override public void run() {
                int a = md.accent();
                over.setTextColor(a);
                String v = md.value();
                setText(big, v != null ? v : md.title());
                setText(title, md.title());
                setText(sub, md.sub());
                if (pl != null) pl.set(md.frac(), a);
            }
        }, false);
        return root;
    }

    /** 3 Strip: one slim row (icon, title and line of text, number) with a thin progress line under it: the shortest card. */
    private static View strip(final Context c, final IslandEvent e, final M md) {
        LinearLayout root = root(c, 0.8f);
        LinearLayout row = Cards.row(c);
        final int s = Cards.dp(c, 38);
        row.addView(tile(c, md, 38, 12f), Cards.lp(s, s));
        LinearLayout t = Cards.col(c);
        t.setPadding(Cards.dp(c, 12), 0, Cards.dp(c, 8), 0);
        final TextView title = Cards.tv(c, md.title(), 15f, Cards.WHITE, true);
        final TextView sub = Cards.tv(c, md.sub().length() > 0 ? md.sub() : md.over(), 12.5f, Cards.SUB, false);
        t.addView(title);
        LinearLayout.LayoutParams sl = Cards.lp(WRAP, WRAP);
        sl.topMargin = Cards.dp(c, 1);
        t.addView(sub, sl);
        row.addView(t, Cards.lpw(0, WRAP, 1f));
        final boolean hasV = md.value() != null, hasF = md.frac() >= 0f;
        final TextView val = Cards.tv(c, "", 17f, md.accent(), true);
        val.setFontFeatureSettings("tnum");
        if (hasV) row.addView(val);
        root.addView(row);
        final ProgressLine pl = hasF ? new ProgressLine(c) : null;
        if (pl != null) {
            LinearLayout.LayoutParams pp = Cards.lp(MATCH, Cards.dp(c, 4));
            pp.topMargin = Cards.dp(c, 10);
            root.addView(pl, pp);
        }
        addKeys(c, e, md, root, 38, 10);
        describe(root, md);
        wireE(e, md, new Runnable() {
            @Override public void run() {
                int a = md.accent();
                setText(title, md.title());
                setText(sub, md.sub().length() > 0 ? md.sub() : md.over());
                if (hasV) { String v = md.value(); setText(val, v == null ? "" : v); val.setTextColor(a); }
                if (pl != null) pl.set(md.frac(), a);
            }
        }, false);
        return root;
    }

    /** 4 Stats: the title on top, then up to four flat tiles of facts (speed, time left, temperature, duration...), then the keys. */
    private static View stats(final Context c, final IslandEvent e, final M md) {
        LinearLayout root = root(c, 1f);
        LinearLayout head = Cards.row(c);
        final int s = Cards.dp(c, 44);
        head.addView(tile(c, md, 44, 14f), Cards.lp(s, s));
        LinearLayout t = Cards.col(c);
        t.setPadding(Cards.dp(c, 12), 0, 0, 0);
        final TextView over = overline(c, md, 10.5f);
        final TextView title = Cards.tv(c, md.title(), 17f, Cards.WHITE, true);
        t.addView(over);
        LinearLayout.LayoutParams tl = Cards.lp(WRAP, WRAP);
        tl.topMargin = Cards.dp(c, 2);
        t.addView(title, tl);
        head.addView(t, Cards.lpw(0, WRAP, 1f));
        root.addView(head);

        String[][] st = md.stats();
        final TextView[] vals = new TextView[st.length];
        for (int i = 0; i < st.length; i += 2) {
            LinearLayout r = Cards.row(c);
            for (int j = i; j < Math.min(st.length, i + 2); j++) {
                LinearLayout tile = Cards.col(c);
                tile.setBackground(Cards.bg(Cards.TILE, Cards.dp(c, 18)));
                tile.setPadding(Cards.dp(c, 12), Cards.dp(c, 9), Cards.dp(c, 12), Cards.dp(c, 10));
                TextView lab = Cards.tv(c, st[j][0].toUpperCase(Locale.getDefault()), 10f, Cards.SUB, true);
                lab.setLetterSpacing(0.08f);
                vals[j] = Cards.tv(c, st[j][1], 16.5f, Cards.WHITE, true);
                vals[j].setFontFeatureSettings("tnum");
                tile.addView(lab);
                LinearLayout.LayoutParams vl = Cards.lp(MATCH, WRAP);
                vl.topMargin = Cards.dp(c, 3);
                tile.addView(vals[j], vl);
                LinearLayout.LayoutParams p = Cards.lpw(0, WRAP, 1f);
                if (j % 2 == 1) p.setMarginStart(Cards.dp(c, 8));
                r.addView(tile, p);
            }
            LinearLayout.LayoutParams rp = Cards.lp(MATCH, WRAP);
            rp.topMargin = Cards.dp(c, i == 0 ? 12 : 8);
            root.addView(r, rp);
        }
        addKeys(c, e, md, root, 44, 12);
        describe(root, md);
        wireE(e, md, new Runnable() {
            @Override public void run() {
                over.setTextColor(md.accent());
                setText(title, md.title());
                String[][] now = md.stats();
                for (int i = 0; i < vals.length && i < now.length; i++) setText(vals[i], now[i][1]);
            }
        }, true);
        return root;
    }
}
