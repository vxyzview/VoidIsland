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
    }
}
