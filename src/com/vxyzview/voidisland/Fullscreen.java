package com.vxyzview.voidisland;

/** Pure rules for "hide the island only while an app is fullscreen" (video, game, reader), so the unit checks cover them. */
final class Fullscreen {
    private Fullscreen() {}

    /** Going fullscreen waits a little (apps flicker the bars while they switch); coming back is quick. */
    static final long ENTER_MS = 350L, LEAVE_MS = 120L;

    /** True when the island must stay out of the way: the switch is on and an app is fullscreen. */
    static boolean hidden(boolean switchOn, boolean fullscreen) {
        return switchOn && fullscreen;
    }

    static long delay(boolean entering) { return entering ? ENTER_MS : LEAVE_MS; }

    /** Android 10 and older: SYSTEM_UI_FLAG_FULLSCREEN (0x4) in the system UI visibility means the status bar is hidden. */
    static boolean legacyFlag(int systemUiVisibility) { return (systemUiVisibility & 0x4) != 0; }
}
