package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import dadb.AdbKeyPair;
import dadb.Dadb;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The box's own ADB (network debugging, 127.0.0.1:5555), so an app can do what only the system may: a Wi-Fi scan
 * while the box sleeps, a silent update, reading the HDMI port. Needs network debugging on in the box's developer
 * options, and the box asks once on its screen ("Allow USB debugging?", Always allow) for this app's key. Each app
 * has its own key (in its files), so each is allowed once. Until that's answered a connection waits for ever (dadb
 * has no timeout; with the screen off nobody can answer), so every call here gives up after a while.
 */
public final class SelfAdb {

    private static final long COMMAND_SECONDS = 15;
    private static final long INSTALL_SECONDS = 120;
    private static final ExecutorService CALLS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "self-adb");
        t.setDaemon(true);
        return t;
    });

    private SelfAdb() {
    }

    /** Runs a shell command as the system shell and returns its output; throws if ADB isn't on, isn't allowed or hangs. */
    public static String shell(Context context, String command) throws Exception {
        return within(COMMAND_SECONDS, () -> {
            try (Dadb self = open(context)) {
                return self.shell(command).getAllOutput();
            }
        });
    }

    /** Installs this APK over the installed app, silently (pm install -r), through /data/local/tmp. */
    public static String install(Context context, File apk) throws Exception {
        String remote = "/data/local/tmp/" + apk.getName();
        return within(INSTALL_SECONDS, () -> {
            try (Dadb self = open(context)) {
                self.push(apk, remote, 0644, apk.lastModified());
                return self.shell("pm install -r '" + remote + "'; rm -f '" + remote + "'").getAllOutput().trim();
            }
        });
    }

    /**
     * True if this app may use the box's ADB now. The first time, this makes the box ask on its screen ("Allow USB
     * debugging?") and waits up to 15 s for the answer, so only call it when the person asked for it (knownAllowed
     * says whether it's been allowed before, without asking).
     */
    public static boolean allowed(Context context) {
        try {
            boolean ok = shell(context, "echo ok").trim().endsWith("ok");
            context.getSharedPreferences("keeper", Context.MODE_PRIVATE).edit().putBoolean("systemAccess", ok).apply();
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    /** Whether the box has allowed this app before (set by allowed), without connecting or asking. */
    public static boolean knownAllowed(Context context) {
        return context.getSharedPreferences("keeper", Context.MODE_PRIVATE).getBoolean("systemAccess", false);
    }

    /**
     * Lets this app use the network for the next 10 minutes even if the box is in deep sleep (Doze), which otherwise
     * cuts an app off, or leaves it the 10 seconds a wake-up alarm gets: too short for a download. It ends by itself.
     * For daily jobs only: it opens the box's ADB. In deep sleep that's cut off too, so ask inside the alarm's 10 seconds,
     * at the start of the job (from a test broadcast it times out). If it can't be had, the job tries anyway.
     */
    public static void allowNetworkWhileAsleep(Context context) {
        try {
            shell(context, "cmd deviceidle tempwhitelist -d 600000 " + context.getPackageName());
        } catch (Exception e) {
            android.util.Log.w("SelfAdb", "no network exemption: " + e);
        }
    }

    private static String within(long seconds, Callable<String> call) throws Exception {
        Future<String> result = CALLS.submit(call);
        try {
            return result.get(seconds, TimeUnit.SECONDS);
        } finally {
            result.cancel(true);
        }
    }

    private static Dadb open(Context context) throws Exception {
        File priv = new File(context.getFilesDir(), "adbkey");
        File pub = new File(context.getFilesDir(), "adbkey.pub");
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub);
        return Dadb.create("127.0.0.1", 5555, AdbKeyPair.read(priv, pub));
    }
}
