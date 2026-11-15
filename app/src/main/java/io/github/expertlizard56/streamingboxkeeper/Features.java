package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What the person has switched on, on the app's screen. Everything is on from the start except app updates, which
 * install other people's apps, Google Assistant switched off, which changes the box's own app, and Keep my settings,
 * which writes the box's own settings: they wait until the person asks for them.
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
    /** An app may hand StreamBox Keeper a newer version of itself to install (InstallFor): only its own update. */
    static final String INSTALL_FOR_APPS = "installForApps";

    /** Google Assistant stays off, so the remote's mic button can't start it, while apps keep the mic (Assistant). */
    static final String ASSISTANT = "assistant";
    /** Box settings a firmware update reset are put back; the person's own changes are kept (KeepSettings). */
    static final String KEEP_SETTINGS = "keepSettings";
    static final String[] ALL = {REMOTE, WIFI, CLEANUP, RESTART, APP_UPDATES, INSTALL_FOR_APPS, ASSISTANT, KEEP_SETTINGS};

    private Features() {
    }

    static boolean on(Context context, String feature) {
        boolean byDefault = !APP_UPDATES.equals(feature) && !ASSISTANT.equals(feature) && !KEEP_SETTINGS.equals(feature);
        return prefs(context).getBoolean("feature." + feature, byDefault);
    }

    static void set(Context context, String feature, boolean on) {
        prefs(context).edit().putBoolean("feature." + feature, on).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("keeper", Context.MODE_PRIVATE);
    }
}
