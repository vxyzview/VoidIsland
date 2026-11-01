package com.vxyzview.voidisland;

import android.app.NotificationManager;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.HashMap;

/** Phone-level events: charging, low battery, ringer switch, Do Not Disturb, headphones, Bluetooth, flashlight. */
final class SystemEvents {
    private final Context c;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int lastPlugged = -1, lastLevel = -1, lastStatus = -1, lastDnd = -1;
    private long startedAt;
    private CameraManager cm;
    private CameraManager.TorchCallback torchCb;
    private final HashMap<String, Boolean> torch = new HashMap<String, Boolean>();
    private boolean torchSeen;

    SystemEvents(Context c) { this.c = c; }

    private boolean on() { return Prefs.b(c, Prefs.K_SYS, true); }

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            try {
                String a = i.getAction();
                if (a == null) return;
                if (Intent.ACTION_SCREEN_ON.equals(a) || Intent.ACTION_SCREEN_OFF.equals(a) || Intent.ACTION_USER_PRESENT.equals(a)) {
                    if (Intent.ACTION_SCREEN_ON.equals(a)) { minuteTick(true); Bus.screen(true); }
                    else if (Intent.ACTION_SCREEN_OFF.equals(a)) { minuteTick(false); Bus.screen(false); }
                    Bus.keyguard();
                    if (Intent.ACTION_SCREEN_ON.equals(a)) Bus.clockNow();
                }
                else if (Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)
                    || Intent.ACTION_DATE_CHANGED.equals(a)) Bus.clockNow();
                else if (Intent.ACTION_BATTERY_CHANGED.equals(a)) battery(i);
                else if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(a)) ringer();
                else if (NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED.equals(a)) dnd();
                else if (Intent.ACTION_HEADSET_PLUG.equals(a)) headset(i);
                else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(a)) bluetooth(i, true);
                else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(a)) bluetooth(i, false);
            } catch (Throwable t) { Dbg.e("system-event", t); /* never crash the service over a toast */ }
        }
    };

    /**
     * The once-a-minute clock broadcast keeps the idle clock exact, but it is only worth waking the app for while the display is
     * on: with the screen off it is 1440 pointless wake-ups a day. Registered on screen-on, dropped on screen-off.
     */
    private boolean tickOn;
    private final BroadcastReceiver tickRx = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            try { Bus.clockNow(); } catch (Throwable t) { /* never crash the service over a clock tick */ }
        }
    };

    private void minuteTick(boolean on) {
        if (on == tickOn) return;
        try {
            if (on) {
                IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
                if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(tickRx, tf, Context.RECEIVER_EXPORTED);
                else c.registerReceiver(tickRx, tf);
            } else {
                c.unregisterReceiver(tickRx);
            }
            tickOn = on;
        } catch (Throwable t) { /* not registered / already gone: the screen-on refresh still sets the clock */ }
    }

    private boolean interactive() {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Throwable t) { return true; }
    }

    void start() {
        startedAt = SystemClock.uptimeMillis();
        // Remember the current battery state first so the sticky broadcast we get on registration is not a "change".
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) seedBattery(b);
        lastDnd = dndOn() ? 1 : 0;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        f.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        f.addAction(Intent.ACTION_HEADSET_PLUG);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_DATE_CHANGED);
        // system broadcasts only; Android 14 (target 34) requires saying so explicitly
        if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(rx, f, Context.RECEIVER_EXPORTED);
        else c.registerReceiver(rx, f);
        if (interactive()) minuteTick(true);   // keeps the idle clock exact while the screen is on (the island's own timer stops while the phone sleeps)
        try {
            cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
            torchCb = new CameraManager.TorchCallback() {
                @Override public void onTorchModeChanged(String id, boolean enabled) { torchChanged(id, enabled); }
            };
            cm.registerTorchCallback(torchCb, h);
        } catch (Throwable t) { cm = null; }
    }

    void stop() {
        minuteTick(false);
        try { c.unregisterReceiver(rx); } catch (Throwable t) { /* not registered */ }
        try { if (cm != null && torchCb != null) cm.unregisterTorchCallback(torchCb); } catch (Throwable t) { /* ignore */ }
        h.removeCallbacksAndMessages(null);
    }

    // ---------------------------------------------------------------- helpers

    private IslandEvent toast(String key, String title, String sub, int icon, int accent, long ms) {
        IslandEvent e = new IslandEvent(key, IslandEvent.SYSTEM);
        e.title = title;
        e.sub = sub;
        e.iconType = icon;
        e.accent = accent;
        e.durationMs = ms;
        return e;
    }

    // ---------------------------------------------------------------- battery

    private void seedBattery(Intent i) {
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        lastLevel = level < 0 ? -1 : Math.round(level * 100f / Math.max(1, scale));
        lastPlugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        lastStatus = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    }

    private void battery(Intent i) {
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = level < 0 ? -1 : Math.round(level * 100f / Math.max(1, scale));
        int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        if (lastPlugged >= 0 && Prefs.b(c, Prefs.K_CHG, true) && pct >= 0) {
            if (plugged > 0 && lastPlugged == 0) charge(pct, plugged, false);
            else if (plugged > 0 && status == BatteryManager.BATTERY_STATUS_FULL && lastStatus != BatteryManager.BATTERY_STATUS_FULL) charge(100, plugged, true);
            else if (plugged == 0 && pct <= 15 && lastLevel > 15) low(pct);
            else if (plugged == 0 && pct <= 5 && lastLevel > 5) low(pct);
        }
        lastPlugged = plugged;
        lastLevel = pct;
        lastStatus = status;
    }

    private void charge(int pct, int plugged, boolean full) {
        IslandEvent e = new IslandEvent("charge", IslandEvent.CHARGE);
        e.level = pct;
        e.charging = true;
        e.title = full ? Str.t(c, "fully_charged", "Fully charged") : (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ? Str.t(c, "wireless_charging", "Wireless charging") : Str.t(c, "charging", "Charging"));
        e.accent = Cards.GREEN;
        e.durationMs = 3600;
        Bus.post(e);
    }

    private void low(int pct) {
        IslandEvent e = new IslandEvent("charge", IslandEvent.CHARGE);
        e.level = pct;
        e.charging = false;
        e.title = pct <= 5 ? Str.t(c, "battery_very_low", "Battery very low") : Str.t(c, "low_battery", "Low battery");
        e.accent = Cards.RED;
        e.durationMs = 4200;
        Bus.post(e);
    }

    // ---------------------------------------------------------------- ringer / DND

    private void ringer() {
        if (!on()) return;
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        switch (am.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT:
                Bus.post(toast("ringer", Str.t(c, "silent", "Silent"), Str.t(c, "ringer_off", "Ringer off"), IconView.BELL_OFF, Cards.RED, 2000));
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                Bus.post(toast("ringer", Str.t(c, "vibrate", "Vibrate"), Str.t(c, "ringer_vibrate", "Ringer on vibrate"), IconView.BELL, Cards.ORANGE, 2000));
                break;
            default:
                Bus.post(toast("ringer", Str.t(c, "ring", "Ring"), Str.t(c, "ringer_on", "Ringer on"), IconView.BELL, Cards.WHITE, 2000));
        }
    }

    private boolean dndOn() {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            int f = nm.getCurrentInterruptionFilter();
            return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
        } catch (Throwable t) { return false; }
    }

    private void dnd() {
        int now = dndOn() ? 1 : 0;
        if (now == lastDnd) return;
        lastDnd = now;
        if (!on()) return;
        Bus.post(toast("dnd", Str.t(c, "dnd", "Do Not Disturb"), now == 1 ? Str.t(c, "on", "On") : Str.t(c, "off", "Off"), IconView.MOON, Cards.PURPLE, 2200));
    }

    // ---------------------------------------------------------------- headphones / Bluetooth

    private void headset(Intent i) {
        if (!on() || SystemClock.uptimeMillis() - startedAt < 1500) return; // ignore the sticky replay at start-up
        boolean plugged = i.getIntExtra("state", 0) == 1;
        Bus.post(toast("headset", plugged ? Str.t(c, "headphones_on", "Headphones connected") : Str.t(c, "headphones_off", "Headphones disconnected"), null, IconView.HEADSET,
            plugged ? Cards.GREEN : Cards.SUB, 2200));
    }

    private void bluetooth(Intent i, boolean connected) {
        if (!on()) return;
        BluetoothDevice dev = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String name = null;
        int icon = IconView.BT;
        try {
            if (dev != null) {
                name = dev.getName();
                BluetoothClass bc = dev.getBluetoothClass();
                if (bc != null && bc.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) icon = IconView.HEADSET;
            }
        } catch (Throwable t) { /* BLUETOOTH_CONNECT not granted: fall back to a generic label */ }
        Bus.post(toast("bt", name != null ? name : Str.t(c, "bt_device", "Bluetooth device"), connected ? Str.t(c, "connected", "Connected") : Str.t(c, "disconnected", "Disconnected"), icon,
            connected ? Cards.BLUE : Cards.SUB, 2400));
    }

    // ---------------------------------------------------------------- flashlight

    private void torchChanged(String id, boolean enabled) {
        Boolean was = torch.put(id, enabled);
        boolean any = false;
        for (Boolean b : torch.values()) if (b) any = true;
        if (any) {
            IslandEvent e = new IslandEvent("torch", IslandEvent.SYSTEM);
            e.live = true;
            e.priority = 30;
            e.title = Str.t(c, "flashlight", "Flashlight");
            e.sub = "On";
            e.iconType = IconView.BOLT;
            e.accent = Cards.YELLOW;
            Bus.post(e);
        } else {
            Bus.remove("torch");
        }
        // The callback replays the current state for every camera at registration: don't pop for that.
        if (torchSeen && was != null && was != enabled && on()) {
            Bus.post(toast("torch_pop", Str.t(c, "flashlight", "Flashlight"), enabled ? Str.t(c, "on", "On") : Str.t(c, "off", "Off"), IconView.BOLT, enabled ? Cards.YELLOW : Cards.SUB, 1800));
        }
        if (!torchSeen) h.postDelayed(new Runnable() { @Override public void run() { torchSeen = true; } }, 1200);
    }
}
