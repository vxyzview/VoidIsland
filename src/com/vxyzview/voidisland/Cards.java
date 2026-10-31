package com.vxyzview.voidisland;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
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
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

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

    /** Accent for things that are otherwise a fixed semantic colour (charging green, timer orange...): only Material You takes those over. */
    static int tone(IslandEvent e, int own) { return S.m3 ? S.accent : own; }

    /** Track behind a progress / seek bar / ring: Material You uses its secondary container, everything else a faint wash of the text colour. */
    static int track() { return S.m3 ? S.secCont : Col.alpha(WHITE, 0.2f); }

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
        if (S.m3 && bgColor == BTN) return button(c, label, S.secCont, icon, S.onSecCont, run);   // M3 filled-tonal button
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

    /**
     * Send a notification's PendingIntent. Apps targeting Android 14 (API 34) must say they allow the intent to start an
     * activity from the background, otherwise tapping the island can silently do nothing. Older Androids use the plain send.
     */
    static void send(android.app.PendingIntent pi) throws android.app.PendingIntent.CanceledException {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic();
            o.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            pi.send(o.toBundle());
        } else {
            pi.send();
        }
    }

    static void fire(IslandEvent e, int i) {
        try {
            if (i < e.actRuns.length && e.actRuns[i] != null) { e.actRuns[i].run(); return; }
            if (i < e.actIntents.length && e.actIntents[i] != null) send(e.actIntents[i]);
        } catch (Throwable t) { /* pending intent cancelled */ }
    }

    static void open(Context c, IslandEvent e) {
        try { if (e.contentIntent != null) { send(e.contentIntent); return; } } catch (Throwable t) { /* fall through */ }
        try {
            if (e.pkg != null) {
                Intent i = c.getPackageManager().getLaunchIntentForPackage(e.pkg);
                if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); }
            }
        } catch (Throwable t) { /* ignore */ }
    }

    /** setText that does nothing when the text already reads the same (a no-op setText still re-measures the view). */
    static void put(TextView t, String s) {
        if (!s.contentEquals(t.getText())) t.setText(s);
    }

    static void put(TextView t, CharSequence s) {
        if (!s.toString().equals(t.getText().toString())) t.setText(s);
    }

    static String timerText(IslandEvent e) {
        if (e.snap != null) return e.snap.toString();                       // the clock app prints its own readout
        if (e.paused) return Parse.clock(e.frozenMs);                       // frozen where the app froze it
        return Parse.clock(Parse.chronoMs(e.when, e.countDown, System.currentTimeMillis()));
    }

    /** Stopwatch glyph for a stopwatch, hourglass for a count-down, pause bars while it is paused. */
    static int timerGlyph(IslandEvent e) { return e.paused ? IconView.PAUSE : (e.countDown ? IconView.HOURGLASS : IconView.TIMER); }

    /** How much of a count-down is left, 0..1 (needs {@link IslandEvent#totalMs}). */
    static float timerFrac(IslandEvent e) {
        if (e.totalMs <= 0) return 0f;
        long left = e.paused ? e.frozenMs : Parse.chronoMs(e.when, true, System.currentTimeMillis());
        return Math.max(0f, Math.min(1f, left / (float) e.totalMs));
    }

    /** The idle clock's accent: the chosen one, else the island's violet. */
    static int clockAcc() { return S.accentOverride ? S.accent : PURPLE; }

    private static String ampm(int hour24) {
        String[] a = new DateFormatSymbols(Locale.getDefault()).getAmPmStrings();
        return a != null && a.length >= 2 ? a[hour24 < 12 ? 0 : 1] : (hour24 < 12 ? "AM" : "PM");
    }

    /** "1:05" with a small, quieter "PM" after it in 12 h mode. */
    static CharSequence clockSpan(boolean use24, float ampmScale) {
        Calendar cal = Calendar.getInstance();
        int h = cal.get(Calendar.HOUR_OF_DAY);
        String base = Parse.timeOfDay(h, cal.get(Calendar.MINUTE), use24);
        if (use24) return base;
        SpannableStringBuilder sb = new SpannableStringBuilder(base + " " + ampm(h));
        sb.setSpan(new RelativeSizeSpan(ampmScale), base.length() + 1, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(SUB), base.length() + 1, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private static void setHands(ClockView v, boolean secs) {
        Calendar cal = Calendar.getInstance();
        v.set(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND), secs);
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
                // The song rides the pill too: art left, the current lyric line on the right (a long line slides sideways);
                // the equalizer bars take over only while no lyric is known.
                final LyricScroll ls = new LyricScroll(c);
                final int lw = Math.max(dp(c, 36), dp(c, S.cW) - g - dp(c, S.cPad * 2) - dp(c, 2));
                ls.bind(null, lw, WHITE, SUB, Prefs.i(c, Prefs.K_LYRSIZE, 13));
                ls.setVisibility(View.INVISIBLE);
                out.addView(ls, fl(WRAP, WRAP, Gravity.END | side, m));
                final String[] lkey = {""};
                final Lyrics.Loaded[] lnow = {null};
                final Runnable show = new Runnable() {
                    @Override public void run() {
                        Lyrics.Loaded l = lnow[0];
                        if (l == null) { ls.tick(0, 0, false, 1f); ls.setVisibility(View.INVISIBLE); eq.setVisibility(View.VISIBLE); }
                        else {
                            ls.bind(l, lw, WHITE, SUB, Prefs.i(c, Prefs.K_LYRSIZE, 13));
                            // lyrics may be nudged earlier (+) or later (-) to match this player and phone
                            ls.tick(position(e) + Prefs.i(c, Prefs.K_LYROFF, 200), e.durMs, e.playing, e.speed);
                            ls.setVisibility(View.VISIBLE);
                            eq.setVisibility(View.GONE);
                        }
                    }
                };
                e.tC = new Runnable() { @Override public void run() { show.run(); } };
                e.uC = new IslandEvent.Updater() {
                    @Override public boolean update() {
                        ri.set(e.art != null ? e.art : e.icon, g * 0.3f);
                        eq.setColor(acc(e));
                        eq.setPlaying(e.playing);
                        LyricsFetch.lyricsLoad(c, e, lkey, lnow, show);
                        return true;
                    }
                };
                LyricsFetch.lyricsLoad(c, e, lkey, lnow, show);
                break;
            }
            case IslandEvent.CALL: {
                out.addView(new IconView(c, IconView.PHONE, GREEN), fl(g, g, Gravity.START | side, m));
                if (e.chrono && !e.incoming) {
                    final TextView t = tv(c, timerText(e), 13f, GREEN, true);
                    t.setFontFeatureSettings("tnum");
                    out.addView(t, fl(WRAP, WRAP, Gravity.END | side, m));
                    e.tC = new Runnable() { @Override public void run() { put(t, timerText(e)); } };
                }
                break;
            }
            case IslandEvent.TIMER: {
                final int tc = tone(e, ORANGE);
                final boolean ring = e.countDown && e.totalMs > 0 && e.snap == null, cp = e.paused, cd = e.countDown;
                final IconView ic = new IconView(c, timerGlyph(e), tc);
                final RingView rg = ring ? new RingView(c) : null;
                if (ring) {   // a count-down wears a ring that drains as the time runs out
                    FrameLayout lead = new FrameLayout(c);
                    lead.addView(rg, new FrameLayout.LayoutParams(g, g));
                    int in = Math.round(g * 0.27f);
                    ic.setPadding(in, in, in, in);
                    lead.addView(ic, new FrameLayout.LayoutParams(g, g));
                    out.addView(lead, fl(g, g, Gravity.START | side, m));
                } else {
                    out.addView(ic, fl(g, g, Gravity.START | side, m));
                }
                final TextView t = tv(c, timerText(e), 13f, e.paused ? SUB : tc, true);
                t.setFontFeatureSettings("tnum");
                out.addView(t, fl(WRAP, WRAP, Gravity.END | side, m));
                e.tC = new Runnable() {
                    @Override public void run() {
                        put(t, timerText(e));
                        if (rg != null) rg.set(timerFrac(e), tc);
                    }
                };
                if (rg != null) rg.set(timerFrac(e), tc);
                e.uC = new IslandEvent.Updater() {
                    @Override public boolean update() {
                        if (e.paused != cp || e.countDown != cd || (e.countDown && e.totalMs > 0 && e.snap == null) != ring) return false;   // looks different now: rebuild
                        e.tC.run();
                        return true;
                    }
                };
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
            case IslandEvent.LIVE: {
                final RoundImage ri = new RoundImage(c);
                ri.set(e.icon, g * 0.3f);
                out.addView(ri, fl(g, g, Gravity.START | side, m));
                final int lmw = Math.max(dp(c, 36), dp(c, S.cW) - g - dp(c, S.cPad * 2) - dp(c, 2));
                if (e.progress >= 0) {
                    final RingView rg = new RingView(c);
                    rg.set(e.progress / 100f, acc(e));
                    out.addView(rg, fl(g, g, Gravity.END | side, m));
                    e.uC = new IslandEvent.Updater() {
                        @Override public boolean update() { rg.set(Math.max(0, e.progress) / 100f, acc(e)); return e.progress >= 0; }
                    };
                } else {
                    final TextView lt = tv(c, e.title, 12.5f, WHITE, true);
                    lt.setMaxWidth(lmw);
                    out.addView(lt, fl(WRAP, WRAP, Gravity.END | side, m));
                    e.uC = new IslandEvent.Updater() {
                        @Override public boolean update() { lt.setText(e.title); return e.progress < 0; }
                    };
                }
                break;
            }
            case IslandEvent.PREVIEW: {
                int pv = S.accentOverride ? S.accent : PURPLE;
                out.addView(new IconView(c, IconView.NOTE, pv), fl(g, g, Gravity.START | side, m));
                EqView eq = new EqView(c);
                eq.setColor(pv);
                out.addView(eq, fl(Math.round(g * 0.9f), Math.round(g * 0.75f), Gravity.END | side, m));
                break;
            }
            case IslandEvent.CLOCK: {
                final boolean use24 = DateFormat.is24HourFormat(c);
                final int look = Prefs.i(c, Prefs.K_CLOCKSTYLE, 0), ac = clockAcc();
                final int lw = Math.max(dp(c, 36), dp(c, S.cW) - g - dp(c, S.cPad * 2) - dp(c, 4));
                final ClockView cv = look == 1 ? null : new ClockView(c);
                final TextView lead = look == 1 ? tv(c, "", 11.5f, ac, true) : null;
                final TextView t = tv(c, "", look == 2 ? 12f : 14f, WHITE, true);
                if (cv != null) {   // a little analog face, the minute hand and centre in the accent
                    cv.setColors(Col.alpha(ac, 0.45f), WHITE, ac);
                    out.addView(cv, fl(g, g, Gravity.START | side, m));
                } else {            // weekday + date, e.g. "SAT 31"
                    lead.setLetterSpacing(0.06f);
                    out.addView(lead, fl(WRAP, WRAP, Gravity.START | side, m));
                }
                t.setFontFeatureSettings("tnum");
                if (look == 2) t.setMaxWidth(lw);
                out.addView(t, fl(WRAP, WRAP, Gravity.END | side, m));
                final Runnable paint = new Runnable() {
                    @Override public void run() {
                        Calendar cal = Calendar.getInstance();
                        if (look == 2) put(t, Parse.words(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)));
                        else put(t, clockSpan(use24, 0.62f));
                        if (cv != null) setHands(cv, false);
                        else put(lead, DateFormat.format("EEE d", cal.getTimeInMillis()).toString().toUpperCase(Locale.getDefault()));
                    }
                };
                paint.run();
                e.tC = paint;
                break;
            }
            default: {
                if (e.icon != null) {
                    RoundImage ri = new RoundImage(c);
                    ri.set(e.icon, g * 0.3f);
                    out.addView(ri, fl(g, g, Gravity.START | side, m));
                } else {
                    out.addView(new IconView(c, e.iconType >= 0 ? e.iconType : IconView.BELL, acc(e)), fl(g, g, Gravity.START | side, m));
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
            case IslandEvent.LIVE: return live(c, e);
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

    /** "Reply" (opens the quick-reply box) next to the notification's first other action, if it has one. */
    private static View replyRow(final Context c, final IslandEvent e) {
        LinearLayout r = row(c);
        r.setPadding(0, dp(c, 12), 0, 0);
        Runnable reply = new Runnable() { @Override public void run() { ReplyActivity.start(c, e); } };
        // Material You: Reply is the primary action, so it gets the primary container; the other action stays tonal
        r.addView(S.m3 ? button(c, "Reply", S.accCont, -1, S.onAccCont, reply) : button(c, "Reply", BTN, -1, reply), lpw(0, dp(c, 40), 1f));
        if (e.actLabels.length > 0) {
            LinearLayout.LayoutParams p = lpw(0, dp(c, 40), 1f);
            p.setMarginStart(dp(c, 8));
            r.addView(button(c, e.actLabels[0], BTN, -1, new Runnable() {
                @Override public void run() { fire(e, 0); }
            }), p);
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
        if (e.replyIntent != null && e.replyInput != null) root.addView(replyRow(c, e));
        else if (e.actLabels.length > 0) root.addView(actionsRow(c, e, 2));
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
            e.tE = new Runnable() { @Override public void run() { put(time, timerText(e)); } };
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
        root.addView(new BatteryView(c, e.level, e.charging, tone(e, e.accent)), lp(dp(c, 76), dp(c, 38)));
        LinearLayout t = col(c);
        t.setPadding(dp(c, 18), 0, 0, 0);
        t.addView(tv(c, e.title, 14.5f, tone(e, e.accent), true));
        TextView pct = tv(c, e.level + "%", 30f, WHITE, true);
        pct.setFontFeatureSettings("tnum");
        t.addView(pct);
        root.addView(t, lpw(0, WRAP, 1f));
        if (e.charging) root.addView(new IconView(c, IconView.BOLT, tone(e, e.accent)), lp(dp(c, 26), dp(c, 26)));
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
            badge.setBackground(bg(S.m3 ? S.accCont : (e.accent & 0x00FFFFFF) | 0x38000000, dp(c, 22)));
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(c, 22), dp(c, 22), Gravity.CENTER);
            badge.addView(new IconView(c, e.iconType >= 0 ? e.iconType : IconView.BELL, S.m3 ? S.onAccCont : e.accent), p);
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

    /** Ride / food / score card: app icon, what it is, the status text, an optional progress bar and the app's own actions. */
    private static View live(final Context c, final IslandEvent e) {
        final boolean hadBar = e.progress >= 0;
        LinearLayout root = col(c);
        root.setPadding(px(c), py(c), px(c), py(c));
        LinearLayout top = row(c);
        final RoundImage ic = new RoundImage(c);
        ic.set(e.icon, dp(c, 10));
        top.addView(ic, lp(dp(c, 44), dp(c, 44)));
        LinearLayout t = col(c);
        t.setPadding(dp(c, 12), 0, dp(c, 8), 0);
        String kindLabel = Live.label(e.liveKind);
        String head = e.appName != null && e.appName.length() > 0 ? (kindLabel.length() > 0 ? e.appName + " \u00b7 " + kindLabel : e.appName) : kindLabel;
        if (head.length() > 0) {
            TextView k = tv(c, head, 11f, acc(e), true);
            k.setPadding(0, 0, 0, dp(c, 2));
            t.addView(k);
        }
        final TextView title = tv(c, e.title, 16f, WHITE, true);
        final TextView body = tv(c, e.text, 13.5f, SUB, false);
        body.setMaxLines(2);
        t.addView(title);
        if (e.text != null && e.text.length() > 0) { body.setPadding(0, dp(c, 2), 0, 0); t.addView(body); }
        top.addView(t, lpw(0, WRAP, 1f));
        final TextView pct = tv(c, Math.max(0, e.progress) + "%", 16f, WHITE, true);
        pct.setFontFeatureSettings("tnum");
        if (hadBar) top.addView(pct);
        root.addView(top);
        final SeekView bar = new SeekView(c);
        bar.setInteractive(false);
        bar.setColor(acc(e));
        bar.setFrac(Math.max(0, e.progress) / 100f);
        if (hadBar) {
            LinearLayout.LayoutParams bp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 18));
            bp.topMargin = dp(c, 10);
            root.addView(bar, bp);
        }
        if (e.actLabels.length > 0) root.addView(actionsRow(c, e, 2));
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if ((e.progress >= 0) != hadBar) return false;
                title.setText(e.title);
                body.setText(e.text);
                pct.setText(Math.max(0, e.progress) + "%");
                bar.setFrac(Math.max(0, e.progress) / 100f);
                return true;
            }
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

    /** One small rounded fact on the clock card: a glyph and a few words. */
    private static LinearLayout chip(Context c, int icon, int iconColor, TextView text) {
        LinearLayout ch = row(c);
        ch.setBackground(bg(TILE, dp(c, 16)));
        ch.setPadding(dp(c, 10), 0, dp(c, 11), 0);
        ch.addView(new IconView(c, icon, iconColor), lp(dp(c, 14), dp(c, 14)));
        text.setPadding(dp(c, 6), 0, 0, 0);
        ch.addView(text);
        return ch;
    }

    private static String alarmText(Context c, boolean use24) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo ai = am == null ? null : am.getNextAlarmClock();
            if (ai == null) return null;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(ai.getTriggerTime());
            int h = cal.get(Calendar.HOUR_OF_DAY);
            String t = Parse.timeOfDay(h, cal.get(Calendar.MINUTE), use24) + (use24 ? "" : " " + ampm(h));
            return t + " \u00b7 " + Parse.until(ai.getTriggerTime(), System.currentTimeMillis()).substring(3);
        } catch (Throwable t) { return null; }
    }

    private static int[] battery(Context c) {   // {percent, charging 0/1} or null
        try {
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return null;
            int lv = b.getIntExtra("level", -1), sc = b.getIntExtra("scale", 100), st = b.getIntExtra("status", 0);
            if (lv < 0 || sc <= 0) return null;
            return new int[]{Math.round(lv * 100f / sc), st == 2 || st == 5 ? 1 : 0};
        } catch (Throwable t) { return null; }
    }

    /** Weather row of the clock card. Its size is fixed up front (the card is measured once); numbers fill in when they arrive. */
    private static View weatherTile(final Context c) {
        LinearLayout tile = row(c);
        tile.setBackground(bg(TILE, dp(c, 20)));
        tile.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        final WxView icon = new WxView(c);
        icon.set(Wx.CLOUDY, true, WHITE);
        tile.addView(icon, lp(dp(c, 42), dp(c, 42)));
        LinearLayout mid = col(c);
        mid.setPadding(dp(c, 12), 0, dp(c, 8), 0);
        final TextView temp = tv(c, "--", 24f, WHITE, true);
        temp.setFontFeatureSettings("tnum");
        final TextView desc = tv(c, "", 12.5f, SUB, false);
        mid.addView(temp);
        mid.addView(desc);
        tile.addView(mid, lpw(0, WRAP, 1f));
        LinearLayout right = col(c);
        right.setGravity(Gravity.END);
        final TextView hl = tv(c, "", 12.5f, WHITE, true);
        final TextView place = tv(c, "", 11.5f, SUB, false);
        hl.setGravity(Gravity.END);
        place.setGravity(Gravity.END);
        place.setMaxWidth(dp(c, 110));
        right.addView(hl);
        right.addView(place);
        tile.addView(right);
        final boolean f = Weather.useF(c);
        final boolean[] shown = {false};
        final Weather.Cb fill = new Weather.Cb() {
            @Override public void on(Weather.Now n) {
                if (n == null) {
                    if (!shown[0]) { temp.setText("--"); desc.setText("Weather unavailable"); }
                    return;
                }
                shown[0] = true;
                icon.set(Wx.kind(n.code), n.day, WHITE);
                temp.setText(Wx.temp(n.temp, f));
                desc.setText(Wx.desc(n.code, n.day) + " \u00b7 feels " + Wx.temp(n.feels, f));
                hl.setText("H " + Wx.temp(n.hi, f) + "  L " + Wx.temp(n.lo, f));
                place.setText(n.place);
            }
        };
        if (!Weather.hasPermission(c)) {
            temp.setText("Weather");
            desc.setText("Allow location in VoidIsland");
        } else {
            Weather.Now have = Weather.cached(c);
            if (have != null) fill.on(have);
            else { temp.setText("--"); desc.setText(Weather.hasPlace(c) ? "Fetching weather\u2026" : "Open VoidIsland once to set the place"); }
            Weather.refresh(c, fill);
        }
        return tile;
    }

    private static View clockCard(final Context c, final IslandEvent e) {
        final boolean use24 = DateFormat.is24HourFormat(c);
        final int ac = clockAcc();
        LinearLayout root = col(c);
        root.setPadding(px(c), py(c) + dp(c, 2), px(c), py(c) + dp(c, 2));
        LinearLayout top = row(c);
        LinearLayout left = col(c);
        final TextView time = tv(c, clockSpan(use24, 0.4f), 44f, WHITE, true);
        time.setFontFeatureSettings("tnum");
        final TextView date = tv(c, dateFull(), 14f, SUB, false);
        date.setPadding(dp(c, 2), dp(c, 2), 0, 0);
        left.addView(time);
        left.addView(date);
        top.addView(left, lpw(0, WRAP, 1f));
        final ClockView cv = new ClockView(c);
        cv.setColors(Col.alpha(ac, 0.45f), WHITE, ac);
        setHands(cv, true);
        top.addView(cv, lp(dp(c, 82), dp(c, 82)));
        root.addView(top);
        if (Weather.enabled(c)) {
            LinearLayout.LayoutParams wp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 64));
            wp.topMargin = dp(c, 12);
            root.addView(weatherTile(c), wp);
        }

        // little facts: next alarm, battery, week of the year
        final TextView alT = tv(c, "", 12f, WHITE, true), baT = tv(c, "", 12f, WHITE, true), wkT = tv(c, "", 12f, WHITE, true);
        final boolean wantAl = Prefs.b(c, Prefs.K_CALARM, true), wantBa = Prefs.b(c, Prefs.K_CBATT, true), wantWk = Prefs.b(c, Prefs.K_CWEEK, false);
        final LinearLayout facts = row(c);
        facts.setPadding(0, dp(c, 12), 0, 0);
        final String al0 = wantAl ? alarmText(c, use24) : null;
        final int[] ba0 = wantBa ? battery(c) : null;
        final LinearLayout alC = chip(c, IconView.ALARM, ac, alT), baC = chip(c, IconView.BATTERY, ac, baT), wkC = chip(c, IconView.CAL, ac, wkT);
        final IconView baIc = (IconView) baC.getChildAt(0);
        boolean any = false;
        LinearLayout.LayoutParams cp = lp(WRAP, dp(c, 30));
        cp.setMarginEnd(dp(c, 8));
        if (al0 != null) { alT.setText(al0); facts.addView(alC, cp); any = true; }
        if (ba0 != null) { baT.setText(ba0[0] + "%"); baIc.set(ba0[1] == 1 ? IconView.BOLT : IconView.BATTERY, ba0[1] == 1 ? GREEN : ac); facts.addView(baC, cp); any = true; }
        if (wantWk) {
            Calendar w = Calendar.getInstance();
            wkT.setText("Week " + w.get(Calendar.WEEK_OF_YEAR));
            facts.addView(wkC, cp);
            any = true;
        }
        if (any) root.addView(facts);

        final int[] lastMin = {-1};
        e.tE = new Runnable() {
            @Override public void run() {
                Calendar cal = Calendar.getInstance();
                int mn = cal.get(Calendar.MINUTE);
                setHands(cv, true);
                if (mn == lastMin[0]) return;
                lastMin[0] = mn;
                put(time, clockSpan(use24, 0.4f));
                put(date, dateFull());
                if (wantAl) { String a = alarmText(c, use24); if (a != null) put(alT, a); }
                if (wantBa) {
                    int[] b = battery(c);
                    if (b != null) { put(baT, b[0] + "%"); baIc.set(b[1] == 1 ? IconView.BOLT : IconView.BATTERY, b[1] == 1 ? GREEN : ac); }
                }
            }
        };
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() { lastMin[0] = -1; e.tE.run(); return true; }
        };
        return root;
    }

    private static String dateFull() {
        String pat;
        try { pat = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"); } catch (Throwable t) { pat = "EEEE, d MMMM"; }
        return DateFormat.format(pat, System.currentTimeMillis()).toString();
    }

    private static View timer(final Context c, final IslandEvent e) {
        final int tc = tone(e, ORANGE);
        final boolean ring = e.countDown && e.totalMs > 0 && e.snap == null, cp = e.paused, cd = e.countDown;
        final LinearLayout root = col(c);
        root.setPadding(px(c), py(c), px(c), py(c));
        LinearLayout top = row(c);
        FrameLayout tile = new FrameLayout(c);
        tile.setBackground(bg(S.m3 ? S.accCont : Col.alpha(tc, 0.18f), dp(c, 15)));
        IconView ic = new IconView(c, timerGlyph(e), S.m3 ? S.onAccCont : tc);
        ic.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10));
        tile.addView(ic, new FrameLayout.LayoutParams(dp(c, 48), dp(c, 48)));
        top.addView(tile, lp(dp(c, 48), dp(c, 48)));
        LinearLayout t = col(c);
        t.setPadding(dp(c, 14), 0, dp(c, 8), 0);
        String kind = e.sub != null && e.sub.length() > 0 ? e.sub.toString() : (e.countDown ? "Timer" : "Stopwatch");
        String named = e.title != null && e.title.length() > 0 && !e.title.toString().equals(e.appName) ? e.title.toString() : kind;
        String head = e.appName != null && e.appName.length() > 0 ? named + " \u00b7 " + e.appName : named;
        TextView k = tv(c, head.toUpperCase(Locale.getDefault()), 11f, e.paused ? SUB : tc, true);
        k.setLetterSpacing(0.06f);
        final TextView time = tv(c, timerText(e), 36f, e.paused ? SUB : WHITE, true);
        time.setFontFeatureSettings("tnum");
        t.addView(k);
        t.addView(time);
        top.addView(t, lpw(0, WRAP, 1f));
        if (e.paused) {
            TextView chipT = tv(c, "Paused", 11.5f, WHITE, true);
            chipT.setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5));
            chipT.setBackground(bg(TILE, dp(c, 14)));
            top.addView(chipT);
        }
        root.addView(top);
        final SeekView bar = new SeekView(c);
        if (ring) {   // time left in the count-down
            bar.setInteractive(false);
            bar.setColor(tc);
            bar.setFrac(timerFrac(e));
            LinearLayout.LayoutParams bp = lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 18));
            bp.topMargin = dp(c, 10);
            root.addView(bar, bp);
        }
        if (e.actLabels.length > 0) root.addView(actionsRow(c, e, 3));
        e.tE = new Runnable() {
            @Override public void run() {
                put(time, timerText(e));
                if (ring) bar.setFrac(timerFrac(e));
            }
        };
        final String[] shownLabels = e.actLabels;
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (!Parse.sameLabels(shownLabels, e.actLabels)) return false;   // Pause <-> Resume: rebuild the buttons
                if (e.paused != cp || e.countDown != cd || (e.countDown && e.totalMs > 0 && e.snap == null) != ring) return false;
                e.tE.run();
                return true;
            }
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
        final int playFg = S.m3 ? S.onAccent : WHITE;
        if (S.m3) {   // M3 filled button: primary pill with the on-primary glyph
            play.set(e.playing ? IconView.PAUSE : IconView.PLAY, playFg);
            play.setBackground(bg(S.accent, dp(c, 28)));
        }
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
            }
        };
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                title.setText(e.title);
                artist.setText(e.text);
                art.set(e.art != null ? e.art : e.icon, dp(c, 14));
                eq.setColor(acc(e));
                eq.setPlaying(e.playing);
                play.set(e.playing ? IconView.PAUSE : IconView.PLAY, playFg);
                boolean can = e.durMs > 0;
                seek.setInteractive(can && (e.mc == null || (e.actions & PlaybackState.ACTION_SEEK_TO) != 0));
                el.setVisibility(can ? View.VISIBLE : View.INVISIBLE);
                rem.setVisibility(can ? View.VISIBLE : View.INVISIBLE);
                e.tE.run();
                return true;
            }
        };
        e.uE.update();
        return root;
    }
}
