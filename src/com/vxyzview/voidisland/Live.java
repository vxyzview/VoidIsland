package com.vxyzview.voidisland;

/** Which kind of "live activity" an ongoing notification is, judged by the app that posted it. Pure Java. */
final class Live {
    private Live() {}

    static final int NONE = 0, RIDE = 1, FOOD = 2, SPORT = 3;

    private static final String[] RIDE_PKGS = {
        "com.grabtaxi.passenger", "com.gojek.app", "com.ubercab", "me.lyft.android", "ee.mtakso.client", "sinet.startup.inDriver",
        "com.olacabs.customer", "com.didiglobal.passenger", "com.careem.acma", "com.rapido.passenger", "com.bluebird.", "com.maxim."
    };
    private static final String[] FOOD_PKGS = {
        "com.ubercab.eats", "com.application.zomato", "in.swiggy.android", "com.dd.doordash", "com.grubhub.android",
        "com.deliveroo.orderapp", "com.global.foodpanda.android", "com.shopee.id", "com.gojek.food", "com.grab.food"
    };
    private static final String[] SPORT_PKGS = {
        "com.espn.score_center", "com.mobilefootie.wc2010", "com.sofascore.results", "de.motain.iscore", "com.nbaimd.gametime.nba2011",
        "com.bleacherreport.android.teamstream", "com.fifa.fifaapp.android", "com.onefootball", "com.livescore", "com.theathletic"
    };

    private static boolean has(String[] list, String pkg) {
        for (String p : list) {
            if (p.endsWith(".") ? pkg.startsWith(p) : pkg.equals(p)) return true;
        }
        return false;
    }

    /** The kind of live activity for {@code pkg}, with the user's own app lists (checked first, so they can override the built-in guesses). */
    static int kind(String pkg, java.util.Set<String> ride, java.util.Set<String> food, java.util.Set<String> sport) {
        if (pkg == null || pkg.length() == 0) return NONE;
        if (food != null && food.contains(pkg)) return FOOD;
        if (ride != null && ride.contains(pkg)) return RIDE;
        if (sport != null && sport.contains(pkg)) return SPORT;
        return kind(pkg);
    }

    static int kind(String pkg) {
        if (pkg == null || pkg.length() == 0) return NONE;
        if (has(FOOD_PKGS, pkg)) return FOOD;
        if (has(RIDE_PKGS, pkg)) return RIDE;
        if (has(SPORT_PKGS, pkg)) return SPORT;
        String p = pkg.toLowerCase();
        if (p.contains("foodpanda") || p.contains("fooddeliver") || p.contains(".eats")) return FOOD;
        if (p.contains("taxi") || p.contains("ridehail") || p.contains("cabs")) return RIDE;
        if (p.contains("livescore") || p.contains("scores")) return SPORT;
        return NONE;
    }

    /** Accent colour per kind (ARGB). */
    static int accent(int kind) {
        switch (kind) {
            case RIDE: return 0xFF0A84FF;
            case FOOD: return 0xFFFF9F0A;
            case SPORT: return 0xFF30D158;
            default: return 0xFFFFFFFF;
        }
    }

    static String label(int kind) {
        switch (kind) {
            case RIDE: return "Ride";
            case FOOD: return "Order";
            case SPORT: return "Live score";
            default: return "";
        }
    }
}
