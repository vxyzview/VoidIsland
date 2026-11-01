package com.vxyzview.voidisland;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.session.PlaybackState;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The music designs, picked under Style > Music. Style 0 ("Classic") is the original pill and card and is still built by
 * {@link Cards}; everything else is built here. Names and descriptions: {@link MusicStyle}.
 *
 * Compact pill: 1 Vinyl (a spinning record), 2 Ring (round art inside a progress ring), 3 Wave (a wide rolling waveform),
 * 4 Song (the title and artist instead of lyrics), 5 Track (title + slim progress line + play badge), 6 Beat (a capsule with a
 * dot-matrix equalizer), 7 Timer (a capsule with the time left).
 * Expanded card: 1 Vinyl (a record and a tonearm), 2 Poster (big centred art), 3 Mini (one slim row), 4 Lyrics (the lines
 * of the song, big, in the middle of the card), 5 Bento (flat tonal tiles), 6 Wave (a waveform across the card), 7 Cover (wide
 * art with the title on a flat chip).
 *
 * The newest designs (5..7) are strictly flat: solid fills, tonal containers and thin lines, no gradients anywhere.
 *
 * Every design keeps what the island promises: it follows the accent, the seek bar can be dragged, the lyric offset applies,
 * and nothing animates while the song is paused or the display is off.
 */
final class Music {
    private Music() {}

    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT, MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private static Bitmap artOf(IslandEvent e) { return e.art != null ? e.art : e.icon; }

    // ================================================================== compact pill

    /** Builds the music pill for styles 1..4. {@code g} is the usual glyph size, {@code m} the side padding, both in px. */
    static void compact(final Context c, final IslandEvent e, FrameLayout out, int h, int g, int m) {
        final Style S = Cards.S;
        final int style = S.musicStyle;
        final int side = Gravity.CENTER_VERTICAL;
        final int pillW = Cards.dp(c, S.cW);
        final int gl;   // the size of the glyph on the left
        if (style == 1) gl = Math.max(g, Math.min(h - Cards.dp(c, 3), Math.round(g * 1.3f)));        // a record wants a little more room
        else if (style == 2) gl = Math.max(g, Math.min(h - Cards.dp(c, 4), Math.round(g * 1.25f)));   // so does a ring
        else gl = g;

        // ---- left: the picture
        final RoundImage ri = style >= 3 ? new RoundImage(c) : null;
        final VinylView vv = style == 1 ? new VinylView(c) : null;
        final ArtRing ar = style == 2 ? new ArtRing(c) : null;
        final View left = vv != null ? vv : (ar != null ? ar : ri);
        if (vv != null) vv.config(false, Cards.acc(e));
        out.addView(left, Cards.fl(gl, gl, Gravity.START | side, m));
        final Runnable art = new Runnable() {
            @Override public void run() {
                Bitmap b = artOf(e);
                if (vv != null) { vv.set(b); vv.setAccent(Cards.acc(e)); vv.setPlaying(e.playing); }
                if (ar != null) ar.set(b);
                if (ri != null) ri.set(b, style == 3 || style == 7 ? -1f : gl * 0.3f);   // Wave + Timer: a full circle, the rest: the usual rounded square
            }
        };
        art.run();

        if (style == 4) { song(c, e, out, h, gl, m, pillW, art); return; }
        if (style >= 5) { flat(c, e, out, h, gl, m, pillW, art, style); return; }

        // ---- right: the lyric line, or a visual while no lyric is known
        final int lw = Math.max(Cards.dp(c, 36), pillW - gl - Cards.dp(c, S.cPad * 2) - Cards.dp(c, 2));
        final LyricScroll ls = new LyricScroll(c);
        ls.bind(null, lw, Cards.WHITE, Cards.SUB, Prefs.i(c, Prefs.K_LYRSIZE, 13));
        ls.setVisibility(View.INVISIBLE);
        final EqView eq = style == 3 ? null : new EqView(c);
        final WaveView wv = style == 3 ? new WaveView(c) : null;
        final View vis;
        if (eq != null) {
            eq.setColor(Cards.acc(e));
            eq.setPlaying(e.playing);
            out.addView(eq, Cards.fl(Math.round(g * 0.9f), Math.round(g * 0.75f), Gravity.END | side, m));
            vis = eq;
        } else {
            wv.setColor(Cards.acc(e));
            wv.setPlaying(e.playing);
            out.addView(wv, Cards.fl(lw, Math.max(Cards.dp(c, 8), Math.round(gl * 0.8f)), Gravity.END | side, m));
            vis = wv;
        }
        out.addView(ls, Cards.fl(WRAP, WRAP, Gravity.END | side, m));

        final String[] lkey = {""};
        final Lyrics.Loaded[] lnow = {null};
        final Runnable show = new Runnable() {
            @Override public void run() {
                Lyrics.Loaded l = lnow[0];
                long pos = Cards.position(e);
                if (ar != null) ar.progress(e.durMs > 0 ? pos / (float) e.durMs : 0f, Cards.acc(e));
                if (l == null) {
                    e.tickHint = 1500;
                    ls.tick(0, 0, false, 1f);
                    ls.setVisibility(View.INVISIBLE);
                    vis.setVisibility(View.VISIBLE);
                } else {
                    e.tickHint = 1000;   // the lyric view schedules its own line changes and slide; this tick is only a re-anchor
                    ls.bind(l, lw, Cards.WHITE, Cards.SUB, Prefs.i(c, Prefs.K_LYRSIZE, 13));
                    ls.tick(pos + Prefs.i(c, Prefs.K_LYROFF, 200), e.durMs, e.playing, e.speed);   // same nudge as the classic pill
                    ls.setVisibility(View.VISIBLE);
                    vis.setVisibility(View.GONE);
                }
            }
        };
        e.tC = show;
        e.uC = new IslandEvent.Updater() {
            @Override public boolean update() {
                art.run();
                if (eq != null) { eq.setColor(Cards.acc(e)); eq.setPlaying(e.playing); }
                if (wv != null) { wv.setColor(Cards.acc(e)); wv.setPlaying(e.playing); }
                Cards.lyricsLoad(c, e, lkey, lnow, show);
                show.run();
                return true;
            }
        };
        Cards.lyricsLoad(c, e, lkey, lnow, show);
    }

    /** Song: art, then the title (and the artist under it when the pill is tall enough), bars at the far end. No lyric lookups at all. */
    private static void song(final Context c, final IslandEvent e, FrameLayout out, int h, int gl, int m, int pillW, final Runnable art) {
        final int side = Gravity.CENTER_VERTICAL;
        final boolean tall = h >= Cards.dp(c, (13f + 9.5f) * Cards.TS * 1.2f + 4f);   // room for two lines of text
        final int eqW = Math.round(gl * 0.9f), eqH = Math.round(gl * 0.75f);
        final int leftEdge = m + gl + Cards.dp(c, 8);
        int rightEdge = m + eqW + Cards.dp(c, 6);
        boolean eqOn = pillW - leftEdge - rightEdge >= Cards.dp(c, 44);
        if (!eqOn) rightEdge = m;
        final int avail = Math.max(Cards.dp(c, 24), pillW - leftEdge - rightEdge);

        final TextView title = Cards.tv(c, e.title, tall ? 12.5f : 13f, Cards.WHITE, true);
        final TextView sub = tall ? Cards.tv(c, e.text, 9.5f, Cards.SUB, false) : null;
        title.setMaxWidth(avail);
        LinearLayout box = Cards.col(c);
        box.setGravity(Gravity.START);
        box.addView(title, Cards.lp(WRAP, WRAP));
        if (sub != null) {
            sub.setMaxWidth(avail);
            LinearLayout.LayoutParams sl = Cards.lp(WRAP, WRAP);
            sl.topMargin = Cards.dp(c, 1);
            box.addView(sub, sl);
            sub.setVisibility(e.text == null || e.text.length() == 0 ? View.GONE : View.VISIBLE);
        }
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START | side);
        bl.setMargins(leftEdge, 0, rightEdge, 0);
        out.addView(box, bl);

        final EqView eq = eqOn ? new EqView(c) : null;
        if (eq != null) {
            eq.setColor(Cards.acc(e));
            eq.setPlaying(e.playing);
            out.addView(eq, Cards.fl(eqW, eqH, Gravity.END | side, m));
        }
        e.tickHint = 5000;   // nothing on this pill moves by itself except the bars, which draw themselves
        e.tC = new Runnable() { @Override public void run() { /* nothing to refresh */ } };
        e.uC = new IslandEvent.Updater() {
            @Override public boolean update() {
                art.run();
                title.setText(e.title);
                if (sub != null) {
                    sub.setText(e.text);
                    sub.setVisibility(e.text == null || e.text.length() == 0 ? View.GONE : View.VISIBLE);
                }
                if (eq != null) { eq.setColor(Cards.acc(e)); eq.setPlaying(e.playing); }
                return true;
            }
        };
    }

    /**
     * The flat pills. 5 Track: the title with a slim progress line under it and a play / pause badge. 6 Beat: the title (and artist)
     * and a tonal capsule holding a dot-matrix equalizer. 7 Timer: the title and a tonal capsule with the time left. Solid fills
     * only. No lyric lookups, so these never touch the network.
     */
    private static void flat(final Context c, final IslandEvent e, FrameLayout out, int h, int gl, int m, int pillW, final Runnable art, final int style) {
        final int side = Gravity.CENTER_VERTICAL;
        final boolean tall = h >= Cards.dp(c, (13f + 9.5f) * Cards.TS * 1.2f + 4f + (style == 5 ? 6f : 0f));   // room for a second line of text
        final int cap = Math.max(Cards.dp(c, 14), Math.min(h - Cards.dp(c, 8), Cards.dp(c, 22)));           // height of the badge / capsule
        final int widgetW = style == 5 ? cap : (style == 6 ? Math.round(cap * 2.1f) : Math.round(cap * 2.7f));
        final int leftEdge = m + gl + Cards.dp(c, 8);
        int rightEdge = m + widgetW + Cards.dp(c, 8);
        final boolean widgetOn = pillW - leftEdge - rightEdge >= Cards.dp(c, 44);
        if (!widgetOn) rightEdge = m;
        final int avail = Math.max(Cards.dp(c, 24), pillW - leftEdge - rightEdge);

        // ---- the text block
        final TextView title = Cards.tv(c, e.title, tall ? 12.5f : 13f, Cards.WHITE, true);
        final TextView sub = tall ? Cards.tv(c, e.text, 9.5f, Cards.SUB, false) : null;
        title.setMaxWidth(avail);
        LinearLayout box = Cards.col(c);
        box.setGravity(Gravity.START);
        box.addView(title, Cards.lp(WRAP, WRAP));
        if (sub != null) {
            sub.setMaxWidth(avail);
            LinearLayout.LayoutParams sl = Cards.lp(WRAP, WRAP);
            sl.topMargin = Cards.dp(c, 1);
            box.addView(sub, sl);
            sub.setVisibility(e.text == null || e.text.length() == 0 ? View.GONE : View.VISIBLE);
        }
        final ProgressLine pl = style == 5 ? new ProgressLine(c) : null;
        if (pl != null) {
            LinearLayout.LayoutParams pp = Cards.lp(avail, Math.max(2, Cards.dp(c, 3)));
            pp.topMargin = Cards.dp(c, tall ? 4 : 3);
            box.addView(pl, pp);
        }
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START | side);
        bl.setMargins(leftEdge, 0, rightEdge, 0);
        out.addView(box, bl);

        // ---- the widget at the far end
        final IconView badge = style == 5 && widgetOn ? new IconView(c, e.playing ? IconView.PAUSE : IconView.PLAY, Cards.acc(e)) : null;
        final DotEqView dots = style == 6 && widgetOn ? new DotEqView(c, 5, 3) : null;
        final TextView left = style == 7 && widgetOn ? Cards.tv(c, "", 10.5f, Cards.acc(e), true) : null;
        final View chip;   // whatever is the tonal capsule / badge, so one routine can recolour it
        if (badge != null) {
            int pad = Math.round(cap * 0.27f);
            badge.setPadding(pad, pad, pad, pad);
            out.addView(badge, Cards.fl(cap, cap, Gravity.END | side, m));
            chip = badge;
        } else if (dots != null) {
            LinearLayout cap6 = Cards.row(c);
            cap6.setGravity(Gravity.CENTER);
            int hp = Cards.dp(c, 7);
            cap6.addView(dots, Cards.lp(widgetW - 2 * hp, cap - Cards.dp(c, 8)));
            out.addView(cap6, Cards.fl(widgetW, cap, Gravity.END | side, m));
            chip = cap6;
        } else if (left != null) {
            left.setGravity(Gravity.CENTER);
            left.setFontFeatureSettings("tnum");
            left.setPadding(Cards.dp(c, 8), 0, Cards.dp(c, 8), 0);
            out.addView(left, Cards.fl(WRAP, cap, Gravity.END | side, m));
            chip = left;
        } else {
            chip = null;
        }
        final int capR = cap / 2;
        final String[] lastRem = {null};
        final Runnable tick = new Runnable() {
            @Override public void run() {
                long dur = e.durMs, pos = Cards.position(e);
                int a = Cards.acc(e);
                if (pl != null) pl.set(dur > 0 ? pos / (float) dur : 0f, a);
                if (left != null) {
                    String t = dur > 0 ? "-" + Parse.clock(Math.max(0L, dur - pos)) : "LIVE";
                    if (!t.equals(lastRem[0])) { lastRem[0] = t; left.setText(t); }
                    left.setTextColor(a);
                }
                e.tickHint = e.playing ? (pl != null ? 800 : 1000) : 4000;   // paused: a slow re-anchor is plenty
            }
        };
        final Runnable skin = new Runnable() {
            @Override public void run() {
                int a = Cards.acc(e);
                if (chip != null) chip.setBackground(Cards.bg(Col.alpha(a, badge != null ? 0.22f : 0.18f), capR));
                if (badge != null) badge.set(e.playing ? IconView.PAUSE : IconView.PLAY, a);
                if (dots != null) { dots.setColor(a); dots.setPlaying(e.playing); }
            }
        };
        skin.run();
        tick.run();
        e.tC = tick;
        e.uC = new IslandEvent.Updater() {
            @Override public boolean update() {
                art.run();
                title.setText(e.title);
                if (sub != null) {
                    sub.setText(e.text);
                    sub.setVisibility(e.text == null || e.text.length() == 0 ? View.GONE : View.VISIBLE);
                }
                skin.run();
                tick.run();
                return true;
            }
        };
    }

    // ================================================================== expanded card

    static View expanded(Context c, IslandEvent e, int width) {
        switch (Cards.S.eMusicStyle) {
            case 1: return vinyl(c, e);
            case 2: return poster(c, e, width);
            case 3: return mini(c, e);
            case 4: return lyrics(c, e);
            case 5: return bento(c, e);
            case 6: return waveCard(c, e);
            case 7: return cover(c, e, width);
            default: return Cards.media(c, e);
        }
    }

    /** The views every card shares, so one wiring routine can keep them all up to date. */
    private static final class Parts {
        TextView title, artist, el, rem;
        SeekView seek;
        IconView prev, play, next;
        int playFg;
    }

    /** The seek bar, with the elapsed / remaining times on either side when {@code times}. */
    private static LinearLayout seekRow(Context c, final IslandEvent e, Parts p, boolean times, int topDp) {
        LinearLayout sr = Cards.row(c);
        sr.setPadding(0, Cards.dp(c, topDp), 0, 0);
        p.seek = new SeekView(c);
        if (Cards.S.accentOverride) p.seek.setColor(Cards.S.accent);
        p.seek.setListener(new SeekView.Listener() {
            @Override public void onSeek(float f) { if (e.durMs > 0) Cards.seekTo(e, (long) (f * e.durMs)); }
        });
        if (times) {
            p.el = Cards.tv(c, "0:00", 11.5f, Cards.SUB, false);
            p.rem = Cards.tv(c, "-0:00", 11.5f, Cards.SUB, false);
            p.el.setFontFeatureSettings("tnum");
            p.rem.setFontFeatureSettings("tnum");
            p.rem.setGravity(Gravity.END);
            sr.addView(p.el, Cards.lp(Cards.dp(c, 38), WRAP));
            sr.addView(p.seek, Cards.lpw(0, Cards.dp(c, 22), 1f));
            sr.addView(p.rem, Cards.lp(Cards.dp(c, 44), WRAP));
        } else {
            sr.addView(p.seek, Cards.lpw(0, Cards.dp(c, 22), 1f));
        }
        return sr;
    }

    /** Previous / play-pause / next, scaled by {@code k} (1 = the classic size). With a theme accent the play button is a filled pill. */
    private static LinearLayout controls(Context c, final IslandEvent e, Parts p, float k) {
        final Style S = Cards.S;
        LinearLayout ctl = Cards.row(c);
        ctl.setGravity(Gravity.CENTER);
        final int skipFg = S.accentOverride ? S.btnOn : Cards.WHITE;
        p.playFg = S.accentOverride ? S.accentOn : Cards.WHITE;
        p.prev = new IconView(c, IconView.PREV, skipFg);
        p.next = new IconView(c, IconView.NEXT, skipFg);
        p.play = new IconView(c, e.playing ? IconView.PAUSE : IconView.PLAY, p.playFg);
        if (S.accentOverride) p.play.setBackground(Cards.bg(S.accent, Cards.dp(c, 28 * k)));
        p.prev.setPadding(Cards.dp(c, 12 * k), Cards.dp(c, 10 * k), Cards.dp(c, 12 * k), Cards.dp(c, 10 * k));
        p.next.setPadding(Cards.dp(c, 12 * k), Cards.dp(c, 10 * k), Cards.dp(c, 12 * k), Cards.dp(c, 10 * k));
        if (S.accentOverride) p.play.setPadding(Cards.dp(c, 24 * k), Cards.dp(c, 14 * k), Cards.dp(c, 24 * k), Cards.dp(c, 14 * k));
        else p.play.setPadding(Cards.dp(c, 12 * k), Cards.dp(c, 8 * k), Cards.dp(c, 12 * k), Cards.dp(c, 8 * k));
        p.prev.setContentDescription("Previous track");
        p.next.setContentDescription("Next track");
        p.play.setContentDescription("Play or pause");
        p.prev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Cards.mediaAction(e, 0); } });
        p.next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Cards.mediaAction(e, 2); } });
        p.play.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Cards.mediaAction(e, 1); } });
        Cards.press(p.prev);
        Cards.press(p.next);
        Cards.press(p.play);
        ctl.addView(p.prev, Cards.lp(Cards.dp(c, 64 * k), Cards.dp(c, 52 * k)));
        LinearLayout.LayoutParams pp = Cards.lp(Cards.dp(c, 76 * k), Cards.dp(c, 56 * k));
        pp.setMargins(Cards.dp(c, 10 * k), 0, Cards.dp(c, 10 * k), 0);
        ctl.addView(p.play, pp);
        ctl.addView(p.next, Cards.lp(Cards.dp(c, 64 * k), Cards.dp(c, 52 * k)));
        return ctl;
    }

    /**
     * Hooks the shared views up to the island's two beats: {@code e.tE} (about every 400 ms: position, times, {@code tick}) and
     * {@code e.uE} (the song, its art or its play state changed: title, buttons, seek rights, {@code upd}).
     */
    private static void wire(final Context c, final IslandEvent e, final Parts p, final Runnable tick, final Runnable upd) {
        e.tE = new Runnable() {
            @Override public void run() {
                long dur = e.durMs, pos = Cards.position(e);
                if (dur > 0) {
                    if (!p.seek.isDragging()) p.seek.setFrac(pos / (float) dur);
                    if (p.el != null) {
                        p.el.setText(Parse.clock(pos));
                        p.rem.setText("-" + Parse.clock(dur - pos));
                    }
                } else {
                    p.seek.setFrac(0f);
                }
                if (tick != null) tick.run();
            }
        };
        e.uE = new IslandEvent.Updater() {
            @Override public boolean update() {
                if (p.title != null) p.title.setText(e.title);
                if (p.artist != null) p.artist.setText(e.text);
                p.play.set(e.playing ? IconView.PAUSE : IconView.PLAY, p.playFg);
                if (Cards.S.accentOverride) p.seek.setColor(Cards.S.accent);
                boolean can = e.durMs > 0;
                p.seek.setInteractive(can && (e.mc == null || (e.actions & PlaybackState.ACTION_SEEK_TO) != 0));
                if (p.el != null) {
                    p.el.setVisibility(can ? View.VISIBLE : View.INVISIBLE);
                    p.rem.setVisibility(can ? View.VISIBLE : View.INVISIBLE);
                }
                if (upd != null) upd.run();
                e.tE.run();
                return true;
            }
        };
        e.uE.update();
    }

    private static LinearLayout root(Context c, float padScale) {
        LinearLayout root = Cards.col(c);
        root.setPadding(Cards.px(c), Math.round(Cards.py(c) * padScale), Cards.px(c), Math.round(Cards.py(c) * padScale));
        return root;
    }

    private static EqView eq(Context c, IslandEvent e) {
        EqView eq = new EqView(c);
        eq.setColor(Cards.acc(e));
        eq.setPlaying(e.playing);
        return eq;
    }

    private static String appCaps(IslandEvent e) {
        return e.appName == null ? "" : e.appName.toUpperCase(Locale.getDefault());
    }

    // ---------------------------------------------------------------- Vinyl

    /** A record with a tonearm on the left, the song's text beside it; then the seek bar and the buttons. */
    private static View vinyl(final Context c, final IslandEvent e) {
        LinearLayout root = root(c, 1f);
        final Parts p = new Parts();
        LinearLayout top = Cards.row(c);
        final VinylView vv = new VinylView(c);
        vv.config(true, Cards.acc(e));
        vv.set(artOf(e));
        vv.setPlaying(e.playing);
        int vs = Cards.dp(c, 98);
        top.addView(vv, Cards.lp(vs, vs));

        LinearLayout txt = Cards.col(c);
        txt.setPadding(Cards.dp(c, 12), 0, Cards.dp(c, 8), 0);
        final TextView app = Cards.tv(c, appCaps(e), 10.5f, Cards.acc(e), true);
        app.setLetterSpacing(0.12f);
        app.setPadding(0, 0, 0, Cards.dp(c, 3));
        app.setVisibility(app.length() == 0 ? View.GONE : View.VISIBLE);
        p.title = Cards.tv(c, e.title, 17f, Cards.WHITE, true);
        p.artist = Cards.tv(c, e.text, 14.5f, Cards.SUB, false);
        p.artist.setPadding(0, Cards.dp(c, 3), 0, 0);
        txt.addView(app);
        txt.addView(p.title);
        txt.addView(p.artist);
        top.addView(txt, Cards.lpw(0, WRAP, 1f));
        final EqView eq = eq(c, e);
        top.addView(eq, Cards.lp(Cards.dp(c, 22), Cards.dp(c, 20)));
        root.addView(top);
        root.addView(seekRow(c, e, p, true, 8));
        root.addView(controls(c, e, p, 1f));
        wire(c, e, p, null, new Runnable() {
            @Override public void run() {
                vv.set(artOf(e));
                vv.setAccent(Cards.acc(e));
                vv.setPlaying(e.playing);
                eq.setColor(Cards.acc(e));
                eq.setPlaying(e.playing);
                app.setText(appCaps(e));
                app.setTextColor(Cards.acc(e));
            }
        });
        return root;
    }

    // ---------------------------------------------------------------- Poster

    /** Big centred art in a soft accent frame (with a bars badge in its corner), the text centred below it. */
    private static View poster(final Context c, final IslandEvent e, int width) {
        LinearLayout root = root(c, 1f);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        final Parts p = new Parts();
        final int pad = Cards.dp(c, 6);
        final int as = Math.max(Cards.dp(c, 96), Math.min(Cards.dp(c, 156), width - 2 * Cards.px(c) - Cards.dp(c, 56)));
        final FrameLayout frame = new FrameLayout(c);
        frame.setBackground(Cards.bg(Col.alpha(Cards.acc(e), 0.20f), Cards.dp(c, 28)));
        frame.setPadding(pad, pad, pad, pad);
        final RoundImage art = new RoundImage(c);
        art.set(artOf(e), Cards.dp(c, 22));
        frame.addView(art, new FrameLayout.LayoutParams(as, as));
        LinearLayout chip = Cards.row(c);
        chip.setBackground(Cards.bg(0x99000000, Cards.dp(c, 10)));
        chip.setPadding(Cards.dp(c, 6), Cards.dp(c, 4), Cards.dp(c, 6), Cards.dp(c, 4));
        final EqView eq = eq(c, e);
        chip.addView(eq, Cards.lp(Cards.dp(c, 16), Cards.dp(c, 14)));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END | Gravity.BOTTOM);
        cl.setMargins(0, 0, Cards.dp(c, 8), Cards.dp(c, 8));
        frame.addView(chip, cl);
        root.addView(frame, Cards.lp(WRAP, WRAP));

        p.title = Cards.tv(c, e.title, 19f, Cards.WHITE, true);
        p.artist = Cards.tv(c, e.text, 14.5f, Cards.SUB, false);
        p.title.setGravity(Gravity.CENTER_HORIZONTAL);
        p.artist.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams tl = Cards.lp(MATCH, WRAP);
        tl.topMargin = Cards.dp(c, 14);
        root.addView(p.title, tl);
        p.artist.setPadding(0, Cards.dp(c, 3), 0, 0);
        root.addView(p.artist, Cards.lp(MATCH, WRAP));
        root.addView(seekRow(c, e, p, true, 12), Cards.lp(MATCH, WRAP));
        root.addView(controls(c, e, p, 1f), Cards.lp(MATCH, WRAP));
        wire(c, e, p, null, new Runnable() {
            @Override public void run() {
                art.set(artOf(e), Cards.dp(c, 22));
                frame.setBackground(Cards.bg(Col.alpha(Cards.acc(e), 0.20f), Cards.dp(c, 28)));
                eq.setColor(Cards.acc(e));
                eq.setPlaying(e.playing);
            }
        });
        return root;
    }

    // ---------------------------------------------------------------- Mini

    /** A single slim row (art, text, buttons) with a thin seek bar under it: the shortest music card. */
    private static View mini(final Context c, final IslandEvent e) {
        LinearLayout root = root(c, 0.85f);
        final Parts p = new Parts();
        LinearLayout top = Cards.row(c);
        final RoundImage art = new RoundImage(c);
        art.set(artOf(e), Cards.dp(c, 12));
        top.addView(art, Cards.lp(Cards.dp(c, 50), Cards.dp(c, 50)));
        LinearLayout txt = Cards.col(c);
        txt.setPadding(Cards.dp(c, 12), 0, Cards.dp(c, 4), 0);
        p.title = Cards.tv(c, e.title, 15.5f, Cards.WHITE, true);
        p.artist = Cards.tv(c, e.text, 13f, Cards.SUB, false);
        p.artist.setPadding(0, Cards.dp(c, 2), 0, 0);
        txt.addView(p.title);
        txt.addView(p.artist);
        top.addView(txt, Cards.lpw(0, WRAP, 1f));
        top.addView(controls(c, e, p, 0.7f), Cards.lp(WRAP, WRAP));
        root.addView(top);
        root.addView(seekRow(c, e, p, false, 8));
        wire(c, e, p, null, new Runnable() {
            @Override public void run() { art.set(artOf(e), Cards.dp(c, 12)); }
        });
        return root;
    }

    // ---------------------------------------------------------------- Lyrics

    private static final String NOTE = "\u266A";

    /** What to say in the lyric area while the song has no lines to show. */
    private static String hint(Context c, IslandEvent e) {
        if (!Prefs.b(c, Prefs.K_LYRICS, true)) return "Lyrics are off";
        String t = e.title == null ? "" : e.title.toString().trim();
        String a = e.text == null ? "" : e.text.toString().trim();
        if (t.length() == 0 || !Lyrics.worthLookup(e.pkg, e.durMs) || Lyrics.missedRecently(t, a, System.currentTimeMillis())) return "No lyrics for this song";
        return "Looking for lyrics\u2026";
    }

    /** A small header (art, title, artist, bars), the previous / current / next line of the song, then the seek bar and buttons. */
    private static View lyrics(final Context c, final IslandEvent e) {
        LinearLayout root = root(c, 1f);
        final Parts p = new Parts();
        LinearLayout top = Cards.row(c);
        final RoundImage art = new RoundImage(c);
        art.set(artOf(e), Cards.dp(c, 12));
        top.addView(art, Cards.lp(Cards.dp(c, 46), Cards.dp(c, 46)));
        LinearLayout txt = Cards.col(c);
        txt.setPadding(Cards.dp(c, 12), 0, Cards.dp(c, 8), 0);
        p.title = Cards.tv(c, e.title, 16f, Cards.WHITE, true);
        p.artist = Cards.tv(c, e.text, 13.5f, Cards.SUB, false);
        p.artist.setPadding(0, Cards.dp(c, 2), 0, 0);
        txt.addView(p.title);
        txt.addView(p.artist);
        top.addView(txt, Cards.lpw(0, WRAP, 1f));
        final EqView eq = eq(c, e);
        top.addView(eq, Cards.lp(Cards.dp(c, 22), Cards.dp(c, 20)));
        root.addView(top);

        // The lyric area has a FIXED height: the card is measured once, so the lines may never change how tall it is.
        final TextView prev = Cards.tv(c, "", 13f, Col.alpha(Cards.WHITE, 0.5f), false);
        final TextView cur = Cards.tv(c, "", 19f, Cards.WHITE, true);
        final TextView next = Cards.tv(c, "", 13f, Col.alpha(Cards.WHITE, 0.5f), false);
        prev.setGravity(Gravity.CENTER_HORIZONTAL);
        cur.setGravity(Gravity.CENTER_HORIZONTAL);
        next.setGravity(Gravity.CENTER_HORIZONTAL);
        cur.setMaxLines(2);
        LinearLayout lb = Cards.col(c);
        lb.setGravity(Gravity.CENTER);
        lb.setPadding(Cards.dp(c, 6), 0, Cards.dp(c, 6), 0);
        lb.addView(prev, Cards.lp(MATCH, WRAP));
        LinearLayout.LayoutParams cl = Cards.lp(MATCH, WRAP);
        cl.topMargin = Cards.dp(c, 5);
        lb.addView(cur, cl);
        LinearLayout.LayoutParams nl = Cards.lp(MATCH, WRAP);
        nl.topMargin = Cards.dp(c, 5);
        lb.addView(next, nl);
        LinearLayout.LayoutParams ll = Cards.lp(MATCH, Math.round(Cards.dp(c, 104) * Math.max(1f, Cards.TS)));
        ll.topMargin = Cards.dp(c, 10);
        root.addView(lb, ll);

        root.addView(seekRow(c, e, p, true, 6));
        root.addView(controls(c, e, p, 0.85f));

        final String[] lkey = {""};
        final Lyrics.Loaded[] lnow = {null};
        final String[] sig = {null};
        final Runnable paint = new Runnable() {
            @Override public void run() {
                Lyrics.Loaded l = lnow[0];
                String pv, cu, nx, s;
                if (l == null) {
                    pv = "";
                    cu = NOTE;
                    nx = hint(c, e);
                    s = "h|" + nx;
                } else {
                    long pos = Cards.position(e) + Prefs.i(c, Prefs.K_LYROFF, 200);   // same nudge as the pill
                    int i = l.indexAt(pos, e.durMs);
                    pv = l.lineOr(i - 1);
                    cu = l.lineOr(i);
                    nx = l.lineOr(i + 1);
                    if (cu.trim().length() == 0) cu = NOTE;   // before the first line, or a gap between two
                    s = "l|" + System.identityHashCode(l) + "|" + i;
                }
                if (s.equals(sig[0])) return;   // same lines as a moment ago: no text work, no fade
                sig[0] = s;
                prev.setText(pv);
                next.setText(nx);
                cur.setText(cu);
                cur.setTextColor(NOTE.equals(cu) ? Cards.acc(e) : Cards.WHITE);
                cur.animate().cancel();
                cur.setAlpha(0f);
                cur.animate().alpha(1f).setDuration(180).start();
            }
        };
        wire(c, e, p, paint, new Runnable() {
            @Override public void run() {
                art.set(artOf(e), Cards.dp(c, 12));
                eq.setColor(Cards.acc(e));
                eq.setPlaying(e.playing);
                Cards.lyricsLoad(c, e, lkey, lnow, paint);
                paint.run();
            }
        });
        return root;
    }
    // ---------------------------------------------------------------- Bento

    /** Previous / play-pause / next as three roomy flat keys: two tonal tiles and an accent-filled play key in the middle. */
    private static LinearLayout bentoKeys(Context c, final IslandEvent e, Parts p) {
        final Style S = Cards.S;
        LinearLayout ctl = Cards.row(c);
        ctl.setGravity(Gravity.CENTER);
        ctl.setPadding(0, Cards.dp(c, 12), 0, 0);
        final int skipFg = S.accentOverride ? S.btnOn : Cards.WHITE;
        final int tileCol = S.accentOverride ? S.btn : Cards.TILE;
        final int a = Cards.acc(e);
        p.playFg = Col.onColor(a);
        p.prev = new IconView(c, IconView.PREV, skipFg);
        p.next = new IconView(c, IconView.NEXT, skipFg);
        p.play = new IconView(c, e.playing ? IconView.PAUSE : IconView.PLAY, p.playFg);
        p.prev.setBackground(Cards.bg(tileCol, Cards.dp(c, 22)));
        p.next.setBackground(Cards.bg(tileCol, Cards.dp(c, 22)));
        p.play.setBackground(Cards.bg(a, Cards.dp(c, 28)));
        int pd = Cards.dp(c, 14);
        p.prev.setPadding(pd, pd, pd, pd);
        p.next.setPadding(pd, pd, pd, pd);
        p.play.setPadding(pd, pd, pd, pd);
        p.prev.setContentDescription("Previous track");
        p.next.setContentDescription("Next track");
        p.play.setContentDescription("Play or pause");
        p.prev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Cards.mediaAction(e, 0); } });
        p.next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Cards.mediaAction(e, 2); } });
        p.play.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Cards.mediaAction(e, 1); } });
        Cards.press(p.prev);
        Cards.press(p.next);
        Cards.press(p.play);
        int gap = Cards.dp(c, 8);
        LinearLayout.LayoutParams l1 = Cards.lpw(0, Cards.dp(c, 52), 1f), l2 = Cards.lpw(0, Cards.dp(c, 56), 1.5f), l3 = Cards.lpw(0, Cards.dp(c, 52), 1f);
        l2.setMargins(gap, 0, gap, 0);
        ctl.addView(p.prev, l1);
        ctl.addView(p.play, l2);
        ctl.addView(p.next, l3);
        return ctl;
    }

    /** Big rounded art beside one tonal tile with the app, title and artist; then the seek bar and three flat keys. */
    private static View bento(final Context c, final IslandEvent e) {
        LinearLayout root = root(c, 1f);
        final Parts p = new Parts();
        LinearLayout top = Cards.row(c);
        final int as = Cards.dp(c, 104);
        final RoundImage art = new RoundImage(c);
        art.set(artOf(e), Cards.dp(c, 24));
        top.addView(art, Cards.lp(as, as));

        LinearLayout tile = Cards.col(c);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setBackground(Cards.bg(Cards.TILE, Cards.dp(c, 24)));
        tile.setPadding(Cards.dp(c, 14), Cards.dp(c, 8), Cards.dp(c, 12), Cards.dp(c, 8));
        LinearLayout head = Cards.row(c);
        final TextView app = Cards.tv(c, appCaps(e), 10.5f, Cards.acc(e), true);
        app.setLetterSpacing(0.12f);
        final EqView eq = eq(c, e);
        head.addView(app, Cards.lpw(0, WRAP, 1f));
        head.addView(eq, Cards.lp(Cards.dp(c, 18), Cards.dp(c, 14)));
        p.title = Cards.tv(c, e.title, 17f, Cards.WHITE, true);
        p.artist = Cards.tv(c, e.text, 14f, Cards.SUB, false);
        tile.addView(head, Cards.lp(MATCH, WRAP));
        LinearLayout.LayoutParams tl = Cards.lp(MATCH, WRAP);
        tl.topMargin = Cards.dp(c, 6);
        tile.addView(p.title, tl);
        LinearLayout.LayoutParams al = Cards.lp(MATCH, WRAP);
        al.topMargin = Cards.dp(c, 2);
        tile.addView(p.artist, al);
        LinearLayout.LayoutParams tp = Cards.lpw(0, Math.round(as * Math.max(1f, Cards.TS)), 1f);
        tp.leftMargin = Cards.dp(c, 8);
        top.addView(tile, tp);
        root.addView(top);
        root.addView(seekRow(c, e, p, true, 10));
        root.addView(bentoKeys(c, e, p));
        wire(c, e, p, null, new Runnable() {
            @Override public void run() {
                int a = Cards.acc(e);
                art.set(artOf(e), Cards.dp(c, 24));
                eq.setColor(a);
                eq.setPlaying(e.playing);
                app.setText(appCaps(e));
                app.setTextColor(a);
                p.playFg = Col.onColor(a);
                p.play.setBackground(Cards.bg(a, Cards.dp(c, 28)));
                p.play.set(e.playing ? IconView.PAUSE : IconView.PLAY, p.playFg);
            }
        });
        return root;
    }

    // ---------------------------------------------------------------- Wave

    /** A small header (art, title, artist), a wide rolling waveform across the card, then the seek bar and the buttons. */
    private static View waveCard(final Context c, final IslandEvent e) {
        LinearLayout root = root(c, 1f);
        final Parts p = new Parts();
        LinearLayout top = Cards.row(c);
        final RoundImage art = new RoundImage(c);
        art.set(artOf(e), Cards.dp(c, 14));
        top.addView(art, Cards.lp(Cards.dp(c, 52), Cards.dp(c, 52)));
        LinearLayout txt = Cards.col(c);
        txt.setPadding(Cards.dp(c, 12), 0, Cards.dp(c, 4), 0);
        p.title = Cards.tv(c, e.title, 16.5f, Cards.WHITE, true);
        p.artist = Cards.tv(c, e.text, 13.5f, Cards.SUB, false);
        p.artist.setPadding(0, Cards.dp(c, 2), 0, 0);
        txt.addView(p.title);
        txt.addView(p.artist);
        top.addView(txt, Cards.lpw(0, WRAP, 1f));
        root.addView(top);

        final WaveView wv = new WaveView(c);
        wv.setColor(Cards.acc(e));
        wv.setPlaying(e.playing);
        LinearLayout.LayoutParams wl = Cards.lp(MATCH, Cards.dp(c, 48));
        wl.topMargin = Cards.dp(c, 12);
        root.addView(wv, wl);
        root.addView(seekRow(c, e, p, true, 6));
        root.addView(controls(c, e, p, 0.9f));
        wire(c, e, p, null, new Runnable() {
            @Override public void run() {
                art.set(artOf(e), Cards.dp(c, 14));
                wv.setColor(Cards.acc(e));
                wv.setPlaying(e.playing);
            }
        });
        return root;
    }

    // ---------------------------------------------------------------- Cover

    /** Wide cover art with the title on a flat chip in its corner (and an equalizer badge); then the seek bar and the buttons. */
    private static View cover(final Context c, final IslandEvent e, int width) {
        LinearLayout root = root(c, 1f);
        final Parts p = new Parts();
        final int chipBg = Col.alpha(Cards.S.bg, 0.84f);   // the island's own colour, so the text on it always reads
        FrameLayout frame = new FrameLayout(c);
        final RoundImage art = new RoundImage(c);
        art.set(artOf(e), Cards.dp(c, 26));
        frame.addView(art, new FrameLayout.LayoutParams(MATCH, Cards.dp(c, 150)));

        LinearLayout chip = Cards.col(c);
        chip.setBackground(Cards.bg(chipBg, Cards.dp(c, 18)));
        chip.setPadding(Cards.dp(c, 12), Cards.dp(c, 8), Cards.dp(c, 14), Cards.dp(c, 9));
        p.title = Cards.tv(c, e.title, 16f, Cards.WHITE, true);
        p.artist = Cards.tv(c, e.text, 12.5f, Cards.SUB, false);
        int maxW = Math.max(Cards.dp(c, 80), width - 2 * Cards.px(c) - Cards.dp(c, 46));
        p.title.setMaxWidth(maxW);
        p.artist.setMaxWidth(maxW);
        chip.addView(p.title);
        LinearLayout.LayoutParams al = Cards.lp(WRAP, WRAP);
        al.topMargin = Cards.dp(c, 1);
        chip.addView(p.artist, al);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START | Gravity.BOTTOM);
        cl.setMargins(Cards.dp(c, 10), 0, 0, Cards.dp(c, 10));
        frame.addView(chip, cl);

        LinearLayout badge = Cards.row(c);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Cards.bg(chipBg, Cards.dp(c, 14)));
        badge.setPadding(Cards.dp(c, 9), Cards.dp(c, 7), Cards.dp(c, 9), Cards.dp(c, 7));
        final EqView eq = eq(c, e);
        badge.addView(eq, Cards.lp(Cards.dp(c, 18), Cards.dp(c, 14)));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END);
        bl.setMargins(0, Cards.dp(c, 10), Cards.dp(c, 10), 0);
        frame.addView(badge, bl);
        root.addView(frame, Cards.lp(MATCH, WRAP));

        root.addView(seekRow(c, e, p, true, 10));
        root.addView(controls(c, e, p, 0.9f));
        wire(c, e, p, null, new Runnable() {
            @Override public void run() {
                art.set(artOf(e), Cards.dp(c, 26));
                eq.setColor(Cards.acc(e));
                eq.setPlaying(e.playing);
            }
        });
        return root;
    }
}
