package com.vxyzview.voidisland;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Quick reply for the island's notification card. The island window can't take keyboard focus,
 * so the typing happens in this small see-through activity; it hands the text to the app's own reply action.
 */
public final class ReplyActivity extends Activity {
    // set right before the activity starts (same process), cleared as soon as it has read them
    private static PendingIntent pending;
    private static RemoteInput input;
    private static CharSequence who, app;

    static void start(Context c, IslandEvent e) {
        if (e.replyIntent == null || e.replyInput == null) return;
        pending = e.replyIntent;
        input = e.replyInput;
        who = e.title;
        app = e.appName;
        Intent i = new Intent(c, ReplyActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NO_HISTORY);
        try { c.startActivity(i); } catch (Throwable t) { /* the system refused a background start: nothing to show */ }
    }

    private PendingIntent pi;
    private RemoteInput ri;
    private EditText et;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pi = pending;
        ri = input;
        final CharSequence name = who, from = app;
        pending = null;
        input = null;
        if (pi == null || ri == null) { finish(); return; }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        final M3 m = new M3(this);
        FrameLayout frame = new FrameLayout(this);
        frame.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(m.dp(20), m.dp(18), m.dp(20), m.dp(14));
        box.setBackground(m.shape(m.surfaceLow, 28));
        box.setClickable(true);   // taps inside the card must not fall through to the dismiss area
        box.addView(m.text("Reply to " + (name == null ? "" : name), 18f, true, m.onSurface));
        if (from != null && from.length() > 0) {
            TextView s = m.text(from, 13f, false, m.onSurfaceVariant);
            s.setPadding(0, m.dp(2), 0, 0);
            box.addView(s);
        }

        et = new EditText(this);
        CharSequence hint = ri.getLabel();
        et.setHint(hint != null && hint.length() > 0 ? hint : "Message");
        et.setHintTextColor(m.onSurfaceVariant);
        et.setTextColor(m.onSurface);
        et.setTextSize(16f);
        et.setMaxLines(4);
        et.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setImeOptions(EditorInfo.IME_ACTION_SEND);
        et.setBackground(m.shape(m.containerHigh, 16));
        et.setPadding(m.dp(14), m.dp(12), m.dp(14), m.dp(12));
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent ev) {
                if (actionId == EditorInfo.IME_ACTION_SEND) { send(); return true; }
                return false;
            }
        });
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ep.topMargin = m.dp(14);
        box.addView(et, ep);

        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        btns.addView(m.button("Cancel", 3, new Runnable() { @Override public void run() { finish(); } }));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMarginStart(m.dp(8));
        btns.addView(m.button("Send", 0, new Runnable() { @Override public void run() { send(); } }), sp);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = m.dp(12);
        box.addView(btns, bp);

        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        fp.setMargins(m.dp(16), m.dp(72), m.dp(16), 0);
        frame.addView(box, fp);
        setContentView(frame);
        et.requestFocus();
    }

    private void send() {
        CharSequence t = et.getText();
        if (t == null || t.toString().trim().length() == 0) return;
        try {
            Intent fill = new Intent();
            Bundle res = new Bundle();
            res.putCharSequence(ri.getResultKey(), t.toString().trim());
            RemoteInput.addResultsToIntent(new RemoteInput[]{ri}, fill, res);
            pi.send(this, 0, fill);
            Toast.makeText(this, "Sent", Toast.LENGTH_SHORT).show();
        } catch (Throwable x) {
            Toast.makeText(this, "Couldn't send the reply", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
