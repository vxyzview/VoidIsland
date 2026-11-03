package com.vxyzview.voidisland;

import android.app.NotificationManager;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
    // connected audio devices (name -> glyph), newest last: the "Keep audio devices on the island" pill shows the newest
    private LinkedHashMap<String, Integer> audio = new LinkedHashMap<String, Integer>();
    private final HashMap<String, Integer> btIcons = new HashMap<String, Integer>();   // Bluetooth audio device name -> glyph, learned from the connect events
    private boolean liveOn;   // the charging pill is currently posted
    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION", EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE",
        EXTRA_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE";
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsCb = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            try {
                if (Prefs.K_BTLIVE.equals(key) || Prefs.K_SYS.equals(key)) audioChanged();
                else if (Prefs.K_CHGLIVE.equals(key) || Prefs.K_CHG.equals(key)) {
                    Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    if (b != null) liveCharge(b);
                }
            } catch (Throwable t) { Dbg.e("system-pref", t); }
        }
    };

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
                else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(a)) airplane(i);
                else if (VOLUME_CHANGED.equals(a)) volume(i);
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
                if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(tickRx, tf, Context.RECEIVER_NOT_EXPORTED);   // system broadcasts still arrive; other apps cannot send us fake ones
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
        f.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        f.addAction(VOLUME_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_DATE_CHANGED);
        // system broadcasts only; Android 14 (target 34) requires saying so explicitly. NOT_EXPORTED still receives system
        // broadcasts but refuses ones sent by other apps, so none of them can fake a charger, a headset or a ringer change.
        if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED);
        else c.registerReceiver(rx, f);
        seedAudio();
        if (b != null) liveCharge(b);
        try { Prefs.sp(c).registerOnSharedPreferenceChangeListener(prefsCb); } catch (Throwable t) { /* settings changes then apply at the next event */ }
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
        try { Prefs.sp(c).unregisterOnSharedPreferenceChangeListener(prefsCb); } catch (Throwable t) { /* not registered */ }
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
            if (plugged > 0 && lastPlugged == 0) charge(i, pct, plugged, false);
            else if (plugged > 0 && status == BatteryManager.BATTERY_STATUS_FULL && lastStatus != BatteryManager.BATTERY_STATUS_FULL) charge(i, 100, plugged, true);
            else if (plugged == 0 && pct <= 15 && lastLevel > 15) low(i, pct);
            else if (plugged == 0 && pct <= 5 && lastLevel > 5) low(i, pct);
        }
        liveCharge(i);
        lastPlugged = plugged;
        lastLevel = pct;
        lastStatus = status;
    }

    /** Fills in what the phone knows about the charge: the charger, temperature, voltage, current and the time to full. Whatever it cannot say stays unknown. */
    private void details(IslandEvent e, Intent i) {
        e.tempC10 = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        e.voltMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        try {
            BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
            if (bm == null) return;
            int v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);   // microamps on most phones, milliamps on a few
            if (v != Integer.MIN_VALUE && v != 0) e.currentMa = Math.abs(v) >= 20000 ? v / 1000 : v;
            if (Build.VERSION.SDK_INT >= 28) {
                long ms = bm.computeChargeTimeRemaining();
                e.etaMin = ms > 0 ? (int) Math.round(ms / 60000.0) : -1;
            }
        } catch (Throwable t) { /* no sensor: the card simply has fewer facts */ }
    }

    private IslandEvent chargeEvent(String key, Intent i, int pct, int plugged, boolean full) {
        IslandEvent e = new IslandEvent(key, IslandEvent.CHARGE);
        e.level = pct;
        e.charging = true;
        e.plugged = plugged;
        e.title = full ? Str.t(c, "fully_charged", "Fully charged") : (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ? Str.t(c, "wireless_charging", "Wireless charging") : Str.t(c, "charging", "Charging"));
        e.accent = Cards.GREEN;
        e.durationMs = 3600;
        details(e, i);
        return e;
    }

    private void charge(final Intent i, final int pct, final int plugged, final boolean full) {
        IslandEvent e = chargeEvent("charge", i, pct, plugged, full);
        Bus.post(e);
        if (e.currentMa == Integer.MIN_VALUE || e.etaMin < 0) {
            // the current and the time to full are often not ready the instant a charger is plugged in: look again once, while the card is still up
            h.postDelayed(new Runnable() {
                @Override public void run() {
                    try {
                        Intent now = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                        if (now == null || now.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) return;   // unplugged meanwhile
                        IslandEvent again = chargeEvent("charge", now, pct, plugged, full);
                        again.durationMs = 2800;
                        Bus.post(again);
                    } catch (Throwable t) { Dbg.e("charge-refresh", t); }
                }
            }, 1100L);
        }
    }

    private void low(Intent i, int pct) {
        IslandEvent e = new IslandEvent("charge", IslandEvent.CHARGE);
        e.level = pct;
        e.charging = false;
        e.title = pct <= 5 ? Str.t(c, "battery_very_low", "Battery very low") : Str.t(c, "low_battery", "Low battery");
        e.accent = Cards.RED;
        e.durationMs = 4200;
        e.tempC10 = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        Bus.post(e);
    }

    /** "Keep charging on the island": while a charger is connected the island keeps a charging pill (live level, charger, time to full). */
    private void liveCharge(Intent i) {
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = level < 0 ? -1 : Math.round(level * 100f / Math.max(1, scale));
        int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        boolean full = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL;
        boolean want = pct >= 0 && plugged > 0 && Prefs.b(c, Prefs.K_CHG, true) && Prefs.b(c, Prefs.K_CHGLIVE, false);
        if (!want) {
            if (liveOn) { liveOn = false; Bus.remove("charge_live"); }
            return;
        }
        liveOn = true;
        IslandEvent e = chargeEvent("charge_live", i, pct, plugged, full);
        e.live = true;
        e.priority = 25;   // above the idle clock, below music, calls, timers and maps
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

    private static final String WIRED = "wired:";   // marks the wired headset in the connected-devices list

    private void headset(Intent i) {
        boolean plugged = i.getIntExtra("state", 0) == 1;
        syncAudioSoon();
        if (!on() || SystemClock.uptimeMillis() - startedAt < 1500) return; // ignore the sticky replay at start-up
        Bus.post(toast("headset", plugged ? Str.t(c, "headphones_on", "Headphones connected") : Str.t(c, "headphones_off", "Headphones disconnected"), null, IconView.HEADSET,
            plugged ? Cards.GREEN : Cards.SUB, 2200));
    }

    /** The glyph for a Bluetooth device: headphones, a speaker, a phone, a watch, or the plain Bluetooth mark. */
    private static int btIcon(BluetoothClass bc) {
        if (bc == null) return IconView.BT;
        switch (bc.getMajorDeviceClass()) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO: {
                int d = bc.getDeviceClass();
                return d == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES || d == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET
                    || d == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE ? IconView.HEADSET : IconView.VOLUME;
            }
            case BluetoothClass.Device.Major.PHONE: return IconView.PHONE;
            case BluetoothClass.Device.Major.WEARABLE: return IconView.CLOCK;
            default: return IconView.BT;
        }
    }

    private void bluetooth(Intent i, boolean connected) {
        BluetoothDevice dev = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String name = null;
        int icon = IconView.BT;
        boolean sound = false;
        try {
            if (dev != null) {
                name = dev.getName();
                BluetoothClass bc = dev.getBluetoothClass();
                icon = btIcon(bc);
                sound = bc != null && bc.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO;
            }
        } catch (Throwable t) { /* BLUETOOTH_CONNECT not granted: fall back to a generic label */ }
        String shown = name != null ? name : Str.t(c, "bt_device", "Bluetooth device");
        if (sound && name != null) btIcons.put(name, icon);   // remembered so the audio pill can show a speaker as a speaker
        if (sound) syncAudioSoon();
        if (!on()) return;
        Bus.post(toast("bt", shown, connected ? Str.t(c, "connected", "Connected") : Str.t(c, "disconnected", "Disconnected"), icon,
            connected ? Cards.BLUE : Cards.SUB, 2400));
    }

    /** Looks at the audio outputs a moment after something connected or disconnected (Android updates its list slightly late), and once more to be sure. */
    private void syncAudioSoon() {
        h.removeCallbacks(audioSync);
        h.postDelayed(audioSync, 700L);
        h.postDelayed(audioSync, 3000L);
    }

    private final Runnable audioSync = new Runnable() { @Override public void run() { syncAudio(); } };

    /** Rebuilds the connected-devices list from AudioManager (the truth: no entry can get stuck after a disconnect), keeping the order they connected in. */
    private void syncAudio() {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            LinkedHashMap<String, Integer> now = new LinkedHashMap<String, Integer>();
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    now.put(WIRED, IconView.HEADSET);
                } else if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_HEARING_AID || t == 26 /* TYPE_BLE_HEADSET, API 31 */) {
                    CharSequence n = d.getProductName();
                    String name = n != null && n.length() > 0 ? n.toString() : Str.t(c, "bt_device", "Bluetooth device");
                    Integer ic = btIcons.get(name);
                    now.put(name, ic != null ? ic : IconView.HEADSET);
                }
            }
            LinkedHashMap<String, Integer> next = new LinkedHashMap<String, Integer>();
            for (Map.Entry<String, Integer> en : audio.entrySet()) if (now.containsKey(en.getKey())) next.put(en.getKey(), now.get(en.getKey()));
            for (Map.Entry<String, Integer> en : now.entrySet()) if (!next.containsKey(en.getKey())) next.put(en.getKey(), en.getValue());
            audio = next;
            audioChanged();
        } catch (Throwable t) { Dbg.e("audio-sync", t); }
    }

    private void seedAudio() { syncAudio(); }

    /** "Keep audio devices on the island": a pill with the newest connected headphones / speaker / car until it disconnects. */
    private void audioChanged() {
        String name = null;
        int icon = IconView.HEADSET;
        for (Map.Entry<String, Integer> en : audio.entrySet()) { name = en.getKey(); icon = en.getValue(); }
        if (name == null || !on() || !Prefs.b(c, Prefs.K_BTLIVE, false)) { Bus.remove("audio:live"); return; }
        if (WIRED.equals(name)) name = Str.t(c, "headphones", "Headphones");
        IslandEvent e = new IslandEvent("audio:live", IslandEvent.SYSTEM);
        e.live = true;
        e.priority = 20;
        e.title = name;
        e.sub = Str.t(c, "connected", "Connected");
        e.iconType = icon;
        e.accent = Cards.BLUE;
        Bus.post(e);
    }

    private void airplane(Intent i) {
        if (!on() || !Prefs.b(c, Prefs.K_AIRPLANE, true) || SystemClock.uptimeMillis() - startedAt < 1500) return;
        boolean state = i.getBooleanExtra("state", false);
        Bus.post(toast("airplane", Str.t(c, "airplane", "Airplane mode"), state ? Str.t(c, "on", "On") : Str.t(c, "off", "Off"), IconView.AIRPLANE, state ? Cards.ORANGE : Cards.SUB, 2200));
    }

    /** The volume bar (off by default: Android shows its own): the key is the same while a button is held, so the card just updates. */
    private void volume(Intent i) {
        if (!on() || !Prefs.b(c, Prefs.K_VOLUME, false) || SystemClock.uptimeMillis() - startedAt < 1500) return;
        int stream = i.getIntExtra(EXTRA_STREAM, -1), value = i.getIntExtra(EXTRA_VALUE, -1);
        String name;
        switch (stream) {
            case AudioManager.STREAM_MUSIC: name = Str.t(c, "vol_media", "Media volume"); break;
            case AudioManager.STREAM_RING: name = Str.t(c, "vol_ring", "Ring volume"); break;
            case AudioManager.STREAM_VOICE_CALL: name = Str.t(c, "vol_call", "Call volume"); break;
            case AudioManager.STREAM_ALARM: name = Str.t(c, "vol_alarm", "Alarm volume"); break;
            default: return;
        }
        if (value < 0) return;
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            int max = Math.max(1, am.getStreamMaxVolume(stream)), pct = Math.round(Math.min(max, value) * 100f / max);
            IslandEvent e = toast("volume", name, pct + "%", pct == 0 ? IconView.BELL_OFF : IconView.VOLUME, pct == 0 ? Cards.RED : Cards.BLUE, 1600);
            e.progress = pct;
            Bus.post(e);
        } catch (Throwable t) { /* no audio service */ }
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
