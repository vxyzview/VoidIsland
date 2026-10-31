package com.vxyzview.voidisland;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import java.util.Locale;

/**
 * Helps the island survive phone makers' "battery savers" that close background apps (and with them the island).
 * The ROM check is pure string logic so the unit checks cover it; everything that touches Android is guarded.
 */
final class KeepAlive {
    private KeepAlive() {}

    /** Brand families known for aggressively closing background apps. */
    static boolean aggressive(String manufacturer) {
        return family(manufacturer) != null;
    }

    /** Short, human name of the brand family for a manufacturer string, or null when the ROM is not known to be aggressive. */
    static String family(String manufacturer) {
        if (manufacturer == null) return null;
        String m = manufacturer.trim().toLowerCase(Locale.ROOT);
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) return "Xiaomi";
        if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) return "Oppo / Realme / OnePlus";
        if (m.contains("vivo") || m.contains("iqoo")) return "Vivo";
        if (m.contains("huawei") || m.contains("honor")) return "Huawei / Honor";
        if (m.contains("samsung")) return "Samsung";
        if (m.contains("asus")) return "Asus";
        return null;
    }

    static boolean aggressiveHere() { return aggressive(Build.MANUFACTURER); }

    static String familyHere() { return family(Build.MANUFACTURER); }

    /** True when the system no longer applies battery optimisation to this app. */
    static boolean batteryUnrestricted(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
        } catch (Throwable t) { return false; }
    }

    private static Intent comp(String pkg, String cls) {
        return new Intent().setComponent(new ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * The phone maker's own "autostart / background" screens, most likely first. They are private to each ROM and change
     * between versions, so the caller tries them one by one and falls back to the app's info page.
     */
    static Intent[] autostartScreens(String manufacturer) {
        String f = family(manufacturer);
        if (f == null) return new Intent[0];
        if (f.startsWith("Xiaomi")) return new Intent[]{
            comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")};
        if (f.startsWith("Oppo")) return new Intent[]{
            comp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            comp("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            comp("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")};
        if (f.startsWith("Vivo")) return new Intent[]{
            comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            comp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")};
        if (f.startsWith("Huawei")) return new Intent[]{
            comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            comp("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")};
        if (f.equals("Samsung")) return new Intent[]{
            comp("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")};
        return new Intent[]{comp("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")};
    }

    /** Plain-language steps for the brand, shown next to the button. */
    static String hint(String manufacturer) {
        String f = family(manufacturer);
        if (f == null) return "";
        if (f.startsWith("Xiaomi")) return "Turn on Autostart for VoidIsland, then set its Battery saver to No restrictions.";
        if (f.startsWith("Oppo")) return "Allow auto-launch and background activity for VoidIsland, and set its battery use to Unrestricted.";
        if (f.startsWith("Vivo")) return "Allow background running and autostart for VoidIsland in the battery and permission settings.";
        if (f.startsWith("Huawei")) return "Set VoidIsland to Manage manually, with Auto-launch, Secondary launch and Run in background all on.";
        if (f.equals("Samsung")) return "Remove VoidIsland from Sleeping apps and set its battery use to Unrestricted.";
        return "Allow VoidIsland to start and run in the background.";
    }
}
