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
import android.os.MessageQueue;
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
    /** Everything the Try-it demos schedule for later (music timeout, download steps, preview): "Clear demos" cancels it all at once. */
    private final Handler dh = new Handler(Looper.getMainLooper());
    private static final String[] DEMO_KEYS = {"media", "call", "pay", "charge", "n:test", "p:demo", "t:demo", "sys:demo", "preview"};
    private M3 m;
    private M3.Nav nav;
    private ScrollView scroll;
    private int page;                       // destination the user picked (may still be fading in)
    private int shown = -1;                 // destination whose content is in the scroll view right now (-1: nothing built yet)
    private int gen;                        // bumped by every (re)build: a pending page-switch fade bails when stale
    private int[] ys = new int[3];          // remembered scroll position per destination
    /** A built destination. Kept, so switching tabs swaps views instead of rebuilding ~100 of them on the UI thread. */
    private static final class Page {
        View root;
        PreviewView preview;
        TextView banner, bannerSub, diag;
        String sig;   // the setup state it was built for: when that changes (Settings screens), the page is rebuilt
    }
    private final Page[] pages = new Page[3];
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
        prewarm();
    }

    /** Builds the other destinations while the UI thread is idle (one per idle slice), so the first tab switch is instant too. */
    private void prewarm() {
        Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
            @Override public boolean queueIdle() {
                if (isFinishing() || isDestroyed()) return false;
                for (int p = 0; p < pages.length; p++) {
                    if (pages[p] == null) {
                        try { pages[p] = make(p); } catch (Throwable t) { pages[p] = null; return false; }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    /** Everything that decides how Home / Behavior look; cheap to read. */
    private String statusSig() {
        return (a11yOn() ? "1" : "0") + (notifOn() ? "1" : "0") + (overlayOn() ? "1" : "0") + (btOn() ? "1" : "0")
            + (KeepAlive.batteryUnrestricted(this) ? "1" : "0");
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
        Page pg = pages[page];
        if (page != 1 && (pg == null || !pg.sig.equals(statusSig()))) showPage(page, true);
        if (page == 2) { h.removeCallbacks(diagRun); h.post(diagRun); }   // onPause stopped it
        h.post(new Runnable() { @Override public void run() { askKeepAlive(); } });
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
        scroll.animate().alpha(0f).setDuration(70).setInterpolator(new LinearInterpolator()).withLayer().withEndAction(new Runnable() {
            @Override public void run() {
                if (my != gen) return;   // a newer rebuild or switch took over
                build(p);
                restoreScroll(ys[p]);
                scroll.setAlpha(0f);
                scroll.setTranslationY(dp(14));
                scroll.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(M3.STANDARD).withLayer().start();
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
        pages[p] = null;   // the content itself changed: build it again
        build(p);
        restoreScroll(y);
    }

    /** Builds destination {@code p} off-screen. The page on screen keeps its own references while the build helpers publish theirs. */
    private Page make(int p) {
        PreviewView sPv = preview;
        TextView sBn = banner, sSub = bannerSub, sDg = diag;
        preview = null;
        banner = null;
        bannerSub = null;
        diag = null;
        LinearLayout c = m.col();
        c.setPadding(dp(16), dp(8), dp(16), dp(28));
        Page pg = new Page();
        pg.root = m.maxSized(c, 480);
        try {
            if (p == 0) buildHome(c);
            else if (p == 1) buildStyle(c);
            else buildBehavior(c);
            pg.preview = preview;
            pg.banner = banner;
            pg.bannerSub = bannerSub;
            pg.diag = diag;
            pg.sig = statusSig();
        } finally {
            preview = sPv;
            banner = sBn;
            bannerSub = sSub;
            diag = sDg;
        }
        return pg;
    }

    /** Puts destination {@code p} on screen: the kept page when it is still valid, otherwise a freshly built one. */
    private void build(int p) {
        shown = p;
        Page pg = pages[p];
        if (pg == null || !pg.sig.equals(statusSig())) {   // never show a page built for an older setup state
            pg = make(p);
            pages[p] = pg;
        }
        preview = pg.preview;
        banner = pg.banner;
        bannerSub = pg.bannerSub;
        diag = pg.diag;
        scroll.removeAllViews();
        scroll.addView(pg.root, new FrameLayout.LayoutParams(M3.MATCH, M3.WRAP));
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
        TextView t = m.text(title, 36f, false, m.onSurface);
        t.setPadding(dp(6), dp(22), 0, 0);
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
            if (s == null) return false;
            ComponentName mine = new ComponentName(this, ListenerService.class);
            for (String part : s.split(":")) {   // compare whole components: another app's name may merely start with ours
                ComponentName cn = ComponentName.unflattenFromString(part);
                if (mine.equals(cn)) return true;
            }
            return false;
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

    /** The phone maker's own autostart screen if this ROM has one we know, otherwise this app's info page. */
    private void openAutostart() {
        for (Intent i : KeepAlive.autostartScreens(Build.MANUFACTURER)) {
            try { startActivity(i); return; } catch (Throwable t) { /* private to this ROM or gone in this version: try the next */ }
        }
        go(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    /** Once, after setup is done, on phones whose battery saver is known to close background apps. */
    private void askKeepAlive() {
        if (isFinishing() || !KeepAlive.aggressiveHere() || Prefs.b(this, "keepalive_asked", false)) return;
        if (!a11yOn() || !notifOn() || KeepAlive.batteryUnrestricted(this)) return;
        Prefs.sp(this).edit().putBoolean("keepalive_asked", true).apply();
        m.confirmDialog(this, "Keep the island running",
            KeepAlive.familyHere() + " phones often close background apps, and then the island simply disappears. "
                + KeepAlive.hint(Build.MANUFACTURER) + " You can do this later from Home > Setup.",
            "Open settings", new Runnable() { @Override public void run() { openAutostart(); } },
            "Later", null);
    }

    private void put(String key, int v) { Prefs.sp(this).edit().putInt(key, v).apply(); }

    // ------------------------------------------------------------------ HOME

    /** A small flat tile on the hero card: what it tracks, and whether it is on. */
    private View statusPill(String label, boolean on, int fg) {
        LinearLayout t = m.col();
        t.setBackground(m.shape(Col.alpha(fg, 0.12f), 22));
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        t.addView(m.text(label, 12f, false, Col.alpha(fg, 0.8f)));
        TextView v = m.text(on ? "On" : "Off", 16f, true, fg);
        v.setPadding(0, dp(2), 0, 0);
        t.addView(v);
        return t;
    }

    private View setupItem(int icon, String title, String sub, boolean done, String actionLabel, Runnable action) {
        View trailing = done ? m.statusDot(true) : m.button(actionLabel, 1, action);
        return m.item(icon, title, sub, trailing, action);
    }

    private void buildHome(LinearLayout p) {
        header(p, "VoidIsland", "Dynamic Island for Android  \u00b7  by vxyzview");

        boolean a = a11yOn(), n = notifOn();
        int state = a && n ? 0 : a ? 1 : 2;   // 0 live · 1 one step to go · 2 setup needed
        int heroBg = state == 0 ? m.primaryContainer : state == 1 ? m.secondaryContainer : m.errorContainer;
        int heroOn = state == 0 ? m.onPrimaryContainer : state == 1 ? m.onSecondaryContainer : m.onErrorContainer;
        int heroGlyph = state == 0 ? IconView.CLOCK : state == 1 ? IconView.BELL : IconView.ACCESS;
        LinearLayout st = m.col();
        st.setBackground(m.shape(heroBg, 36));
        st.setPadding(dp(24), dp(24), dp(24), dp(20));
        LinearLayout hr = m.row();
        hr.addView(new CookieView(this, heroGlyph, heroOn, heroBg, 8), m.lp(dp(68), dp(68)));   // flat scalloped badge
        LinearLayout ht = m.col();
        ht.setPadding(dp(18), 0, 0, 0);
        banner = m.text("", 25f, false, heroOn);
        bannerSub = m.text("", 14f, false, Col.alpha(heroOn, 0.86f));
        bannerSub.setPadding(0, dp(5), 0, 0);
        bannerSub.setLineSpacing(0f, 1.1f);
        ht.addView(banner);
        ht.addView(bannerSub);
        hr.addView(ht, m.lpw(1f));
        st.addView(hr);
        LinearLayout pills = m.row();
        pills.addView(statusPill("Accessibility", a, heroOn), m.lpw(1f));
        LinearLayout.LayoutParams pp = m.lpw(1f);
        pp.setMarginStart(dp(10));
        pills.addView(statusPill("Notifications", n, heroOn), pp);
        st.addView(pills, m.top(M3.MATCH, M3.WRAP, 20));
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
        su.addView(setupItem(IconView.BT, "Bluetooth", "Android 12+ needs this to show connect and disconnect pop-ups with the device name", btOn(), "Allow", new Runnable() {
            @Override public void run() { if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, 7); }
        }));
        String fam = KeepAlive.familyHere();
        su.addView(setupItem(IconView.BOLT, "Battery: unrestricted",
            (fam != null ? "Recommended on " + fam + " phones: " : "Optional: ") + "stops the system from putting the island to sleep",
            KeepAlive.batteryUnrestricted(this), "Open", open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        if (fam != null) {
            Runnable autostart = new Runnable() { @Override public void run() { openAutostart(); } };
            su.addView(m.item(IconView.CLOCK, "Autostart & background", KeepAlive.hint(Build.MANUFACTURER),
                m.button("Open", 1, autostart), autostart));
        }
        TextView tip = m.body("Switch greyed out on Android 13+? Open App info, tap \u22ee, then Allow restricted settings.");
        tip.setPadding(0, dp(10), 0, dp(10));
        su.addView(tip);
        su.addView(m.button("App info", 2, new Runnable() {
            @Override public void run() { go(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        }));

        LinearLayout ty = m.card(p, "Try it");
        String[] dNm = {"Music", "Call", "Payment", "Charging", "Message", "Download", "Timer", "Headset"};
        String[] dCd = {"m", "c", "p", "g", "n", "d", "t", "s"};
        int[] dIc = {IconView.NOTE, IconView.PHONE, IconView.CARD, IconView.BOLT, IconView.BELL, IconView.DOWNLOAD, IconView.TIMER, IconView.HEADSET};
        LinearLayout row = null;
        for (int i = 0; i < dNm.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                ty.addView(row, m.top(M3.MATCH, M3.WRAP, i == 0 ? 0 : 8));
            }
            final String code = dCd[i];
            View b = m.richButton(dIc[i], dNm[i], (i / 2 + i % 2) % 2, new Runnable() { @Override public void run() { demo(code); } });
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

    /** Slider steps of 30 minutes shown as a clock time ("22:00"). */
    private M3.Fmt hourFmt() {
        return new M3.Fmt() { @Override public String f(int v) { return Rules.hhmm(v * 30); } };
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

        LinearLayout ck = m.card(p, "Idle clock");
        ck.addView(label("Compact pill"));
        ck.addView(m.segmentedGrid(new String[]{"Dial", "Center", "Split", "Date", "Line", "Capsule", "Sky", "Aura", "Dots", "Beacon"}, s.clockStyle, 5, new M3.OnSel() {
            @Override public void on(int i) { put(Prefs.K_CLOCKSTYLE, i); showPage(1, true); }   // the island itself shows the new clock right away
        }), m.top(M3.MATCH, M3.WRAP, 0));
        String[] cd = {"A mini dial whose ring fills with the minutes, the time and date on the right.",
            "Just the time, centred and clean, with a small AM / PM.",
            "Hour on the left, minutes on the right, so a camera hole sits right between them.",
            "Day and date on the left, the time on the right.",
            "The time, centred, over a slim line whose round head travels the hour.",
            "The time in a filled accent capsule, centred.",
            "A sun, a sinking sun or a moon for the part of the day, with the time and the part's name.",
            "Centred digits that fade from your accent colour into white.",
            "The week as seven dots with today lit up, and the time on the right.",
            "A glowing lamp in a ring that fills with the day, and a softly glowing time with how much of the day has gone."};
        TextView cdv = m.body(cd[s.clockStyle]);
        cdv.setPadding(0, dp(10), 0, 0);
        ck.addView(cdv);
        ck.addView(label("Expanded card"));
        ck.addView(m.segmentedGrid(new String[]{"Classic", "Digital", "Analog", "Bold", "Week", "Rings", "Flip", "Horizon"}, s.eClockStyle, 4, new M3.OnSel() {
            @Override public void on(int i) {
                put(Prefs.K_ECLOCKSTYLE, i);
                showPage(1, true);
                if (Prefs.b(MainActivity.this, Prefs.K_CLOCK, true)) Bus.expand("clock");   // open the card for a few seconds so you can see it
            }
        }), m.top(M3.MATCH, M3.WRAP, 0));
        String[] ed = {"Big time, a dial with a seconds hand, the date, this week and how much of the day is gone.",
            "A big centred time with the time in words, the date and the day's progress.",
            "A large dial with a seconds hand beside the time and the date, plus the day's progress.",
            "Huge stacked hour and minutes with the date beside them and this week below.",
            "The time and date up top, then this week and the day's progress. No dial.",
            "Three activity rings: how much of the day, the hour and the minute has gone, with the time beside them.",
            "A flip clock with a seconds line that fills each minute, the date and the week below.",
            "The sun (or the moon at night) travels an arc over the time, with the date and the time in words."};
        TextView edv = m.body(ed[s.eClockStyle]);
        edv.setPadding(0, dp(10), 0, 0);
        ck.addView(edv);
        if (!Prefs.b(this, Prefs.K_CLOCK, true)) {
            TextView off = m.text("The idle clock is off. Turn on Clock when idle under Behavior to see it.", 13.5f, true, m.error);
            off.setPadding(0, dp(12), 0, 0);
            ck.addView(off);
        }

        LinearLayout mu = m.card(p, "Music");
        mu.addView(label("Compact pill"));
        mu.addView(m.segmentedGrid(MusicStyle.CNAMES, s.musicStyle, 5, new M3.OnSel() {
            @Override public void on(int i) {
                put(Prefs.K_MSTYLE, i);
                showPage(1, true);
                musicPreview(false);   // show the pill with the new design right away
            }
        }), m.top(M3.MATCH, M3.WRAP, 0));
        TextView mcd = m.body(MusicStyle.CDESC[s.musicStyle]);
        mcd.setPadding(0, dp(10), 0, 0);
        mu.addView(mcd);
        mu.addView(label("Expanded card"));
        mu.addView(m.segmentedGrid(MusicStyle.ENAMES, s.eMusicStyle, 5, new M3.OnSel() {
            @Override public void on(int i) {
                put(Prefs.K_EMSTYLE, i);
                showPage(1, true);
                musicPreview(true);    // open the card for a few seconds so you can see it
            }
        }), m.top(M3.MATCH, M3.WRAP, 0));
        TextView mev = m.body(MusicStyle.EDESC[s.eMusicStyle]);
        mev.setPadding(0, dp(10), 0, 0);
        mu.addView(mev);
        if (!Prefs.b(this, Prefs.K_MEDIA, true)) {
            TextView off = m.text("Music is off. Turn on Music under Behavior to see it on the island.", 13.5f, true, m.error);
            off.setPadding(0, dp(12), 0, 0);
            mu.addView(off);
        }

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
        co.addView(label("Accent (buttons, bars, rings, icons)"));
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
        m.toggle(sh, "Music lyrics", "The playing song's lyrics show on the pill, long lines slide sideways. Looked up on lrclib.net, which receives the song title, artist and length. Lyrics are never saved to storage", Prefs.K_LYRICS, true, null);
        m.slider(sh, "Lyrics size", Prefs.K_LYRSIZE, 9, 16, 13, 1, unit(" sp"), null);
        m.slider(sh, "Lyrics timing", Prefs.K_LYROFF, -10, 10, 2, 100, new M3.Fmt() {
            @Override public String f(int v) { return (v > 0 ? "+" : "") + (v * 100) + " ms"; }
        }, null);
        m.toggle(sh, "Calls", "Answer and decline from the island", Prefs.K_CALL, true, null);
        m.toggle(sh, "Payments", "Payment and transfer confirmations", Prefs.K_PAY, true, null);
        m.toggle(sh, "Notifications", "Banners, downloads, timers, maps", Prefs.K_NOTIF, true, null);
        m.toggle(sh, "Live activities", "Rides, food delivery and live scores stay on the island while they run", Prefs.K_LIVEACT, true, null);
        m.toggle(sh, "Charging & battery", "Plugged in, full, low battery", Prefs.K_CHG, true, null);
        m.toggle(sh, "Phone events", "Silent mode, DND, headphones, Bluetooth, flashlight", Prefs.K_SYS, true, null);

        LinearLayout ru = m.card(p, "Rules");
        m.toggle(ru, "Hide when an app is fullscreen", "Videos, games and readers that hide the status bar. Everywhere else the island stays visible", Prefs.K_FULL, true, null);
        m.toggle(ru, "Hide idle clock in apps", "While an app is open only the clock pill hides — banners, music and timers keep showing", Prefs.K_HIDEAPP, true, null);
        m.toggle(ru, "Hide music & call pill inside their own app", null, Prefs.K_INAPP, true, null);
        final M3Switch[] absorbSw = new M3Switch[1];
        absorbSw[0] = m.toggle(ru, "Only show notifications on the island",
            "Off by default. Messages and payments shown on the island are removed from the notification shade, so nothing pops up twice, but a banner you miss is gone. Calls, timers, maps, downloads and music keep their own notification",
            Prefs.K_ABSORB, Prefs.ABSORB_DEF, new Runnable() {
                @Override public void run() {
                    if (!Prefs.b(MainActivity.this, Prefs.K_ABSORB, Prefs.ABSORB_DEF)) return;   // switched off: nothing to confirm
                    Prefs.absorbUnconfirmed = true;
                    m.confirmDialog(MainActivity.this, "Remove banners from the shade?",
                        "Messages and payments the island shows will be removed from the normal notification shade. If you look away and miss the banner, the message will not be waiting in the shade.\n\n"
                            + "Nothing is removed while the island is off, hidden, or ignoring that app, and apps on Keep in shade are never touched.",
                        "Turn on", new Runnable() { @Override public void run() { Prefs.absorbUnconfirmed = false; } },
                        "Cancel", new Runnable() {
                            @Override public void run() {
                                Prefs.absorbUnconfirmed = false;
                                Prefs.sp(MainActivity.this).edit().putBoolean(Prefs.K_ABSORB, false).apply();
                                if (absorbSw[0] != null) absorbSw[0].revert(false);
                            }
                        });
                }
            });
        // The lock-screen switch only matters when the island sits above the status bar; in "Under panel" mode it does nothing.
        if (!(s.qs == Style.QS_UNDER && overlayOn())) m.toggle(ru, "Show on lock screen", "Show the island over the lock screen", Prefs.K_LOCK, false, null);
        m.toggle(ru, "Haptic feedback", null, Prefs.K_HAPTIC, true, null);
        m.slider(ru, "Banner time", Prefs.K_DUR, 2, 9, 5, 1000, unit(" s"), null);

        LinearLayout qr = m.card(p, "Island rules");
        m.toggle(qr, "Quiet hours", "Keep banners off the island during these hours. They stay in the shade, untouched", Prefs.K_QUIET, false, null);
        m.slider(qr, "Quiet from", Prefs.K_QFROM, 0, 47, 44, 30, hourFmt(), null);
        m.slider(qr, "Quiet until", Prefs.K_QTO, 0, 47, 14, 30, hourFmt(), null);
        qr.addView(m.item(IconView.APPS, "Quiet hours apps", "Only these apps go quiet. Leave empty for all apps", null, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, AppsActivity.class).putExtra(AppsActivity.EXTRA_MODE, 2)); }
        }));
        m.toggle(qr, "Mute group chats", "Group conversations do not pop up on the island", Prefs.K_NOGROUP, false, null);
        m.toggle(qr, "One banner per chat", "Only the first message of a chat pops up; follow-ups within 5 minutes stay quiet", Prefs.K_ONEPERCHAT, false, null);

        LinearLayout ap = m.card(p, "Apps");
        ap.addView(m.item(IconView.APPS, "Choose apps", "Switch off apps you never want on the island", null, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, AppsActivity.class)); }
        }));
        ap.addView(m.item(IconView.APPS, "Keep in shade", "Apps that also keep their normal notification, like banking or one-time codes", null, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, AppsActivity.class).putExtra(AppsActivity.EXTRA_MODE, 1)); }
        }));
        ap.addView(m.item(IconView.NAV, "Ride apps", "Extra apps whose ongoing trip shows as a live activity", null, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, AppsActivity.class).putExtra(AppsActivity.EXTRA_MODE, 4)); }
        }));
        ap.addView(m.item(IconView.CARD, "Food apps", "Extra apps whose ongoing delivery shows as a live activity", null, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, AppsActivity.class).putExtra(AppsActivity.EXTRA_MODE, 5)); }
        }));
        ap.addView(m.item(IconView.TIMER, "Score apps", "Extra apps whose live match shows as a live activity", null, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, AppsActivity.class).putExtra(AppsActivity.EXTRA_MODE, 6)); }
        }));

        LinearLayout bk = m.card(p, "Backup");
        bk.addView(m.item(IconView.DOWNLOAD, "Back up settings", "Save every setting to a file, so a reinstall or a new phone starts where you left off", null, new Runnable() {
            @Override public void run() {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
                    .putExtra(Intent.EXTRA_TITLE, "voidisland-backup.txt");
                try { startActivityForResult(i, REQ_BACKUP); } catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this, "No file picker found on this phone", Toast.LENGTH_LONG).show(); }
            }
        }));
        bk.addView(m.item(IconView.TUNE, "Restore settings", "Load a backup file. This replaces your current settings", null, new Runnable() {
            @Override public void run() {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                try { startActivityForResult(i, REQ_RESTORE); } catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this, "No file picker found on this phone", Toast.LENGTH_LONG).show(); }
            }
        }));
    }

    private static final int REQ_BACKUP = 41, REQ_RESTORE = 42;

    @Override protected void onActivityResult(int req, int res, final Intent data) {
        super.onActivityResult(req, res, data);
        if ((req != REQ_BACKUP && req != REQ_RESTORE) || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        final boolean save = req == REQ_BACKUP;
        new Thread(new Runnable() {
            @Override public void run() {
                String msg;
                try {
                    if (save) {
                        java.io.OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                        try { os.write(Backup.write(Prefs.sp(MainActivity.this).getAll()).getBytes("UTF-8")); } finally { os.close(); }
                        msg = "Settings saved";
                    } else {
                        java.io.InputStream is = getContentResolver().openInputStream(uri);
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        try {
                            byte[] buf = new byte[8192];
                            for (int n; (n = is.read(buf)) > 0;) { bo.write(buf, 0, n); if (bo.size() > 1000000) break; }
                        } finally { is.close(); }
                        final java.util.Map<String, Object> vals = bo.size() > 1000000 ? null : Backup.read(bo.toString("UTF-8"));
                        if (vals == null) msg = "That file is not a VoidIsland backup";
                        else {
                            Backup.apply(Prefs.sp(MainActivity.this), vals);
                            msg = "Settings restored";
                            runOnUiThread(new Runnable() { @Override public void run() { if (!isFinishing()) recreate(); } });
                        }
                    }
                } catch (Throwable t) { msg = save ? "Could not save the file" : "Could not read the file"; }
                final String shown = msg;
                runOnUiThread(new Runnable() { @Override public void run() { Toast.makeText(MainActivity.this, shown, Toast.LENGTH_LONG).show(); } });
            }
        }, "vi-backup").start();
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
        dh.removeCallbacks(rmPreview);
        dh.postDelayed(rmPreview, 1700);
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

    /** The demo song (Try it > Music). It uses the real music's key, so it replaces whatever is playing for 30 seconds. */
    private void postMusicDemo() {
        IslandEvent e = ev("media", IslandEvent.MEDIA);
        e.live = true; e.priority = 60;
        e.title = "Midnight Void"; e.text = "vxyzview"; e.art = demoArt(); e.accent = 0xFFB388FF;
        e.playing = true; e.durMs = 215000; e.posMs = 62000; e.posAt = SystemClock.elapsedRealtime();
        e.actions = PlaybackState.ACTION_SEEK_TO;
        Bus.post(e);
        dh.postDelayed(new Runnable() { @Override public void run() { Bus.remove("media"); } }, 30000);
    }

    /**
     * After picking a music design: make sure there is a song to look at. Real music that is already playing is left alone
     * (it is redrawn in the new design by itself); otherwise the demo song stands in. The card opens a moment later, once the
     * island has finished applying the new setting.
     */
    private void musicPreview(final boolean expanded) {
        if (!Bus.active() || !Prefs.b(this, Prefs.K_MEDIA, true)) return;
        if (!Bus.has("media")) postMusicDemo();
        if (expanded) dh.postDelayed(new Runnable() { @Override public void run() { Bus.expand("media"); } }, 260);
    }

    private void demo(String code) {
        if (!ready()) return;
        if ("m".equals(code)) {
            postMusicDemo();
            Bus.expand("media");
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
            IslandEvent e = new IslandEvent("sys:demo", IslandEvent.SYSTEM);
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
            dh.postDelayed(new Runnable() { @Override public void run() { Bus.remove("p:demo"); } }, 900);
        } else {
            dh.postDelayed(new Runnable() { @Override public void run() { if (Bus.active()) download(Math.min(100, pct + 5)); } }, 280);
        }
    }

    private void clearDemos() {
        // 1) stop the demos' own pending steps first, or e.g. the download loop would put its card straight back
        dh.removeCallbacksAndMessages(null);
        // 2) take every demo away in ONE step; the island rebuilds itself to a clean idle state (see IslandController.clear)
        try { Bus.clear(DEMO_KEYS); } catch (Throwable t) { /* never take the app (and the island) down */ }
    }
}
