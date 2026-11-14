package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The apps the person keeps up to date (Features.APP_UPDATES), each with where its updates come from: a GitHub page
 * (github.com/owner/repo, its newest release), a direct link to an .apk, or a Downloader code (the numbers people type
 * into the Downloader app; aftv.news/<code> names the link, looked up again at every check). Once a day each source is looked at; a file
 * is only installed if it's the same app (its package name, read from the download) and a higher version than the one
 * on the box. Android also refuses an update signed by anyone else, so a wrong link can't swap an app for another.
 * Downloads wait in the app's cache and install as soon as the app isn't in use (not on screen, not playing sound), or
 * while the box sleeps (Jobs). StreamBox Keeper itself is on the list too
 * (a known app, always on the box): its listing is checked by Updater instead, which installs it at once.
 */
final class AppSources {

    private static final String TAG = "AppUpdates";
    /** A Downloader code: just its numbers. */
    private static final Pattern CODE = Pattern.compile("\\d{4,8}");
    /** On a Downloader code's page, the link it sends to. */
    private static final Pattern CODE_TARGET = Pattern.compile("to:\\s*<a href=\"([^\"]+)\"");
    private static final Pattern GITHUB = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+)");

    /** One kept-up-to-date app. */
    static final class App {
        final String pkg;
        final String source;

        App(String pkg, String source) {
            this.pkg = pkg;
            this.source = source;
        }
    }

    /** Ready-made sources, offered when the app is on the box: package, GitHub page. */
    static final String[][] KNOWN = {
            {"io.github.expertlizard56.streamingboxkeeper", "github.com/" + Updater.REPO},
            {"org.smarttube.stable", "github.com/yuliskov/SmartTube"},
            {"org.smarttube.beta", "github.com/yuliskov/SmartTube"},
            {"com.limelight", "github.com/moonlight-stream/moonlight-android"},
            {"com.limelight.noir", "github.com/ClassicOldSong/moonlight-android"},
            {"io.github.expertlizard56.homewatch.tv", "github.com/ExpertLizard56/home-watch"},
    };

    /** The known apps before 0.1.2: a box set up earlier already had the chance to list (or drop) these. */
    private static final Set<String> FIRST_KNOWN = new HashSet<>(Arrays.asList(
            "org.smarttube.stable", "org.smarttube.beta", "com.limelight", "com.limelight.noir"));

    private AppSources() {
    }

    /**
     * The list. A known app goes on it once, the first time it's on this box (also after the box was set up); one
     * taken off stays off.
     */
    static List<App> load(Context context) {
        SharedPreferences prefs = prefs(context);
        List<App> apps = new ArrayList<>();
        String saved = prefs.getString("appSources", null);
        if (saved != null) {
            try {
                JSONArray list = new JSONArray(saved);
                for (int i = 0; i < list.length(); i++) {
                    JSONObject app = list.getJSONObject(i);
                    apps.add(new App(app.getString("package"), app.getString("source")));
                }
            } catch (Exception e) {
                Log.w(TAG, "app list unreadable: " + e);
            }
        }
        Set<String> offered = new HashSet<>(prefs.getStringSet("knownOffered",
                saved == null ? Collections.emptySet() : FIRST_KNOWN));
        List<String[]> add = toOffer(offered, pkg -> installedCode(context, pkg) >= 0);
        for (String[] known : add) {
            offered.add(known[0]);
            if (apps.stream().noneMatch(a -> a.pkg.equals(known[0]))) apps.add(new App(known[0], known[1]));
        }
        if (saved == null || !add.isEmpty()) {
            save(context, apps);
            prefs.edit().putStringSet("knownOffered", offered).apply();
        }
        return apps;
    }

    /** The known apps to put on the list now: on the box, and not put on it before. */
    static List<String[]> toOffer(Set<String> offered, Predicate<String> installed) {
        List<String[]> add = new ArrayList<>();
        for (String[] known : KNOWN) {
            if (!offered.contains(known[0]) && installed.test(known[0])) add.add(known);
        }
        return add;
    }

    static void save(Context context, List<App> apps) {
        JSONArray list = new JSONArray();
        try {
            for (App app : apps) list.put(new JSONObject().put("package", app.pkg).put("source", app.source));
        } catch (Exception ignored) {
        }
        prefs(context).edit().putString("appSources", list.toString()).apply();
    }

    static void add(Context context, String pkg, String source) {
        List<App> apps = load(context);
        apps.removeIf(a -> a.pkg.equals(pkg));
        apps.add(new App(pkg, source.trim()));
        save(context, apps);
        prefs(context).edit().remove("seen." + pkg).remove("status." + pkg).apply();
    }

    static void remove(Context context, String pkg) {
        List<App> apps = load(context);
        apps.removeIf(a -> a.pkg.equals(pkg));
        save(context, apps);
        new File(context.getCacheDir(), pkg + ".apk").delete();
        prefs(context).edit().remove("pending." + pkg).remove("seen." + pkg).remove("status." + pkg).apply();
    }

    /** Whether the text is something updates can come from: a GitHub page, or an http(s) link. */
    static boolean validSource(String source) {
        String s = source.trim();
        return GITHUB.matcher(s).find() || s.matches("(?i)https?://\\S+") || CODE.matcher(s).matches();
    }

    /** What the last look found, for the screen ("Up to date", "Updated to 32.71", "Couldn't check: ..."). */
    static String status(Context context, String pkg) {
        return prefs(context).getString("status." + pkg, "Not checked yet");
    }

    /** Looks at every source and downloads what's newer. Blocks; Jobs runs it once a day. */
    static void findUpdates(Context context) {
        SharedPreferences prefs = prefs(context);
        for (App app : load(context)) {
            if (app.pkg.equals(context.getPackageName())) continue;  // Updater's
            long have = installedCode(context, app.pkg);
            if (have < 0) {
                setStatus(prefs, app.pkg, "Not on this box");
                continue;
            }
            try {
                String source = app.source.trim();
                // A Downloader code is looked up each time: its owner can point it at a newer file.
                if (CODE.matcher(source).matches()) source = downloaderLink(source);
                Matcher github = GITHUB.matcher(source);
                String lower = source.toLowerCase(java.util.Locale.ROOT);
                boolean releasesPage = github.find() && !lower.contains("/download/") && !lower.endsWith(".apk");
                String found = releasesPage ? fromGitHub(context, prefs, app, github.group(1), github.group(2))
                        : fromLink(context, prefs, app, source);
                if (found != null) setStatus(prefs, app.pkg, found);
            } catch (Exception e) {
                setStatus(prefs, app.pkg, "Couldn't check: " + e.getMessage());
                Log.i(TAG, app.pkg + ": " + e);
            }
        }
    }

    /** The newest release on GitHub: skipped if already looked at, else its file for this box is downloaded. */
    private static String fromGitHub(Context context, SharedPreferences prefs, App app, String owner, String repo)
            throws Exception {
        String name = repo.replaceAll("\\.git$", "");
        JSONObject release = new JSONObject(Jobs.get("https://api.github.com/repos/" + owner + "/" + name + "/releases/latest"));
        String tag = release.getString("tag_name");
        if (tag.equals(prefs.getString("seen." + app.pkg, null))) return null;
        JSONObject asset = apkFor(release.getJSONArray("assets"));
        if (asset == null) {
            prefs.edit().putString("seen." + app.pkg, tag).apply();
            return "Release " + tag + " has no .apk";
        }
        File apk = new File(context.getCacheDir(), app.pkg + ".apk");
        Jobs.download(asset.getString("browser_download_url"), apk);
        if (!Jobs.digestMatches(asset, apk)) {
            apk.delete();
            throw new Exception("the download didn't match GitHub's checksum");
        }
        String result = keepIfNewer(context, prefs, app, apk, tag);
        prefs.edit().putString("seen." + app.pkg, tag).apply();
        return result;
    }

    /** A direct link: downloaded only when the server says the file changed (its ETag, date or size). */
    private static String fromLink(Context context, SharedPreferences prefs, App app, String link) throws Exception {
        HttpURLConnection head = (HttpURLConnection) new URL(link).openConnection();
        head.setRequestMethod("HEAD");
        head.setInstanceFollowRedirects(true);
        head.setConnectTimeout(10000);
        head.setReadTimeout(20000);
        String mark = head.getHeaderField("ETag") + "|" + head.getHeaderField("Last-Modified") + "|"
                + head.getContentLengthLong();
        head.disconnect();
        if (mark.equals(prefs.getString("seen." + app.pkg, null))) return null;
        File apk = new File(context.getCacheDir(), app.pkg + ".apk");
        Jobs.download(link, apk);
        String result = keepIfNewer(context, prefs, app, apk, null);
        prefs.edit().putString("seen." + app.pkg, mark).apply();
        return result;
    }

    /** Where a Downloader code points now, from its page on aftv.news. */
    static String downloaderLink(String code) throws Exception {
        Matcher target = CODE_TARGET.matcher(Jobs.get("https://aftv.news/" + code));
        if (!target.find()) throw new Exception("Downloader code " + code + " doesn't lead anywhere");
        return target.group(1).replace("&amp;", "&");
    }

    /** Keeps the download for installing if it's this app and newer; otherwise deletes it. */
    private static String keepIfNewer(Context context, SharedPreferences prefs, App app, File apk, String tag) {
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
        if (info == null) {
            apk.delete();
            return "The download isn't an app";
        }
        if (!app.pkg.equals(info.packageName)) {
            apk.delete();
            return "That link is for another app (" + info.packageName + ")";
        }
        if (info.getLongVersionCode() <= installedCode(context, app.pkg)) {
            apk.delete();
            return "Up to date";
        }
        String version = info.versionName != null ? info.versionName : tag;
        prefs.edit().putString("pending." + app.pkg, version).apply();
        Log.i(TAG, app.pkg + " " + version + " downloaded; installs when it isn't in use");
        return "Version " + version + " installs when it isn't in use";
    }

    /** The release's .apk for this box: one named for its processor type, else a universal one, else the only one. */
    static JSONObject apkFor(JSONArray assets) throws Exception {
        List<JSONObject> apks = new ArrayList<>();
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (asset.getString("name").toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) apks.add(asset);
        }
        for (String abi : android.os.Build.SUPPORTED_ABIS) {
            for (JSONObject apk : apks) if (apk.getString("name").contains(abi)) return apk;
        }
        for (JSONObject apk : apks) if (apk.getString("name").toLowerCase(java.util.Locale.ROOT).contains("universal")) return apk;
        String[] others = {"arm64", "armeabi", "x86", "mips"};
        List<JSONObject> plain = new ArrayList<>();
        for (JSONObject apk : apks) {
            boolean forAnother = false;
            for (String other : others) forAnother |= apk.getString("name").contains(other);
            if (!forAnother) plain.add(apk);
        }
        return plain.size() >= 1 ? plain.get(0) : null;
    }

    /**
     * Installs what's waiting, unless that app is in use: on screen (only while the box is awake: asleep, the last app
     * still counts as on screen) or playing sound. Replacing an app stops it, but nothing else on the box or the TV.
     */
    static void installPending(Context context, boolean asleep) {
        SharedPreferences prefs = prefs(context);
        String inUse = null;
        for (App app : load(context)) {
            String version = prefs.getString("pending." + app.pkg, null);
            if (version == null || app.pkg.equals(context.getPackageName())) continue;
            SelfAdb.allowNetworkWhileAsleep(context);  // pushing the APK goes through the box's ADB
            try {
                if (inUse == null) inUse = SelfAdb.shell(context, IN_USE);
            } catch (Exception e) {
                Log.i(TAG, "couldn't tell what's in use: " + e);
                return;
            }
            if (inUse(inUse, app.pkg, uid(context, app.pkg), asleep)) {
                Log.i(TAG, app.pkg + " is in use; its update waits");
                continue;
            }
            File apk = new File(context.getCacheDir(), app.pkg + ".apk");
            try {
                String result = apk.exists() ? SelfAdb.install(context, apk) : "the download is gone";
                if (!result.contains("Success") && !result.contains("Failure") && apk.exists()) continue;
                apk.delete();
                prefs.edit().remove("pending." + app.pkg).apply();
                String label = label(context, app.pkg);
                if (result.contains("Success")) {
                    setStatus(prefs, app.pkg, "Updated to " + version);
                    Jobs.note(prefs, "lastAppUpdate", label + " " + version);
                } else {
                    // Android said no: most often the update is signed by someone else than the installed app.
                    setStatus(prefs, app.pkg, "Android refused " + version + " (" + result.trim() + ")");
                }
            } catch (Exception e) {
                Log.i(TAG, app.pkg + " not installed yet: " + e);
            }
        }
    }

    /** One shell call: the app on screen, and the sound players that are playing (each with its app's uid). */
    static final String IN_USE = "dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'; "
            + "dumpsys audio | grep AudioPlaybackConfiguration | grep 'state:started'";

    /** Whether IN_USE's output shows the app on screen (only counted awake) or playing sound. */
    static boolean inUse(String report, String pkg, int uid, boolean asleep) {
        for (String line : report.split("\n")) {
            if (line.contains("ResumedActivity")) {
                if (!asleep && line.contains(" " + pkg + "/")) return true;
            } else if (uid >= 0 && line.contains("state:started") && line.contains("u/pid:" + uid + "/")) {
                return true;
            }
        }
        return false;
    }

    private static int uid(Context context, String pkg) {
        try {
            return context.getPackageManager().getApplicationInfo(pkg, 0).uid;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    /** The app's name as the box shows it, or its package. */
    static String label(Context context, String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    static long installedCode(Context context, String pkg) {
        try {
            return context.getPackageManager().getPackageInfo(pkg, 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    /** Whether this app is on the list. */
    static boolean listed(Context context, String pkg) {
        return load(context).stream().anyMatch(a -> a.pkg.equals(pkg));
    }

    static void setStatus(SharedPreferences prefs, String pkg, String status) {
        prefs.edit().putString("status." + pkg, status).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("keeper", Context.MODE_PRIVATE);
    }
}
