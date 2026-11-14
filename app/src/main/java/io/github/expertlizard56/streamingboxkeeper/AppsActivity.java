package io.github.expertlizard56.streamingboxkeeper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * The apps kept up to date (AppSources): each with where its updates come from and what the last look found. OK on
 * one: change its link or stop updating it. "Add an app": pick one of the box's apps, then say where its updates come
 * from (a GitHub page or a direct .apk link; the known ones are filled in).
 */
public class AppsActivity extends Activity {

    private LinearLayout column;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF161B22, 0xFF0B0E12}));
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(64), dp(40), dp(64), dp(40));
        scroll.addView(column);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        column.removeAllViews();
        TextView title = text("Apps To Keep Updated", 34, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        column.addView(title);
        TextView about = text(Features.on(this, Features.APP_UPDATES)
                ? "Checked once a day; updates install at night while the box sleeps."
                : "App updates are switched off on the main screen: nothing here is checked until they're on.", 16, 0x99FFFFFF);
        about.setPadding(0, dp(6), 0, dp(24));
        column.addView(about);
        View first = null;
        for (AppSources.App app : AppSources.load(this)) {
            View row = row(AppSources.label(this, app.pkg), app.source, AppSources.status(this, app.pkg),
                    v -> changeOrRemove(app));
            if (first == null) first = row;
        }
        View add = row("+  Add an app", "Any app on this box: from its Downloader code, its GitHub page, or a link to its .apk", null,
                v -> pickApp());
        (first != null ? first : add).requestFocus();
    }

    private void changeOrRemove(AppSources.App app) {
        if (app.pkg.equals(getPackageName())) {  // its updates only come from its own GitHub page
            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle(AppSources.label(this, app.pkg))
                    .setItems(new String[] {"Stop updating it"}, (d, which) -> {
                        AppSources.remove(this, app.pkg);
                        build();
                    })
                    .show();
            return;
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(AppSources.label(this, app.pkg))
                .setItems(new String[] {"Change where updates come from", "Stop updating it"}, (d, which) -> {
                    if (which == 0) askSource(app.pkg, app.source);
                    else {
                        AppSources.remove(this, app.pkg);
                        build();
                    }
                })
                .show();
    }

    /** The box's apps with a home screen tile, not already listed, by name. */
    private void pickApp() {
        PackageManager pm = getPackageManager();
        Set<String> listed = new java.util.HashSet<>();
        for (AppSources.App app : AppSources.load(this)) listed.add(app.pkg);
        TreeMap<String, String> apps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = info.activityInfo.packageName;
                boolean system = (info.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                if (system || listed.contains(pkg)) continue;
                apps.put(info.loadLabel(pm).toString() + " ", pkg);
            }
        }
        if (apps.isEmpty()) {
            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setMessage("No other installed apps to add.").setPositiveButton("OK", null).show();
            return;
        }
        List<String> names = new ArrayList<>(apps.keySet());
        List<String> pkgs = new ArrayList<>(apps.values());
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Which App?")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    String pkg = pkgs.get(which);
                    if (pkg.equals(getPackageName())) {
                        AppSources.add(this, pkg, knownSource(pkg));
                        build();
                    } else {
                        askSource(pkg, knownSource(pkg));
                    }
                })
                .show();
    }

    private static String knownSource(String pkg) {
        for (String[] known : AppSources.KNOWN) if (known[0].equals(pkg)) return known[1];
        return "";
    }

    /** Where updates come from: typed with the on-screen keyboard (or a phone's, through the Google TV app). */
    private void askSource(String pkg, String now) {
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        field.setSingleLine(true);
        field.setHint("Downloader code, github.com/owner/app, or https://.../app.apk");
        field.setText(now);
        field.setSelection(field.getText().length());
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Where Do " + AppSources.label(this, pkg) + "'s Updates Come From?")
                .setMessage("Its Downloader code (the numbers you type into Downloader), its GitHub page, or a direct link to its .apk file.")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String source = field.getText().toString();
                    if (!AppSources.validSource(source)) {
                        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                                .setMessage("That isn't a Downloader code, a GitHub page or a link. Try again.")
                                .setPositiveButton("OK", (d2, w2) -> askSource(pkg, source)).show();
                        return;
                    }
                    AppSources.add(this, pkg, source);
                    build();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private View row(String title, String detail, String status, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(24), dp(16), dp(24), dp(16));
        row.setFocusable(true);
        row.setBackground(background());
        row.setOnClickListener(onClick);
        row.addView(text(title, 22, Color.WHITE));
        row.addView(text(detail, 15, 0x99FFFFFF));
        if (status != null) row.addView(text(status, 15, 0xFF8AB4F8));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        size.bottomMargin = dp(12);
        column.addView(row, size);
        return row;
    }

    private StateListDrawable background() {
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(dp(16));
        focused.setColor(0xFF2A3340);
        focused.setStroke(dp(3), Color.WHITE);
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(dp(16));
        normal.setColor(0xFF1C222B);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[0], normal);
        return states;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
