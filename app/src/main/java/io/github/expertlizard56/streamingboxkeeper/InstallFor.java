package io.github.expertlizard56.streamingboxkeeper;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Installs a newer version of another app for that app, through the box's own ADB (SelfAdb), so an app can keep itself
 * up to date on a box without the system's "install unknown apps" route. Android counts an ADB install as a developer
 * install, which Google's developer verification for sideloaded apps (2026-27) leaves alone, so an app that updates
 * itself this way keeps working on boxes after that starts (Features.INSTALL_FOR_APPS).
 *
 * An app asks with an explicit broadcast of {@link #ACTION} to this receiver: the new APK as the intent's data (a
 * content: link it grants read access to) and {@link #EXTRA_REPLY}, a PendingIntent of its own. The PendingIntent says
 * who's asking (Android vouches for its creator), and the answer goes back through it with PackageInstaller's
 * EXTRA_STATUS (STATUS_SUCCESS or STATUS_FAILURE) and EXTRA_STATUS_MESSAGE, plus {@link #EXTRA_VIA} = "boxkeeper".
 *
 * Only an update of the asking app itself goes in: the APK's package must be the asker's, already installed, signed
 * with the same key, and newer. So no app can use this to install anything else, or to replace another app. An app
 * on a box that hasn't allowed this app's ADB (SelfAdb.knownAllowed) gets a failure at once and installs its own way.
 */
public final class InstallFor extends BroadcastReceiver {

    public static final String ACTION = "io.github.expertlizard56.streamingboxkeeper.INSTALL_UPDATE";
    public static final String EXTRA_REPLY = "reply";
    public static final String EXTRA_VIA = "via";

    private static final String TAG = "InstallFor";

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingIntent reply = intent.getParcelableExtra(EXTRA_REPLY);
        Uri apk = intent.getData();
        if (reply == null || apk == null) {
            Log.w(TAG, "asked without a reply or an APK");
            return;
        }
        String asker = reply.getCreatorPackage();
        PendingResult result = goAsync();
        new Thread(() -> {
            String failure;
            try {
                failure = install(context.getApplicationContext(), asker, apk);
            } catch (Exception e) {
                failure = e.toString();
            }
            answer(context, reply, failure);
            Log.i(TAG, asker + ": " + (failure == null ? "installed" : failure));
            result.finish();
        }, "install-for").start();
    }

    /** Null when it went in, else why not. */
    private static String install(Context context, String asker, Uri apk) throws Exception {
        if (!Features.on(context, Features.INSTALL_FOR_APPS)) return "switched off in Streaming Box Keeper";
        if (!SelfAdb.knownAllowed(context)) return "the box hasn't allowed Streaming Box Keeper's ADB";
        if (asker == null) return "unknown asker";
        File dir = new File(context.getCacheDir(), "install-for");
        if (!dir.isDirectory() && !dir.mkdirs()) return "no room";
        File file = new File(dir, asker + ".apk");
        try {
            try (InputStream in = context.getContentResolver().openInputStream(apk);
                 OutputStream out = new FileOutputStream(file)) {
                if (in == null) return "couldn't read the APK";
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
            String refusal = refusal(context.getPackageManager(), asker, file);
            if (refusal != null) return refusal;
            String out = SelfAdb.install(context, file);
            return out.contains("Success") ? null : "pm install: " + out;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /** Why this APK may not go in for this asker, or null: same package, installed, same key, newer. */
    static String refusal(PackageManager pm, String asker, File file) {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo update = pm.getPackageArchiveInfo(file.getPath(), flags);
        if (update == null) return "not an APK";
        if (!asker.equals(update.packageName)) return "an app may only update itself (" + update.packageName + ")";
        PackageInfo installed;
        try {
            installed = pm.getPackageInfo(asker, flags);
        } catch (PackageManager.NameNotFoundException e) {
            return "not installed";
        }
        if (version(update) <= version(installed)) return "not newer";
        if (!Arrays.equals(signers(installed), signers(update))) return "signed with a different key";
        return null;
    }

    private static long version(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signers(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        }
        return info.signatures;
    }

    private static void answer(Context context, PendingIntent reply, String failure) {
        Intent fill = new Intent()
                .putExtra(PackageInstaller.EXTRA_STATUS,
                        failure == null ? PackageInstaller.STATUS_SUCCESS : PackageInstaller.STATUS_FAILURE)
                .putExtra(EXTRA_VIA, "boxkeeper");
        if (failure != null) fill.putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, failure);
        try {
            reply.send(context, 0, fill);
        } catch (PendingIntent.CanceledException e) {
            // On success the asker was replaced, and its PendingIntent may be gone with it: nobody to tell.
        }
    }
}
