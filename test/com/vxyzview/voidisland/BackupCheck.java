package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the Backup logic (pure Java: runs on a plain JVM, no Android). */
final class BackupCheck {
    private BackupCheck() {}

    static void run() {
        // --- Backup: settings survive a round trip, awkward text included
        java.util.HashMap<String, Object> prefs = new java.util.HashMap<String, Object>();
        java.util.HashSet<String> apps = new java.util.HashSet<String>(); apps.add("com.a.b"); apps.add("com.c,d|e");
        prefs.put("on", Boolean.TRUE); prefs.put("dur", Integer.valueOf(5)); prefs.put("big", Long.valueOf(1234567890123L));
        prefs.put("scale", Float.valueOf(1.25f)); prefs.put("name", "line1\nline2 | pipe, comma \\ slash"); prefs.put("blocked", apps);
        String saved = Backup.write(prefs);
        java.util.Map<String, Object> back = Backup.read(saved);
        ok(back != null && back.size() == prefs.size(), "backup: every setting comes back");
        ok(back != null && Boolean.TRUE.equals(back.get("on")) && Integer.valueOf(5).equals(back.get("dur")) && Long.valueOf(1234567890123L).equals(back.get("big")) && Float.valueOf(1.25f).equals(back.get("scale")), "backup: numbers and switches keep their type");
        ok(back != null && "line1\nline2 | pipe, comma \\ slash".equals(back.get("name")), "backup: newlines, pipes, commas and backslashes survive");
        ok(back != null && apps.equals(back.get("blocked")), "backup: app lists survive, even with commas in the names");
        ok(Backup.read("not a backup") == null && Backup.read("") == null && Backup.read(null) == null, "backup: other files are refused");
        ok(Backup.read(Backup.HEADER + "\nb|ok|true\ngarbage\ni|bad|xyz\ni|good|7\n").size() == 2, "backup: damaged lines are skipped, the rest is kept");
        ok(Backup.write(prefs).equals(saved), "backup: the same settings always give the same file");

        // --- Backup: a restore only sets keys this version knows
        java.util.Map<String, Object> mixed = new java.util.TreeMap<String, Object>();
        mixed.put("dur", Integer.valueOf(7)); mixed.put("f_lyric", Boolean.FALSE); mixed.put("evil_key", "x"); mixed.put("", "empty");
        java.util.Set<String> known = new java.util.HashSet<String>(java.util.Arrays.asList("dur", "f_lyric", "f_media"));
        java.util.Map<String, Object> kept = Backup.onlyKnown(mixed, known);
        ok(kept.size() == 2 && Integer.valueOf(7).equals(kept.get("dur")) && Boolean.FALSE.equals(kept.get("f_lyric")), "backup: known keys keep their values");
        ok(!kept.containsKey("evil_key") && !kept.containsKey(""), "backup: unknown and empty keys are left out");
        ok(Backup.onlyKnown(mixed, new java.util.HashSet<String>()).isEmpty() && Backup.onlyKnown(null, known).isEmpty() && Backup.onlyKnown(mixed, null).isEmpty(),
            "backup: no known keys (or no input) leaves nothing to restore, so nothing would be cleared");
        java.util.Map<String, Object> viaFile = Backup.read(Backup.HEADER + "\ni|dur|9\nb|f_lyric|true\ns|other_app_key|v\n");
        ok(viaFile != null && Backup.onlyKnown(viaFile, known).size() == 2 && viaFile.size() == 3, "backup: a file with a foreign key restores only the known ones");

        // --- Backup: reading a picked file is size-capped
        try {
            byte[] small = new byte[5000];
            ok(Backup.readCapped(new java.io.ByteArrayInputStream(small), Backup.MAX_BYTES).length == 5000, "backup: a small file is read whole");
            ok(Backup.readCapped(new java.io.ByteArrayInputStream(new byte[20000]), 20000).length == 20000, "backup: a file of exactly the limit is still read");
            ok(Backup.readCapped(new java.io.ByteArrayInputStream(new byte[20001]), 20000) == null, "backup: a file one byte over the limit is refused");
            ok(Backup.readCapped(new java.io.ByteArrayInputStream(new byte[0]), 100).length == 0, "backup: an empty file reads as empty (and is then not a backup)");
            boolean threw = false;
            try { Backup.readCapped(null, 100); } catch (java.io.IOException e) { threw = true; }
            ok(threw, "backup: a picker that gives no stream is an error, not 'not a backup'");
        } catch (java.io.IOException e) { ok(false, "backup: readCapped threw " + e); }

        // --- Backup: the restore plan
        java.util.Set<String> never = new java.util.HashSet<String>(java.util.Arrays.asList("absorb", "lyrics_notice"));
        String file = Backup.HEADER + "\ni|dur|9\nb|f_lyric|true\nb|absorb|true\nb|lyrics_notice|true\ns|foreign|v\n";
        Backup.Plan plan = Backup.plan(file, known, never);
        ok(plan != null && plan.values.size() == 2 && Integer.valueOf(9).equals(plan.values.get("dur")), "backup plan: the known settings are kept");
        ok(plan != null && !plan.values.containsKey("absorb") && !plan.values.containsKey("lyrics_notice"), "backup plan: keys with their own confirmation are never set from a file");
        ok(plan != null && plan.ignored == 1, "backup plan: only the foreign key counts as ignored, not the keys held back on purpose");
        ok(Backup.plan("not a backup", known, never) == null && Backup.plan(null, known, never) == null, "backup plan: a file that is not a backup gives no plan");
        Backup.Plan none = Backup.plan(Backup.HEADER + "\ns|foreign|v\n", known, never);
        ok(none != null && none.values.isEmpty() && none.ignored == 1, "backup plan: nothing known means an empty plan (the app then changes nothing)");
        ok(Backup.plan(file, known, null).values.containsKey("dur"), "backup plan: no held-back keys is fine");
    }
}
