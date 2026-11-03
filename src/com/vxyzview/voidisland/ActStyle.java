package com.vxyzview.voidisland;

/**
 * The designs for every activity (Style > Activities): names, descriptions and which preference holds each choice.
 * Pure Java, so the unit checks cover it; the views are built in {@link Acts}.
 *
 * Every activity (call, payment, charging, download, timer, device, navigation, live activity, message) picks a compact
 * pill design and an expanded card design on its own. Design 0 is always the classic one that {@link Cards} still builds;
 * the rest are the flat designs from {@link Acts}: solid fills, tonal containers and thin lines, no gradients anywhere.
 */
final class ActStyle {
    private ActStyle() {}

    static final int CALL = 0, PAYMENT = 1, CHARGE = 2, DOWNLOAD = 3, TIMER = 4, DEVICE = 5, NAV = 6, LIVE = 7, MESSAGE = 8, COUNT = 9;

    static final String[] NAMES = {"Call", "Payment", "Charging", "Download", "Timer", "Device", "Navigation", "Live", "Message"};

    /** The preference holding the compact design / expanded design of each activity. */
    static final String[] CKEY = {Prefs.K_AC_CALL, Prefs.K_AC_PAY, Prefs.K_AC_CHG, Prefs.K_AC_DL, Prefs.K_AC_TIMER, Prefs.K_AC_DEV, Prefs.K_AC_NAV, Prefs.K_AC_LIVE, Prefs.K_AC_MSG};
    static final String[] EKEY = {Prefs.K_AE_CALL, Prefs.K_AE_PAY, Prefs.K_AE_CHG, Prefs.K_AE_DL, Prefs.K_AE_TIMER, Prefs.K_AE_DEV, Prefs.K_AE_NAV, Prefs.K_AE_LIVE, Prefs.K_AE_MSG};

    static final int COMPACT = 5, EXPANDED = 5;

    static final String[] CNAMES = {"Classic", "Capsule", "Track", "Focus", "Signal"};
    static final String[] CDESC = {
        "The original pill: the icon on the left, the time or the text on the right.",
        "A tonal tile with the icon on the left and a filled accent capsule on the right that holds the number.",
        "The icon, the title with a slim progress line under it, and the number at the end.",
        "Just the number (or the title), centred and bold, with its small icon.",
        "The icon on the left and a little live gauge on the right: voice bars, battery cells, a ring or pulsing dots."};
    static final String[] ENAMES = {"Classic", "Bento", "Hero", "Strip", "Stats"};
    static final String[] EDESC = {
        "The original card.",
        "Flat tonal tiles: a big icon tile beside the title tile, a number tile with a progress line, then roomy buttons.",
        "One big centred number with the title above it and the buttons below. Calm and easy to read from far away.",
        "One slim row with a thin progress line: the shortest card there is.",
        "The title on top and up to four flat tiles of facts (speed, time left, temperature, duration...), then the buttons."};

    /** Which activity an island event belongs to, or -1 when it has no design of its own (music and the idle clock have theirs). */
    static int of(int eventType) {
        switch (eventType) {
            case IslandEvent.CALL: return CALL;
            case IslandEvent.PAYMENT: return PAYMENT;
            case IslandEvent.CHARGE: return CHARGE;
            case IslandEvent.PROGRESS: return DOWNLOAD;
            case IslandEvent.TIMER: return TIMER;
            case IslandEvent.SYSTEM: return DEVICE;
            case IslandEvent.NAV: return NAV;
            case IslandEvent.LIVE: return LIVE;
            case IslandEvent.NOTIF: return MESSAGE;
            default: return -1;
        }
    }

    /**
     * Whether this activity can ever be seen as the little pill. Payments and messages are banners: they open as a card and leave,
     * so only their expanded design matters. Charging and devices become a pill while "Keep ... on the island" is on.
     */
    static boolean hasCompact(int act) { return act != PAYMENT && act != MESSAGE; }

    static int clamp(int v, int count) { return v < 0 ? 0 : (v >= count ? count - 1 : v); }

    /** "1h 05m", "12 min", "45 s": a length of time in words short enough for a pill. */
    static String span(long ms) {
        if (ms < 0) ms = 0;
        long s = (ms + 500) / 1000;
        if (s < 60) return s + " s";
        long m = s / 60;
        if (m < 60) return m + " min";
        long h = m / 60;
        m = m % 60;
        return h + "h " + (m < 10 ? "0" : "") + m + "m";
    }

    /**
     * How long a download still needs, judged from its own pace: it went from {@code p0} to {@code p} percent in {@code elapsedMs}.
     * @return the time left in ms, or -1 when there is not enough to go on yet (no progress made, or under three seconds of it)
     */
    static long eta(int p0, int p, long elapsedMs) {
        if (p0 < 0 || p <= p0 || p >= 100 || elapsedMs < 3000L) return -1L;
        return Math.round(elapsedMs * (100.0 - p) / (p - p0));
    }

    /** Charger kind in words for BatteryManager.BATTERY_PLUGGED_* (1 AC, 2 USB, 4 wireless, 8 dock). */
    static String plug(int plugged, int currentMa, int voltMv) {
        if (plugged == 4) return "Wireless";
        if (plugged == 2) return "USB";
        if (plugged == 1) {
            long mw = currentMa == Integer.MIN_VALUE || voltMv <= 0 ? 0 : (long) Math.abs(currentMa) * voltMv / 1000L;
            return mw >= 10000L ? "Fast" : "AC";   // measured at the battery, so 10 W here is a 15 W+ charger
        }
        return plugged > 0 ? "Dock" : "";
    }

    /** Charging power in watts from the battery's current (mA) and voltage (mV): "9.5 W", or null when either is unknown. */
    static String watts(int currentMa, int voltMv) {
        if (currentMa == Integer.MIN_VALUE || voltMv <= 0) return null;
        double w = Math.abs((double) currentMa) * voltMv / 1_000_000.0;
        if (w < 0.1) return null;
        return (w < 10 ? String.format(java.util.Locale.US, "%.1f", w) : String.valueOf(Math.round(w))) + " W";
    }

    /** A temperature in tenths of a degree Celsius as "31.5\u00B0C" (null when unknown). */
    static String temp(int c10) {
        if (c10 < 0) return null;
        return (c10 / 10) + "." + (c10 % 10) + "\u00B0C";
    }
}
