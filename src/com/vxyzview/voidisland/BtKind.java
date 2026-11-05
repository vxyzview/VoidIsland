package com.vxyzview.voidisland;

/**
 * What kind of thing a Bluetooth device is, from its class-of-device number (BluetoothClass.getDeviceClass()). Pure Java: the
 * numbers are the Bluetooth SIG / Android BluetoothClass values, and the unit checks compare them with Android's own constants.
 */
final class BtKind {
    private BtKind() {}

    static final int OTHER = 0, HEADSET = 1, SPEAKER = 2, CAR = 3, WATCH = 4, PHONE = 5, COMPUTER = 6, INPUT = 7, AUDIO = 8;

    static int of(int deviceClass) {
        switch (deviceClass) {
            case 0x0404: case 0x0408: case 0x0410: case 0x0418: return HEADSET;   // headset, hands-free, microphone, headphones
            case 0x0414: case 0x041C: case 0x0428: return SPEAKER;                // loudspeaker, portable audio, hi-fi
            case 0x0420: return CAR;                                              // car audio
            case 0x0704: return WATCH;                                            // wrist watch
            default: break;
        }
        switch (deviceClass & 0x1F00) {                                           // the major class: what is left
            case 0x0200: return PHONE;
            case 0x0100: return COMPUTER;
            case 0x0700: return WATCH;     // other wearables
            case 0x0500: return INPUT;     // keyboards, mice, gamepads
            case 0x0400: return AUDIO;     // any other audio / video device
            default: return OTHER;
        }
    }
}
