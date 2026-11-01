package com.vxyzview.voidisland;

/**
 * The bookkeeping of one quick-reply session: which banner the box is open for, and whether that banner's original
 * notification was taken away while the box was open. Pure Java (no Android types) so the unit checks cover it.
 * The controller owns the timers and the views; this only answers "is a reply open, for which banner, and is it gone".
 */
final class ReplyFlow<T> {
    private T ev;
    private String key;
    private boolean gone;

    boolean active() { return ev != null; }

    T event() { return ev; }

    /** True when {@code e} is the very banner the box is open for. */
    boolean is(Object e) { return ev != null && ev == e; }

    /** True when a box is open for the chat/banner with this key. */
    boolean isKey(String k) { return ev != null && key != null && key.equals(k); }

    /** The box opened for {@code e}. */
    void begin(T e, String k) { ev = e; key = k; gone = false; }

    /** A newer message of the same chat replaced the banner while the box stays open. */
    void replace(T e) { if (ev != null) ev = e; }

    /** The banner's original notification was removed while the box was open: take the banner away afterwards. */
    void markGone() { if (ev != null) gone = true; }

    boolean gone() { return gone; }

    /** The session is over; returns the banner it was for (null if none) and forgets everything. */
    T clear() {
        T old = ev;
        ev = null;
        key = null;
        gone = false;
        return old;
    }
}
