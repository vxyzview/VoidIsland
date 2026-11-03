package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for how Android's "why did the process end" codes are explained (pure Java; the codes are ApplicationExitInfo.REASON_*). */
final class ExitReasonCheck {
    private ExitReasonCheck() {}

    static void run() {
        ok(ExitReason.systemStopped(3) && ExitReason.systemStopped(9) && ExitReason.systemStopped(14) && ExitReason.systemStopped(13) && ExitReason.systemStopped(2),
            "exit: low memory, resource use, freezer, 'other' and being signalled are Android stopping the island");
        ok(!ExitReason.systemStopped(10) && !ExitReason.systemStopped(11) && !ExitReason.systemStopped(1) && !ExitReason.systemStopped(16),
            "exit: you closing it, an update or a self-restart are not Android stopping it");
        ok(ExitReason.text(10).contains("you") && ExitReason.hint(10).toLowerCase().contains("accessibility"), "exit: a force stop tells you to switch the accessibility service back on");
        ok(ExitReason.hint(3).toLowerCase().contains("battery") && ExitReason.hint(14).toLowerCase().contains("battery"), "exit: system stops point at the battery setting");
        ok(ExitReason.hint(4).toLowerCase().contains("log") && ExitReason.hint(6).toLowerCase().contains("log"), "exit: a crash or a freeze points at the diagnostic log");
        boolean all = true;
        for (int r = 0; r <= 16; r++) all &= ExitReason.text(r).length() > 0;
        ok(all, "exit: every known code has a text");
        ok(ExitReason.text(99).length() > 0 && ExitReason.hint(99).length() >= 0, "exit: an unknown future code still reads sensibly");
    }
}
