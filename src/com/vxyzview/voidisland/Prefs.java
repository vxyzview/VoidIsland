package com.vxyzview.voidisland;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

final class Prefs {
    private Prefs() {}

    // behaviour
    static final String K_ON = "on", K_MEDIA = "f_media", K_NOTIF = "f_notif", K_CALL = "f_call", K_PAY = "f_pay",
        K_CHG = "f_chg", K_SYS = "f_sys", K_FULL = "hide_full", K_INAPP = "hide_inapp", K_HIDEAPP = "hide_app",
        K_HAPTIC = "haptic", K_DUR = "dur", K_ABSORB = "absorb", K_BLOCK = "blocked", K_QS = "qs", K_LOCK = "lock_show", K_POKE = "poke",
        K_CLOCK = "f_clock", K_CLOCKSTYLE = "clock_style", K_ECLOCKSTYLE = "clock_style_e", K_LYRICS = "f_lyric", K_THEME = "app_theme",
        K_LIVEACT = "f_live", K_KEEP = "keep_apps", K_QUIET = "quiet_on", K_QFROM = "quiet_from", K_QTO = "quiet_to",
        K_QAPPS = "quiet_apps", K_NOGROUP = "no_group", K_ONEPERCHAT = "one_per_chat",
        K_LYROFF = "lyric_off", K_LYRSIZE = "lyric_size",
        K_LIVE_RIDE = "live_ride", K_LIVE_FOOD = "live_food", K_LIVE_SPORT = "live_sport";
    // shape + size
    static final String K_SHAPE = "shape", K_ESHAPE = "e_shape", K_CW = "c_w", K_CH = "c_h", K_CR = "c_r", K_CPAD = "c_pad", K_GLYPH = "glyph",
        K_EW = "e_w", K_ER = "e_r", K_PADX = "pad_x", K_PADY = "pad_y", K_TS = "t_scale";
    // margins
    static final String K_Y = "y_off", K_X = "x_off", K_DROP = "m_drop", K_SIDE = "m_side";
    // colours
    static final String K_COLMODE = "col_mode", K_BG = "col_bg", K_BGA = "bg_alpha", K_TXTMODE = "txt_mode", K_TXT = "col_txt",
        K_ACCMODE = "acc_mode", K_ACC = "col_acc", K_BW = "border_w", K_BCOL = "border_col";
    // motion
    static final String K_SPEED = "speed", K_BOUNCE = "bounce";

    /** Removing originals from the shade is opt-in: a banner you miss is not waiting in the shade, so it starts off. */
    static final boolean ABSORB_DEF = false;

    /** True while the "Remove banners from the shade?" dialog is open: the switch is already saved, but nothing may be removed yet. */
    static volatile boolean absorbUnconfirmed;

    private static volatile SharedPreferences cached;

    static SharedPreferences sp(Context c) {
        SharedPreferences s = cached;
        if (s == null) {
            s = c.getApplicationContext().getSharedPreferences("void_island", Context.MODE_PRIVATE);
            cached = s;
        }
        return s;
    }

    /** Membership test on a string-set pref without copying the set (hot path: runs for every notification). */
    static boolean has(Context c, String key, String value) {
        if (value == null) return false;
        Set<String> s = sp(c).getStringSet(key, null);   // the live set: read only
        return s != null && s.contains(value);
    }

    /** The stored set itself, read only and never null: for hot paths that must not copy it. Do not modify the result. */
    static Set<String> view(Context c, String key) {
        Set<String> s = sp(c).getStringSet(key, null);
        return s == null ? java.util.Collections.<String>emptySet() : s;
    }

    static boolean b(Context c, String k, boolean def) { return sp(c).getBoolean(k, def); }

    static int i(Context c, String k, int def) { return sp(c).getInt(k, def); }

    static Set<String> set(Context c, String key) { return new HashSet<String>(sp(c).getStringSet(key, new HashSet<String>())); }

    static void setSet(Context c, String key, Set<String> s) { sp(c).edit().putStringSet(key, s).apply(); }

    static Set<String> blocked(Context c) { return set(c, K_BLOCK); }

    static void setBlocked(Context c, Set<String> s) { setSet(c, K_BLOCK, s); }

    /** Every key that changes how the island LOOKS (sizes, shape, colours...). Used for presets and reset. */
    static final String[] STYLE_KEYS = {K_SHAPE, K_ESHAPE, K_CW, K_CH, K_CR, K_CPAD, K_GLYPH, K_EW, K_ER, K_PADX, K_PADY, K_TS, K_Y, K_X,
        K_DROP, K_SIDE, K_COLMODE, K_BG, K_BGA, K_TXTMODE, K_TXT, K_ACCMODE, K_ACC, K_BW, K_BCOL, K_SPEED, K_BOUNCE};
}
