package io.github.expertlizard56.streamingboxkeeper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

/**
 * Lets a button-remapping app (Button Mapper, or any other) have the remote's buttons: whatever it maps wins, except
 * the Input button, which stays this app's. Every app that filters remote keys sees every press, so the trouble is two
 * acting on one button, like the CleanYourTV home screen's own button helpers (InputKeyService: the YouTube button;
 * HomeKeyService: the Settings button) and the remapping app. While another app that filters keys is switched on,
 * those helpers are switched off; when it's switched off again, the ones switched off come back. This app's own
 * handling (Input, the TV remote while the TV is elsewhere) stays as it is.
 */
final class RemapApps {

    private static final String TAG = "BoxKeeper";
    private static final String LAUNCHER = "com.gathin.tvhome";
    private static final String[] HELPERS = {LAUNCHER + "/.InputKeyService", LAUNCHER + "/.HomeKeyService"};

    private RemapApps() {
    }

    /** Checks and switches the home screen's helpers off or back on to match. Blocks briefly; off the main thread. */
    static void check(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("keeper", Context.MODE_PRIVATE);
        boolean remapper = remapAppOn(context);
        String switchedOff = prefs.getString("launcherHelpersOff", "");
        if (remapper == !switchedOff.isEmpty()) return;
        String now = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        StringBuilder next = new StringBuilder();
        StringBuilder removed = new StringBuilder();
        for (String service : (now == null ? "" : now).split(":")) {
            if (service.isEmpty()) continue;
            if (remapper && isHelper(service)) {
                removed.append(removed.length() > 0 ? ":" : "").append(service);
                continue;
            }
            next.append(next.length() > 0 ? ":" : "").append(service);
        }
        if (!remapper) next.append(next.length() > 0 ? ":" : "").append(switchedOff);
        if (remapper && removed.length() == 0) return;  // the home screen's helpers aren't on: nothing to step aside
        try {
            SelfAdb.shell(context, "settings put secure enabled_accessibility_services '" + next + "'");
            prefs.edit().putString("launcherHelpersOff", remapper ? removed.toString() : "").apply();
            Log.i(TAG, remapper ? "a button-remapping app is on: the home screen's button helpers are off"
                    : "no button-remapping app: the home screen's button helpers are back on");
        } catch (Exception e) {
            Log.w(TAG, "the home screen's button helpers weren't changed: " + e);
        }
    }

    /** Another switched-on accessibility app that filters remote keys (not this app, not the home screen). */
    private static boolean remapAppOn(Context context) {
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            String pkg = info.getResolveInfo().serviceInfo.packageName;
            if (pkg.equals(context.getPackageName()) || pkg.equals(LAUNCHER)) continue;
            if ((info.getCapabilities() & AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS) != 0) return true;
        }
        return false;
    }

    private static boolean isHelper(String service) {
        for (String helper : HELPERS) {
            if (helper.equalsIgnoreCase(service) || service.equalsIgnoreCase(helper.replace("/.", "/" + LAUNCHER + "."))) {
                return true;
            }
        }
        return false;
    }
}
