package com.vxyzview.voidisland;

import android.content.Context;

/**
 * Looks a string up by name in res/values(-xx)/strings.xml, with the English text as the fallback. (The build has no R class,
 * so resources are found by name.) Only the text the island itself shows is translated so far; the settings screens are English.
 */
final class Str {
    private Str() {}

    static String t(Context c, String name, String en) {
        try {
            int id = c.getResources().getIdentifier(name, "string", c.getPackageName());
            return id != 0 ? c.getString(id) : en;
        } catch (Throwable t) { return en; }
    }

    static String t(Context c, String name, String en, Object... args) {
        try { return String.format(t(c, name, en), args); } catch (Throwable t) { return String.format(en, args); }
    }
}
