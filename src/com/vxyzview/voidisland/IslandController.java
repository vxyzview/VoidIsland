package com.vxyzview.voidisland;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Decides WHAT the island shows (priorities, popups, queue) and owns the overlay window. Main thread only.
 *
 * Two window layers:
 *  - APP layer (TYPE_APPLICATION_OVERLAY): sits UNDER the status bar and the notification shade, so Quick Settings
 *    naturally cover the island, exactly like a native feature. Needs "Display over other apps".
 *  - ACCESSIBILITY layer: sits above everything (even the status bar). The island hides itself while the shade is open.
 */
final class IslandController implements IslandView.Host {
    private final AccessibilityService svc;
    private WindowManager wm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final float d;

    private IslandView view;
    private WindowManager.LayoutParams lp;
    private boolean attached, layerApp, shadeOpen, locked;
    private Style style = new Style();
    private int screenW, screenH;
    private boolean landscape;
    private float cutCx = Float.NaN, cutCy = Float.NaN;

    private final LinkedHashMap<String, IslandEvent> live = new LinkedHashMap<String, IslandEvent>();
    private final ArrayDeque<IslandEvent> queue = new ArrayDeque<IslandEvent>();
    private IslandEvent popup, userExpanded, shown;
    private int shownMode = IslandView.HIDDEN;
    private String pinnedKey, fg = "";
    private boolean restyle;   // next present() should morph, not cross-fade (settings were just changed)

    private final Runnable popupEnd = new Runnable() {
        @Override public void run() { popup = null; evaluate(); }
    };
    private final Runnable collapse = new Runnable() {
        @Override public void run() { userExpanded = null; evaluate(); }
    };
    private final Runnable applyRun = new Runnable() {
        @Override public void run() { applyPrefs(); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            IslandEvent e = shown;
            if (e == null) return;
            Runnable t = shownMode == IslandView.COMPACT ? e.tC : e.tE;
            if (t != null) { t.run(); h.postDelayed(this, 400); }
        }
    };

    IslandController(AccessibilityService svc) {
        this.svc = svc;
        this.d = svc.getResources().getDisplayMetrics().density;
    }

    private int dp(float v) { return Math.round(v * d); }

    boolean isAppLayer() { return layerApp; }

    // ------------------------------------------------------------------ window

    void attach() {
        if (attached) return;
        style = Style.load(svc);
        layerApp = wantApp(style);
        attachWindow();
    }

    private boolean wantApp(Style s) {
        if (s.qs != Style.QS_UNDER) return false;
        try { return Settings.canDrawOverlays(svc); } catch (Throwable t) { return false; }
    }

    private int statusBarPx() {
        int id = svc.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? svc.getResources().getDimensionPixelSize(id) : dp(24);
    }

    private void attachWindow() {
        readScreen();
        Cards.apply(style);
        view = new IslandView(svc, this, d);
        view.configure(style, screenW, statusBarPx(), layerApp);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        int type = layerApp ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        wm = (WindowManager) (layerApp ? svc.getApplicationContext() : svc).getSystemService(Context.WINDOW_SERVICE);
        lp = new WindowManager.LayoutParams(view.winW(), view.winH(), type, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.setTitle("VoidIsland");
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.setFitInsetsTypes(0);
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        position();
        try {
            wm.addView(view, lp);
        } catch (RuntimeException e) {
            if (layerApp) { // overlay permission revoked / refused: fall back to the accessibility layer
                layerApp = false;
                attachWindow();
                return;
            }
            throw e;
        }
        attached = true;
        locked = isLocked();
        view.post(new Runnable() {
            @Override public void run() { readCutout(); position(); updateLayout(); }
        });
    }

    void detach() {
        h.removeCallbacksAndMessages(null);
        if (attached) {
            try { wm.removeViewImmediate(view); } catch (Throwable t) { /* already gone */ }
        }
        attached = false;
        live.clear();
        queue.clear();
    }

    private void reattach(boolean app) {
        try { if (attached) wm.removeViewImmediate(view); } catch (Throwable t) { /* already gone */ }
        attached = false;
        h.removeCallbacks(tick);
        shown = null;
        shownMode = IslandView.HIDDEN;
        layerApp = app;
        try {
            attachWindow();
            evaluate();
        } catch (Throwable t) {
            attached = false; // could not show the island at all; the service stays alive and will retry on the next settings change
        }
    }

    private void updateLayout() {
        if (!attached) return;
        try { wm.updateViewLayout(view, lp); } catch (Throwable t) { /* window gone */ }
    }

    @Override public void applyWindow(int w, int hgt, boolean touchable) {
        lp.width = w;
        lp.height = hgt;
        if (touchable) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        updateLayout();
    }

    private void readScreen() {
        DisplayMetrics dm = svc.getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        landscape = svc.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void readCutout() {
        cutCx = Float.NaN;
        cutCy = Float.NaN;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                DisplayManager dm = (DisplayManager) svc.getSystemService(Context.DISPLAY_SERVICE);
                Display disp = dm.getDisplay(Display.DEFAULT_DISPLAY);
                useCutout(disp == null ? null : disp.getCutout(), 0, 0);
            } else if (Build.VERSION.SDK_INT >= 28 && view != null) {
                WindowInsets wi = view.getRootWindowInsets();
                if (wi != null) {
                    int[] loc = new int[2];
                    view.getLocationOnScreen(loc);
                    useCutout(wi.getDisplayCutout(), loc[0], loc[1]);
                }
            }
        } catch (Throwable t) { /* no cutout info: use defaults */ }
    }

    private void useCutout(DisplayCutout dc, int ox, int oy) {
        if (dc == null) return;
        Rect best = null;
        for (Rect r : dc.getBoundingRects()) {
            if (r.top + oy < dp(40) && (best == null || r.top < best.top)) best = r;
        }
        if (best != null) {
            cutCx = ox + best.exactCenterX();
            cutCy = oy + best.exactCenterY();
        }
    }

    /** Auto-aligns to the camera hole when there is one (notch: glued to the top edge), then applies the margins. */
    private void position() {
        int top;
        if (style.shape == Style.NOTCH) {
            top = 0;
        } else {
            float cy = Float.isNaN(cutCy) ? dp(14) : cutCy;
            top = Math.max(0, Math.max(dp(2), Math.round(cy - dp(style.cH) / 2f)) + dp(style.mTop));
        }
        float cx = Float.isNaN(cutCx) || Math.abs(cutCx - screenW / 2f) > screenW * 0.15f ? screenW / 2f : cutCx;
        lp.x = Math.round(cx - screenW / 2f) + dp(style.mX);
        lp.y = top;
    }

    /** Debounced: called whenever a setting changes (slider drags fire a lot). */
    void scheduleApply() {
        h.removeCallbacks(applyRun);
        h.postDelayed(applyRun, 90);
    }

    void applyPrefs() {
        if (!attached) return;
        style = Style.load(svc);
        boolean app = wantApp(style);
        if (app != layerApp) { reattach(app); return; }
        readScreen();
        Cards.apply(style);
        view.configure(style, screenW, statusBarPx(), layerApp);
        position();
        updateLayout();
        locked = isLocked();
        shown = null;
        shownMode = IslandView.HIDDEN;
        restyle = true;
        evaluate();
        restyle = false;
    }

    void onConfigChanged() {
        if (!attached) return;
        readScreen();
        readCutout();
        applyPrefs();
    }

    // ------------------------------------------------------------------ panels / lock

    private boolean isLocked() {
        try { return ((KeyguardManager) svc.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked(); } catch (Throwable t) { return false; }
    }

    /** The notification shade / Quick Settings opened or closed (only matters on the accessibility layer). */
    void setShade(boolean open) {
        if (shadeOpen == open) return;
        shadeOpen = open;
        evaluate();
    }

    void refreshLock() {
        boolean now = isLocked();
        if (now == locked) return;
        locked = now;
        evaluate();
    }

    // ------------------------------------------------------------------ events in

    private boolean allowed(IslandEvent e) {
        switch (e.type) {
            case IslandEvent.MEDIA: return Prefs.b(svc, Prefs.K_MEDIA, true);
            case IslandEvent.CALL: return Prefs.b(svc, Prefs.K_CALL, true);
            case IslandEvent.PAYMENT: return Prefs.b(svc, Prefs.K_PAY, true);
            case IslandEvent.CHARGE: return Prefs.b(svc, Prefs.K_CHG, true);
            case IslandEvent.SYSTEM: return Prefs.b(svc, Prefs.K_SYS, true) || "toast".equals(e.key);
            case IslandEvent.PREVIEW: return true;
            default: return Prefs.b(svc, Prefs.K_NOTIF, true);
        }
    }

    void post(IslandEvent e) {
        if (!attached) return;
        if (!allowed(e) || (e.pkg != null && Prefs.blocked(svc).contains(e.pkg))) return;
        if (e.type == IslandEvent.NOTIF && e.pkg != null && e.pkg.equals(fg)) return; // don't banner the app you're in
        if (e.live) {
            IslandEvent old = live.get(e.key);
            if (old == null) live.put(e.key, e);
            else old.copyFrom(e);
        } else {
            if (popup != null && popup.key.equals(e.key)) {
                popup = e;
                schedulePopup(e);
            } else {
                for (Iterator<IslandEvent> it = queue.iterator(); it.hasNext();) if (it.next().key.equals(e.key)) it.remove();
                if (queue.size() >= 4) queue.pollFirst();
                queue.addLast(e);
            }
        }
        evaluate();
    }

    void remove(String key) {
        IslandEvent old = live.remove(key);
        if (old != null) {
            if (userExpanded == old) userExpanded = null;
            if (key.equals(pinnedKey)) pinnedKey = null;
        }
        if (popup != null && popup.key.equals(key)) { popup = null; h.removeCallbacks(popupEnd); }
        evaluate();
    }

    void removeByNkey(String nkey, boolean liveOnly) {
        if (nkey == null) return;
        ArrayList<String> dead = new ArrayList<String>();
        for (IslandEvent e : live.values()) if (nkey.equals(e.nkey)) dead.add(e.key);
        for (String k : dead) {
            IslandEvent old = live.remove(k);
            if (userExpanded == old) userExpanded = null;
            if (k.equals(pinnedKey)) pinnedKey = null;
        }
        if (!liveOnly && popup != null && nkey.equals(popup.nkey)) { popup = null; h.removeCallbacks(popupEnd); }
        evaluate();
    }

    void refresh() { evaluate(); }

    void expandKey(String key) { expandLive(live.get(key)); }

    void setForeground(String pkg) {
        if (pkg == null || pkg.equals(fg)) return;
        fg = pkg;
        evaluate();
    }

    // ------------------------------------------------------------------ what to show

    private boolean hiddenBySource(IslandEvent e) {
        return Prefs.b(svc, Prefs.K_INAPP, true) && e.pkg != null && e.pkg.equals(fg)
            && (e.type == IslandEvent.MEDIA || e.type == IslandEvent.CALL);
    }

    private IslandEvent ring() {
        for (IslandEvent e : live.values()) if (e.popup && !hiddenBySource(e) && allowed(e)) return e;
        return null;
    }

    private IslandEvent topLive() {
        IslandEvent best = null;
        for (IslandEvent e : live.values()) {
            if (hiddenBySource(e) || !allowed(e)) continue;
            if (pinnedKey != null && pinnedKey.equals(e.key)) return e;
            if (best == null || e.priority >= best.priority) best = e;
        }
        return best;
    }

    private boolean suppressed() {
        if (!Prefs.b(svc, Prefs.K_ON, true)) return true;
        if (landscape && Prefs.b(svc, Prefs.K_LAND, true)) return true;
        if (!layerApp) {
            if (locked && !style.lockShow) return true;                      // above-status-bar layer would draw over the lock screen
            if (style.qs != Style.QS_TOP && shadeOpen) return true;          // hide while Quick Settings / shade are open
        }
        return false;
    }

    private void schedulePopup(IslandEvent e) {
        h.removeCallbacks(popupEnd);
        h.postDelayed(popupEnd, Math.max(1200, e.durationMs));
    }

    private void evaluate() {
        if (!attached) return;
        IslandEvent want = null;
        int mode = IslandView.HIDDEN;
        if (!suppressed()) {
            IslandEvent ring = ring();
            if (ring != null) {
                want = ring;
                mode = IslandView.EXPANDED;
            } else {
                if (popup == null && !queue.isEmpty()) {
                    popup = queue.pollFirst();
                    schedulePopup(popup);
                }
                if (popup != null) {
                    want = popup;
                    mode = IslandView.EXPANDED;
                } else if (userExpanded != null && live.get(userExpanded.key) == userExpanded) {
                    want = userExpanded;
                    mode = IslandView.EXPANDED;
                } else {
                    IslandEvent top = topLive();
                    if (top != null) { want = top; mode = IslandView.COMPACT; }
                }
            }
        }
        show(want, mode);
    }

    private void show(IslandEvent want, int mode) {
        if (want == shown && mode == shownMode) {
            if (want != null && want.dirty) {
                want.dirty = false;
                IslandEvent.Updater u = mode == IslandView.COMPACT ? want.uC : want.uE;
                if (u == null || !u.update()) view.present(want, mode);
            }
            return;
        }
        shown = want;
        shownMode = mode;
        if (want != null) want.dirty = false;
        view.present(want, mode, !restyle);
        h.removeCallbacks(tick);
        if (want != null) h.postDelayed(tick, 60);
    }

    // ------------------------------------------------------------------ gestures

    private void expandLive(IslandEvent e) {
        if (e == null) return;
        userExpanded = e;
        h.removeCallbacks(collapse);
        h.postDelayed(collapse, 8000);
        evaluate();
    }

    @Override public void onTap(int mode) {
        IslandEvent e = shown;
        if (e == null) return;
        if (mode == IslandView.COMPACT) {
            expandLive(e);
        } else if (mode == IslandView.EXPANDED) {
            Cards.open(svc, e);
            if (e == popup) { popup = null; h.removeCallbacks(popupEnd); }
            else if (e == userExpanded) userExpanded = null;
            evaluate();
        }
    }

    @Override public void onSwipe(int dir) {
        IslandEvent e = shown;
        if (e == null) return;
        if (dir == 0) { // up: put it away
            if (shownMode == IslandView.EXPANDED) {
                if (e.popup) e.popup = false;               // ringing call -> keep as compact pill
                else if (e == popup) { popup = null; h.removeCallbacks(popupEnd); }
                else userExpanded = null;
                evaluate();
            }
        } else if (dir == 1) { // down: expand, or pull the notification shade
            if (shownMode == IslandView.COMPACT) expandLive(e);
            else svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
        } else if (shownMode == IslandView.COMPACT) {
            cycle(dir == 2 ? 1 : -1);
        }
    }

    @Override public void onOutside() {
        if (shownMode == IslandView.EXPANDED && shown != null && shown == userExpanded) {
            userExpanded = null;
            evaluate();
        }
    }

    /** Swipe sideways on the pill to flip between simultaneous live activities. */
    private void cycle(int dir) {
        List<IslandEvent> l = new ArrayList<IslandEvent>();
        for (IslandEvent e : live.values()) if (!hiddenBySource(e) && allowed(e)) l.add(e);
        if (l.size() < 2) return;
        int i = Math.max(0, l.indexOf(shown));
        pinnedKey = l.get((i + dir + l.size()) % l.size()).key;
        evaluate();
    }
}
