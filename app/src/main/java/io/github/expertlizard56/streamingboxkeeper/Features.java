package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What the person has switched on, on the app's screen. Everything is on from the start except app updates, which
 * install other people's apps, and Google Assistant switched off, which changes the box's own app: both wait until the
 * person asks for them (Edition.featureDefault).
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
    /** The apps the person listed (AppSources) kept up to date while the box sleeps. */
    static final String APP_UPDATES = "appUpdates";
    /** Streaming Box Keeper updates itself from its GitHub releases (Updater). */
    static final String SELF_UPDATE = "selfUpdate";
    /** An app may hand Streaming Box Keeper a newer version of itself to install (InstallFor): only its own update. */
    static final String INSTALL_FOR_APPS = "installForApps";

    /** Google Assistant stays off, so the remote's mic button can't start it, while apps keep the mic (Assistant). */
    static final String ASSISTANT = "assistant";
    static final String[] ALL = {REMOTE, WIFI, CLEANUP, RESTART, APP_UPDATES, SELF_UPDATE, INSTALL_FOR_APPS, ASSISTANT};

    private Features() {
    }

    static boolean on(Context context, String feature) {
        return prefs(context).getBoolean("feature." + feature, Edition.featureDefault(feature));
    }

    static void set(Context context, String feature, boolean on) {
        prefs(context).edit().putBoolean("feature." + feature, on).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("keeper", Context.MODE_PRIVATE);
    }
}
