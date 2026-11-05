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

import static com.vxyzview.voidisland.Cards.*;

/**
 * The idle clock as it is drawn on the island: the compact pill ({@link #compactClock}) and the expanded card ({@link #clockCard}).
 * Split out of {@link Cards}, whose helpers (text, rows, colours, sizes) it uses through a static import.
 */
final class ClockCards {
    private ClockCards() {}

    /**
     * The idle clock on the compact pill, in the style chosen under Style > Idle clock (the styles are listed at
     * {@link Cards#CLOCK_STYLES}). Everything repaints once a minute through {@code e.tC}.
     */
    static void compactClock(Context c, IslandEvent e, FrameLayout out, int h, int g, int m, int side) {
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

    /** The expanded idle-clock card, in the style chosen under Style > Idle clock (see {@link Cards#ECLOCK_STYLES}). */
    static View clockCard(final Context c, final IslandEvent e) {
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

        final String lblDay = Str.t(c, "clock_day", "Day"), lblHour = Str.t(c, "clock_hour", "Hour"), lblMinute = Str.t(c, "clock_minute", "Minute");
        final String fmtWeek = Str.t(c, "clock_week", "Week %d"), fmtMin = Str.t(c, "clock_min", "%d min"), fmtSec = Str.t(c, "clock_sec", "%d s");
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
                if (week != null) put(week, String.format(fmtWeek, cal.get(Calendar.WEEK_OF_YEAR)));
                if (strip != null) strip.update(ms);
                if (rings != null) rings.set(Parse.dayFrac(hr, mn, sc), (mn + sc / 60f) / 60f, sc / 60f);
                if (sky != null) sky.hour(hr);
                if (leg != null) {
                    putSpan(leg[0], legend(ac, lblDay, Math.round(Parse.dayFrac(hr, mn, sc) * 100f) + "%"));
                    putSpan(leg[1], legend(c2, lblHour, String.format(fmtMin, mn)));
                    putSpan(leg[2], legend(c3, lblMinute, String.format(fmtSec, sc)));
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
}
