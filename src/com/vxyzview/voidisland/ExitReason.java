package com.vxyzview.voidisland;

/**
 * Explains Android's "why did this process end" codes (the values of ApplicationExitInfo.REASON_*, Android 11+) in plain words,
 * for Help > "Why did the island stop?". Pure Java so the unit checks cover it.
 */
final class ExitReason {
    private ExitReason() {}

    /** True when Android itself ended the process (memory, battery limits, the vendor's cleaner), as opposed to you or the app. */
    static boolean systemStopped(int reason) {
        return reason == 2 || reason == 3 || reason == 9 || reason == 13 || reason == 14;
    }

    static String text(int reason) {
        switch (reason) {
            case 1: return "It restarted itself";
            case 2: return "Android ended it";
            case 3: return "Android freed memory";
            case 4: case 5: return "It crashed";
            case 6: return "It stopped responding";
            case 7: return "It failed to start";
            case 8: return "A permission changed";
            case 9: return "Android stopped it for using too many resources";
            case 10: return "Closed by you (swiped away or Force stop)";
            case 11: return "Stopped by a user or profile switch";
            case 12: return "Something it depends on stopped";
            case 13: return "Android stopped it (no reason given)";
            case 14: return "Android froze it in the background";
            case 15: return "The app's state changed (enabled or disabled)";
            case 16: return "The app was updated";
            default: return "Ended (reason " + reason + ")";
        }
    }

    /** What to do about it; empty when nothing is needed. */
    static String hint(int reason) {
        switch (reason) {
            case 2: case 3: case 9: case 13: case 14:
                return "Set VoidIsland's battery use to Unrestricted (Behavior > Keep the island running)";
            case 10: return "After a Force stop, switch the accessibility service back on in Android settings";
            case 4: case 5: case 6: case 7: return "Copy the diagnostic log into a bug report";
            case 8: return "Check Display over other apps and Notification access";
            default: return "";
        }
    }
}
