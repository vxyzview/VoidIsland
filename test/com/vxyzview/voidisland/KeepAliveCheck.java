package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the KeepAlive logic (pure Java: runs on a plain JVM, no Android). */
final class KeepAliveCheck {
    private KeepAliveCheck() {}

    static void run() {
        // --- KeepAlive: which phones get the background-running help
        ok(KeepAlive.aggressive("Xiaomi") && KeepAlive.aggressive("POCO") && KeepAlive.aggressive("realme") && KeepAlive.aggressive("OnePlus"), "keepalive: Xiaomi family and Oppo family detected");
        ok(KeepAlive.aggressive("vivo") && KeepAlive.aggressive("HUAWEI") && KeepAlive.aggressive("HONOR") && KeepAlive.aggressive("samsung"), "keepalive: Vivo, Huawei, Honor and Samsung detected");
        ok(!KeepAlive.aggressive("Google") && !KeepAlive.aggressive("motorola") && !KeepAlive.aggressive(null) && !KeepAlive.aggressive(""), "keepalive: stock-like ROMs get no extra prompt");
        ok("Xiaomi".equals(KeepAlive.family("Redmi")) && KeepAlive.hint("Google").length() == 0 && KeepAlive.hint("Xiaomi").length() > 0, "keepalive: brand name and hint text");
    }
}
