package com.vxyzview.voidisland;

import android.app.PendingIntent;
import android.app.RemoteInput;
import android.graphics.Bitmap;
import android.media.session.MediaController;

/** One thing the island can show (a call, a song, a payment, a toast...). Plain data + the hooks its views register. */
final class IslandEvent {
    static final int MEDIA = 0, CALL = 1, NAV = 2, TIMER = 3, PROGRESS = 4, PAYMENT = 5, CHARGE = 6, NOTIF = 7, SYSTEM = 8, PREVIEW = 9, CLOCK = 10, LIVE = 11;

    interface Updater { /** @return false if the views must be rebuilt instead */ boolean update(); }

    final String key;
    final int type;

    int priority;
    boolean live;          // stays until removed (call, music, download...)
    boolean popup;         // live AND pinned open (ringing call)
    long durationMs = 4500; // transient events auto-dismiss after this

    String pkg, nkey, appName;
    String chat;           // "one banner per chat" key, recorded once the banner has really been shown
    CharSequence title, text, sub;
    Bitmap icon, art;
    int accent = 0xFFFFFFFF;
    int iconType = -1;

    long when;             // chronometer base (wall clock)
    boolean countDown, chrono;
    int progress = -1;     // 0..100, -1 = none

    PendingIntent contentIntent;
    String[] actLabels = new String[0];
    PendingIntent[] actIntents = new PendingIntent[0];
    Runnable[] actRuns = new Runnable[0];

    // quick reply (messaging notifications that carry an inline-reply action)
    PendingIntent replyIntent;
    RemoteInput replyInput;

    // live activity (ride / food / score)
    int liveKind;

    // media
    MediaController mc;
    boolean playing;
    long posMs, durMs, posAt, actions;
    float speed = 1f;

    // battery
    int level;
    boolean charging;

    // payment / call
    boolean success = true, incoming;

    // runtime hooks, filled in by Cards when views are built
    Updater uC, uE;
    Runnable tC, tE;
    /** How soon the compact pill next needs a refresh, in ms (0 = the default); set by the card itself, not copied between events. */
    int tickHint;
    boolean dirty;
    /** Set by the listener: the original notification may be removed once this banner is really on screen. Not copied. */
    boolean absorbOk;
    /** The notification's effective lock-screen visibility (Notification.VISIBILITY_*; the platform default is PRIVATE). */
    int vis = Privacy.VIS_PRIVATE;
    /** True once the lock-screen privacy rules replaced this banner's content with a generic line. */
    boolean redacted;
    /** True once the island has put this banner on screen (so the hooks that run on first display run once). */
    boolean shownOnce;

    // Live pills (navigation, timers, rides...) outlive a lock: the privacy rules hide their text only while the phone is locked,
    // so what was replaced is kept here and put back after unlocking. (A banner is redacted for good: see IslandController.redact.)
    private boolean hidden;
    private CharSequence hTitle, hText, hSub;
    private Bitmap hIcon;
    private String[] hLabels;
    private PendingIntent[] hIntents;
    private Runnable[] hRuns;

    IslandEvent(String key, int type) {
        this.key = key;
        this.type = type;
    }

    Bitmap appIcon() { return icon != null ? icon : art; }

    boolean contentHidden() { return hidden; }

    /** Swaps this pill's personal content for a generic line (no buttons) until {@link #restoreContent()}. */
    void hideContent(CharSequence newTitle, CharSequence newText, Bitmap newIcon) {
        if (hidden) return;
        hTitle = title; hText = text; hSub = sub; hIcon = icon;
        hLabels = actLabels; hIntents = actIntents; hRuns = actRuns;
        title = newTitle; text = newText; sub = null; icon = newIcon;
        actLabels = new String[0]; actIntents = new PendingIntent[0]; actRuns = new Runnable[0];
        hidden = true;
        dirty = true;
    }

    /** Puts back what {@link #hideContent} replaced (the phone was unlocked). */
    void restoreContent() {
        if (!hidden) return;
        title = hTitle; text = hText; sub = hSub; icon = hIcon;
        actLabels = hLabels; actIntents = hIntents; actRuns = hRuns;
        forgetHidden();
        dirty = true;
    }

    private void forgetHidden() {
        hidden = false;
        hTitle = hText = hSub = null;
        hIcon = null;
        hLabels = null; hIntents = null; hRuns = null;
    }

    void copyFrom(IslandEvent o) {
        priority = o.priority; live = o.live; popup = o.popup; durationMs = o.durationMs;
        pkg = o.pkg; nkey = o.nkey; appName = o.appName; chat = o.chat;
        title = o.title; text = o.text; sub = o.sub;
        icon = o.icon; art = o.art; accent = o.accent; iconType = o.iconType;
        when = o.when; countDown = o.countDown; chrono = o.chrono; progress = o.progress;
        contentIntent = o.contentIntent; actLabels = o.actLabels; actIntents = o.actIntents; actRuns = o.actRuns;
        replyIntent = o.replyIntent; replyInput = o.replyInput; liveKind = o.liveKind;
        mc = o.mc; playing = o.playing; posMs = o.posMs; durMs = o.durMs; posAt = o.posAt; actions = o.actions; speed = o.speed;
        level = o.level; charging = o.charging; success = o.success; incoming = o.incoming;
        vis = o.vis;
        forgetHidden();   // fresh content from the app replaces whatever was hidden: the lock rules look at it again
        dirty = true;
    }
}
