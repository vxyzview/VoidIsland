package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the Lyrics logic (pure Java: runs on a plain JVM, no Android). */
final class LyricsCheck {
    private LyricsCheck() {}

    static void run() {
        // --- Lyrics: LRC/plain parsing and line lookup (the music card's lyrics row)
        Lyrics.Loaded s0 = Lyrics.parse("[ti:Demo]\n[offset:0]\n[00:01.00]First\n[00:10.50]Second\n[00:30]Third\n", null);
        ok(s0 != null && s0.synced, "lyrics: LRC body parsed as synced tags skipped");
        ok(s0.textAt(900, 0) == null, "lyrics: nothing before the first stamp");
        ok("First".equals(s0.textAt(1500, 0)), "lyrics: first line at 1.5 s");
        ok("Second".equals(s0.textAt(10500, 0)), "lyrics: second line at 10.5 s");
        ok("Second".equals(s0.textAt(29000, 0)), "lyrics: line holds until the next stamp");
        ok("Third".equals(s0.textAt(31000, 0)), "lyrics: [mm:ss] stamp without fraction");
        Lyrics.Loaded d0 = Lyrics.parse("[00:30.00]Late first\n[00:05.00][00:12.00]Doubled\n", null);
        ok("Doubled".equals(d0.textAt(6000, 0)), "lyrics: double stamp at 6 s");
        ok("Doubled".equals(d0.textAt(13000, 0)), "lyrics: second stamp same line at 13 s");
        ok("Late first".equals(d0.textAt(31000, 0)), "lyrics: out-of-order rows sorted");
        ok(Lyrics.parse("[by:x]\n[re:y]\n", null) == null, "lyrics: tag-only body rejected");
        Lyrics.Loaded p0 = Lyrics.parse(null, "Alpha\n\nBeta\n");
        ok(p0 != null && !p0.synced && p0.line.length == 2, "lyrics: plain text, blank lines dropped");
        ok("Alpha".equals(p0.textAt(0, 100000)), "lyrics: plain line at 0%");
        ok("Beta".equals(p0.textAt(60000, 100000)), "lyrics: plain line follows song progress");
        ok(p0.textAt(5000, 0) == null, "lyrics: plain text needs the song duration");
        Lyrics.remember("Who", "Does", p0);
        ok(Lyrics.cached("who", "does") == p0, "lyrics: cache lookup ignores case");

        // --- memory pressure: trim keeps only the most recently used songs, then nothing
        ok(Lyrics.cleanTitle("Song (feat. Guest) - Remastered 2011").equals("Song"), "lyrics: title loses feat. / remaster suffixes");
        ok(Lyrics.cleanTitle("(Only)").equals("(Only)") && Lyrics.cleanTitle("Plain").equals("Plain"), "lyrics: a title is never cleaned down to nothing");
        ok(Lyrics.cleanArtist("Main, Other").equals("Main") && Lyrics.cleanArtist("Main & Other").equals("Main") && Lyrics.cleanArtist("Band - Topic").equals("Band"), "lyrics: first artist is used for the loose lookup");
        ok(Lyrics.cleanArtist("Solo").equals("Solo") && Lyrics.cleanArtist("").equals(""), "lyrics: a single artist is kept as is");
        Lyrics.clearAll();
        for (int i = 0; i < 6; i++) Lyrics.remember("song" + i, "a", Lyrics.parse(null, "x" + i));
        Lyrics.cached("song0", "a");   // touched last: now the most recently used
        Lyrics.rememberMiss("nothing", "here", 1000L);
        Lyrics.trim(2);
        ok(Lyrics.cacheSize() == 2 && Lyrics.cached("song0", "a") != null && Lyrics.cached("song5", "a") != null && Lyrics.cached("song1", "a") == null,
            "lyrics: trim keeps the most recently used songs");
        ok(!Lyrics.missedRecently("nothing", "here", 1001L), "lyrics: trim forgets the misses");
        Lyrics.trim(0);
        ok(Lyrics.cacheSize() == 0, "lyrics: trim(0) empties the cache");
        Lyrics.clearAll();
        ok(!Trim.shortage(0) && !Trim.shortage(5) && Trim.shortage(10) && Trim.shortage(15), "trim: running-low and critical count as a shortage");
        ok(!Trim.shortage(20), "trim: closing the settings screen (UI hidden) is not a shortage");
        ok(Trim.shortage(40) && Trim.shortage(60) && Trim.shortage(80), "trim: background levels count as a shortage");
        ok(Trim.keepSongs(10) == 4 && Trim.keepSongs(60) == 4 && Trim.keepSongs(80) == 0, "trim: only the last level drops every cached song");

        // --- Pill lyric scroll: the pill shows the whole song, so lookups must return an index, not just text
        ok(s0.indexAt(900, 0) == -1, "scroll: nothing before the first stamp");
        ok(s0.indexAt(1500, 0) == 0 && s0.indexAt(65000, 0) == 2, "scroll: synced index, past the last stamp stays on the last line");
        ok(p0.indexAt(0, 100000) == 0 && p0.indexAt(60000, 100000) == 1, "scroll: plain index follows song progress");
        ok(p0.indexAt(5000, 0) == -1, "scroll: plain text needs the song duration");
        float[] tops = {0f, 30f, 70f};
        ok(Lyrics.snapOffset(0, tops, 30f) == 0f, "scroll: first row sits at the top");
        ok(Lyrics.snapOffset(1, tops, 30f) == 35f, "scroll: active row centres in the window");
        ok(Lyrics.snapOffset(1, tops, 60f) == 10f, "scroll: window taller than the song clamps to the last edge");
        ok(Lyrics.snapOffset(0, new float[]{0f, 20f}, 30f) == 0f, "scroll: song shorter than the pill does not scroll");

        ok(s0.nextStartMs(1500, 0) == 10500 && s0.nextStartMs(0, 0) == 1000, "lyrics: next line start (synced), also before the first line");
        ok(s0.nextStartMs(65000, 0) == -1, "lyrics: no next line after the last one");
        ok(p0.nextStartMs(0, 100000) > 0 && p0.nextStartMs(60000, 100000) == -1, "lyrics: next line start (plain)");

        // --- Optimizations: formatter-free clock, exact wake-ups, lyric cache
        ok("0:00".equals(Parse.clock(0)) && "0:09".equals(Parse.clock(9999)) && "10:05".equals(Parse.clock(605000)) && "1:00:00".equals(Parse.clock(3600000)) && "12:34:56".equals(Parse.clock(45296000)) && "1:05:07".equals(Parse.clock(3907000)), "opt: clock text without String.format");
        ok(Parse.nextSecondMs(1000L, false, 5250L) == 750L && Parse.nextSecondMs(1000L, false, 5000L) == 1000L, "opt: stopwatch wakes at the next whole second");
        ok(Parse.nextSecondMs(10000L, true, 5250L) == 751L && Parse.nextSecondMs(10000L, true, 9000L) == 1L, "opt: countdown wakes when its second drops");
        ok(Parse.nextSecondMs(10000L, true, 12000L) == 1000L, "opt: expired countdown does not spin");
        ok(Parse.nextMinuteMs(0L) == 60020L && Parse.nextMinuteMs(59999L) == 21L, "opt: idle clock sleeps until the minute turns");
        for (int i = 0; i < 2000; i++) { long w = i * 37L, nw = w + i * 13L; long d = Parse.nextSecondMs(w, false, nw); if (d < 1 || d > 1000) { ok(false, "opt: wake delay always 1..1000"); break; } }
        ok(Parse.clock(Parse.chronoMs(0L, false, 61000L + 400L)).equals(Parse.clock(Parse.chronoMs(0L, false, 61000L + Parse.nextSecondMs(0L, false, 61400L) - 1L))) && !Parse.clock(Parse.chronoMs(0L, false, 61400L)).equals(Parse.clock(Parse.chronoMs(0L, false, 61400L + Parse.nextSecondMs(0L, false, 61400L)))), "opt: text really changes at the wake-up, not before");
        Lyrics.clearAll();
        Lyrics.Loaded lo = Lyrics.parse("[00:01.00]a\n[00:02.00]b", null);
        for (int i = 0; i < Lyrics.CACHE_MAX + 10; i++) { Lyrics.remember("song" + i, "x", lo); if (i == 5) Lyrics.cached("song0", "x"); Lyrics.cached("song0", "x"); }
        ok(Lyrics.cacheSize() == Lyrics.CACHE_MAX, "opt: lyric cache is capped, not wiped");
        ok(Lyrics.cached("song0", "x") != null && Lyrics.cached("song1", "x") == null, "opt: lyric cache keeps the song you replay, drops the oldest");
        ok(!Lyrics.missedRecently("none", "y", 1000L), "opt: unknown song is not a known miss");
        Lyrics.rememberMiss("none", "y", 1000L);
        ok(Lyrics.missedRecently("none", "y", 1000L + Lyrics.MISS_TTL_MS - 1) && !Lyrics.missedRecently("none", "y", 1000L + Lyrics.MISS_TTL_MS), "opt: a miss is remembered for 2 minutes, then retried");
        Lyrics.remember("none", "y", lo);
        ok(!Lyrics.missedRecently("none", "y", 1001L), "opt: finding lyrics clears the miss");

        // --- Lyrics: which players are worth a lookup, and how long a miss is believed
        ok(!Lyrics.worthLookup("com.google.android.youtube", 200000L) && !Lyrics.worthLookup("com.netflix.mediaclient", 0L), "lyrics: video apps are never looked up");
        ok(!Lyrics.worthLookup("au.com.shiftyjelly.pocketcasts", 0L) && !Lyrics.worthLookup("fm.podcast.player", 0L), "lyrics: podcast apps are never looked up");
        ok(!Lyrics.worthLookup("com.spotify.music", Lyrics.LONGEST_SONG_MS + 1), "lyrics: something longer than 15 minutes is not a song");
        ok(Lyrics.worthLookup("com.spotify.music", 215000L) && Lyrics.worthLookup("com.google.android.apps.youtube.music", 0L) && Lyrics.worthLookup(null, 0L), "lyrics: music players and unknown length are looked up");
        Lyrics.clearAll();
        Lyrics.rememberMiss("inst", "z", 5000L, Lyrics.NONE_TTL_MS);
        ok(Lyrics.missedRecently("inst", "z", 5000L + Lyrics.MISS_TTL_MS + 1) && !Lyrics.missedRecently("inst", "z", 5000L + Lyrics.NONE_TTL_MS), "lyrics: \"no lyrics\" is believed for an hour, a failed lookup only 2 minutes");

        // --- Music styles: the lyrics card reads the lines around the current one, and every style has a name and a description
        Lyrics.Loaded lc = new Lyrics.Loaded(new long[]{1000L, 2000L, 3000L}, new String[]{"a", "b", null}, true);
        ok("".equals(lc.lineOr(-1)) && "a".equals(lc.lineOr(0)) && "".equals(lc.lineOr(2)) && "".equals(lc.lineOr(3)), "lyrics card: lines outside the song, and null ones, read as empty");
        ok(lc.indexAt(500L, 0L) == -1 && "".equals(lc.lineOr(lc.indexAt(500L, 0L))) && "a".equals(lc.lineOr(lc.indexAt(500L, 0L) + 1)), "lyrics card: before the first line the next line is the first one");
        ok("b".equals(lc.lineOr(lc.indexAt(2500L, 0L))) && "a".equals(lc.lineOr(lc.indexAt(2500L, 0L) - 1)), "lyrics card: previous and current line follow the song position");
        ok(MusicStyle.CNAMES.length == MusicStyle.COMPACT && MusicStyle.CDESC.length == MusicStyle.COMPACT, "music: every compact style has a name and a description");
        ok(MusicStyle.ENAMES.length == MusicStyle.EXPANDED && MusicStyle.EDESC.length == MusicStyle.EXPANDED, "music: every expanded style has a name and a description");
    }
}
