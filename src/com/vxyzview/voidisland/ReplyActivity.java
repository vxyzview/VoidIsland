package com.vxyzview.voidisland;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Quick reply for the island's notification card. The island window can't take keyboard focus, so the typing happens in
 * this see-through activity, which hands the text to the app's own reply action.
 *
 * The box is drawn as the card's own lower half: same black, same width, same corner radius, flush against the card's
 * bottom edge (which the island flattens while the box is open), so card and box read as one shape that grew downwards.
 * While the box is open the island holds the banner: it does not time out and nothing replaces it. The banner and the
 * box leave together: when the reply is sent, when it is given up (cancel / back / tap outside / swipe the card away),
 * or when another button on the card is pressed.
 */
public final class ReplyActivity extends Activity {
    // set right before the activity starts (same process), cleared as soon as it has read them
    private static PendingIntent pending;
    private static RemoteInput input;
    private static CharSequence who;
    private static String pendKey;
    /** The box that is open right now, so the island can close it (card opened, swiped away, other button, call...). */
    private static ReplyActivity cur;

    private static int startToken;   // which start() the 2.5 s watchdog belongs to

    static void start(final Context c, final IslandEvent e) {
        if (e.replyIntent == null || e.replyInput == null) return;
        if (Cards.keyguarded(c)) return;   // never offer a reply box over the lock screen
        ReplyActivity open = cur;
        if (open != null && !open.done && e.key.equals(open.key)) { open.focus(); return; }   // already open for this banner
        if (pending != null) return;   // a box is already on its way up: a second tap must not stack another one
        final int token = ++startToken;
        pending = e.replyIntent;
        input = e.replyInput;
        who = e.title;
        pendKey = e.key;
        Bus.replyBegin(e);   // hold the card and flatten its bottom edge (false = nothing to join: the box floats on its own)
        Intent i = new Intent(c, ReplyActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NO_HISTORY);
        try { c.startActivity(i); } catch (Throwable t) { failed(c, e); return; }
        // A background start the system refuses throws nothing: the screen just never appears. If it has not picked its
        // data up by now, say so and open the app instead of leaving the person with a dead button.
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { if (pending != null && token == startToken) failed(c, e); }   // not a later box's data
        }, 2500);
    }

    private static void failed(Context c, IslandEvent e) {
        Dbg.i("reply box could not open (background start refused)");
        pending = null;   // don't hold the app's reply intent until some later start
        input = null;
        who = null;
        pendKey = null;
        Bus.replyEnd(e.key, false);
        try { Toast.makeText(c, Str.t(c, "reply_open_fail", "Couldn't open the reply box, opening the app instead"), Toast.LENGTH_SHORT).show(); } catch (Throwable t) { /* ignore */ }
        Cards.open(c, e);
    }

    /** The island let go of the banner (opened, swiped away, another button pressed, a call came in...): the box goes too. */
    static void closeFromIsland() {
        ReplyActivity a = cur;
        if (a != null) a.retract(false);
    }

    /** The card moved or changed height (a newer message replaced it): the box slides to the card's new bottom edge. */
    static void follow() {
        ReplyActivity a = cur;
        if (a != null) a.reposition();
    }

    /** A newer message arrived in the same chat: replies now go through its (fresher) reply action. */
    static void update(IslandEvent e) {
        ReplyActivity a = cur;
        if (a == null || a.done || !e.key.equals(a.key) || e.replyIntent == null || e.replyInput == null) return;
        a.pi = e.replyIntent;
        a.ri = e.replyInput;
    }

    private PendingIntent pi;
    private RemoteInput ri;
    private String key;
    private EditText et;
    private View sendBtn;
    private IconView sendIcon;
    private LinearLayout dockV;
    private FrameLayout root;
    private boolean done, sending, joined;
    private int sendGlyph;
    private final int[] rect = new int[4];   // card left, card bottom, card width, corner radius (screen px)
    private float restY;                     // where the box rests (translationY), for the slide in / out
    private int overlapPx;                   // how far the box tucks under the card
    private boolean placed;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pi = pending;
        ri = input;
        key = pendKey;
        final CharSequence name = who;
        pending = null;
        input = null;
        who = null;
        pendKey = null;
        if (pi == null || ri == null || key == null) { finish(); return; }
        cur = this;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        final Style s = Cards.S;   // same process as the island: the box is drawn with exactly the island's colours
        joined = Bus.replyDock(rect);
        final int overlap = joined && Color.alpha(s.bg) >= 250 ? 1 : 0;   // tuck 1 px under the card so no hairline can show at the seam

        root = new FrameLayout(this);
        root.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { retract(false); } });

        dockV = new LinearLayout(this);
        dockV.setOrientation(LinearLayout.HORIZONTAL);
        dockV.setGravity(Gravity.BOTTOM);
        dockV.setClickable(true);   // taps inside the box must not fall through to the dismiss area
        float radius = joined ? rect[3] : Cards.dp(this, 28);
        dockV.setBackground(new DockBg(s.bg, radius, Cards.dp(this, s.borderW), s.borderCol, joined));
        int side = Cards.px(this);
        dockV.setPadding(side, Cards.dp(this, 2) + overlap, side, Cards.py(this));

        // cancel: a small tonal circle
        FrameLayout close = circle(s.btn, IconView.CLOSE, s.btnOn, 40, 18);
        close.setContentDescription(Str.t(this, "cancel", "Cancel"));
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { retract(false); } });
        Cards.press(close);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Cards.dp(this, 40), Cards.dp(this, 40));
        cp.setMarginEnd(Cards.dp(this, 8));
        cp.bottomMargin = Cards.dp(this, 2);
        dockV.addView(close, cp);

        // the text field: the island's own tile tone, fully rounded
        et = new EditText(this);
        CharSequence label = ri.getLabel();
        String nm = name == null ? "" : name.toString().trim();
        if (nm.length() > 22) nm = nm.substring(0, 21) + "\u2026";
        et.setHint(nm.length() > 0 ? Str.t(this, "reply_to", "Reply to %s", nm) : (label != null && label.length() > 0 ? label : Str.t(this, "message", "Message")));
        et.setHintTextColor(s.sub);
        et.setTextColor(s.text);
        et.setTextSize(15.5f);
        et.setMaxLines(4);
        et.setMinHeight(Cards.dp(this, 44));
        et.setGravity(Gravity.CENTER_VERTICAL);
        et.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        GradientDrawable fb = new GradientDrawable();
        fb.setColor(s.tile);
        fb.setCornerRadius(Cards.dp(this, 22));
        et.setBackground(fb);
        et.setPadding(Cards.dp(this, 16), Cards.dp(this, 10), Cards.dp(this, 16), Cards.dp(this, 10));
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent ev) {
                if (actionId == EditorInfo.IME_ACTION_SEND) { send(); return true; }
                return false;
            }
        });
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b2, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b2, int d) { }
            @Override public void afterTextChanged(Editable e) {
                ready(e.toString().trim().length() > 0);
                Bus.replyTouch();   // still typing: the island's safety timeout starts over
            }
        });
        dockV.addView(et, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // send: the one filled button, the island's accent (or its text colour on a plain island)
        int fill = s.accentOverride ? s.accent : s.text;
        sendGlyph = Col.onColor(fill);
        sendBtn = circle(fill, IconView.SEND, sendGlyph, 44, 20);
        sendIcon = (IconView) ((FrameLayout) sendBtn).getChildAt(0);
        sendBtn.setContentDescription(Str.t(this, "send", "Send"));
        sendBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { send(); } });
        Cards.press(sendBtn);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Cards.dp(this, 44), Cards.dp(this, 44));
        sp.setMarginStart(Cards.dp(this, 8));
        dockV.addView(sendBtn, sp);
        ready(false);

        FrameLayout.LayoutParams fp;
        if (joined) {
            fp = new FrameLayout.LayoutParams(rect[2], FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        } else {   // no card to join (should not happen): a plain floating box, as before
            fp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP);
            fp.setMargins(Cards.dp(this, 16), Cards.dp(this, 72), Cards.dp(this, 16), 0);
        }
        dockV.setVisibility(View.INVISIBLE);   // shown once it knows where the card's edge is
        root.addView(dockV, fp);
        setContentView(root);

        final int ov = overlap;
        overlapPx = overlap;
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (root == null || dockV.getHeight() == 0) return;
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                place(ov);
            }
        });
        et.requestFocus();
    }

    /** Puts the box exactly under the card (the window may start below the status bar) and slides it out from under the card. */
    private void place(int overlap) {
        int h = dockV.getHeight();
        placed = true;
        if (joined) {
            Bus.replyDock(rect);   // the card may have changed since this screen was started
            int[] o = new int[2];
            root.getLocationOnScreen(o);
            dockV.setTranslationX(rect[0] - o[0]);
            restY = rect[1] - overlap - o[1];
        } else {
            restY = 0f;
        }
        dockV.setVisibility(View.VISIBLE);
        if (!animating()) { dockV.setTranslationY(restY); return; }
        if (joined) {
            // the island is drawn above this window: starting hidden behind the card, the box comes out of it
            dockV.setTranslationY(restY - h);
            dockV.animate().translationY(restY).setDuration(300).setInterpolator(M3.STANDARD).start();
        } else {
            dockV.setAlpha(0f);
            dockV.setTranslationY(-Cards.dp(this, 10));
            dockV.animate().alpha(1f).translationY(0f).setDuration(200).setInterpolator(M3.STANDARD).start();
        }
    }

    private void reposition() {
        if (done || !joined || !placed || dockV == null) return;
        int[] r = new int[4];
        if (!Bus.replyDock(r)) return;
        int[] o = new int[2];
        root.getLocationOnScreen(o);
        float ny = r[1] - overlapPx - o[1];
        float nx = r[0] - o[0];
        rect[0] = r[0]; rect[1] = r[1];
        if (ny == restY && nx == dockV.getTranslationX()) return;
        restY = ny;
        dockV.setTranslationX(nx);
        if (animating()) dockV.animate().translationY(ny).setDuration(250).setInterpolator(M3.STANDARD).start();
        else dockV.setTranslationY(ny);
    }

    /** Follows Android's "Remove animations" / animator scale 0. */
    private boolean animating() {
        try { return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f; }
        catch (Throwable t) { return true; }
    }

    private FrameLayout circle(int fill, int glyph, int glyphColor, int sizeDp, int glyphDp) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        f.setBackground(g);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Cards.dp(this, glyphDp), Cards.dp(this, glyphDp), Gravity.CENTER);
        f.addView(new IconView(this, glyph, glyphColor), lp);
        return f;
    }

    /** The send button is only live with something to send. */
    private void ready(boolean on) {
        if (sendBtn == null) return;
        sendBtn.setEnabled(on);
        sendBtn.setAlpha(on ? 1f : 0.38f);
    }

    private void focus() {
        if (et == null) return;
        et.requestFocus();
        try { ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(et, 0); } catch (Throwable t) { /* ignore */ }
    }

    private void send() {
        if (done || sending) return;
        CharSequence t = et.getText();
        if (t == null || t.toString().trim().length() == 0) return;
        try {
            Intent fill = new Intent();
            Bundle res = new Bundle();
            res.putCharSequence(ri.getResultKey(), t.toString().trim());
            RemoteInput.addResultsToIntent(new RemoteInput[]{ri}, fill, res);
            pi.send(this, 0, fill);
        } catch (Throwable x) {
            Dbg.e("reply-send", x);
            // stay open: what was typed is not lost, and the person can try again or cancel
            Toast.makeText(this, Str.t(this, "reply_send_fail", "Couldn't send the reply"), Toast.LENGTH_SHORT).show();
            return;
        }
        sending = true;
        et.setEnabled(false);
        sendIcon.set(IconView.CHECK, sendGlyph);   // the arrow turns into a tick
        sendBtn.setEnabled(false);
        sendBtn.setAlpha(1f);
        // a beat to see it went, then banner and box leave together
        root.postDelayed(new Runnable() { @Override public void run() { retract(true); } }, animating() ? 380 : 0);
    }

    /** Ends the reply: the island lets the banner go (with the box when sent) while the box slides back under the card. */
    private void retract(final boolean sent) {
        if (done) return;
        done = true;
        Bus.replyEnd(key, sent);
        try { ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(et.getWindowToken(), 0); } catch (Throwable t) { /* ignore */ }
        final Runnable fin = new Runnable() { @Override public void run() { finish(); } };
        if (dockV == null || dockV.getHeight() == 0 || dockV.getVisibility() != View.VISIBLE || !animating()) { fin.run(); return; }
        if (joined) dockV.animate().translationY(restY - dockV.getHeight()).setDuration(220).setInterpolator(M3.STANDARD).withEndAction(fin).start();
        else dockV.animate().alpha(0f).setDuration(140).withEndAction(fin).start();
    }

    @Override public void onBackPressed() { retract(false); }

    @Override protected void onStop() {
        super.onStop();
        if (!done && key != null) {   // sent to the background (home, app switch, screen off): nothing keeps the banner waiting
            done = true;
            Bus.replyEnd(key, false);
            finish();
        }
    }

    @Override protected void onDestroy() {
        if (cur == this) cur = null;
        if (!done && key != null) { done = true; Bus.replyEnd(key, false); }
        super.onDestroy();
    }

    /**
     * The box's background: the card's own colour, square on top (where it meets the card) and rounded at the bottom
     * with the card's radius. When the island has a border, the two sides and the bottom continue it without a line at the seam.
     */
    private static final class DockBg extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path pf = new Path(), pl = new Path();
        private final RectF rf = new RectF();
        private final float[] rad = new float[8];
        private final float r, bw;
        private final boolean flatTop;

        DockBg(int color, float r, float bw, int borderColor, boolean flatTop) {
            this.r = r;
            this.bw = (borderColor >>> 24) == 0 ? 0f : bw;
            this.flatTop = flatTop;
            fill.setColor(color);
            line.setColor(borderColor);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(this.bw);
        }

        @Override protected void onBoundsChange(Rect b) {
            float w = b.width(), h = b.height();
            float rb = Math.max(0f, Math.min(r, Math.min(h, w / 2f)));
            float rt = flatTop ? 0f : rb;
            rad[0] = rt; rad[1] = rt; rad[2] = rt; rad[3] = rt;
            rad[4] = rb; rad[5] = rb; rad[6] = rb; rad[7] = rb;
            rf.set(b);
            pf.rewind();
            pf.addRoundRect(rf, rad, Path.Direction.CW);
            pl.rewind();
            if (bw <= 0f) return;
            float i = bw / 2f, l = b.left + i, rr = b.right - i, bt = b.bottom - i, t = b.top;
            if (flatTop) {
                pl.moveTo(l, t);
                pl.lineTo(l, bt - rb);
                pl.arcTo(l, bt - 2f * rb, l + 2f * rb, bt, 180f, -90f, false);
                pl.lineTo(rr - rb, bt);
                pl.arcTo(rr - 2f * rb, bt - 2f * rb, rr, bt, 90f, -90f, false);
                pl.lineTo(rr, t);
            } else {
                pl.addRoundRect(new RectF(l, t + i, rr, bt), rb, rb, Path.Direction.CW);
            }
        }

        @Override public void draw(Canvas c) {
            c.drawPath(pf, fill);
            if (bw > 0f) c.drawPath(pl, line);
        }

        @Override public void setAlpha(int a) { fill.setAlpha(a); }

        @Override public void setColorFilter(android.graphics.ColorFilter f) { fill.setColorFilter(f); }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
