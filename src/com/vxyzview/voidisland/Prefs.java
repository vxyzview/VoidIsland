package com.vxyzview.voidisland;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

final class Prefs {
    private Prefs() {}

    // behaviour
    static final String K_ON = "on", K_MEDIA = "f_media", K_NOTIF = "f_notif", K_CALL = "f_call", K_PAY = "f_pay",
        K_CHG = "f_chg", K_SYS = "f_sys", K_TOAST = "f_toast", K_LAND = "hide_land", K_INAPP = "hide_inapp", K_HIDEAPP = "hide_app",
        K_HAPTIC = "haptic", K_DUR = "dur", K_BLOCK = "blocked", K_QS = "qs", K_LOCK = "lock_show", K_POKE = "poke",
        K_CLOCK = "f_clock";
    // shape + size
    static final String K_SHAPE = "shape", K_CW = "c_w", K_CH = "c_h", K_CR = "c_r", K_CPAD = "c_pad", K_GLYPH = "glyph",
        K_EW = "e_w", K_ER = "e_r", K_PADX = "pad_x", K_PADY = "pad_y", K_TS = "t_scale";
    // margins
    static final String K_Y = "y_off", K_X = "x_off", K_DROP = "m_drop", K_SIDE = "m_side";
    // colours
    static final String K_COLMODE = "col_mode", K_BG = "col_bg", K_BGA = "bg_alpha", K_TXTMODE = "txt_mode", K_TXT = "col_txt",
        K_ACCMODE = "acc_mode", K_ACC = "col_acc", K_BW = "border_w", K_BCOL = "border_col";
    // motion
    static final String K_SPEED = "speed", K_BOUNCE = "bounce";

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("void_island", Context.MODE_PRIVATE);
    }

    static boolean b(Context c, String k, boolean def) { return sp(c).getBoolean(k, def); }

    static int i(Context c, String k, int def) { return sp(c).getInt(k, def); }

    static Set<String> blocked(Context c) { return new HashSet<String>(sp(c).getStringSet(K_BLOCK, new HashSet<String>())); }

    static void setBlocked(Context c, Set<String> s) { sp(c).edit().putStringSet(K_BLOCK, s).apply(); }

    /** Every key that changes how the island LOOKS (sizes, shape, colours...). Used for presets and reset. */
    static final String[] STYLE_KEYS = {K_SHAPE, K_CW, K_CH, K_CR, K_CPAD, K_GLYPH, K_EW, K_ER, K_PADX, K_PADY, K_TS, K_Y, K_X,
        K_DROP, K_SIDE, K_COLMODE, K_BG, K_BGA, K_TXTMODE, K_TXT, K_ACCMODE, K_ACC, K_BW, K_BCOL, K_SPEED, K_BOUNCE};
}
