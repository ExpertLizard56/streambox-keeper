package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import org.json.JSONObject;

/**
 * Keeps StreamBox Keeper itself current from its GitHub releases (its listing in the app updates, AppSources, with app
 * updates switched on; Check asks once a day): it asks for the newest
 * release, and if its version is higher than this one, downloads its APK, checks it against GitHub's own SHA-256, and
 * installs it silently through SelfAdb. Android only accepts it if it's signed with the same key as the installed app,
 * so a copy built by someone else is never replaced by ours (or the other way round).
 */
final class Updater {

    private static final String TAG = "BoxUpdater";
    /** The public repository whose releases carry streambox-keeper-<version>.apk. */
    static final String REPO = "ExpertLizard56/streambox-keeper";

    private Updater() {
    }

    /** Looks for a newer release and installs it. Blocks; call off the main thread. */
    static void check(Context context) {
        SelfAdb.allowNetworkWhileAsleep(context);
        try {
            JSONObject release = new JSONObject(Jobs.get("https://api.github.com/repos/" + REPO + "/releases/latest"));
            String tag = release.getString("tag_name");
            String have = Jobs.installedVersion(context, context.getPackageName());
            android.content.SharedPreferences prefs = context.getSharedPreferences("keeper", Context.MODE_PRIVATE);
            String pkg = context.getPackageName();
            if (Jobs.compare(tag, have) <= 0) {
                AppSources.setStatus(prefs, pkg, "Up to date");
                return;
            }
            JSONObject asset = null;
            for (int i = 0; i < release.getJSONArray("assets").length(); i++) {
                JSONObject a = release.getJSONArray("assets").getJSONObject(i);
                if (a.getString("name").matches("streambox-keeper-[\\d.]+\\.apk")
                        || a.getString("name").matches("streaming-box-keeper-[\\d.]+\\.apk")) {  // the name before 0.1.1
                    asset = a;
                }
            }
            if (asset == null) {
                AppSources.setStatus(prefs, pkg, "Release " + tag + " has no .apk");
                return;
            }
            Log.i(TAG, "updating " + have + " -> " + tag);
            File apk = new File(context.getCacheDir(), "streambox-keeper-update.apk");
            Jobs.download(asset.getString("browser_download_url"), apk);
            if (!Jobs.digestMatches(asset, apk)) {
                Log.w(TAG, "the download doesn't match GitHub's checksum; not installing");
                AppSources.setStatus(prefs, pkg, "Couldn't check: the download didn't match GitHub's checksum");
                apk.delete();
                return;
            }
            String version = tag.replaceFirst("^v", "");
            // Written first: this app stops as it's replaced, so nothing after a successful install runs.
            AppSources.setStatus(prefs, pkg, "Updated to " + version);
            Jobs.note(prefs, "lastAppUpdate", "StreamBox Keeper " + version);
            String result = SelfAdb.install(context, apk);
            apk.delete();
            Log.i(TAG, result);
            if (!result.contains("Success")) AppSources.setStatus(prefs, pkg, "Android refused " + version + " (" + result.trim() + ")");
        } catch (Exception e) {
            Log.i(TAG, "no update now (" + e.getMessage() + ")");
            AppSources.setStatus(context.getSharedPreferences("keeper", Context.MODE_PRIVATE), context.getPackageName(),
                    "Couldn't check: " + e.getMessage());
        }
    }

    /** The file's SHA-256, in lower-case hex. */
    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = in.read(buffer)) > 0; ) digest.update(buffer, 0, n);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
