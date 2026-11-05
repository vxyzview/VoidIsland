package com.vxyzview.voidisland;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.widget.Toast;

/** Copies a one-time code (or a payment's amount) to the clipboard. Android 13+ is told it is sensitive, so it stays out of the clipboard preview. */
final class Clip {
    private Clip() {}

    static void copyCode(Context c, String code) { copy(c, "code", code, Str.t(c, "code_copied", "Code copied")); }

    static void copyAmount(Context c, String amount) { copy(c, "amount", amount, Str.t(c, "amount_copied", "Amount copied")); }

    private static void copy(Context c, String label, String text, String toast) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || text == null) return;
            ClipData d = ClipData.newPlainText(label, text);
            if (Build.VERSION.SDK_INT >= 33) {
                PersistableBundle extras = new PersistableBundle();
                extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
                d.getDescription().setExtras(extras);
            }
            cm.setPrimaryClip(d);
            // Android 13+ shows its own "copied" confirmation; older versions say nothing, so say it here
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(c, toast, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) { Dbg.e("copy-" + label, t); }
    }
}
