package com.vxyzview.voidisland;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.BatteryManager;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;

/**
 * The cards for calls, payments, charging, downloads, timers, phone events (headset, Bluetooth, ringer, Do Not Disturb,
 * flashlight), navigation, live activities and message banners, compact and expanded. One flat look for all of them: solid
 * fills, tonal capsules and badges, segmented bars. No gradients anywhere. Every colour comes from the island's theme
 * ({@link Cards}), so Material You and custom accents apply as before.
 */
final class ActivityCards {
    private ActivityCards() {}

    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT, MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    // ---------------------------------------------------------------------------------------------------- shared bits

    private static int dp(Context c, float v) { return Cards.dp(c, v); }

    private static boolean has(CharSequence s) { return s != null && s.length() > 0; }

    private static String str(CharSequence s) { return s == null ? "" : s.toString(); }

    private static LinearLayout card(Context c) {
        LinearLayout root = Cards.col(c);
        root.setPadding(Cards.px(c), Cards.py(c), Cards.px(c), Cards.py(c));
        return root;
    }

    /** Layout params with a top margin (dp). */
    private static LinearLayout.LayoutParams gap(Context c, int w, int h, float topDp) {
        LinearLayout.LayoutParams p = Cards.lp(w, h);
        p.topMargin = dp(c, topDp);
        return p;
    }

    /** The small "who is this from" line: app icon and name, with an optional view at the far end. */
    private static LinearLayout appLine(Context c, IslandEvent e, View end) {
        LinearLayout head = Cards.row(c);
        RoundImage ic = new RoundImage(c);
        ic.set(e.appIcon(), dp(c, 6));
        head.addView(ic, Cards.lp(dp(c, 20), dp(c, 20)));
        TextView app = Cards.tv(c, str(e.appName), 13f, Cards.SUB, false);
        app.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        head.addView(app, Cards.lpw(0, WRAP, 1f));
        if (end != null) head.addView(end);
        return head;
    }

    /** A row child that takes the spare width but lets {@code v} keep its own size, pinned to the start. */
    private static View startSlot(Context c, View v) {
        FrameLayout f = new FrameLayout(c);
        f.addView(v, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START | Gravity.CENTER_VERTICAL));
        return f;
    }

    private static void addChip(Context c, LinearLayout row, View chip) {
        LinearLayout.LayoutParams p = Cards.lp(WRAP, WRAP);
        if (row.getChildCount() > 0) p.setMarginStart(dp(c, 8));
        row.addView(chip, p);
    }

    /** "20 sec left", "5 min left", "1 h 5 min left" (or "... to full" for a battery). */
    private static String span(Context c, long ms, boolean toFull) {
        int[] h = Pace.human(ms);
        if (toFull) {
            if (h[0] == Pace.HOUR) return Str.t(c, "full_hm", "%1$d h %2$d min to full", h[1], h[2]);
            return Str.t(c, "full_min", "%d min to full", h[0] == Pace.SEC ? 1 : h[1]);
        }
        if (h[0] == Pace.SEC) return Str.t(c, "eta_sec", "%d sec left", h[1]);
        if (h[0] == Pace.MIN) return Str.t(c, "eta_min", "%d min left", h[1]);
        return Str.t(c, "eta_hm", "%1$d h %2$d min left", h[1], h[2]);
    }

    /** How full a timer's ring is: time left over the longest time seen (count-down), or the seconds of the minute (stopwatch). */
    private static float ringFrac(IslandEvent e, long ms) {
        if (!e.countDown) return (ms % 60000L) / 60000f;
        if (ms > e.span) e.span = ms;
        return e.span <= 0L ? 0f : Math.min(1f, ms / (float) e.span);
    }

    /** One timer refresh: the time, the ring, and the red "time is up" colour once a count-down has run out. */
    private static void tickTimer(IslandEvent e, TextView time, GlyphRing ring, int base, int normal) {
        long ms = Parse.chronoMs(e.when, e.countDown, System.currentTimeMillis());
        Cards.put(time, Parse.clock(ms));
        boolean up = e.countDown && ms <= 0L;
        int tone = up ? Cards.neg() : base, text = up ? Cards.neg() : normal;
        if (time.getCurrentTextColor() != text) time.setTextColor(text);
        ring.set(ringFrac(e, ms), tone, IconView.TIMER);
    }

    private static String status(Context c, IslandEvent e) {
        if (has(e.text)) return e.text.toString();
        return e.incoming ? Str.t(c, "incoming_call", "Incoming call") : Str.t(c, "on_call", "On call");
    }

    // ------------------------------------------------------------------------------------------------ compact (the pill)

    /** The pill's content row: a glyph at the start, whatever follows, all centred between the pill's margins. */
    private static LinearLayout pillRow(Context c, FrameLayout out, int m) {
        LinearLayout row = Cards.row(c);
        row.setPadding(m, 0, m, 0);
        out.addView(row, new FrameLayout.LayoutParams(MATCH, MATCH));
        return row;
    }

    private static int capH(Context c, int h) { return Math.max(dp(c, 14), Math.min(h - dp(c, 8), dp(c, 22))); }

    private static int pillW(Context c) { return dp(c, Cards.S.cW); }

    /** A rough width for a capsule holding {@code s}, to decide whether the pill has room for a label as well. */
    private static int estW(Context c, CharSequence s) { return dp(c, 18) + (s == null ? 0 : s.length()) * dp(c, 7); }

    private static TextView capsule(Context c, CharSequence text, int color, int capH, float sp) {
        TextView t = Cards.tv(c, text, sp, color, true);
        t.setGravity(Gravity.CENTER);
        t.setFontFeatureSettings("tnum");
        t.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        t.setBackground(Cards.bg(Col.alpha(color, 0.18f), capH));
        return t;
    }

    private static TextView pillLabel(Context c, CharSequence s, int color, float sp) {
        TextView t = Cards.tv(c, s, sp, color, true);
        t.setPadding(dp(c, 7), 0, dp(c, 7), 0);
        return t;
    }

    /** Puts {@code label} in the pill's middle, or a spacer when it is null, so whatever follows sits at the far end. */
    private static void middle(Context c, LinearLayout row, View label) {
        if (label != null) row.addView(label, Cards.lpw(0, WRAP, 1f));
        else row.addView(new View(c), Cards.lpw(0, 1, 1f));
    }

    /** A call on the pill: who (avatar, name) and for how long (timer capsule). */
    static void compactCall(final Context c, final IslandEvent e, FrameLayout out, int h, int g, int m) {
        final boolean inc = e.incoming, ch = e.chrono;
        final Bitmap ic = e.icon;
        final int pos = Cards.pos(), cap = capH(c, h);
        LinearLayout row = pillRow(c, out, m);
        if (inc) {
            row.addView(Flat.badge(c, IconView.PHONE, pos, g, true), Cards.lp(g, g));
        } else {
            RoundImage av = new RoundImage(c);
            if (ic != null) av.set(ic, -1f); else av.setLetter(Parse.initial(e.title), Cards.TILE);
            row.addView(av, Cards.lp(g, g));
        }
        final TextView time = !inc && ch ? capsule(c, Cards.timerText(e), pos, cap, 12f) : null;
        int room = pillW(c) - 2 * m - g - (time != null ? estW(c, Cards.timerText(e)) : cap) - dp(c, 14);
        final TextView name = room >= dp(c, 36) ? pillLabel(c, str(e.title), Cards.WHITE, 12f) : null;
        middle(c, row, name);
        if (time != null) {
            row.addView(time, Cards.lp(WRAP, cap));
            e.tC = new Runnable() { @Override public void run() { Cards.put(time, Cards.timerText(e)); } };
        } else if (!inc) {
            row.addView(Flat.badge(c, IconView.PHONE, pos, cap, false), Cards.lp(cap, cap));   // dialling: not timed yet
        }
        e.uC = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (e.incoming != inc || e.chrono != ch || e.icon != ic) return false;
                if (name != null) Cards.put(name, str(e.title));
                return true;
            }
        };
    }

    /** A timer on the pill: a ring that drains (or sweeps, for a stopwatch) and the time in a capsule. */
    static void compactTimer(final Context c, final IslandEvent e, FrameLayout out, int h, int g, int m) {
        final int cap = capH(c, h), base = Cards.accOr(Cards.ORANGE);
        LinearLayout row = pillRow(c, out, m);
        final GlyphRing ring = new GlyphRing(c);
        row.addView(ring, Cards.lp(g, g));
        final TextView time = capsule(c, Cards.timerText(e), base, cap, 12f);
        int room = pillW(c) - 2 * m - g - estW(c, Cards.timerText(e)) - dp(c, 14);
        final TextView label = room >= dp(c, 36) && has(e.title) ? pillLabel(c, e.title, Cards.SUB, 12f) : null;
        middle(c, row, label);
        row.addView(time, Cards.lp(WRAP, cap));
        final Runnable tick = new Runnable() { @Override public void run() { tickTimer(e, time, ring, base, base); } };
        tick.run();
        e.tC = tick;
        e.uC = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (label != null) Cards.put(label, str(e.title));
                tick.run();
                return true;
            }
        };
    }

    /** Navigation on the pill: a solid direction badge and the instruction. */
    static void compactNav(final Context c, final IslandEvent e, FrameLayout out, int h, int g, int m) {
        int tone = Cards.accOr(Cards.BLUE);
        LinearLayout row = pillRow(c, out, m);
        row.addView(Flat.badge(c, IconView.NAV, tone, g, true), Cards.lp(g, g));
        final TextView t = pillLabel(c, str(e.title), Cards.WHITE, 12.5f);
        row.addView(t, Cards.lpw(0, WRAP, 1f));
        e.uC = new IslandEvent.Updater() {
            @Override public boolean update() {
                Cards.put(t, str(e.title));
                return true;
            }
        };
    }

    /** A download or a live activity on the pill: the app, the title when there is room, and a ring with the percent. */
    static void compactProgress(final Context c, final IslandEvent e, FrameLayout out, int h, int g, int m) {
        final boolean bar = e.progress >= 0;
        final int tone = Cards.acc(e), cap = capH(c, h);
        if (e.type == IslandEvent.PROGRESS) e.pace.add(e.progress, System.currentTimeMillis());
        LinearLayout row = pillRow(c, out, m);
        RoundImage ic = new RoundImage(c);
        ic.set(e.icon, g * 0.3f);
        row.addView(ic, Cards.lp(g, g));
        String pc = Math.max(0, e.progress) + "%";
        int room = pillW(c) - 2 * m - g - (bar ? estW(c, pc) + cap : 0) - dp(c, 14);
        final TextView title = room >= dp(c, 36) ? pillLabel(c, str(e.title), Cards.WHITE, 12f) : null;
        middle(c, row, title);
        final RingView ring = bar ? new RingView(c) : null;
        final TextView pt = bar ? Cards.tv(c, pc, 11.5f, tone, true) : null;
        if (bar) {
            LinearLayout capsule = Cards.row(c);
            capsule.setGravity(Gravity.CENTER);
            capsule.setBackground(Cards.bg(Col.alpha(tone, 0.18f), cap));
            capsule.setPadding(dp(c, 5), 0, dp(c, 9), 0);
            int rs = cap - dp(c, 8);
            capsule.addView(ring, Cards.lp(rs, rs));
            pt.setFontFeatureSettings("tnum");
            pt.setPadding(dp(c, 5), 0, 0, 0);
            capsule.addView(pt);
            row.addView(capsule, Cards.lp(WRAP, cap));
            ring.set(Math.max(0, e.progress) / 100f, tone);
        }
        e.uC = new IslandEvent.Updater() {
            @Override public boolean update() {
                if ((e.progress >= 0) != bar) return false;
                if (title != null) Cards.put(title, str(e.title));
                if (bar) {
                    ring.set(e.progress / 100f, tone);
                    Cards.put(pt, e.progress + "%");
                }
                return true;
            }
        };
    }

    /** Everything else that can sit on the pill (flashlight; and, should they ever be shown small, payment, charging, messages). */
    static void compactGeneric(final Context c, final IslandEvent e, FrameLayout out, int h, int g, int m) {
        final int cap = capH(c, h);
        LinearLayout row = pillRow(c, out, m);
        int tone = Cards.tone(e);
        CharSequence end = null;   // the capsule at the pill's far end
        switch (e.type) {
            case IslandEvent.CHARGE:
                tone = e.charging ? Cards.pos() : (e.level <= 15 ? Cards.neg() : tone);
                row.addView(Flat.badge(c, e.charging ? IconView.BOLT : IconView.BATTERY, tone, g, false), Cards.lp(g, g));
                end = e.level + "%";
                break;
            case IslandEvent.PAYMENT:
                tone = e.success ? Cards.pos() : Cards.neg();
                row.addView(Flat.badge(c, e.success ? IconView.CHECK : IconView.CLOSE, tone, g, false), Cards.lp(g, g));
                end = e.sub;
                break;
            case IslandEvent.SYSTEM:
                if (e.state == 2) tone = Cards.SUB;
                if (e.icon != null) {
                    RoundImage ri = new RoundImage(c);
                    ri.set(e.icon, g * 0.3f);
                    row.addView(ri, Cards.lp(g, g));
                } else {
                    row.addView(Flat.badge(c, e.iconType >= 0 ? e.iconType : IconView.BELL, tone, g, false), Cards.lp(g, g));
                }
                end = e.tag;
                break;
            default:
                RoundImage ri = new RoundImage(c);
                ri.set(e.icon, g * 0.3f);
                row.addView(ri, Cards.lp(g, g));
        }
        int room = pillW(c) - 2 * m - g - (has(end) ? estW(c, end) : 0) - dp(c, 14);
        final TextView title = room >= dp(c, 36) ? pillLabel(c, str(e.title), Cards.WHITE, 12f) : null;
        middle(c, row, title);
        if (has(end)) row.addView(capsule(c, end, tone, cap, 12f), Cards.lp(WRAP, cap));
        e.uC = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (title != null) Cards.put(title, str(e.title));
                return true;
            }
        };
    }

    // ----------------------------------------------------------------------------------------------- expanded (the card)

    /** A call: who, what it is, how long, and big answer / decline / hang-up buttons. */
    static View call(final Context c, final IslandEvent e) {
        final boolean inc = e.incoming, hadTime = e.chrono;
        final int n = Math.min(3, e.actLabels.length);
        final String[] shownLabels = e.actLabels;
        final int good = Cards.pos();
        LinearLayout root = card(c);

        // what the call is (a chip) and which app carries it
        LinearLayout head = Cards.row(c);
        final TextView st = Flat.chip(c, status(c, e), good, 11.5f);
        head.addView(st);
        if (has(e.appName)) {
            TextView via = Cards.tv(c, Str.t(c, "via_app", "via %s", e.appName), 12f, Cards.SUB, false);
            via.setGravity(Gravity.END);
            via.setPadding(dp(c, 10), 0, 0, 0);
            head.addView(via, Cards.lpw(0, WRAP, 1f));
        }
        root.addView(head);

        // the avatar (rings swell out of it while the phone rings; one still ring while talking), the name, the timer
        LinearLayout mid = Cards.row(c);
        final int box = dp(c, 78), av = dp(c, 58);
        FrameLayout avatar = new FrameLayout(c);
        avatar.addView(new PulseRing(c, good, inc, (float) av / box), new FrameLayout.LayoutParams(box, box, Gravity.CENTER));
        RoundImage ai = new RoundImage(c);
        if (e.icon != null) ai.set(e.icon, -1f); else ai.setLetter(Parse.initial(e.title), Cards.TILE);
        avatar.addView(ai, new FrameLayout.LayoutParams(av, av, Gravity.CENTER));
        mid.addView(avatar, Cards.lp(box, box));
        final TextView nm = Cards.tv(c, e.title, 22f, Cards.WHITE, true);
        nm.setPadding(dp(c, 10), 0, dp(c, 8), 0);
        mid.addView(nm, Cards.lpw(0, WRAP, 1f));
        final TextView time = hadTime && !inc ? Flat.chip(c, Cards.timerText(e), good, 17f) : null;
        if (time != null) {
            time.setFontFeatureSettings("tnum");
            mid.addView(time);
            e.tE = new Runnable() { @Override public void run() { Cards.put(time, Cards.timerText(e)); } };
        }
        root.addView(mid, gap(c, MATCH, WRAP, 8));

        // the buttons
        LinearLayout br = Cards.row(c);
        int[] kind = new int[Math.max(n, 1)];
        for (int i = 0; i < n; i++) kind[i] = Parse.actionKind(e.actLabels[i]);
        if (inc && n >= 2 && kind[0] == 0 && kind[1] == 0) { kind[0] = -1; kind[1] = 1; }
        if (n == 0) {
            br.addView(Cards.button(c, Str.t(c, "open", "Open"), Cards.BTN, -1, new Runnable() {
                @Override public void run() { Cards.open(c, e); }
            }), Cards.lpw(0, dp(c, 50), 1f));
        }
        for (int i = 0; i < n; i++) {
            final int idx = i;
            String label = e.actLabels[i];
            if (label == null || label.length() == 0) label = kind[i] < 0 ? Str.t(c, "decline", "Decline") : Str.t(c, "accept", "Accept");
            int color, fg;
            if (kind[i] < 0) { color = Cards.neg(); fg = Cards.S.accentOverride ? Cards.S.errOn : 0xFFFFFFFF; }
            else if (kind[i] > 0) { color = Cards.pos(); fg = Cards.S.accentOverride ? Cards.S.tertOn : 0xFFFFFFFF; }
            else { color = Cards.BTN; fg = Cards.BTN_ON; }
            int icon = n >= 3 ? -1 : (kind[i] < 0 ? IconView.HANGUP : (kind[i] > 0 ? IconView.PHONE : -1));
            LinearLayout.LayoutParams p = Cards.lpw(0, dp(c, 50), 1f);
            if (i > 0) p.setMarginStart(dp(c, 10));
            br.addView(Cards.button(c, label, color, icon, fg, new Runnable() {
                @Override public void run() { Cards.fire(c, e, idx); }
            }), p);
        }
        root.addView(br, gap(c, MATCH, WRAP, 16));
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (e.incoming != inc || e.chrono != hadTime || Math.min(3, e.actLabels.length) != n
                    || !Parse.sameLabels(shownLabels, e.actLabels)) return false;
                Cards.put(st, status(c, e));
                Cards.put(nm, str(e.title));
                return true;
            }
        };
        return root;
    }

    /** A payment: the app and the time, a check ring, the amount large, who it was, and a one-tap copy of the amount. */
    static View payment(final Context c, final IslandEvent e) {
        final int col = e.success ? Cards.pos() : Cards.neg();
        LinearLayout root = card(c);
        TextView when = Cards.tv(c, DateFormat.getTimeFormat(c).format(new Date()), 12f, Cards.SUB, false);
        root.addView(appLine(c, e, when));

        LinearLayout mid = Cards.row(c);
        CheckView ck = new CheckView(c, col, e.success);
        ck.setBackground(Cards.bg(Col.alpha(col, 0.16f), dp(c, 32)));
        mid.addView(ck, Cards.lp(dp(c, 64), dp(c, 64)));
        LinearLayout t = Cards.col(c);
        t.setPadding(dp(c, 16), 0, dp(c, 8), 0);
        final TextView status = Cards.tv(c, e.title, 13.5f, col, true);
        t.addView(status);
        final boolean hadAmount = has(e.sub), hadText = has(e.text);
        final TextView amount = Cards.tv(c, e.sub, 32f, Cards.WHITE, true);
        amount.setFontFeatureSettings("tnum");
        if (hadAmount) { amount.setPadding(0, dp(c, 3), 0, 0); t.addView(amount); }
        final TextView desc = Cards.tv(c, e.text, 13.5f, Cards.SUB, false);
        if (hadText) { desc.setPadding(0, dp(c, 3), 0, 0); t.addView(desc); }
        mid.addView(t, Cards.lpw(0, WRAP, 1f));
        if (hadAmount && !e.redacted) mid.addView(copyButton(c, e.sub.toString(), col), Cards.lp(dp(c, 44), dp(c, 44)));
        root.addView(mid, gap(c, MATCH, WRAP, 12));
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (has(e.sub) != hadAmount || has(e.text) != hadText) return false;
                status.setText(e.title);
                amount.setText(e.sub);
                desc.setText(e.text);
                return true;
            }
        };
        return root;
    }

    /** A round button that copies {@code text}; its icon turns into a tick for a moment. */
    private static View copyButton(final Context c, final String text, final int doneColor) {
        final IconView cp = new IconView(c, IconView.COPY, Cards.BTN_ON);
        int pd = dp(c, 12);
        cp.setPadding(pd, pd, pd, pd);
        cp.setBackground(Cards.bg(Cards.BTN, dp(c, 22)));
        cp.setContentDescription(Str.t(c, "copy_amount", "Copy amount"));
        cp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Clip.copyAmount(c, text);
                cp.set(IconView.CHECK, doneColor);
                cp.postDelayed(new Runnable() {
                    @Override public void run() { cp.set(IconView.COPY, Cards.BTN_ON); }
                }, 1600L);
            }
        });
        Cards.press(cp);
        return cp;
    }

    /** Charging: what it is, the time to full, the level large, a segmented bar, and the plug, temperature and low-battery hints. */
    static View charge(final Context c, final IslandEvent e) {
        final int tone = e.charging ? Cards.pos() : (e.level <= 15 ? Cards.neg() : Cards.tone(e));
        LinearLayout root = card(c);
        LinearLayout head = Cards.row(c);
        int bs = dp(c, 30);
        head.addView(Flat.badge(c, e.charging ? IconView.BOLT : IconView.BATTERY, tone, bs, false), Cards.lp(bs, bs));
        TextView title = Cards.tv(c, e.title, 15f, tone, true);
        title.setPadding(dp(c, 10), 0, dp(c, 8), 0);
        head.addView(title, Cards.lpw(0, WRAP, 1f));
        if (e.charging && e.etaMs > 0L) head.addView(Cards.tv(c, span(c, e.etaMs, true), 12.5f, Cards.SUB, false));
        root.addView(head);
        TextView pct = Cards.tv(c, e.level + "%", 44f, Cards.WHITE, true);
        pct.setFontFeatureSettings("tnum");
        root.addView(pct, gap(c, WRAP, WRAP, 10));
        SegBar bar = new SegBar(c, 20).intro();
        bar.set(e.level / 100f, tone);
        root.addView(bar, gap(c, MATCH, dp(c, 12), 10));
        LinearLayout chips = Cards.row(c);
        if (e.charging) {
            String plug = plugName(c, e.plug);
            if (plug != null) addChip(c, chips, Flat.tag(c, plug, 12f));
        }
        if (e.temp != Integer.MIN_VALUE) addChip(c, chips, Flat.tag(c, String.format(Locale.getDefault(), "%.1f\u00B0C", e.temp / 10f), 12f));
        if (!e.charging && e.level <= 15) addChip(c, chips, Flat.chip(c, Str.t(c, "charge_soon", "Charge soon"), tone, 12f));
        if (chips.getChildCount() > 0) root.addView(chips, gap(c, MATCH, WRAP, 12));
        return root;
    }

    private static String plugName(Context c, int plug) {
        if (plug == BatteryManager.BATTERY_PLUGGED_AC) return Str.t(c, "plug_ac", "AC");
        if (plug == BatteryManager.BATTERY_PLUGGED_USB) return Str.t(c, "plug_usb", "USB");
        return null;   // wireless: the title already says so
    }

    /** A download: the app's icon with a download badge, the file, the percent large, a segmented bar, the time left. */
    static View progress(final Context c, final IslandEvent e) {
        final int tone = Cards.acc(e);
        e.pace.add(e.progress, System.currentTimeMillis());
        final String[] shownLabels = e.actLabels;
        final boolean hadText = has(e.text);
        LinearLayout root = card(c);
        LinearLayout top = Cards.row(c);
        FrameLayout ib = new FrameLayout(c);
        RoundImage ic = new RoundImage(c);
        ic.set(e.icon, dp(c, 12));
        ib.addView(ic, new FrameLayout.LayoutParams(dp(c, 46), dp(c, 46), Gravity.START | Gravity.TOP));
        ib.addView(Flat.badge(c, IconView.DOWNLOAD, tone, dp(c, 22), true),
            new FrameLayout.LayoutParams(dp(c, 22), dp(c, 22), Gravity.END | Gravity.BOTTOM));
        top.addView(ib, Cards.lp(dp(c, 54), dp(c, 52)));
        LinearLayout t = Cards.col(c);
        t.setPadding(dp(c, 12), 0, dp(c, 8), 0);
        final TextView title = Cards.tv(c, e.title, 16f, Cards.WHITE, true);
        final TextView body = Cards.tv(c, e.text, 13.5f, Cards.SUB, false);
        t.addView(title);
        if (hadText) { body.setPadding(0, dp(c, 2), 0, 0); t.addView(body); }
        top.addView(t, Cards.lpw(0, WRAP, 1f));
        final TextView pct = Cards.tv(c, Math.max(0, e.progress) + "%", 28f, tone, true);
        pct.setFontFeatureSettings("tnum");
        top.addView(pct);
        root.addView(top);
        final SegBar bar = new SegBar(c, 24).intro();
        bar.set(Math.max(0, e.progress) / 100f, tone);
        root.addView(bar, gap(c, MATCH, dp(c, 10), 14));

        // the app on the left; the time still needed, once the speed is known, on the right
        LinearLayout info = Cards.row(c);
        if (has(e.appName)) info.addView(startSlot(c, Flat.tag(c, e.appName, 12f)), Cards.lpw(0, WRAP, 1f));
        else info.addView(new View(c), Cards.lpw(0, 1, 1f));
        final TextView eta = Flat.chip(c, "0", tone, 12f);
        eta.setVisibility(View.INVISIBLE);
        addChip(c, info, eta);
        root.addView(info, gap(c, MATCH, WRAP, 12));
        if (e.actLabels.length > 0) root.addView(Cards.actionsRow(c, e, 2));
        final Runnable tick = new Runnable() { @Override public void run() { refreshEta(c, e, eta); } };
        tick.run();
        e.tE = tick;
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (has(e.text) != hadText || !Parse.sameLabels(shownLabels, e.actLabels)) return false;
                title.setText(e.title);
                body.setText(e.text);
                Cards.put(pct, Math.max(0, e.progress) + "%");
                bar.set(Math.max(0, e.progress) / 100f, tone);
                tick.run();
                return true;
            }
        };
        return root;
    }

    private static void refreshEta(Context c, IslandEvent e, TextView eta) {
        long ms = e.pace.remainingMs(System.currentTimeMillis());
        if (ms < 0L) {
            if (eta.getVisibility() != View.INVISIBLE) eta.setVisibility(View.INVISIBLE);
            return;
        }
        Cards.put(eta, span(c, ms, false));
        if (eta.getVisibility() != View.VISIBLE) eta.setVisibility(View.VISIBLE);
    }

    /** A timer or stopwatch: a ring that drains (or sweeps) around a glyph, the time large, when it ends, and its buttons. */
    static View timer(final Context c, final IslandEvent e) {
        final int base = Cards.accOr(Cards.ORANGE);
        final String[] shownLabels = e.actLabels;
        final long builtWhen = e.when;
        LinearLayout root = card(c);
        LinearLayout top = Cards.row(c);
        final GlyphRing ring = new GlyphRing(c);
        top.addView(ring, Cards.lp(dp(c, 78), dp(c, 78)));
        LinearLayout t = Cards.col(c);
        t.setPadding(dp(c, 16), 0, 0, 0);
        LinearLayout kr = Cards.row(c);
        kr.addView(Flat.chip(c, e.countDown ? Str.t(c, "timer_kind", "Timer") : Str.t(c, "stopwatch_kind", "Stopwatch"), base, 11.5f));
        final TextView label = Cards.tv(c, str(e.title), 13f, Cards.SUB, false);
        label.setPadding(dp(c, 8), 0, 0, 0);
        kr.addView(label, Cards.lpw(0, WRAP, 1f));
        t.addView(kr);
        final TextView time = Cards.tv(c, Cards.timerText(e), 40f, Cards.WHITE, true);
        time.setFontFeatureSettings("tnum");
        time.setPadding(0, dp(c, 4), 0, 0);
        t.addView(time);
        if (e.countDown && e.when > System.currentTimeMillis()) {
            TextView ends = Cards.tv(c, Str.t(c, "ends_at", "Ends %s", DateFormat.getTimeFormat(c).format(new Date(e.when))), 12.5f, Cards.SUB, false);
            ends.setPadding(0, dp(c, 3), 0, 0);
            t.addView(ends);
        }
        top.addView(t, Cards.lpw(0, WRAP, 1f));
        root.addView(top);
        if (e.actLabels.length > 0) root.addView(Cards.actionsRow(c, e, 3));
        final Runnable tick = new Runnable() { @Override public void run() { tickTimer(e, time, ring, base, Cards.WHITE); } };
        tick.run();
        e.tE = tick;
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (!Parse.sameLabels(shownLabels, e.actLabels)) return false;                // Pause <-> Resume: rebuild the buttons
                if (e.countDown && Math.abs(e.when - builtWhen) > 2000L) return false;        // restarted: the "ends" time moved
                Cards.put(label, str(e.title));
                tick.run();
                return true;
            }
        };
        return root;
    }

    /** Headset, Bluetooth, ringer, Do Not Disturb, flashlight: a badge, what happened, and its state at the end of the row. */
    static View system(final Context c, final IslandEvent e) {
        final boolean off = e.state == 2, hadSub = has(e.sub);
        final int tone = off ? Cards.SUB : Cards.tone(e);
        final String[] shownLabels = e.actLabels;
        LinearLayout root = card(c);
        LinearLayout row = Cards.row(c);
        int bs = dp(c, 50);
        if (e.icon != null) {
            RoundImage ri = new RoundImage(c);
            ri.set(e.icon, dp(c, 14));
            row.addView(ri, Cards.lp(bs, bs));
        } else {
            row.addView(Flat.badge(c, e.iconType >= 0 ? e.iconType : IconView.BELL, tone, bs, false), Cards.lp(bs, bs));
        }
        LinearLayout t = Cards.col(c);
        t.setPadding(dp(c, 14), 0, dp(c, 8), 0);
        final TextView title = Cards.tv(c, e.title, 17f, Cards.WHITE, true);
        t.addView(title);
        final TextView sub = Cards.tv(c, e.sub, 13f, Cards.SUB, false);
        if (hadSub) { sub.setPadding(0, dp(c, 2), 0, 0); t.addView(sub); }
        row.addView(t, Cards.lpw(0, WRAP, 1f));
        final PipsView pips = e.slot >= 0 ? new PipsView(c, 3) : null;
        final LinearLayout chip = pips == null && has(e.tag) ? Flat.dotChip(c, e.tag, tone, 12f) : null;
        if (pips != null) {
            pips.set(e.slot, tone);
            row.addView(pips, Cards.lp(dp(c, 44), dp(c, 9)));
        } else if (chip != null) {
            row.addView(chip);
        }
        root.addView(row);
        if (e.actLabels.length > 0) root.addView(Cards.actionsRow(c, e, 2));
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if ((e.state == 2) != off || has(e.sub) != hadSub || (e.slot >= 0) != (pips != null)
                    || (pips == null && has(e.tag)) != (chip != null) || !Parse.sameLabels(shownLabels, e.actLabels)) return false;
                title.setText(e.title);
                sub.setText(e.sub);
                if (pips != null) pips.set(e.slot, tone);
                if (chip != null) Flat.chipText(chip).setText(e.tag);
                return true;
            }
        };
        return root;
    }

    /** Navigation: the app, a big direction badge, the instruction and where it leads. */
    static View nav(final Context c, final IslandEvent e) {
        final int tone = Cards.accOr(Cards.BLUE);
        final boolean hadText = has(e.text);
        LinearLayout root = card(c);
        root.addView(appLine(c, e, Flat.chip(c, Str.t(c, "navigation", "Navigation"), tone, 11.5f)));
        LinearLayout mid = Cards.row(c);
        int bs = dp(c, 54);
        mid.addView(Flat.badge(c, IconView.NAV, tone, bs, true), Cards.lp(bs, bs));
        LinearLayout t = Cards.col(c);
        t.setPadding(dp(c, 14), 0, dp(c, 4), 0);
        final TextView title = Cards.tv(c, e.title, 22f, Cards.WHITE, true);
        title.setMaxLines(2);
        t.addView(title);
        final TextView text = Cards.tv(c, e.text, 14f, Cards.SUB, false);
        text.setMaxLines(2);
        if (hadText) { text.setPadding(0, dp(c, 3), 0, 0); t.addView(text); }
        mid.addView(t, Cards.lpw(0, WRAP, 1f));
        root.addView(mid, gap(c, MATCH, WRAP, 12));
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (has(e.text) != hadText) return false;
                title.setText(e.title);
                text.setText(e.text);
                return true;
            }
        };
        return root;
    }

    private static String liveLabel(Context c, int kind) {
        switch (kind) {
            case Live.RIDE: return Str.t(c, "live_ride", "Ride");
            case Live.FOOD: return Str.t(c, "live_food", "Order");
            case Live.SPORT: return Str.t(c, "live_sport", "Live score");
            default: return Str.t(c, "live_update", "Live update");
        }
    }

    private static int liveGlyph(int kind) {
        switch (kind) {
            case Live.RIDE: return IconView.CAR;
            case Live.FOOD: return IconView.FOOD;
            case Live.SPORT: return IconView.TROPHY;
            default: return -1;
        }
    }

    /** Ride / food / score: the app, what kind it is, the status, a segmented bar and the app's own buttons. */
    static View live(final Context c, final IslandEvent e) {
        final boolean hadBar = e.progress >= 0, hadText = has(e.text);
        final String[] shownLabels = e.actLabels;
        final int tone = Cards.acc(e);
        LinearLayout root = card(c);
        root.addView(appLine(c, e, Flat.chip(c, liveLabel(c, e.liveKind), tone, 11.5f)));
        LinearLayout body = Cards.row(c);
        int glyph = liveGlyph(e.liveKind), bs = dp(c, 50);
        if (glyph >= 0) body.addView(Flat.badge(c, glyph, tone, bs, false), Cards.lp(bs, bs));
        LinearLayout t = Cards.col(c);
        t.setPadding(glyph >= 0 ? dp(c, 14) : 0, 0, dp(c, 8), 0);
        final TextView title = Cards.tv(c, e.title, 17f, Cards.WHITE, true);
        t.addView(title);
        final TextView text = Cards.tv(c, e.text, 13.5f, Cards.SUB, false);
        text.setMaxLines(2);
        if (hadText) { text.setPadding(0, dp(c, 2), 0, 0); t.addView(text); }
        body.addView(t, Cards.lpw(0, WRAP, 1f));
        final TextView pct = Cards.tv(c, Math.max(0, e.progress) + "%", 24f, tone, true);
        pct.setFontFeatureSettings("tnum");
        if (hadBar) body.addView(pct);
        root.addView(body, gap(c, MATCH, WRAP, 12));
        final SegBar bar = new SegBar(c, 24).intro();
        bar.set(Math.max(0, e.progress) / 100f, tone);
        if (hadBar) root.addView(bar, gap(c, MATCH, dp(c, 10), 14));
        if (e.actLabels.length > 0) root.addView(Cards.actionsRow(c, e, 2));
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if ((e.progress >= 0) != hadBar || has(e.text) != hadText) return false;
                if (!Parse.sameLabels(shownLabels, e.actLabels)) return false;   // the app changed its buttons: rebuild them
                title.setText(e.title);
                text.setText(e.text);
                Cards.put(pct, Math.max(0, e.progress) + "%");
                bar.set(Math.max(0, e.progress) / 100f, tone);
                return true;
            }
        };
        return root;
    }

    /** A message banner: the sender's picture (with the app's icon on its corner), the app, the text, and quick actions. */
    static View notif(final Context c, final IslandEvent e) {
        LinearLayout root = card(c);
        LinearLayout top = Cards.row(c);
        final Bitmap appIc = Img.appIcon(c, e.pkg);
        final boolean photo = e.icon != null && appIc != null && e.icon != appIc;   // a contact's picture, not just the app's icon
        final RoundImage ic = new RoundImage(c);
        ic.set(e.icon, photo ? -1f : dp(c, 13));
        FrameLayout av = new FrameLayout(c);
        av.addView(ic, new FrameLayout.LayoutParams(dp(c, 48), dp(c, 48), Gravity.START | Gravity.TOP));
        if (photo) {
            View cut = new View(c);
            cut.setBackground(Cards.bg(Cards.S.bg | 0xFF000000, dp(c, 13)));
            av.addView(cut, new FrameLayout.LayoutParams(dp(c, 26), dp(c, 26), Gravity.END | Gravity.BOTTOM));
            RoundImage badge = new RoundImage(c);
            badge.set(appIc, dp(c, 5));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(c, 20), dp(c, 20), Gravity.END | Gravity.BOTTOM);
            bp.setMargins(0, 0, dp(c, 3), dp(c, 3));
            av.addView(badge, bp);
        }
        top.addView(av, Cards.lp(dp(c, 54), dp(c, 52)));
        LinearLayout t = Cards.col(c);
        t.setPadding(dp(c, 12), 0, 0, 0);
        final TextView title = Cards.tv(c, e.title, 16f, Cards.WHITE, true);
        final TextView body = Cards.tv(c, e.text, 14f, Cards.SUB, false);
        body.setMaxLines(2);
        body.setPadding(0, dp(c, 2), 0, 0);
        if (has(e.appName)) {
            TextView app = Cards.tv(c, e.appName, 11.5f, Cards.accOr(Col.alpha(Cards.WHITE, 0.55f)), true);
            app.setPadding(0, 0, 0, dp(c, 2));
            t.addView(app);
        }
        t.addView(title);
        if (has(e.text)) t.addView(body);
        top.addView(t, Cards.lpw(0, WRAP, 1f));
        root.addView(top);
        root.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);   // TalkBack reads a new banner out
        root.setContentDescription((has(e.appName) ? e.appName + ". " : "") + e.title + (has(e.text) ? ". " + e.text : ""));
        if (e.replyIntent != null && e.replyInput != null && !Cards.keyguarded(c)) root.addView(Cards.replyRow(c, e));
        else if (e.actLabels.length > 0) root.addView(Cards.actionsRow(c, e, 2));
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                title.setText(e.title);
                body.setText(e.text);
                ic.set(e.icon, photo ? -1f : dp(c, 13));
                return true;
            }
        };
        return root;
    }
}
