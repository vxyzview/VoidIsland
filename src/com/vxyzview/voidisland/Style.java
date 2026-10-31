package com.vxyzview.voidisland;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything about how the island looks and behaves, read once from preferences. Sizes are dp, colours are ARGB. */
final class Style {
    static final int PILL = 0, NOTCH = 1, BOX = 2;
    static final int QS_UNDER = 0, QS_HIDE = 1, QS_TOP = 2;

    // shape + size (compact and expanded are set independently)
    int cShape = PILL, eShape = PILL;
    int cW = 150, cH = 32, cR = 16, cPad = 9, glyph = 100;
    int eW = 340, eR = 38, padX = 20, padY = 16, tScale = 100;
    // margins (mDrop < 0 = automatic)
    int mTop = 0, mX = 0, mDrop = -1, mSide = 12;
    // colours
    int colMode = 0, bgCustom = 0xFF000000, bgAlpha = 100, textMode = 0, textCustom = 0xFFFFFFFF;
    int accMode = 0, accCustom = 0xFFB388FF, borderW = 0, borderCol = 0x66FFFFFF;
    // motion
    int speed = 100, bounce = 80;
    // behaviour
    int qs = QS_UNDER;
    boolean lockShow = false, haptic = true;

    // derived
    int bg = 0xFF000000, text = 0xFFFFFFFF, sub = 0xB3FFFFFF, btn = 0x2EFFFFFF, tile = 0x24FFFFFF, accent = 0xFFB388FF;
    boolean accentOverride = false;
    // Material You accent set (only filled in when accMode == 1). The island itself stays whatever colour it is (black / AMOLED);
    // only the accents -- buttons, play button, bars, rings, badges, icons -- take the wallpaper's tonal palette, always the dark-scheme tones.
    boolean m3 = false;
    int onAccent = 0xFF381E72, accCont = 0xFF4F378B, onAccCont = 0xFFEADDFF, secCont = 0xFF4A4458, onSecCont = 0xFFE8DEF8;

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    static Style load(Context c) {
        SharedPreferences sp = Prefs.sp(c);
        Style s = new Style();
        s.cShape = clamp(sp.getInt(Prefs.K_SHAPE, s.cShape), 0, 2);
        int es = sp.getInt(Prefs.K_ESHAPE, -1);            // never set yet: expanded follows the compact shape
        s.eShape = es >= 0 && es <= 2 ? es : s.cShape;
        s.cW = clamp(sp.getInt(Prefs.K_CW, s.cW), 80, 300);
        s.cH = clamp(sp.getInt(Prefs.K_CH, s.cH), 20, 64);
        s.cR = clamp(sp.getInt(Prefs.K_CR, s.cR), 0, 40);
        s.cPad = clamp(sp.getInt(Prefs.K_CPAD, s.cPad), 0, 24);
        s.glyph = clamp(sp.getInt(Prefs.K_GLYPH, s.glyph), 50, 130);
        s.eW = clamp(sp.getInt(Prefs.K_EW, s.eW), 220, 440);
        s.eR = clamp(sp.getInt(Prefs.K_ER, s.eR), 0, 60);
        s.padX = clamp(sp.getInt(Prefs.K_PADX, s.padX), 6, 36);
        s.padY = clamp(sp.getInt(Prefs.K_PADY, s.padY), 6, 32);
        s.tScale = clamp(sp.getInt(Prefs.K_TS, s.tScale), 70, 140);
        s.mTop = clamp(sp.getInt(Prefs.K_Y, s.mTop), -30, 80);
        s.mX = clamp(sp.getInt(Prefs.K_X, s.mX), -200, 200);
        s.mDrop = clamp(sp.getInt(Prefs.K_DROP, s.mDrop), -1, 60);
        s.mSide = clamp(sp.getInt(Prefs.K_SIDE, s.mSide), 12, 60);
        s.colMode = clamp(sp.getInt(Prefs.K_COLMODE, 0), 0, 2);
        s.bgCustom = sp.getInt(Prefs.K_BG, s.bgCustom) | 0xFF000000;
        s.bgAlpha = clamp(sp.getInt(Prefs.K_BGA, s.bgAlpha), 20, 100);
        s.textMode = clamp(sp.getInt(Prefs.K_TXTMODE, 0), 0, 1);
        s.textCustom = sp.getInt(Prefs.K_TXT, s.textCustom) | 0xFF000000;
        s.accMode = clamp(sp.getInt(Prefs.K_ACCMODE, 0), 0, 2);
        s.accCustom = sp.getInt(Prefs.K_ACC, s.accCustom) | 0xFF000000;
        s.borderW = clamp(sp.getInt(Prefs.K_BW, 0), 0, 4);
        s.borderCol = sp.getInt(Prefs.K_BCOL, s.borderCol);
        s.speed = clamp(sp.getInt(Prefs.K_SPEED, s.speed), 50, 200);
        s.bounce = clamp(sp.getInt(Prefs.K_BOUNCE, s.bounce), 0, 100);
        s.qs = clamp(sp.getInt(Prefs.K_QS, QS_UNDER), 0, 2);
        s.lockShow = sp.getBoolean(Prefs.K_LOCK, false);
        s.haptic = sp.getBoolean(Prefs.K_HAPTIC, true);
        s.derive(c);
        return s;
    }

    /** Works out final colours from the chosen modes (black / Material You / custom). */
    void derive(Context c) {
        int base;
        if (colMode == 1) base = Pal.get(c, "accent1", 900, 0xFF1D1033);
        else if (colMode == 2) base = bgCustom;
        else base = 0xFF000000;
        bg = Col.alpha(base, bgAlpha / 100f);
        int on = colMode == 1 ? Pal.get(c, "neutral1", 50, 0xFFF4EFF6) : Col.onColor(base);
        text = textMode == 1 ? textCustom : on;
        sub = Col.alpha(text, 0.70f);
        btn = Col.alpha(text, 0.18f);
        tile = Col.alpha(text, 0.14f);
        accentOverride = accMode != 0;
        m3 = accMode == 1;
        accent = accMode == 1 ? Pal.get(c, "accent1", 200, 0xFFD0BCFF) : (accMode == 2 ? accCustom : 0xFFB388FF);
        if (m3) {
            // same dark-scheme mapping the settings app uses: primary / on-primary, primary container, secondary container
            onAccent = Pal.get(c, "accent1", 800, 0xFF381E72);
            accCont = Pal.get(c, "accent1", 700, 0xFF4F378B);
            onAccCont = Pal.get(c, "accent1", 100, 0xFFEADDFF);
            secCont = Pal.get(c, "accent2", 700, 0xFF4A4458);
            onSecCont = Pal.get(c, "accent2", 100, 0xFFE8DEF8);
        }
    }
}
