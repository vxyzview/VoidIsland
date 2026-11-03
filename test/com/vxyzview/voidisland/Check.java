package com.vxyzview.voidisland;

/**
 * Runs every check class and exits non-zero on any failure. One class per area (SpringCheck, ParseCheck, PrivacyCheck...):
 * add a new area by writing {@code XCheck.run()} and listing it below.
 */
public class Check {
    static int fails = 0;
    static void ok(boolean c, String m) { if (!c) { fails++; System.out.println("FAIL: " + m); } else System.out.println("ok:   " + m); }

    public static void main(String[] a) {
        SpringCheck.run();
        AbsorbCheck.run();
        KeepAliveCheck.run();
        ParseCheck.run();
        ColGeoCheck.run();
        ShadeCheck.run();
        LyricsCheck.run();
        RulesCheck.run();
        RestyleCheck.run();
        ActStyleCheck.run();
        BackupCheck.run();
        PrivacyCheck.run();
        ClassifierCheck.run();
        IslandEventCheck.run();
        TraceCheck.run();
        AppFilterCheck.run();
        ExitReasonCheck.run();
        LiveCheck.run();
        SnoozeCheck.run();
        System.out.println(fails == 0 ? "ALL CHECKS PASSED" : (fails + " FAILED"));
        System.exit(fails == 0 ? 0 : 1);
    }
}
