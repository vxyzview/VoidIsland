package com.vxyzview.voidisland;

import java.util.LinkedHashMap;

/** Static hand-off between the two system-bound services (notifications, accessibility) and the island. Main thread only. */
final class Bus {
    private Bus() {}

    private static IslandController ctl;
    private static final LinkedHashMap<String, IslandEvent> pending = new LinkedHashMap<String, IslandEvent>();

    static void attach(IslandController c) {
        ctl = c;
        for (IslandEvent e : pending.values()) c.post(e);
        pending.clear();
    }

    static void detach(IslandController c) { if (ctl == c) ctl = null; }

    static boolean active() { return ctl != null; }

    /** @return true only when the island took the event and can show it right now (false when it is not running). */
    static boolean post(IslandEvent e) {
        if (ctl != null) return ctl.post(e);
        if (e.live) pending.put(e.key, e); // live things (music, calls) are replayed once the island exists
        return false;
    }

    static void remove(String key) {
        if (ctl != null) ctl.remove(key);
        pending.remove(key);
    }

    /** Remove several keys at once and leave the island in a clean idle state (used by "Clear demos"). */
    static void clear(String[] keys) {
        for (String k : keys) pending.remove(k);
        if (ctl != null) ctl.clear(keys);
    }

    static void removeByNkey(String nkey, boolean liveOnly) {
        if (ctl != null) ctl.removeByNkey(nkey, liveOnly);
    }

    // --- diagnostics shown in the app (does panel detection work on this phone?)
    static volatile boolean shadeOpen;
    static volatile long sysSeenAt;
    static volatile int sysCount;
    static volatile String sysLast = "";

    static void shade(boolean open) {
        shadeOpen = open;
        if (ctl != null) ctl.setShade(open);
    }

    static void clockNow() { if (ctl != null) ctl.clockNow(); }

    /** The screen turned on or off: the island stops its timers while the screen is dark. */
    static void screen(boolean on) { if (ctl != null) ctl.setScreen(on); }

    static void keyguard() { if (ctl != null) ctl.refreshLock(); }

    static boolean appLayer() { return ctl != null && ctl.isAppLayer(); }

    /** Ask the island to re-evaluate after an event was edited in place. */
    static void refresh() { if (ctl != null) ctl.refresh(); }

    static void expand(String key) { if (ctl != null) ctl.expandKey(key); }

    static void foreground(String pkg) { if (ctl != null) ctl.setForeground(pkg); }
}
