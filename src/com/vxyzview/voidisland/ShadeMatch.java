package com.vxyzview.voidisland;

import java.util.Locale;

/** Does a SystemUI accessibility event look like the notification shade / Quick Settings panel? (pure Java) */
final class ShadeMatch {
    private ShadeMatch() {}

    private static final String[] TEXT = {
        "shade", "quick", "panel", "notif", "cepat", "r\u00e1pid", "rapide", "schnell", "rapid", "\u5feb\u901f", "\u901a\u77e5",
        "\u0431\u044b\u0441\u0442\u0440", "\ube60\ub978", "\u30af\u30a4\u30c3\u30af"};
    private static final String[] CLASS = {
        "notificationpanel", "notificationshade", "quicksettings", "qspanel", "quickstatus", "statusbarwindow", "shadewindow"};

    static boolean panel(String text, String cls) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT), c = cls == null ? "" : cls.toLowerCase(Locale.ROOT);
        for (String w : TEXT) if (t.contains(w)) return true;
        for (String w : CLASS) if (c.contains(w)) return true;
        return false;
    }
}
