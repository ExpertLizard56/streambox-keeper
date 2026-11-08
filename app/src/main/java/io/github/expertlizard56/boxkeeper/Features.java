package io.github.expertlizard56.boxkeeper;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What the person has switched on, on the app's screen. Everything is on from the start except app updates, which
 * install other people's apps and so wait until the person asks for them.
 */
final class Features {

    /** The remote also runs the TV while it shows another input (LinkService). */
    static final String REMOTE = "remote";
    /** A sleeping box gets back on Wi-Fi after the router or access point restarts (Check). */
    static final String WIFI = "wifi";
    /** Background apps are closed at night while the box sleeps (Jobs.tidy). */
    static final String CLEANUP = "cleanup";
    /** At most once a week, a restart at night if memory stays full and the TV is off (Jobs.restartIfNeeded). */
    static final String RESTART = "restart";
    /** Sideloaded apps from GitHub (Jobs.APPS) kept up to date while the box sleeps. */
    static final String APP_UPDATES = "appUpdates";
    /** Box Keeper updates itself from its GitHub releases (Updater). */
    static final String SELF_UPDATE = "selfUpdate";

    static final String[] ALL = {REMOTE, WIFI, CLEANUP, RESTART, APP_UPDATES, SELF_UPDATE};

    private Features() {
    }

    static boolean on(Context context, String feature) {
        return prefs(context).getBoolean("feature." + feature, !APP_UPDATES.equals(feature));
    }

    static void set(Context context, String feature, boolean on) {
        prefs(context).edit().putBoolean("feature." + feature, on).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("keeper", Context.MODE_PRIVATE);
    }
}
