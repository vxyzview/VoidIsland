package com.vxyzview.voidisland;

import java.util.Locale;

/** Weather codes -> icon kind, words and temperature text (pure Java so it can be unit-tested on a JVM). Codes are WMO, as Open-Meteo reports them. */
final class Wx {
    private Wx() {}

    static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, DRIZZLE = 4, RAIN = 5, SNOW = 6, STORM = 7;

    static int kind(int code) {
        if (code == 0) return CLEAR;
        if (code == 1 || code == 2) return PARTLY;
        if (code == 3) return CLOUDY;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 57) return DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95 && code <= 99) return STORM;
        return CLOUDY;
    }

    static String desc(int code, boolean day) {
        switch (kind(code)) {
            case CLEAR: return day ? "Sunny" : "Clear";
            case PARTLY: return code == 1 ? (day ? "Mostly sunny" : "Mostly clear") : "Partly cloudy";
            case CLOUDY: return "Cloudy";
            case FOG: return "Foggy";
            case DRIZZLE: return code >= 56 ? "Freezing drizzle" : "Drizzle";
            case RAIN: return code >= 80 ? "Showers" : (code >= 65 ? "Heavy rain" : "Rain");
            case SNOW: return "Snow";
            default: return code >= 96 ? "Thunderstorm, hail" : "Thunderstorm";
        }
    }

    /** Countries that show Fahrenheit. */
    static boolean fahrenheitCountry(String cc) {
        if (cc == null) return false;
        String c = cc.toUpperCase(Locale.ROOT);
        return c.equals("US") || c.equals("LR") || c.equals("MM") || c.equals("BS") || c.equals("BZ") || c.equals("KY") || c.equals("PW");
    }

    /** @param mode 0 = follow the country, 1 = Celsius, 2 = Fahrenheit */
    static boolean useF(int mode, String cc) { return mode == 2 || (mode == 0 && fahrenheitCountry(cc)); }

    /** "27°" in the chosen unit. */
    static String temp(double celsius, boolean f) {
        long v = Math.round(f ? celsius * 9.0 / 5.0 + 32.0 : celsius);
        return (v == 0 ? 0 : v) + "\u00b0";
    }
}
