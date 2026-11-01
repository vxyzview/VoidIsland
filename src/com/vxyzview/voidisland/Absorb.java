package com.vxyzview.voidisland;

/** One-shot notifications belong to the island: the original is removed so the banner is not shown twice. */
final class Absorb {
    private Absorb() {}

    /** Text banners (messages, payments) are absorbed; live things (calls, timers, nav, progress) keep their own notification. */
    static boolean applies(int type, boolean live, boolean initial) {
        return !initial && !live && (type == IslandEvent.NOTIF || type == IslandEvent.PAYMENT);
    }

    /**
     * The one rule for removing an original notification. It is only ever removed when the person opted in, the app is
     * not on the "keep in shade" list, AND the island really took the banner and can show it right now. If the island is
     * off, crashed, hidden (landscape, lock screen, shade open) or ignoring the app, the original stays where it is.
     */
    static boolean shouldRemove(boolean optedIn, boolean keptInShade, boolean islandShowing, int type, boolean live, boolean initial) {
        return optedIn && !keptInShade && islandShowing && applies(type, live, initial);
    }
}
