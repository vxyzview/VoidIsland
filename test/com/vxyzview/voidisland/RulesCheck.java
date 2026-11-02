package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the Rules logic (pure Java: runs on a plain JVM, no Android). */
final class RulesCheck {
    private RulesCheck() {}

    static void run() {
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
        Rules.resetChats();
        ok(!Rules.chatQuiet("wa|Cy", 1000L, 60000L) && !Rules.chatQuiet("wa|Cy", 2000L, 60000L), "rules: looking never records a chat");
        Rules.chatShown("wa|Cy", 3000L);
        ok(Rules.chatQuiet("wa|Cy", 4000L, 60000L) && !Rules.chatQuiet("wa|Cy", 70000L, 60000L), "rules: a shown banner quiets its chat for the window");
        // a banner dropped before anyone saw it must not silence its chat
        Rules.chatShown("wa|Dee", 5000L);
        ok(Rules.chatQuiet("wa|Dee", 6000L, 60000L), "rules: a recorded chat is quiet");
        Rules.chatForget("wa|Dee");
        ok(!Rules.chatQuiet("wa|Dee", 6000L, 60000L) && !Rules.chatRepeat("wa|Dee", 6000L, 60000L), "rules: a forgotten chat shows its next banner");
        Rules.chatShown("wa|Eli", 5000L);
        Rules.chatForget("wa|Fay"); Rules.chatForget(null);
        ok(Rules.chatQuiet("wa|Eli", 6000L, 60000L), "rules: forgetting one chat (or none) leaves the others alone");
        ok("07:30".equals(Rules.hhmm(450)) && "00:00".equals(Rules.hhmm(1440)) && "23:30".equals(Rules.hhmm(-30)), "rules: clock text");

        // --- Live activities: judged by the app
        ok(Live.kind("com.grabtaxi.passenger") == Live.RIDE && Live.kind("me.lyft.android") == Live.RIDE, "live: ride apps");
        ok(Live.kind("com.ubercab.eats") == Live.FOOD && Live.kind("com.ubercab") == Live.RIDE, "live: Uber Eats is food, Uber is a ride");
        ok(Live.kind("com.espn.score_center") == Live.SPORT, "live: score apps");
        ok(Live.kind("com.shopee.id") == Live.FOOD, "live: Shopee ID hosts ShopeeFood, so it is matched by exact package");
        ok(Live.kind("com.whatsapp") == Live.NONE && Live.kind(null) == Live.NONE && Live.kind("") == Live.NONE, "live: ordinary apps are not live activities");
        ok(Live.kind("com.some.foodpanda.app") == Live.FOOD && Live.kind("io.city.taxi") == Live.RIDE, "live: keyword fallback");

        // --- Live activities: the user's own app lists win over the built-in guesses
        java.util.Set<String> myFood = new java.util.HashSet<String>(); myFood.add("com.whatsapp"); myFood.add("com.ubercab");
        java.util.Set<String> myRide = new java.util.HashSet<String>(); myRide.add("com.example.shuttle");
        ok(Live.kind("com.example.shuttle", myRide, null, null) == Live.RIDE, "live: an app on the user's ride list is a ride");
        ok(Live.kind("com.whatsapp", myRide, myFood, null) == Live.FOOD, "live: the user can add any app");
        ok(Live.kind("com.ubercab", myRide, myFood, null) == Live.FOOD, "live: the user's list overrides the built-in guess");
        ok(Live.kind("com.grabtaxi.passenger", null, null, null) == Live.RIDE && Live.kind(null, myRide, myFood, null) == Live.NONE, "live: empty lists change nothing");
    }
}
