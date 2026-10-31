package com.vxyzview.voidisland;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One small GET helper shared by lyrics and weather: short timeouts, a hard size cap (never buffers a surprise download),
 * and null on any failure. Always call it from a worker thread. (HttpURLConnection already asks for gzip and unpacks it.)
 */
final class Http {
    private Http() {}

    static final String UA = "VoidIsland/1.1 (https://github.com/vxyzview/VoidIsland)";

    /** @return the body of a 200 response, or null on any failure / empty body / more than {@code maxChars} characters. */
    static String get(String url, int connectMs, int readMs, int maxChars) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(connectMs);
            con.setReadTimeout(readMs);
            con.setUseCaches(false);
            con.setRequestProperty("User-Agent", UA);
            con.setRequestProperty("Accept", "application/json");
            if (con.getResponseCode() != 200) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            try {
                StringBuilder b = new StringBuilder();
                char[] buf = new char[8192];
                for (int n; (n = r.read(buf)) > 0;) {
                    b.append(buf, 0, n);
                    if (b.length() > maxChars) return null;   // far bigger than any real answer: don't buffer it
                }
                return b.length() > 0 ? b.toString() : null;
            } finally {
                try { r.close(); } catch (Throwable ignored) { /* ignore */ }
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (con != null) try { con.disconnect(); } catch (Throwable ignored) { /* ignore */ }
        }
    }

    static JSONObject object(String url, int connectMs, int readMs, int maxChars) {
        String b = get(url, connectMs, readMs, maxChars);
        try { return b == null ? null : new JSONObject(b); } catch (Throwable t) { return null; }
    }

    static JSONArray array(String url, int connectMs, int readMs, int maxChars) {
        String b = get(url, connectMs, readMs, maxChars);
        try { return b == null ? null : new JSONArray(b); } catch (Throwable t) { return null; }
    }
}
