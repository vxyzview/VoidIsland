package com.vxyzview.voidisland;

/** One-shot notifications belong to the island: the original is removed so the banner is not shown twice. */
final class Absorb {
    private Absorb() {}

    /** Text banners (messages, payments) are absorbed; live things (calls, timers, nav, progress) keep their own notification. */
    static boolean applies(int type, boolean live, boolean initial) {
        return !initial && !live && (type == IslandEvent.NOTIF || type == IslandEvent.PAYMENT);
    }

    /** How long the original rests: the banner's life plus a margin, never under 6s (the heads-up may still be animating). */
    static long delayMs(long bannerMs) { return Math.max(6000, bannerMs + 3000); }
}
