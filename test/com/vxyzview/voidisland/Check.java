package com.vxyzview.voidisland;

public class Check {
    static int fails = 0;
    static void ok(boolean c, String m) { if (!c) { fails++; System.out.println("FAIL: " + m); } else System.out.println("ok:   " + m); }

    public static void main(String[] a) {
        // --- Spring: expanding width 150 -> 340 should overshoot slightly, settle < 0.9s, never explode
        Spring s = new Spring(); s.snap(150f); s.eps = 0.3f; s.tune(330f, 0.60f); s.to(340f);
        float peak = 0, t = 0; int frames = 0;
        while (s.step(1f / 60f) && frames < 600) { frames++; t += 1f / 60f; peak = Math.max(peak, s.value); }
        System.out.printf("expand: settled in %.2fs (%d frames), peak %.1f%n", t, frames, peak);
        ok(peak > 340f && peak < 340f + 0.14f * 190f, "expand overshoots a little (bounce) but < 14%");
        ok(t < 0.95f, "expand settles under 0.95s");
        ok(s.value == 340f, "expand lands exactly on target");

        // collapse should be snappier, small bounce
        Spring c = new Spring(); c.snap(340f); c.tune(420f, 0.80f); c.to(150f);
        float low = 1e9f; t = 0; frames = 0;
        while (c.step(1f / 60f) && frames < 600) { frames++; t += 1f / 60f; low = Math.min(low, c.value); }
        System.out.printf("collapse: settled in %.2fs, trough %.1f%n", t, low);
        ok(t < 0.8f && low > 150f - 0.05f * 190f, "collapse settles < 0.8s, bounce < 5%");

        // stability with a janky 50ms frame
        Spring j = new Spring(); j.snap(0f); j.tune(430f, 0.8f); j.to(100f);
        int n = 0; while (j.step(0.05f) && n < 200) n++;
        ok(j.value == 100f && n < 60, "stable with 50ms frame hitches");

        // --- system animation scale: springs follow it, and "Remove animations" (scale 0) means no motion
        float keepRate = Spring.rate;
        Spring.rate = 2f;   // animation scale 0.5x: twice as fast
        Spring fast = new Spring(); fast.snap(150f); fast.tune(330f, 0.60f); fast.to(340f);
        float tf = 0; int ff = 0;
        while (fast.step(1f / 60f) && ff < 600) { ff++; tf += 1f / 60f; }
        ok(tf < t && fast.value == 340f, "animation scale 0.5x settles sooner and still lands exactly");
        Spring.rate = 0.5f;   // animation scale 2x: slower but stable
        Spring slow = new Spring(); slow.snap(150f); slow.tune(330f, 0.60f); slow.to(340f);
        int fs = 0;
        while (slow.step(1f / 60f) && fs < 2000) fs++;
        ok(slow.value == 340f && fs > frames, "animation scale 2x is slower but lands exactly");
        Spring.rate = Float.POSITIVE_INFINITY;   // animation scale 0
        Spring none = new Spring(); none.snap(150f); none.tune(330f, 0.60f); none.to(340f);
        boolean still = none.step(1f / 60f);
        ok(!still && none.value == 340f && none.velocity == 0f, "animation scale 0 jumps straight to the target");
        Spring.rate = keepRate;

        // --- Absorb opt-in: the original is only removed when the island really shows the banner
        ok(Absorb.shouldRemove(true, false, true, IslandEvent.NOTIF, false, false), "absorb: opted in + island showing removes the original");
        ok(!Absorb.shouldRemove(false, false, true, IslandEvent.NOTIF, false, false), "absorb: off by default means nothing is removed");
        ok(!Absorb.shouldRemove(true, false, false, IslandEvent.NOTIF, false, false), "absorb: island not showing (off, hidden, ignoring the app) keeps the original");
        ok(!Absorb.shouldRemove(true, true, true, IslandEvent.NOTIF, false, false), "absorb: keep-in-shade apps are never touched");
        ok(!Absorb.shouldRemove(true, false, true, IslandEvent.CALL, true, false), "absorb: live things still keep their notification");
        ok(!Prefs.ABSORB_DEF, "absorb: starts off");

        // --- KeepAlive: which phones get the background-running help
        ok(KeepAlive.aggressive("Xiaomi") && KeepAlive.aggressive("POCO") && KeepAlive.aggressive("realme") && KeepAlive.aggressive("OnePlus"), "keepalive: Xiaomi family and Oppo family detected");
        ok(KeepAlive.aggressive("vivo") && KeepAlive.aggressive("HUAWEI") && KeepAlive.aggressive("HONOR") && KeepAlive.aggressive("samsung"), "keepalive: Vivo, Huawei, Honor and Samsung detected");
        ok(!KeepAlive.aggressive("Google") && !KeepAlive.aggressive("motorola") && !KeepAlive.aggressive(null) && !KeepAlive.aggressive(""), "keepalive: stock-like ROMs get no extra prompt");
        ok("Xiaomi".equals(KeepAlive.family("Redmi")) && KeepAlive.hint("Google").length() == 0 && KeepAlive.hint("Xiaomi").length() > 0, "keepalive: brand name and hint text");

        // --- Parse
        ok("Rp 25.000".equals(Parse.amount("Pembayaran Rp 25.000 berhasil.")), "amount: Rp 25.000");
        ok("$12.50".equals(Parse.amount("You paid $12.50 at Starbucks")), "amount: $12.50");
        ok(Parse.amount("form 12 submitted") == null, "amount: 'form 12' is not money");
        ok("Rp125.000,00".equals(Parse.amount("Transfer Rp125.000,00 ke Budi.")), "amount: Rp125.000,00");
        ok(Parse.isPayment("id.dana", "DANA", "Pembayaran berhasil Rp 18.500"), "payment: DANA");
        ok(Parse.isPayment("com.google.android.apps.walletnfcrel", "Google Wallet", "Paid $4.20 at Blue Bottle"), "payment: Google Wallet");
        ok(!Parse.isPayment("com.whatsapp", "Budi", "see you at 5, bring 20 bucks"), "payment: chat is not payment");
        ok(!Parse.isPayment("com.news.app", "Markets", "Dollar steady at $1.08 in late trade"), "payment: news with $ is not payment");
        ok(Parse.isFailure("Payment failed"), "failure word");
        ok(Parse.isIncoming("Anda menerima Rp 50.000 dari Sari"), "incoming word");
        ok(Parse.actionKind("Decline") == -1 && Parse.actionKind("Answer") == 1 && Parse.actionKind("Tolak") == -1 && Parse.actionKind("Jawab") == 1, "call button kinds");
        ok(Parse.actionKind("Send message") == 0 && Parse.actionKind("End call") == -1 && Parse.actionKind("Hang up") == -1 && Parse.actionKind("Speaker") == 0, "call button kinds: 'Send' is not 'End'");
        ok("1:05".equals(Parse.clock(65000)) && "1:01:01".equals(Parse.clock(3661000)) && "0:00".equals(Parse.clock(-5)), "clock format");
        ok("B".equals(Parse.initial("  budi")), "initial");

        // --- Col
        ok(Col.alpha(0xFF336699, 0.5f) == 0x80336699, "Col.alpha");
        ok(Col.mix(0xFF000000, 0xFFFFFFFF, 0.5f) == 0xFF808080, "Col.mix midpoint");
        ok(Col.onColor(0xFF000000) == 0xFFFFFFFF && Col.onColor(0xFFF2F2F7) == 0xFF111114, "Col.onColor contrast");
        ok(Col.onColor(0xFF0B1B3A) == 0xFFFFFFFF, "Col.onColor navy -> white text");

        // --- Geo: pill / box / notch
        float[] g = new float[5];
        ok(Geo.radius(Geo.PILL, Geo.COMPACT, 32f, 16f, 38f, 32f) == 16f, "pill compact = fully round");
        ok(Geo.radius(Geo.PILL, Geo.EXPANDED, 120f, 16f, 38f, 32f) == 38f, "pill expanded uses card radius");
        ok(Geo.radius(Geo.PILL, Geo.EXPANDED, 50f, 16f, 38f, 32f) == 25f, "pill expanded never exceeds half height");
        ok(Geo.radius(Geo.BOX, Geo.COMPACT, 32f, 10f, 22f, 32f) == 10f, "box compact uses own radius");
        ok(Geo.radius(Geo.BOX, Geo.COMPACT, 12f, 30f, 22f, 32f) == 6f, "box radius clamps to half height");
        ok(Geo.radius(Geo.NOTCH, Geo.COMPACT, 28f, 14f, 28f, 32f) == 14f, "notch compact bottom radius");
        ok(Geo.radius(Geo.PILL, Geo.HIDDEN, 32f, 16f, 38f, 30f) == 15f, "hidden = dot");
        Geo.surface(Geo.PILL, 400f, 150f, 0f, 32f, 16f, 0f, g);
        ok(g[0] == 125f && g[2] == 275f && g[1] == 0f && g[3] == 32f && g[4] == 16f, "pill rect centred in window");
        Geo.surface(Geo.NOTCH, 400f, 130f, 0f, 28f, 14f, 14f, g);
        ok(g[1] == -14f && g[3] == 28f && g[4] == 14f, "notch pushed up so top corners are off-screen");
        ok(g[4] * 2f <= g[3] - g[1], "notch radius fits its height");
        Geo.surface(Geo.NOTCH, 400f, 130f, 0f, 28f, 14f, 7f, g);   // pill->notch morph: glue springs, the top follows it
        ok(g[1] == -7f && g[3] == 28f && g[4] == 14f, "notch glue springs smoothly");
        Geo.surface(Geo.BOX, 400f, 20f, 0f, 100f, 38f, 0f, g);
        ok(g[4] == 10f, "box radius clamps to half the width");

        // --- Absorb: one-shot notifications are the island's to show; their original rests, then returns
        ok(Absorb.applies(IslandEvent.NOTIF, false, false) && Absorb.applies(IslandEvent.PAYMENT, false, false), "absorb: text banners are absorbed");
        ok(!Absorb.applies(IslandEvent.CALL, true, false) && !Absorb.applies(IslandEvent.TIMER, true, false)
            && !Absorb.applies(IslandEvent.PROGRESS, true, false), "absorb: live things keep their original notification");
        ok(!Absorb.applies(IslandEvent.NOTIF, false, true), "absorb: never hides notifications that were already up at connect");
        ok(Absorb.delayMs(5200) == 8200, "absorb: rests 3s past the banner");
        ok(Absorb.delayMs(2000) == 6000 && Absorb.delayMs(20000) == 23000, "absorb: delay floor and scale");

        // --- ShadeMatch
        ok(ShadeMatch.panel("Notification shade.", "android.widget.FrameLayout"), "shade: en notification shade");
        ok(ShadeMatch.panel("Quick settings.", ""), "shade: en quick settings");
        ok(ShadeMatch.panel("", "com.android.systemui.statusbar.phone.NotificationPanelView"), "shade: legacy class name");
        ok(ShadeMatch.panel("Setelan cepat", ""), "shade: id quick settings");
        ok(!ShadeMatch.panel("Volume", "android.widget.FrameLayout"), "shade: volume is not the shade");
        ok(!ShadeMatch.panel("Power menu", "android.widget.LinearLayout"), "shade: power menu is not the shade");
        ok(!ShadeMatch.panel("Lock screen.", "android.widget.FrameLayout"), "shade: lock screen is not the shade");
        // --- Parse: idle wall clock (icon + text on an empty island)
        ok("1:05".equals(Parse.timeOfDay(1, 5, false)), "time 12h: 01 -> 1:05");
        ok("12:07".equals(Parse.timeOfDay(0, 7, false)), "time 12h: 00 -> 12:07");
        ok("13:07".equals(Parse.timeOfDay(13, 7, true)), "time 24h: 13 -> 13:07");
        ok("3:03".equals(Parse.timeOfDay(15, 3, false)), "time 12h: 15 -> 3:03");
        ok("23:59".equals(Parse.timeOfDay(23, 59, true)), "time 24h: 23:59");

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

        // --- Island rules: quiet hours (may wrap midnight), per-app scope, one banner per chat
        ok(Rules.inQuiet(23 * 60, 22 * 60, 7 * 60) && Rules.inQuiet(3 * 60, 22 * 60, 7 * 60), "rules: quiet window wraps past midnight");
        ok(!Rules.inQuiet(12 * 60, 22 * 60, 7 * 60) && !Rules.inQuiet(7 * 60, 22 * 60, 7 * 60), "rules: outside the window, end is exclusive");
        ok(Rules.inQuiet(13 * 60, 12 * 60, 14 * 60) && !Rules.inQuiet(14 * 60, 12 * 60, 14 * 60), "rules: same-day window");
        ok(!Rules.inQuiet(5, 600, 600), "rules: equal start and end means off");
        java.util.HashSet<String> qa = new java.util.HashSet<String>();
        ok(Rules.quietCovers("a.b", qa) && Rules.quietCovers("a.b", null), "rules: empty app list covers every app");
        qa.add("x.y");
        ok(Rules.quietCovers("x.y", qa) && !Rules.quietCovers("a.b", qa), "rules: listed apps only");
        Rules.resetChats();
        ok(!Rules.chatRepeat("wa|Ann", 1000L, 60000L), "rules: first banner of a chat shows");
        ok(Rules.chatRepeat("wa|Ann", 30000L, 60000L), "rules: follow-up inside the window stays quiet");
        ok(!Rules.chatRepeat("wa|Bob", 30000L, 60000L), "rules: another chat is independent");
        ok(!Rules.chatRepeat("wa|Ann", 70000L, 60000L), "rules: chat pops again after the window");
        ok("07:30".equals(Rules.hhmm(450)) && "00:00".equals(Rules.hhmm(1440)) && "23:30".equals(Rules.hhmm(-30)), "rules: clock text");

        // --- Timers / stopwatches
        ok(Parse.chronoMs(10000L, false, 75000L) == 65000L, "chrono: stopwatch counts up from its base");
        ok(Parse.chronoMs(80000L, true, 75000L) == 5000L, "chrono: timer counts down to its end");
        ok(Parse.chronoMs(80000L, true, 90000L) == 0L && Parse.chronoMs(80000L, false, 70000L) == 0L, "chrono: never negative");
        ok(Parse.wallBase(100000L, 160000L, 1000000L) == 940000L, "chrono: elapsed-realtime base -> wall-clock base");
        ok("1:05".equals(Parse.clock(Parse.chronoMs(Parse.wallBase(100000L, 165000L, 5000000L), false, 5000000L))), "chrono: custom stopwatch reads 1:05");
        ok(Parse.sameLabels(new String[]{"Lap", "Pause"}, new String[]{"Lap", "Pause"}) && !Parse.sameLabels(new String[]{"Lap", "Pause"}, new String[]{"Lap", "Resume"}), "chrono: Pause -> Resume is noticed");
        ok(!Parse.sameLabels(new String[]{"A"}, new String[]{"A", "B"}) && Parse.sameLabels(new String[0], new String[0]), "chrono: label lists of different length differ");
        ok(Parse.isClockApp("com.google.android.deskclock") && Parse.isClockApp("com.sec.android.app.clockpackage") && Parse.isClockApp("com.coloros.alarmclock"), "chrono: known clock apps");
        ok(!Parse.isClockApp("com.whatsapp") && !Parse.isClockApp(null), "chrono: other apps are not clock apps");

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

        // --- Live activities: judged by the app
        ok(Live.kind("com.grabtaxi.passenger") == Live.RIDE && Live.kind("me.lyft.android") == Live.RIDE, "live: ride apps");
        ok(Live.kind("com.ubercab.eats") == Live.FOOD && Live.kind("com.ubercab") == Live.RIDE, "live: Uber Eats is food, Uber is a ride");
        ok(Live.kind("com.espn.score_center") == Live.SPORT, "live: score apps");
        ok(Live.kind("com.shopee.id") == Live.FOOD, "live: prefix match");
        ok(Live.kind("com.whatsapp") == Live.NONE && Live.kind(null) == Live.NONE && Live.kind("") == Live.NONE, "live: ordinary apps are not live activities");
        ok(Live.kind("com.some.foodpanda.app") == Live.FOOD && Live.kind("io.city.taxi") == Live.RIDE, "live: keyword fallback");

        // --- stopwatch / timer readouts and the text clock
        ok(Parse.findClockMs("01:23") == 83000L && Parse.findClockMs("Stopwatch 12:34.56") == 754560L && Parse.findClockMs("1:02:03") == 3723000L, "timer: reads mm:ss, mm:ss.cc and h:mm:ss");
        ok(Parse.findClockMs("no time here") == -1L && Parse.findClockMs("07:75") == -1L && Parse.findClockMs(null) == -1L, "timer: no time / impossible minutes are not a readout");
        ok(Parse.findClockMs("0:05.3") == 5300L && Parse.findClockMs("0:05.123") == 5123L, "timer: tenths and thousandths");
        ok(Parse.timerWords("Stopwatch") && Parse.timerWords("Pause") && !Parse.timerWords("Next alarm 07:00"), "timer: stopwatch words are told from an alarm");
        ok(Parse.timerWords("Lap") && Parse.timerWords("Stopwatch  00:12") && !Parse.timerWords("Collapse") && !Parse.timerWords("Alarm 07:00 Snooze Dismiss"), "timer: 'lap' is a word, an alarm is not a timer");
        ok(Parse.pausedByActions(new String[]{"Resume", "Reset"}) && !Parse.pausedByActions(new String[]{"Lap", "Pause"}) && Parse.pausedByActions(new String[]{"Lanjutkan"}) && !Parse.pausedByActions(new String[0]), "timer: Resume without Pause means frozen");
        ok(Parse.frozenMs(1000L, false, 6000L, 99000L) == 5000L && Parse.frozenMs(10000L, true, 4000L, 99000L) == 6000L, "timer: a paused stopwatch / countdown freezes at its post time");
        ok(Parse.frozenMs(50000L, false, 1000L, 60000L) == 10000L, "timer: a post time before the base falls back to now");
        ok(Parse.totalMs(0L, 299600L) == 300000L && Parse.totalMs(300000L, 120000L) == 300000L && Parse.totalMs(0L, 400L) == 0L, "timer: first-seen length is the full mark of the ring");
        ok(Parse.isClockApp("com.google.android.deskclock") && Parse.isClockApp("com.sec.android.app.clockpackage") && Parse.isClockApp("com.fossor.stopwatchtimer") && !Parse.isClockApp("com.whatsapp"), "timer: clock apps are recognised");
        ok("Quarter past ten".equals(Parse.words(10, 15)) && "Half past three".equals(Parse.words(15, 30)) && "Twenty to six".equals(Parse.words(17, 40)), "words: past / half / to");
        ok("Noon".equals(Parse.words(12, 1)) && "Midnight".equals(Parse.words(23, 58)) && "Ten o'clock".equals(Parse.words(10, 2)) && "Five past one".equals(Parse.words(13, 4)), "words: o'clock, noon, midnight, rounding to five");
        ok("Twenty five past eleven".equals(Parse.words(11, 25)) && "Five to twelve".equals(Parse.words(11, 55)) && "One o'clock".equals(Parse.words(0, 58)), "words: twenty five, hour rollover");
        ok("in 25m".equals(Parse.until(25 * 60000L, 0L)) && "in 7h 48m".equals(Parse.until((7 * 60 + 48) * 60000L, 0L)) && "in 2d 3h".equals(Parse.until((51 * 60) * 60000L, 0L)), "alarm: wait text");
        ok(Wx.kind(0) == Wx.CLEAR && Wx.kind(2) == Wx.PARTLY && Wx.kind(63) == Wx.RAIN && Wx.kind(81) == Wx.RAIN && Wx.kind(73) == Wx.SNOW && Wx.kind(95) == Wx.STORM && Wx.kind(45) == Wx.FOG && Wx.kind(53) == Wx.DRIZZLE, "weather: codes map to icons");
        ok("Sunny".equals(Wx.desc(0, true)) && "Clear".equals(Wx.desc(0, false)) && "Showers".equals(Wx.desc(81, true)), "weather: words follow day / night");
        ok("27\u00b0".equals(Wx.temp(26.6, false)) && "80\u00b0".equals(Wx.temp(26.6, true)) && "0\u00b0".equals(Wx.temp(-0.2, false)) && "-3\u00b0".equals(Wx.temp(-3.2, false)), "weather: temperature text, no minus zero");
        ok(Wx.useF(0, "US") && !Wx.useF(0, "ID") && Wx.useF(2, "ID") && !Wx.useF(1, "US"), "weather: unit follows the country unless chosen");

        // --- Lru: oldest entry leaves first, recently used ones stay (no mass wipe at the cap)
        java.util.LinkedHashMap<String, Integer> lru = Lru.map(3);
        lru.put("a", 1); lru.put("b", 2); lru.put("c", 3);
        lru.get("a");                      // a is now the freshest
        lru.put("d", 4);                   // b is the oldest -> goes
        ok(lru.size() == 3 && !lru.containsKey("b") && lru.containsKey("a") && lru.containsKey("c") && lru.containsKey("d"), "lru: evicts only the least recently used entry");
        for (int i = 0; i < 50; i++) lru.put("k" + i, i);
        ok(lru.size() == 3, "lru: never grows past its cap");

        // --- Rules: a busy phone no longer forgets a chat that just buzzed
        Rules.resetChats();
        long t0 = 1000000L;
        ok(!Rules.chatRepeat("hot|chat", t0, 60000L), "chats: first banner of a chat shows");
        boolean hotKept = true;
        for (int i = 0; i < 300; i++) {            // 300 other chats while this one keeps being active (old code wiped everything at 200)
            Rules.chatRepeat("other|" + i, t0 + 1, 60000L);
            if (i % 50 == 49 && !Rules.chatRepeat("hot|chat", t0 + 2, 60000L)) hotKept = false;
        }
        ok(hotKept && Rules.chatRepeat("hot|chat", t0 + 2, 60000L), "chats: an active chat is still remembered after 300 other chats");
        for (int i = 0; i < 400; i++) Rules.chatRepeat("flood|" + i, t0 + 3, 60000L);
        ok(!Rules.chatRepeat("hot|chat", t0 + 4, 60000L), "chats: memory stays bounded, the oldest chat is eventually forgotten");
        Rules.resetChats();

        // --- Http: size cap, status codes and failures all come back as null; a good answer comes back whole
        try {
            com.sun.net.httpserver.HttpServer srv = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            srv.createContext("/ok", new com.sun.net.httpserver.HttpHandler() {
                @Override public void handle(com.sun.net.httpserver.HttpExchange x) throws java.io.IOException {
                    byte[] b = "{\"a\":1}".getBytes("UTF-8"); x.sendResponseHeaders(200, b.length); x.getResponseBody().write(b); x.close(); }
            });
            srv.createContext("/big", new com.sun.net.httpserver.HttpHandler() {
                @Override public void handle(com.sun.net.httpserver.HttpExchange x) throws java.io.IOException {
                    byte[] b = new byte[50000]; java.util.Arrays.fill(b, (byte) 'x'); x.sendResponseHeaders(200, b.length); x.getResponseBody().write(b); x.close(); }
            });
            srv.createContext("/gone", new com.sun.net.httpserver.HttpHandler() {
                @Override public void handle(com.sun.net.httpserver.HttpExchange x) throws java.io.IOException { x.sendResponseHeaders(404, -1); x.close(); }
            });
            srv.start();
            String base = "http://127.0.0.1:" + srv.getAddress().getPort();
            ok("{\"a\":1}".equals(Http.get(base + "/ok", 2000, 2000, 1000)), "http: a small 200 answer comes back whole");
            ok(Http.get(base + "/big", 2000, 2000, 10000) == null, "http: an answer over the size cap is dropped");
            ok(Http.get(base + "/big", 2000, 2000, 100000) != null, "http: the same answer is fine under a bigger cap");
            ok(Http.get(base + "/gone", 2000, 2000, 1000) == null, "http: a 404 is null");
            srv.stop(0);
            ok(Http.get(base + "/ok", 500, 500, 1000) == null, "http: a dead server is null, not an exception");
        } catch (Throwable ex) {
            ok(false, "http: local server test could not run: " + ex);
        }

        System.out.println(fails == 0 ? "ALL CHECKS PASSED" : (fails + " FAILED"));
        System.exit(fails == 0 ? 0 : 1);
    }
}
