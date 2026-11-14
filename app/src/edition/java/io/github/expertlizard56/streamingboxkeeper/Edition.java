package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Where a build of this app can add its own extras without changing the shared code. This is the public build's: it
 * adds nothing. A private build (the developer's own, for his home) leaves out this folder (app/src/edition) and puts
 * its own Edition in its place, with the same methods; everything else it builds from this repository as it is.
 */
final class Edition {

    private Edition() {
    }

    /** At every start (boot, install, update), before the first check. */
    static void started(Context context) {
    }

    /** A feature's setting before the person has touched it. */
    static boolean featureDefault(String feature) {
        // App updates install other people's apps, and the Assistant switch changes the box's own app: both off until
        // the person asks for them.
        return !Features.APP_UPDATES.equals(feature) && !Features.ASSISTANT.equals(feature);
    }

    /** At the end of each 5-minute check while online (Jobs.run): this build's own upkeep jobs. */
    static void jobs(Context context, SharedPreferences prefs, boolean asleep, boolean force) {
    }

    /** A test job this build adds (Jobs.runOne); true if it ran one by that name. */
    static boolean runOne(Context context, String job) {
        return false;
    }

    /** Once a day: true if this build updated itself its own way, so the GitHub check is skipped. */
    static boolean updateSelf(Context context) {
        return false;
    }

    /** A remote key this build takes before anything else sees it (LinkService); true to take it. */
    static boolean takesKey(int keyCode) {
        return false;
    }
}
