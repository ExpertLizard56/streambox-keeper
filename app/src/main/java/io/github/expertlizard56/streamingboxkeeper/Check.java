package io.github.expertlizard56.streamingboxkeeper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * Gets a sleeping box back on Wi-Fi after the Wi-Fi drops (Features.WIFI). Asleep, some boxes' Wi-Fi chips don't look
 * for the network again (the Onn 4K Pro's rtl8852bs, for one), and Android ignores an app's own scan request, so
 * after the router or an access point restarts the box stays offline until someone wakes it. The system's scan
 * request isn't ignored: every 5 minutes a wake-up alarm checks the network, and offline it asks for "cmd wifi
 * start-scan" through the box's own ADB (SelfAdb), up to a minute; Android then rejoins. The screen stays off and the
 * TV is never touched. Connected, on Wi-Fi or Ethernet, it runs the upkeep jobs (Jobs), and once a day looks for a
 * newer Streaming Box Keeper on GitHub (Updater, Features.SELF_UPDATE). With Google Assistant switched off
 * (Assistant, Features.ASSISTANT) it also re-reads both halves of that, since a system update can undo either.
 */
public class Check extends BroadcastReceiver {

    static final String TAG = "BoxKeeper";
    private static final long EVERY_MS = 5 * 60 * 1000;
    private static final long SCAN_FOR_MS = 60 * 1000;
    private static final long UPDATE_EVERY_MS = 24 * 60 * 60 * 1000;

    /** Sets the next check, replacing any already set. */
    static void schedule(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent next = PendingIntent.getBroadcast(context, 0, new Intent(context, Check.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long when = SystemClock.elapsedRealtime() + EVERY_MS;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, next);
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, next);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        schedule(context);
        run(context.getApplicationContext());
    }

    /** Checks now, in the background: rejoins if offline, and looks for an update once a day while online. */
    static void run(Context context) {
        PowerManager.WakeLock awake = context.getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "boxkeeper:check");
        awake.acquire(10 * 60 * 1000);  // a download and an install fit easily
        new Thread(() -> {
            try {
                if (!connected(context) && Features.on(context, Features.WIFI)) rejoin(context);
                if (connected(context)) {
                    // Switched on, Google Assistant and the mic's voice engine are re-checked at every wake-up, because
                    // a system update can undo either (Assistant).
                    if (Features.on(context, Features.ASSISTANT)) Assistant.keepOff(context);
                    updateDaily(context);
                    Jobs.run(context, false);
                }
            } finally {
                if (awake.isHeld()) awake.release();
            }
        }, "streaming-box-keeper").start();
    }

    private static void rejoin(Context context) {
        long until = SystemClock.elapsedRealtime() + SCAN_FOR_MS;
        long nextScan = 0;
        while (!connected(context) && SystemClock.elapsedRealtime() < until) {
            if (SystemClock.elapsedRealtime() >= nextScan) {
                Log.i(TAG, "not connected: asking the system for a Wi-Fi scan");
                try {
                    SelfAdb.shell(context, "cmd wifi start-scan");
                } catch (Exception e) {
                    Log.w(TAG, "system scan failed (network debugging on, and allowed?): " + e);
                }
                nextScan = SystemClock.elapsedRealtime() + 20_000;
            }
            SystemClock.sleep(1000);
        }
        Log.i(TAG, connected(context) ? "back on the network" : "still not connected; next try in 5 minutes");
    }

    private static void updateDaily(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("keeper", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - prefs.getLong("updateCheckedAt", 0) < UPDATE_EVERY_MS) return;
        prefs.edit().putLong("updateCheckedAt", now).apply();
        if (Edition.updateSelf(context)) return;
        if (Features.on(context, Features.SELF_UPDATE)) Updater.check(context);
    }

    /**
     * On Wi-Fi or Ethernet: a network interface (wlan or eth) that's up with an IPv4 address. Not asked of Android's
     * ConnectivityManager, which hides the network from an app in the background on a sleeping box (it said "offline"
     * on a connected box, Nov 2); the interface itself loses its address when the Wi-Fi drops.
     */
    static boolean connected(Context context) {
        try {
            for (NetworkInterface net : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String name = net.getName();
                if (!net.isUp() || net.isLoopback() || !(name.startsWith("wlan") || name.startsWith("eth"))) continue;
                for (InetAddress address : Collections.list(net.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLinkLocalAddress()) return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "network interfaces not read: " + e);
        }
        return false;
    }
}
