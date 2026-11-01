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
    boolean dirty;

    IslandEvent(String key, int type) {
        this.key = key;
        this.type = type;
    }

    Bitmap appIcon() { return icon != null ? icon : art; }

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
        dirty = true;
    }
}
