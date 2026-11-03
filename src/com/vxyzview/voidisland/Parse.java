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
        "receipt", "qris", "pembayaran", "dibayar", "transaksi", "pembelian", "isi ulang", "uang masuk",
        "dana masuk", "tap to pay", "you sent", "sent you", "you received", "charged",
        "pay ", "withdraw", "tarik tunai"};
    private static final String[] FAIL_WORDS = {
        "failed", "declined", "gagal", "ditolak", "unsuccessful", "error", "cancelled", "canceled", "dibatalkan"};
    private static final String[] IN_WORDS = {
        "received", "menerima", "diterima", "uang masuk", "dana masuk", "credited", "deposit", "refund", "sent you"};
    // reminders, bills and promos mention money and "payment" but nothing was paid: never a "Payment successful" card
    private static final String[] NOT_PAY_WORDS = {
        "reminder", "overdue", "payment due", "bill due", "is due", "are due", "due on", "due by", "due today", "due tomorrow",
        "due soon", "upcoming", "will be charged", "will be debited", "expires", "expiring", "promo", "discount", "voucher",
        "cashback", "% off", " off your", " off on", "pengingat", "jatuh tempo", "diskon", "promosi",
        // nothing has been paid yet ("paid" also sits inside "unpaid"), or the app is still asking for the money
        "unpaid", "not paid", "belum dibayar", "belum lunas", "outstanding", "past due", "pay now", "bayar sekarang",
        "segera bayar", "complete your payment", "selesaikan pembayaran", "awaiting payment", "waiting for payment", "menunggu pembayaran"};
    // prefix match on a word start, so "End call" / "Ending" count but "Send" does not
    private static final Pattern NEG_BTN = Pattern.compile(
        "(?<!\\p{L})(decline|reject|hang|end|tolak|akhiri|tutup|matikan|putus|dismiss|ignore|cancel|stop)");
    private static final Pattern POS_BTN = Pattern.compile("(?<!\\p{L})(answer|accept|jawab|angkat|terima|pick)");

    // --- one-time codes (the "Copy code" button on a banner)
    private static final Pattern OTP_KEYWORD = Pattern.compile("(?<!\\p{L})(otp|one[- ]?time|code|kode|passcode)");
    // a code that is really something else: promos, postal codes
    private static final String[] NOT_OTP_WORDS = {"promo", "voucher", "coupon", "kupon", "discount", "diskon", "referral",
        "zip", "postal", "postcode", "kode pos", "area code"};
    private static final Pattern OTP_NUMBER = Pattern.compile("(?<![\\d.,])(\\d{3}[- ]\\d{3}|\\d{4,8})(?!\\d|[.,]\\d)");
    // dates and clock times (\"2026-11-03\", \"14:30\"): their digits are never a code
    private static final Pattern DATE_OR_TIME = Pattern.compile("\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}|\\d{1,2}:\\d{2}(?::\\d{2})?");
    private static final Pattern UNIT_AFTER = Pattern.compile("^\\s?(%|min|menit|minute|detik|sec|jam|hour|km|kg|mb|gb)");

    /**
     * The one-time code in a notification (\"Your code is 482913\", \"Kode OTP 7391\", \"G-123456\"), digits only, or null. It needs a
     * keyword (otp, code, kode...) next to a 4-8 digit number; amounts, dates, times and numbers followed by a unit never count,
     * and neither do promo or postal codes. The number after the first keyword wins, else the last one before it.
     */
    static String otp(String title, String text) {
        String all = (title == null ? "" : title) + " " + (text == null ? "" : text);
        String lower = all.toLowerCase(Locale.ROOT);
        if (any(lower, NOT_OTP_WORDS)) return null;
        Matcher kw = OTP_KEYWORD.matcher(lower);
        if (!kw.find()) return null;
        int kwAt = kw.start();
        // blank the money / date / time spans (not cut them out) so positions stay comparable with the keyword's
        StringBuilder b = new StringBuilder(lower);
        blank(b, AMOUNT.matcher(lower));
        blank(b, DATE_OR_TIME.matcher(lower));
        String clean = b.toString();
        String before = null, after = null;
        Matcher m = OTP_NUMBER.matcher(clean);
        while (m.find()) {
            if (UNIT_AFTER.matcher(clean.substring(m.end())).find()) continue;
            String digits = m.group().replaceAll("[^0-9]", "");
            if (m.start() >= kwAt) { after = digits; break; }
            before = digits;
        }
        return after != null ? after : before;
    }

    private static void blank(StringBuilder b, Matcher m) {
        while (m.find()) for (int i = m.start(); i < m.end(); i++) b.setCharAt(i, ' ');
    }

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
            || p.equals("com.telkom.mwallet")
            || p.equals("com.bca") || p.equals("id.co.bri.brimo") || p.equals("id.bmri.livin")
            || p.equals("src.com.bni") || p.equals("com.btpn.dc") || p.equals("com.jago.digitalbanking")
            || p.equals("com.squareup.cash") || p.equals("com.venmo") || p.equals("com.revolut.revolut")
            || p.equals("com.transferwise.android") || p.equals("com.phonepe.app") || p.equals("net.one97.paytm")
            || p.equals("com.eg.android.alipaygphone")
            || p.startsWith("com.samsung.android.spay");
    }

    /**
     * Shopping / ride super-apps that have a wallet inside: they count as a payment source only when the text also says
     * something was paid. A price alone ("Pesanan Rp 100.000 dikirim", a ride fare) is an order or ride update, not a payment.
     */
    static boolean superApp(String pkg) {
        return "com.gojek.app".equals(pkg) || "com.shopee.id".equals(pkg) || "com.grab.passenger".equals(pkg);
    }

    static boolean isPayment(String pkg, String title, String text) {
        String all = (title == null ? "" : title) + " " + (text == null ? "" : text);
        String lower = all.toLowerCase(Locale.ROOT);
        if (any(lower, NOT_PAY_WORDS)) return false;
        boolean word = any(lower, PAY_WORDS), pk = payPkg(pkg), amt = amount(all) != null;
        // a payment app showing only a balance ("Saldo DANA Rp 50.000") is information; with a payment word next to it, it is a payment
        if (!word && (lower.contains("saldo") || lower.contains("balance"))) return false;
        return (word && (pk || superApp(pkg) || amt)) || (pk && amt);
    }

    /**
     * Like {@link #isPayment}, but a chat message never counts unless it comes from a payment app: "I paid $20 for the
     * tickets" in a messenger is a message, not a payment.
     */
    static boolean isPaymentFrom(boolean messaging, String pkg, String title, String text) {
        return isPayment(pkg, title, text) && (!messaging || payPkg(pkg) || superApp(pkg));
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

    /** The two halves of {@link #timeOfDay}: {"1", "05"} (12 h) or {"13", "05"} (24 h), for clocks that show hours and minutes apart. */
    static String[] timeParts(int hour24, int minute, boolean use24) {
        String t = timeOfDay(hour24, minute, use24);
        int c = t.indexOf(':');
        return new String[]{t.substring(0, c), t.substring(c + 1)};
    }

    /** Milliseconds until the wall clock's second changes (the expanded clock card shows a seconds hand). */
    static long nextWallSecondMs(long now) {
        return 1000L - (now % 1000L) + 5L;
    }

    /** How much of the day has passed, 0..1 (the expanded clock card's progress bar). */
    static float dayFrac(int hour, int minute, int second) {
        float f = (hour * 3600 + minute * 60 + second) / 86400f;
        return f < 0f ? 0f : (f > 1f ? 1f : f);
    }

    private static final String[] HOUR_WORDS = {"twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven"};
    private static final String[] PART_WORDS = {"", "five past", "ten past", "quarter past", "twenty past", "twenty-five past", "half past",
        "twenty-five to", "twenty to", "quarter to", "ten to", "five to"};

    /**
     * The time the way you would say it ("Quarter to eleven"), to the nearest five minutes. Off-beat minutes are
     * hedged: "Almost" just before, "Just after" just past. 12:00 / 00:00 read "Noon" / "Midnight".
     */
    static String timeWords(int hour24, int minute) {
        int h = ((hour24 % 24) + 24) % 24, m = ((minute % 60) + 60) % 60;
        int r = ((m + 2) / 5) * 5, diff = m - r;
        int hh = (r > 30 ? h + 1 : h) % 24;
        if (r == 60) r = 0;
        String lead = diff < 0 ? "Almost " : (diff > 0 ? "Just after " : "");
        String body;
        if (r == 0) body = hh == 0 ? "midnight" : (hh == 12 ? "noon" : HOUR_WORDS[hh % 12] + " o'clock");
        else body = PART_WORDS[r / 5] + " " + HOUR_WORDS[hh % 12];
        String all = lead + body;
        return Character.toUpperCase(all.charAt(0)) + all.substring(1);
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
