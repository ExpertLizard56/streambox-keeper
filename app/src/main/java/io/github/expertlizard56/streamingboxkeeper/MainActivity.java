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
 * The app's screen: the set-up steps (system access, the TV remote, which TV, the remote's own power and volume, the
 * Input button), each done or with a button that does it or says how; a switch for each feature (Features); and what the jobs last did. Opening it also starts the
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
        TextView title = text("StreamBox Keeper", 36, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        column.addView(title);
        TextView loading = text("Checking…", 18, 0x8CFFFFFF);
        column.addView(loading);
        new Thread(() -> {
            boolean debugging = adbPortOpen();
            // Checked again only if it was allowed before: a first check would make the box ask on its own.
            boolean allowed = debugging && SelfAdb.knownAllowed(this) && SelfAdb.allowed(this);
            RemoteSetup remote = allowed ? RemoteSetup.read(this) : null;
            Assistant.State assistant = allowed ? Assistant.read(this) : null;
            RemoteMic.State mic = allowed ? RemoteMic.read(this) : null;
            runOnUiThread(() -> {
                column.removeView(loading);
                build(debugging, allowed, remote, assistant, mic);
            });
        }, "status").start();
    }

    private void build(boolean debugging, boolean allowed, RemoteSetup remote, Assistant.State assistant,
            RemoteMic.State mic) {
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
                    allowed ? "Press the button to switch on StreamBox Keeper's remote helper."
                            : "Settings > Accessibility > StreamBox Keeper: turn it on.",
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
                tv == null ? "A Roku TV or Fire TV on your network. StreamBox Keeper usually finds it by itself."
                        : ("fire".equals(tv.kind) ? "Fire TV" : "Roku TV") + " at " + tv.address,
                tv == null ? "Find my TV" : "Change", v -> startActivity(new Intent(this, SetupActivity.class)));
        if (first == null && tv == null && allowed) first = pick;
        // 4. The remote's own power and volume buttons, set up in Google TV's own steps (RemoteSetup).
        if (RemoteSetup.available(this)) {
            boolean done = remote != null && remote.done();
            String detail = remote == null
                    ? "So the remote turns the TV on and off and sets its volume. Google TV asks which TV (or soundbar) you have."
                    : "Power " + RemoteSetup.words(remote.power) + ", volume " + RemoteSetup.words(remote.volume) + ".";
            View b = step(done, done ? "Remote's power and volume" : "Set up the remote's power and volume", detail,
                    done ? "Change" : "Set up", v -> openSettings(RemoteSetup.WIZARD));
            if (first == null && !done) first = b;
        }
        // The remote's mic: bonded, but Google TV's remote service never set it up (RemoteMic). Only said when it's so.
        if (mic != null && mic.broken != null) {
            View b = step(false, "The remote's mic isn't set up",
                    "So voice search won't hear you. Fix: Settings > Remotes & Accessories, forget " + mic.broken
                            + ", then hold Back + Home until its light blinks.",
                    "Open settings", v -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS));
            if (first == null) first = b;
        }
        // 5. Which button opens the input menu (InputKey: Input or ★, the same button on different remotes, plus one
        // from another remote if added).
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16, getResources().getDisplayMetrics());
        android.text.SpannableStringBuilder input = new android.text.SpannableStringBuilder()
                .append(InputKey.label(this, px, 0x99FFFFFF))
                .append(" opens the input menu. Button-remapping apps can't take it. Different remote? Change adds its button.");
        boolean clash = false;
        if (remote != null && remote.inputByInfrared()) {
            clash = true;
            input.append(" But the remote sends its Input button to the TV by infrared, so the TV switches by itself:"
                    + " set Input to TV (CEC) in the remote's set-up.");
        }
        if (remote != null && remote.starByInfrared()) {
            clash = true;
            input.append(" But Google TV set ★ to switch the TV's inputs by infrared, so the TV switches by itself:"
                    + " set ★ to an app in Set up customizable button.");
        }
        View in = step(!clash, "Input button", input, "Change", v -> startActivity(new Intent(this, InputKeyActivity.class)));
        if (first == null && clash) first = in;
        step(true, "Disable remote buttons", BlockedKeys.describe(this), "Change",
                v -> startActivity(new Intent(this, BlockedKeysActivity.class)));

        column.addView(heading("Features"));
        View firstSwitch = null;
        firstSwitch = feature(Features.REMOTE, "Remote controls the TV",
                "When the TV is on another input, the remote's arrows, OK and Back go to the TV.");
        feature(Features.WIFI, "Back on Wi-Fi after a drop", "A sleeping box rejoins within 5 minutes.");
        feature(Features.CLEANUP, "Nightly cleanup", "Closes background apps at night while the box sleeps.");
        feature(Features.RESTART, "Weekly restart when needed",
                "At night, only if memory stays full and the TV is off.");
        feature(Features.APP_UPDATES, "Keep apps up to date",
                "Apps you choose, from their GitHub pages or download links, installed when they're not in use.");
        int listed = AppSources.load(this).size();
        step(listed > 0, "Apps to keep updated", listed == 0 ? "None yet." : listed + (listed == 1 ? " app." : " apps."),
                "Choose apps", v -> startActivity(new Intent(this, AppsActivity.class)));
        feature(Features.INSTALL_FOR_APPS, "Let apps update themselves through it",
                "An app that asks can hand over its own new version to be installed. Only its own, signed the same way.");
        assistantFeature(assistant);
        keepSettingsFeature();

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
    private View step(boolean done, String title, CharSequence detail, String action, View.OnClickListener onClick) {
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

    /**
     * The Google Assistant switch. Unlike the others it switches one of the box's own apps off through the box's own
     * ADB (Assistant), so it does that as it's flipped, and says so when this box has no Google Assistant to switch off.
     */
    private View assistantFeature(Assistant.State state) {
        boolean present = state == null || state.installed;
        String explain = present
                ? "Apps can still use the mic, so voice search keeps working in them. If the mic sends no sound at all,"
                        + " pair the remote again: Settings > Remotes & Accessories, forget it, then hold Back + Home."
                : "Google Assistant isn't on this box, so there's nothing to switch off.";
        LinearLayout row = card();
        row.setFocusable(true);
        row.setBackground(buttonBackground());
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("Turn off Google Assistant", 20, Color.WHITE));
        TextView detail = text(explain, 15, 0x99FFFFFF);
        words.addView(detail);
        row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        final Switch toggle = new Switch(this);
        toggle.setChecked(present && Features.on(this, Features.ASSISTANT));
        toggle.setFocusable(false);
        toggle.setClickable(false);
        row.addView(toggle);
        // Switched on but the box doesn't agree (a system update put it back): the next check fixes it.
        if (present && Features.on(this, Features.ASSISTANT) && state != null && !state.done()) {
            String waiting = "Not off yet: the next check switches it off. " + explain;
            detail.setText(waiting);
        }
        row.setOnClickListener(v -> {
            if (!present) return;  // nothing to switch off, and the line above says why
            boolean on = !toggle.isChecked();
            toggle.setChecked(on);
            Features.set(this, Features.ASSISTANT, on);
            final String working = on ? "Turning Google Assistant off…" : "Turning it back on…";
            detail.setText(working);
            new Thread(() -> {
                String result;
                try {
                    if (on) Assistant.turnOff(this);
                    else Assistant.turnOn(this);
                    result = on ? "Off. " + explain : "Back on. " + explain;
                } catch (Exception e) {
                    result = "Couldn't change it: it needs system access (step 1). " + explain;
                }
                final String said = result;
                runOnUiThread(() -> detail.setText(said));
            }, "assistant").start();
        });
        column.addView(row, cardSize());
        return row;
    }

    /**
     * Keep my settings (KeepSettings): the switch, what's remembered and the last put-back. Switched on, it remembers
     * the box as it is right away.
     */
    private View keepSettingsFeature() {
        LinearLayout row = card();
        row.setFocusable(true);
        row.setBackground(buttonBackground());
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("Keep my settings", 20, Color.WHITE));
        TextView detail = text(keepSettingsWords(), 15, 0x99FFFFFF);
        words.addView(detail);
        row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(Features.on(this, Features.KEEP_SETTINGS));
        toggle.setFocusable(false);
        toggle.setClickable(false);
        row.addView(toggle);
        row.setOnClickListener(v -> {
            boolean on = !toggle.isChecked();
            toggle.setChecked(on);
            Features.set(this, Features.KEEP_SETTINGS, on);
            if (!on) {
                detail.setText(keepSettingsWords());
                return;
            }
            detail.setText("Remembering this box's settings…");
            new Thread(() -> {
                KeepSettings.start(this);
                runOnUiThread(() -> detail.setText(keepSettingsWords()));
            }, "keep-settings").start();
        });
        column.addView(row, cardSize());
        return row;
    }

    private String keepSettingsWords() {
        StringBuilder s = new StringBuilder("After a system update, puts back the settings it reset: frame rate matching,"
                + " HDR output, screen saver, home screen and the TV remote helper. Your own changes are kept.");
        if (!Features.on(this, Features.KEEP_SETTINGS)) return s.toString();
        java.util.Map<String, String> kept = KeepSettings.remembered(this);
        if (kept.isEmpty()) {
            s.append("\nNothing remembered yet: it needs system access (step 1).");
        } else {
            s.append("\nRemembered:");
            for (java.util.Map.Entry<String, String> e : kept.entrySet()) {
                s.append("\n  ").append(KeptSettings.label(e.getKey())).append(": ").append(e.getValue());
            }
        }
        String fix = getSharedPreferences("keeper", MODE_PRIVATE).getString("lastSettingsFix", null);
        s.append("\nLast put back: ").append(fix == null ? "Not Yet" : fix);
        return s.toString();
    }

    private boolean remoteServiceOn() {
        String on = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && on.contains(getPackageName() + "/");
    }

    /** Adds StreamBox Keeper's remote helper to the switched-on accessibility services, keeping the others. */
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

    private TextView text(CharSequence s, int sp, int color) {
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
