package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keep my settings (Features.KEEP_SETTINGS, off until turned on): puts back box settings a firmware update reset, and
 * leaves the person's own changes alone. KeptSettings decides; this reads (one shell call) and writes through the box's
 * own ADB (SelfAdb), at each Check and when the switch goes on. Watched: match_content_frame_rate, hdr_conversion_mode
 * (read only at boot: the box isn't restarted for it, the screen says it takes effect at the next restart),
 * screensaver_components, the Home app, StreamBox Keeper's own TV remote in the accessibility list, and the apps the
 * person turned off (turned off again after a firmware update turns them back on).
 */
final class KeepSettings {

    private static final String TAG = "BoxKeeper";
    private static final String A11Y = "settings get secure enabled_accessibility_services";
    private static final String READ = "echo fp=$(getprop ro.build.fingerprint)"
            + "; echo frame=$(settings get secure match_content_frame_rate)"
            + "; echo hdr=$(settings get global hdr_conversion_mode)"
            + "; echo saver=$(settings get secure screensaver_components)"
            + "; echo a11y=$(" + A11Y + ")"
            + "; echo home=$(cmd package resolve-activity --brief -a android.intent.action.MAIN"
            + " -c android.intent.category.HOME | tail -n 1)"
            // The apps turned off by a person (enabled=3), not the ones the firmware keeps off (enabled=2). No "off="
            // line at all if the list couldn't be read, so a failed read never looks like "none turned off".
            + "; d=$(pm list packages -d) && echo off=$(for p in $(echo \"$d\" | cut -d: -f2); do"
            + " dumpsys package $p 2>/dev/null | grep -m1 'User 0:' | grep -q 'enabled=3' && printf '%s,' $p; done)";

    private KeepSettings() {
    }

    /** The switch just went on: forget anything from before, and remember the box as it is now. Blocks. */
    static void start(Context context) {
        SharedPreferences.Editor e = prefs(context).edit().remove("keep.fp");
        for (String key : KeptSettings.ALL) e.remove("keep." + key);
        e.apply();
        run(context);
    }

    /** One check: remember, or put back after a firmware update. Blocks; call off the main thread. */
    static void run(Context context) {
        SharedPreferences prefs = prefs(context);
        String pkg = context.getPackageName();
        Map<String, String> read;
        try {
            read = KeptSettings.parse(SelfAdb.shell(context, READ), pkg);
        } catch (Exception e) {
            Log.w(TAG, "settings not read (system access?): " + e);
            return;
        }
        KeptSettings.Decision d = KeptSettings.decide(prefs.getString("keep.fp", null), remembered(context),
                read.remove("fp"), read);
        if (d == null) return;
        StringBuilder done = new StringBuilder();
        boolean hdr = false;
        for (Map.Entry<String, String> back : d.putBack.entrySet()) {
            try {
                String out = SelfAdb.shell(context, command(context, back.getKey(), back.getValue(), pkg));
                if (KeptSettings.APPS_OFF.equals(back.getKey())) {
                    // The command prints each app it turned off; one that's gone from the box is forgotten.
                    java.util.TreeSet<String> offAgain = KeptSettings.set(out.replace("\r", "").replace('\n', ','));
                    java.util.TreeSet<String> keep = KeptSettings.set(read.get(KeptSettings.APPS_OFF));
                    keep.addAll(offAgain);
                    d.remember.put(KeptSettings.APPS_OFF, KeptSettings.join(keep));
                    if (offAgain.isEmpty()) continue;
                    if (done.length() > 0) done.append(", ");
                    done.append("turned off again: ").append(KeptSettings.join(offAgain).replace(",", ", "));
                    continue;
                }
                if (done.length() > 0) done.append(", ");
                done.append(KeptSettings.label(back.getKey())).append(" back to ").append(back.getValue());
                hdr |= KeptSettings.HDR.equals(back.getKey());
            } catch (Exception e) {
                Log.w(TAG, back.getKey() + " not put back: " + e);
            }
        }
        SharedPreferences.Editor e = prefs.edit().putString("keep.fp", d.fingerprint);
        for (String key : KeptSettings.ALL) {
            String v = d.remember.get(key);
            if (v == null) e.remove("keep." + key);
            else e.putString("keep." + key, v);
        }
        e.apply();
        if (done.length() > 0) {
            if (hdr) done.append(" (HDR takes effect at the next restart)");
            Log.i(TAG, "after a firmware update: " + done);
            Jobs.note(prefs, "lastSettingsFix", done.toString());
        }
    }

    /** What's remembered now, by key, in KeptSettings.ALL's order. */
    static Map<String, String> remembered(Context context) {
        SharedPreferences prefs = prefs(context);
        Map<String, String> found = new LinkedHashMap<>();
        for (String key : KeptSettings.ALL) {
            String v = prefs.getString("keep." + key, null);
            if (v != null) found.put(key, v);
        }
        return found;
    }

    /** The shell command that puts one setting back. */
    private static String command(Context context, String key, String value, String pkg) throws Exception {
        switch (key) {
            case KeptSettings.FRAME_RATE: return "settings put secure match_content_frame_rate " + quote(value);
            case KeptSettings.HDR: return "settings put global hdr_conversion_mode " + quote(value);
            case KeptSettings.SCREENSAVER: return "settings put secure screensaver_components " + quote(value);
            case KeptSettings.HOME: return "cmd package set-home-activity " + quote(value);
            case KeptSettings.REMOTE_HELPER:
                // Read again right before writing, so no other service is lost: only our own entry changes.
                boolean on = "on".equals(value);
                String list = SelfAdb.shell(context, A11Y).trim();
                return "settings put secure enabled_accessibility_services "
                        + quote(KeptSettings.withHelper(list, pkg, pkg + "/.LinkService", on))
                        + (on ? "; settings put secure accessibility_enabled 1" : "");
            case KeptSettings.APPS_OFF:
                StringBuilder names = new StringBuilder();
                for (String p : KeptSettings.set(value)) names.append(' ').append(quote(p));
                // Prints each one Android turned off; an app no longer on the box just fails quietly.
                return "for p in" + names + "; do pm disable-user --user 0 \"$p\" 2>/dev/null"
                        + " | grep -q 'new state' && echo \"$p\"; done; true";
            default: throw new IllegalArgumentException(key);
        }
    }

    /** Single quotes for the shell, with any single quote inside kept. */
    private static String quote(String s) {
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("keeper", Context.MODE_PRIVATE);
    }
}
