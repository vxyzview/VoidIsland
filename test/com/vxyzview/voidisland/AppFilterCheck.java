package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the app picker's search (pure Java). */
final class AppFilterCheck {
    private AppFilterCheck() {}

    static void run() {
        ok(AppFilter.matches("WhatsApp", "com.whatsapp", null) && AppFilter.matches("WhatsApp", "com.whatsapp", "") && AppFilter.matches("WhatsApp", "com.whatsapp", "   "),
            "app filter: no query shows every app");
        ok(AppFilter.matches("WhatsApp", "com.whatsapp", "what") && AppFilter.matches("WhatsApp", "com.whatsapp", "APP"), "app filter: part of the name, any case");
        ok(AppFilter.matches("Messages", "com.google.android.apps.messaging", "google"), "app filter: the package name matches too");
        ok(!AppFilter.matches("WhatsApp", "com.whatsapp", "telegram"), "app filter: a different app does not match");
        ok(AppFilter.matches("Google Maps", "com.google.android.apps.maps", "maps goo"), "app filter: every word must match, in any order");
        ok(!AppFilter.matches("Google Maps", "com.google.android.apps.maps", "maps zzz"), "app filter: one word that matches nothing rules the app out");
        ok(AppFilter.matches("  Mail ", "x.mail", "  mail  "), "app filter: spaces around the query are ignored");
        ok(AppFilter.matches(null, "com.a", "com") && !AppFilter.matches(null, null, "x"), "app filter: a missing name or package is handled");
    }
}
