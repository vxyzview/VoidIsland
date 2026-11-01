package com.vxyzview.voidisland;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.view.animation.PathInterpolator;
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

    /** M3 "standard" easing, cubic-bezier(0.2, 0, 0, 1): quick start, long soft landing. Shared by every animation here. */
    static final PathInterpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);

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

    /** Tonal container card (32dp corners) with its section label sitting above it, like grouped M3 settings lists. */
    LinearLayout card(LinearLayout parent, String heading) {
        if (heading != null) {
            TextView t = text(heading, 14f, true, primary);
            t.setLetterSpacing(0.02f);
            t.setPadding(dp(10), 0, 0, 0);
            parent.addView(t, top(WRAP, WRAP, 26));
        }
        LinearLayout k = col();
        k.setBackground(shape(container, 32));
        k.setPadding(dp(20), dp(12), dp(20), dp(16));
        parent.addView(k, top(MATCH, WRAP, heading != null ? 10 : 16));
        return k;
    }

    /**
     * The two tonal pairs the app uses for tiles: 0 = primary container, 1 = secondary container. Both come from the
     * system's Material You accent (secondary is the same hue, calmer), so the whole app follows the wallpaper colour.
     */
    int toneBg(int tone) { return tone == 1 ? secondaryContainer : primaryContainer; }

    int toneFg(int tone) { return tone == 1 ? onSecondaryContainer : onPrimaryContainer; }

    // ------------------------------------------------------------------ buttons + rows

    /** kind: 0 filled, 1 tonal, 2 outlined, 3 text */
    TextView button(String label, int kind, final Runnable r) {
        int fill = kind == 0 ? primary : (kind == 1 ? secondaryContainer : 0);
        int fg = kind == 0 ? onPrimary : (kind == 1 ? onSecondaryContainer : primary);
        TextView t = text(label, 14f, true, fg);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(kind == 3 ? 40 : 44));
        int ph = dp(kind == 3 ? 14 : 22);
        t.setPadding(ph, 0, ph, 0);
        t.setBackground(ripple(fill, 22, kind == 2 ? outline : 0, fg));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return t;
    }

    /** 48dp round icon button (M3 icon button): back arrows and the like. */
    View iconButton(int icon, String description, final Runnable r) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(ripple(0, 24, 0, onSurface));
        f.setClickable(true);
        f.setFocusable(true);
        f.setContentDescription(description);
        f.addView(new IconView(c, icon, onSurface), new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        f.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return f;
    }

    /** Leading tonal icon circle + title + supporting text + optional trailing view. */
    LinearLayout item(int icon, String title, String sub, View trailing, final Runnable click) {
        LinearLayout r = row();
        r.setPadding(0, dp(8), 0, dp(8));
        r.setMinimumHeight(dp(56));
        if (icon >= 0) {
            FrameLayout lead = new FrameLayout(c);
            lead.setBackground(shape(primaryContainer, 16));
            lead.addView(new IconView(c, icon, onPrimaryContainer), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            r.addView(lead, lp(dp(44), dp(44)));
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
        r.setBackground(ripple(0, 12, 0, onSurface));
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
        s.setFormat(fmt);
        final Runnable later = perFrame(s, after);
        int cur = Prefs.i(c, key, def * mult) / mult;
        s.set(min, max, Math.max(min, Math.min(max, cur)));
        val.setText(fmt.f(s.value()));
        s.setOnChange(new M3Slider.OnChange() {
            @Override public void onChange(int v, boolean user) {
                val.setText(fmt.f(v));
                if (!user) return;
                Prefs.sp(c).edit().putInt(key, v * mult).apply();
                if (later != null) later.run();
            }
        });
        parent.addView(s, lp(MATCH, dp(40)));
        return s;
    }

    /** Runs {@code after} at most once per frame: a drag fires dozens of changes, the live preview only needs the latest. */
    private Runnable perFrame(final View on, final Runnable after) {
        if (after == null) return null;
        final boolean[] queued = new boolean[1];
        final Runnable fire = new Runnable() {
            @Override public void run() {
                queued[0] = false;
                after.run();
            }
        };
        return new Runnable() {
            @Override public void run() {
                if (queued[0]) return;
                queued[0] = true;
                on.postOnAnimation(fire);
            }
        };
    }

    /** Segmented button group. selected may be -1 (none). */
    final class Seg {
        final TextView[] t;
        final GradientDrawable[] fill;   // the ripple's content layer: recoloured in place so a press ripple is never cut short
        final String[] labels;
        final OnSel cb;

        Seg(String[] labels, OnSel cb) {
            this.labels = labels;
            this.cb = cb;
            t = new TextView[labels.length];
            fill = new GradientDrawable[labels.length];
        }

        void select(int sel) {
            for (int i = 0; i < t.length; i++) {
                boolean on = i == sel;
                fill[i].setColor(on ? secondaryContainer : 0);
                t[i].setTextColor(on ? onSecondaryContainer : onSurface);
                t[i].setText(labels[i]);
            }
        }
    }

    LinearLayout segmented(String[] labels, int sel, OnSel cb) {
        final Seg seg = new Seg(labels, cb);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = shape(0, 22);
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
            GradientDrawable fill = shape(0, 0);
            t.setBackground(new RippleDrawable(ColorStateList.valueOf(Col.alpha(onSurface, 0.16f)), fill, shape(0xFFFFFFFF, 0)));
            seg.fill[i] = fill;
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    seg.select(idx);
                    seg.cb.on(idx);
                }
            });
            seg.t[i] = t;
            box.addView(t, new LinearLayout.LayoutParams(0, dp(44), 1f));
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
    View richButton(int icon, String label, int tone, final Runnable r) {
        int bg = toneBg(tone), fg = toneFg(tone);
        LinearLayout b = row();
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), dp(16), dp(8), dp(16));
        b.setBackground(ripple(bg, 22, 0, fg));
        b.addView(new IconView(c, icon, fg), lp(dp(20), dp(20)));
        TextView t = text(label, 14.5f, true, fg);
        t.setPadding(dp(10), 0, 0, 0);
        b.addView(t);
        b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return b;
    }

    /**
     * One destination of the navigation bar / rail: a 32dp active-indicator pill (grows out of its centre
     * when selected, ripples when pressed) holding the icon, and a label centred underneath.
     */
    final class NavItem extends LinearLayout {
        private final Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF ir = new RectF();
        private final View ind;
        private final IconView ico;
        private final TextView lab;
        private float act;            // 0 inactive .. 1 active (drives indicator, icon + label colour)
        private ValueAnimator va;

        NavItem(String label, int icon, int indicatorDp, int padDp) {
            super(M3.this.c);
            setOrientation(LinearLayout.VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(0, dp(padDp), 0, dp(padDp));
            setClickable(true);
            setFocusable(true);
            setContentDescription(label);

            FrameLayout box = new FrameLayout(M3.this.c);
            ind = new View(M3.this.c) {
                @Override protected void onDraw(Canvas cv) {
                    if (act <= 0.001f) return;
                    float w = getWidth(), h = getHeight(), pw = w * (0.3f + 0.7f * act);
                    ip.setColor(Col.alpha(secondaryContainer, act));
                    ir.set((w - pw) / 2f, 0f, (w + pw) / 2f, h);
                    cv.drawRoundRect(ir, h / 2f, h / 2f, ip);
                }
            };
            // The parent's pressed state is dispatched to this (non-clickable) child, so the ripple stays inside the pill.
            ind.setForeground(ripple(0, 16, 0, onSurface));
            box.addView(ind, new FrameLayout.LayoutParams(MATCH, MATCH));
            ico = new IconView(M3.this.c, icon, onSurfaceVariant);
            box.addView(ico, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            addView(box, lp(dp(indicatorDp), dp(32)));

            lab = text(label, 12f, true, onSurfaceVariant);
            lab.setSingleLine();
            lab.setGravity(Gravity.CENTER_HORIZONTAL);   // a vertical LinearLayout hands children MATCH_PARENT width by default: centre the text itself
            lab.setPadding(0, dp(4), 0, 0);
            addView(lab, new LinearLayout.LayoutParams(WRAP, WRAP));
        }

        void setActive(boolean on, boolean animate) {
            setSelected(on);
            if (va != null) { va.cancel(); va = null; }
            float to = on ? 1f : 0f;
            if (!animate || to == act) {
                act = to;
                paint();
                return;
            }
            va = ValueAnimator.ofFloat(act, to);
            va.setDuration(on ? 280 : 160);
            va.setInterpolator(STANDARD);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    act = (Float) a.getAnimatedValue();
                    paint();
                }
            });
            va.start();
        }

        private void paint() {
            ico.set(ico.type(), Col.mix(onSurfaceVariant, onSecondaryContainer, act));
            lab.setTextColor(Col.mix(onSurfaceVariant, onSurface, act));
            ind.invalidate();
        }
    }

    /** Handle on a built navigation bar or rail: {@link #view} goes into the layout, {@link #select} moves the selection. */
    final class Nav {
        final LinearLayout view;
        private final NavItem[] items;
        private int sel = -1;

        Nav(LinearLayout view, int n) {
            this.view = view;
            this.items = new NavItem[n];
        }

        /** Selects item i. Animated unless this is the first selection. */
        void select(int i, boolean animate) {
            for (int k = 0; k < items.length; k++) items[k].setActive(k == i, animate && sel >= 0);
            sel = i;
        }

        int selected() { return sel; }
    }

    /**
     * Material 3 navigation bar (compact windows) or rail (expanded windows, e.g. tablets and landscape phones):
     * same items, one row or one column. {@code cb} fires when another item is picked, {@code again} when the
     * item that is already selected is tapped again (M3: scroll that destination back to the top).
     */
    private Nav nav(boolean rail, String[] labels, int[] icons, int sel, final OnSel cb, final OnSel again) {
        final Nav nav = new Nav(new LinearLayout(c), labels.length);
        LinearLayout box = nav.view;
        box.setOrientation(rail ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        box.setBackgroundColor(container);
        if (rail) {
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(dp(8), dp(16), dp(8), dp(24));
        } else {
            box.setPadding(dp(8), dp(12), dp(8), dp(16));
        }
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            NavItem item = new NavItem(labels[i], icons[i], rail ? 56 : 64, rail ? 6 : 0);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (nav.selected() == idx) {
                        if (again != null) again.on(idx);
                        return;
                    }
                    nav.select(idx, true);
                    cb.on(idx);
                }
            });
            nav.items[i] = item;
            box.addView(item, rail ? new LinearLayout.LayoutParams(MATCH, WRAP) : new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        nav.select(sel, false);
        return nav;
    }

    Nav navBar(String[] labels, int[] icons, int sel, OnSel cb, OnSel again) { return nav(false, labels, icons, sel, cb, again); }

    Nav navRail(String[] labels, int[] icons, int sel, OnSel cb, OnSel again) { return nav(true, labels, icons, sel, cb, again); }

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

    /**
     * Small Material 3 confirm dialog. {@code onNo} also runs when the dialog is dismissed any other way (back, tap outside),
     * so a caller that must undo something can rely on exactly one of the two callbacks firing.
     */
    void confirmDialog(Activity a, String title, String body, String yes, final Runnable onYes, String no, final Runnable onNo) {
        final Dialog dlg = new Dialog(a);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final boolean[] answered = new boolean[1];
        LinearLayout box = col();
        box.setBackground(shape(containerHigh, 28));
        box.setPadding(dp(24), dp(24), dp(24), dp(16));
        box.addView(text(title, 22f, false, onSurface));
        TextView b = text(body, 14.5f, false, onSurfaceVariant);
        b.setPadding(0, dp(14), 0, dp(4));
        b.setLineSpacing(0f, 1.12f);
        box.addView(b);
        LinearLayout btns = row();
        btns.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        btns.setPadding(0, dp(12), 0, 0);
        btns.addView(button(no, 3, new Runnable() { @Override public void run() { dlg.dismiss(); } }));
        btns.addView(button(yes, 3, new Runnable() {
            @Override public void run() {
                answered[0] = true;
                dlg.dismiss();
                if (onYes != null) onYes.run();
            }
        }));
        box.addView(btns);
        dlg.setContentView(box);
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) { if (!answered[0] && onNo != null) onNo.run(); }
        });
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dlg.getWindow().setLayout(Math.min(Math.round(c.getResources().getDisplayMetrics().widthPixels * 0.9f), dp(360)), WRAP);
        }
        dlg.show();
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
    private M3.OnSel cb;
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

    /** Re-binds a recycled switch: jumps to {@code on} with no animation and no callback. */
    void bind(boolean on, M3.OnSel cb) {
        if (va != null) { va.cancel(); va = null; }
        this.on = on;
        this.t = on ? 1f : 0f;
        this.cb = cb;
        invalidate();
    }

    /** Moves the switch to {@code v} with the usual animation but without calling the callback (undoing a refused change). */
    void revert(boolean v) {
        if (on == v) return;
        on = v;
        if (va != null) va.cancel();
        va = ValueAnimator.ofFloat(t, on ? 1f : 0f);
        va.setDuration(220);
        va.setInterpolator(M3.STANDARD);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { t = (Float) a.getAnimatedValue(); invalidate(); }
        });
        va.start();
    }

    void toggle() {
        on = !on;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (va != null) va.cancel();
        va = ValueAnimator.ofFloat(t, on ? 1f : 0f);
        va.setDuration(220);
        va.setInterpolator(M3.STANDARD);
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
        if (t > 0.35f) {   // the check fades in as the thumb grows
            float s = 14f * d;
            IconView.glyph(c, p, IconView.CHECK, Col.alpha(m.primary, (t - 0.35f) / 0.65f), cx - s / 2f, h / 2f - s / 2f, s);
        }
    }
}

/**
 * Material 3 Expressive slider, flat colours only: two 16dp tracks split by a gap, and a slim handle bar between them.
 * Touch it and the bar eases open into a pill that carries the value (so the number is never hidden under your finger),
 * while the tracks make room. The handle glides to wherever you tap, short ranges get step dots and a tick under the finger.
 */
final class M3Slider extends View {
    interface OnChange { void onChange(int value, boolean user); }

    private final M3 m;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final float[] rad = new float[8];
    private final float slop;
    private int min, max = 100, val;
    private OnChange cb;
    private M3.Fmt fmt;
    private String label = "";
    private float labelW;
    private boolean touching, drag, pending, running;
    private float pos, press;          // what is drawn: handle position (0..1) and how far the bar has opened into a pill (0..1)
    private long last;
    private float downX, downY;

    private final Runnable step = new Runnable() {
        @Override public void run() {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            float tp = frac(), tr = touching ? 1f : 0f;
            pos += (tp - pos) * (1f - (float) Math.exp(-dt * 26f));
            press += (tr - press) * (1f - (float) Math.exp(-dt * 20f));
            boolean more = Math.abs(tp - pos) > 0.0004f || Math.abs(tr - press) > 0.004f;
            if (!more) { pos = tp; press = tr; }
            invalidate();
            if (more) postOnAnimation(this); else running = false;
        }
    };
    private final Runnable release = new Runnable() {
        @Override public void run() { touching = false; kick(); }
    };

    M3Slider(M3 m) {
        super(m.c);
        this.m = m;
        slop = android.view.ViewConfiguration.get(m.c).getScaledTouchSlop();   // once, not on every touch event
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextSize(13f * m.c.getResources().getDisplayMetrics().scaledDensity);
    }

    void set(int min, int max, int v) {
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.val = v;
        pos = frac();   // programmatic: no glide
        relabel();
        invalidate();
    }

    int value() { return val; }

    void setOnChange(OnChange c) { cb = c; }

    /** How the value is written inside the pill (the same text the row shows next to its title). */
    void setFormat(M3.Fmt f) {
        fmt = f;
        relabel();
    }

    private void relabel() {
        label = fmt != null ? fmt.f(val) : String.valueOf(val);
        labelW = p.measureText(label);
    }

    private float frac() { return (val - min) / (float) (max - min); }

    private void kick() {
        if (running) return;
        running = true;
        last = System.nanoTime();
        postOnAnimation(step);
    }

    /** A track piece whose outer end is fully round and whose end next to the handle is nearly square. */
    private void piece(Canvas c, float rl, float rr, int color) {
        rad[0] = rad[1] = rad[6] = rad[7] = rl;
        rad[2] = rad[3] = rad[4] = rad[5] = rr;
        path.reset();
        path.addRoundRect(r, rad, Path.Direction.CW);
        p.setColor(color);
        c.drawPath(path, p);
    }

    @Override protected void onDraw(Canvas c) {
        float d = m.d, pad = 14f * d, x0 = pad, x1 = getWidth() - pad, cy = getHeight() / 2f;
        float big = 8f * d, small = 3f * d, gap = 6f * d, hh = 16f * d;
        float idleW = 4f * d, pillW = Math.max(40f * d, labelW + 24f * d);
        float hw = (idleW + (pillW - idleW) * press) / 2f;
        float tx = x0 + (x1 - x0) * pos;
        float hx = Math.max(hw, Math.min(getWidth() - hw, tx));   // the pill never leaves the view, even at the ends
        p.setStyle(Paint.Style.FILL);
        float rs = hx + hw + gap;                  // the part still to go
        if (x1 - rs > 2f * d) {
            r.set(rs, cy - big, x1, cy + big);
            piece(c, small, big, m.secondaryContainer);
        }
        float le = hx - hw - gap;                  // the part already done
        if (le - x0 > 2f * d) {
            r.set(x0, cy - big, le, cy + big);
            piece(c, big, small, m.primary);
        }
        int n = max - min;
        if (n <= 20) {                             // short range: a dot per step
            float rd = 1.7f * d, clear = hw + gap + 3f * d;
            for (int i = 1; i < n; i++) {
                float x = x0 + (x1 - x0) * i / n;
                if (Math.abs(x - hx) < clear) continue;
                p.setColor(x < hx ? Col.alpha(m.onPrimary, 0.75f) : Col.alpha(m.primary, 0.8f));
                c.drawCircle(x, cy, rd, p);
            }
        } else if (x1 - rs > 20f * d) {            // long range: one end stop
            p.setColor(m.primary);
            c.drawCircle(x1 - big, cy, 2f * d, p);
        }
        p.setColor(m.primary);
        r.set(hx - hw, cy - hh, hx + hw, cy + hh);
        float rr = Math.min(hw, hh);
        c.drawRoundRect(r, rr, rr, p);
        if (press > 0.55f) {                       // the value, once the pill is open enough to hold it
            p.setColor(Col.alpha(m.onPrimary, (press - 0.55f) / 0.45f));
            c.drawText(label, hx, cy - (p.ascent() + p.descent()) / 2f, p);
        }
    }

    private void update(float x) {
        float pad = 14f * m.d, f = (x - pad) / Math.max(1f, getWidth() - 2f * pad);
        f = f < 0 ? 0 : (f > 1 ? 1 : f);
        int v = min + Math.round(f * (max - min));
        if (v != val) {
            val = v;
            relabel();
            if (max - min <= 20) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (cb != null) cb.onChange(v, true);
        }
        kick();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Math.abs(pos - frac()) > 0.0005f) kick();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(step);
        removeCallbacks(release);
        running = false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                pending = true;
                drag = false;
                removeCallbacks(release);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (drag) { update(e.getX()); return true; }
                if (pending) {
                    float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
                    if (dx > slop && dx > dy) {            // horizontal drag: this is the slider's
                        pending = false;
                        drag = true;
                        touching = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                        update(e.getX());
                    } else if (dy > slop) {                // vertical: let the page scroll
                        pending = false;
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (pending) {                             // plain tap: set the value, show the pill for a moment
                    update(e.getX());
                    touching = true;
                    postDelayed(release, 280);
                } else {
                    touching = false;
                }
                drag = false;
                pending = false;
                kick();
                return true;
            default:
                drag = false;
                pending = false;
                touching = false;
                kick();
                return true;
        }
    }
}

/** A solid scalloped "cookie" badge (M3 Expressive shape) with a glyph in the middle. Flat colours only. */
final class CookieView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int icon, bg, fg, lobes;

    CookieView(android.content.Context c, int icon, int bg, int fg, int lobes) {
        super(c);
        this.icon = icon;
        this.bg = bg;
        this.fg = fg;
        this.lobes = lobes;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        path.reset();
        float cx = w / 2f, cy = h / 2f, big = Math.min(w, h) / 2f;
        int n = 240;
        for (int i = 0; i <= n; i++) {
            double a = 2.0 * Math.PI * i / n;
            float rr = (float) (big * (0.915 + 0.085 * Math.cos(lobes * a)));
            float x = cx + rr * (float) Math.cos(a), y = cy + rr * (float) Math.sin(a);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
    }

    @Override protected void onDraw(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(bg);
        c.drawPath(path, p);
        float s = Math.min(getWidth(), getHeight()) * 0.46f;
        IconView.glyph(c, p, icon, fg, (getWidth() - s) / 2f, (getHeight() - s) / 2f, s);
    }
}
