package com.vxyzview.voidisland;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.widget.Toast;

/** Copies a one-time code to the clipboard. Android 13+ is told it is sensitive, so the code stays out of the clipboard preview. */
final class Clip {
    private Clip() {}

    static void copyCode(Context c, String code) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || code == null) return;
            ClipData d = ClipData.newPlainText("code", code);
            if (Build.VERSION.SDK_INT >= 33) {
                PersistableBundle extras = new PersistableBundle();
                extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
                d.getDescription().setExtras(extras);
            }
            cm.setPrimaryClip(d);
            // Android 13+ shows its own "copied" confirmation; older versions say nothing, so say it here
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(c, Str.t(c, "code_copied", "Code copied"), Toast.LENGTH_SHORT).show();
        } catch (Throwable t) { Dbg.e("copy-code", t); }
    }

    /** Copies plain text that is not secret (a payment amount): no sensitive flag, same "copied" hint on older Android. */
    static void copyText(Context c, String label, String text, String saidAs) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || text == null) return;
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(c, saidAs, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) { Dbg.e("copy-text", t); }
    }
}
