package com.vxyzview.voidisland;

import java.util.Locale;

/** The app picker's search: every word of the query must appear in the app's name or its package name (pure Java). */
final class AppFilter {
    private AppFilter() {}

    static boolean matches(String label, String pkg, String query) {
        if (query == null) return true;
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        String hay = (label == null ? "" : label.toLowerCase(Locale.ROOT)) + "\n" + (pkg == null ? "" : pkg.toLowerCase(Locale.ROOT));
        for (String word : q.split("\\s+")) if (!hay.contains(word)) return false;
        return true;
    }
}
