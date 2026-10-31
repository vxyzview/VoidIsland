package com.vxyzview.voidisland;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Weather for the expanded clock, opt-in. Needs the approximate-location permission and the internet.
 * What leaves the phone: the approximate position (rounded to ~1 km) to open-meteo.com, nothing else. Asked for only
 * while the clock card is open and the last answer is older than 30 minutes; nothing runs in the background.
 */
final class Weather {
    private Weather() {}

    static final long TTL_MS = 30L * 60L * 1000L;

    static final class Now {
        double temp, feels, hi, lo;
        int code;
        boolean day = true;
        long at;
        String place = "";
    }

    interface Cb { void on(Now n); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService NET = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) { Thread t = new Thread(r, "vi-weather"); t.setDaemon(true); return t; }
    });
    private static volatile Now mem;
    private static boolean busy;   // main thread only

    static boolean enabled(Context c) { return Prefs.b(c, Prefs.K_WEATHER, false); }

    static boolean hasPermission(Context c) {
        try { return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable t) { return false; }
    }

    static boolean hasPlace(Context c) { return !Float.isNaN(lat(c)); }

    private static float lat(Context c) { return Prefs.sp(c).getFloat(Prefs.K_WLAT, Float.NaN); }

    private static float lon(Context c) { return Prefs.sp(c).getFloat(Prefs.K_WLON, Float.NaN); }

    /** The newest approximate position the phone already knows, remembered (rounded) for later. Cheap: no new fix is requested. */
    static void rememberLocation(Context c) {
        if (!hasPermission(c)) return;
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return;
            Location best = null;
            for (String p : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER}) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (Throwable t) { /* provider missing or refused */ }
            }
            if (best == null) return;
            float la = Math.round(best.getLatitude() * 100f) / 100f, lo = Math.round(best.getLongitude() * 100f) / 100f;
            SharedPreferences sp = Prefs.sp(c);
            boolean moved = Float.isNaN(lat(c)) || Math.abs(la - lat(c)) > 0.05f || Math.abs(lo - lon(c)) > 0.05f;
            SharedPreferences.Editor e = sp.edit().putFloat(Prefs.K_WLAT, la).putFloat(Prefs.K_WLON, lo);
            if (moved) { e.putString(Prefs.K_WPLACE, ""); mem = null; }
            e.apply();
        } catch (Throwable t) { /* no location this time */ }
    }

    /** The last answer, however old (memory first, then what was saved), or null if there never was one. */
    static Now cached(Context c) {
        Now n = mem;
        if (n != null) return n;
        try {
            String s = Prefs.sp(c).getString(Prefs.K_WCACHE, "");
            if (s == null || s.length() == 0) return null;
            String[] f = s.split("\\|", -1);
            if (f.length < 7) return null;
            n = new Now();
            n.temp = Double.parseDouble(f[0]); n.feels = Double.parseDouble(f[1]); n.hi = Double.parseDouble(f[2]); n.lo = Double.parseDouble(f[3]);
            n.code = Integer.parseInt(f[4]); n.day = "1".equals(f[5]); n.at = Long.parseLong(f[6]);
            n.place = Prefs.sp(c).getString(Prefs.K_WPLACE, "");
            mem = n;
            return n;
        } catch (Throwable t) { return null; }
    }

    static boolean fresh(Now n) { return n != null && System.currentTimeMillis() - n.at < TTL_MS; }

    /** Fetches new weather when the last answer is stale; always calls back on the main thread (null = nothing to show / offline). */
    static void refresh(final Context ctx, final Cb cb) {
        final Context c = ctx.getApplicationContext();
        Now have = cached(c);
        if (fresh(have)) { cb.on(have); return; }
        if (!enabled(c) || !hasPermission(c)) { cb.on(have); return; }
        rememberLocation(c);
        final float la = lat(c), lo = lon(c);
        if (Float.isNaN(la) || Float.isNaN(lo)) { cb.on(have); return; }
        if (busy) { cb.on(have); return; }
        busy = true;
        NET.execute(new Runnable() {
            @Override public void run() {
                final Now got = fetch(c, la, lo);
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        if (got != null) mem = got;
                        cb.on(got != null ? got : cached(c));
                    }
                });
            }
        });
    }

    private static Now fetch(Context c, float la, float lo) {
        HttpURLConnection h = null;
        try {
            String q = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                + "&current=temperature_2m,apparent_temperature,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto", la, lo);
            h = (HttpURLConnection) new URL(q).openConnection();
            h.setConnectTimeout(6000);
            h.setReadTimeout(8000);
            h.setRequestProperty("User-Agent", "VoidIsland (Android)");
            if (h.getResponseCode() != 200) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject o = new JSONObject(sb.toString());
            JSONObject cur = o.getJSONObject("current");
            JSONObject day = o.optJSONObject("daily");
            Now n = new Now();
            n.temp = cur.getDouble("temperature_2m");
            n.feels = cur.optDouble("apparent_temperature", n.temp);
            n.code = cur.optInt("weather_code", 3);
            n.day = cur.optInt("is_day", 1) == 1;
            JSONArray mx = day == null ? null : day.optJSONArray("temperature_2m_max"), mn = day == null ? null : day.optJSONArray("temperature_2m_min");
            n.hi = mx != null && mx.length() > 0 ? mx.optDouble(0, n.temp) : n.temp;
            n.lo = mn != null && mn.length() > 0 ? mn.optDouble(0, n.temp) : n.temp;
            n.at = System.currentTimeMillis();
            SharedPreferences sp = Prefs.sp(c);
            String place = sp.getString(Prefs.K_WPLACE, "");
            if (place == null || place.length() == 0) {
                place = placeName(c, la, lo);
                sp.edit().putString(Prefs.K_WPLACE, place).apply();
            }
            n.place = place;
            sp.edit().putString(Prefs.K_WCACHE, n.temp + "|" + n.feels + "|" + n.hi + "|" + n.lo + "|" + n.code + "|" + (n.day ? "1" : "0") + "|" + n.at).apply();
            return n;
        } catch (Throwable t) {
            return null;   // offline / rate-limited: keep showing the last answer
        } finally {
            if (h != null) h.disconnect();
        }
    }

    /** City name from the phone's own geocoder when it has one; empty otherwise. */
    private static String placeName(Context c, float la, float lo) {
        try {
            if (!Geocoder.isPresent()) return "";
            @SuppressWarnings("deprecation")
            List<Address> l = new Geocoder(c, Locale.getDefault()).getFromLocation(la, lo, 1);
            if (l == null || l.isEmpty()) return "";
            Address a = l.get(0);
            String s = a.getLocality();
            if (s == null || s.length() == 0) s = a.getSubAdminArea();
            if (s == null || s.length() == 0) s = a.getAdminArea();
            return s == null ? "" : s;
        } catch (Throwable t) { return ""; }
    }

    /** True for Fahrenheit, following the unit setting (auto = by the phone's country). */
    static boolean useF(Context c) { return Wx.useF(Prefs.i(c, Prefs.K_WUNIT, 0), Locale.getDefault().getCountry()); }
}
