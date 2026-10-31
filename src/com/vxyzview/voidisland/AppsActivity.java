package com.vxyzview.voidisland;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Per-app switch: which apps are allowed to appear on the island. */
public final class AppsActivity extends Activity {
    /** One launcher app: label resolved once, up front, so sorting and scrolling never hit the PackageManager. */
    private static final class App {
        final String pkg, label;
        final ResolveInfo info;

        App(String pkg, String label, ResolveInfo info) {
            this.pkg = pkg;
            this.label = label;
            this.info = info;
        }
    }

    /** Views of one list row, so scrolling re-binds instead of re-inflating. */
    private static final class Row {
        View root;
        ImageView icon;
        TextView name;
        M3Switch sw;
        String pkg;
    }

    private final ArrayList<App> apps = new ArrayList<App>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> icons = new LruCache<String, Bitmap>(80);
    private Set<String> blocked;
    private PackageManager pm;
    private M3 m;
    private Adapter adapter;
    private TextView status;
    private int iconPx;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        m = new M3(this);
        pm = getPackageManager();
        iconPx = m.dp(40);
        blocked = new HashSet<String>(Prefs.blocked(this));
        getWindow().setBackgroundDrawable(new ColorDrawable(m.surface));
        getWindow().setStatusBarColor(m.surface);
        getWindow().setNavigationBarColor(m.surfaceLow);
        int f = View.SYSTEM_UI_FLAG_VISIBLE;
        if (!m.dark) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(f);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(m.dp(16), m.dp(8), m.dp(16), 0);
        LinearLayout bar = m.row();
        bar.addView(m.iconButton(IconView.BACK, "Back", new Runnable() { @Override public void run() { finish(); } }), m.lp(m.dp(48), m.dp(48)));
        root.addView(bar);
        TextView t = m.text("Apps", 32f, false, m.onSurface);
        t.setPadding(m.dp(4), m.dp(8), 0, 0);
        root.addView(t);
        TextView s = m.text("Switch off apps you never want on the island.", 14f, false, m.onSurfaceVariant);
        s.setPadding(m.dp(4), m.dp(6), 0, m.dp(10));
        root.addView(s);

        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setVerticalScrollBarEnabled(false);
        lv.setSelector(new ColorDrawable(0));
        lv.setClipToPadding(false);
        lv.setPadding(0, 0, 0, m.dp(16));
        adapter = new Adapter();
        lv.setAdapter(adapter);
        status = m.text("Loading apps…", 14f, false, m.onSurfaceVariant);
        status.setPadding(m.dp(4), m.dp(8), 0, 0);
        root.addView(status);
        root.addView(m.maxSized(lv, 520), new LinearLayout.LayoutParams(M3.MATCH, 0, 1f));
        setContentView(root);

        load();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    /** Scans and sorts the launcher apps off the UI thread; the list appears when it is ready. */
    private void load() {
        io.execute(new Runnable() {
            @Override public void run() {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> all = pm.queryIntentActivities(q, 0);
                final ArrayList<App> out = new ArrayList<App>();
                HashSet<String> seen = new HashSet<String>();
                for (ResolveInfo r : all) {
                    String pkg = r.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                    out.add(new App(pkg, String.valueOf(r.loadLabel(pm)), r));
                }
                final Collator col = Collator.getInstance();
                Collections.sort(out, new Comparator<App>() {
                    @Override public int compare(App a, App c) { return col.compare(a.label, c.label); }
                });
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        apps.addAll(out);
                        status.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
                        if (apps.isEmpty()) status.setText("No apps found.");
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
        @Override public long getItemId(int i) { return i; }

        private Row make() {
            final Row h = new Row();
            LinearLayout row = new LinearLayout(AppsActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(m.dp(56));
            row.setPadding(0, m.dp(8), m.dp(8), m.dp(8));
            row.setBackground(m.ripple(0, 12, 0, m.onSurface));
            row.setClickable(true);
            h.icon = new ImageView(AppsActivity.this);
            row.addView(h.icon, new LinearLayout.LayoutParams(iconPx, iconPx));
            h.name = m.text("", 16f, false, m.onSurface);
            h.name.setSingleLine();
            h.name.setPadding(m.dp(16), 0, m.dp(8), 0);
            row.addView(h.name, new LinearLayout.LayoutParams(0, M3.WRAP, 1f));
            h.sw = new M3Switch(m, true, null);
            row.addView(h.sw, new LinearLayout.LayoutParams(m.dp(52), m.dp(32)));
            row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { h.sw.toggle(); } });
            row.setTag(h);
            h.root = row;
            return h;
        }

        @Override public View getView(int i, View v, ViewGroup parent) {
            Row h = v == null ? make() : (Row) v.getTag();
            final App a = apps.get(i);
            h.pkg = a.pkg;
            h.name.setText(a.label);
            h.sw.bind(!blocked.contains(a.pkg), new M3.OnSel() {
                @Override public void on(int on) {
                    if (on == 1) blocked.remove(a.pkg); else blocked.add(a.pkg);
                    Prefs.setBlocked(AppsActivity.this, blocked);
                }
            });
            Bitmap cached = icons.get(a.pkg);
            if (cached != null) {
                h.icon.setImageBitmap(cached);
            } else {
                h.icon.setImageDrawable(null);
                fetchIcon(a, h);
            }
            return h.root;
        }

        /** Decodes the icon on the worker thread and drops it into the row, unless that row was recycled for another app meanwhile. */
        private void fetchIcon(final App a, final Row h) {
            try {
                io.execute(new Runnable() {
                    @Override public void run() {
                        final Bitmap bmp = Img.bmp(a.info.loadIcon(pm), iconPx);
                        if (bmp == null) return;
                        icons.put(a.pkg, bmp);
                        runOnUiThread(new Runnable() {
                            @Override public void run() { if (a.pkg.equals(h.pkg)) h.icon.setImageBitmap(bmp); }
                        });
                    }
                });
            } catch (RuntimeException closing) { /* activity is going away: executor already shut down */ }
        }
    }
}
