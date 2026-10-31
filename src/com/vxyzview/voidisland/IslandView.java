package com.vxyzview.voidisland;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/**
 * The island itself. How it stays smooth:
 *  - ONE surface whose width/height/corner radius/drop are driven by springs from a single Choreographer callback.
 *  - The surface is clipped with a ViewOutline (done on the render thread); content is only alpha/scale-transformed.
 *  - The overlay WINDOW is never resized per frame: it is resized once before a transition (to fit old+new) and once after.
 *  - No allocations in the frame loop; the loop stops completely when everything has settled.
 * Shape (pill / notch / box), sizes, colours and motion all come from {@link Style}.
 */
final class IslandView extends ViewGroup {
    static final int HIDDEN = Geo.HIDDEN, COMPACT = Geo.COMPACT, EXPANDED = Geo.EXPANDED;

    interface Host {
        void applyWindow(int w, int h, boolean touchable);
        void onTap(int mode);
        /** 0 = up, 1 = down, 2 = left, 3 = right */
        void onSwipe(int dir);
        void onOutside();
        /** Something threw inside the island's own animation / install code: rebuild the overlay instead of letting the process die. */
        void onFault();
    }

    /** FrameLayout that never needs an off-screen buffer for alpha. */
    static final class Layer extends FrameLayout {
        Layer(Context c) { super(c); }
        @Override public boolean hasOverlappingRendering() { return false; }
    }

    private final Host host;
    private final float d;
    final int padX;
    int padB, drop;
    private final Layer compact, expanded;
    private final Spring sw = new Spring(), sh = new Spring(), sr = new Spring(), st = new Spring(), sg = new Spring(),
        swap = new Spring(), press = new Spring();
    private final float[] geo = new float[5];
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Style style = new Style();
    private int mode = HIDDEN;
    private IslandEvent cEv, eEv;
    private int baseCW = 150, baseEW = 340, cH = 32, dot = 32, cRpx = 16, eRpx = 38, borderPx;
    private int cLW = 1, eLW = 1, eLH = 1;
    private int winW, winH;
    private boolean touchable;
    private Runnable pendingRun, pendingSwap;
    private int gen;   // bumped by every present(): work queued by an older present() must never run after a newer one
    private int wantW, wantH;
    private boolean running;
    private long last;
    private boolean haptics = true;

    private final float slop;
    private float downX, downY;
    private long downT;
    private boolean swiping;

    private final Runnable fallback = new Runnable() {
        @Override public void run() {
            if (pendingRun != null) { Runnable r = pendingRun; pendingRun = null; guarded(r); }
        }
    };

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override public void doFrame(long nanos) {
            // This runs inside the process that also hosts the accessibility service: an exception that escapes here
            // kills the process and Android switches the service off. Stop animating and let the controller rebuild instead.
            try {
                float dt = last == 0 ? 1f / 60f : Math.min((nanos - last) / 1e9f, 0.05f);
                last = nanos;
                boolean act = stepAll(dt);
                applyVisuals();
                if (act) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    running = false;
                    last = 0;
                    settled();
                }
            } catch (Throwable t) {
                running = false;
                last = 0;
                host.onFault();
            }
        }
    };

    /** Runs queued island work; a failure asks the controller for a clean rebuild rather than crashing the process. */
    private void guarded(Runnable r) {
        try { r.run(); } catch (Throwable t) { host.onFault(); }
    }

    IslandView(Context c, Host host, float density) {
        super(c);
        this.host = host;
        this.d = density;
        padX = dp(12);
        padB = dp(18);
        drop = dp(8);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        compact = new Layer(c);
        expanded = new Layer(c);
        addView(compact);
        addView(expanded);
        compact.setVisibility(INVISIBLE);
        expanded.setVisibility(INVISIBLE);
        stroke.setStyle(Paint.Style.STROKE);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                Geo.surface(shapeFor(mode), v.getWidth(), sw.value, st.value, sh.value, sr.value, sg.value, geo);
                if (geo[2] <= geo[0] || geo[3] <= geo[1]) { o.setEmpty(); return; }
                o.setRoundRect(Math.round(geo[0]), Math.round(geo[1]), Math.round(geo[2]), Math.round(geo[3]), geo[4]);
            }
        });
        setClipToOutline(true);
        st.eps = 0.25f;
        swap.snap(1f);
        swap.eps = 0.004f;
        press.snap(1f);
        press.eps = 0.002f;
        setVisibility(INVISIBLE);
    }

    private int dp(float v) { return Math.round(v * d); }

    // ------------------------------------------------------------------ configuration

    /** Apply a new style (sizes, shape, colours, motion). Safe to call at any time. */
    void configure(Style s, int screenW, int statusBarPx, boolean appLayer) {
        style = s;
        cH = dp(s.cH);
        dot = cH;
        baseEW = Math.min(dp(s.eW), screenW - 2 * Math.max(padX, dp(s.mSide)));
        baseCW = Math.min(dp(s.cW), baseEW - dp(40));
        cRpx = dp(s.cR);
        eRpx = dp(s.eR);
        borderPx = dp(s.borderW);
        // the card sits a little lower than the pill (like iOS); an expanded notch stays glued to the top edge
        drop = s.eShape == Style.NOTCH ? 0 : (s.mDrop < 0 ? (appLayer ? statusBarPx + dp(2) : dp(8)) : dp(s.mDrop));
        padB = dp(18) + drop;
        haptics = s.haptic;
        setBackgroundColor(s.bg);
        stroke.setColor(s.borderCol);
        if (mode == HIDDEN) {
            sw.snap(dot);
            sh.snap(dot);
            sr.snap(dot / 2f);
            st.snap(0f);
            sg.snap(0f);
            winW = even(dot + 2 * padX);
            winH = dot + padB;
        }
    }

    int winW() { return winW; }

    int winH() { return winH; }

    private static int even(int v) { return (v + 1) & ~1; }

    // ------------------------------------------------------------------ showing things

    /** Morph to {@code m} showing {@code ev} (content cross-fades when it changes within the same state). */
    void present(IslandEvent ev, int m) { present(ev, m, true); }

    /** @param fade false = replace content instantly and let the springs animate the surface to its new size (live restyling) */
    void present(final IslandEvent ev, final int m, boolean fade) {
        final int g = ++gen;
        pendingRun = null;
        pendingSwap = null;
        // A swap-out (content fading to 0) may have been interrupted by this newer change. Its swap-in was just discarded,
        // so bring the content back now; otherwise the island stays faded to nothing until the service is restarted.
        if (swap.target < 1f) { swap.tune(520f, 1f); swap.to(1f); start(); }
        final int tw, th, exH;
        final View ex;
        if (m == EXPANDED) {
            ex = Cards.buildExpanded(getContext(), ev, baseEW);
            ex.measure(MeasureSpec.makeMeasureSpec(baseEW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            exH = ex.getMeasuredHeight();
            tw = baseEW;
            th = exH;
        } else if (m == COMPACT) {
            ex = null; exH = 0; tw = baseCW; th = cH;
        } else {
            ex = null; exH = 0; tw = dot; th = dot;
        }
        final boolean swapNeeded = fade && m == mode && ((m == COMPACT && cEv != null) || (m == EXPANDED && eEv != null));
        final Runnable install = new Runnable() {
            @Override public void run() {
                if (g != gen) return;   // a newer present() replaced this one while it waited
                if (m == EXPANDED) installExpanded(ev, ex, baseEW, exH);
                else if (m == COMPACT) installCompact(ev);
                retarget(m, tw, th);
            }
        };
        Runnable go = install;
        if (swapNeeded) {
            go = new Runnable() {
                @Override public void run() {
                    if (g != gen) return;
                    pendingSwap = new Runnable() {
                        @Override public void run() {
                            if (g != gen) return;
                            install.run();
                            swap.tune(520f, 1f);
                            swap.to(1f);
                        }
                    };
                    swap.tune(1500f, 1f);
                    swap.to(0f);
                    start();
                }
            };
        }
        if (m != HIDDEN && getVisibility() != VISIBLE) setVisibility(VISIBLE);
        int needW = Math.max(tw, Math.max(Math.round(sw.target), Math.round(sw.value))) + 2 * padX;
        int needH = Math.max(th, Math.max(Math.round(sh.target), Math.round(sh.value))) + padB;
        ensureWindow(needW, needH, m != HIDDEN, go);
    }

    private void ensureWindow(int w, int h, boolean touch, Runnable run) {
        w = even(w);
        if (w <= winW && h <= winH && touch == touchable) { run.run(); return; }
        int nw = Math.max(w, winW), nh = Math.max(h, winH);
        boolean resized = nw != winW || nh != winH;
        winW = nw;
        winH = nh;
        touchable = touch;
        host.applyWindow(nw, nh, touch);
        if (!resized) { run.run(); return; }
        pendingRun = run;
        wantW = nw;
        wantH = nh;
        removeCallbacks(fallback);
        postDelayed(fallback, 120);
    }

    private void installCompact(IslandEvent ev) {
        cEv = ev;
        cLW = baseCW;
        Cards.buildCompact(getContext(), ev, compact, cH);
        requestLayout();
    }

    private void installExpanded(IslandEvent ev, View content, int w, int h) {
        eEv = ev;
        expanded.removeAllViews();
        eLW = w;
        eLH = h;
        expanded.addView(content, new FrameLayout.LayoutParams(w, h));
        requestLayout();
    }

    /** The shape governing a given state: compact and expanded set it independently. */
    private int shapeFor(int md) { return md == EXPANDED ? style.eShape : style.cShape; }

    private void retarget(int m, int tw, int th) {
        boolean grow = tw > sw.target + 2 || th > sh.target + 2;
        mode = m;
        int shp = shapeFor(m);
        float rad = Geo.radius(shp, m, th, cRpx, eRpx, dot);
        float k = style.speed / 100f, z = 1f - 0.5f * style.bounce / 100f;     // bounce 80 -> damping ratio 0.6
        float zc = Math.min(1f, z + 0.2f);
        if (m == HIDDEN) {
            sw.tune(460f * k, 0.9f); sh.tune(460f * k, 0.9f); sr.tune(460f * k, 0.9f);
        } else if (grow) {
            sw.tune(330f * k, z); sh.tune(290f * k, Math.min(1f, z + 0.02f)); sr.tune(300f * k, Math.min(1f, z + 0.2f));
        } else {
            sw.tune(420f * k, zc); sh.tune(420f * k, zc); sr.tune(380f * k, Math.min(1f, zc + 0.1f));
        }
        st.tune((m == EXPANDED ? 330f : 430f) * k, m == EXPANDED ? z + 0.02f : zc);
        sg.tune((m == EXPANDED ? 330f : 430f) * k, m == EXPANDED ? z + 0.02f : zc);   // glue rides st's clock so pill/notch morphs stay together
        sw.to(tw);
        sh.to(th);
        sr.to(rad);
        st.to(m == EXPANDED ? drop : 0f);
        sg.to(shp == Style.NOTCH ? rad : 0f);   // glue (notch top pushed above the edge) only when the shown shape is a notch
        start();
    }

    // ------------------------------------------------------------------ animation loop

    /** Follows Settings > Developer options > Animator duration scale (0 = "Remove animations" = no motion). */
    private void syncAnimScale() {
        float sc = 1f;
        try { sc = Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f); } catch (Throwable t) { /* keep normal speed */ }
        Spring.rate = sc <= 0f ? Float.POSITIVE_INFINITY : 1f / Math.max(0.1f, Math.min(10f, sc));
    }

    private void start() {
        if (!running) {
            syncAnimScale();
            running = true;
            last = 0;
            Choreographer.getInstance().postFrameCallback(frame);
        }
    }

    private boolean stepAll(float dt) {
        // swap may only be heading to 0 while a swap-in is waiting for it; anything else would leave the island blank for good
        if (swap.target == 0f && pendingSwap == null) { swap.tune(520f, 1f); swap.to(1f); }
        boolean a = sw.step(dt);
        a |= sh.step(dt);
        a |= sr.step(dt);
        a |= st.step(dt);
        a |= sg.step(dt);
        a |= press.step(dt);
        a |= swap.step(dt);
        if (pendingSwap != null && swap.target == 0f && swap.value < 0.04f) {
            Runnable r = pendingSwap;
            pendingSwap = null;
            r.run();
            a = true;
        }
        return a || pendingSwap != null;
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    private static float smooth(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3f - 2f * t);
    }

    private void applyVisuals() {
        invalidateOutline();
        if (borderPx > 0) invalidate();
        float p = clamp01((sw.value - baseCW) / Math.max(1f, baseEW - baseCW));
        float appear = clamp01((sw.value - dot) / Math.max(1f, baseCW - dot));
        float sa = clamp01(swap.value);
        setLayer(compact, (1f - smooth(0f, 0.42f, p)) * smooth(0.55f, 1f, appear) * sa, 1f - 0.12f * p);
        setLayer(expanded, smooth(0.52f, 0.95f, p) * sa, 0.90f + 0.10f * p);
        compact.setTranslationY(st.value);
        expanded.setTranslationY(st.value);
        setScaleX(press.value);
        setScaleY(press.value);
    }

    private void setLayer(View v, float a, float s) {
        int vis = a > 0.01f ? VISIBLE : INVISIBLE;
        if (v.getVisibility() != vis) v.setVisibility(vis);
        if (vis == VISIBLE) {
            v.setAlpha(a);
            v.setScaleX(s);
            v.setScaleY(s);
        }
    }

    @Override protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (borderPx > 0 && (style.borderCol >>> 24) != 0) {
            Geo.surface(shapeFor(mode), getWidth(), sw.value, st.value, sh.value, sr.value, sg.value, geo);
            stroke.setStrokeWidth(borderPx * 2f); // half of it falls outside the clip, so this shows exactly borderPx
            c.drawRoundRect(geo[0], geo[1], geo[2], geo[3], geo[4], geo[4], stroke);
        }
    }

    /** Everything stopped moving: free views, shrink the window so it stops catching touches. */
    private void settled() {
        if (mode == HIDDEN) {
            compact.removeAllViews();
            expanded.removeAllViews();
            cEv = null;
            eEv = null;
            setVisibility(INVISIBLE);
        } else if (mode == COMPACT) {
            expanded.removeAllViews();
            eEv = null;
        } else {
            compact.removeAllViews();
            cEv = null;
        }
        if (pendingRun != null || pendingSwap != null) return;
        int w = even((mode == HIDDEN ? dot : Math.round(sw.target)) + 2 * padX);
        int h = (mode == HIDDEN ? dot : Math.round(sh.target)) + padB;
        boolean t = mode != HIDDEN;
        if (w == winW && h == winH && t == touchable) return;
        winW = w;
        winH = h;
        touchable = t;
        host.applyWindow(w, h, t);
    }

    // ------------------------------------------------------------------ layout

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), MeasureSpec.getSize(hSpec));
        compact.measure(MeasureSpec.makeMeasureSpec(cLW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cH, MeasureSpec.EXACTLY));
        expanded.measure(MeasureSpec.makeMeasureSpec(eLW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(eLH, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int cl = (w - cLW) / 2, el = (w - eLW) / 2;
        compact.layout(cl, 0, cl + cLW, cH);
        expanded.layout(el, 0, el + eLW, eLH);
        compact.setPivotX(cLW / 2f);
        compact.setPivotY(cH / 2f);
        expanded.setPivotX(eLW / 2f);
        expanded.setPivotY(0f);
        setPivotX(w / 2f);
        setPivotY(0f);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        applyVisuals();
        if (pendingRun != null && w >= wantW && h >= wantH) {
            final Runnable r = pendingRun;
            pendingRun = null;
            removeCallbacks(fallback);
            post(new Runnable() { @Override public void run() { guarded(r); } });
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Choreographer.getInstance().removeFrameCallback(frame);
        running = false;
        last = 0;
    }

    // ------------------------------------------------------------------ touch

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_OUTSIDE) { host.onOutside(); return true; }
        if (a == MotionEvent.ACTION_DOWN) { press.tune(600f, 0.7f); press.to(0.955f); start(); }
        else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { press.tune(380f, 0.5f); press.to(1f); start(); }
        return super.dispatchTouchEvent(e);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                swiping = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!swiping) {
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    boolean vertical = Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx);
                    boolean sideways = mode == COMPACT && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy);
                    if (vertical || sideways) { swiping = true; return true; }
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                downT = SystemClock.uptimeMillis();
                swiping = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getRawX() - downX) > slop || Math.abs(e.getRawY() - downY) > slop) swiping = true;
                return true;
            case MotionEvent.ACTION_UP: {
                float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                if (swiping) {
                    float min = 26f * d;
                    if (Math.abs(dy) >= Math.abs(dx) && Math.abs(dy) > min) host.onSwipe(dy < 0 ? 0 : 1);
                    else if (Math.abs(dx) > min) host.onSwipe(dx < 0 ? 2 : 3);
                } else if (SystemClock.uptimeMillis() - downT < 450) {
                    if (haptics) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                    host.onTap(mode);
                }
                return true;
            }
            default:
                return true;
        }
    }
}
