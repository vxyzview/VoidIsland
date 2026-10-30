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
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable shadeTimeout = new Runnable() {
        @Override public void run() { Bus.shade(false); } // safety net: never stay hidden forever if a "closed" event was missed
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefs = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            if (ctl != null) ctl.scheduleApply();
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (ctl != null) shutdown(); // reconnect without unbind: start clean
        try {
            ctl = new IslandController(this);
            ctl.attach();
            Bus.attach(ctl);
            sys = new SystemEvents(this);
            sys.start();
            Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefs);
        } catch (Throwable t) {
            // a broken overlay must never crash the accessibility service
            if (ctl != null) { Bus.detach(ctl); ctl = null; }
        }
    }

    private void shade(boolean open) {
        h.removeCallbacks(shadeTimeout);
        if (open) h.postDelayed(shadeTimeout, 25000);
        Bus.shade(open);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent ev) {
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
                if (pk.equals(getPackageName()) || pk.equals("android") || cls.startsWith("android.")) return;
                if (Bus.shadeOpen) shade(false); // another app's window changed, so the panel must be closed
                if (!pk.equals(fg)) { fg = pk; ctl.setForeground(pk); }
                break;
            }
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: {
                Parcelable data = ev.getParcelableData();
                if (data instanceof Notification) {
                    if (ListenerService.connected) return; // the listener already has it, with more detail
                    IslandEvent e = Classifier.from(this, pk, (Notification) data, "a:" + pk + ":" + ev.getEventTime(), -1, false);
                    if (e != null) Bus.post(e);
                } else if (Prefs.b(this, Prefs.K_TOAST, false) && !ev.getText().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (CharSequence t : ev.getText()) { if (sb.length() > 0) sb.append(' '); sb.append(t); }
                    IslandEvent e = new IslandEvent("toast", IslandEvent.SYSTEM);
                    e.title = sb.toString();
                    e.sub = Img.appLabel(this, pk);
                    e.icon = Img.appIcon(this, pk);
                    e.pkg = pk;
                    e.durationMs = 2400;
                    Bus.post(e);
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
