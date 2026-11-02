package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the lock-screen privacy rules (pure Java: runs on a plain JVM, no Android). */
final class PrivacyCheck {
    private PrivacyCheck() {}

    static void run() {
        final int PUB = Privacy.VIS_PUBLIC, PRIV = Privacy.VIS_PRIVATE, SEC = Privacy.VIS_SECRET;

        // not behind a PIN / pattern / password: Android's own lock screen hides nothing, so neither does the island
        ok(Privacy.onLockScreen(SEC, false, true, true) == Privacy.SHOW, "privacy: a swipe-only lock screen hides nothing, even a secret notification");
        ok(Privacy.onLockScreen(PRIV, false, true, false) == Privacy.SHOW, "privacy: unlocked phone shows private notifications in full");

        // secure lock screen
        ok(Privacy.onLockScreen(SEC, true, false, false) == Privacy.HIDE, "privacy: a secret notification never shows over a secure lock screen");
        ok(Privacy.onLockScreen(PUB, true, true, false) == Privacy.SHOW, "privacy: a public notification shows in full");
        ok(Privacy.onLockScreen(PRIV, true, true, false) == Privacy.REDACT, "privacy: a private notification is redacted when the person hides sensitive content");
        ok(Privacy.onLockScreen(PRIV, true, false, false) == Privacy.SHOW, "privacy: a private notification shows in full when the person allows sensitive content");
        ok(Privacy.onLockScreen(PUB, true, false, true) == Privacy.HIDE && Privacy.onLockScreen(PRIV, true, false, true) == Privacy.HIDE,
            "privacy: 'no notifications on the lock screen' hides everything");
        ok(Privacy.onLockScreen(-5, true, false, false) == Privacy.HIDE, "privacy: an out-of-range visibility below secret counts as secret");

        // channel override can only make a notification less visible
        ok(Privacy.effectiveVisibility(PUB, Privacy.NO_OVERRIDE) == PUB, "privacy: no channel override keeps the notification's own visibility");
        ok(Privacy.effectiveVisibility(PUB, PRIV) == PRIV && Privacy.effectiveVisibility(PUB, SEC) == SEC, "privacy: a stricter channel wins");
        ok(Privacy.effectiveVisibility(SEC, PUB) == SEC && Privacy.effectiveVisibility(PRIV, PUB) == PRIV, "privacy: a looser channel never loosens the notification");
        ok(Privacy.onLockScreen(Privacy.effectiveVisibility(PUB, SEC), true, false, false) == Privacy.HIDE, "privacy: a secret channel hides a public notification");
    }
}
