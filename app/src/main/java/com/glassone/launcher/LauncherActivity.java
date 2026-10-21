package com.glassone.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
    private final Handler clockHandler = new Handler(Looper.getMainLooper());

    private AppAdapter adapter;
    private EditText search;
    private LinearLayout dock;
    private TextView clockText;
    private TextView dateText;

    private final Runnable clockTicker = new Runnable() {
        @Override public void run() {
            updateClock();
            clockHandler.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            configureWindow();
            setContentView(buildUi());
            loadApps();
            clockHandler.post(clockTicker);
        } catch (Throwable t) {
            showSafeError(t);
        }
    }

    @Override
    protected void onDestroy() {
        clockHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void configureWindow() {
        try {
            Window w = getWindow();
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                w.setDecorFitsSystemWindows(false);
                WindowInsetsController c = w.getInsetsController();
                if (c != null) {
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
        } catch (Throwable ignored) {}
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.addView(new AuroraBackgroundView(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(52), dp(18), dp(18));
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Top area: large floating glass clock + round Control Center button.
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(topRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(128)));

        LinearLayout clockCard = new LinearLayout(this);
        clockCard.setOrientation(LinearLayout.VERTICAL);
        clockCard.setGravity(Gravity.CENTER_VERTICAL);
        clockCard.setPadding(dp(22), dp(14), dp(18), dp(14));
        clockCard.setBackground(glassPanel(86, 34, 30));
        clockCard.setElevation(dp(10));
        LinearLayout.LayoutParams clockCardLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        clockCardLp.rightMargin = dp(12);
        topRow.addView(clockCard, clockCardLp);

        TextView brand = text("GLASS ONE", 11, true);
        brand.setTextColor(0xD9FFFFFF);
        brand.setLetterSpacing(0.16f);
        clockCard.addView(brand);

        clockText = text("", 45, true);
        clockText.setLetterSpacing(-0.03f);
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clockLp.topMargin = dp(2);
        clockCard.addView(clockText, clockLp);

        dateText = text("", 15, false);
        dateText.setTextColor(0xDEFFFFFF);
        clockCard.addView(dateText);
        updateClock();

        TextView control = text("••\n••", 18, true);
        control.setGravity(Gravity.CENTER);
        control.setLineSpacing(-dp(5), 0.75f);
        control.setBackground(glassPanel(105, 42, 40));
        control.setElevation(dp(12));
        control.setContentDescription("Glass Control Center");
        topRow.addView(control, new LinearLayout.LayoutParams(dp(72), dp(72)));
        control.setOnClickListener(v -> showControlCenter());
        control.setOnLongClickListener(v -> {
            openHomeSettings();
            return true;
        });

        // Floating glass search bar.
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(16), 0, dp(12), 0);
        searchBar.setBackground(glassPanel(78, 26, 30));
        searchBar.setElevation(dp(8));
        LinearLayout.LayoutParams searchBarLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        searchBarLp.topMargin = dp(12);
        page.addView(searchBar, searchBarLp);

        TextView magnifier = text("⌕", 27, false);
        magnifier.setGravity(Gravity.CENTER);
        magnifier.setTextColor(0xEEFFFFFF);
        searchBar.addView(magnifier, new LinearLayout.LayoutParams(dp(36), dp(50)));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Cerca app");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xBFFFFFFF);
        search.setTextSize(16f);
        search.setPadding(dp(6), 0, dp(6), 0);
        search.setBackgroundColor(Color.TRANSPARENT);
        searchBar.addView(search, new LinearLayout.LayoutParams(0, dp(54), 1f));

        TextView mic = text("●", 13, false);
        mic.setGravity(Gravity.CENTER);
        mic.setTextColor(0xD9FFFFFF);
        searchBar.addView(mic, new LinearLayout.LayoutParams(dp(34), dp(50)));

        // App grid.
        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(6));
        grid.setHorizontalSpacing(dp(4));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(14), 0, dp(5));
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        page.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Tiny iOS-like page indicator.
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(dots, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));
        for (int i = 0; i < 3; i++) {
            View dot = new View(this);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(i == 0 ? 0xEFFFFFFF : 0x66FFFFFF);
            dot.setBackground(dg);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(6), dp(6));
            dlp.leftMargin = dp(4);
            dlp.rightMargin = dp(4);
            dots.addView(dot, dlp);
        }

        // Large floating translucent dock.
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(9), dp(8), dp(9), dp(7));
        dock.setBackground(glassPanel(96, 36, 34));
        dock.setElevation(dp(14));
        page.addView(dock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(94)));

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

    private void updateClock() {
        try {
            Date now = new Date();
            if (clockText != null) {
                clockText.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
            }
            if (dateText != null) {
                String d = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now);
                dateText.setText(cap(d));
            }
        } catch (Throwable ignored) {}
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved;
        try {
            resolved = pm.queryIntentActivities(query, 0);
        } catch (Throwable t) {
            resolved = new ArrayList<>();
        }

        allApps.clear();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo r : resolved) {
            try {
                if (r == null || r.activityInfo == null) continue;
                String pkg = r.activityInfo.packageName;
                String cls = r.activityInfo.name;
                if (pkg == null || cls == null || pkg.equals(getPackageName())) continue;
                if (!seen.add(pkg + "/" + cls)) continue;

                CharSequence labelRaw = r.loadLabel(pm);
                String label = labelRaw == null ? pkg : labelRaw.toString();
                Drawable icon;
                try {
                    icon = r.loadIcon(pm);
                } catch (Throwable iconError) {
                    icon = getDrawable(android.R.drawable.sym_def_app_icon);
                }
                allApps.add(new AppItem(label, pkg, cls, icon));
            } catch (Throwable ignored) {}
        }

        Collections.sort(allApps, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        filterApps("");
        buildDock();

        if (allApps.isEmpty()) {
            Toast.makeText(this, "Non riesco a leggere le app installate.", Toast.LENGTH_LONG).show();
        }
    }

    private void filterApps(String value) {
        String q = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        shownApps.clear();
        if (q.isEmpty()) {
            shownApps.addAll(allApps);
        } else {
            for (AppItem a : allApps) {
                if (a.label.toLowerCase(Locale.ROOT).contains(q)) shownApps.add(a);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void buildDock() {
        if (dock == null) return;
        dock.removeAllViews();
        String[] wanted = {
                "com.samsung.android.dialer",
                "com.google.android.apps.messaging",
                "com.samsung.android.messaging",
                "com.sec.android.app.sbrowser",
                "com.android.chrome",
                "com.sec.android.app.camera"
        };

        List<AppItem> selected = new ArrayList<>();
        for (String pkg : wanted) {
            AppItem a = byPackage(pkg);
            if (a != null && !selected.contains(a)) selected.add(a);
            if (selected.size() >= 4) break;
        }
        for (AppItem a : allApps) {
            if (selected.size() >= 4) break;
            if (!selected.contains(a)) selected.add(a);
        }

        for (AppItem a : selected) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(2), 0, dp(2), 0);
            dock.addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));

            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            iv.setPadding(dp(6), dp(6), dp(6), dp(6));
            iv.setBackground(glassPanel(62, 20, 22));
            item.addView(iv, new LinearLayout.LayoutParams(dp(58), dp(58)));

            TextView label = text(shortDockLabel(a.label), 10, false);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextColor(0xEEFFFFFF);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(20));
            item.addView(label, llp);

            item.setContentDescription(a.label);
            item.setOnClickListener(v -> launch(a));
        }
    }

    private String shortDockLabel(String s) {
        if (s == null) return "";
        String v = s.trim();
        if (v.length() <= 11) return v;
        return v.substring(0, 10) + "…";
    }

    private AppItem byPackage(String pkg) {
        for (AppItem a : allApps) {
            if (a.pkg.equals(pkg)) return a;
        }
        return null;
    }

    private void launch(AppItem a) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(a.pkg, a.cls));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Throwable e) {
            Toast.makeText(this, "Impossibile aprire " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Throwable e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Throwable ignored) {
                Toast.makeText(this,
                        "Vai in Impostazioni > App > App predefinite > App Home",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showControlCenter() {
        try {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(18), dp(14), dp(18), dp(10));
            box.setBackground(glassPanel(135, 54, 30));

            TextView title = text("Glass Control Center", 21, true);
            title.setPadding(dp(4), dp(4), dp(4), dp(14));
            box.addView(title);
            box.addView(actionButton("Wi‑Fi", Settings.ACTION_WIFI_SETTINGS));
            box.addView(actionButton("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS));
            box.addView(actionButton("Sfondo", Intent.ACTION_SET_WALLPAPER));
            box.addView(actionButton("Imposta Glass One come Home", Settings.ACTION_HOME_SETTINGS));
            box.addView(actionButton("Impostazioni", Settings.ACTION_SETTINGS));

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(box)
                    .setNegativeButton("Chiudi", null)
                    .create();
            dialog.setOnShowListener(d -> {
                Window w = dialog.getWindow();
                if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            });
            dialog.show();
        } catch (Throwable t) {
            Toast.makeText(this, "Control Center non disponibile", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView actionButton(String label, String action) {
        TextView v = text(label, 16, true);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(18), 0, dp(18), 0);
        v.setBackground(glassPanel(58, 20, 22));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        lp.bottomMargin = dp(9);
        v.setLayoutParams(lp);
        v.setOnClickListener(x -> {
            try {
                startActivity(new Intent(action));
            } catch (Throwable ignored) {
                Toast.makeText(this, "Funzione non disponibile", Toast.LENGTH_SHORT).show();
            }
        });
        return v;
    }

    private void showLauncherOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Glass One")
                .setMessage("Premi Imposta Home per usare Glass One come launcher predefinito. Tieni premuto il pulsante tondo in alto per riaprire rapidamente questa impostazione.")
                .setPositiveButton("Imposta Home", (d, w) -> openHomeSettings())
                .setNeutralButton("Control Center", (d, w) -> showControlCenter())
                .setNegativeButton("Annulla", null)
                .show();
    }

    private TextView text(String s, float sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        v.setTypeface(android.graphics.Typeface.create("sans",
                bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        return v;
    }

    private GradientDrawable glassPanel(int alphaTop, int alphaBottom, int radiusDp) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(alphaTop, 255, 255, 255),
                        Color.argb(alphaBottom, 255, 255, 255)
                });
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(Math.max(1, dp(1)), 0x70FFFFFF);
        return g;
    }

    private void showSafeError(Throwable t) {
        try {
            TextView error = new TextView(this);
            error.setText("Glass One non è riuscito ad avviarsi.\n\nTocca qui per aprire le impostazioni Home.");
            error.setTextColor(Color.WHITE);
            error.setTextSize(18f);
            error.setGravity(Gravity.CENTER);
            error.setPadding(dp(30), dp(30), dp(30), dp(30));
            error.setBackgroundColor(0xFF0B1230);
            error.setOnClickListener(v -> openHomeSettings());
            setContentView(error);
        } catch (Throwable ignored) {}
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
                cell.setPadding(dp(2), dp(4), dp(2), dp(2));

                icon = new ImageView(LauncherActivity.this);
                icon.setId(android.R.id.icon);
                icon.setPadding(dp(6), dp(6), dp(6), dp(6));
                icon.setBackground(glassPanel(52, 15, 21));
                cell.addView(icon, new LinearLayout.LayoutParams(dp(66), dp(66)));

                label = text("", 11, false);
                label.setId(android.R.id.text1);
                label.setGravity(Gravity.CENTER);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setTextColor(0xECFFFFFF);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(26));
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
            cell.setContentDescription(a.label);
            cell.setOnClickListener(v -> launch(a));
            return cell;
        }
    }

    private static final class AuroraBackgroundView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        AuroraBackgroundView(android.content.Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            paint.setShader(new LinearGradient(
                    0, 0, w, h,
                    new int[]{0xFF081126, 0xFF123A73, 0xFF3A1F69, 0xFF11172E},
                    new float[]{0f, 0.38f, 0.72f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);

            drawGlow(canvas, w * 0.18f, h * 0.16f, w * 0.72f,
                    0xA24AA8FF, 0x004AA8FF);
            drawGlow(canvas, w * 0.88f, h * 0.31f, w * 0.66f,
                    0x8D9A56FF, 0x009A56FF);
            drawGlow(canvas, w * 0.50f, h * 0.78f, w * 0.78f,
                    0x66702CFF, 0x00702CFF);
            drawGlow(canvas, w * 0.10f, h * 0.92f, w * 0.48f,
                    0x4D1EB4FF, 0x001EB4FF);

            paint.setShader(new LinearGradient(
                    0, 0, w, 0,
                    new int[]{0x22FFFFFF, 0x00FFFFFF, 0x16FFFFFF},
                    null,
                    Shader.TileMode.CLAMP));
            canvas.save();
            canvas.rotate(-28f, w * 0.45f, h * 0.35f);
            canvas.drawRoundRect(-w * 0.25f, h * 0.20f, w * 1.25f, h * 0.31f,
                    h * 0.05f, h * 0.05f, paint);
            canvas.restore();
        }

        private void drawGlow(Canvas canvas, float cx, float cy, float radius,
                              int inner, int outer) {
            paint.setShader(new RadialGradient(
                    cx, cy, radius,
                    new int[]{inner, outer},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, paint);
        }
    }
}
