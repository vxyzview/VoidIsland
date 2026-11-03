package com.vxyzview.voidisland;

/**
 * The yes / no decisions the classifier makes about a plain notification, as pure Java (no Android types) so the unit
 * checks cover them: is it a fresh, dismissible message-like notification that deserves a banner at all?
 */
final class Gate {
    private Gate() {}

    /** {@link #reject} result for a notification that is not a banner by design (not a drop worth explaining). */
    static final int SILENT = -1;

    /**
     * @param initial         true while scanning what was already showing when the island connected (only live things count)
     * @param ongoing         the notification is an ongoing one (a service, a player...)
     * @param hasContent      it has a title or some text
     * @param importance      the channel's importance, or -1 when unknown
     * @param minImportance   the lowest importance that deserves a banner (NotificationManager.IMPORTANCE_DEFAULT)
     * @param summaryOnlyAlert the group only alerts through its summary, so this child must stay quiet
     * @param category        Notification.category, may be null
     */
    static boolean banner(boolean initial, boolean ongoing, boolean hasContent, int importance, int minImportance,
                          boolean summaryOnlyAlert, String category) {
        return reject(initial, ongoing, hasContent, importance, minImportance, summaryOnlyAlert, category) == 0;
    }

    /**
     * Same decision as {@link #banner}, with the reason: 0 when it deserves a banner, {@link #SILENT} when it never would
     * (already showing, ongoing, or a child of a summary-only group), else the {@link Trace} reason it was kept off.
     */
    static int reject(boolean initial, boolean ongoing, boolean hasContent, int importance, int minImportance,
                      boolean summaryOnlyAlert, String category) {
        if (initial || ongoing) return SILENT;
        if (!hasContent) return Trace.NO_CONTENT;
        if (importance >= 0 && importance < minImportance) return Trace.LOW_IMPORTANCE;
        if (summaryOnlyAlert) return SILENT;
        return quietCategory(category) ? Trace.HOUSEKEEPING : 0;
    }

    /** Categories that are the system's or a service's housekeeping, never something to announce (values of Notification.CATEGORY_*). */
    static boolean quietCategory(String category) {
        return "service".equals(category) || "sys".equals(category) || "status".equals(category) || "progress".equals(category);
    }
}
