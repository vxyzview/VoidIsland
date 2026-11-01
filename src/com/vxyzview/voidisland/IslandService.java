package com.vxyzview.voidisland;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

/**
 * Hosts the island overlay and follows which app is in front. It does not read screen content
 * (canRetrieveWindowContent = false). It also notices when the notification shade / Quick Settings open
 * (from SystemUI's own accessibility events) so the island can get out of the way.
 */
public final class IslandService extends AccessibilityService {
    private static final String SYSUI = "com.android.systemui";

    private IslandController ctl;
    private SystemEvents sys;
    private String fg = "";
    private String ime = "";   // the current keyboard's package: never counts as "an app is open"
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable shadeTimeout = new Runnable() {
        @Override public void run() { Bus.shade(false); } // safety net: never stay hidden forever if a "closed" event was missed
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefs = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            if (ctl != null) ctl.scheduleApply();
        }
    };

    private int setupTries;
    private final Runnable retrySetup = new Runnable() {
        @Override public void run() { if (ctl == null) setup(); }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (ctl != null) shutdown(); // reconnect without unbind: start clean
        setupTries = 0;
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (v != null && v.length() > 0) Cards.appVersion = v;
        } catch (Throwable t) { /* keep the default */ }
        setup();
    }

    /** Builds the island. A failure (window refused right after boot, say) is retried a few times instead of leaving the island dead. */
    private void setup() {
        try {
            ctl = new IslandController(this);
            ctl.attach();
            Bus.attach(ctl);
            sys = new SystemEvents(this);
            sys.start();
            readIme();
            Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefs);
        } catch (Throwable t) {
            // a broken overlay must never crash the accessibility service: clean up fully, then try again shortly
            try { if (sys != null) { sys.stop(); sys = null; } } catch (Throwable ignored) { /* ignore */ }
            if (ctl != null) { Bus.detach(ctl); try { ctl.detach(); } catch (Throwable ignored) { /* ignore */ } ctl = null; }
            if (++setupTries <= 3) h.postDelayed(retrySetup, 3000L * setupTries);
        }
    }

    /** The current keyboard's package (it can be switched at any time, so this is re-read when an unknown window shows up). */
    private void readIme() {
        try {
            String im = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (im != null) {
                int slash = im.indexOf('/');
                ime = slash >= 0 ? im.substring(0, slash) : im;
            }
        } catch (Throwable t) { /* no IME info: keyboards may tick the foreground, harmless */ }
    }

    private void shade(boolean open) {
        h.removeCallbacks(shadeTimeout);
        if (open) h.postDelayed(shadeTimeout, 25000);
        Bus.shade(open);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent ev) {
        // an exception here would kill the process and switch the accessibility service off: never let one escape
        try { handleEvent(ev); } catch (Throwable t) { /* ignore: the next event starts clean */ }
    }

    private void handleEvent(AccessibilityEvent ev) {
        if (ev == null || ctl == null) return;
        CharSequence p = ev.getPackageName();
        if (p == null) return;
        String pk = p.toString();
        switch (ev.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
                CharSequence cn = ev.getClassName();
                String cls = cn == null ? "" : cn.toString();
                if (pk.equals(SYSUI)) { onSystemUi(ev, cls); return; }
                // real activities only: skip our own windows, keyboards, dialogs and popups
                if (pk.equals(getPackageName())) {
                    // our own screens behave like the home screen so the island (and the Try-it demos) stay alive
                    if (!fg.isEmpty()) { fg = ""; ctl.clearForeground(); }
                    return;
                }
                if (!pk.equals(fg) && !pk.equals(ime)) readIme();   // the keyboard may have been switched since we last looked
                if (pk.equals(ime) || pk.equals("android") || cls.startsWith("android.")) return;
                if (Bus.shadeOpen) shade(false); // another app's window changed, so the panel must be closed
                if (!pk.equals(fg)) { fg = pk; ctl.setForeground(pk); }
                break;
            }
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: {
                Parcelable data = ev.getParcelableData();
                if (data instanceof Notification) {
                    if (ListenerService.connected) return; // the listener already has it, with more detail
                    IslandEvent e = Classifier.from(this, pk, (Notification) data, "a:" + pk + ":" + ev.getEventTime(), -1, false);
                    if (e != null && Bus.post(e) && e.chat != null) Rules.chatShown(e.chat, System.currentTimeMillis());
                }
                break;
            }
            default:
                break;
        }
    }

    /** SystemUI announces its panes ("Notification shade.", "Quick settings.") when they appear / disappear. */
    private void onSystemUi(AccessibilityEvent ev, String cls) {
        StringBuilder sb = new StringBuilder();
        for (CharSequence t : ev.getText()) { if (sb.length() > 0) sb.append(' '); sb.append(t); }
        int change = Build.VERSION.SDK_INT >= 28 ? ev.getContentChangeTypes() : 0;
        Bus.sysCount++;
        Bus.sysSeenAt = System.currentTimeMillis();
        Bus.sysLast = sb + " \u00b7 " + cls + " \u00b7 " + change;
        if (!ShadeMatch.panel(sb.toString(), cls)) return;
        shade((change & AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED) == 0);
    }

    @Override public void onInterrupt() { }

    /**
     * The service lives as long as the phone does, so it must give memory back when the system asks. Caches are only
     * ever a convenience (icons, artwork, lyrics already fetched): all of them refill on demand.
     */
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        try {
            if (Trim.shortage(level)) {
                Img.trim();
                MediaTracker.trim();
                Lyrics.trim(Trim.keepSongs(level));
            }
        } catch (Throwable t) { /* a failed trim must never take the service down */ }
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (ctl != null) ctl.onConfigChanged();
    }

    private void shutdown() {
        h.removeCallbacksAndMessages(null);
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefs);
        if (sys != null) { sys.stop(); sys = null; }
        if (ctl != null) { Bus.detach(ctl); ctl.detach(); ctl = null; }
        Bus.shadeOpen = false;
        fg = "";   // a new controller starts with no foreground app, so this must not remember the old one
    }

    @Override public boolean onUnbind(Intent i) {
        shutdown();
        return super.onUnbind(i);
    }

    @Override public void onDestroy() {
        shutdown();
        super.onDestroy();
    }
}
