package com.vxyzview.voidisland;

/**
 * Lock-screen privacy: what the island may put on screen for a notification while the phone is locked behind a PIN,
 * pattern or password. It follows what Android's own lock screen would do, so the island is never more talkative
 * than the system. Pure Java (no Android types) so the unit checks cover it.
 */
final class Privacy {
    private Privacy() {}

    /** The island may show the banner as it is. */
    static final int SHOW = 0;
    /** The banner may show, but its content must be replaced by a generic line. */
    static final int REDACT = 1;
    /** The banner must not show at all. */
    static final int HIDE = 2;

    /** Same values as {@code Notification.VISIBILITY_*}. */
    static final int VIS_SECRET = -1, VIS_PRIVATE = 0, VIS_PUBLIC = 1;
    /** Same value as {@code NotificationManager.VISIBILITY_NO_OVERRIDE}: the channel has no opinion. */
    static final int NO_OVERRIDE = -1000;

    /** The notification's own visibility, made stricter (never looser) by its channel's lock-screen setting. */
    static int effectiveVisibility(int notification, int channel) {
        if (channel == NO_OVERRIDE) return notification;
        return Math.min(notification, channel);
    }

    /**
     * Whether an island event of this kind carries text from somebody's notification, so it follows the rules below: message and
     * payment banners, and the pills for navigation, timers, downloads and live activities (a route, a driver, a label).
     * Calls and music keep the system's own lock-screen behaviour, and the island's own pills carry nobody's text.
     */
    static boolean guarded(int type) {
        return type == IslandEvent.NOTIF || type == IslandEvent.PAYMENT || type == IslandEvent.NAV
            || type == IslandEvent.TIMER || type == IslandEvent.PROGRESS || type == IslandEvent.LIVE;
    }

    /**
     * @param visibility    effective {@code Notification.VISIBILITY_*} of the notification
     * @param secureLocked  the phone is locked behind a PIN / pattern / password (a plain swipe lock hides nothing)
     * @param hideSensitive the user chose "hide sensitive content" for the lock screen
     * @param showNone      the user chose to show no notifications on the lock screen at all
     */
    static int onLockScreen(int visibility, boolean secureLocked, boolean hideSensitive, boolean showNone) {
        if (!secureLocked) return SHOW;
        if (showNone || visibility <= VIS_SECRET) return HIDE;
        if (visibility < VIS_PUBLIC && hideSensitive) return REDACT;
        return SHOW;
    }
}
