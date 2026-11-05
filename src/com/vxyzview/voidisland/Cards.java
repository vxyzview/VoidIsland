package com.vxyzview.voidisland;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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

import org.json.JSONArray;
import org.json.JSONObject;

/** Builds the views for every kind of island content. Compact = the little pill, expanded = the card. */
final class Cards {
    private Cards() {}

    static final int GREEN = 0xFF30D158, RED = 0xFFFF453A, ORANGE = 0xFFFF9F0A,
        BLUE = 0xFF0A84FF, YELLOW = 0xFFFFD60A, PURPLE = 0xFFB388FF;
    // Theme colours: "WHITE" is simply the island's text colour (white on black by default, dark on a light island).
    static int WHITE = 0xFFFFFFFF, SUB = 0xB3FFFFFF, BTN = 0x2EFFFFFF, TILE = 0x24FFFFFF;
    static int BTN_ON = 0xFFFFFFFF;   // label colour on a tonal (BTN) button
    static float TS = 1f;
    static Style S = new Style();

    static void apply(Style s) {
        S = s;
        WHITE = s.text;
        SUB = s.sub;
        BTN = s.btn;
        TILE = s.tile;
        BTN_ON = s.btnOn;
        TS = s.tScale / 100f;
    }

    /** Unfilled part of a seek bar / progress bar / ring: M3 secondary container with a theme accent, faint text colour otherwise. */
    static int track() { return S.accentOverride ? S.btn : Col.alpha(WHITE, 0.2f); }

    /** Outline of the battery: M3 on-secondary-container tint with a theme accent, half-strength text colour otherwise. */
    static int outline() { return S.accentOverride ? Col.alpha(S.btnOn, 0.6f) : Col.alpha(WHITE, 0.5f); }

    /** "Good" colour (accepted call, success, active call): Material 3 tertiary with a theme accent, classic green otherwise. */
    static int pos() { return S.accentOverride ? S.tert : GREEN; }

    /** "Bad" colour (decline, failure): Material 3 error with a theme accent, classic red otherwise. */
    static int neg() { return S.accentOverride ? S.err : RED; }

    static int acc(IslandEvent e) { return S.accentOverride ? S.accent : e.accent; }

    /** Like {@link #acc} but keeps meaning: a red event (low battery) turns M3 error, a green one (charging) turns M3 tertiary. */
    static int tone(IslandEvent e) {
        if (!S.accentOverride) return e.accent;
        if (e.accent == RED) return S.err;
        if (e.accent == GREEN) return S.tert;
        return S.accent;
    }

    /** The theme accent (Material You or custom) when one is chosen, otherwise the content's own colour. */
    static int accOr(int own) { return S.accentOverride ? S.accent : own; }

    /** Button for the card's action rows. With a theme accent: the first one is a filled accent button, the rest are tonal (Material 3 style). */
    private static View actionBtn(Context c, String label, boolean primary, Runnable run) {
        if (!S.accentOverride) return button(c, label, BTN, -1, run);
        return primary ? button(c, label, S.accent, -1, S.accentOn, run) : button(c, label, S.btn, -1, S.btnOn, run);
    }

    /** The app's versionName, for the lyrics User-Agent (set when the service starts). */
    static volatile String appVersion = "dev";

    static int px(Context c) { return dp(c, S.padX); }

    static int py(Context c) { return dp(c, S.padY); }
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

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
        return button(c, label, bgColor, icon, semantic ? 0xFFFFFFFF : (bgColor == BTN ? BTN_ON : WHITE), run);
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
        b.setContentDescription(label);   // screen readers announce the button by its label
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { run.run(); }
        });
        press(b);
        return b;
    }

    /**
     * Sends a notification's PendingIntent. On Android 14+ the sender has to opt in before the target app may start an
     * activity from the background; without it taps on action buttons (and on the banner itself) silently did nothing.
     * @return false when it could not be sent (cancelled by the app, or refused)
     */
    static boolean send(Context c, PendingIntent pi) {
        if (pi == null) return false;
        Bundle opts = null;
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                ActivityOptions ao = ActivityOptions.makeBasic();
                ao.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                opts = ao.toBundle();
            } catch (Throwable t) { opts = null; }
        }
        try {
            pi.send(c, 0, null, null, null, null, opts);
            return true;
        } catch (Throwable t) {   // CanceledException: the app replaced or cancelled it
            return false;
        }
    }

    /**
     * Runs button {@code i} of an event: the island's own action, or the app's PendingIntent. When the app's intent is gone
     * (cancelled, or replaced by a newer notification) the app is opened instead, so a press never ends in silence.
     */
    static boolean fire(Context c, IslandEvent e, int i) {
        try {
            if (i < e.actRuns.length && e.actRuns[i] != null) { e.actRuns[i].run(); return true; }
            if (i < e.actIntents.length && send(c, e.actIntents[i])) return true;
        } catch (Throwable t) { Dbg.e("action", t); /* fall through to opening the app */ }
        open(c, e);
        return false;
    }

    /** A banner's own button was pressed: it has done its job, so put it away (calls, timers and music stay on the island). */
    private static void done(IslandEvent e) {
        if (e.type != IslandEvent.NOTIF) return;
        Bus.remove(e.key);
        ReplyActivity.closeFromIsland();   // another button was pressed while a reply box was open: the box goes with the banner
    }

    static void open(Context c, IslandEvent e) { open(c, e.contentIntent, e.pkg); }

    static void open(Context c, PendingIntent content, String pkg) {
        if (content != null && send(c, content)) return;
        try {
            if (pkg != null) {
                Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); }
            }
        } catch (Throwable t) { Dbg.e("open", t); }
    }

    /** True while the lock screen is up: no quick reply from there (the box could not show over it, and it would skip the unlock). */
    static boolean keyguarded(Context c) {
        try { return ((android.app.KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked(); }
        catch (Throwable t) { return false; }
    }

    /** True while the phone is locked behind a PIN, pattern or password (a plain swipe-to-unlock lock does not count). */
    static boolean secureLocked(Context c) {
        try {
            android.app.KeyguardManager km = (android.app.KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
            return km.isKeyguardLocked() && km.isKeyguardSecure();
        } catch (Throwable t) { return true; }   // cannot tell: assume the strict case
    }

    /** The user's lock-screen choice "hide sensitive content" (an unreadable setting counts as hidden: the strict case). */
    static boolean hideSensitiveOnLock(Context c) {
        try { return android.provider.Settings.Secure.getInt(c.getContentResolver(), "lock_screen_allow_private_notifications", 1) == 0; }
        catch (Throwable t) { return true; }
    }

    /** The user's lock-screen choice "don't show notifications at all". */
    static boolean lockShowsNoNotifications(Context c) {
        try { return android.provider.Settings.Secure.getInt(c.getContentResolver(), "lock_screen_show_notifications", 1) == 0; }
        catch (Throwable t) { return false; }
    }

    /** setText that does nothing when the text already reads the same (a no-op setText still re-measures the view). */
    static void put(TextView t, String s) {
        if (!s.contentEquals(t.getText())) t.setText(s);
    }

    static String timerText(IslandEvent e) {
        return Parse.clock(Parse.chronoMs(e.when, e.countDown, System.currentTimeMillis()));
    }

    /** The idle pill's clock text: "1:05" (12 h) or "13:05" (24 h), following the system setting. */
    static String clockText(boolean use24) {
        Calendar cal = Calendar.getInstance();
        return Parse.timeOfDay(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), use24);
    }

    /** "SUN, NOV 1": the short caption under the compact clock, in the phone's language and date order. */
    static String shortDate(long now) {
        String pat;
        try { pat = DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEMMMd"); }
        catch (Throwable t) { pat = "EEE, MMM d"; }
        return DateFormat.format(pat, now).toString().toUpperCase(java.util.Locale.getDefault());
    }

    static CharSequence dateText() {
        return DateFormat.format("EEEE, MMM d", System.currentTimeMillis());
    }

    /** "10:42" with an accent colon and, in 12 h, a small dim "PM" after it ({@code suffix} = its size relative to the digits). */
    static CharSequence bigTime(int hour24, int minute, boolean use24, int accent, float suffix) {
        String time = Parse.timeOfDay(hour24, minute, use24);
        SpannableStringBuilder sb = new SpannableStringBuilder(time);
        int colon = time.indexOf(':');
        if (colon >= 0) sb.setSpan(new ForegroundColorSpan(accent), colon, colon + 1, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!use24) {
            String mark = " " + ampm(hour24);
            int from = sb.length();
            sb.append(mark);
            sb.setSpan(new RelativeSizeSpan(suffix), from, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(SUB), from, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    /** setText for styled text that does nothing when the characters already read the same. */
    static void putSpan(TextView t, CharSequence s) {
        if (!s.toString().contentEquals(t.getText())) t.setText(s);
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
                if (S.musicStyle != 0) { Music.compact(c, e, out, h, g, m); break; }   // Vinyl / Ring / Wave / Song live in Music
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
                        if (l == null) { e.tickHint = 1500; ls.tick(0, 0, false, 1f); ls.setVisibility(View.INVISIBLE); eq.setVisibility(View.VISIBLE); }
                        else {
                            e.tickHint = 1000;   // the lyric view schedules its own line changes and slide; this tick is only a re-anchor
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
                        LyricsNet.load(c, e, lkey, lnow, show);
                        show.run();   // a seek / pause / speed change re-anchors the lyric right away, so the tick can stay slow
                        return true;
                    }
                };
                LyricsNet.load(c, e, lkey, lnow, show);
                break;
            }
            case IslandEvent.CALL: ActivityCards.compactCall(c, e, out, h, g, m); break;
            case IslandEvent.TIMER: ActivityCards.compactTimer(c, e, out, h, g, m); break;
            case IslandEvent.NAV: ActivityCards.compactNav(c, e, out, h, g, m); break;
            case IslandEvent.PROGRESS:
            case IslandEvent.LIVE: ActivityCards.compactProgress(c, e, out, h, g, m); break;
            case IslandEvent.PREVIEW: {
                out.addView(new IconView(c, IconView.NOTE, accOr(PURPLE)), fl(g, g, Gravity.START | side, m));
                EqView eq = new EqView(c);
                eq.setColor(accOr(PURPLE));
                out.addView(eq, fl(Math.round(g * 0.9f), Math.round(g * 0.75f), Gravity.END | side, m));
                break;
            }
            case IslandEvent.CLOCK: {
                ClockCards.compactClock(c, e, out, h, g, m, side);
                break;
            }
            default: ActivityCards.compactGeneric(c, e, out, h, g, m);
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

    // ---------------------------------------------------------------- idle clock (compact pill)

    /** "PM" / "AM" in the phone's language. */
    static String ampm(int hour24) {
        String[] ap = amPmStrings();
        return hour24 % 24 >= 12 ? ap[1] : ap[0];
    }

    private static java.util.Locale apLocale;
    private static String[] apStrings;

    /** Symbols are built once per language instead of once per redraw (the clock cards call this every minute tick). */
    private static String[] amPmStrings() {
        java.util.Locale now = java.util.Locale.getDefault();
        String[] ap = apStrings;
        if (ap == null || !now.equals(apLocale)) {
            ap = new DateFormatSymbols(now).getAmPmStrings();
            apStrings = ap;
            apLocale = now;
        }
        return ap;
    }

    /** {@code text} followed by a small dim AM / PM (12 h only). */
    static CharSequence withAmPm(CharSequence text, int hour24, boolean use24, float size) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        if (!use24) {
            int from = sb.length();
            sb.append(' ').append(ampm(hour24));
            sb.setSpan(new RelativeSizeSpan(size), from, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(SUB), from, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    static CharSequence tint(String text, int color) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        sb.setSpan(new ForegroundColorSpan(color), 0, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /**
     * The idle clock on the compact pill, in the style chosen under Style > Idle clock:
     * 0 Dial (mini dial left, time + date right), 1 Center (just the time, centred), 2 Split (hour left, minutes right, the camera
     * between them), 3 Date (day left, time right), 4 Line (centred time over a line with a round head that fills as the hour goes),
     * 5 Capsule (the time in a filled accent capsule), 6 Sky (a sun / moon glyph for the part of the day, time right),
     * 7 Aura (the digits fade from the accent into the text colour), 8 Dots (the week as seven dots, today lit, time right),
     * 9 Beacon (a glowing lamp inside a ring that fills with the day, and a softly glowing time).
     * Everything repaints once a minute through {@code e.tC}.
     */
    static final int CLOCK_STYLES = 10;

    // ---------------------------------------------------------------- expanded cards

    static View buildExpanded(Context c, IslandEvent e, int width) {
        e.uE = null;
        e.tE = null;
        switch (e.type) {
            case IslandEvent.MEDIA: return Music.expanded(c, e, width);
            case IslandEvent.CALL: return ActivityCards.call(c, e);
            case IslandEvent.PAYMENT: return ActivityCards.payment(c, e);
            case IslandEvent.CHARGE: return ActivityCards.charge(c, e);
            case IslandEvent.PROGRESS: return ActivityCards.progress(c, e);
            case IslandEvent.LIVE: return ActivityCards.live(c, e);
            case IslandEvent.TIMER: return ActivityCards.timer(c, e);
            case IslandEvent.CLOCK: return ClockCards.clockCard(c, e);
            case IslandEvent.SYSTEM: return ActivityCards.system(c, e);
            case IslandEvent.NAV: return ActivityCards.nav(c, e);
            default: return ActivityCards.notif(c, e);
        }
    }

    static View actionsRow(final Context c, final IslandEvent e, int max) {
        LinearLayout r = row(c);
        r.setPadding(0, dp(c, 12), 0, 0);
        int n = Math.min(max, e.actLabels.length);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            View b = actionBtn(c, e.actLabels[i], i == 0, new Runnable() {
                @Override public void run() { fire(c, e, idx); done(e); }
            });
            LinearLayout.LayoutParams p = lpw(0, dp(c, 40), 1f);
            if (i > 0) p.setMarginStart(dp(c, 8));
            r.addView(b, p);
        }
        return r;
    }

    /** "Reply" (opens the quick-reply box) next to the notification's first other action, if it has one. */
    static View replyRow(final Context c, final IslandEvent e) {
        LinearLayout r = row(c);
        r.setPadding(0, dp(c, 12), 0, 0);
        r.addView(actionBtn(c, Str.t(c, "reply", "Reply"), true, new Runnable() {
            @Override public void run() { ReplyActivity.start(c, e); }
        }), lpw(0, dp(c, 40), 1f));
        if (e.actLabels.length > 0) {
            LinearLayout.LayoutParams p = lpw(0, dp(c, 40), 1f);
            p.setMarginStart(dp(c, 8));
            r.addView(actionBtn(c, e.actLabels[0], false, new Runnable() {
                @Override public void run() { fire(c, e, 0); done(e); }
            }), p);
        }
        return r;
    }

    /** "Day  [bar]  42%": how much of the day is gone. */
    static final class DayRow {
        final LinearLayout row;
        final SeekView bar;
        final TextView pct;

        DayRow(Context c, int ac, int topPad) {
            row = Cards.row(c);
            row.setPadding(0, dp(c, topPad), 0, 0);
            bar = new SeekView(c);
            bar.setInteractive(false);
            bar.setColor(ac);
            pct = tv(c, "", 12f, WHITE, true);
            pct.setFontFeatureSettings("tnum");
            row.addView(tv(c, Str.t(c, "clock_day", "Day"), 11.5f, SUB, false));
            LinearLayout.LayoutParams bp = lpw(0, dp(c, 18), 1f);
            bp.setMarginStart(dp(c, 10));
            bp.setMarginEnd(dp(c, 10));
            row.addView(bar, bp);
            row.addView(pct);
        }
    }

    /** "● Day  43%": a coloured bullet, a dim label, the value in the text colour (a legend line for the rings). */
    static CharSequence legend(int color, String label, String value) {
        SpannableStringBuilder sb = new SpannableStringBuilder("\u25CF  ");
        sb.setSpan(new ForegroundColorSpan(color), 0, 1, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(label).append("  ");
        int from = sb.length();
        sb.append(value);
        sb.setSpan(new ForegroundColorSpan(WHITE), from, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    static String titleCase(String s) {
        return s.length() == 0 ? s : s.substring(0, 1) + s.substring(1).toLowerCase(java.util.Locale.getDefault());
    }

    static TextView endAligned(TextView t) {
        t.setGravity(Gravity.END);
        return t;
    }

    /**
     * The expanded idle clock, in the style chosen under Style > Idle clock:
     * 0 Classic (big time, time in words, dial with a seconds hand, date, this week, the day's progress),
     * 1 Digital (a big centred time, words, date, progress), 2 Analog (a large dial beside the time and date, progress),
     * 3 Bold (huge stacked hour and minutes, the date beside them, this week), 4 Week (time and date, this week, progress),
     * 5 Rings (three activity rings: the day, the hour and the minute), 6 Flip (a flip clock with a seconds line),
     * 7 Horizon (the sun or moon travelling an arc over the time).
     */
    static final int ECLOCK_STYLES = 8;

    /** The classic music card (style 0); the other styles are built by {@link Music}. */
    static View media(final Context c, final IslandEvent e) {
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
        if (S.accentOverride) seek.setColor(S.accent);
        seek.setListener(new SeekView.Listener() {
            @Override public void onSeek(float f) { if (e.durMs > 0) seekTo(e, (long) (f * e.durMs)); }
        });
        sr.addView(el, lp(dp(c, 38), WRAP));
        sr.addView(seek, lpw(0, dp(c, 22), 1f));
        sr.addView(rem, lp(dp(c, 44), WRAP));
        root.addView(sr);

        LinearLayout ctl = row(c);
        ctl.setGravity(Gravity.CENTER);
        final int skipFg = S.accentOverride ? S.btnOn : WHITE;   // M3: the skip buttons use the tonal (secondary) tint
        final IconView prev = new IconView(c, IconView.PREV, skipFg), next = new IconView(c, IconView.NEXT, skipFg);
        // Material 3 style: with a theme accent the play button is a filled accent pill with an on-accent glyph
        final int playFg = S.accentOverride ? S.accentOn : WHITE;
        final IconView play = new IconView(c, e.playing ? IconView.PAUSE : IconView.PLAY, playFg);
        if (S.accentOverride) play.setBackground(bg(S.accent, dp(c, 28)));
        prev.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        next.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        if (S.accentOverride) play.setPadding(dp(c, 24), dp(c, 14), dp(c, 24), dp(c, 14));   // glyph sits inside the filled pill
        else play.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        prev.setContentDescription(Str.t(c, "prev_track", "Previous track"));
        next.setContentDescription(Str.t(c, "next_track", "Next track"));
        play.setContentDescription(Str.t(c, "play_pause", "Play or pause"));
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
                if (S.accentOverride) seek.setColor(S.accent);
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
