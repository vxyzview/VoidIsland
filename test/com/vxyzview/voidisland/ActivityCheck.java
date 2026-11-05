package com.vxyzview.voidisland;

import static com.vxyzview.voidisland.Check.ok;

import android.bluetooth.BluetoothClass;

/**
 * Unit checks for the logic behind the activity cards: the time-left guess for downloads (Pace), the Bluetooth device kinds
 * (BtKind, compared with Android's own BluetoothClass constants) and what an event copies from a newer copy of itself.
 */
final class ActivityCheck {
    private ActivityCheck() {}

    static void run() {
        pace();
        bluetooth();
        try {
            events();
        } catch (NoClassDefFoundError missing) {
            System.out.println("skip: activity event checks need android.jar on the classpath (" + missing.getMessage() + ")");
        }
    }

    private static boolean same(int[] a, int... b) { return java.util.Arrays.equals(a, b); }

    private static void pace() {
        Pace p = new Pace();
        ok(p.remainingMs(0L) == -1L, "pace: nothing seen yet means no guess");

        p.add(0, 0L); p.add(10, 10000L); p.add(20, 20000L); p.add(30, 30000L);
        ok(Math.abs(p.remainingMs(30000L) - 70000L) <= 500L, "pace: 1% a second with 70% left is about 70 s");
        p.add(30, 31000L);   // the same reading again says nothing new
        ok(Math.abs(p.remainingMs(31000L) - 70000L) <= 500L, "pace: a repeated reading does not move the guess");
        ok(Math.abs(p.remainingMs(60000L) - 99000L) <= 500L, "pace: a stalled download's guess grows with the wait");

        Pace early = new Pace();
        early.add(0, 0L); early.add(10, 1000L); early.add(20, 1500L);
        ok(early.remainingMs(1500L) == -1L, "pace: under two seconds watched is too early to guess");

        Pace one = new Pace();
        one.add(0, 0L); one.add(50, 10000L);
        ok(one.remainingMs(10000L) == -1L, "pace: a single step says nothing about speed");

        Pace done = new Pace();
        done.add(0, 0L); done.add(50, 5000L); done.add(100, 10000L);
        ok(done.remainingMs(10000L) == -1L, "pace: a finished download has no time left to guess");

        Pace back = new Pace();
        back.add(0, 0L); back.add(40, 10000L); back.add(80, 20000L);
        ok(back.remainingMs(20000L) > 0L, "pace: a moving download gets a guess");
        back.add(10, 21000L);
        ok(back.remainingMs(21000L) == -1L, "pace: a reading that goes backwards starts the guess over");

        ok(same(Pace.human(1000L), Pace.SEC, 5, 0), "pace.human: one second reads as 5 s, never 0");
        ok(same(Pace.human(12000L), Pace.SEC, 15, 0), "pace.human: seconds round up in fives");
        ok(same(Pace.human(58000L), Pace.MIN, 1, 0), "pace.human: nearly a minute is a minute");
        ok(same(Pace.human(61000L), Pace.MIN, 2, 0), "pace.human: minutes round up");
        ok(same(Pace.human(59 * 60000L), Pace.MIN, 59, 0), "pace.human: 59 minutes stay minutes");
        ok(same(Pace.human(60 * 60000L), Pace.HOUR, 1, 0), "pace.human: 60 minutes is 1 h");
        ok(same(Pace.human(75 * 60000L), Pace.HOUR, 1, 15), "pace.human: hours and minutes");
        ok(same(Pace.human(0L), Pace.SEC, 5, 0), "pace.human: zero is the smallest step");
    }

    private static void bluetooth() {
        ok(BtKind.of(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES) == BtKind.HEADSET, "bt: headphones");
        ok(BtKind.of(BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET) == BtKind.HEADSET, "bt: wearable headset");
        ok(BtKind.of(BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE) == BtKind.HEADSET, "bt: hands-free");
        ok(BtKind.of(BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE) == BtKind.HEADSET, "bt: microphone");
        ok(BtKind.of(BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER) == BtKind.SPEAKER, "bt: loudspeaker");
        ok(BtKind.of(BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO) == BtKind.SPEAKER, "bt: portable audio");
        ok(BtKind.of(BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO) == BtKind.SPEAKER, "bt: hi-fi audio");
        ok(BtKind.of(BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) == BtKind.CAR, "bt: car audio");
        ok(BtKind.of(BluetoothClass.Device.WEARABLE_WRIST_WATCH) == BtKind.WATCH, "bt: wrist watch");
        ok(BtKind.of(BluetoothClass.Device.WEARABLE_PAGER) == BtKind.WATCH, "bt: other wearables count as watches");
        ok(BtKind.of(BluetoothClass.Device.PHONE_SMART) == BtKind.PHONE, "bt: smartphone");
        ok(BtKind.of(BluetoothClass.Device.PHONE_CELLULAR) == BtKind.PHONE, "bt: cellular phone");
        ok(BtKind.of(BluetoothClass.Device.COMPUTER_LAPTOP) == BtKind.COMPUTER, "bt: laptop");
        ok(BtKind.of(BluetoothClass.Device.COMPUTER_DESKTOP) == BtKind.COMPUTER, "bt: desktop");
        ok(BtKind.of(BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER) == BtKind.AUDIO, "bt: other audio / video gear is an audio device");
        ok(BtKind.of(BluetoothClass.Device.Major.PERIPHERAL | 0x40) == BtKind.INPUT, "bt: a keyboard is an input device");
        ok(BtKind.of(BluetoothClass.Device.Major.UNCATEGORIZED) == BtKind.OTHER, "bt: uncategorised stays generic");
        ok(BtKind.of(0) == BtKind.OTHER, "bt: no class at all stays generic");
    }

    private static void events() {
        IslandEvent fresh = new IslandEvent("x", IslandEvent.CHARGE);
        ok(fresh.temp == Integer.MIN_VALUE && fresh.etaMs == -1L && fresh.slot == -1 && fresh.state == 0 && fresh.plug == 0,
            "event: the card details start out as 'not known'");

        IslandEvent old = new IslandEvent("p:1", IslandEvent.PROGRESS);
        old.span = 5000L;
        IslandEvent now = new IslandEvent("p:1", IslandEvent.PROGRESS);
        now.progress = 20; now.plug = 1; now.temp = 318; now.etaMs = 90000L; now.state = 2; now.tag = "Off"; now.slot = 1;
        old.copyFrom(now);
        ok(old.plug == 1 && old.temp == 318 && old.etaMs == 90000L && old.state == 2 && "Off".equals(old.tag) && old.slot == 1,
            "event: a newer copy brings the charging and phone-card details");
        ok(old.span == 5000L, "event: a newer copy never overwrites the count-down span kept on the live event");
        ok(old.progress == 20, "event: a newer copy brings its progress");
    }
}
