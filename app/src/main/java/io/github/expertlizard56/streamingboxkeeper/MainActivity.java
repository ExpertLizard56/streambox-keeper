package io.github.expertlizard56.streamingboxkeeper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * The app's screen: the three set-up steps (system access, the TV remote, which TV), each done or with a button that
 * does it or says how; a switch for each feature (Features); and what the jobs last did. Opening it also starts the
 * upkeep (it starts after every boot too).
 */
public class MainActivity extends Activity {

    private LinearLayout column;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Check.schedule(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF161B22, 0xFF0B0E12}));
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(64), dp(40), dp(64), dp(40));
        scroll.addView(column);
        setContentView(scroll);
    }

    /** Built again each time the screen comes back (after the box's question, or the settings page, is answered). */
    @Override
    protected void onResume() {
        super.onResume();
        column.removeAllViews();
        TextView title = text("Streaming Box Keeper", 36, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        column.addView(title);
        TextView loading = text("Checking…", 18, 0x8CFFFFFF);
        column.addView(loading);
        new Thread(() -> {
            boolean debugging = adbPortOpen();
            // Checked again only if it was allowed before: a first check would make the box ask on its own.
            boolean allowed = debugging && SelfAdb.knownAllowed(this) && SelfAdb.allowed(this);
            runOnUiThread(() -> {
                column.removeView(loading);
                build(debugging, allowed);
            });
        }, "status").start();
    }

    private void build(boolean debugging, boolean allowed) {
        View first = null;

        column.addView(heading("Set up"));
        // 1. System access: the box's own debugging, allowed once.
        if (!debugging) {
            first = step(false, "Turn on network debugging",
                    "Settings > System > About: press Build seven times. Then Settings > System > Developer options: "
                            + "turn on Network debugging (or USB debugging on boxes without it).",
                    "Open Developer options", v -> openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } else if (!allowed) {
            first = step(false, "Allow system access",
                    "Press the button: the box asks \"Allow USB debugging?\". Tick Always allow, then Allow.",
                    "Ask now", v -> {
                        ((TextView) v).setText("Answer on the screen…");
                        new Thread(() -> {
                            SelfAdb.allowed(this);  // the box asks; waits for the answer
                            runOnUiThread(this::recreate);
                        }, "ask").start();
                    });
        } else {
            step(true, "System access", "Allowed.", null, null);
        }
        // 2. The TV remote: an accessibility service, switched on through system access when that's allowed.
        boolean remoteOn = remoteServiceOn();
        if (!remoteOn) {
            View b = step(false, "Turn on the TV remote",
                    allowed ? "Press the button to switch on Streaming Box Keeper's remote helper."
                            : "Settings > Accessibility > Streaming Box Keeper: turn it on.",
                    allowed ? "Turn on" : "Open Accessibility", v -> {
                        if (allowed) new Thread(() -> {
                            enableRemoteService();
                            runOnUiThread(this::recreate);
                        }).start();
                        else openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    });
            if (first == null) first = b;
        } else {
            step(true, "TV remote", "On. " + LinkService.describe(this), null, null);
        }
        // 3. Which TV: found alone when it's clear, else picked from a list.
        TvConfig tv = TvConfig.load(this);
        View pick = step(tv != null, tv == null ? "Choose your TV" : "Your TV",
                tv == null ? "A Roku TV or Fire TV on your network. Streaming Box Keeper usually finds it by itself."
                        : ("fire".equals(tv.kind) ? "Fire TV" : "Roku TV") + " at " + tv.address,
                tv == null ? "Find my TV" : "Change", v -> startActivity(new Intent(this, SetupActivity.class)));
        if (first == null && tv == null && allowed) first = pick;

        column.addView(heading("Features"));
        View firstSwitch = null;
        firstSwitch = feature(Features.REMOTE, "Remote controls the TV",
                "When the TV is on another input, the remote's arrows, OK and Back go to the TV.");
        feature(Features.WIFI, "Back on Wi-Fi after a drop", "A sleeping box rejoins within 5 minutes.");
        feature(Features.CLEANUP, "Nightly cleanup", "Closes background apps at night while the box sleeps.");
        feature(Features.RESTART, "Weekly restart when needed",
                "At night, only if memory stays full and the TV is off.");
        feature(Features.APP_UPDATES, "Keep apps up to date",
                "Apps you choose, from their GitHub pages or download links, installed at night.");
        int listed = AppSources.load(this).size();
        step(listed > 0, "Apps to keep updated", listed == 0 ? "None yet." : listed + (listed == 1 ? " app." : " apps."),
                "Choose apps", v -> startActivity(new Intent(this, AppsActivity.class)));
        feature(Features.SELF_UPDATE, "Update Streaming Box Keeper", "New versions from GitHub, installed automatically.");
        feature(Features.INSTALL_FOR_APPS, "Let apps update themselves through it",
                "An app that asks can hand over its own new version to be installed. Only its own, signed the same way.");

        column.addView(heading("Last done"));
        android.content.SharedPreferences prefs = getSharedPreferences("keeper", MODE_PRIVATE);
        String[][] items = {{"lastTidy", "Cleanup"}, {"lastRestart", "Restart"}, {"lastAppUpdate", "App update"}};
        for (String[] item : items) {
            column.addView(text(item[1] + ": " + prefs.getString(item[0], "Not Yet"), 16, 0xB3FFFFFF));
        }
        column.addView(text("Version " + Jobs.installedVersion(this, getPackageName()), 14, 0x66FFFFFF));

        (first != null ? first : firstSwitch).requestFocus();
    }

    /** One set-up step: done (a check mark) or not (its button). Returns the button, or null. */
    private View step(boolean done, String title, String detail, String action, View.OnClickListener onClick) {
        LinearLayout row = card();
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text((done ? "✓  " : "•  ") + title, 22, done ? 0xFF81C995 : Color.WHITE));
        TextView sub = text(detail, 16, 0x99FFFFFF);
        sub.setPadding(dp(30), dp(4), 0, 0);
        words.addView(sub);
        row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        View button = null;
        if (action != null) {
            TextView b = text(action, 18, Color.WHITE);
            b.setPadding(dp(22), dp(10), dp(22), dp(10));
            b.setFocusable(true);
            b.setBackground(buttonBackground());
            b.setOnClickListener(onClick);
            row.addView(b);
            button = b;
        }
        column.addView(row, cardSize());
        return button;
    }

    /** A feature's switch, with what it does under its name. */
    private View feature(String feature, String title, String detail) {
        LinearLayout row = card();
        row.setFocusable(true);
        row.setBackground(buttonBackground());
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(title, 20, Color.WHITE));
        words.addView(text(detail, 15, 0x99FFFFFF));
        row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(Features.on(this, feature));
        toggle.setFocusable(false);
        toggle.setClickable(false);
        row.addView(toggle);
        row.setOnClickListener(v -> {
            boolean on = !toggle.isChecked();
            toggle.setChecked(on);
            Features.set(this, feature, on);
        });
        column.addView(row, cardSize());
        return row;
    }

    private boolean remoteServiceOn() {
        String on = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && on.contains(getPackageName() + "/");
    }

    /** Adds Streaming Box Keeper's remote helper to the switched-on accessibility services, keeping the others. */
    private void enableRemoteService() {
        String on = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String mine = getPackageName() + "/.LinkService";
        String next = on == null || on.isEmpty() ? mine : on + ":" + mine;
        try {
            SelfAdb.shell(this, "settings put secure enabled_accessibility_services '" + next + "'; "
                    + "settings put secure accessibility_enabled 1");
        } catch (Exception ignored) {
            // The screen shows the step again, with the Accessibility settings way.
        }
    }

    /** The box's network debugging is listening (port 5555 on the box itself). */
    private static boolean adbPortOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 5555), 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private TextView heading(String s) {
        TextView t = text(s, 15, 0xFF8AB4F8);
        t.setAllCaps(true);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, dp(28), 0, dp(10));
        return t;
    }

    private LinearLayout card() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(24), dp(16), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xFF1C222B);
        row.setBackground(bg);
        return row;
    }

    private LinearLayout.LayoutParams cardSize() {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        size.bottomMargin = dp(12);
        return size;
    }

    private StateListDrawable buttonBackground() {
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(dp(16));
        focused.setColor(0xFF2A3340);
        focused.setStroke(dp(3), Color.WHITE);
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(dp(16));
        normal.setColor(0xFF263040);
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
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
