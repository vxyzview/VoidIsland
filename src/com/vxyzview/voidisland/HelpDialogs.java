package com.vxyzview.voidisland;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * The lists under Behavior > Help: recent banners, why a banner did not show, why Android stopped the island, and the diagnostic log.
 * All of them are in-memory (or Android's own record) and hold no message text beyond what the Recent list itself shows.
 */
final class HelpDialogs {
    private HelpDialogs() {}

    static void recent(final Activity a) {
        final Recent.Item[] all = Recent.all();
        if (all.length == 0) { Toast.makeText(a, Str.t(a, "no_banners_yet", "No banners yet"), Toast.LENGTH_SHORT).show(); return; }
        String[] rows = new String[all.length];
        DateFormat tf = android.text.format.DateFormat.getTimeFormat(a);
        for (int i = 0; i < all.length; i++) {
            Recent.Item it = all[i];
            rows[i] = tf.format(new Date(it.at)) + "  " + (it.app.length() > 0 ? it.app + " · " : "") + it.title
                + (it.text.length() > 0 ? "\n" + it.text : "");
        }
        new AlertDialog.Builder(a).setTitle(Str.t(a, "recent_banners", "Recent banners"))
            .setItems(rows, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) { Cards.open(a, all[which].open, all[which].pkg); }
            })
            .setNeutralButton(Str.t(a, "clear", "Clear"), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { Recent.clear(); }
            })
            .setPositiveButton(Str.t(a, "close", "Close"), null).show();
    }

    static void trace(Activity a) {
        Trace.Item[] all = Trace.all();
        if (all.length == 0) { Toast.makeText(a, Str.t(a, "nothing_kept_off", "Nothing has been kept off the island lately"), Toast.LENGTH_SHORT).show(); return; }
        String[] rows = new String[all.length];
        DateFormat tf = android.text.format.DateFormat.getTimeFormat(a);
        for (int i = 0; i < all.length; i++) {
            Trace.Item it = all[i];
            rows[i] = tf.format(new Date(it.at)) + "  " + Img.appLabel(a, it.pkg) + " · " + Trace.text(it.reason)
                + (it.count > 1 ? "  ×" + it.count : "") + "\n" + Trace.hint(it.reason);
        }
        new AlertDialog.Builder(a).setTitle(Str.t(a, "why_not_shown", "Why didn't it show?"))
            .setItems(rows, null)
            .setNeutralButton(Str.t(a, "clear", "Clear"), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { Trace.clear(); }
            })
            .setPositiveButton(Str.t(a, "close", "Close"), null).show();
    }

    @android.annotation.TargetApi(30)
    static void exits(Activity a) {
        List<android.app.ApplicationExitInfo> list;
        try {
            list = ((android.app.ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE)).getHistoricalProcessExitReasons(a.getPackageName(), 0, 10);
        } catch (Throwable t) { list = null; }
        if (list == null || list.isEmpty()) { Toast.makeText(a, Str.t(a, "no_stops", "Android has not recorded any stops yet"), Toast.LENGTH_SHORT).show(); return; }
        String[] rows = new String[list.size()];
        DateFormat df = android.text.format.DateFormat.getMediumDateFormat(a), tf = android.text.format.DateFormat.getTimeFormat(a);
        boolean system = false;
        for (int i = 0; i < rows.length; i++) {
            int r = list.get(i).getReason();
            Date when = new Date(list.get(i).getTimestamp());
            String hint = ExitReason.hint(r);
            rows[i] = df.format(when) + " " + tf.format(when) + "  " + ExitReason.text(r) + (hint.length() > 0 ? "\n" + hint : "");
            system |= ExitReason.systemStopped(r);
        }
        new AlertDialog.Builder(a).setTitle(system ? Str.t(a, "android_stopping", "Android has been stopping the island") : Str.t(a, "recent_stops", "Recent stops"))
            .setItems(rows, null)
            .setPositiveButton(Str.t(a, "close", "Close"), null).show();
    }

    static void log(final Activity a) {
        final String text = Dbg.dump();
        new AlertDialog.Builder(a).setTitle(Str.t(a, "diag_log", "Diagnostic log")).setMessage(text)
            .setPositiveButton(Str.t(a, "copy", "Copy"), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    try {
                        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("VoidIsland log", "VoidIsland " + Cards.appVersion + ", Android " + Build.VERSION.SDK_INT + ", " + Build.MANUFACTURER + " " + Build.MODEL + "\n" + text));
                        Toast.makeText(a, Str.t(a, "copied", "Copied"), Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) { /* no clipboard */ }
                }
            })
            .setNeutralButton(Str.t(a, "clear", "Clear"), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { Dbg.clear(); }
            })
            .setNegativeButton(Str.t(a, "close", "Close"), null).show();
    }
}
