package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the classifier's pure decisions: Gate (does it deserve a banner), Dedupe (repeats) and chat keys. */
final class ClassifierCheck {
    private ClassifierCheck() {}

    private static boolean banner(boolean initial, boolean ongoing, boolean content, int importance, boolean summary, String cat) {
        return Gate.banner(initial, ongoing, content, importance, 3, summary, cat);   // 3 = IMPORTANCE_DEFAULT
    }

    static void run() {
        // --- Gate
        ok(banner(false, false, true, 4, false, "msg"), "gate: a fresh message with content gets a banner");
        ok(banner(false, false, true, -1, false, null), "gate: unknown importance and no category still gets a banner");
        ok(!banner(true, false, true, 4, false, "msg"), "gate: notifications already up at connect never banner");
        ok(!banner(false, true, true, 4, false, "msg"), "gate: ongoing notifications never banner");
        ok(!banner(false, false, false, 4, false, "msg"), "gate: nothing to say, no banner");
        ok(!banner(false, false, true, 2, false, "msg") && banner(false, false, true, 3, false, "msg"), "gate: below default importance is silent, default and up is not");
        ok(!banner(false, false, true, 0, false, "msg"), "gate: importance 0 (blocked by Do Not Disturb) is silent");
        ok(!banner(false, false, true, 4, true, "msg"), "gate: a child of a summary-only group stays quiet");
        ok(!banner(false, false, true, 4, false, "service") && !banner(false, false, true, 4, false, "sys")
            && !banner(false, false, true, 4, false, "status") && !banner(false, false, true, 4, false, "progress"), "gate: housekeeping categories stay quiet");
        ok(banner(false, false, true, 4, false, "email") && banner(false, false, true, 4, false, "social"), "gate: ordinary categories get a banner");

        // --- Dedupe
        Dedupe.clear();
        ok(!Dedupe.repeated("k1", "Alice|hi", 1000L), "dedupe: the first sight of a notification is not a repeat");
        ok(Dedupe.repeated("k1", "Alice|hi", 5000L), "dedupe: the same text again within 30 s is a repeat");
        ok(!Dedupe.repeated("k1", "Alice|hello", 6000L), "dedupe: new text on the same notification is not a repeat");
        ok(!Dedupe.repeated("k2", "Alice|hello", 6000L), "dedupe: another notification with the same text is not a repeat");
        ok(!Dedupe.repeated("k1", "Alice|hello", 6000L + Dedupe.WINDOW_MS), "dedupe: the same text after 30 s shows again");
        ok(!Dedupe.repeated("k3", "x", 100000L) && !Dedupe.repeated("k3", "y", 100001L) && !Dedupe.repeated("k3", "x", 100002L), "dedupe: only the latest text counts");
        ok(!Dedupe.repeated("k4", "t", 50000L) && !Dedupe.repeated("k4", "t", 10000L), "dedupe: a clock set back is not read as a repeat");
        Dedupe.clear();
        for (int i = 0; i < Dedupe.MAX + 50; i++) Dedupe.repeated("n" + i, "s", 1L);
        ok(Dedupe.size() == Dedupe.MAX, "dedupe: memory is bounded, oldest first out");
        ok(!Dedupe.repeated("n0", "s", 2L) && Dedupe.repeated("n" + (Dedupe.MAX + 49), "s", 2L), "dedupe: the oldest entry was forgotten, the newest remembered");
        Dedupe.clear();

        // --- chat keys (one banner per chat)
        ok("com.chat|Family".equals(Rules.chatKey("com.chat", "Family", "Mum")), "chat key: a group's conversation title wins over the sender");
        ok("com.chat|Mum".equals(Rules.chatKey("com.chat", null, "Mum")) && "com.chat|Mum".equals(Rules.chatKey("com.chat", "  ", "Mum")), "chat key: no conversation title falls back to the sender");
        ok(!Rules.chatKey("com.a", null, "Bo").equals(Rules.chatKey("com.b", null, "Bo")), "chat key: the same sender in two apps is two chats");
    }
}
