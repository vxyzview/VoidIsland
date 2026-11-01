package com.vxyzview.voidisland;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * A small in-memory log for bug reports (Behavior > Help > Diagnostic log). It keeps the last lines only, never touches
 * storage or the network, and records WHERE something failed and what kind of error it was, never message text.
 * Pure Java so the unit checks cover it.
 */
final class Dbg {
    private Dbg() {}

    static final int MAX = 120;
    private static final ArrayDeque<String> LOG = new ArrayDeque<String>();
    private static String last = "";
    private static int repeat;

    /** An error caught at {@code where}: the exception's class and the first frame of this app's code, nothing else. */
    static synchronized void e(String where, Throwable t) {
        String what = t == null ? "error" : t.getClass().getSimpleName();
        String at = "";
        if (t != null) {
            StackTraceElement[] st = t.getStackTrace();
            StackTraceElement pick = st.length > 0 ? st[0] : null;
            for (StackTraceElement x : st) if (x.getClassName().startsWith("com.vxyzview.")) { pick = x; break; }
            if (pick != null) at = " @ " + pick.getFileName() + ":" + pick.getLineNumber();
        }
        add(where + ": " + what + at);
    }

    /** A plain note (a rebuild, a setting restored...). */
    static synchronized void i(String msg) { add(msg); }

    private static void add(String line) {
        if (line.equals(last)) {   // the same failure over and over: one line with a counter
            repeat++;
            LOG.pollLast();
            LOG.addLast(stamp() + line + " (x" + (repeat + 1) + ")");
            return;
        }
        last = line;
        repeat = 0;
        if (LOG.size() >= MAX) LOG.pollFirst();
        LOG.addLast(stamp() + line);
    }

    private static String stamp() { return new SimpleDateFormat("MM-dd HH:mm:ss ", Locale.US).format(new Date()); }

    static synchronized String dump() {
        if (LOG.isEmpty()) return "Nothing logged since the island started.";
        StringBuilder b = new StringBuilder();
        for (String s : LOG) b.append(s).append('\n');
        return b.toString();
    }

    static synchronized int size() { return LOG.size(); }

    static synchronized void clear() { LOG.clear(); last = ""; repeat = 0; }
}
