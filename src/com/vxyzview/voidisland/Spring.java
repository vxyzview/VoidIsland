package com.vxyzview.voidisland;

/** Tiny damped spring. Stepped in fixed sub-steps so it stays stable at any frame time. No allocations. */
final class Spring {
    float value, velocity, target;
    float k = 300f, c = 30f, eps = 0.3f;
    boolean active;

    /**
     * System animation speed shared by every spring: 1 = normal, 2 = twice as fast (animation scale 0.5x),
     * POSITIVE_INFINITY = no animation at all (scale 0 / "Remove animations"): springs jump straight to their target.
     */
    static float rate = 1f;

    /** stiffness k, damping ratio zeta (1 = critical, below 1 = bouncy). */
    void tune(float stiffness, float zeta) {
        k = stiffness;
        c = 2f * zeta * (float) Math.sqrt(stiffness);
    }

    void snap(float v) { value = v; target = v; velocity = 0f; active = false; }

    void to(float t) { target = t; active = true; }

    /** @return true while still moving */
    boolean step(float dt) {
        if (!active) return false;
        if (rate == Float.POSITIVE_INFINITY) { value = target; velocity = 0f; active = false; return false; }
        dt *= rate;
        while (dt > 0f) {
            float h = dt > 0.0041667f ? 0.0041667f : dt;
            dt -= h;
            velocity += (-k * (value - target) - c * velocity) * h;
            value += velocity * h;
        }
        if (Math.abs(value - target) < eps && Math.abs(velocity) < eps * 12f) {
            value = target; velocity = 0f; active = false;
        }
        return active;
    }
}
