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

        // --- Live activities: judged by the app
        ok(Live.kind("com.grabtaxi.passenger") == Live.RIDE && Live.kind("me.lyft.android") == Live.RIDE, "live: ride apps");
        ok(Live.kind("com.ubercab.eats") == Live.FOOD && Live.kind("com.ubercab") == Live.RIDE, "live: Uber Eats is food, Uber is a ride");
        ok(Live.kind("com.espn.score_center") == Live.SPORT, "live: score apps");
        ok(Live.kind("com.shopee.id") == Live.FOOD, "live: prefix match");
        ok(Live.kind("com.whatsapp") == Live.NONE && Live.kind(null) == Live.NONE && Live.kind("") == Live.NONE, "live: ordinary apps are not live activities");
        ok(Live.kind("com.some.foodpanda.app") == Live.FOOD && Live.kind("io.city.taxi") == Live.RIDE, "live: keyword fallback");

        System.out.println(fails == 0 ? "ALL CHECKS PASSED" : (fails + " FAILED"));
        System.exit(fails == 0 ? 0 : 1);
    }
}
