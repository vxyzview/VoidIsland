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
        } catch (Throwable t) { /* fall through to opening the app */ }
        open(c, e);
        return false;
    }

    /** A banner's own button was pressed: it has done its job, so put it away (calls, timers and music stay on the island). */
    private static void done(IslandEvent e) {
        if (e.type == IslandEvent.NOTIF) Bus.remove(e.key);
    }

    static void open(Context c, IslandEvent e) {
        if (e.contentIntent != null && send(c, e.contentIntent)) return;
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

    private static CharSequence dateText() {
        return DateFormat.format("EEEE, MMM d", System.currentTimeMillis());
    }

    /** "10:42" with an accent colon and, in 12 h, a small dim "PM" after it ({@code suffix} = its size relative to the digits). */
    static CharSequence bigTime(int hour24, int minute, boolean use24, int accent, float suffix) {
        String time = Parse.timeOfDay(hour24, minute, use24);
        SpannableStringBuilder sb = new SpannableStringBuilder(time);
        int colon = time.indexOf(':');
        if (colon >= 0) sb.setSpan(new ForegroundColorSpan(accent), colon, colon + 1, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!use24) {
            String[] ap = new DateFormatSymbols().getAmPmStrings();
            String mark = " " + (hour24 % 24 >= 12 ? ap[1] : ap[0]);
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
                        lyricsLoad(c, e, lkey, lnow, show);
                        show.run();   // a seek / pause / speed change re-anchors the lyric right away, so the tick can stay slow
                        return true;
                    }
                };
                lyricsLoad(c, e, lkey, lnow, show);
                break;
            }
            case IslandEvent.CALL: {
                out.addView(new IconView(c, IconView.PHONE, pos()), fl(g, g, Gravity.START | side, m));
                if (e.chrono && !e.incoming) {
                    final TextView t = tv(c, timerText(e), 13f, pos(), true);
                    t.setFontFeatureSettings("tnum");
                    out.addView(t, fl(WRAP, WRAP, Gravity.END | side, m));
                    e.tC = new Runnable() { @Override public void run() { put(t, timerText(e)); } };
                }
                break;
            }
            case IslandEvent.TIMER: {
                out.addView(new IconView(c, IconView.TIMER, accOr(ORANGE)), fl(g, g, Gravity.START | side, m));
                final TextView t = tv(c, timerText(e), 13f, accOr(ORANGE), true);
                t.setFontFeatureSettings("tnum");
                out.addView(t, fl(WRAP, WRAP, Gravity.END | side, m));
                e.tC = new Runnable() { @Override public void run() { put(t, timerText(e)); } };
                break;
            }
            case IslandEvent.NAV: {
                out.addView(new IconView(c, IconView.NAV, accOr(BLUE)), fl(g, g, Gravity.START | side, m));
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
                out.addView(new IconView(c, IconView.NOTE, accOr(PURPLE)), fl(g, g, Gravity.START | side, m));
                EqView eq = new EqView(c);
                eq.setColor(accOr(PURPLE));
                out.addView(eq, fl(Math.round(g * 0.9f), Math.round(g * 0.75f), Gravity.END | side, m));
                break;
            }
            case IslandEvent.CLOCK: {
                compactClock(c, e, out, h, g, m, side);
                break;
            }
            default: {
                if (e.icon != null) {
                    RoundImage ri = new RoundImage(c);
                    ri.set(e.icon, g * 0.3f);
                    out.addView(ri, fl(g, g, Gravity.START | side, m));
                } else {
                    out.addView(new IconView(c, e.iconType >= 0 ? e.iconType : IconView.BELL, tone(e)), fl(g, g, Gravity.START | side, m));
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

    // ---------------------------------------------------------------- idle clock (compact pill)

    /** "PM" / "AM" in the phone's language. */
    private static String ampm(int hour24) {
        String[] ap = new DateFormatSymbols().getAmPmStrings();
        return hour24 % 24 >= 12 ? ap[1] : ap[0];
    }

    /** {@code text} followed by a small dim AM / PM (12 h only). */
    private static CharSequence withAmPm(CharSequence text, int hour24, boolean use24, float size) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        if (!use24) {
            int from = sb.length();
            sb.append(' ').append(ampm(hour24));
            sb.setSpan(new RelativeSizeSpan(size), from, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(SUB), from, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private static CharSequence tint(String text, int color) {
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

    private static void compactClock(Context c, IslandEvent e, FrameLayout out, int h, int g, int m, int side) {
        final boolean use24 = DateFormat.is24HourFormat(c);
        final int ac = acc(e);
        final float hdp = h / c.getResources().getDisplayMetrics().density;
        final boolean tall = h >= dp(c, (14.5f + 8.5f) * TS * 1.2f + 4f);   // room for two lines of text
        final Runnable refresh;
        switch (S.clockStyle) {
            case 1: {   // Center
                final TextView t = tv(c, "", 15.5f, WHITE, true);
                t.setFontFeatureSettings("tnum");
                t.setLetterSpacing(0.02f);
                out.addView(t, fl(WRAP, WRAP, Gravity.CENTER, 0));
                refresh = new Runnable() {
                    @Override public void run() {
                        Calendar cal = Calendar.getInstance();
                        putSpan(t, bigTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), use24, ac, 0.58f));
                    }
                };
                break;
            }
            case 2: {   // Split
                final TextView hr = tv(c, "", 15.5f, WHITE, true);
                final TextView mn = tv(c, "", 15.5f, ac, true);
                hr.setFontFeatureSettings("tnum");
                mn.setFontFeatureSettings("tnum");
                out.addView(hr, fl(WRAP, WRAP, Gravity.START | side, m + dp(c, 2)));
                out.addView(mn, fl(WRAP, WRAP, Gravity.END | side, m + dp(c, 2)));
                refresh = new Runnable() {
                    @Override public void run() {
                        Calendar cal = Calendar.getInstance();
                        int hh = cal.get(Calendar.HOUR_OF_DAY);
                        String[] parts = Parse.timeParts(hh, cal.get(Calendar.MINUTE), use24);
                        putSpan(hr, parts[0]);
                        putSpan(mn, withAmPm(tint(parts[1], ac), hh, use24, 0.58f));
                    }
                };
                break;
            }
            case 3: {   // Date
                final TextView t = tv(c, "", 14.5f, WHITE, true);
                t.setFontFeatureSettings("tnum");
                t.setLetterSpacing(0.02f);
                out.addView(t, fl(WRAP, WRAP, Gravity.END | side, m));
                final TextView wd = tv(c, "", tall ? 8.5f : 12.5f, ac, true);
                final TextView dn = tall ? tv(c, "", 14.5f, WHITE, true) : null;
                wd.setLetterSpacing(0.08f);
                LinearLayout box = col(c);
                box.setGravity(Gravity.START);
                box.addView(wd, lp(WRAP, WRAP));
                if (dn != null) {
                    LinearLayout.LayoutParams dl = lp(WRAP, WRAP);
                    dl.topMargin = dp(c, 1);
                    box.addView(dn, dl);
                }
                out.addView(box, fl(WRAP, WRAP, Gravity.START | side, m + dp(c, 2)));
                refresh = new Runnable() {
                    @Override public void run() {
                        Calendar cal = Calendar.getInstance();
                        int hh = cal.get(Calendar.HOUR_OF_DAY);
                        putSpan(t, bigTime(hh, cal.get(Calendar.MINUTE), use24, ac, 0.58f));
                        String day = String.valueOf(cal.get(Calendar.DAY_OF_MONTH));
                        String name = DateFormat.format("EEE", cal.getTimeInMillis()).toString().toUpperCase(java.util.Locale.getDefault());
                        if (dn != null) { putSpan(wd, name); putSpan(dn, day); }
                        else putSpan(wd, name + " " + day);
                    }
                };
                break;
            }
            case 4: {   // Line: the time over a slim line whose round head travels the hour. Always drawn, the text shrinks to make room.
                final float barDp = 6f, gapDp = 1f;
                final boolean bar = hdp >= 22f;
                final float sp = Math.max(10f, Math.min(15.5f, (hdp - 6f - gapDp - (bar ? barDp : 0f)) / (1.2f * TS)));
                final TextView t = tv(c, "", sp, WHITE, true);
                t.setFontFeatureSettings("tnum");
                t.setLetterSpacing(0.02f);
                t.setGravity(Gravity.CENTER);
                final MinuteBar mb = bar ? new MinuteBar(c) : null;
                LinearLayout box = col(c);
                box.setGravity(Gravity.CENTER_HORIZONTAL);
                box.addView(t, lp(WRAP, WRAP));
                if (mb != null) {
                    mb.style(ac);
                    mb.knob(true);
                    LinearLayout.LayoutParams bl = lp(dp(c, 60f), dp(c, barDp));
                    bl.topMargin = dp(c, gapDp);
                    box.addView(mb, bl);
                }
                out.addView(box, fl(WRAP, WRAP, Gravity.CENTER, 0));
                refresh = new Runnable() {
                    @Override public void run() {
                        Calendar cal = Calendar.getInstance();
                        int mi = cal.get(Calendar.MINUTE);
                        putSpan(t, bigTime(cal.get(Calendar.HOUR_OF_DAY), mi, use24, ac, 0.58f));
                        if (mb != null) mb.frac((mi + cal.get(Calendar.SECOND) / 60f) / 60f);
                    }
                };
                break;
            }
            case 5: {   // Capsule: the time inside a filled accent capsule, AM / PM just outside it
                final int on = S.accentOverride ? S.accentOn : Col.onColor(ac);
                final TextView t = tv(c, "", 14f, on, true);
                t.setFontFeatureSettings("tnum");
                t.setLetterSpacing(0.02f);
                t.setGravity(Gravity.CENTER);
                final int vp = Math.round(Math.max(0f, Math.min(3.5f, (hdp - 6f - 16.8f * TS) / 2f)) * c.getResources().getDisplayMetrics().density);
                t.setPadding(dp(c, 12), vp, dp(c, 12), vp);
                t.setBackground(bg(ac, dp(c, 40)));
                final TextView ap = use24 ? null : tv(c, "", 9.5f, SUB, true);
                LinearLayout box = row(c);
                box.addView(t, lp(WRAP, WRAP));
                if (ap != null) {
                    LinearLayout.LayoutParams al = lp(WRAP, WRAP);
                    al.setMarginStart(dp(c, 5));
                    box.addView(ap, al);
                }
                out.addView(box, fl(WRAP, WRAP, Gravity.CENTER, 0));
                refresh = new Runnable() {
                    @Override public void run() {
                        Calendar cal = Calendar.getInstance();
                        int hh = cal.get(Calendar.HOUR_OF_DAY);
                        SpannableStringBuilder sb = new SpannableStringBuilder(Parse.timeOfDay(hh, cal.get(Calendar.MINUTE), use24));
                        int colon = sb.toString().indexOf(':');
                        if (colon >= 0) sb.setSpan(new ForegroundColorSpan(Col.alpha(on, 0.6f)), colon, colon + 1, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                        putSpan(t, sb);
                        if (ap != null) putSpan(ap, ampm(hh));
                    }
                };
                break;
            }
            case 6: {   // Sky: a sun or moon for the part of the day on the left, the time (and the part's name) on the right
                final SkyGlyph sky = new SkyGlyph(c);
                sky.style(accOr(YELLOW), accOr(0xFFD6E2FF));
                out.addView(sky, fl(g, g, Gravity.START | side, m));
                final TextView t = tv(c, "", 14.5f, WHITE, true);
                t.setFontFeatureSettings("tnum");
                t.setLetterSpacing(0.02f);
                t.setGravity(Gravity.END);
                final TextView pt = tall ? tv(c, "", 8.5f, ac, true) : null;
                LinearLayout box = col(c);
                box.setGravity(Gravity.END);
                box.addView(t, lp(WRAP, WRAP));
                if (pt != null) {
                    pt.setLetterSpacing(0.1f);
                    pt.setGravity(Gravity.END);
                    LinearLayout.LayoutParams pl = lp(WRAP, WRAP);
                    pl.topMargin = dp(c, 1);
                    box.addView(pt, pl);
                }
                out.addView(box, fl(WRAP, WRAP, Gravity.END | side, m));
                refresh = new Runnable() {
                    @Override public void run() {
                        Calendar cal = Calendar.getInstance();
                        int hh = cal.get(Calendar.HOUR_OF_DAY);
                        sky.hour(hh);
                        putSpan(t, bigTime(hh, cal.get(Calendar.MINUTE), use24, ac, 0.62f));
                        if (pt != null) putSpan(pt, DayPart.NAMES[DayPart.of(hh)]);
                    }
                };
                break;
            }
            case 7: {   // Aura: digits that fade from the accent into the text colour
                final GradientText t = new GradientText(c);
                t.setText("");
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * TS);
                t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                t.setIncludeFontPadding(false);
                t.setMaxLines(1);
                t.setFontFeatureSettings("tnum");
                t.setLetterSpacing(0.03f);
                t.colors(ac, WHITE);
                final TextView ap = use24 ? null : tv(c, "", 9.5f, SUB, true);
                LinearLayout box = row(c);
                box.addView(t, lp(WRAP, WRAP));
                if (ap != null) {
                    LinearLayout.LayoutParams al = lp(WRAP, WRAP);
                    al.setMarginStart(dp(c, 4));
                    box.addView(ap, al);
                }
                out.addView(box, fl(WRAP, WRAP, Gravity.CENTER, 0));
                refresh = new Runnable() {
                    @Override public void run() {
                        Calendar cal = Calendar.getInstance();
                        int hh = cal.get(Calendar.HOUR_OF_DAY);
                        putSpan(t, Parse.timeOfDay(hh, cal.get(Calendar.MINUTE), use24));
                        if (ap != null) putSpan(ap, ampm(hh));
                    }
                };
                break;
            }
            case 8: {   // Dots: the week as seven dots (today lit), the time on the right
                final WeekDots wk = new WeekDots(c);
                wk.style(ac);
                out.addView(wk, fl(dp(c, 54), Math.min(g, dp(c, 18)), Gravity.START | side, m + dp(c, 2)));
                final TextView t = tv(c, "", 14.5f, WHITE, true);
                t.setFontFeatureSettings("tnum");
                t.setLetterSpacing(0.02f);
                out.addView(t, fl(WRAP, WRAP, Gravity.END | side, m));
                refresh = new Runnable() {
                    @Override public void run() {
                        Calendar cal = Calendar.getInstance();
                        wk.update(cal.getTimeInMillis());
                        putSpan(t, bigTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), use24, ac, 0.58f));
                    }
                };
                break;
            }
            case 9: {   // Beacon: a glowing lamp in a ring that fills with the day on the left, the glowing time on the right
                final BeaconView bc = new BeaconView(c);
                bc.style(ac);
                out.addView(bc, fl(g, g, Gravity.START | side, m));
                final TextView t = tv(c, "", 15f, WHITE, true);
                t.setFontFeatureSettings("tnum");
                t.setLetterSpacing(0.03f);
                t.setGravity(Gravity.END);
                t.setShadowLayer(dp(c, 5), 0f, 0f, Col.alpha(ac, 0.9f));   // the glow: text shadow is the one blur a hardware canvas draws
                final TextView pc = tall ? tv(c, "", 8.5f, ac, true) : null;
                LinearLayout box = col(c);
                box.setGravity(Gravity.END);
                box.addView(t, lp(WRAP, WRAP));
                if (pc != null) {
                    pc.setLetterSpacing(0.1f);
                    pc.setGravity(Gravity.END);
                    LinearLayout.LayoutParams pl = lp(WRAP, WRAP);
                    pl.topMargin = dp(c, 1);
                    box.addView(pc, pl);
                }
                out.addView(box, fl(WRAP, WRAP, Gravity.END | side, m));
                refresh = new Runnable() {
                    @Override public void run() {
                        Calendar cal = Calendar.getInstance();
                        int hh = cal.get(Calendar.HOUR_OF_DAY), mi = cal.get(Calendar.MINUTE);
                        float day = (hh * 60 + mi) / 1440f;
                        bc.day(day);
                        putSpan(t, bigTime(hh, mi, use24, ac, 0.58f));
                        if (pc != null) putSpan(pc, Math.round(day * 100f) + "% OF DAY");
                    }
                };
                break;
            }
            default: {   // Dial
                // Left: a mini dial whose accent ring fills with the minutes of the hour.
                // Right: the time (accent colon, small dim AM/PM) and, when the pill is tall enough, the date under it.
                final ClockFaceView face = new ClockFaceView(c);
                face.style(ac, false);
                face.sweep(true);
                out.addView(face, fl(g, g, Gravity.START | side, m));
                final TextView t = tv(c, "", 14.5f, WHITE, true);
                t.setFontFeatureSettings("tnum");
                t.setLetterSpacing(0.02f);
                t.setGravity(Gravity.END);
                final TextView dt = tall ? tv(c, "", 8.5f, SUB, true) : null;
                LinearLayout box = col(c);
                box.setGravity(Gravity.END);
                box.addView(t, lp(WRAP, WRAP));
                if (dt != null) {
                    dt.setLetterSpacing(0.08f);
                    dt.setGravity(Gravity.END);
                    LinearLayout.LayoutParams dl = lp(WRAP, WRAP);
                    dl.topMargin = dp(c, 1);
                    box.addView(dt, dl);
                }
                out.addView(box, fl(WRAP, WRAP, Gravity.END | side, m));
                refresh = new Runnable() {
                    @Override public void run() {
                        Calendar cal = Calendar.getInstance();
                        int hr = cal.get(Calendar.HOUR_OF_DAY), mn = cal.get(Calendar.MINUTE);
                        face.time(hr, mn, 0);
                        putSpan(t, bigTime(hr, mn, use24, ac, 0.62f));
                        if (dt != null) putSpan(dt, shortDate(cal.getTimeInMillis()));
                    }
                };
            }
        }
        refresh.run();
        e.tC = refresh;
    }

    // ---------------------------------------------------------------- expanded cards

    static View buildExpanded(Context c, IslandEvent e, int width) {
        e.uE = null;
        e.tE = null;
        switch (e.type) {
            case IslandEvent.MEDIA: return Music.expanded(c, e, width);
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
    private static View replyRow(final Context c, final IslandEvent e) {
        LinearLayout r = row(c);
        r.setPadding(0, dp(c, 12), 0, 0);
        r.addView(actionBtn(c, "Reply", true, new Runnable() {
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
            TextView app = tv(c, e.appName, 11f, Col.alpha(WHITE, 0.5f), false);
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
        final String[] shownLabels = e.actLabels;
        LinearLayout root = col(c);
        root.setPadding(px(c), py(c), px(c), py(c));
        LinearLayout top = row(c);
        RoundImage av = new RoundImage(c);
        if (e.icon != null) av.set(e.icon, -1f); else av.setLetter(Parse.initial(e.title), TILE);
        top.addView(av, lp(dp(c, 48), dp(c, 48)));
        LinearLayout t = col(c);
        t.setPadding(dp(c, 14), 0, dp(c, 8), 0);
        final TextView st = tv(c, e.text, 13f, inc ? pos() : SUB, false);
        final TextView nm = tv(c, e.title, 20f, WHITE, true);
        nm.setPadding(0, dp(c, 2), 0, 0);
        t.addView(st);
        t.addView(nm);
        top.addView(t, lpw(0, WRAP, 1f));
        final TextView time = hadTime && !inc ? tv(c, timerText(e), 17f, pos(), true) : null;
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
            int color, fg;
            if (kind[i] < 0) { color = neg(); fg = S.accentOverride ? S.errOn : 0xFFFFFFFF; }
            else if (kind[i] > 0) { color = pos(); fg = S.accentOverride ? S.tertOn : 0xFFFFFFFF; }
            else { color = BTN; fg = BTN_ON; }
            int icon = kind[i] < 0 ? IconView.HANGUP : (kind[i] > 0 ? IconView.PHONE : -1);
            LinearLayout.LayoutParams p = lpw(0, dp(c, 46), 1f);
            if (i > 0) p.setMarginStart(dp(c, 12));
            br.addView(button(c, label, color, icon, fg, new Runnable() { @Override public void run() { fire(c, e, idx); } }), p);
        }
        root.addView(br);
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (e.incoming != inc || e.chrono != hadTime || Math.min(2, e.actLabels.length) != n
                    || !Parse.sameLabels(shownLabels, e.actLabels)) return false;
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
        int col = e.success ? pos() : neg();
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
        root.addView(new BatteryView(c, e.level, e.charging, tone(e)), lp(dp(c, 76), dp(c, 38)));
        LinearLayout t = col(c);
        t.setPadding(dp(c, 18), 0, 0, 0);
        t.addView(tv(c, e.title, 14.5f, tone(e), true));
        TextView pct = tv(c, e.level + "%", 30f, WHITE, true);
        pct.setFontFeatureSettings("tnum");
        t.addView(pct);
        root.addView(t, lpw(0, WRAP, 1f));
        if (e.charging) root.addView(new IconView(c, IconView.BOLT, tone(e)), lp(dp(c, 26), dp(c, 26)));
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
            badge.setBackground(bg((tone(e) & 0x00FFFFFF) | 0x38000000, dp(c, 22)));
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(c, 22), dp(c, 22), Gravity.CENTER);
            badge.addView(new IconView(c, e.iconType >= 0 ? e.iconType : IconView.BELL, tone(e)), p);
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
        final String[] shownLabels = e.actLabels;
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
                if (!Parse.sameLabels(shownLabels, e.actLabels)) return false;   // the app changed its buttons: rebuild them
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

    /** "Day  [bar]  42%": how much of the day is gone. */
    private static final class DayRow {
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
            row.addView(tv(c, "Day", 11.5f, SUB, false));
            LinearLayout.LayoutParams bp = lpw(0, dp(c, 18), 1f);
            bp.setMarginStart(dp(c, 10));
            bp.setMarginEnd(dp(c, 10));
            row.addView(bar, bp);
            row.addView(pct);
        }
    }

    /** "● Day  43%": a coloured bullet, a dim label, the value in the text colour (a legend line for the rings). */
    private static CharSequence legend(int color, String label, String value) {
        SpannableStringBuilder sb = new SpannableStringBuilder("\u25CF  ");
        sb.setSpan(new ForegroundColorSpan(color), 0, 1, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(label).append("  ");
        int from = sb.length();
        sb.append(value);
        sb.setSpan(new ForegroundColorSpan(WHITE), from, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private static String titleCase(String s) {
        return s.length() == 0 ? s : s.substring(0, 1) + s.substring(1).toLowerCase(java.util.Locale.getDefault());
    }

    private static TextView endAligned(TextView t) {
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

    private static View clockCard(final Context c, final IslandEvent e) {
        final boolean use24 = DateFormat.is24HourFormat(c);
        final int ac = acc(e), acOn = S.accentOverride ? S.accentOn : Col.onColor(ac);
        final int style = S.eClockStyle;
        final int match = ViewGroup.LayoutParams.MATCH_PARENT;
        LinearLayout root = col(c);
        root.setPadding(px(c), py(c), px(c), py(c));

        TextView time0 = null, words0 = null, date0 = null, week0 = null, hour0 = null, min0 = null, dow0 = null;
        ClockFaceView face0 = null;
        WeekStrip strip0 = null;
        DayRow day0 = null;
        RingsView rings0 = null;
        SkyGlyph sky0 = null;
        TextView[] leg0 = null;
        FlipTile flipH0 = null, flipM0 = null;
        MinuteBar secBar0 = null;
        HorizonView hor0 = null;
        switch (style) {
            case 5: {   // Rings
                LinearLayout top = row(c);
                FrameLayout disc = new FrameLayout(c);
                rings0 = new RingsView(c);
                rings0.style(ac, Col.mix(ac, WHITE, 0.45f), Col.mix(ac, WHITE, 0.8f));
                disc.addView(rings0, new FrameLayout.LayoutParams(dp(c, 124), dp(c, 124)));
                sky0 = new SkyGlyph(c);
                sky0.style(accOr(YELLOW), accOr(0xFFD6E2FF));
                disc.addView(sky0, new FrameLayout.LayoutParams(dp(c, 26), dp(c, 26), Gravity.CENTER));
                top.addView(disc, lp(dp(c, 124), dp(c, 124)));
                LinearLayout info = col(c);
                info.setPadding(dp(c, 18), 0, 0, 0);
                time0 = tv(c, "", 38f, WHITE, true);
                time0.setFontFeatureSettings("tnum");
                date0 = tv(c, "", 15f, WHITE, true);
                date0.setPadding(0, dp(c, 2), 0, dp(c, 8));
                info.addView(time0);
                info.addView(date0);
                leg0 = new TextView[3];
                for (int i = 0; i < 3; i++) {
                    leg0[i] = tv(c, "", 12f, SUB, false);
                    leg0[i].setFontFeatureSettings("tnum");
                    LinearLayout.LayoutParams ll = lp(WRAP, WRAP);
                    ll.topMargin = dp(c, i == 0 ? 0 : 3);
                    info.addView(leg0[i], ll);
                }
                top.addView(info, lpw(0, WRAP, 1f));
                root.addView(top);
                break;
            }
            case 6: {   // Flip
                LinearLayout tiles = row(c);
                final int tileBg = TILE;
                flipH0 = new FlipTile(c);
                flipM0 = new FlipTile(c);
                flipH0.style(tileBg, WHITE, SUB, 56f);
                flipM0.style(tileBg, WHITE, SUB, 56f);
                tiles.addView(flipH0, lpw(0, dp(c, 100), 1f));
                ColonDots cd = new ColonDots(c);
                cd.color(ac);
                tiles.addView(cd, lp(dp(c, 22), dp(c, 100)));
                tiles.addView(flipM0, lpw(0, dp(c, 100), 1f));
                root.addView(tiles, lp(match, WRAP));
                secBar0 = new MinuteBar(c);
                secBar0.style(ac);
                secBar0.knob(true);
                LinearLayout.LayoutParams sb = lp(match, dp(c, 8));
                sb.topMargin = dp(c, 12);
                root.addView(secBar0, sb);
                LinearLayout dl = row(c);
                dl.setPadding(0, dp(c, 10), 0, 0);
                date0 = tv(c, "", 15f, WHITE, true);
                week0 = tv(c, "", 12.5f, SUB, false);
                dl.addView(date0, lpw(0, WRAP, 1f));
                dl.addView(week0);
                root.addView(dl);
                break;
            }
            case 7: {   // Horizon
                FrameLayout sky = new FrameLayout(c);
                hor0 = new HorizonView(c);
                hor0.style(ac, accOr(YELLOW), accOr(0xFFD6E2FF), use24 ? "06:00" : "6 AM", use24 ? "18:00" : "6 PM");
                sky.addView(hor0, new FrameLayout.LayoutParams(match, match));
                time0 = tv(c, "", 38f, WHITE, true);
                time0.setFontFeatureSettings("tnum");
                FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                tl.bottomMargin = dp(c, 14);
                sky.addView(time0, tl);
                root.addView(sky, lp(match, dp(c, 112)));
                date0 = tv(c, "", 15f, WHITE, true);
                date0.setGravity(Gravity.CENTER);
                date0.setPadding(0, dp(c, 8), 0, 0);
                words0 = tv(c, "", 13f, SUB, false);
                words0.setGravity(Gravity.CENTER);
                words0.setPadding(0, dp(c, 2), 0, 0);
                root.addView(date0, lp(match, WRAP));
                root.addView(words0, lp(match, WRAP));
                break;
            }
            case 1: {   // Digital
                root.setGravity(Gravity.CENTER_HORIZONTAL);
                time0 = tv(c, "", 62f, WHITE, true);
                time0.setFontFeatureSettings("tnum");
                words0 = tv(c, "", 13.5f, SUB, false);
                words0.setPadding(0, dp(c, 2), 0, 0);
                root.addView(time0, lp(WRAP, WRAP));
                root.addView(words0, lp(WRAP, WRAP));
                LinearLayout dl = row(c);
                dl.setPadding(0, dp(c, 12), 0, 0);
                date0 = tv(c, "", 15f, WHITE, true);
                week0 = tv(c, "", 12.5f, SUB, false);
                week0.setPadding(dp(c, 10), 0, 0, 0);
                dl.addView(date0);
                dl.addView(week0);
                root.addView(dl, lp(WRAP, WRAP));
                day0 = new DayRow(c, ac, 14);
                root.addView(day0.row, lp(match, WRAP));
                break;
            }
            case 2: {   // Analog
                LinearLayout top = row(c);
                face0 = new ClockFaceView(c);
                face0.style(ac, true);
                top.addView(face0, lp(dp(c, 116), dp(c, 116)));
                LinearLayout info = col(c);
                info.setPadding(dp(c, 16), 0, 0, 0);
                time0 = tv(c, "", 36f, WHITE, true);
                time0.setFontFeatureSettings("tnum");
                date0 = tv(c, "", 15f, WHITE, true);
                date0.setPadding(0, dp(c, 6), 0, 0);
                week0 = tv(c, "", 12.5f, SUB, false);
                week0.setPadding(0, dp(c, 2), 0, 0);
                info.addView(time0);
                info.addView(date0);
                info.addView(week0);
                top.addView(info, lpw(0, WRAP, 1f));
                root.addView(top);
                day0 = new DayRow(c, ac, 12);
                root.addView(day0.row, lp(match, WRAP));
                break;
            }
            case 3: {   // Bold
                LinearLayout top = row(c);
                LinearLayout stack = col(c);
                hour0 = tv(c, "", 60f, WHITE, true);
                hour0.setFontFeatureSettings("tnum");
                min0 = tv(c, "", 60f, ac, true);
                min0.setFontFeatureSettings("tnum");
                stack.addView(hour0);
                stack.addView(min0);
                top.addView(stack, lp(WRAP, WRAP));
                LinearLayout info = col(c);
                info.setGravity(Gravity.END);
                dow0 = endAligned(tv(c, "", 12.5f, ac, true));
                dow0.setLetterSpacing(0.12f);
                date0 = endAligned(tv(c, "", 24f, WHITE, true));
                date0.setPadding(0, dp(c, 2), 0, 0);
                week0 = endAligned(tv(c, "", 12.5f, SUB, false));
                words0 = endAligned(tv(c, "", 13f, SUB, false));
                words0.setPadding(0, dp(c, 8), 0, 0);
                info.addView(dow0);
                info.addView(date0);
                info.addView(week0);
                info.addView(words0);
                top.addView(info, lpw(0, WRAP, 1f));
                root.addView(top);
                strip0 = new WeekStrip(c);
                strip0.style(ac, acOn);
                LinearLayout.LayoutParams sp = lp(match, dp(c, 54));
                sp.topMargin = dp(c, 10);
                root.addView(strip0, sp);
                break;
            }
            case 4: {   // Week
                LinearLayout top = row(c);
                time0 = tv(c, "", 38f, WHITE, true);
                time0.setFontFeatureSettings("tnum");
                top.addView(time0, lpw(0, WRAP, 1f));
                LinearLayout info = col(c);
                info.setGravity(Gravity.END);
                date0 = endAligned(tv(c, "", 15f, WHITE, true));
                week0 = endAligned(tv(c, "", 12.5f, SUB, false));
                week0.setPadding(0, dp(c, 2), 0, 0);
                info.addView(date0);
                info.addView(week0);
                top.addView(info, lp(WRAP, WRAP));
                root.addView(top);
                strip0 = new WeekStrip(c);
                strip0.style(ac, acOn);
                LinearLayout.LayoutParams sp = lp(match, dp(c, 54));
                sp.topMargin = dp(c, 8);
                root.addView(strip0, sp);
                day0 = new DayRow(c, ac, 6);
                root.addView(day0.row, lp(match, WRAP));
                break;
            }
            default: {   // Classic
                LinearLayout top = row(c);
                LinearLayout left = col(c);
                time0 = tv(c, "", 46f, WHITE, true);
                time0.setFontFeatureSettings("tnum");
                words0 = tv(c, "", 13.5f, SUB, false);
                words0.setPadding(0, dp(c, 4), 0, 0);
                left.addView(time0);
                left.addView(words0);
                top.addView(left, lpw(0, WRAP, 1f));
                face0 = new ClockFaceView(c);
                face0.style(ac, true);
                top.addView(face0, lp(dp(c, 76), dp(c, 76)));
                root.addView(top);
                LinearLayout dl = row(c);
                dl.setPadding(0, dp(c, 12), 0, 0);
                date0 = tv(c, "", 15f, WHITE, true);
                week0 = tv(c, "", 12.5f, SUB, false);
                dl.addView(date0, lpw(0, WRAP, 1f));
                dl.addView(week0);
                root.addView(dl);
                strip0 = new WeekStrip(c);
                strip0.style(ac, acOn);
                LinearLayout.LayoutParams sp = lp(match, dp(c, 54));
                sp.topMargin = dp(c, 6);
                root.addView(strip0, sp);
                day0 = new DayRow(c, ac, 4);
                root.addView(day0.row, lp(match, WRAP));
            }
        }
        final TextView time = time0, words = words0, date = date0, week = week0, hourT = hour0, minT = min0, dow = dow0;
        final ClockFaceView face = face0;
        final WeekStrip strip = strip0;
        final DayRow day = day0;
        final RingsView rings = rings0;
        final SkyGlyph sky = sky0;
        final TextView[] leg = leg0;
        final FlipTile flipH = flipH0, flipM = flipM0;
        final MinuteBar secBar = secBar0;
        final HorizonView hor = hor0;
        final float suffix = style == 1 || style == 7 ? 0.3f : (style == 0 ? 0.34f : 0.4f);
        final int c2 = Col.mix(ac, WHITE, 0.45f), c3 = Col.mix(ac, WHITE, 0.8f);

        final Runnable tick = new Runnable() {
            @Override public void run() {
                Calendar cal = Calendar.getInstance();
                long ms = cal.getTimeInMillis();
                int hr = cal.get(Calendar.HOUR_OF_DAY), mn = cal.get(Calendar.MINUTE), sc = cal.get(Calendar.SECOND);
                if (face != null) face.time(hr, mn, sc);
                if (time != null) putSpan(time, bigTime(hr, mn, use24, ac, suffix));
                if (hourT != null && minT != null) {
                    String[] parts = Parse.timeParts(hr, mn, use24);
                    putSpan(hourT, parts[0]);
                    putSpan(minT, withAmPm(tint(parts[1], ac), hr, use24, 0.28f));
                }
                if (words != null) put(words, Parse.timeWords(hr, mn));
                if (dow != null) put(dow, DateFormat.format("EEEE", ms).toString().toUpperCase(java.util.Locale.getDefault()));
                if (date != null) {
                    String ds;
                    if (style == 3) ds = DateFormat.format("MMM d", ms).toString();
                    else if (style == 5) ds = DateFormat.format("EEE, MMM d", ms).toString();
                    else if (style == 7) ds = dateText().toString() + "  \u00B7  " + titleCase(DayPart.NAMES[DayPart.of(hr)]);
                    else ds = dateText().toString();
                    put(date, ds);
                }
                if (week != null) put(week, "Week " + cal.get(Calendar.WEEK_OF_YEAR));
                if (strip != null) strip.update(ms);
                if (rings != null) rings.set(Parse.dayFrac(hr, mn, sc), (mn + sc / 60f) / 60f, sc / 60f);
                if (sky != null) sky.hour(hr);
                if (leg != null) {
                    putSpan(leg[0], legend(ac, "Day", Math.round(Parse.dayFrac(hr, mn, sc) * 100f) + "%"));
                    putSpan(leg[1], legend(c2, "Hour", mn + " min"));
                    putSpan(leg[2], legend(c3, "Minute", sc + " s"));
                }
                if (flipH != null && flipM != null) {
                    String[] parts = Parse.timeParts(hr, mn, use24);
                    flipH.set(parts[0], use24 ? "" : ampm(hr));
                    flipM.set(parts[1], "");
                }
                if (secBar != null) secBar.frac((sc + (ms % 1000L) / 1000f) / 60f);
                if (hor != null) hor.time(hr + mn / 60f + sc / 3600f);
                if (day != null) {
                    float f = Parse.dayFrac(hr, mn, sc);
                    day.bar.setFrac(f);
                    put(day.pct, Math.round(f * 100f) + "%");
                }
            }
        };
        tick.run();
        e.tE = tick;
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() { tick.run(); return true; }
        };
        return root;
    }

    private static View timer(final Context c, final IslandEvent e) {
        LinearLayout root = col(c);
        root.setPadding(px(c), py(c), px(c), py(c));
        LinearLayout top = row(c);
        top.addView(new IconView(c, IconView.TIMER, accOr(ORANGE)), lp(dp(c, 30), dp(c, 30)));
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
        e.tE = new Runnable() { @Override public void run() { put(time, timerText(e)); } };
        final String[] shownLabels = e.actLabels;
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (!Parse.sameLabels(shownLabels, e.actLabels)) return false;   // Pause <-> Resume: rebuild the buttons
                label.setText(e.title);
                put(time, timerText(e));
                return true;
            }
        };
        return root;
    }

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
        prev.setContentDescription("Previous track");
        next.setContentDescription("Next track");
        play.setContentDescription("Play or pause");
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

    // ---------------------------------------------------------------- lyrics fetch (LRCLIB)

    /** Callback for {@link #lyricsFetch}: always called on the main thread (even on an instant cache hit). */
    interface GotLyrics { void got(String track, String artist, Lyrics.Loaded l); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * Two daemon workers at most: skipping through songs queues lookups instead of spawning a thread (and two HTTP calls) each.
     * Idle workers exit after 20 s (a fixed pool kept both threads, and their stacks, alive for the life of the process).
     */
    private static final java.util.concurrent.ExecutorService NET = lyricPool();

    private static java.util.concurrent.ExecutorService lyricPool() {
        java.util.concurrent.ThreadPoolExecutor ex = new java.util.concurrent.ThreadPoolExecutor(2, 2, 20L,
            java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<Runnable>(),
            new java.util.concurrent.ThreadFactory() {
                @Override public Thread newThread(Runnable r) { Thread t = new Thread(r, "vi-lyrics"); t.setDaemon(true); return t; }
            });
        ex.allowCoreThreadTimeOut(true);
        return ex;
    }
    /** Set by the worker that ran a lookup when it failed to reach the service (as opposed to being told there are no lyrics). */
    private static final ThreadLocal<Boolean> NET_ERR = new ThreadLocal<Boolean>();

    /** Lookups already running, by song: a second view asking for the same song waits for that answer. Main thread only. */
    private static final java.util.HashMap<String, java.util.ArrayList<GotLyrics>> INFLIGHT = new java.util.HashMap<String, java.util.ArrayList<GotLyrics>>();

    /** Cache-first lyrics for the playing song; slow network work sits on a daemon thread, results land on main. */
    static void lyricsFetch(final String track, final String artist, final long durS, final GotLyrics done) {
        Lyrics.Loaded hit = Lyrics.cached(track, artist);
        if (hit != null) { done.got(track, artist, hit); return; }
        if (Lyrics.missedRecently(track, artist, System.currentTimeMillis())) { done.got(track, artist, null); return; }
        final String fk = track.toLowerCase() + "|" + artist.toLowerCase();
        java.util.ArrayList<GotLyrics> waiting = INFLIGHT.get(fk);
        if (waiting != null) { waiting.add(done); return; }
        waiting = new java.util.ArrayList<GotLyrics>();
        waiting.add(done);
        INFLIGHT.put(fk, waiting);
        try {
            NET.execute(new Runnable() {
                @Override public void run() {
                    // An exception on this worker thread would kill the whole process (and with it the accessibility service).
                    Lyrics.Loaded found = null;
                    NET_ERR.set(Boolean.FALSE);
                    try { found = lyricsNet(track, artist, durS); } catch (Throwable t) { NET_ERR.set(Boolean.TRUE); /* odd lyric sheet / network hiccup: no lyrics */ }
                    final Lyrics.Loaded l = found;
                    final boolean none = found == null && !Boolean.TRUE.equals(NET_ERR.get());   // answered "nothing here" vs. could not ask
                    MAIN.post(new Runnable() {
                        @Override public void run() {
                            if (l == null) Lyrics.rememberMiss(track, artist, System.currentTimeMillis(), none ? Lyrics.NONE_TTL_MS : Lyrics.MISS_TTL_MS);
                            java.util.ArrayList<GotLyrics> all = INFLIGHT.remove(fk);
                            if (all != null) for (GotLyrics g : all) {
                                try { g.got(track, artist, l); } catch (Throwable t) { /* that card is gone or broken: the rest still get their answer */ }
                            }
                        }
                    });
                }
            });
        } catch (Throwable t) {
            INFLIGHT.remove(fk);
            done.got(track, artist, null);
        }
    }

    /** The caller's small holders (key + loaded) plus the callback that should repaint its lyric row. */
    static void lyricsLoad(final Context c, final IslandEvent e, final String[] key, final Lyrics.Loaded[] now, final Runnable on) {
        if (!Prefs.b(c, Prefs.K_LYRICS, true)) { now[0] = null; key[0] = ""; on.run(); return; }
        final String track = e.title == null ? "" : e.title.toString().trim();
        final String artist = e.text == null ? "" : e.text.toString().trim();
        if (track.length() == 0) { now[0] = null; key[0] = ""; on.run(); return; }
        if (!Lyrics.worthLookup(e.pkg, e.durMs)) { now[0] = null; key[0] = ""; on.run(); return; }   // video / podcast apps: no lookup
        final String k = track + "|" + artist + "|" + (e.durMs / 1000);
        if (k.equals(key[0])) return;   // already working on this exact song (found or not)
        key[0] = k;
        Lyrics.Loaded hit = Lyrics.cached(track, artist);
        if (hit != null) { now[0] = hit; on.run(); return; }
        lyricsFetch(track, artist, e.durMs / 1000, new GotLyrics() {
            @Override public void got(String t0, String a0, Lyrics.Loaded l) {
                if (l != null) Lyrics.remember(t0, a0, l);
                String n0 = e.title == null ? "" : e.title.toString().trim();
                String n1 = e.text == null ? "" : e.text.toString().trim();
                if (!n0.equals(t0) || !n1.equals(a0)) return;   // the song changed while we were fetching
                now[0] = l;
                on.run();
            }
        });
    }

    /**
     * LRCLIB (no key): /api/get first, then up to three searches, each looser than the last (exact title + artist, a free-text
     * "artist title", the cleaned-up title alone). The looser ones only accept a result whose length matches the song.
     */
    private static Lyrics.Loaded lyricsNet(String track, String artist, long durS) {
        JSONObject best = null;
        try {
            String q = "https://lrclib.net/api/get?track_name=" + URLEncoder.encode(track, "UTF-8")
                + (artist.length() > 0 ? "&artist_name=" + URLEncoder.encode(artist, "UTF-8") : "")
                + (durS > 0 ? "&duration=" + durS : "");
            best = httpJson(q);   // null = no exact record (404) or a busy-server 503: the searches below still run
            if (best != null && !hasLyrics(best)) best = null;   // instrumental / empty record
        } catch (Throwable t) { /* fall through to search */ }
        if (best == null) {
            NET_ERR.set(Boolean.FALSE);   // only the searches decide whether this miss is believable (the exact lookup 404s for most songs)
            try {
                String cleanTrack = Lyrics.cleanTitle(track), cleanArtist = Lyrics.cleanArtist(artist);
                String[] urls = {
                    "https://lrclib.net/api/search?track_name=" + URLEncoder.encode(track, "UTF-8")
                        + (artist.length() > 0 ? "&artist_name=" + URLEncoder.encode(artist, "UTF-8") : ""),
                    "https://lrclib.net/api/search?q=" + URLEncoder.encode((cleanArtist + " " + cleanTrack).trim(), "UTF-8"),
                    "https://lrclib.net/api/search?track_name=" + URLEncoder.encode(cleanTrack, "UTF-8")
                };
                for (int u = 0; u < urls.length && best == null; u++) {
                    JSONArray arr = httpArray(urls[u]);
                    if (arr != null) best = pickBest(arr, cleanArtist, durS, u > 0);
                }
            } catch (Throwable t) { /* offline or rate-limited: no lyrics this round, no caching of failures */ }
        }
        if (best == null) return null;
        String s0 = jstr(best, "syncedLyrics"), p0 = jstr(best, "plainLyrics");
        return Lyrics.parse(s0.length() > 0 ? s0 : null, p0.length() > 0 ? p0 : null);
    }

    /** A string field, or "" when it is missing or JSON null (Android's optString turns JSON null into the text "null"). */
    private static String jstr(JSONObject o, String key) {
        return o == null || o.isNull(key) ? "" : o.optString(key, "");
    }

    private static boolean hasLyrics(JSONObject o) { return jstr(o, "syncedLyrics").length() > 0 || jstr(o, "plainLyrics").length() > 0; }

    /** The search hit with lyrics that fits best: synced beats plain, a matching length and artist add weight. {@code strict}: length must fit. */
    private static JSONObject pickBest(JSONArray arr, String artist, long durS, boolean strict) {
        JSONObject best = null;
        int score = -1;
        String want = artist.toLowerCase();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || !hasLyrics(o)) continue;
            double gap = Math.abs(o.optDouble("duration", 0) - durS);
            boolean artistOk = want.length() > 0 && jstr(o, "artistName").toLowerCase().contains(want);
            // a loose search may only return the right song: by its length, or (when the player gives none) by its artist
            if (strict && (durS > 0 ? gap > 3 : !artistOk)) continue;
            int sc = (jstr(o, "syncedLyrics").length() > 0 ? 500 : 0) + (jstr(o, "plainLyrics").length() > 0 ? 1 : 0);
            if (durS > 0 && gap <= 2) sc += 200;
            if (artistOk) sc += 50;
            if (sc > score) { score = sc; best = o; }
        }
        return best;
    }

    private static JSONObject httpJson(String url) {
        String b = httpText(url);
        try { return b == null ? null : new JSONObject(b); } catch (Throwable t) { NET_ERR.set(Boolean.TRUE); return null; }
    }

    private static JSONArray httpArray(String url) {
        String b = httpText(url);
        try { return b == null ? null : new JSONArray(b); } catch (Throwable t) { NET_ERR.set(Boolean.TRUE); return null; }
    }

    private static String httpText(String url) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(6000);
            con.setRequestProperty("User-Agent", "VoidIsland/" + appVersion + " (https://github.com/vxyzview/VoidIsland)");
            int code = con.getResponseCode();
            if (code != 200) { if (code != 404) NET_ERR.set(Boolean.TRUE); return null; }   // 404 = the song is simply not there
            BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            try {
                StringBuilder b = new StringBuilder();
                char[] buf = new char[8192];
                for (int n; (n = r.read(buf)) > 0;) {
                    b.append(buf, 0, n);
                    if (b.length() > 600000) { NET_ERR.set(Boolean.TRUE); return null; }   // far bigger than any lyric sheet: don't buffer it (and not a believable "no lyrics")
                }
                return b.length() > 0 ? b.toString() : null;
            } finally {
                try { r.close(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            NET_ERR.set(Boolean.TRUE);
            return null;
        } finally {
            if (con != null) try { con.disconnect(); } catch (Throwable ignored) {}
        }
    }
}
