package com.vxyzview.voidisland;

import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Settings backup: every preference as plain text, one per line ({@code type|key|value}), so the file can be saved anywhere
 * and read back after a reinstall. The text format is pure Java so the unit checks cover it.
 */
final class Backup {
    private Backup() {}

    static final String HEADER = "VoidIsland backup 1";

    /** The largest backup file that is read: a real one is a few KB, so anything bigger is not a backup. */
    static final int MAX_BYTES = 1000000;

    /**
     * Reads the whole stream, or returns null when it holds more than {@code max} bytes (a huge file is never buffered).
     * A null stream (a picker that handed back nothing) is an IOException, so the caller reports "could not read" rather than "not a backup".
     */
    static byte[] readCapped(java.io.InputStream in, int max) throws java.io.IOException {
        if (in == null) throw new java.io.IOException("no stream");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) != -1;) {
            if (out.size() + n > max) return null;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** What a restore would set: the known settings of a file, and how many entries were left out. */
    static final class Plan {
        final Map<String, Object> values;
        final int ignored;
        Plan(Map<String, Object> values, int ignored) { this.values = values; this.ignored = ignored; }
    }

    /**
     * The plan for restoring {@code text}, or null when it is not a backup. {@code never} are keys a file may not decide (they have their
     * own confirmation in the app): they are dropped without counting as ignored. Keys this version does not know are dropped and counted.
     */
    static Plan plan(String text, Set<String> known, Set<String> never) {
        Map<String, Object> all = read(text);
        if (all == null) return null;
        if (never != null) for (String k : never) all.remove(k);
        Map<String, Object> vals = onlyKnown(all, known);
        return new Plan(vals, all.size() - vals.size());
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') b.append("\\\\");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '|') b.append("\\p");
            else if (c == ',') b.append("\\c");
            else b.append(c);
        }
        return b.toString();
    }

    private static String unesc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                if (n == 'n') b.append('\n');
                else if (n == 'r') b.append('\r');
                else if (n == 'p') b.append('|');
                else if (n == 'c') b.append(',');
                else b.append(n);
            } else b.append(c);
        }
        return b.toString();
    }

    /** All preferences as backup text. Keys are sorted so two backups of the same settings are identical. */
    static String write(Map<String, ?> all) {
        StringBuilder b = new StringBuilder(HEADER).append('\n');
        for (Map.Entry<String, ?> en : new TreeMap<String, Object>(all).entrySet()) {
            Object v = en.getValue();
            String k = en.getKey();
            if (k == null || v == null || k.indexOf('|') >= 0 || k.indexOf('\n') >= 0) continue;
            String t;
            String s;
            if (v instanceof Boolean) { t = "b"; s = v.toString(); }
            else if (v instanceof Integer) { t = "i"; s = v.toString(); }
            else if (v instanceof Long) { t = "l"; s = v.toString(); }
            else if (v instanceof Float) { t = "f"; s = v.toString(); }
            else if (v instanceof String) { t = "s"; s = esc((String) v); }
            else if (v instanceof Set) {
                t = "t";
                StringBuilder sb = new StringBuilder();
                java.util.ArrayList<String> items = new java.util.ArrayList<String>();
                for (Object o : (Set<?>) v) if (o != null) items.add(o.toString());
                java.util.Collections.sort(items);
                for (String o : items) { if (sb.length() > 0) sb.append(','); sb.append(esc(o)); }
                s = sb.toString();
            } else continue;
            b.append(t).append('|').append(k).append('|').append(s).append('\n');
        }
        return b.toString();
    }

    /** Reads backup text back into typed values; null when the text is not a VoidIsland backup. Bad lines are skipped. */
    static Map<String, Object> read(String text) {
        if (text == null) return null;
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length == 0 || !HEADER.equals(lines[0].trim())) return null;
        Map<String, Object> out = new TreeMap<String, Object>();
        for (int i = 1; i < lines.length; i++) {
            String[] p = lines[i].split("\\|", 3);
            if (p.length < 3 || p[0].length() != 1 || p[1].length() == 0) continue;
            try {
                char t = p[0].charAt(0);
                if (t == 'b') out.put(p[1], Boolean.valueOf("true".equals(p[2])));
                else if (t == 'i') out.put(p[1], Integer.valueOf(p[2]));
                else if (t == 'l') out.put(p[1], Long.valueOf(p[2]));
                else if (t == 'f') out.put(p[1], Float.valueOf(p[2]));
                else if (t == 's') out.put(p[1], unesc(p[2]));
                else if (t == 't') {
                    HashSet<String> set = new HashSet<String>();
                    if (p[2].length() > 0) for (String x : p[2].split(",")) set.add(unesc(x));
                    out.put(p[1], set);
                }
            } catch (RuntimeException bad) { /* a damaged line: leave that one setting alone */ }
        }
        return out;
    }

    /** The entries of {@code values} whose key is in {@code known}; anything else in the file (stale or foreign keys) is left out. */
    static Map<String, Object> onlyKnown(Map<String, Object> values, Set<String> known) {
        Map<String, Object> out = new TreeMap<String, Object>();
        if (values == null || known == null) return out;
        for (Map.Entry<String, Object> en : values.entrySet()) if (known.contains(en.getKey())) out.put(en.getKey(), en.getValue());
        return out;
    }

    /** Replaces every stored preference with the backup's values. */
    @SuppressWarnings("unchecked")
    static void apply(SharedPreferences sp, Map<String, Object> values) {
        SharedPreferences.Editor ed = sp.edit();
        ed.clear();
        for (Map.Entry<String, Object> en : values.entrySet()) {
            Object v = en.getValue();
            String k = en.getKey();
            if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) ed.putInt(k, (Integer) v);
            else if (v instanceof Long) ed.putLong(k, (Long) v);
            else if (v instanceof Float) ed.putFloat(k, (Float) v);
            else if (v instanceof String) ed.putString(k, (String) v);
            else if (v instanceof Set) ed.putStringSet(k, (Set<String>) v);
        }
        ed.commit();
    }
}
