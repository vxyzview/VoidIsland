package com.vxyzview.voidisland;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * "Why didn't it show?": the last few times the island kept a message banner off the screen, and why. It holds the app's package
 * name and a reason code, never any message text, and lives in memory only (like Dbg). Pure Java so the unit checks cover it.
 */
final class Trace {
    private Trace() {}

    static final int MAX = 40;
    /** The same app dropped for the same reason within this long is one line with a counter, not a flood. */
    static final long MERGE_MS = 60000L;

    static final int OFF_APP = 1, OFF_TYPE = 2, LOW_IMPORTANCE = 3, NO_CONTENT = 4, HOUSEKEEPING = 5, REPEAT = 6,
        QUIET_HOURS = 7, MUTED_GROUP = 8, ONE_PER_CHAT = 9, IN_APP = 10, QUEUE_FULL = 11, LOCK_SECRET = 12,
        ISLAND_OFF = 13, FULLSCREEN = 14, LOCKED = 15, SHADE_OPEN = 16, SNOOZED = 17;

    static final class Item {
        final String pkg;
        final int reason;
        long at;
        int count = 1;
        Item(String pkg, int reason, long at) { this.pkg = pkg; this.reason = reason; this.at = at; }
    }

    private static final ArrayDeque<Item> LOG = new ArrayDeque<Item>();

    static synchronized void add(String pkg, int reason, long now) {
        if (pkg == null || reason <= 0) return;
        for (Item it : LOG) {   // newest first: only the latest line for this app and reason can merge
            if (it.pkg.equals(pkg) && it.reason == reason) {
                if (now - it.at < MERGE_MS) { it.count++; it.at = now; LOG.remove(it); LOG.addFirst(it); return; }
                break;
            }
        }
        if (LOG.size() >= MAX) LOG.pollLast();
        LOG.addFirst(new Item(pkg, reason, now));
    }

    static synchronized Item[] all() { return new ArrayList<Item>(LOG).toArray(new Item[0]); }

    static synchronized int size() { return LOG.size(); }

    static synchronized void clear() { LOG.clear(); }

    /** What happened, in a few words. */
    static String text(int reason) {
        switch (reason) {
            case OFF_APP: return "App is switched off";
            case OFF_TYPE: return "That kind of banner is switched off";
            case LOW_IMPORTANCE: return "Silent notification";
            case NO_CONTENT: return "Nothing to show (no title or text)";
            case HOUSEKEEPING: return "System or background notice";
            case REPEAT: return "Same text sent again";
            case QUIET_HOURS: return "Quiet hours";
            case MUTED_GROUP: return "Group chats are off";
            case ONE_PER_CHAT: return "One banner per chat";
            case IN_APP: return "You were in that app";
            case QUEUE_FULL: return "Too many banners at once";
            case LOCK_SECRET: return "Hidden on the lock screen";
            case ISLAND_OFF: return "The island was switched off";
            case FULLSCREEN: return "Full-screen app in front";
            case LOCKED: return "Phone was locked";
            case SHADE_OPEN: return "Notification shade was open";
            case SNOOZED: return "App snoozed";
            default: return "Unknown";
        }
    }

    /** Where to change it, or why it is by design. */
    static String hint(int reason) {
        switch (reason) {
            case OFF_APP: return "Home > Choose apps";
            case OFF_TYPE: return "Home > what the island shows";
            case LOW_IMPORTANCE: return "The app sent it without sound or pop-up, so the island leaves it alone";
            case NO_CONTENT: return "The app sent it empty";
            case HOUSEKEEPING: return "Android marks these as background notices";
            case REPEAT: return "Identical text within 30 seconds is shown once";
            case QUIET_HOURS: return "Behavior > Quiet hours";
            case MUTED_GROUP: return "Behavior > Banner rules";
            case ONE_PER_CHAT: return "Behavior > Banner rules";
            case IN_APP: return "Banners for an app are skipped while it is open";
            case QUEUE_FULL: return "Four banners were already waiting; this one stayed in the shade";
            case LOCK_SECRET: return "The notification is private or secret: lock-screen privacy";
            case ISLAND_OFF: return "Turn the island on again on the Home tab";
            case FULLSCREEN: return "Behavior > Hide in full screen";
            case LOCKED: return "Behavior > Show on lock screen";
            case SHADE_OPEN: return "Hidden while Quick Settings or the shade is open";
            case SNOOZED: return "You long-pressed one of its banners; it resumes by itself after an hour";
            default: return "";
        }
    }
}
