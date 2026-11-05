package com.vxyzview.voidisland;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Apps > Backup: saving every setting to a file the person picks, and restoring from one. The file format and the rules for what a
 * restore may set live in {@link Backup} (pure Java, covered by the unit checks); this class is the Android side: the file picker
 * result, one background thread for the file work, the confirmation, and the toasts.
 */
final class BackupFlow {
    private BackupFlow() {}

    static final int REQ_BACKUP = 41, REQ_RESTORE = 42;

    /** Keys a file may never decide: each has its own confirmation in the app. */
    private static final Set<String> NEVER_FROM_FILE = new HashSet<String>(Arrays.asList(
        Prefs.K_ABSORB,           // removing banners from the shade is only ever switched on through its own confirmation
        Prefs.K_LYRICS_NOTICE));  // a file is not the person's answer to the lyrics notice

    /** One worker for all the file work, gone again 20 s after the last job (no idle thread, same as the lyric workers). */
    private static final ThreadPoolExecutor IO = newIo();

    private static ThreadPoolExecutor newIo() {
        ThreadPoolExecutor ex = new ThreadPoolExecutor(1, 1, 20L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
            @Override public Thread newThread(Runnable r) { Thread t = new Thread(r, "vi-backup"); t.setDaemon(true); return t; }
        });
        ex.allowCoreThreadTimeOut(true);
        return ex;
    }

    /** The picker's answer, from {@code onActivityResult}. Returns false when {@code req} is not one of ours. */
    static boolean onResult(Activity a, M3 m, int req, int res, Intent data) {
        if (req != REQ_BACKUP && req != REQ_RESTORE) return false;
        if (res != Activity.RESULT_OK || data == null || data.getData() == null) return true;   // cancelled: nothing to do
        if (req == REQ_BACKUP) save(a, data.getData()); else restore(a, m, data.getData());
        return true;
    }

    private static void save(final Activity a, final Uri uri) {
        IO.execute(new Runnable() {
            @Override public void run() {
                String msg;
                try {
                    OutputStream os = a.getContentResolver().openOutputStream(uri, "wt");
                    if (os == null) throw new IOException("no stream");
                    try { os.write(Backup.write(Prefs.sp(a).getAll()).getBytes("UTF-8")); } finally { os.close(); }
                    msg = Str.t(a, "backup_saved", "Settings saved");
                } catch (Throwable t) {
                    Dbg.e("backup.save", t);
                    msg = Str.t(a, "backup_save_fail", "Could not save the file");
                }
                toast(a, msg);
            }
        });
    }

    private static void restore(final Activity a, final M3 m, final Uri uri) {
        IO.execute(new Runnable() {
            @Override public void run() {
                final Backup.Plan plan;
                try {
                    InputStream is = a.getContentResolver().openInputStream(uri);
                    byte[] raw;
                    try { raw = Backup.readCapped(is, Backup.MAX_BYTES); } finally { if (is != null) is.close(); }
                    plan = raw == null ? null : Backup.plan(new String(raw, "UTF-8"), Prefs.knownKeys(), NEVER_FROM_FILE);
                } catch (Throwable t) {
                    Dbg.e("backup.read", t);
                    toast(a, Str.t(a, "backup_read_fail", "Could not read the file"));
                    return;
                }
                if (plan == null) { toast(a, Str.t(a, "backup_not_valid", "That file is not a VoidIsland backup")); return; }
                if (plan.values.isEmpty()) { toast(a, Str.t(a, "backup_nothing_known", "That file has no settings this version knows, so nothing was changed")); return; }
                confirm(a, m, plan);
            }
        });
    }

    /** The person confirms first: a restore replaces everything. */
    private static void confirm(final Activity a, final M3 m, final Backup.Plan plan) {
        final boolean noticeSeen = Prefs.b(a, Prefs.K_LYRICS_NOTICE, false);
        final String body = Str.t(a, "restore_body", "This replaces all your current settings with the %d in the file.", plan.values.size())
            + (plan.ignored > 0 ? " " + Str.t(a, "restore_ignored", "%d entries this version does not know are ignored.", plan.ignored) : "")
            + "\n\n" + Str.t(a, "restore_absorb_note", "\"Only on the island\" stays off until you turn it on again.");
        a.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (a.isFinishing()) return;
                m.confirmDialog(a, Str.t(a, "restore_title", "Restore settings?"), body, Str.t(a, "restore", "Restore"), new Runnable() {
                    @Override public void run() { apply(a, plan, noticeSeen); }
                }, Str.t(a, "cancel", "Cancel"), null);
            }
        });
    }

    private static void apply(final Activity a, final Backup.Plan plan, final boolean noticeSeen) {
        IO.execute(new Runnable() {
            @Override public void run() {
                Map<String, Object> vals = new TreeMap<String, Object>(plan.values);
                if (noticeSeen) vals.put(Prefs.K_LYRICS_NOTICE, Boolean.TRUE);   // this phone already saw the notice
                try {
                    Backup.apply(Prefs.sp(a), vals);
                } catch (Throwable t) {
                    Dbg.e("backup.apply", t);
                    toast(a, Str.t(a, "backup_apply_fail", "Could not restore the settings"));
                    return;
                }
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (a.isFinishing()) return;
                        Toast.makeText(a, Str.t(a, "backup_restored", "Settings restored"), Toast.LENGTH_LONG).show();
                        a.recreate();
                    }
                });
            }
        });
    }

    private static void toast(final Activity a, final String msg) {
        a.runOnUiThread(new Runnable() { @Override public void run() { Toast.makeText(a, msg, Toast.LENGTH_LONG).show(); } });
    }
}
