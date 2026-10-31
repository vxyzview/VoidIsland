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
    // accent family (filled button / tonal button): only meaningful while accentOverride is on
    int accentOn = 0xFF381E72, accentCont = 0xFF4F378B, accentOnCont = 0xFFEADDFF;
    // the rest of the Material 3 roles, also only meaningful while accentOverride is on:
    // tonal button (secondary container) + its label, "good" (tertiary) and "bad" (error) with their on-colours
    int btnOn = 0xFFFFFFFF;
    int tert = 0xFF30D158, tertOn = 0xFFFFFFFF, err = 0xFFFF453A, errOn = 0xFFFFFFFF;

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
        // Android 12+: the accent follows Material You out of the box (the island itself stays black unless told otherwise)
        s.accMode = clamp(sp.getInt(Prefs.K_ACCMODE, Pal.dynamic() ? 1 : 0), 0, 2);
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
        if (accMode == 1) {
            // same tones Material 3 uses for a dark scheme: primary, on-primary, primary container, on-primary container
            accent = Pal.get(c, "accent1", 200, 0xFFD0BCFF);
            accentOn = Pal.get(c, "accent1", 800, 0xFF381E72);
            accentCont = Pal.get(c, "accent1", 700, 0xFF4F378B);
            accentOnCont = Pal.get(c, "accent1", 100, 0xFFEADDFF);
        } else if (accMode == 2) {
            accent = accCustom;
            accentOn = Col.onColor(accent);
            accentCont = Col.container(accent);
            accentOnCont = Col.onContainer(accent);
        } else {
            accent = 0xFFB388FF;
        }
        deriveRoles(c);
    }

    /**
     * The other Material 3 (dark scheme) roles, so the whole island follows the theme and not just the accent:
     * tonal buttons = secondary container, "good" things (charging, payment success, accept, active call) = the SAME
     * accent as everything else (a separate tertiary hue made charging / payment look like a different theme), "bad" = error,
     * tiles = a surface-container tone, text = on-surface / on-surface-variant. The island itself stays black.
     */
    private void deriveRoles(Context c) {
        btnOn = text;
        tert = 0xFF30D158; tertOn = 0xFFFFFFFF; err = 0xFFFF453A; errOn = 0xFFFFFFFF;   // Auto: the classic green / red
        if (accMode == 1) {
            err = 0xFFF2B8B5; errOn = 0xFF601410;   // M3 error roles are fixed, they are not part of the wallpaper palette
            tert = accent;     // "good" = the wallpaper accent itself, so every card shares one colour
            tertOn = accentOn;
            btn = Pal.get(c, "accent2", 700, 0xFF4A4458);
            btnOn = Pal.get(c, "accent2", 100, 0xFFE8DEF8);
            if (colMode != 2) tile = Pal.get(c, "neutral1", 800, 0xFF322F35);
            if (colMode == 0 && textMode == 0) {   // black island: M3 on-surface / on-surface-variant instead of pure white
                text = Pal.get(c, "neutral1", 100, 0xFFE6E1E5);
                sub = Pal.get(c, "neutral2", 200, 0xFFCAC4D0);
            }
        } else if (accMode == 2) {
            err = 0xFFF2B8B5; errOn = 0xFF601410;
            tert = accent;     // "good" = the custom accent itself (no second hue)
            tertOn = accentOn;
            btn = accentCont;
            btnOn = accentOnCont;
        }
    }
}
