package com.vxyzview.voidisland;

import android.app.PendingIntent;

import java.util.ArrayList;

/**
 * The last banners the island really showed, so one you looked away from is not lost (removing the original from the shade
 * is opt-in, and Android cannot bring a removed notification back). Memory only: gone when the process ends. Main thread only.
 */
final class Recent {
    private Recent() {}

    static final int MAX = 20;

    static final class Item {
        final String key, pkg, app, title, text;
        final long at;
        final PendingIntent open;

        Item(String key, String pkg, String app, String title, String text, long at, PendingIntent open) {
            this.key = key; this.pkg = pkg; this.app = app; this.title = title; this.text = text; this.at = at; this.open = open;
        }
    }

    private static final ArrayList<Item> L = new ArrayList<Item>();

    static void add(IslandEvent e) {
        if (e == null || e.key == null) return;
        for (int i = L.size() - 1; i >= 0; i--) if (e.key.equals(L.get(i).key)) L.remove(i);   // the same chat again: newest only
        L.add(0, new Item(e.key, e.pkg, e.appName == null ? "" : e.appName, e.title == null ? "" : e.title.toString(),
            e.text == null ? "" : e.text.toString(), System.currentTimeMillis(), e.contentIntent));
        while (L.size() > MAX) L.remove(L.size() - 1);
    }

    /** Newest first. */
    static Item[] all() { return L.toArray(new Item[0]); }

    static void clear() { L.clear(); }
}
