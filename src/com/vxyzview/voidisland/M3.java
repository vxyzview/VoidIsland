package com.vxyzview.voidisland;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Material 3 / Material You look for the settings app: dynamic colour roles (Android 12+) and the components we need. */
final class M3 {
    interface OnSel { void on(int i); }
    interface OnColor { void on(int argb); }
    interface Fmt { String f(int v); }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    final Context c;
    final float d;
    /** 0 follow the system · 1 light · 2 dark · 3 AMOLED (pure black). */
    static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2, THEME_AMOLED = 3;
    final int themeMode;
    final boolean dark, amoled, wide;

    final int primary, onPrimary, primaryContainer, onPrimaryContainer, secondaryContainer, onSecondaryContainer,
        tertiaryContainer, onTertiaryContainer, surface, surfaceLow, container, containerHigh, onSurface,
        onSurfaceVariant, outline, outlineVariant, error, errorContainer, onErrorContainer;

    M3(Context c) {
        this.c = c;
        d = c.getResources().getDisplayMetrics().density;
        // M3 window size classes: >= 600dp width is an expanded window (tablets, big splits, phones in landscape).
        wide = c.getResources().getConfiguration().screenWidthDp >= 600;
        themeMode = Prefs.i(c, Prefs.K_THEME, THEME_SYSTEM);
        boolean sysDark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        dark = themeMode == THEME_DARK || themeMode == THEME_AMOLED || (themeMode == THEME_SYSTEM && sysDark);
        amoled = themeMode == THEME_AMOLED;

        // Palette tones resolve per the mode we are *showing*, so pinning Light over a dark system
        // still reads true light Material You tones (and vice versa) from the system palette.
        Context pc = c;
        if (dark != sysDark) {
            try {
                Configuration cfg = new Configuration(c.getResources().getConfiguration());
                cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
                pc = c.createConfigurationContext(cfg);
            } catch (Throwable t) { pc = c;   // falls back to the static scheme below
            }
        }

        int sSurface, sContainer, sContainerHigh, sOutlineVariant;
        if (dark) {
            primary = Pal.get(pc, "accent1", 200, 0xFFD0BCFF);
            onPrimary = Pal.get(pc, "accent1", 800, 0xFF381E72);
            primaryContainer = Pal.get(pc, "accent1", 700, 0xFF4F378B);
            onPrimaryContainer = Pal.get(pc, "accent1", 100, 0xFFEADDFF);
            secondaryContainer = Pal.get(pc, "accent2", 700, 0xFF4A4458);
            onSecondaryContainer = Pal.get(pc, "accent2", 100, 0xFFE8DEF8);
            tertiaryContainer = Pal.get(pc, "accent3", 700, 0xFF633B48);
            onTertiaryContainer = Pal.get(pc, "accent3", 100, 0xFFFFD8E4);
            sSurface = Pal.get(pc, "neutral1", 900, 0xFF141218);
            sContainer = Pal.get(pc, "neutral1", 800, 0xFF211F26);
            sContainerHigh = Pal.get(pc, "neutral1", 700, 0xFF2B2930);
            onSurface = Pal.get(pc, "neutral1", 100, 0xFFE6E0E9);
            onSurfaceVariant = Pal.get(pc, "neutral2", 200, 0xFFCAC4D0);
            outline = Pal.get(pc, "neutral2", 400, 0xFF938F99);
            sOutlineVariant = Pal.get(pc, "neutral2", 700, 0xFF49454F);
            error = 0xFFF2B8B5;
            errorContainer = 0xFF8C1D18;
            onErrorContainer = 0xFFF2B8B5;
        } else {
            primary = Pal.get(pc, "accent1", 600, 0xFF6750A4);
            onPrimary = Pal.get(pc, "accent1", 0, 0xFFFFFFFF);
            primaryContainer = Pal.get(pc, "accent1", 100, 0xFFEADDFF);
            onPrimaryContainer = Pal.get(pc, "accent1", 900, 0xFF21005D);
            secondaryContainer = Pal.get(pc, "accent2", 100, 0xFFE8DEF8);
            onSecondaryContainer = Pal.get(pc, "accent2", 900, 0xFF1D192B);
            tertiaryContainer = Pal.get(pc, "accent3", 100, 0xFFFFD8E4);
            onTertiaryContainer = Pal.get(pc, "accent3", 900, 0xFF31111D);
            sSurface = Pal.get(pc, "neutral1", 10, 0xFFFEF7FF);
            sContainer = Pal.get(pc, "neutral1", 50, 0xFFF3EDF7);
            sContainerHigh = Pal.get(pc, "neutral1", 100, 0xFFECE6F0);
            onSurface = Pal.get(pc, "neutral1", 900, 0xFF1D1B20);
            onSurfaceVariant = Pal.get(pc, "neutral2", 700, 0xFF49454F);
            outline = Pal.get(pc, "neutral2", 500, 0xFF79747E);
            sOutlineVariant = Pal.get(pc, "neutral2", 200, 0xFFCAC4D0);
            error = 0xFFB3261E;
            errorContainer = 0xFFF9DEDC;
            onErrorContainer = 0xFF410E0B;
        }
        // M3 elevation ladder: pages sit on surface, cards / the nav bar on surface-container,
        // dialogs on surface-container-high. AMOLED pins the neutrals to true black so OLED pixels
        // actually stay off; the accent containers keep their wallpaper colour, so the look stays Material You.
        if (amoled) {
            sSurface = 0xFF000000;
            sContainer = 0xFF17181C;
            sContainerHigh = 0xFF222328;
            sOutlineVariant = 0xFF2D2F36;
            surfaceLow = 0xFF0F0F12;
        } else {
            surfaceLow = Col.mix(sSurface, sContainer, 0.42f);
        }
        surface = sSurface;
        container = sContainer;
        containerHigh = sContainerHigh;
        outlineVariant = sOutlineVariant;
    }

    int dp(float v) { return Math.round(v * d); }

    // ------------------------------------------------------------------ type + shapes

    TextView text(CharSequence s, float sp, boolean medium, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    TextView body(CharSequence s) { return text(s, 14f, false, onSurfaceVariant); }

    GradientDrawable shape(int color, float rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(rDp));
        return g;
    }

    Drawable ripple(int fill, float rDp, int stroke, int rippleBase) {
        GradientDrawable content = shape(fill, rDp);
        if (stroke != 0) content.setStroke(dp(1), stroke);
        return new RippleDrawable(ColorStateList.valueOf(Col.alpha(rippleBase, 0.16f)), content, shape(0xFFFFFFFF, rDp));
    }

    LinearLayout col() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    LinearLayout.LayoutParams lpw(float weight) { return new LinearLayout.LayoutParams(0, WRAP, weight); }

    LinearLayout.LayoutParams top(int w, int h, float topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(topDp);
        return p;
    }

    /**
     * Host that keeps its single child no wider than maxDp and centres it — settings content
     * never stretches across a tablet or a split-screen window, cards stay readable.
     */
    View maxSized(final View child, final float maxDp) {
        FrameLayout f = new FrameLayout(c) {
            @Override protected void onMeasure(int wSpec, int hSpec) {
                int cap = Math.min(MeasureSpec.getSize(wSpec), dp(maxDp));
                child.measure(MeasureSpec.makeMeasureSpec(cap, MeasureSpec.EXACTLY), hSpec);
                setMeasuredDimension(MeasureSpec.getSize(wSpec),
                    View.resolveSizeAndState(Math.max(getMinimumHeight(), child.getMeasuredHeight()), hSpec, 0));
            }
        };
        f.addView(child, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL));
        return f;
    }

    /** Filled card with an optional small coloured heading. */
    LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout k = col();
        k.setBackground(shape(surfaceLow, 28));
        k.setPadding(dp(20), dp(18), dp(20), dp(20));
        parent.addView(k, top(MATCH, WRAP, 14));
        if (heading != null) {
            TextView t = text(heading, 15f, true, primary);
            t.setPadding(0, 0, 0, dp(10));
            k.addView(t);
        }
        return k;
    }

    // ------------------------------------------------------------------ buttons + rows

    /** kind: 0 filled, 1 tonal, 2 outlined, 3 text */
    TextView button(String label, int kind, final Runnable r) {
        int fill = kind == 0 ? primary : (kind == 1 ? secondaryContainer : 0);
        int fg = kind == 0 ? onPrimary : (kind == 1 ? onSecondaryContainer : primary);
        TextView t = text(label, 14f, true, fg);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(40));
        int ph = dp(kind == 3 ? 12 : 22);
        t.setPadding(ph, 0, ph, 0);
        t.setBackground(ripple(fill, 20, kind == 2 ? outline : 0, fg));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return t;
    }

    /** Leading tonal icon circle + title + supporting text + optional trailing view. */
    LinearLayout item(int icon, String title, String sub, View trailing, final Runnable click) {
        LinearLayout r = row();
        r.setPadding(0, dp(8), 0, dp(8));
        r.setMinimumHeight(dp(56));
        if (icon >= 0) {
            FrameLayout lead = new FrameLayout(c);
            lead.setBackground(shape(primaryContainer, 20));
            lead.addView(new IconView(c, icon, onPrimaryContainer), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            r.addView(lead, lp(dp(40), dp(40)));
        }
        LinearLayout t = col();
        t.setPadding(icon >= 0 ? dp(16) : 0, 0, dp(12), 0);
        t.addView(text(title, 16f, false, onSurface));
        if (sub != null && sub.length() > 0) {
            TextView s = text(sub, 13f, false, onSurfaceVariant);
            s.setPadding(0, dp(2), 0, 0);
            t.addView(s);
        }
        r.addView(t, lpw(1f));
        if (trailing != null) r.addView(trailing);
        if (click != null) {
            r.setBackground(ripple(0, 12, 0, onSurface));
            r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { click.run(); } });
        }
        return r;
    }

    View statusDot(boolean ok) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(shape(ok ? primary : 0, 14));
        if (!ok) {
            GradientDrawable g = shape(0, 14);
            g.setStroke(dp(2), outline);
            f.setBackground(g);
        }
        if (ok) f.addView(new IconView(c, IconView.CHECK, onPrimary), new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        f.setLayoutParams(lp(dp(28), dp(28)));
        return f;
    }

    M3Switch toggle(LinearLayout parent, String label, String sub, final String key, boolean def, final Runnable after) {
        LinearLayout r = row();
        r.setPadding(0, dp(8), 0, dp(8));
        r.setMinimumHeight(dp(52));
        LinearLayout t = col();
        t.setPadding(0, 0, dp(12), 0);
        t.addView(text(label, 16f, false, onSurface));
        if (sub != null) {
            TextView s = text(sub, 13f, false, onSurfaceVariant);
            s.setPadding(0, dp(2), 0, 0);
            t.addView(s);
        }
        r.addView(t, lpw(1f));
        final M3Switch sw = new M3Switch(this, Prefs.b(c, key, def), new OnSel() {
            @Override public void on(int v) {
                Prefs.sp(c).edit().putBoolean(key, v == 1).apply();
                if (after != null) after.run();
            }
        });
        r.addView(sw, lp(dp(52), dp(32)));
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { sw.toggle(); } });
        parent.addView(r);
        return sw;
    }

    /** Label + live value + slider that writes {@code key} (value * mult) into preferences. */
    M3Slider slider(LinearLayout parent, String label, final String key, int min, int max, int def, final int mult, final Fmt fmt, final Runnable after) {
        LinearLayout head = row();
        head.setPadding(0, dp(10), 0, 0);
        head.addView(text(label, 16f, false, onSurface), lpw(1f));
        final TextView val = text("", 14f, true, primary);
        head.addView(val);
        parent.addView(head);
        final M3Slider s = new M3Slider(this);
        int cur = Prefs.i(c, key, def * mult) / mult;
        s.set(min, max, Math.max(min, Math.min(max, cur)));
        val.setText(fmt.f(s.value()));
        s.setOnChange(new M3Slider.OnChange() {
            @Override public void onChange(int v, boolean user) {
                val.setText(fmt.f(v));
                if (!user) return;
                Prefs.sp(c).edit().putInt(key, v * mult).apply();
                if (after != null) after.run();
            }
        });
        parent.addView(s, lp(MATCH, dp(40)));
        return s;
    }

    /** Segmented button group. selected may be -1 (none). */
    final class Seg {
        final TextView[] t;
        final String[] labels;
        final OnSel cb;

        Seg(String[] labels, OnSel cb) {
            this.labels = labels;
            this.cb = cb;
            t = new TextView[labels.length];
        }

        void select(int sel) {
            for (int i = 0; i < t.length; i++) {
                boolean on = i == sel;
                t[i].setBackgroundColor(on ? secondaryContainer : 0);
                t[i].setTextColor(on ? onSecondaryContainer : onSurface);
                t[i].setText(labels[i]);
            }
        }
    }

    LinearLayout segmented(String[] labels, int sel, OnSel cb) {
        final Seg seg = new Seg(labels, cb);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = shape(0, 20);
        bg.setStroke(dp(1), outlineVariant);
        box.setBackground(bg);
        box.setClipToOutline(true);
        box.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            if (i > 0) {
                View div = new View(c);
                div.setBackgroundColor(outlineVariant);
                box.addView(div, lp(dp(1), MATCH));
            }
            TextView t = text(labels[i], 13.5f, true, onSurface);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine();
            t.setPadding(dp(4), 0, dp(4), 0);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    seg.select(idx);
                    seg.cb.on(idx);
                }
            });
            seg.t[i] = t;
            box.addView(t, new LinearLayout.LayoutParams(0, dp(40), 1f));
        }
        seg.select(sel);
        return box;
    }

    /** Small outlined pill chip. */
    TextView chip(String label, final Runnable r) {
        TextView t = text(label, 13.5f, true, onSurfaceVariant);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(34));
        t.setPadding(dp(14), 0, dp(14), 0);
        t.setBackground(ripple(0, 17, outlineVariant, onSurface));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return t;
    }

    /** Tonal button with a leading glyph — M3's icon + label button, used for the Try-it demos. */
    View richButton(int icon, String label, final Runnable r) {
        LinearLayout b = row();
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(6), dp(13), dp(6), dp(13));
        b.setBackground(ripple(secondaryContainer, 16, 0, onSecondaryContainer));
        b.addView(new IconView(c, icon, onSecondaryContainer), lp(dp(18), dp(18)));
        TextView t = text(label, 14f, true, onSecondaryContainer);
        t.setPadding(dp(8), 0, 0, 0);
        b.addView(t);
        b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return b;
    }

    /** Material 3 navigation bar. */
    final class Nav {
        final View[] pill;
        final TextView[] lab;
        final IconView[] ico;

        Nav(int n) {
            pill = new View[n];
            lab = new TextView[n];
            ico = new IconView[n];
        }

        void select(int sel) {
            for (int i = 0; i < pill.length; i++) {
                boolean on = i == sel;
                pill[i].setBackground(shape(on ? secondaryContainer : 0, 16));
                ico[i].set(ico[i].type(), on ? onSecondaryContainer : onSurfaceVariant);
                lab[i].setTextColor(on ? onSurface : onSurfaceVariant);
            }
        }
    }

    LinearLayout navBar(String[] labels, int[] icons, int sel, final OnSel cb) {
        final Nav nav = new Nav(labels.length);
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(surfaceLow);
        bar.setPadding(dp(8), dp(12), dp(8), dp(16));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout item = col();
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout p = new FrameLayout(c);
            IconView ic = new IconView(c, icons[i], onSurfaceVariant);
            p.addView(ic, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            item.addView(p, lp(dp(64), dp(32)));
            TextView l = text(labels[i], 12f, true, onSurfaceVariant);
            l.setSingleLine();
            l.setPadding(0, dp(4), 0, 0);
            item.addView(l);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    nav.select(idx);
                    cb.on(idx);
                }
            });
            nav.pill[i] = p;
            nav.lab[i] = l;
            nav.ico[i] = ic;
            bar.addView(item, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        nav.select(sel);
        return bar;
    }

    /** Material 3 navigation rail for expanded-width windows (tablets, landscape phones): same items, one column. */
    LinearLayout navRail(String[] labels, int[] icons, int sel, final OnSel cb) {
        final Nav nav = new Nav(labels.length);
        LinearLayout rail = new LinearLayout(c);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setBackgroundColor(surfaceLow);
        rail.setPadding(dp(8), dp(16), dp(8), dp(24));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout item = col();
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, dp(8), 0, dp(8));
            item.setClickable(true);
            FrameLayout p = new FrameLayout(c);
            IconView ic = new IconView(c, icons[i], onSurfaceVariant);
            p.addView(ic, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            item.addView(p, lp(dp(56), dp(32)));
            TextView l = text(labels[i], 12f, true, onSurfaceVariant);
            l.setSingleLine();
            l.setPadding(0, dp(4), 0, 0);
            item.addView(l);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    nav.select(idx);
                    cb.on(idx);
                }
            });
            nav.pill[i] = p;
            nav.lab[i] = l;
            nav.ico[i] = ic;
            rail.addView(item);
        }
        nav.select(sel);
        return rail;
    }

    // ------------------------------------------------------------------ colours

    /** Row of colour swatches (+ a "custom" one that opens the picker). */
    View swatches(final Activity a, final int[] colors, final int selected, final boolean custom, final OnColor cb) {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = row();
        r.setPadding(0, dp(8), 0, dp(4));
        boolean found = false;
        for (final int col : colors) {
            boolean on = col == selected;
            found |= on;
            r.addView(swatch(col, on, false, new Runnable() { @Override public void run() { cb.on(col); } }), lp(dp(44), dp(44)));
        }
        if (custom) {
            r.addView(swatch(selected, !found, true, new Runnable() {
                @Override public void run() { colorDialog(a, selected, cb); }
            }), lp(dp(44), dp(44)));
        }
        hs.addView(r);
        return hs;
    }

    private View swatch(final int color, boolean on, boolean plus, final Runnable click) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0);
        if (on) ring.setStroke(dp(3), primary);
        f.setBackground(ring);
        View dot = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        boolean filled = !plus || on;   // the "custom" swatch is empty (shows +) until a custom colour is chosen
        g.setColor(filled ? color : 0);
        g.setStroke(dp(1), filled ? outlineVariant : outline);
        dot.setBackground(g);
        f.addView(dot, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        if (!filled) f.addView(text("+", 20f, true, onSurfaceVariant), new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));
        else if (on) f.addView(new IconView(c, IconView.CHECK, Col.onColor(color | 0xFF000000)), new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        f.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { click.run(); } });
        return f;
    }

    /** HSV colour picker dialog. */
    void colorDialog(Activity a, int initial, final OnColor cb) {
        final Dialog dlg = new Dialog(a);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final float[] hsv = new float[3];
        Color.colorToHSV(initial | 0xFF000000, hsv);
        LinearLayout box = col();
        box.setBackground(shape(containerHigh, 28));
        box.setPadding(dp(24), dp(24), dp(24), dp(16));
        box.addView(text("Pick a colour", 22f, false, onSurface));
        final View prev = new View(c);
        final TextView hex = text("", 14f, true, onSurfaceVariant);
        LinearLayout pr = row();
        pr.setPadding(0, dp(16), 0, dp(4));
        pr.addView(prev, lp(dp(56), dp(56)));
        hex.setPadding(dp(16), 0, 0, 0);
        pr.addView(hex);
        box.addView(pr);
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                int col = Color.HSVToColor(hsv);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(col);
                g.setStroke(dp(1), outline);
                prev.setBackground(g);
                hex.setText(String.format("#%06X", col & 0xFFFFFF));
            }
        };
        String[] names = {"Hue", "Saturation", "Brightness"};
        int[] max = {360, 100, 100};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView l = text(names[i], 14f, false, onSurfaceVariant);
            l.setPadding(0, dp(8), 0, 0);
            box.addView(l);
            M3Slider s = new M3Slider(this);
            s.set(0, max[i], Math.round(i == 0 ? hsv[0] : hsv[i] * 100f));
            s.setOnChange(new M3Slider.OnChange() {
                @Override public void onChange(int v, boolean user) {
                    hsv[k] = k == 0 ? v : v / 100f;
                    refresh.run();
                }
            });
            box.addView(s, lp(MATCH, dp(40)));
        }
        refresh.run();
        LinearLayout btns = row();
        btns.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        btns.setPadding(0, dp(12), 0, 0);
        btns.addView(button("Cancel", 3, new Runnable() { @Override public void run() { dlg.dismiss(); } }));
        btns.addView(button("Apply", 3, new Runnable() {
            @Override public void run() {
                dlg.dismiss();
                cb.on(Color.HSVToColor(hsv) | 0xFF000000);
            }
        }));
        box.addView(btns);
        dlg.setContentView(box);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dlg.getWindow().setLayout(Math.min(Math.round(c.getResources().getDisplayMetrics().widthPixels * 0.9f), dp(360)), WRAP);
        }
        dlg.show();
    }

    static int lerp(int a, int b, float t) { return Col.mix(a, b, t); }
}

/** Material 3 switch: 52x32dp track, thumb grows from 16 to 24dp. */
final class M3Switch extends View {
    private final M3 m;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final M3.OnSel cb;
    private boolean on;
    private float t;
    private ValueAnimator va;

    M3Switch(M3 m, boolean on, M3.OnSel cb) {
        super(m.c);
        this.m = m;
        this.on = on;
        this.t = on ? 1f : 0f;
        this.cb = cb;
        setClickable(true);
        setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggle(); } });
    }

    boolean isOn() { return on; }

    void toggle() {
        on = !on;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (va != null) va.cancel();
        va = ValueAnimator.ofFloat(t, on ? 1f : 0f);
        va.setDuration(180);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { t = (Float) a.getAnimatedValue(); invalidate(); }
        });
        va.start();
        if (cb != null) cb.on(on ? 1 : 0);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), d = m.d;
        r.set(0, 0, w, h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Col.mix(m.containerHigh, m.primary, t));
        c.drawRoundRect(r, h / 2f, h / 2f, p);
        if (t < 1f) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * d);
            p.setColor(Col.alpha(m.outline, 1f - t));
            r.set(d, d, w - d, h - d);
            c.drawRoundRect(r, h / 2f, h / 2f, p);
            p.setStyle(Paint.Style.FILL);
        }
        float rad = (8f + 4f * t) * d, cx = (16f + 20f * t) * d;
        p.setColor(Col.mix(m.outline, m.onPrimary, t));
        c.drawCircle(cx, h / 2f, rad, p);
    }
}

/** Material 3 slider: 4dp tracks with a gap, 20dp thumb with a halo while dragging. */
final class M3Slider extends View {
    interface OnChange { void onChange(int value, boolean user); }

    private final M3 m;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int min, max = 100, val;
    private boolean drag;
    private OnChange cb;

    M3Slider(M3 m) {
        super(m.c);
        this.m = m;
    }

    void set(int min, int max, int v) {
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.val = v;
        invalidate();
    }

    int value() { return val; }

    void setOnChange(OnChange c) { cb = c; }

    private float frac() { return (val - min) / (float) (max - min); }

    @Override protected void onDraw(Canvas c) {
        float d = m.d, pad = 14f * d, x0 = pad, x1 = getWidth() - pad, cy = getHeight() / 2f, th = 4f * d, gap = 8f * d;
        float tx = x0 + (x1 - x0) * frac();
        p.setStyle(Paint.Style.FILL);
        p.setColor(m.secondaryContainer);
        r.set(Math.min(x1, tx + gap), cy - th / 2f, x1, cy + th / 2f);
        if (r.width() > 0) c.drawRoundRect(r, th / 2f, th / 2f, p);
        p.setColor(m.primary);
        r.set(x0, cy - th / 2f, Math.max(x0, tx - gap), cy + th / 2f);
        if (r.width() > 0) c.drawRoundRect(r, th / 2f, th / 2f, p);
        if (drag) {
            p.setColor(Col.alpha(m.primary, 0.16f));
            c.drawCircle(tx, cy, 20f * d, p);
        }
        p.setColor(m.primary);
        c.drawCircle(tx, cy, (drag ? 12f : 10f) * d, p);
    }

    private void update(float x) {
        float pad = 14f * m.d, f = (x - pad) / Math.max(1f, getWidth() - 2f * pad);
        f = f < 0 ? 0 : (f > 1 ? 1 : f);
        int v = min + Math.round(f * (max - min));
        if (v != val) {
            val = v;
            if (cb != null) cb.onChange(v, true);
        }
        invalidate();
    }

    private float downX, downY;
    private boolean pending;

    @Override public boolean onTouchEvent(MotionEvent e) {
        float slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                pending = true;
                drag = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (drag) { update(e.getX()); return true; }
                if (pending) {
                    float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
                    if (dx > slop && dx > dy) {            // horizontal drag: this is the slider's
                        pending = false;
                        drag = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                        update(e.getX());
                    } else if (dy > slop) {                // vertical: let the page scroll
                        pending = false;
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (pending) update(e.getX());             // plain tap sets the value
                drag = false;
                pending = false;
                invalidate();
                return true;
            default:
                drag = false;
                pending = false;
                invalidate();
                return true;
        }
    }
}
