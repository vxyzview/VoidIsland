package com.vxyzview.voidisland;

import android.graphics.Path;

/** Minimal SVG path-data parser (M L H V C S Z, absolute + relative). Enough for Material icon paths. */
final class Svg {
    private final String s;
    private int i;

    private Svg(String s) { this.s = s; }

    static Path parse(String d) { return new Svg(d).run(); }

    private void skip() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == ',' || c == '\n' || c == '\t' || c == '\r') i++; else break;
        }
    }

    private boolean more() { skip(); return i < s.length(); }

    private boolean isCmd() {
        char c = s.charAt(i);
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private float num() {
        skip();
        int st = i;
        if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
        boolean dot = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') i++;
            else if (c == '.' && !dot) { dot = true; i++; }
            else break;
        }
        return Float.parseFloat(s.substring(st, i));
    }

    private Path run() {
        Path p = new Path();
        float cx = 0, cy = 0, sx = 0, sy = 0, rx = 0, ry = 0;
        char cmd = 0, prev = 0;
        while (more()) {
            if (isCmd()) cmd = s.charAt(i++);
            boolean rel = cmd >= 'a';
            switch (Character.toUpperCase(cmd)) {
                case 'Z':
                    p.close(); cx = sx; cy = sy; prev = 'Z';
                    continue;
                case 'M': {
                    float x = num(), y = num();
                    if (rel) { x += cx; y += cy; }
                    p.moveTo(x, y); cx = sx = x; cy = sy = y; prev = 'M';
                    cmd = rel ? 'l' : 'L';
                    break;
                }
                case 'L': {
                    float x = num(), y = num();
                    if (rel) { x += cx; y += cy; }
                    p.lineTo(x, y); cx = x; cy = y; prev = 'L';
                    break;
                }
                case 'H': {
                    float x = num();
                    if (rel) x += cx;
                    p.lineTo(x, cy); cx = x; prev = 'H';
                    break;
                }
                case 'V': {
                    float y = num();
                    if (rel) y += cy;
                    p.lineTo(cx, y); cy = y; prev = 'V';
                    break;
                }
                case 'C': {
                    float x1 = num(), y1 = num(), x2 = num(), y2 = num(), x = num(), y = num();
                    if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy; }
                    p.cubicTo(x1, y1, x2, y2, x, y); rx = x2; ry = y2; cx = x; cy = y; prev = 'C';
                    break;
                }
                case 'S': {
                    float x2 = num(), y2 = num(), x = num(), y = num();
                    if (rel) { x2 += cx; y2 += cy; x += cx; y += cy; }
                    boolean sm = prev == 'C' || prev == 'S';
                    float x1 = sm ? 2 * cx - rx : cx, y1 = sm ? 2 * cy - ry : cy;
                    p.cubicTo(x1, y1, x2, y2, x, y); rx = x2; ry = y2; cx = x; cy = y; prev = 'S';
                    break;
                }
                default:
                    return p; // unsupported command: stop, keep what we have
            }
        }
        return p;
    }
}
