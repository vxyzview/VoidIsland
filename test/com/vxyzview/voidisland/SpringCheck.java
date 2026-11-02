package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the Spring logic (pure Java: runs on a plain JVM, no Android). */
final class SpringCheck {
    private SpringCheck() {}

    static void run() {
        // --- Spring: expanding width 150 -> 340 should overshoot slightly, settle < 0.9s, never explode
        Spring s = new Spring(); s.snap(150f); s.eps = 0.3f; s.tune(330f, 0.60f); s.to(340f);
        float peak = 0, t = 0; int frames = 0;
        while (s.step(1f / 60f) && frames < 600) { frames++; t += 1f / 60f; peak = Math.max(peak, s.value); }
        System.out.printf("expand: settled in %.2fs (%d frames), peak %.1f%n", t, frames, peak);
        ok(peak > 340f && peak < 340f + 0.14f * 190f, "expand overshoots a little (bounce) but < 14%");
        ok(t < 0.95f, "expand settles under 0.95s");
        ok(s.value == 340f, "expand lands exactly on target");

        // collapse should be snappier, small bounce
        Spring c = new Spring(); c.snap(340f); c.tune(420f, 0.80f); c.to(150f);
        float low = 1e9f; t = 0; frames = 0;
        while (c.step(1f / 60f) && frames < 600) { frames++; t += 1f / 60f; low = Math.min(low, c.value); }
        System.out.printf("collapse: settled in %.2fs, trough %.1f%n", t, low);
        ok(t < 0.8f && low > 150f - 0.05f * 190f, "collapse settles < 0.8s, bounce < 5%");

        // stability with a janky 50ms frame
        Spring j = new Spring(); j.snap(0f); j.tune(430f, 0.8f); j.to(100f);
        int n = 0; while (j.step(0.05f) && n < 200) n++;
        ok(j.value == 100f && n < 60, "stable with 50ms frame hitches");

        // --- system animation scale: springs follow it, and "Remove animations" (scale 0) means no motion
        float keepRate = Spring.rate;
        Spring.rate = 2f;   // animation scale 0.5x: twice as fast
        Spring fast = new Spring(); fast.snap(150f); fast.tune(330f, 0.60f); fast.to(340f);
        float tf = 0; int ff = 0;
        while (fast.step(1f / 60f) && ff < 600) { ff++; tf += 1f / 60f; }
        ok(tf < t && fast.value == 340f, "animation scale 0.5x settles sooner and still lands exactly");
        Spring.rate = 0.5f;   // animation scale 2x: slower but stable
        Spring slow = new Spring(); slow.snap(150f); slow.tune(330f, 0.60f); slow.to(340f);
        int fs = 0;
        while (slow.step(1f / 60f) && fs < 2000) fs++;
        ok(slow.value == 340f && fs > frames, "animation scale 2x is slower but lands exactly");
        Spring.rate = Float.POSITIVE_INFINITY;   // animation scale 0
        Spring none = new Spring(); none.snap(150f); none.tune(330f, 0.60f); none.to(340f);
        boolean still = none.step(1f / 60f);
        ok(!still && none.value == 340f && none.velocity == 0f, "animation scale 0 jumps straight to the target");
        Spring.rate = keepRate;
    }
}
