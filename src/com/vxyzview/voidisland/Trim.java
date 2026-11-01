package com.vxyzview.voidisland;

/** Memory-pressure levels (ComponentCallbacks2.TRIM_MEMORY_*) judged in pure Java, so the unit checks cover them. */
final class Trim {
    private Trim() {}

    static final int RUNNING_LOW = 10, UI_HIDDEN = 20, BACKGROUND = 40, COMPLETE = 80;

    /**
     * True when the system is really short on memory. UI_HIDDEN alone is not a shortage (it fires whenever the settings
     * screen is closed), so caches are kept for it.
     */
    static boolean shortage(int level) {
        return (level >= RUNNING_LOW && level < UI_HIDDEN) || level >= BACKGROUND;
    }

    /** How many fetched lyric sheets survive a trim: the freshest few normally, none when the process is next in line to be killed. */
    static int keepSongs(int level) {
        return level >= COMPLETE ? 0 : 4;
    }
}
