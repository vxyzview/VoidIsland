package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

/** Unit checks for the Parse logic (pure Java: runs on a plain JVM, no Android). */
final class ParseCheck {
    private ParseCheck() {}

    static void run() {
        // --- Parse
        ok("Rp 25.000".equals(Parse.amount("Pembayaran Rp 25.000 berhasil.")), "amount: Rp 25.000");
        ok("$12.50".equals(Parse.amount("You paid $12.50 at Starbucks")), "amount: $12.50");
        ok(Parse.amount("form 12 submitted") == null, "amount: 'form 12' is not money");
        ok("Rp125.000,00".equals(Parse.amount("Transfer Rp125.000,00 ke Budi.")), "amount: Rp125.000,00");
        ok(Parse.isPayment("id.dana", "DANA", "Pembayaran berhasil Rp 18.500"), "payment: DANA");
        ok(Parse.isPayment("com.google.android.apps.walletnfcrel", "Google Wallet", "Paid $4.20 at Blue Bottle"), "payment: Google Wallet");
        ok(!Parse.isPayment("com.whatsapp", "Budi", "see you at 5, bring 20 bucks"), "payment: chat is not payment");
        ok(!Parse.isPaymentFrom(true, "com.whatsapp", "Alex", "I paid $20 for the tickets"), "payment: a chat message that mentions paying is still a message");
        ok(!Parse.isPaymentFrom(true, "org.telegram.messenger", "Sam", "I'll pay you back $15 tomorrow"), "payment: telegram chat is not a payment");
        ok(Parse.isPaymentFrom(false, "com.whatsapp", "Store", "Receipt for $9.99"), "payment: non-chat notification still classified as before");
        ok(Parse.isPaymentFrom(true, "id.dana", "DANA", "Pembayaran berhasil Rp 18.500"), "payment: a payment app is trusted even with messaging-style");
        ok(!Parse.isPayment("com.news.app", "Markets", "Dollar steady at $1.08 in late trade"), "payment: news with $ is not payment");
        ok(!Parse.isPayment("com.bank.app", "Payment due", "Your payment of $50 is due tomorrow"), "payment: a bill reminder is not a payment");
        ok(!Parse.isPayment("com.bank.app", "Payment reminder", "Invoice of Rp 150.000 is due"), "payment: a payment reminder is not a payment");
        ok(!Parse.isPayment("com.shop.pay", "Pay later", "Get $5 off your next order"), "payment: a promo is not a payment");
        ok(Live.kind("com.shopee.sg") == Live.NONE, "live: the Shopee marketplace is not a food app");
        ok(!Parse.isPayment("id.dana", "Saldo Anda", "Saldo DANA Rp 50.000"), "payment: a balance notice is not a payment");
        ok(!Parse.isPayment("com.bank.app", "Tagihan", "Tagihan Rp 1.200.000 belum dibayar"), "payment: an unpaid bill is not a payment");
        ok(!Parse.isPayment("com.shop.app", "Invoice", "Your invoice of $40 is unpaid"), "payment: 'unpaid' is not 'paid'");
        ok(!Parse.isPayment("com.shopee.id", "Pesanan", "Pesanan Rp 100.000 dikirim"), "payment: a shipping update with a price is not a payment");
        ok(!Parse.isPayment("com.gojek.app", "Driver", "Tarif Rp 25.000, driver menuju lokasi"), "payment: a ride fare update is not a payment");
        ok(Parse.isPayment("com.shopee.id", "ShopeePay", "Pembayaran berhasil Rp 100.000"), "payment: a paid Shopee order still is");
        ok(Parse.isPayment("com.bank.app", "Tagihan", "Tagihan Rp 200.000 sudah dibayar"), "payment: a bill that was paid still is");
        ok(!Parse.isPayment("com.bank.app", "Menunggu pembayaran", "Selesaikan pembayaran Rp 90.000 sebelum besok"), "payment: pending payment prompt is not a payment");
        {
            ReplyFlow<String> f = new ReplyFlow<String>();
            ok(!f.active() && f.event() == null && !f.isKey("a"), "reply flow: starts idle");
            String card = "card-1";
            f.begin(card, "n:chat1");
            ok(f.active() && f.is(card) && f.isKey("n:chat1") && !f.isKey("n:chat2") && !f.gone(), "reply flow: begin holds that banner and key");
            String newer = "card-2";
            f.replace(newer);
            ok(f.is(newer) && !f.is(card) && f.isKey("n:chat1"), "reply flow: a newer message of the same chat takes the card over");
            f.markGone();
            ok(f.gone() && f.active(), "reply flow: original removed while open is remembered, box stays");
            String old = f.clear();
            ok("card-2".equals(old) && !f.active() && !f.gone() && !f.isKey("n:chat1"), "reply flow: clear hands back the banner and forgets everything");
            f.markGone();
            f.replace("x");
            ok(!f.active() && !f.gone() && f.clear() == null, "reply flow: nothing happens while idle");
            f.begin("a", "k1");
            f.markGone();
            f.begin("b", "k2");
            ok(f.is("b") && !f.gone(), "reply flow: a new session starts clean");
        }
        {
            Dbg.clear();
            ok(Dbg.size() == 0 && Dbg.dump().startsWith("Nothing logged"), "log: starts empty");
            String firstDump = "";
            for (int rep = 0; rep < 2; rep++) {   // one source line, so both failures look the same
                Dbg.e("probe", new IllegalStateException("secret message text"));
                if (rep == 0) firstDump = Dbg.dump();
            }
            ok(firstDump.contains("probe: IllegalStateException") && !firstDump.contains("secret"), "log: records where and what, never the message");
            ok(Dbg.size() == 1 && Dbg.dump().contains("(x2)"), "log: the same failure repeating is one counted line");
            for (int i = 0; i < Dbg.MAX + 30; i++) Dbg.i("line " + i);
            ok(Dbg.size() == Dbg.MAX && Dbg.dump().contains("line " + (Dbg.MAX + 29)) && !Dbg.dump().contains("line 0\n"), "log: keeps only the newest lines");
            Dbg.clear();
            ok(Dbg.size() == 0, "log: clear empties it");
        }
        {
            StringBuilder big = new StringBuilder();
            for (int i = 0; i < 900; i++) big.append(String.format("[%02d:%02d.00] line %d\n", i / 60, i % 60, i));
            Lyrics.Loaded lb = Lyrics.parse(big.toString(), null);
            ok(lb != null && lb.line.length == 900 && lb.at[899] == 899000L, "lyrics: a long sheet is kept whole, not cut at 400 lines");
            StringBuilder two = new StringBuilder();
            for (int i = 0; i < 300; i++) two.append(String.format("[%02d:%02d.00][%02d:%02d.00] chorus %d\n", i / 60, i % 60, 5 + i / 60, i % 60, i));
            ok(Lyrics.parse(two.toString(), null).line.length == 600, "lyrics: several stamps per line all count");
        }
        ok(Parse.isFailure("Payment failed"), "failure word");
        ok(Parse.isIncoming("Anda menerima Rp 50.000 dari Sari"), "incoming word");
        ok(Parse.actionKind("Decline") == -1 && Parse.actionKind("Answer") == 1 && Parse.actionKind("Tolak") == -1 && Parse.actionKind("Jawab") == 1, "call button kinds");
        ok(Parse.actionKind("Send message") == 0 && Parse.actionKind("End call") == -1 && Parse.actionKind("Hang up") == -1 && Parse.actionKind("Speaker") == 0, "call button kinds: 'Send' is not 'End'");
        ok("1:05".equals(Parse.clock(65000)) && "1:01:01".equals(Parse.clock(3661000)) && "0:00".equals(Parse.clock(-5)), "clock format");
        ok("B".equals(Parse.initial("  budi")), "initial");

        // --- Parse: idle wall clock (icon + text on an empty island)
        ok("1:05".equals(Parse.timeOfDay(1, 5, false)), "time 12h: 01 -> 1:05");
        ok("12:07".equals(Parse.timeOfDay(0, 7, false)), "time 12h: 00 -> 12:07");
        ok("13:07".equals(Parse.timeOfDay(13, 7, true)), "time 24h: 13 -> 13:07");
        ok("3:03".equals(Parse.timeOfDay(15, 3, false)), "time 12h: 15 -> 3:03");
        ok("23:59".equals(Parse.timeOfDay(23, 59, true)), "time 24h: 23:59");
        ok(java.util.Arrays.equals(new String[]{"1", "05"}, Parse.timeParts(13, 5, false)), "time parts 12h: 13:05 -> 1 / 05");
        ok(java.util.Arrays.equals(new String[]{"13", "05"}, Parse.timeParts(13, 5, true)), "time parts 24h: 13:05 -> 13 / 05");
        ok(java.util.Arrays.equals(new String[]{"12", "00"}, Parse.timeParts(0, 0, false)), "time parts 12h: midnight -> 12 / 00");
        // --- Parse: the clock card's "text clock" line, seconds wake-up and day progress
        ok("Quarter to eleven".equals(Parse.timeWords(10, 45)), "words: 10:45 -> Quarter to eleven");
        ok("Twenty to eleven".equals(Parse.timeWords(10, 40)), "words: 10:40 -> Twenty to eleven");
        ok("Just after twenty to eleven".equals(Parse.timeWords(10, 42)), "words: 10:42 hedges with Just after");
        ok("Almost quarter to eleven".equals(Parse.timeWords(10, 43)), "words: 10:43 hedges with Almost");
        ok("Quarter past three".equals(Parse.timeWords(15, 15)), "words: 15:15 -> Quarter past three");
        ok("Half past twelve".equals(Parse.timeWords(12, 30)), "words: 12:30 -> Half past twelve");
        ok("Nine o'clock".equals(Parse.timeWords(9, 0)) && "Noon".equals(Parse.timeWords(12, 0)) && "Midnight".equals(Parse.timeWords(0, 0)), "words: o'clock / noon / midnight");
        ok("Almost midnight".equals(Parse.timeWords(23, 58)) && "Just after one o'clock".equals(Parse.timeWords(13, 2)), "words: rounding across the hour");
        for (int hh = 0; hh < 24; hh++) for (int mm = 0; mm < 60; mm++) { String w = Parse.timeWords(hh, mm); if (w.length() < 4 || !Character.isUpperCase(w.charAt(0))) { ok(false, "words: every minute reads something"); hh = 99; break; } }
        ok(Parse.nextWallSecondMs(0L) == 1005L && Parse.nextWallSecondMs(999L) == 6L, "clock card: wakes when the second turns");
        ok(Parse.dayFrac(0, 0, 0) == 0f && Parse.dayFrac(12, 0, 0) == 0.5f && Parse.dayFrac(23, 59, 59) < 1f, "clock card: day progress 0..1");

        // --- Timers / stopwatches
        ok(Parse.chronoMs(10000L, false, 75000L) == 65000L, "chrono: stopwatch counts up from its base");
        ok(Parse.chronoMs(80000L, true, 75000L) == 5000L, "chrono: timer counts down to its end");
        ok(Parse.chronoMs(80000L, true, 90000L) == 0L && Parse.chronoMs(80000L, false, 70000L) == 0L, "chrono: never negative");
        ok(Parse.wallBase(100000L, 160000L, 1000000L) == 940000L, "chrono: elapsed-realtime base -> wall-clock base");
        ok("1:05".equals(Parse.clock(Parse.chronoMs(Parse.wallBase(100000L, 165000L, 5000000L), false, 5000000L))), "chrono: custom stopwatch reads 1:05");
        ok(Parse.sameLabels(new String[]{"Lap", "Pause"}, new String[]{"Lap", "Pause"}) && !Parse.sameLabels(new String[]{"Lap", "Pause"}, new String[]{"Lap", "Resume"}), "chrono: Pause -> Resume is noticed");
        ok(!Parse.sameLabels(new String[]{"A"}, new String[]{"A", "B"}) && Parse.sameLabels(new String[0], new String[0]), "chrono: label lists of different length differ");
        ok(Parse.isClockApp("com.google.android.deskclock") && Parse.isClockApp("com.sec.android.app.clockpackage") && Parse.isClockApp("com.coloros.alarmclock"), "chrono: known clock apps");
        ok(!Parse.isClockApp("com.whatsapp") && !Parse.isClockApp(null), "chrono: other apps are not clock apps");

        // --- one-time codes (the Copy code button)
        ok("482913".equals(Parse.otp("Messages", "Your verification code is 482913. Do not share it.")), "otp: a plain six-digit code");
        ok("123456".equals(Parse.otp("Google", "G-123456 is your Google verification code")), "otp: Google's G- prefix is not part of the code");
        ok("7391".equals(Parse.otp("BCA", "Kode OTP Anda 7391, jangan beritahukan kepada siapapun")), "otp: Indonesian wording, four digits");
        ok("123456".equals(Parse.otp("Bank", "Use 123 456 to log in. OTP valid for 5 min")), "otp: a code written as 3 + 3 with a space");
        ok("123456".equals(Parse.otp("Bank", "Your OTP is 123-456")), "otp: a code written as 3 + 3 with a dash");
        ok("884412".equals(Parse.otp("Bank", "Transfer Rp 250.000 to Budi. OTP 884412")), "otp: an amount in the same text is not the code");
        ok("884412".equals(Parse.otp("Shop", "Order 5521 confirmed. Your code is 884412")), "otp: the number after the keyword wins over an earlier one");
        ok("884412".equals(Parse.otp("Shop", "Order 5521: 884412 is your code")), "otp: with the keyword last, the number just before it wins");
        ok(Parse.otp("Bank", "Payment of Rp 150.000 received") == null, "otp: no keyword, no code");
        ok(Parse.otp("Bank", "Your code expires in 10 minutes") == null, "otp: a keyword with no number is no code");
        ok(Parse.otp("Courier", "Your parcel code 1234567890 is ready") == null, "otp: a ten-digit number is not a code");
        ok(Parse.otp("Cal", "Meeting code room: call at 14:30 on 2026-11-03") == null, "otp: times and dates are not codes");
        ok(Parse.otp("Shop", "Use promo code 2024 for 10% off") == null, "otp: a promo code is not a one-time code");
        ok(Parse.otp("Post", "Zip code 90210 updated") == null, "otp: a zip code is not a one-time code");
        ok("4821".equals(Parse.otp("Bank", "Your code is 4821 and it expires in 3600 seconds")), "otp: a number followed by a time unit is skipped");
        ok(Parse.otp(null, null) == null && Parse.otp("", "") == null, "otp: no text, no code");
        ok(Parse.otp("Decode", "barcode 123456") == null, "otp: 'code' inside another word is not a keyword");
    }
}
