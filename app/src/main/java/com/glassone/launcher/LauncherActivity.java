package com.glassone.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LauncherActivity extends Activity {

    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> shownApps = new ArrayList<>();
    private AppAdapter adapter;
    private EditText search;
    private LinearLayout dock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(buildUi());
        loadApps();
    }

    private void configureWindow() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.setDecorFitsSystemWindows(false);
        WindowInsetsController c = w.getInsetsController();
        if (c != null) {
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setPadding(dp(14), dp(40), dp(14), dp(22));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(6), 0, dp(6), 0);
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(18), dp(14), dp(12), dp(14));
        hero.setBackground(glass(0xB8323C4A, 30));
        page.addView(hero, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hero.addView(clockBox, clockLp);

        TextView clock = text(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 38, true);
        clockBox.addView(clock);
        TextView date = text(cap(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(new Date())), 14, false);
        date.setTextColor(0xDFFFFFFF);
        clockBox.addView(date);

        TextView control = roundButton("◫");
        hero.addView(control, new LinearLayout.LayoutParams(dp(54), dp(54)));
        control.setOnClickListener(v -> showControlCenter());

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Cerca app");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xAFFFFFFF);
        search.setTextSize(16f);
        search.setPadding(dp(20), 0, dp(20), 0);
        search.setBackground(glass(0xA82B3542, 28));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        searchLp.topMargin = dp(12);
        page.addView(search, searchLp);

        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(10));
        grid.setHorizontalSpacing(dp(4));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(12), 0, dp(12));
        grid.setSelector(android.R.color.transparent);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        page.addView(grid, gridLp);

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(8), dp(8), dp(8), dp(8));
        dock.setBackground(glass(0xBC313B48, 34));
        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86));
        dockLp.topMargin = dp(8);
        page.addView(dock, dockLp);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                filterApps(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        root.setOnLongClickListener(v -> {
            showLauncherOptions();
            return true;
        });
        return root;
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(q, PackageManager.MATCH_ALL);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo r : resolved) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName;
            String cls = r.activityInfo.name;
            if (pkg.equals(getPackageName())) continue;
            String key = pkg + "/" + cls;
            if (!seen.add(key)) continue;
            CharSequence l = r.loadLabel(pm);
            allApps.add(new AppItem(l == null ? pkg : l.toString(), pkg, cls, r.loadIcon(pm)));
        }
        Collections.sort(allApps, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        shownApps.clear();
        shownApps.addAll(allApps);
        adapter.notifyDataSetChanged();
        buildDock();
    }

    private void filterApps(String value) {
        String q = value.trim().toLowerCase(Locale.ROOT);
        shownApps.clear();
        if (q.isEmpty()) shownApps.addAll(allApps);
        else for (AppItem a : allApps) {
            if (a.label.toLowerCase(Locale.ROOT).contains(q)) shownApps.add(a);
        }
        adapter.notifyDataSetChanged();
    }

    private void buildDock() {
        dock.removeAllViews();
        String[] wanted = {
                "com.samsung.android.dialer",
                "com.google.android.apps.messaging",
                "com.sec.android.app.sbrowser",
                "com.sec.android.app.camera"
        };
        List<AppItem> selected = new ArrayList<>();
        for (String p : wanted) {
            AppItem a = byPackage(p);
            if (a != null && !selected.contains(a)) selected.add(a);
        }
        for (AppItem a : allApps) {
            if (selected.size() >= 4) break;
            if (!selected.contains(a)) selected.add(a);
        }
        for (AppItem a : selected) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            iv.setPadding(dp(10), dp(10), dp(10), dp(10));
            iv.setBackground(glass(0x603E4A58, 22));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(64), 1f);
            lp.leftMargin = dp(5);
            lp.rightMargin = dp(5);
            dock.addView(iv, lp);
            iv.setOnClickListener(v -> launch(a));
        }
    }

    private AppItem byPackage(String pkg) {
        for (AppItem a : allApps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    private void launch(AppItem a) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(a.pkg, a.cls));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception ignored) {
            Intent i = getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (i != null) startActivity(i);
        }
    }

    private void showControlCenter() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(10));
        box.addView(actionButton("Wi‑Fi", Settings.ACTION_WIFI_SETTINGS));
        box.addView(actionButton("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS));
        box.addView(actionButton("Sfondo", Settings.ACTION_WALLPAPER_SETTINGS));
        box.addView(actionButton("Impostazioni", Settings.ACTION_SETTINGS));
        new AlertDialog.Builder(this)
                .setTitle("Glass Control Center")
                .setView(box)
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private TextView actionButton(String label, String action) {
        TextView v = text(label, 17, true);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(18), 0, dp(18), 0);
        v.setBackground(glass(0x553E4A58, 22));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        lp.bottomMargin = dp(8);
        v.setLayoutParams(lp);
        v.setOnClickListener(x -> {
            try { startActivity(new Intent(action)); } catch (Exception ignored) {}
        });
        return v;
    }

    private void showLauncherOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Glass One")
                .setMessage("Vuoi impostare Glass One come schermata Home predefinita?")
                .setPositiveButton("Imposta Home", (d, w) -> {
                    try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
                    catch (Exception ignored) {}
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private TextView roundButton(String s) {
        TextView v = text(s, 27, false);
        v.setGravity(Gravity.CENTER);
        v.setBackground(glass(0x663E4A58, 27));
        return v;
    }

    private TextView text(String s, float sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        v.setTypeface(android.graphics.Typeface.create("sans", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        return v;
    }

    private GradientDrawable glass(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), 0x66FFFFFF);
        return g;
    }

    private String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class AppItem {
        final String label;
        final String pkg;
        final String cls;
        final Drawable icon;
        AppItem(String label, String pkg, String cls, Drawable icon) {
            this.label = label;
            this.pkg = pkg;
            this.cls = cls;
            this.icon = icon;
        }
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shownApps.size(); }
        @Override public Object getItem(int p) { return shownApps.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convertView == null) {
                cell = new LinearLayout(LauncherActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(3), dp(4), dp(3), dp(4));
                icon = new ImageView(LauncherActivity.this);
                icon.setId(android.R.id.icon);
                icon.setPadding(dp(7), dp(7), dp(7), dp(7));
                icon.setBackground(glass(0x493E4A58, 20));
                cell.addView(icon, new LinearLayout.LayoutParams(dp(62), dp(62)));
                label = text("", 11, true);
                label.setId(android.R.id.text1);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(25));
                lp.topMargin = dp(3);
                cell.addView(label, lp);
            } else {
                cell = (LinearLayout) convertView;
                icon = cell.findViewById(android.R.id.icon);
                label = cell.findViewById(android.R.id.text1);
            }
            AppItem a = shownApps.get(position);
            icon.setImageDrawable(a.icon);
            label.setText(a.label);
            cell.setOnClickListener(v -> launch(a));
            return cell;
        }
    }
}
