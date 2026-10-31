package com.vxyzview.voidisland;

import android.os.SystemClock;
import android.util.Log;

/**
 * Optional timing log, off unless you turn it on from a computer:
 *   adb shell setprop log.tag.VoidPerf DEBUG      (and  ... VERBOSE  to turn it off again with  setprop log.tag.VoidPerf INFO)
 *   adb logcat -s VoidPerf
 * Costs one clock read per timed section when off. Main thread only. Prints count / average / worst every 40 samples.
 */
final class Perf {
    private Perf() {}

    private static final String TAG = "VoidPerf";
    private static boolean on;
    private static long checkedAt = -100000L;

    /** Named stopwatch with a running summary. */
    static final class Stat {
        final String name;
        long n, sum, max;
        Stat(String name) { this.name = name; }
    }

    static final Stat PRESENT = new Stat("present (build+measure card)");
    static final Stat FRAME = new Stat("frame (springs+visuals)");
    static final Stat ART = new Stat("artwork (scale+accent)");
    static final Stat PROBE = new Stat("probe (RemoteViews inflate)");

    private static boolean enabled() {
        long now = SystemClock.uptimeMillis();
        if (now - checkedAt > 5000L) {
            checkedAt = now;
            try { on = Log.isLoggable(TAG, Log.DEBUG); } catch (Throwable t) { on = false; }
        }
        return on;
    }

    /** @return a token for {@link #end}, or 0 when logging is off. */
    static long begin() { return enabled() ? System.nanoTime() : 0L; }

    static void end(Stat s, long token) {
        if (token == 0L) return;
        add(s, System.nanoTime() - token);
    }

    /** Report a duration measured elsewhere (e.g. on a worker thread); call on the main thread. */
    static void record(Stat s, long ns) {
        if (ns > 0 && enabled()) add(s, ns);
    }

    private static void add(Stat s, long ns) {
        s.n++;
        s.sum += ns;
        if (ns > s.max) s.max = ns;
        if (s.n >= 40) {
            Log.d(TAG, String.format(java.util.Locale.US, "%s: n=%d avg=%.2fms worst=%.2fms", s.name, s.n, s.sum / s.n / 1e6, s.max / 1e6));
            s.n = 0; s.sum = 0; s.max = 0;
        }
    }
}
