package com.vxyzview.voidisland;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Material You settings app: Home (setup + demos), Style (shape / size / margin / padding / colour / motion), Behavior. */
public final class MainActivity extends Activity {
    private static final int[] BG = {0xFF000000, 0xFF1C1C1E, 0xFF0B1B3A, 0xFF2A1240, 0xFF0F2A1D, 0xFF3A0F1A, 0xFFF2F2F7};
    private static final int[] TXT = {0xFFFFFFFF, 0xFF111114, 0xFFB388FF, 0xFF30D158, 0xFFFFD60A, 0xFFFF9F0A};
    private static final int[] ACC = {0xFFB388FF, 0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFFF375F, 0xFFFFD60A, 0xFFFFFFFF};
    private static final int[] BORDER = {0x66FFFFFF, 0xFFFFFFFF, 0xFFB388FF, 0xFF0A84FF, 0xFF30D158, 0xFFFF375F};

    private static final String S_PAGE = "page", S_Y = "ys", S_PREVIEW = "pv";

    private final Handler h = new Handler(Looper.getMainLooper());
    private M3 m;
    private M3.Nav nav;
    private ScrollView scroll;
    private int page;                       // destination the user picked (may still be fading in)
    private int shown = -1;                 // destination whose content is in the scroll view right now (-1: nothing built yet)
    private int gen;                        // bumped by every (re)build: a pending page-switch fade bails when stale
    private int[] ys = new int[3];          // remembered scroll position per destination
    private PreviewView preview;
    private boolean previewExpanded;
    private TextView banner, bannerSub, diag;
    private boolean lastOverlay;

    private final Runnable rmPreview = new Runnable() { @Override public void run() { Bus.remove("preview"); } };
    private final Runnable diagRun = new Runnable() {
        @Override public void run() {
            updateDiag();
            if (page == 2) h.postDelayed(this, 700);
        }
    };

    private int dp(float v) { return m.dp(v); }

    // ------------------------------------------------------------------ lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        m = new M3(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(m.surface));
        getWindow().setStatusBarColor(m.surface);
        getWindow().setNavigationBarColor(m.surfaceLow);
        int f = View.SYSTEM_UI_FLAG_VISIBLE;
        if (!m.dark) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(f);

        int start = 0;
        if (b != null) {   // rotation, theme switch, process restore: land back on the same tab, same scroll
            start = Math.max(0, Math.min(2, b.getInt(S_PAGE, 0)));
            int[] saved = b.getIntArray(S_Y);
            if (saved != null && saved.length == ys.length) ys = saved;
            previewExpanded = b.getBoolean(S_PREVIEW, false);
        }

        LinearLayout root = new LinearLayout(this);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        M3.OnSel pickNav = new M3.OnSel() {
            @Override public void on(int i) { switchPage(i); }
        };
        M3.OnSel topNav = new M3.OnSel() {   // tapping the tab you are already on glides it back to the top
            @Override public void on(int i) { scroll.smoothScrollTo(0, 0); }
        };
        String[] names = {"Home", "Style", "Behavior"};
        int[] glyphs = {IconView.HOME, IconView.PALETTE, IconView.TUNE};
        if (m.wide) {   // expanded window (tablet, split screen, phone in landscape): a Material rail beats a stretched bar
            nav = m.navRail(names, glyphs, start, pickNav, topNav);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.addView(nav.view, new LinearLayout.LayoutParams(dp(88), M3.MATCH));
            root.addView(scroll, new LinearLayout.LayoutParams(0, M3.MATCH, 1f));
        } else {
            nav = m.navBar(names, glyphs, start, pickNav, topNav);
            root.setOrientation(LinearLayout.VERTICAL);
            root.addView(scroll, new LinearLayout.LayoutParams(M3.MATCH, 0, 1f));
            root.addView(nav.view, new LinearLayout.LayoutParams(M3.MATCH, M3.WRAP));
        }
        setContentView(root);
        lastOverlay = overlayOn();
        showPage(start, true);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (shown >= 0) ys[shown] = scroll.getScrollY();
        out.putInt(S_PAGE, page);
        out.putIntArray(S_Y, ys);
        out.putBoolean(S_PREVIEW, previewExpanded);
    }

    @Override protected void onResume() {
        super.onResume();
        boolean o = overlayOn();
        if (o != lastOverlay) { // permission was just granted/revoked in Settings: let the island switch layer
            lastOverlay = o;
            Prefs.sp(this).edit().putLong(Prefs.K_POKE, System.currentTimeMillis()).apply();
        }
        if (page != 1) showPage(page, true);
    }

    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(diagRun);
    }

    /** Back from Style / Behavior lands on Home first (the start destination); back from Home leaves the app. */
    @Override public void onBackPressed() {
        if (page != 0) {
            nav.select(0, true);
            switchPage(0);
        } else {
            super.onBackPressed();
        }
    }

    /**
     * User picked another tab: Material "fade through". The old page fades out fast, the new one fades in
     * while rising a few dp. Only the viewport-sized ScrollView is animated (never its tall content), so it stays cheap.
     */
    private void switchPage(final int p) {
        if (p == page) return;
        ys[shown] = scroll.getScrollY();
        page = p;
        final int my = ++gen;
        scroll.animate().cancel();
        scroll.animate().alpha(0f).setDuration(90).setInterpolator(new LinearInterpolator()).withEndAction(new Runnable() {
            @Override public void run() {
                if (my != gen) return;   // a newer rebuild or switch took over
                build(p);
                restoreScroll(ys[p]);
                scroll.setAlpha(0f);
                scroll.setTranslationY(dp(14));
                scroll.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(M3.STANDARD).start();
            }
        }).start();
    }

    /** Instant rebuild of page {@code p} (settings changed, or returning from system Settings), without any visible jump. */
    private void showPage(int p, boolean keepScroll) {
        int y = !keepScroll ? 0 : (shown == p ? scroll.getScrollY() : ys[p]);
        page = p;
        gen++;
        scroll.animate().cancel();
        scroll.setAlpha(1f);
        scroll.setTranslationY(0f);
        build(p);
        restoreScroll(y);
    }

    private void build(int p) {
        shown = p;
        preview = null;
        banner = null;
        diag = null;
        scroll.removeAllViews();
        LinearLayout c = m.col();
        c.setPadding(dp(16), dp(8), dp(16), dp(28));
        scroll.addView(m.maxSized(c, 480), new FrameLayout.LayoutParams(M3.MATCH, M3.WRAP));
        if (p == 0) buildHome(c);
        else if (p == 1) buildStyle(c);
        else buildBehavior(c);
        h.removeCallbacks(diagRun);
        if (p == 2) h.post(diagRun);
    }

    /**
     * The new content has no height until it is laid out, so scrollTo() right now would clamp to 0 and a
     * post()ed scrollTo would run after that first frame was drawn (a visible jump to the top and back).
     * Apply it just before the first draw instead.
     */
    private void restoreScroll(final int y) {
        scroll.scrollTo(0, 0);
        if (y <= 0) return;
        scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                ViewTreeObserver o = scroll.getViewTreeObserver();
                if (o.isAlive()) o.removeOnPreDrawListener(this);
                scroll.scrollTo(0, y);
                return true;
            }
        });
    }

    private void header(LinearLayout parent, String title, String sub) {
        TextView t = m.text(title, 32f, false, m.onSurface);
        t.setPadding(dp(4), dp(20), 0, 0);
        parent.addView(t);
        TextView s = m.text(sub, 14f, false, m.onSurfaceVariant);
        s.setPadding(dp(4), dp(6), 0, dp(4));
        parent.addView(s);
    }

    // ------------------------------------------------------------------ status helpers

    private boolean a11yOn() {
        if (Bus.active()) return true;
        try {
            String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) return false;
            ComponentName cn = new ComponentName(this, IslandService.class);
            return s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString());
        } catch (Throwable t) { return false; }
    }

    private boolean notifOn() {
        try {
            String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            return s != null && s.contains(getPackageName());
        } catch (Throwable t) { return false; }
    }

    private boolean overlayOn() {
        try { return Settings.canDrawOverlays(this); } catch (Throwable t) { return false; }
    }

    private boolean btOn() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED;
    }

    private void go(Intent i) {
        try { startActivity(i); } catch (ActivityNotFoundException e) { Toast.makeText(this, "Open Settings manually for this one", Toast.LENGTH_LONG).show(); }
    }

    private Runnable open(final String action) {
        return new Runnable() { @Override public void run() { go(new Intent(action)); } };
    }

    private void put(String key, int v) { Prefs.sp(this).edit().putInt(key, v).apply(); }

    // ------------------------------------------------------------------ HOME

    private View setupItem(int icon, String title, String sub, boolean done, String actionLabel, Runnable action) {
        View trailing = done ? m.statusDot(true) : m.button(actionLabel, 1, action);
        return m.item(icon, title, sub, trailing, action);
    }

    private void buildHome(LinearLayout p) {
        header(p, "VoidIsland", "Dynamic Island for Android  \u00b7  by vxyzview");

        boolean a = a11yOn(), n = notifOn();
        int state = a && n ? 0 : a ? 1 : 2;   // 0 live · 1 one step to go · 2 setup needed
        int heroBg = state == 0 ? m.primaryContainer : state == 1 ? m.tertiaryContainer : m.errorContainer;
        int heroOn = state == 0 ? m.onPrimaryContainer : state == 1 ? m.onTertiaryContainer : m.onErrorContainer;
        int heroGlyph = state == 0 ? IconView.CLOCK : state == 1 ? IconView.BELL : IconView.ACCESS;
        LinearLayout st = m.col();
        st.setBackground(m.shape(heroBg, 28));
        st.setPadding(dp(22), dp(20), dp(22), dp(22));
        LinearLayout hr = m.row();
        FrameLayout isle = new FrameLayout(this);   // a small black pill — the island itself, idle clock on
        isle.setBackground(m.shape(0xFF000000, 16));
        isle.addView(new IconView(this, heroGlyph, 0xFFFFFFFF), new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER));
        hr.addView(isle, m.lp(dp(50), dp(30)));
        LinearLayout ht = m.col();
        ht.setPadding(dp(14), 0, 0, 0);
        banner = m.text("", 20f, true, heroOn);
        bannerSub = m.text("", 13.5f, false, Col.alpha(heroOn, 0.86f));
        bannerSub.setPadding(0, dp(3), 0, 0);
        ht.addView(banner);
        ht.addView(bannerSub);
        hr.addView(ht, m.lpw(1f));
        st.addView(hr);
        p.addView(st, m.top(M3.MATCH, M3.WRAP, 12));
        if (a && n) { banner.setText("Island is live"); bannerSub.setText("Everything is on. Try a demo below, or play some music."); }
        else if (a) { banner.setText("One step to go"); bannerSub.setText("Turn on Notification access to show music, calls and payments."); }
        else { banner.setText("Setup needed"); bannerSub.setText("Turn on step 1 so the island has somewhere to live."); }

        LinearLayout su = m.card(p, "Setup");
        su.addView(setupItem(IconView.ACCESS, "Accessibility", "Draws the island and sees which app is open", a, "Enable", open(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        su.addView(setupItem(IconView.BELL, "Notification access", "Music, calls, payments, downloads and banners", n, "Enable", open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        su.addView(setupItem(IconView.LAYERS, "Display over other apps", "Recommended: Quick Settings slide over the island", overlayOn(), "Allow", new Runnable() {
            @Override public void run() { go(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
        }));
        su.addView(setupItem(IconView.BT, "Bluetooth names", "Optional: show which device connected", btOn(), "Allow", new Runnable() {
            @Override public void run() { if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, 7); }
        }));
        su.addView(m.item(IconView.BOLT, "Battery: unrestricted", "Optional: stops the system from putting the island to sleep",
            m.button("Open", 1, open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)), open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        TextView tip = m.body("Switch greyed out on Android 13+? Open App info, tap \u22ee, then Allow restricted settings.");
        tip.setPadding(0, dp(10), 0, dp(10));
        su.addView(tip);
        su.addView(m.button("App info", 2, new Runnable() {
            @Override public void run() { go(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        }));

        LinearLayout ty = m.card(p, "Try it");
        String[] dNm = {"Music", "Call", "Payment", "Charging", "Message", "Download", "Timer", "Toast"};
        String[] dCd = {"m", "c", "p", "g", "n", "d", "t", "s"};
        int[] dIc = {IconView.NOTE, IconView.PHONE, IconView.CARD, IconView.BOLT, IconView.BELL, IconView.DOWNLOAD, IconView.TIMER, IconView.MOON};
        LinearLayout row = null;
        for (int i = 0; i < dNm.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                ty.addView(row, m.top(M3.MATCH, M3.WRAP, i == 0 ? 0 : 8));
            }
            final String code = dCd[i];
            View b = m.richButton(dIc[i], dNm[i], new Runnable() { @Override public void run() { demo(code); } });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, M3.WRAP, 1f);
            if (i % 2 == 1) bp.setMarginStart(dp(8));
            row.addView(b, bp);
        }
        TextView clear = m.button("Clear demos", 3, new Runnable() { @Override public void run() { clearDemos(); } });
        ty.addView(clear, m.top(M3.WRAP, M3.WRAP, 6));

        LinearLayout th = m.card(p, "Theme");
        th.addView(m.body("Follow the system, or pin it. AMOLED is true black \u2014 pixels stay off on OLED screens. The island keeps its own colours."));
        th.addView(m.segmented(new String[]{"System", "Light", "Dark", "AMOLED"}, m.themeMode, new M3.OnSel() {
            @Override public void on(int i) {
                put(Prefs.K_THEME, i);
                recreate();   // page + scroll come back through onSaveInstanceState
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        }), m.top(M3.MATCH, dp(40), 12));

        TextView tip2 = m.body("Tip: if an app still shows its own pop-up on top, switch off floating / pop-up notifications for that app in the system settings.");
        tip2.setPadding(dp(4), dp(16), dp(4), 0);
        p.addView(tip2);
    }

    // ------------------------------------------------------------------ STYLE

    private void changed(boolean expanded) {
        if (preview != null) preview.set(Style.load(this));
        previewIsland(expanded);
    }

    private Runnable after(final boolean expanded) {
        return new Runnable() { @Override public void run() { changed(expanded); } };
    }

    private M3.Fmt unit(final String u) {
        return new M3.Fmt() { @Override public String f(int v) { return v + u; } };
    }

    private void preset(String name) {
        SharedPreferences.Editor e = Prefs.sp(this).edit();
        for (String k : Prefs.STYLE_KEYS) e.remove(k);
        if ("notch".equals(name)) {
            e.putInt(Prefs.K_SHAPE, Style.NOTCH).putInt(Prefs.K_ESHAPE, Style.NOTCH).putInt(Prefs.K_CW, 130).putInt(Prefs.K_CH, 28).putInt(Prefs.K_CR, 14)
                .putInt(Prefs.K_EW, 330).putInt(Prefs.K_ER, 30);
        } else if ("mini".equals(name)) {
            e.putInt(Prefs.K_CW, 112).putInt(Prefs.K_CH, 26).putInt(Prefs.K_CPAD, 7).putInt(Prefs.K_GLYPH, 90)
                .putInt(Prefs.K_EW, 300).putInt(Prefs.K_ER, 30).putInt(Prefs.K_TS, 92);
        } else if ("box".equals(name)) {
            e.putInt(Prefs.K_SHAPE, Style.BOX).putInt(Prefs.K_ESHAPE, Style.BOX).putInt(Prefs.K_CW, 140).putInt(Prefs.K_CH, 34).putInt(Prefs.K_CR, 10)
                .putInt(Prefs.K_EW, 340).putInt(Prefs.K_ER, 20);
        } else if ("you".equals(name)) {
            e.putInt(Prefs.K_COLMODE, 1).putInt(Prefs.K_ACCMODE, 1).putInt(Prefs.K_ER, 32);
        }
        e.apply();
        showPage(1, true);
        changed(previewExpanded);
    }

    private void buildStyle(LinearLayout p) {
        final Style s = Style.load(this);
        header(p, "Style", "Make the island yours. It updates live.");

        LinearLayout pv = m.card(p, null);
        preview = new PreviewView(m);
        preview.set(s);
        pv.addView(preview, new LinearLayout.LayoutParams(M3.MATCH, M3.WRAP));
        pv.addView(m.segmented(new String[]{"Compact", "Expanded"}, previewExpanded ? 1 : 0, new M3.OnSel() {
            @Override public void on(int i) { previewExpanded = i == 1; preview.setExpanded(previewExpanded); previewIsland(previewExpanded); }
        }), m.top(M3.MATCH, dp(40), 12));
        if (previewExpanded) preview.snapExpanded(true);   // a rebuild must not replay the expand animation

        LinearLayout qs = m.card(p, "Quick styles");
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout cr = m.row();
        String[][] presets = {{"iOS pill", "ios"}, {"Notch", "notch"}, {"Mini", "mini"}, {"Box", "box"}, {"Material You", "you"}};
        for (String[] pr : presets) {
            final String id = pr[1];
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(M3.WRAP, M3.WRAP);
            lp.setMarginEnd(dp(8));
            cr.addView(m.chip(pr[0], new Runnable() { @Override public void run() { preset(id); } }), lp);
        }
        hs.addView(cr);
        qs.addView(hs);

        LinearLayout sh = m.card(p, "Shape");
        sh.addView(label("Compact shape"));
        sh.addView(m.segmented(new String[]{"Pill", "Notch", "Box"}, s.cShape, new M3.OnSel() {
            @Override public void on(int i) { put(Prefs.K_SHAPE, i); showPage(1, true); changed(previewExpanded); }
        }), m.top(M3.MATCH, M3.WRAP, 8));
        String[] cDesc = {"A rounded capsule floating around the camera, like the iPhone Dynamic Island.",
            "Glued to the top edge with rounded bottom corners, like a display notch.",
            "A rounded rectangle with a corner radius you choose."};
        TextView cd0 = m.body(cDesc[s.cShape]);
        cd0.setPadding(0, dp(10), 0, 0);
        sh.addView(cd0);
        sh.addView(label("Expanded shape"));
        sh.addView(m.segmented(new String[]{"Pill", "Notch", "Box"}, s.eShape, new M3.OnSel() {
            @Override public void on(int i) { put(Prefs.K_ESHAPE, i); showPage(1, true); changed(previewExpanded); }
        }), m.top(M3.MATCH, M3.WRAP, 14));
        String[] eDesc = {"The card floats just below the camera, with its own corner shape.",
            "An expanded notch \u2014 glued to the top edge, rounded at the bottom. Works best with a compact notch.",
            "A rounded rectangle card with a corner radius you choose."};
        TextView ed0 = m.body(eDesc[s.eShape]);
        ed0.setPadding(0, dp(10), 0, 0);
        sh.addView(ed0);

        LinearLayout cp = m.card(p, "Compact pill");
        m.slider(cp, "Pill width", Prefs.K_CW, 80, 300, 150, 1, unit(" dp"), after(false));
        m.slider(cp, "Pill height", Prefs.K_CH, 20, 64, 32, 1, unit(" dp"), after(false));
        if (s.cShape != Style.PILL) m.slider(cp, s.cShape == Style.NOTCH ? "Notch corner radius" : "Box corner radius", Prefs.K_CR, 0, 40, 16, 1, unit(" dp"), after(false));
        m.slider(cp, "Pill padding", Prefs.K_CPAD, 0, 24, 9, 1, unit(" dp"), after(false));
        m.slider(cp, "Icon size", Prefs.K_GLYPH, 50, 130, 100, 1, unit("%"), after(false));
        if (s.cShape != Style.NOTCH) m.slider(cp, "Top margin", Prefs.K_Y, -30, 80, 0, 1, unit(" dp"), after(false));
        m.slider(cp, "Horizontal position", Prefs.K_X, -200, 200, 0, 1, unit(" dp"), after(false));

        LinearLayout ec = m.card(p, "Expanded card");
        m.slider(ec, "Card width", Prefs.K_EW, 220, 440, 340, 1, unit(" dp"), after(true));
        m.slider(ec, "Card corner radius", Prefs.K_ER, 0, 60, 38, 1, unit(" dp"), after(true));
        if (s.eShape != Style.NOTCH) {
            m.slider(ec, "Card drop (gap below the pill)", Prefs.K_DROP, -1, 60, -1, 1, new M3.Fmt() {
                @Override public String f(int v) { return v < 0 ? "Auto" : v + " dp"; }
            }, after(true));
        }
        m.slider(ec, "Side margin", Prefs.K_SIDE, 12, 60, 12, 1, unit(" dp"), after(true));
        m.slider(ec, "Padding, sides", Prefs.K_PADX, 6, 36, 20, 1, unit(" dp"), after(true));
        m.slider(ec, "Padding, top & bottom", Prefs.K_PADY, 6, 32, 16, 1, unit(" dp"), after(true));
        m.slider(ec, "Text size", Prefs.K_TS, 70, 140, 100, 1, unit("%"), after(true));

        LinearLayout co = m.card(p, "Colors");
        co.addView(label("Island color"));
        co.addView(m.segmented(new String[]{"Black", "Material You", "Custom"}, s.colMode, new M3.OnSel() {
            @Override public void on(int i) { put(Prefs.K_COLMODE, i); showPage(1, true); changed(previewExpanded); }
        }));
        if (s.colMode == 2) {
            co.addView(m.swatches(this, BG, s.bgCustom, true, new M3.OnColor() {
                @Override public void on(int c) { put(Prefs.K_BG, c); showPage(1, true); changed(previewExpanded); }
            }));
        }
        m.slider(co, "Background opacity", Prefs.K_BGA, 20, 100, 100, 1, unit("%"), after(previewExpanded));
        co.addView(label("Text color"));
        co.addView(m.segmented(new String[]{"Auto", "Custom"}, s.textMode, new M3.OnSel() {
            @Override public void on(int i) { put(Prefs.K_TXTMODE, i); showPage(1, true); changed(previewExpanded); }
        }));
        if (s.textMode == 1) {
            co.addView(m.swatches(this, TXT, s.textCustom, true, new M3.OnColor() {
                @Override public void on(int c) { put(Prefs.K_TXT, c); showPage(1, true); changed(previewExpanded); }
            }));
        }
        co.addView(label("Accent (bars, progress, rings)"));
        co.addView(m.segmented(new String[]{"Auto", "Material You", "Custom"}, s.accMode, new M3.OnSel() {
            @Override public void on(int i) { put(Prefs.K_ACCMODE, i); showPage(1, true); changed(previewExpanded); }
        }));
        if (s.accMode == 2) {
            co.addView(m.swatches(this, ACC, s.accCustom, true, new M3.OnColor() {
                @Override public void on(int c) { put(Prefs.K_ACC, c); showPage(1, true); changed(previewExpanded); }
            }));
        }
        final boolean hadBorder = s.borderW > 0;
        m.slider(co, "Border", Prefs.K_BW, 0, 4, 0, 1, unit(" dp"), new Runnable() {
            @Override public void run() {
                boolean has = Prefs.i(MainActivity.this, Prefs.K_BW, 0) > 0;
                if (has != hadBorder) showPage(1, true); // only rebuild when the colour row has to appear / disappear
                changed(previewExpanded);
            }
        });
        if (s.borderW > 0) {
            co.addView(m.swatches(this, BORDER, s.borderCol, true, new M3.OnColor() {
                @Override public void on(int c) { put(Prefs.K_BCOL, c); showPage(1, true); changed(previewExpanded); }
            }));
        }

        LinearLayout mo = m.card(p, "Motion");
        int sel = -1;
        if (s.speed == 90 && s.bounce == 35) sel = 0;
        else if (s.speed == 100 && s.bounce == 80) sel = 1;
        else if (s.speed == 140 && s.bounce == 55) sel = 2;
        mo.addView(m.segmented(new String[]{"Smooth", "Bouncy", "Snappy"}, sel, new M3.OnSel() {
            @Override public void on(int i) {
                int[][] v = {{90, 35}, {100, 80}, {140, 55}};
                put(Prefs.K_SPEED, v[i][0]);
                put(Prefs.K_BOUNCE, v[i][1]);
                showPage(1, true);
                changed(previewExpanded);
            }
        }));
        m.slider(mo, "Speed", Prefs.K_SPEED, 50, 200, 100, 1, unit("%"), after(true));
        m.slider(mo, "Bounce", Prefs.K_BOUNCE, 0, 100, 80, 1, unit("%"), after(true));

        TextView reset = m.button("Reset style", 2, new Runnable() { @Override public void run() { preset("ios"); } });
        p.addView(reset, m.top(M3.WRAP, M3.WRAP, 16));
    }

    private TextView label(String s) {
        TextView t = m.text(s, 14f, true, m.onSurfaceVariant);
        t.setPadding(0, dp(14), 0, dp(8));
        return t;
    }

    // ------------------------------------------------------------------ BEHAVIOR

    private void buildBehavior(LinearLayout p) {
        Style s = Style.load(this);
        header(p, "Behavior", "When and where the island shows up");

        LinearLayout qs = m.card(p, "Quick Settings & panels");
        qs.addView(m.body("What should the island do when you pull down Quick Settings or the notification shade?"));
        qs.addView(m.segmented(new String[]{"Under panel", "Hide", "On top"}, s.qs, new M3.OnSel() {
            @Override public void on(int i) { put(Prefs.K_QS, i); showPage(2, true); }
        }), m.top(M3.MATCH, dp(40), 14));
        String[] d = {"The island sits under the status bar, so Quick Settings slide over it like a built-in feature. Needs \u201cDisplay over other apps\u201d.",
            "The island stays above the status bar and fades away while a panel is open.",
            "The island always stays on top, even over Quick Settings."};
        TextView dd = m.body(d[s.qs]);
        dd.setPadding(0, dp(12), 0, 0);
        qs.addView(dd);
        if (s.qs == Style.QS_UNDER && !overlayOn()) {
            TextView w = m.text("Permission needed: until you allow it the island uses Hide mode.", 13.5f, true, m.error);
            w.setPadding(0, dp(12), 0, dp(8));
            qs.addView(w);
            qs.addView(m.button("Allow display over other apps", 1, new Runnable() {
                @Override public void run() { go(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            }));
        }
        diag = m.text("", 12.5f, false, m.onSurfaceVariant);
        diag.setPadding(0, dp(14), 0, 0);
        qs.addView(diag);

        LinearLayout sh = m.card(p, "What it shows");
        m.toggle(sh, "Island enabled", null, Prefs.K_ON, true, null);
        m.toggle(sh, "Clock when idle", "Show the time on an empty island", Prefs.K_CLOCK, true, null);
        m.toggle(sh, "Music & media", "Album art, seek bar, controls", Prefs.K_MEDIA, true, null);
        m.toggle(sh, "Music lyrics", "The playing song's lyrics scroll on the pill (from LRCLIB)", Prefs.K_LYRICS, true, null);
        m.toggle(sh, "Calls", "Answer and decline from the island", Prefs.K_CALL, true, null);
        m.toggle(sh, "Payments", "Payment and transfer confirmations", Prefs.K_PAY, true, null);
        m.toggle(sh, "Notifications", "Banners, downloads, timers, maps", Prefs.K_NOTIF, true, null);
        m.toggle(sh, "Charging & battery", "Plugged in, full, low battery", Prefs.K_CHG, true, null);
        m.toggle(sh, "Phone events", "Silent mode, DND, headphones, Bluetooth, flashlight", Prefs.K_SYS, true, null);
        m.toggle(sh, "App toasts", "Short messages apps pop up", Prefs.K_TOAST, false, null);

        LinearLayout ru = m.card(p, "Rules");
        m.toggle(ru, "Hide in landscape", null, Prefs.K_LAND, true, null);
        m.toggle(ru, "Hide idle clock in apps", "While an app is open only the clock pill hides — banners, music and timers keep showing", Prefs.K_HIDEAPP, true, null);
        m.toggle(ru, "Hide music & call pill inside their own app", null, Prefs.K_INAPP, true, null);
        m.toggle(ru, "Only show notifications on the island", "While a message or payment shows up here, its original notification rests for a few seconds so it does not pop twice. It returns to the shade afterwards \u2014 nothing is lost", Prefs.K_ABSORB, true, null);
        m.toggle(ru, "Show on lock screen", "Only when the island is above the status bar", Prefs.K_LOCK, false, null);
        m.toggle(ru, "Haptic feedback", null, Prefs.K_HAPTIC, true, null);
        m.slider(ru, "Banner time", Prefs.K_DUR, 2, 9, 5, 1000, unit(" s"), null);

        LinearLayout ap = m.card(p, "Apps");
        ap.addView(m.item(IconView.APPS, "Choose apps", "Switch off apps you never want on the island", null, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, AppsActivity.class)); }
        }));
    }

    private void updateDiag() {
        if (diag == null) return;
        long ago = Bus.sysSeenAt == 0 ? -1 : (System.currentTimeMillis() - Bus.sysSeenAt) / 1000;
        String layer = Bus.appLayer() ? "Layer: under system panels \u2713" : (Bus.active() ? "Layer: above the status bar" : "The island is not running yet (step 1)");
        String det = Bus.appLayer() ? "" : (ago < 0 ? "\nPanel detection: waiting. Pull down Quick Settings once to test it."
            : "\nPanel detection: " + (Bus.shadeOpen ? "panel open" : "panel closed") + "  \u00b7  last system event " + ago + " s ago\n" + Bus.sysLast);
        diag.setText(layer + det);
    }

    // ------------------------------------------------------------------ demos

    private boolean ready() {
        if (Bus.active()) return true;
        Toast.makeText(this, "Turn on the Accessibility service first (step 1)", Toast.LENGTH_LONG).show();
        return false;
    }

    private void previewIsland(boolean expanded) {
        if (!Bus.active()) return;
        IslandEvent e = new IslandEvent("preview", IslandEvent.PREVIEW);
        e.live = true;
        e.popup = expanded;
        e.priority = 999;
        e.pkg = getPackageName();
        e.title = "VoidIsland";
        e.text = "Looking good.";
        e.icon = Img.appIcon(this, getPackageName());
        Bus.post(e);
        h.removeCallbacks(rmPreview);
        h.postDelayed(rmPreview, 1700);
    }

    private Bitmap demoArt() {
        Bitmap b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF41307A);   // flat deep violet — the island's own colours never use gradients
        c.drawRect(0, 0, 256, 256, p);
        p.setColor(0xCC000000);
        c.drawCircle(128, 128, 84, p);
        p.setColor(0xFFB388FF);
        c.drawCircle(128, 128, 26, p);
        p.setColor(0xFF000000);
        c.drawCircle(128, 128, 7, p);
        return b;
    }

    private IslandEvent ev(String key, int type) {
        IslandEvent e = new IslandEvent(key, type);
        e.pkg = getPackageName();
        e.appName = "VoidIsland";
        e.icon = Img.appIcon(this, getPackageName());
        return e;
    }

    private void demo(String code) {
        if (!ready()) return;
        if ("m".equals(code)) {
            IslandEvent e = ev("media", IslandEvent.MEDIA);
            e.live = true; e.priority = 60;
            e.title = "Midnight Void"; e.text = "vxyzview"; e.art = demoArt(); e.accent = 0xFFB388FF;
            e.playing = true; e.durMs = 215000; e.posMs = 62000; e.posAt = SystemClock.elapsedRealtime();
            e.actions = PlaybackState.ACTION_SEEK_TO;
            Bus.post(e);
            Bus.expand("media");
            h.postDelayed(new Runnable() { @Override public void run() { Bus.remove("media"); } }, 30000);
        } else if ("c".equals(code)) {
            IslandEvent e = ev("call", IslandEvent.CALL);
            e.live = true; e.popup = true; e.incoming = true; e.priority = 100;
            e.title = "Void Dev"; e.text = "Incoming call";
            e.actLabels = new String[]{"Decline", "Accept"};
            e.actIntents = new PendingIntent[2];
            e.actRuns = new Runnable[]{
                new Runnable() { @Override public void run() { Bus.remove("call"); } },
                new Runnable() { @Override public void run() { ongoingCall(); } }};
            Bus.post(e);
        } else if ("p".equals(code)) {
            IslandEvent e = ev("pay", IslandEvent.PAYMENT);
            e.appName = "Wallet"; e.title = "Payment successful"; e.sub = "Rp 125.000"; e.text = "Kopi Void Coffee";
            e.durationMs = 5200;
            Bus.post(e);
        } else if ("g".equals(code)) {
            IslandEvent e = ev("charge", IslandEvent.CHARGE);
            e.level = 78; e.charging = true; e.title = "Charging"; e.accent = Cards.GREEN; e.durationMs = 3600;
            Bus.post(e);
        } else if ("n".equals(code)) {
            IslandEvent e = ev("n:test", IslandEvent.NOTIF);
            e.title = "vxyzview"; e.text = "Your island is alive. Tap me to open VoidIsland.";
            e.contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            e.durationMs = Prefs.i(this, Prefs.K_DUR, 5000);
            Bus.post(e);
        } else if ("d".equals(code)) {
            download(0);
        } else if ("t".equals(code)) {
            IslandEvent e = ev("t:demo", IslandEvent.TIMER);
            e.live = true; e.priority = 80; e.title = "Timer"; e.countDown = true; e.when = System.currentTimeMillis() + 5 * 60 * 1000L;
            e.actLabels = new String[]{"Stop"};
            e.actIntents = new PendingIntent[1];
            e.actRuns = new Runnable[]{new Runnable() { @Override public void run() { Bus.remove("t:demo"); } }};
            Bus.post(e);
        } else if ("s".equals(code)) {
            IslandEvent e = new IslandEvent("toast", IslandEvent.SYSTEM);
            e.title = "Headphones connected"; e.iconType = IconView.HEADSET; e.accent = Cards.GREEN; e.durationMs = 2400;
            Bus.post(e);
        }
    }

    private void ongoingCall() {
        IslandEvent e = ev("call", IslandEvent.CALL);
        e.live = true; e.priority = 100; e.incoming = false; e.chrono = true; e.when = System.currentTimeMillis();
        e.title = "Void Dev"; e.text = "On call";
        e.actLabels = new String[]{"Hang up"};
        e.actIntents = new PendingIntent[1];
        e.actRuns = new Runnable[]{new Runnable() { @Override public void run() { Bus.remove("call"); } }};
        Bus.post(e);
    }

    private void download(final int pct) {
        IslandEvent e = ev("p:demo", IslandEvent.PROGRESS);
        e.live = true; e.priority = 50; e.title = "void-island-v2.apk"; e.text = "Downloading"; e.progress = pct; e.accent = Cards.GREEN;
        Bus.post(e);
        if (pct >= 100) {
            h.postDelayed(new Runnable() { @Override public void run() { Bus.remove("p:demo"); } }, 900);
        } else {
            h.postDelayed(new Runnable() { @Override public void run() { if (Bus.active()) download(Math.min(100, pct + 5)); } }, 280);
        }
    }

    private void clearDemos() {
        for (String k : new String[]{"media", "call", "pay", "charge", "n:test", "p:demo", "t:demo", "toast", "preview"}) Bus.remove(k);
    }
}
