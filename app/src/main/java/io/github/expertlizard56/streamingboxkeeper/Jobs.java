package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * The box's upkeep, run from Check every 5 minutes while the box is online. Each job keeps when it last ran, and
 * anything that could be noticed only happens while the box sleeps. Each can be switched off on the app's screen
 * (Features).
 *  - App updates (off until switched on): once a day, the apps the person listed (AppSources: a GitHub page or a
 *    direct .apk link each), installed while the box sleeps. Android refuses one signed by anyone else.
 *  - Cleanup: once a night, 3-6 AM, asleep: close the background apps (am kill-all). After days on, a box can have
 *    hundreds of MB in swap and playback stutters; this frees most of it.
 *  - Restart: at most once a week, after that night's cleanup, if swap is still over 250 MB, asleep, and the TV isn't
 *    on (asked over HDMI-CEC). One-touch play is switched off for the restart, so the box coming back up doesn't turn
 *    the TV on, and switched back on afterwards if it was on.
 * Everything goes through SelfAdb (the box's own system), allowed once on the box.
 */
final class Jobs {

    private static final String TAG = "BoxKeeper";
    private static final int NIGHT_FROM = 3;
    private static final int NIGHT_TO = 6;
    private static final long HOUR_MS = 60 * 60 * 1000L;
    private static final long DAY_MS = 24 * HOUR_MS;
    private static final long RESTART_AFTER_MS = 7 * DAY_MS;
    private static final long SWAP_MB_BEFORE_RESTART = 250;
    private Jobs() {
    }

    /** Whatever is due now. Blocks; Check calls it in the background while the box is online. */
    static void run(Context context, boolean force) {
        SharedPreferences prefs = context.getSharedPreferences("keeper", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        boolean asleep = !context.getSystemService(PowerManager.class).isInteractive();
        boolean night = night() || force;
        restoreOneTouchPlay(context, prefs);
        if (Features.on(context, Features.APP_UPDATES)) {
            if (due(prefs, "appsCheckedAt", DAY_MS, now, force)) {
                SelfAdb.allowNetworkWhileAsleep(context);
                AppSources.findUpdates(context);
            }
            if (asleep) AppSources.installPending(context);
        }
        long tidiedAt = prefs.getLong("tidiedAt", 0);
        // A cleanup that never wrote its result isn't asked about for ever (each look opens the box's ADB).
        if (tidiedAt > prefs.getLong("swapAfterTidyAt", 0) && now - tidiedAt < HOUR_MS) readTidy(context, prefs);
        if (Features.on(context, Features.CLEANUP) && asleep && night && due(prefs, "tidiedAt", 20 * HOUR_MS, now, force)) {
            tidy(context, prefs);
        }
        if (Features.on(context, Features.RESTART) && asleep && night) restartIfNeeded(context, prefs, now, force);
        Edition.jobs(context, prefs, asleep, force);
    }

    /**
     * One job now, whatever the hour or state, for testing from a computer:
     * `adb shell am broadcast -a io.github.expertlizard56.streamingboxkeeper.RUN --es job tidy|tidy-result|apps|detect|update|restart
     * -n io.github.expertlizard56.streamingboxkeeper/.TestRun` ("restart" really restarts the box). Only the system shell may send it.
     */
    static void runOne(Context context, String job) {
        SharedPreferences prefs = context.getSharedPreferences("keeper", Context.MODE_PRIVATE);
        switch (job == null ? "" : job) {
            case "tidy": prefs.edit().putLong("tidiedAt", System.currentTimeMillis()).apply(); tidy(context, prefs); break;
            case "tidy-result": readTidy(context, prefs); break;
            case "apps": AppSources.findUpdates(context); AppSources.installPending(context); break;
            case "detect": detect(context); break;
            case "update": Updater.check(context); break;
            case "restart": restartIfNeeded(context, prefs, System.currentTimeMillis(), true); break;
            default: if (!Edition.runOne(context, job)) Log.w(TAG, "no job " + job);
        }
    }

    /**
     * The TV search as a first start would run it, without saving anything: what it finds and what it would pick, to
     * detect.txt in the app's folder (and the log). For bug reports and testing on a box that's already set up.
     */
    private static void detect(Context context) {
        SelfAdb.allowNetworkWhileAsleep(context);
        int port = TvFinder.hdmiPort(context);
        boolean onScreen = TvFinder.boxOnScreen(context);
        String hdmiName = TvFinder.tvHdmiName(context);
        java.util.List<TvFinder.Found> found = TvFinder.search(context, port, onScreen, hdmiName);
        TvFinder.Found pick = port > 0 ? TvFinder.pick(found) : null;
        StringBuilder out = new StringBuilder("HDMI port " + port + (onScreen ? ", box on screen" : ", box not on screen")
                + ", TV's HDMI name " + hdmiName);
        for (TvFinder.Found tv : found) {
            out.append("\nfound ").append(tv.kind).append(" ").append(tv.label())
                    .append(tv.showingBox ? ", showing this box" : "")
                    .append(tv.hdmiMatch == null ? "" : tv.hdmiMatch ? ", HDMI name matches" : ", HDMI name doesn't match")
                    .append(tv.notThisBox ? ", not this box's" : "");
        }
        out.append("\nwould pick: ").append(pick == null ? "nothing (the screen would ask)"
                : pick.kind + " " + pick.label() + ", input " + TvFinder.inputFor(pick.kind, port)).append("\n");
        Log.i(TAG, "detect: " + out.toString().replace("\n", "; "));
        File dir = context.getExternalFilesDir(null);
        if (dir == null) return;
        try {
            java.nio.file.Files.write(new File(dir, "detect.txt").toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "detect.txt not written: " + e);
        }
    }

    private static boolean night() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= NIGHT_FROM && hour < NIGHT_TO;
    }

    private static boolean due(SharedPreferences prefs, String key, long every, long now, boolean force) {
        if (!force && now - prefs.getLong(key, 0) < every) return false;
        prefs.edit().putLong(key, now).apply();
        return true;
    }

    // --- app updates ---

    /** GitHub's own SHA-256 of the file, when it gives one ("digest"), checked against the download. */
    static boolean digestMatches(JSONObject asset, File file) throws Exception {
        String digest = asset.optString("digest", "");
        if (!digest.startsWith("sha256:")) return true;
        return Updater.sha256(file).equalsIgnoreCase(digest.substring("sha256:".length()));
    }

    static String installedVersion(Context context, String pkg) {
        try {
            return context.getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Compares the first run of numbers in each, like 31.0 vs v30.5: negative, zero or positive. */
    static int compare(String a, String b) {
        int[] x = numbers(a);
        int[] y = numbers(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0;
            int q = i < y.length ? y[i] : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int[] numbers(String text) {
        Matcher m = Pattern.compile("\\d+(\\.\\d+)*").matcher(text == null ? "" : text);
        if (!m.find()) return new int[0];
        String[] parts = m.group().split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i]);
        return out;
    }

    // --- cleanup and restart ---

    private static final String TIDY_RESULT = "/data/local/tmp/streaming-box-keeper-tidy";

    /**
     * Closes the background apps. "am kill-all" can close this app too (it's a background app between checks), so
     * the whole job runs detached in the system shell and writes "swap before, swap after" (MB) to TIDY_RESULT;
     * readTidy picks that up at the next check, which the restart then goes by.
     */
    private static void tidy(Context context, SharedPreferences prefs) {
        String swap = "awk '/SwapTotal/{t=$2} /SwapFree/{f=$2} END{print int((t-f)/1024)}' /proc/meminfo";
        String job = "b=$(" + swap + "); am kill-all; sleep 5; a=$(" + swap + "); echo \"$b $a\" > " + TIDY_RESULT;
        try {
            SelfAdb.shell(context, "rm -f " + TIDY_RESULT + "; nohup sh -c '" + job.replace("'", "'\\''") + "' >/dev/null 2>&1 &");
            Log.i(TAG, "closing the background apps");
        } catch (Exception e) {
            Log.w(TAG, "cleanup not started: " + e);
        }
    }

    /** The last cleanup's numbers, once (then the file is removed). */
    private static void readTidy(Context context, SharedPreferences prefs) {
        try {
            String out = SelfAdb.shell(context, "cat " + TIDY_RESULT + " 2>/dev/null && rm -f " + TIDY_RESULT).trim();
            String[] parts = out.split("\\s+");
            if (parts.length != 2) return;
            long before = Long.parseLong(parts[0]);
            long after = Long.parseLong(parts[1]);
            prefs.edit().putLong("swapAfterTidy", after).putLong("swapAfterTidyAt", System.currentTimeMillis()).apply();
            note(prefs, "lastTidy", Math.max(0, before - after) + " MB freed, " + after + " MB left in swap");
            Log.i(TAG, "background apps closed: " + (before - after) + " MB back out of swap (" + after + " MB in it)");
        } catch (Exception e) {
            Log.i(TAG, "cleanup result not read: " + e.getMessage());
        }
    }

    private static void restartIfNeeded(Context context, SharedPreferences prefs, long now, boolean force) {
        boolean tidiedTonight = now - prefs.getLong("swapAfterTidyAt", 0) < 4 * HOUR_MS;
        long swap = prefs.getLong("swapAfterTidy", 0);
        long last = prefs.getLong("restartedAt", 0);
        if (!force && (!tidiedTonight || swap < SWAP_MB_BEFORE_RESTART || now - last < RESTART_AFTER_MS)) return;
        if (tvOn(context)) {
            Log.i(TAG, "restart due, but the TV is on; not tonight");
            return;
        }
        prefs.edit().putLong("restartedAt", now).commit();
        note(prefs, "lastRestart", swap + " MB in swap after the cleanup");
        Log.i(TAG, "restarting: " + swap + " MB still in swap after the cleanup");
        try {
            oneTouchPlayOffForRestart(context, prefs);
            SelfAdb.shell(context, "reboot");
        } catch (Exception e) {
            Log.w(TAG, "restart failed: " + e);
        }
    }

    /** The TV says over HDMI-CEC it's on. False if it says standby, or doesn't say. */
    private static boolean tvOn(Context context) {
        try {
            String dump = SelfAdb.shell(context, "dumpsys hdmi_control | grep 'logical_address: 0x00' | head -1");
            return dump.contains("power_status: 0");
        } catch (Exception e) {
            return false;
        }
    }

    // --- one-touch play around a restart ---

    private static final String ONE_TOUCH = "tv_wake_on_one_touch_play";

    /**
     * With one-touch play on, a box coming back from a restart turns its TV on. It's switched off for the restart and
     * remembered, and restoreOneTouchPlay switches it back on at the first check after the box is up again.
     */
    private static void oneTouchPlayOffForRestart(Context context, SharedPreferences prefs) throws Exception {
        String now = SelfAdb.shell(context, "cmd hdmi_control cec_setting get " + ONE_TOUCH);
        if (!now.contains("= 1")) return;
        SelfAdb.shell(context, "cmd hdmi_control cec_setting set " + ONE_TOUCH + " 0");
        prefs.edit().putBoolean("restoreOneTouch", true).commit();
    }

    private static void restoreOneTouchPlay(Context context, SharedPreferences prefs) {
        if (!prefs.getBoolean("restoreOneTouch", false)) return;
        // After the restart's own wake-up has passed: at least two minutes into this boot.
        if (android.os.SystemClock.elapsedRealtime() < 2 * 60 * 1000L) return;
        try {
            SelfAdb.shell(context, "cmd hdmi_control cec_setting set " + ONE_TOUCH + " 1");
            prefs.edit().remove("restoreOneTouch").apply();
        } catch (Exception e) {
            Log.w(TAG, "one-touch play not switched back on yet: " + e);
        }
    }

    static void note(SharedPreferences prefs, String key, String what) {
        String when = android.text.format.DateFormat.format("MMM d, h:mm a", System.currentTimeMillis()).toString();
        prefs.edit().putString(key, when + ": " + what).apply();
    }

    // --- network ---

    static String get(String url) throws Exception {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setConnectTimeout(10000);
        http.setReadTimeout(20000);
        http.setRequestProperty("User-Agent", "streaming-box-keeper");
        try (InputStream in = http.getInputStream()) {
            return new String(Streams.readAll(in), StandardCharsets.UTF_8);
        } finally {
            http.disconnect();
        }
    }

    static void download(String url, File to) throws Exception {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setConnectTimeout(10000);
        http.setReadTimeout(60000);
        File part = new File(to.getPath() + ".part");
        try (InputStream in = http.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
            Streams.copy(in, out);
        } finally {
            http.disconnect();
        }
        if (!part.renameTo(to)) throw new java.io.IOException("couldn't keep the download");
    }
}
