package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for live-activity detection (pure Java). */
final class LiveCheck {
    private LiveCheck() {}

    static void run() {
        final int ONGOING = 2;   // Notification.FLAG_ONGOING_EVENT
        ok(Live.promoted(ONGOING | 262144, null), "live: a notification Android 16 promoted (FLAG_PROMOTED_ONGOING) is a live activity");
        ok(Live.promoted(ONGOING, "android.app.Notification$ProgressStyle"), "live: an app that uses ProgressStyle is a live activity");
        ok(!Live.promoted(ONGOING, "android.app.Notification$MessagingStyle") && !Live.promoted(ONGOING, null) && !Live.promoted(0, ""), "live: other styles and plain ongoing notifications are not");
        ok(Live.promoted(ONGOING | 262144, "android.app.Notification$BigTextStyle"), "live: the promotion flag wins whatever the style");
        ok(Live.accent(Live.PROMOTED) != Live.accent(Live.NONE) && Live.label(Live.PROMOTED).length() == 0, "live: a promoted activity has its own colour and no made-up kind label");
        ok(Live.kind("com.whatsapp") == Live.NONE, "live: an app nobody listed is still not a ride, order or score by name");
    }
}
