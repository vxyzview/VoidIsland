package com.vxyzview.voidisland;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
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

/** Per-app switch: which apps are allowed to appear on the island. */
public final class AppsActivity extends Activity {
    private final ArrayList<ResolveInfo> apps = new ArrayList<ResolveInfo>();
    private Set<String> blocked;
    private PackageManager pm;
    private M3 m;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        m = new M3(this);
        pm = getPackageManager();
        blocked = new HashSet<String>(Prefs.blocked(this));
        getWindow().setBackgroundDrawable(new ColorDrawable(m.surface));
        getWindow().setStatusBarColor(m.surface);
        getWindow().setNavigationBarColor(m.surfaceLow);
        int f = View.SYSTEM_UI_FLAG_VISIBLE;
        if (!m.dark) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(f);

        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all = pm.queryIntentActivities(q, 0);
        final Collator col = Collator.getInstance();
        Collections.sort(all, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo c) {
                return col.compare(String.valueOf(a.loadLabel(pm)), String.valueOf(c.loadLabel(pm)));
            }
        });
        HashSet<String> seen = new HashSet<String>();
        for (ResolveInfo r : all) if (!r.activityInfo.packageName.equals(getPackageName()) && seen.add(r.activityInfo.packageName)) apps.add(r);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(m.dp(16), m.dp(16), m.dp(16), 0);
        TextView t = m.text("Apps", 32f, false, m.onSurface);
        root.addView(t);
        TextView s = m.text("Switch off apps you never want on the island.", 14f, false, m.onSurfaceVariant);
        s.setPadding(0, m.dp(6), 0, m.dp(10));
        root.addView(s);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setAdapter(new Adapter());
        root.addView(m.maxSized(lv, 520), new LinearLayout.LayoutParams(M3.MATCH, 0, 1f));
        setContentView(root);
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int i, View v, ViewGroup parent) {
            final ResolveInfo r = apps.get(i);
            final String pkg = r.activityInfo.packageName;
            LinearLayout row = new LinearLayout(AppsActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(m.dp(56));
            row.setPadding(0, m.dp(8), m.dp(8), m.dp(8));
            row.setBackground(m.ripple(0, 12, 0, m.onSurface));
            row.setClickable(true);
            ImageView icon = new ImageView(AppsActivity.this);
            icon.setImageDrawable(r.loadIcon(pm));
            row.addView(icon, new LinearLayout.LayoutParams(m.dp(40), m.dp(40)));
            TextView name = m.text(r.loadLabel(pm), 16f, false, m.onSurface);
            name.setSingleLine();
            name.setPadding(m.dp(16), 0, m.dp(8), 0);
            row.addView(name, new LinearLayout.LayoutParams(0, M3.WRAP, 1f));
            final M3Switch sw = new M3Switch(m, !blocked.contains(pkg), new M3.OnSel() {
                @Override public void on(int on) {
                    if (on == 1) blocked.remove(pkg); else blocked.add(pkg);
                    Prefs.setBlocked(AppsActivity.this, blocked);
                }
            });
            row.addView(sw, new LinearLayout.LayoutParams(m.dp(52), m.dp(32)));
            row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { sw.toggle(); } });
            return row;
        }
    }
}
