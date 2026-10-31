package com.vxyzview.voidisland;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java text heuristics (payments, call buttons, clocks). No Android imports so it can be unit-tested on a JVM. */
final class Parse {
    private Parse() {}

    private static final Pattern AMOUNT = Pattern.compile(
        "(?i)(?:\\b(?:rp|idr|usd|rm|myr|sgd|php|thb|vnd|inr|eur|gbp|jpy|aud|cad)\\.?\\s?|[$\u20ac\u00a3\u00a5\u20b9\u20b1\u0e3f\u20ab]\\s?)\\d[\\d.,]*"
        + "|\\b\\d[\\d.,]*\\s?(?:rupiah|idr|usd|eur|gbp|sgd|myr)\\b");

    private static final String[] PAY_WORDS = {
        "payment", "paid", "purchase", "transaction", "transfer", "debit", "top up", "top-up", "topup", "refund",
        "receipt", "qris", "pembayaran", "dibayar", "transaksi", "pembelian", "isi ulang", "saldo", "uang masuk",
        "dana masuk", "tap to pay", "you sent", "sent you", "you received", "charged", "invoice", "tagihan",
        "pay ", "withdraw", "tarik tunai"};
    private static final String[] FAIL_WORDS = {
        "failed", "declined", "gagal", "ditolak", "unsuccessful", "error", "cancelled", "canceled", "dibatalkan"};
    private static final String[] IN_WORDS = {
        "received", "menerima", "diterima", "uang masuk", "dana masuk", "credited", "deposit", "refund", "sent you"};
    // prefix match on a word start, so "End call" / "Ending" count but "Send" does not
    private static final Pattern NEG_BTN = Pattern.compile(
        "(?<!\\p{L})(decline|reject|hang|end|tolak|akhiri|tutup|matikan|putus|dismiss|ignore|cancel|stop)");
    private static final Pattern POS_BTN = Pattern.compile("(?<!\\p{L})(answer|accept|jawab|angkat|terima|pick)");

    /** First money-looking amount in the text ("Rp 25.000", "$12.50"), or null. */
    static String amount(String s) {
        if (s == null) return null;
        Matcher m = AMOUNT.matcher(s);
        if (!m.find()) return null;
        String a = m.group().replaceAll("[.,]+$", "").trim();
        return a.length() < 2 ? null : a;
    }

    private static boolean any(String lower, String[] words) {
        for (String w : words) if (lower.contains(w)) return true;
        return false;
    }

    static boolean payPkg(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.contains("payroll")) return false;
        return p.contains("pay") || p.contains("wallet") || p.equals("id.dana") || p.equals("ovo.id")
            || p.equals("com.gojek.app") || p.equals("com.shopee.id") || p.equals("com.telkom.mwallet")
            || p.equals("com.bca") || p.equals("id.co.bri.brimo") || p.equals("id.bmri.livin")
            || p.equals("src.com.bni") || p.equals("com.btpn.dc") || p.equals("com.jago.digitalbanking")
            || p.equals("com.squareup.cash") || p.equals("com.venmo") || p.equals("com.revolut.revolut")
            || p.equals("com.transferwise.android") || p.equals("com.phonepe.app") || p.equals("net.one97.paytm")
            || p.equals("com.grab.passenger") || p.equals("com.eg.android.alipaygphone")
            || p.startsWith("com.samsung.android.spay");
    }

    static boolean isPayment(String pkg, String title, String text) {
        String all = (title == null ? "" : title) + " " + (text == null ? "" : text);
        String lower = all.toLowerCase(Locale.ROOT);
        boolean word = any(lower, PAY_WORDS), pk = payPkg(pkg), amt = amount(all) != null;
        return (word && (pk || amt)) || (pk && amt);
    }

    static boolean isFailure(String s) { return s != null && any(s.toLowerCase(Locale.ROOT), FAIL_WORDS); }

    static boolean isIncoming(String s) { return s != null && any(s.toLowerCase(Locale.ROOT), IN_WORDS); }

    /** -1 = negative (decline / hang up), 1 = positive (answer), 0 = unknown. */
    static int actionKind(String label) {
        if (label == null) return 0;
        String l = label.toLowerCase(Locale.ROOT);
        if (NEG_BTN.matcher(l).find()) return -1;
        if (POS_BTN.matcher(l).find()) return 1;
        return 0;
    }

    static String clock(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        StringBuilder b = new StringBuilder(8);   // hot path (every second): no Formatter
        if (h > 0) b.append(h).append(':').append(m < 10 ? "0" : "");
        b.append(m).append(':').append(sec < 10 ? "0" : "").append(sec);
        return b.toString();
    }

    /** Milliseconds until a running chronometer's whole-second readout next changes (1..1000). */
    static long nextSecondMs(long when, boolean countDown, long now) {
        long ms = countDown ? when - now : now - when;
        if (ms < 0) return 1000L;                       // already expired / not started: nothing to wake for soon
        long wait = countDown ? (ms % 1000L) + 1L : 1000L - (ms % 1000L);
        return Math.max(1L, Math.min(1000L, wait));
    }

    /** Milliseconds until the wall clock's minute changes (a few ms of margin so the text is never read early). */
    static long nextMinuteMs(long now) {
        return 60000L - (now % 60000L) + 20L;
    }

    /** What a running chronometer reads right now: time left (count-down) or time elapsed, from its wall-clock base. */
    static long chronoMs(long when, boolean countDown, long now) {
        long ms = countDown ? when - now : now - when;
        return ms < 0 ? 0 : ms;
    }

    /** A Chronometer base (SystemClock.elapsedRealtime timeline) re-expressed on the wall clock the island's timers use. */
    static long wallBase(long chronoBase, long elapsedNow, long wallNow) {
        return wallNow - (elapsedNow - chronoBase);
    }

    /** True when two action-label lists read the same, so a pause/resume swap is noticed. */
    static boolean sameLabels(String[] a, String[] b) {
        if (a == b) return true;
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            String x = a[i], y = b[i];
            if (x == null ? y != null : !x.equals(y)) return false;
        }
        return true;
    }

    /** Clock apps (stopwatch / timer) whose notifications carry their own chronometer instead of the standard one. */
    static boolean isClockApp(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("deskclock") || p.contains("clockpackage") || p.contains("alarmclock")
            || p.equals("com.android.bbkclock") || p.equals("com.miui.clock") || p.equals("com.google.android.apps.clock");
    }

    /** Wall-clock text for the island's idle pill: "1:05" (12 h) or "13:05" (24 h). Hours never get a leading zero. */
    static String timeOfDay(int hour24, int minute, boolean use24) {
        int h = hour24 % 24;
        if (!use24) {
            if (h == 0) h = 12;
            else if (h > 12) h -= 12;
        }
        return String.format(Locale.US, "%d:%02d", h, minute);
    }

    static String initial(CharSequence name) {
        if (name == null) return "?";
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isLetterOrDigit(ch)) return String.valueOf(Character.toUpperCase(ch));
        }
        return "?";
    }
}
