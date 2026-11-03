package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the "why didn't it show?" trace and the reasons Gate gives (pure Java). */
final class TraceCheck {
    private TraceCheck() {}

    private static int why(boolean initial, boolean ongoing, boolean content, int importance, boolean summary, String cat) {
        return Gate.reject(initial, ongoing, content, importance, 3, summary, cat);
    }

    static void run() {
        // --- Gate gives the reason, and agrees with banner()
        ok(why(false, false, true, 4, false, "msg") == 0, "trace: a fresh message has no reason to be dropped");
        ok(why(true, false, true, 4, false, "msg") == Gate.SILENT && why(false, true, true, 4, false, "msg") == Gate.SILENT, "trace: already-showing and ongoing are silent by design, not drops");
        ok(why(false, false, false, 4, false, "msg") == Trace.NO_CONTENT, "trace: empty notification says so");
        ok(why(false, false, true, 2, false, "msg") == Trace.LOW_IMPORTANCE, "trace: a silent channel says so");
        ok(why(false, false, true, 4, true, "msg") == Gate.SILENT, "trace: a summary-only child is silent by design");
        ok(why(false, false, true, 4, false, "progress") == Trace.HOUSEKEEPING, "trace: a housekeeping category says so");
        ok(why(false, false, true, -1, false, null) == 0, "trace: unknown importance is not a reason");

        // --- the log
        Trace.clear();
        Trace.add("com.a", Trace.LOW_IMPORTANCE, 1000L);
        Trace.add("com.b", Trace.OFF_APP, 2000L);
        Trace.Item[] all = Trace.all();
        ok(all.length == 2 && "com.b".equals(all[0].pkg) && "com.a".equals(all[1].pkg), "trace: newest first");
        ok(all[0].reason == Trace.OFF_APP && all[0].at == 2000L && all[0].count == 1, "trace: keeps the reason and the time");

        Trace.clear();
        Trace.add("com.a", Trace.LOW_IMPORTANCE, 1000L);
        Trace.add("com.a", Trace.LOW_IMPORTANCE, 5000L);
        Trace.add("com.a", Trace.LOW_IMPORTANCE, 9000L);
        ok(Trace.size() == 1 && Trace.all()[0].count == 3 && Trace.all()[0].at == 9000L, "trace: the same app and reason within a minute is one line with a counter");
        Trace.add("com.a", Trace.LOW_IMPORTANCE, 9000L + Trace.MERGE_MS);
        ok(Trace.size() == 2, "trace: after a minute it is a new line");

        Trace.clear();
        Trace.add("com.a", Trace.LOW_IMPORTANCE, 1000L);
        Trace.add("com.b", Trace.OFF_APP, 2000L);
        Trace.add("com.a", Trace.LOW_IMPORTANCE, 3000L);
        ok(Trace.size() == 2 && "com.a".equals(Trace.all()[0].pkg) && Trace.all()[0].count == 2, "trace: a repeat moves its line back to the top");

        Trace.clear();
        Trace.add("com.a", Trace.LOW_IMPORTANCE, 1000L);
        Trace.add("com.a", Trace.OFF_APP, 2000L);
        ok(Trace.size() == 2, "trace: another reason for the same app is its own line");

        Trace.clear();
        for (int i = 0; i < Trace.MAX + 25; i++) Trace.add("com.app" + i, Trace.OFF_APP, 1000L + i);
        ok(Trace.size() == Trace.MAX, "trace: memory is bounded");
        ok(("com.app" + (Trace.MAX + 24)).equals(Trace.all()[0].pkg), "trace: the oldest lines go first");

        Trace.clear();
        Trace.add(null, Trace.OFF_APP, 1L);
        Trace.add("com.a", 0, 1L);
        ok(Trace.size() == 0, "trace: no package or no reason is not logged");

        boolean allText = true;
        for (int r = Trace.OFF_APP; r <= Trace.SNOOZED; r++) allText &= !"Unknown".equals(Trace.text(r)) && Trace.hint(r).length() > 0;
        ok(allText, "trace: every reason has a text and a hint");
        Trace.clear();
    }
}
