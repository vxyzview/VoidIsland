package com.vxyzview.voidisland;

/**
 * Guesses how long a progress bar still needs, from how fast it has been moving. Pure Java (no Android types) so the unit
 * checks cover it. Feed it every reading with {@link #add}; ask {@link #remainingMs} for the guess (-1 = nothing to go on yet).
 */
final class Pace {
    /** Less watched time than this, or fewer steps: no guess yet, a first step says nothing about speed. */
    static final long MIN_WATCH_MS = 2000L;
    static final int MIN_STEPS = 2;
    static final int SEC = 0, MIN = 1, HOUR = 2;

    private int last = -1, steps;
    private long lastAt, firstAt;
    private float rate;   // percent per millisecond, smoothed

    /** One reading of the bar (0..100) at wall-clock time {@code now}. A reading that goes backwards starts the guess over. */
    void add(int pct, long now) {
        if (pct < 0) return;
        if (pct > 100) pct = 100;
        if (last < 0 || pct < last || now < lastAt) {
            last = pct;
            lastAt = firstAt = now;
            rate = 0f;
            steps = 0;
            return;
        }
        if (pct == last) return;   // only a change in the reading says anything about speed
        float inst = (pct - last) / (float) Math.max(1L, now - lastAt);
        rate = steps == 0 ? inst : 0.7f * rate + 0.3f * inst;
        steps++;
        last = pct;
        lastAt = now;
    }

    /** Milliseconds still needed at the speed seen so far; -1 when unknown (too early, finished, or not moving). */
    long remainingMs(long now) {
        if (last < 0 || last >= 100 || steps < MIN_STEPS || rate <= 0f || lastAt - firstAt < MIN_WATCH_MS) return -1L;
        float step = 1f / rate;                  // ms one percent takes at this speed
        float left = (100 - last) * step;
        long stall = now - lastAt;
        if (stall > step) left += stall - step;  // stopped for longer than a step takes: the guess grows with the wait
        return Math.round(Math.min(left, 3.6e8f));
    }

    /** A duration for people: {SEC, s} under a minute (in steps of 5), {MIN, m} under an hour, else {HOUR, h, m}. */
    static int[] human(long ms) {
        long s = Math.max(1L, (ms + 999L) / 1000L);
        long r = ((s + 4L) / 5L) * 5L;
        if (r < 60L) return new int[]{SEC, (int) r, 0};
        long m = (s + 59L) / 60L;
        if (m < 60L) return new int[]{MIN, (int) m, 0};
        return new int[]{HOUR, (int) Math.min(99L, m / 60L), (int) (m % 60L)};
    }
}
