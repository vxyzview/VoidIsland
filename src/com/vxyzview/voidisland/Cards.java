package com.vxyzview.voidisland;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;

import org.json.JSONArray;
import org.json.JSONObject;

/** Builds the views for every kind of island content. Compact = the little pill, expanded = the card. */
final class Cards {
    private Cards() {}

    static final int GREEN = 0xFF30D158, RED = 0xFFFF453A, ORANGE = 0xFFFF9F0A,
        BLUE = 0xFF0A84FF, YELLOW = 0xFFFFD60A, PURPLE = 0xFFB388FF;
    // Theme colours: "WHITE" is simply the island's text colour (white on black by default, dark on a light island).
    static int WHITE = 0xFFFFFFFF, SUB = 0xB3FFFFFF, BTN = 0x2EFFFFFF, TILE = 0x24FFFFFF;
    static float TS = 1f;
    static Style S = new Style();

    static void apply(Style s) {
        S = s;
        WHITE = s.text;
        SUB = s.sub;
        BTN = s.btn;
        TILE = s.tile;
        TS = s.tScale / 100f;
    }

    static int acc(IslandEvent e) { return S.accentOverride ? S.accent : e.accent; }

    static int px(Context c) { return dp(c, S.padX); }

    static int py(Context c) { return dp(c, S.padY); }
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    // ---------------------------------------------------------------- helpers

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static TextView tv(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * TS);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams lpw(int w, int h, float weight) { return new LinearLayout.LayoutParams(w, h, weight); }

    static FrameLayout.LayoutParams fl(int w, int h, int gravity, int margin) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, gravity);
        p.setMargins(margin, 0, margin, 0);
        return p;
    }

    static GradientDrawable bg(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** iOS-style squish while pressed. */
    static void press(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View x, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) x.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start();
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) x.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
                return false;
            }
        });
    }

    static View button(Context c, String label, int bgColor, int icon, final Runnable run) {
        boolean semantic = bgColor == RED || bgColor == GREEN;
        return button(c, label, bgColor, icon, semantic ? 0xFFFFFFFF : WHITE, run);
    }

    static View button(Context c, String label, int bgColor, int icon, int fg, final Runnable run) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setBackground(bg(bgColor, dp(c, 26)));
        b.setPadding(dp(c, 12), 0, dp(c, 14), 0);
        if (icon >= 0) b.addView(new IconView(c, icon, fg), lp(dp(c, 20), dp(c, 20)));
        TextView t = tv(c, label, 14.5f, fg, true);
        t.setPadding(icon >= 0 ? dp(c, 6) : 0, 0, 0, 0);
        b.addView(t);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { run.run(); }
        });
        press(b);
        return b;
    }

    static void fire(IslandEvent e, int i) {
        try {
            if (i < e.actRuns.length && e.actRuns[i] != null) { e.actRuns[i].run(); return; }
            if (i < e.actIntents.length && e.actIntents[i] != null) e.actIntents[i].send();
        } catch (Throwable t) { /* pending intent cancelled */ }
    }

    static void open(Context c, IslandEvent e) {
        try { if (e.contentIntent != null) { e.contentIntent.send(); return; } } catch (Throwable t) { /* fall through */ }
        try {
            if (e.pkg != null) {
                Intent i = c.getPackageManager().getLaunchIntentForPackage(e.pkg);
                if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); }
            }
        } catch (Throwable t) { /* ignore */ }
    }

    static String timerText(IslandEvent e) {
        long now = System.currentTimeMillis();
        return Parse.clock(e.countDown ? e.when - now : now - e.when);
    }

    /** The idle pill's clock text: "1:05" (12 h) or "13:05" (24 h), following the system setting. */
    static String clockText(boolean use24) {
        Calendar cal = Calendar.getInstance();
        return Parse.timeOfDay(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), use24);
    }

    private static CharSequence dateText() {
        return DateFormat.format("EEE, MMM d", System.currentTimeMillis());
    }

    static long position(IslandEvent e) {
        long p = e.posMs;
        if (e.playing) {
            long now = SystemClock.elapsedRealtime(), at = e.posAt > 0 ? e.posAt : now;
            p += (long) ((now - at) * (e.speed <= 0f ? 1f : e.speed));
        }
        if (e.durMs > 0 && p > e.durMs) p = e.durMs;
        return p < 0 ? 0 : p;
    }

    /** 0 = previous, 1 = play/pause, 2 = next. Works on real sessions and on the demo track. */
    static void mediaAction(IslandEvent e, int what) {
        try {
            if (e.mc != null) {
                MediaController.TransportControls tc = e.mc.getTransportControls();
                if (what == 0) tc.skipToPrevious();
                else if (what == 2) tc.skipToNext();
                else if (e.playing) tc.pause();
                else tc.play();
            } else if (what == 1) {
                long now = SystemClock.elapsedRealtime();
                e.posMs = position(e);
                e.posAt = now;
                e.playing = !e.playing;
                e.dirty = true;
                Bus.refresh();
            }
        } catch (Throwable t) { /* session gone */ }
    }

    static void seekTo(IslandEvent e, long ms) {
        try {
            if (e.mc != null) e.mc.getTransportControls().seekTo(ms);
            else { e.posMs = ms; e.posAt = SystemClock.elapsedRealtime(); }
        } catch (Throwable t) { /* ignore */ }
    }

    // ---------------------------------------------------------------- compact pill

    static void buildCompact(final Context c, final IslandEvent e, FrameLayout out, int h) {
        out.removeAllViews();
        e.uC = null;
        e.tC = null;
        final int g = Math.max(dp(c, 10), Math.min(h - dp(c, 4), Math.round((h - dp(c, 12)) * S.glyph / 100f))), m = dp(c, S.cPad);
        final int side = Gravity.CENTER_VERTICAL;
        switch (e.type) {
            case IslandEvent.MEDIA: {
                final RoundImage ri = new RoundImage(c);
                ri.set(e.art != null ? e.art : e.icon, g * 0.3f);
                out.addView(ri, fl(g, g, Gravity.START | side, m));
                final EqView eq = new EqView(c);
                eq.setColor(acc(e));
                eq.setPlaying(e.playing);
                out.addView(eq, fl(Math.round(g * 0.9f), Math.round(g * 0.75f), Gravity.END | side, m));
                e.uC = new IslandEvent.Updater() {
                    @Override public boolean update() {
                        ri.set(e.art != null ? e.art : e.icon, g * 0.3f);
                        eq.setColor(acc(e));
                        eq.setPlaying(e.playing);
                        return true;
                    }
                };
                break;
            }
            case IslandEvent.CALL: {
                out.addView(new IconView(c, IconView.PHONE, GREEN), fl(g, g, Gravity.START | side, m));
                if (e.chrono && !e.incoming) {
                    final TextView t = tv(c, timerText(e), 13f, GREEN, true);
                    t.setFontFeatureSettings("tnum");
                    out.addView(t, fl(WRAP, WRAP, Gravity.END | side, m));
                    e.tC = new Runnable() { @Override public void run() { t.setText(timerText(e)); } };
                }
                break;
            }
            case IslandEvent.TIMER: {
                out.addView(new IconView(c, IconView.TIMER, ORANGE), fl(g, g, Gravity.START | side, m));
                final TextView t = tv(c, timerText(e), 13f, ORANGE, true);
                t.setFontFeatureSettings("tnum");
                out.addView(t, fl(WRAP, WRAP, Gravity.END | side, m));
                e.tC = new Runnable() { @Override public void run() { t.setText(timerText(e)); } };
                break;
            }
            case IslandEvent.NAV: {
                out.addView(new IconView(c, IconView.NAV, BLUE), fl(g, g, Gravity.START | side, m));
                final TextView t = tv(c, e.title, 12.5f, WHITE, true);
                t.setMaxWidth(dp(c, 58));
                out.addView(t, fl(WRAP, WRAP, Gravity.END | side, m));
                e.uC = new IslandEvent.Updater() {
                    @Override public boolean update() { t.setText(e.title); return true; }
                };
                break;
            }
            case IslandEvent.PROGRESS: {
                final RoundImage ri = new RoundImage(c);
                ri.set(e.icon, g * 0.3f);
                out.addView(ri, fl(g, g, Gravity.START | side, m));
                final RingView rg = new RingView(c);
                rg.set(Math.max(0, e.progress) / 100f, acc(e));
                out.addView(rg, fl(g, g, Gravity.END | side, m));
                e.uC = new IslandEvent.Updater() {
                    @Override public boolean update() { rg.set(Math.max(0, e.progress) / 100f, acc(e)); return true; }
                };
                break;
            }
            case IslandEvent.PREVIEW: {
                out.addView(new IconView(c, IconView.NOTE, PURPLE), fl(g, g, Gravity.START | side, m));
                EqView eq = new EqView(c);
                eq.setColor(PURPLE);
                out.addView(eq, fl(Math.round(g * 0.9f), Math.round(g * 0.75f), Gravity.END | side, m));
                break;
            }
            case IslandEvent.CLOCK: {
                final boolean use24 = DateFormat.is24HourFormat(c);
                out.addView(new IconView(c, IconView.CLOCK, acc(e)), fl(g, g, Gravity.START | side, m));
                final TextView t = tv(c, clockText(use24), 13f, WHITE, true);
                t.setFontFeatureSettings("tnum");
                out.addView(t, fl(WRAP, WRAP, Gravity.END | side, m));
                e.tC = new Runnable() { @Override public void run() { t.setText(clockText(use24)); } };
                break;
            }
            default: {
                if (e.icon != null) {
                    RoundImage ri = new RoundImage(c);
                    ri.set(e.icon, g * 0.3f);
                    out.addView(ri, fl(g, g, Gravity.START | side, m));
                } else {
                    out.addView(new IconView(c, e.iconType >= 0 ? e.iconType : IconView.BELL, e.accent), fl(g, g, Gravity.START | side, m));
                }
            }
        }
        if (e.uC == null) { // nothing to refresh live: only rebuild if the shape of the content changed
            final boolean ch = e.chrono, inc = e.incoming;
            final Bitmap ic = e.icon;
            final int it = e.iconType;
            e.uC = new IslandEvent.Updater() {
                @Override public boolean update() { return e.chrono == ch && e.incoming == inc && e.icon == ic && e.iconType == it; }
            };
        }
    }

    // ---------------------------------------------------------------- expanded cards

    static View buildExpanded(Context c, IslandEvent e, int width) {
        e.uE = null;
        e.tE = null;
        switch (e.type) {
            case IslandEvent.MEDIA: return media(c, e);
            case IslandEvent.CALL: return call(c, e);
            case IslandEvent.PAYMENT: return payment(c, e);
            case IslandEvent.CHARGE: return charge(c, e);
            case IslandEvent.PROGRESS: return progress(c, e);
            case IslandEvent.TIMER: return timer(c, e);
            case IslandEvent.CLOCK: return clockCard(c, e);
            case IslandEvent.SYSTEM: return system(c, e);
            default: return notif(c, e);
        }
    }

    private static View actionsRow(final Context c, final IslandEvent e, int max) {
        LinearLayout r = row(c);
        r.setPadding(0, dp(c, 12), 0, 0);
        int n = Math.min(max, e.actLabels.length);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            View b = button(c, e.actLabels[i], BTN, -1, new Runnable() {
                @Override public void run() { fire(e, idx); }
            });
            LinearLayout.LayoutParams p = lpw(0, dp(c, 40), 1f);
            if (i > 0) p.setMarginStart(dp(c, 8));
            r.addView(b, p);
        }
        return r;
    }

    private static View notif(final Context c, final IslandEvent e) {
        LinearLayout root = col(c);
        root.setPadding(px(c), py(c), px(c), py(c));
        LinearLayout top = row(c);
        final RoundImage ic = new RoundImage(c);
        ic.set(e.icon, dp(c, 11));
        top.addView(ic, lp(dp(c, 44), dp(c, 44)));
        LinearLayout t = col(c);
        t.setPadding(dp(c, 12), 0, 0, 0);
        final TextView title = tv(c, e.title, 15.5f, WHITE, true);
        final TextView body = tv(c, e.text, 14f, SUB, false);
        body.setMaxLines(2);
        body.setPadding(0, dp(c, 2), 0, 0);
        if (e.appName != null && e.appName.length() > 0) {
            TextView app = tv(c, e.appName, 11f, 0x80FFFFFF, false);
            app.setPadding(0, 0, 0, dp(c, 2));
            t.addView(app);
        }
        t.addView(title);
        if (e.text != null && e.text.length() > 0) t.addView(body);
        top.addView(t, lpw(0, WRAP, 1f));
        root.addView(top);
        if (e.actLabels.length > 0) root.addView(actionsRow(c, e, 2));
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() { title.setText(e.title); body.setText(e.text); ic.set(e.icon, dp(c, 11)); return true; }
        };
        return root;
    }

    private static View call(final Context c, final IslandEvent e) {
        final boolean inc = e.incoming, hadTime = e.chrono;
        final int n = Math.min(2, e.actLabels.length);
        LinearLayout root = col(c);
        root.setPadding(px(c), py(c), px(c), py(c));
        LinearLayout top = row(c);
        RoundImage av = new RoundImage(c);
        if (e.icon != null) av.set(e.icon, -1f); else av.setLetter(Parse.initial(e.title), TILE);
        top.addView(av, lp(dp(c, 48), dp(c, 48)));
        LinearLayout t = col(c);
        t.setPadding(dp(c, 14), 0, dp(c, 8), 0);
        final TextView st = tv(c, e.text, 13f, inc ? GREEN : SUB, false);
        final TextView nm = tv(c, e.title, 20f, WHITE, true);
        nm.setPadding(0, dp(c, 2), 0, 0);
        t.addView(st);
        t.addView(nm);
        top.addView(t, lpw(0, WRAP, 1f));
        final TextView time = hadTime && !inc ? tv(c, timerText(e), 17f, GREEN, true) : null;
        if (time != null) {
            time.setFontFeatureSettings("tnum");
            top.addView(time);
            e.tE = new Runnable() { @Override public void run() { time.setText(timerText(e)); } };
        }
        root.addView(top);

        LinearLayout br = row(c);
        br.setPadding(0, dp(c, 16), 0, 0);
        int[] kind = new int[2];
        for (int i = 0; i < n; i++) kind[i] = Parse.actionKind(e.actLabels[i]);
        if (inc && n >= 2 && kind[0] == 0 && kind[1] == 0) { kind[0] = -1; kind[1] = 1; }
        if (n == 0) {
            br.addView(button(c, "Open", BTN, -1, new Runnable() { @Override public void run() { open(c, e); } }), lpw(0, dp(c, 46), 1f));
        }
        for (int i = 0; i < n; i++) {
            final int idx = i;
            String label = e.actLabels[i];
            if (label == null || label.length() == 0) label = kind[i] < 0 ? "Decline" : "Accept";
            int color = kind[i] < 0 ? RED : (kind[i] > 0 ? GREEN : BTN);
            int icon = kind[i] < 0 ? IconView.HANGUP : (kind[i] > 0 ? IconView.PHONE : -1);
            LinearLayout.LayoutParams p = lpw(0, dp(c, 46), 1f);
            if (i > 0) p.setMarginStart(dp(c, 12));
            br.addView(button(c, label, color, icon, new Runnable() { @Override public void run() { fire(e, idx); } }), p);
        }
        root.addView(br);
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (e.incoming != inc || e.chrono != hadTime || Math.min(2, e.actLabels.length) != n) return false;
                st.setText(e.text);
                nm.setText(e.title);
                return true;
            }
        };
        return root;
    }

    private static View payment(final Context c, final IslandEvent e) {
        LinearLayout root = col(c);
        root.setPadding(px(c), py(c), px(c), py(c));
        LinearLayout head = row(c);
        RoundImage ic = new RoundImage(c);
        ic.set(e.appIcon(), dp(c, 6));
        head.addView(ic, lp(dp(c, 20), dp(c, 20)));
        TextView app = tv(c, e.appName == null ? "Payment" : e.appName, 13f, SUB, false);
        app.setPadding(dp(c, 8), 0, 0, 0);
        head.addView(app);
        root.addView(head);
        LinearLayout mid = row(c);
        mid.setPadding(0, dp(c, 12), 0, 0);
        int col = e.success ? GREEN : RED;
        mid.addView(new CheckView(c, col, e.success), lp(dp(c, 58), dp(c, 58)));
        LinearLayout t = col(c);
        t.setPadding(dp(c, 16), 0, 0, 0);
        t.addView(tv(c, e.title, 14.5f, col, true));
        if (e.sub != null && e.sub.length() > 0) {
            TextView amt = tv(c, e.sub, 30f, WHITE, true);
            amt.setFontFeatureSettings("tnum");
            amt.setPadding(0, dp(c, 2), 0, 0);
            t.addView(amt);
        }
        if (e.text != null && e.text.length() > 0) {
            TextView d = tv(c, e.text, 13.5f, SUB, false);
            d.setPadding(0, dp(c, 3), 0, 0);
            t.addView(d);
        }
        mid.addView(t, lpw(0, WRAP, 1f));
        root.addView(mid);
        return root;
    }

    private static View charge(final Context c, final IslandEvent e) {
        LinearLayout root = row(c);
        root.setPadding(px(c), py(c), px(c), py(c));
        root.addView(new BatteryView(c, e.level, e.charging, e.accent), lp(dp(c, 76), dp(c, 38)));
        LinearLayout t = col(c);
        t.setPadding(dp(c, 18), 0, 0, 0);
        t.addView(tv(c, e.title, 14.5f, e.accent, true));
        TextView pct = tv(c, e.level + "%", 30f, WHITE, true);
        pct.setFontFeatureSettings("tnum");
        t.addView(pct);
        root.addView(t, lpw(0, WRAP, 1f));
        if (e.charging) root.addView(new IconView(c, IconView.BOLT, e.accent), lp(dp(c, 26), dp(c, 26)));
        return root;
    }

    private static View system(final Context c, final IslandEvent e) {
        LinearLayout root = row(c);
        root.setPadding(px(c), py(c), px(c), py(c));
        FrameLayout badge = new FrameLayout(c);
        if (e.icon != null) {
            RoundImage ri = new RoundImage(c);
            ri.set(e.icon, dp(c, 12));
            badge.addView(ri, new FrameLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        } else {
            badge.setBackground(bg((e.accent & 0x00FFFFFF) | 0x38000000, dp(c, 22)));
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(c, 22), dp(c, 22), Gravity.CENTER);
            badge.addView(new IconView(c, e.iconType >= 0 ? e.iconType : IconView.BELL, e.accent), p);
        }
        root.addView(badge, lp(dp(c, 44), dp(c, 44)));
        LinearLayout t = col(c);
        t.setPadding(dp(c, 14), 0, 0, 0);
        final TextView title = tv(c, e.title, 16f, WHITE, true);
        t.addView(title);
        final TextView sub = tv(c, e.sub, 13f, SUB, false);
        if (e.sub != null && e.sub.length() > 0) { sub.setPadding(0, dp(c, 2), 0, 0); t.addView(sub); }
        root.addView(t, lpw(0, WRAP, 1f));
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() { title.setText(e.title); sub.setText(e.sub); return true; }
        };
        return root;
    }

    private static View progress(final Context c, final IslandEvent e) {
        LinearLayout root = col(c);
        root.setPadding(px(c), py(c), px(c), py(c));
        LinearLayout top = row(c);
        final RoundImage ic = new RoundImage(c);
        ic.set(e.icon, dp(c, 10));
        top.addView(ic, lp(dp(c, 40), dp(c, 40)));
        LinearLayout t = col(c);
        t.setPadding(dp(c, 12), 0, dp(c, 8), 0);
        final TextView title = tv(c, e.title, 15.5f, WHITE, true);
        final TextView body = tv(c, e.text, 13.5f, SUB, false);
        t.addView(title);
        if (e.text != null && e.text.length() > 0) t.addView(body);
        top.addView(t, lpw(0, WRAP, 1f));
        final TextView pct = tv(c, Math.max(0, e.progress) + "%", 17f, WHITE, true);
        pct.setFontFeatureSettings("tnum");
        top.addView(pct);
        root.addView(top);
        final SeekView bar = new SeekView(c);
        bar.setInteractive(false);
        bar.setColor(acc(e));
        bar.setFrac(Math.max(0, e.progress) / 100f);
        LinearLayout.LayoutParams bp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 18));
        bp.topMargin = dp(c, 10);
        root.addView(bar, bp);
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                title.setText(e.title);
                body.setText(e.text);
                pct.setText(Math.max(0, e.progress) + "%");
                bar.setFrac(Math.max(0, e.progress) / 100f);
                return true;
            }
        };
        return root;
    }

    private static View clockCard(final Context c, final IslandEvent e) {
        final boolean use24 = DateFormat.is24HourFormat(c);
        LinearLayout root = col(c);
        root.setGravity(Gravity.CENTER);
        root.setPadding(px(c), py(c) + dp(c, 6), px(c), py(c) + dp(c, 8));
        final TextView time = tv(c, clockText(use24), 36f, WHITE, true);
        time.setFontFeatureSettings("tnum");
        time.setGravity(Gravity.CENTER);
        final TextView date = tv(c, dateText(), 13f, SUB, false);
        date.setGravity(Gravity.CENTER);
        date.setPadding(0, dp(c, 2), 0, 0);
        root.addView(time);
        root.addView(date);
        e.tE = new Runnable() {
            @Override public void run() {
                time.setText(clockText(use24));
                date.setText(dateText());
            }
        };
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() { e.tE.run(); return true; }
        };
        return root;
    }

    private static View timer(final Context c, final IslandEvent e) {
        LinearLayout root = col(c);
        root.setPadding(px(c), py(c), px(c), py(c));
        LinearLayout top = row(c);
        top.addView(new IconView(c, IconView.TIMER, ORANGE), lp(dp(c, 30), dp(c, 30)));
        LinearLayout t = col(c);
        t.setPadding(dp(c, 14), 0, 0, 0);
        final TextView label = tv(c, e.title, 14f, SUB, false);
        final TextView time = tv(c, timerText(e), 34f, WHITE, true);
        time.setFontFeatureSettings("tnum");
        t.addView(label);
        t.addView(time);
        top.addView(t, lpw(0, WRAP, 1f));
        root.addView(top);
        if (e.actLabels.length > 0) root.addView(actionsRow(c, e, 2));
        e.tE = new Runnable() { @Override public void run() { time.setText(timerText(e)); } };
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() { label.setText(e.title); return true; }
        };
        return root;
    }

    private static View media(final Context c, final IslandEvent e) {
        LinearLayout root = col(c);
        root.setPadding(px(c), py(c), px(c), py(c));
        LinearLayout top = row(c);
        final RoundImage art = new RoundImage(c);
        art.set(e.art != null ? e.art : e.icon, dp(c, 14));
        top.addView(art, lp(dp(c, 62), dp(c, 62)));
        LinearLayout txt = col(c);
        txt.setPadding(dp(c, 14), 0, dp(c, 10), 0);
        final TextView title = tv(c, e.title, 17f, WHITE, true);
        final TextView artist = tv(c, e.text, 14.5f, SUB, false);
        artist.setPadding(0, dp(c, 3), 0, 0);
        txt.addView(title);
        txt.addView(artist);
        top.addView(txt, lpw(0, WRAP, 1f));
        final EqView eq = new EqView(c);
        eq.setColor(acc(e));
        eq.setPlaying(e.playing);
        top.addView(eq, lp(dp(c, 22), dp(c, 20)));
        root.addView(top);

        final TextView lyr = tv(c, "", 14f, SUB, false);
        lyr.setPadding(0, dp(c, 4), 0, 0);
        lyr.setVisibility(View.INVISIBLE);   // kept reserved (never GONE): the card's height is measured once
        root.addView(lyr, lpw(ViewGroup.LayoutParams.MATCH_PARENT, WRAP, 1f));

        LinearLayout sr = row(c);
        sr.setPadding(0, dp(c, 14), 0, 0);
        final TextView el = tv(c, "0:00", 11.5f, SUB, false);
        final TextView rem = tv(c, "-0:00", 11.5f, SUB, false);
        el.setFontFeatureSettings("tnum");
        rem.setFontFeatureSettings("tnum");
        rem.setGravity(Gravity.END);
        final SeekView seek = new SeekView(c);
        seek.setListener(new SeekView.Listener() {
            @Override public void onSeek(float f) { if (e.durMs > 0) seekTo(e, (long) (f * e.durMs)); }
        });
        sr.addView(el, lp(dp(c, 38), WRAP));
        sr.addView(seek, lpw(0, dp(c, 22), 1f));
        sr.addView(rem, lp(dp(c, 44), WRAP));
        root.addView(sr);

        LinearLayout ctl = row(c);
        ctl.setGravity(Gravity.CENTER);
        final IconView prev = new IconView(c, IconView.PREV, WHITE), next = new IconView(c, IconView.NEXT, WHITE);
        final IconView play = new IconView(c, e.playing ? IconView.PAUSE : IconView.PLAY, WHITE);
        prev.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        next.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        play.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        prev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { mediaAction(e, 0); } });
        next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { mediaAction(e, 2); } });
        play.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { mediaAction(e, 1); } });
        press(prev);
        press(next);
        press(play);
        ctl.addView(prev, lp(dp(c, 64), dp(c, 52)));
        LinearLayout.LayoutParams pp = lp(dp(c, 76), dp(c, 56));
        pp.setMargins(dp(c, 10), 0, dp(c, 10), 0);
        ctl.addView(play, pp);
        ctl.addView(next, lp(dp(c, 64), dp(c, 52)));
        root.addView(ctl);

        final String[] lkey = {""};          // the song the lyrics row is currently about (track|artist|durS)
        final Lyrics.Loaded[] lnow = {null};
        final Runnable showLyric = new Runnable() {
            @Override public void run() {
                Lyrics.Loaded l = lnow[0];
                if (l == null) { lyr.setVisibility(View.INVISIBLE); return; }
                String line = l.textAt(position(e), e.durMs);
                if (line == null) { lyr.setText(""); lyr.setVisibility(View.INVISIBLE); }
                else { lyr.setText(line); lyr.setVisibility(View.VISIBLE); }
            }
        };
        final Runnable reloadLyrics = new Runnable() {
            @Override public void run() {
                if (!Prefs.b(c, Prefs.K_LYRICS, true)) { lnow[0] = null; lkey[0] = ""; showLyric.run(); return; }
                final String track = e.title == null ? "" : e.title.toString().trim();
                final String artist = e.text == null ? "" : e.text.toString().trim();
                if (track.length() == 0) { lnow[0] = null; lkey[0] = ""; showLyric.run(); return; }
                final String key = track + "|" + artist + "|" + (e.durMs / 1000);
                if (key.equals(lkey[0])) return;   // already attempted this exact song (nothing was found)
                lkey[0] = key;
                lyricsFetch(track, artist, e.durMs / 1000, new GotLyrics() {
                    @Override public void got(String t0, String a0, Lyrics.Loaded l) {
                        if (l != null) Lyrics.remember(t0, a0, l);
                        String n0 = e.title == null ? "" : e.title.toString().trim();
                        String n1 = e.text == null ? "" : e.text.toString().trim();
                        if (!n0.equals(t0) || !n1.equals(a0)) return;   // the song changed while we were fetching
                        lnow[0] = l;
                        showLyric.run();
                    }
                });
            }
        };
        e.tE = new Runnable() {
            @Override public void run() {
                long dur = e.durMs, pos = position(e);
                if (dur > 0) {
                    if (!seek.isDragging()) seek.setFrac(pos / (float) dur);
                    el.setText(Parse.clock(pos));
                    rem.setText("-" + Parse.clock(dur - pos));
                } else {
                    seek.setFrac(0f);
                }
                showLyric.run();
            }
        };
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                title.setText(e.title);
                artist.setText(e.text);
                art.set(e.art != null ? e.art : e.icon, dp(c, 14));
                eq.setColor(acc(e));
                eq.setPlaying(e.playing);
                play.set(e.playing ? IconView.PAUSE : IconView.PLAY, WHITE);
                boolean can = e.durMs > 0;
                seek.setInteractive(can && (e.mc == null || (e.actions & PlaybackState.ACTION_SEEK_TO) != 0));
                el.setVisibility(can ? View.VISIBLE : View.INVISIBLE);
                rem.setVisibility(can ? View.VISIBLE : View.INVISIBLE);
                e.tE.run();
                reloadLyrics.run();
                return true;
            }
        };
        e.uE.update();
        return root;
    }

    // ---------------------------------------------------------------- lyrics fetch (LRCLIB)

    /** Callback for {@link #lyricsFetch}: always called on the main thread (even on an instant cache hit). */
    interface GotLyrics { void got(String track, String artist, Lyrics.Loaded l); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Cache-first lyrics for the playing song; slow network work sits on a daemon thread, results land on main. */
    static void lyricsFetch(final String track, final String artist, final long durS, final GotLyrics done) {
        Lyrics.Loaded hit = Lyrics.cached(track, artist);
        if (hit != null) { done.got(track, artist, hit); return; }
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                final Lyrics.Loaded l = lyricsNet(track, artist, durS);
                MAIN.post(new Runnable() {
                    @Override public void run() { done.got(track, artist, l); }
                });
            }
        }, "vi-lyrics");
        t.setDaemon(true);
        t.start();
    }

    /** LRCLIB (no key): /api/get first, then /api/search picking the entry whose duration fits best. */
    private static Lyrics.Loaded lyricsNet(String track, String artist, long durS) {
        JSONObject best = null;
        try {
            String q = "https://lrclib.net/api/get?track_name=" + URLEncoder.encode(track, "UTF-8")
                + (artist.length() > 0 ? "&artist_name=" + URLEncoder.encode(artist, "UTF-8") : "")
                + (durS > 0 ? "&duration=" + durS : "");
            best = httpJson(q);   // null = no exact record (404) or a busy-server 503 — the search fallback still runs
        } catch (Throwable t) { /* fall through to search */ }
        if (best == null) {
            try {
                String q = "https://lrclib.net/api/search?track_name=" + URLEncoder.encode(track, "UTF-8")
                    + (artist.length() > 0 ? "&artist_name=" + URLEncoder.encode(artist, "UTF-8") : "");
                JSONArray arr = httpArray(q);
                if (arr != null) {
                    int ai = -1, score = -1000000;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        String s0 = o.optString("syncedLyrics", ""), p0 = o.optString("plainLyrics", "");
                        if (s0.length() == 0 && p0.length() == 0) continue;
                        int sc = (s0.length() > 0 ? 500 : 0) + (p0.length() > 0 ? 1 : 0);
                        double d = o.optDouble("duration", 0);
                        if (durS > 0 && Math.abs(d - durS) <= 2) sc += 200;
                        if (sc > score) { score = sc; ai = i; }
                    }
                    if (ai >= 0) best = arr.optJSONObject(ai);
                }
            } catch (Throwable t) { /* offline or rate-limited: no lyrics this round, no caching of failures */ }
        }
        if (best == null) return null;
        String s0 = best.optString("syncedLyrics", ""), p0 = best.optString("plainLyrics", "");
        return Lyrics.parse(s0.length() > 0 ? s0 : null, p0.length() > 0 ? p0 : null);
    }

    private static JSONObject httpJson(String url) {
        String b = httpText(url);
        try { return b == null ? null : new JSONObject(b); } catch (Throwable t) { return null; }
    }

    private static JSONArray httpArray(String url) {
        String b = httpText(url);
        try { return b == null ? null : new JSONArray(b); } catch (Throwable t) { return null; }
    }

    private static String httpText(String url) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(6000);
            con.setRequestProperty("User-Agent", "VoidIsland/1.1 (https://github.com/vxyzview/VoidIsland)");
            if (con.getResponseCode() != 200) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            StringBuilder b = new StringBuilder();
            char[] buf = new char[8192];
            for (int n; (n = r.read(buf)) > 0;) b.append(buf, 0, n);
            r.close();
            return b.length() > 0 ? b.toString() : null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (con != null) try { con.disconnect(); } catch (Throwable ignored) {}
        }
    }
}
