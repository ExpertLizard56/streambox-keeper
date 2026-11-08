package io.github.expertlizard56.boxkeeper;

import android.content.Context;
import android.view.KeyEvent;
import dadb.AdbKeyPair;
import dadb.AdbStream;
import dadb.Dadb;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okio.BufferedSink;
import okio.BufferedSource;

/**
 * An Amazon Fire TV smart TV, through its network debugging (ADB on port 5555). The first connection asks on the TV
 * to allow this box; answer Always allow. A small loop on the TV reports what it shows whenever that changes. Keys go
 * into the TV's virtual keyboard as real presses and releases, so a held arrow repeats on the TV like its own remote.
 * Inputs switch with the TV's own HDMI keys, pressed by FireKeys: a small program the box copies to the TV and keeps
 * running for the connection (box-keeper/firekeys), as the TV's own `input keyevent` takes ~0.9 s to start for
 * every key (Nov 8: box from the TV's home screen in 1.5 s, most of it that start). If it isn't running, `input
 * keyevent` still does it.
 */
final class FireTv implements Tv {

    /**
     * Prints "S|<wakefulness>|<HDMI hardware shown, or empty>|<resumed activity>" when any of them changes, and "P"
     * every 4 seconds so a connection that died without closing (the box's Wi-Fi dropped) shows up as silence.
     */
    private static final String WATCH = "l=; n=0; while :; do"
            + " n=$((n+1)); [ $n -ge 10 ] && echo P && n=0;"
            + " w=$(dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*');"
            + " a=$(dumpsys activity activities | grep -m1 mResumedActivity);"
            + " h=; case \"$a\" in *PassthroughPlayer*) h=$(dumpsys tv_input"
            + " | grep -o 'HDMIInputService/HW[0-9]*, pkg=[^}]*}, mCallback: android' | head -1 | grep -o 'HW[0-9]*');; esac;"
            + " s=\"$w|$h|$a\"; [ \"$s\" != \"$l\" ] && echo \"S|$s\" && l=$s; sleep 0.4; done";
    private static final Pattern HDMI_STATE = Pattern.compile("HDMIInputService/(HW[0-9]+): info: .*?; state: ([0-9])");

    private final String host;
    private final String boxInput;
    private final File keyDir;
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private volatile Dadb adb;
    private volatile BufferedSink keys;
    /** FireKeys' stdin while it runs on the TV: one Android key code a line. Null while it isn't running. */
    private volatile BufferedSink fastKeys;
    private static final String FAST_KEYS_ON_TV = "/data/local/tmp/boxkeeper-keys.dex";
    private final File fastKeysHere;
    private volatile String keyDevice;
    /** Last report from the TV, or null while there is no connection. */
    private volatile String state;
    /** When the TV was last heard from (or a connection attempt began). */
    private volatile long heard;
    private Thread watcher;
    private volatile long answered;
    private volatile boolean closed;

    FireTv(Context context, TvConfig config) {
        host = config.address;
        boxInput = config.input;
        keyDir = context.getFilesDir();
        fastKeysHere = new File(keyDir, "firekeys.dex");
        try (java.io.InputStream in = context.getAssets().open("firekeys.dex");
                java.io.FileOutputStream out = new java.io.FileOutputStream(fastKeysHere)) {
            Streams.copy(in, out);
        } catch (Exception ignored) {
            // Without it the TV's own input keyevent switches inputs, just slower.
        }
        watcher = new Thread(this::watch, "fire-tv");
        watcher.setDaemon(true);
        watcher.start();
        Thread watchdog = new Thread(this::watchdog, "fire-tv-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    /** Stays connected for good: on any failure it waits a little and connects again. */
    private void watch() {
        while (!closed) {
            try {
                heard = System.currentTimeMillis();
                connect();
                AdbStream stream = adb.open("shell:" + WATCH);
                BufferedSource out = stream.getSource();
                for (String line; (line = out.readUtf8Line()) != null; ) {
                    heard = System.currentTimeMillis();
                    answered = heard;
                    if (line.startsWith("S|")) state = line;
                }
            } catch (Exception ignored) {
            }
            disconnect();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
                // A late interrupt from the watchdog: just connect again.
            }
        }
    }

    /**
     * A read on a connection that died without closing never returns. After 15 seconds without a word from the TV, the
     * connection is closed and watch() interrupted (closing alone can leave it waiting on the echo thread's read), and
     * watch() connects again.
     */
    private void watchdog() {
        while (!closed) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
            Dadb current = adb;
            if (current != null && System.currentTimeMillis() - heard > 15000) {
                state = null;
                try {
                    current.close();
                } catch (Exception ignored) {
                }
                watcher.interrupt();
            }
        }
    }

    private synchronized void connect() throws Exception {
        File priv = new File(keyDir, "adbkey");
        File pub = new File(keyDir, "adbkey.pub");
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub);
        adb = Dadb.create(host, 5555, AdbKeyPair.read(priv, pub));
        keyDevice = run("for d in /dev/input/event*; do getevent -p $d 2>/dev/null | grep -q amzkeyboard && echo $d; done")
                .trim();
        AdbStream shell = adb.open("shell:");
        keys = shell.getSink();
        BufferedSource echo = shell.getSource();
        Thread drain = new Thread(() -> {
            try {
                while (echo.read(new okio.Buffer(), 8192) != -1) { }
            } catch (Exception ignored) {
            }
        }, "fire-tv-echo");
        drain.setDaemon(true);
        drain.start();
        startFastKeys();
    }

    /** Copies FireKeys to the TV and starts it; if that fails, inputs switch the slower way. */
    private void startFastKeys() {
        try {
            adb.push(fastKeysHere, FAST_KEYS_ON_TV, 0644, fastKeysHere.lastModified());
            AdbStream stream = adb.open("shell:CLASSPATH=" + FAST_KEYS_ON_TV + " exec app_process / FireKeys");
            BufferedSource out = stream.getSource();
            String first = out.readUtf8Line();
            if (!"ready".equals(first)) return;
            fastKeys = stream.getSink();
            Thread drain = new Thread(() -> {
                try {
                    while (out.read(new okio.Buffer(), 8192) != -1) { }
                } catch (Exception ignored) {
                }
                fastKeys = null;
            }, "fire-tv-keys");
            drain.setDaemon(true);
            drain.start();
        } catch (Exception e) {
            fastKeys = null;
        }
    }

    private synchronized void disconnect() {
        state = null;
        keys = null;
        fastKeys = null;
        try {
            if (adb != null) adb.close();
        } catch (Exception ignored) {
        }
        adb = null;
    }

    @Override
    public long lastAnswered() {
        return answered;
    }

    @Override
    public void close() {
        closed = true;
        disconnect();
        watcher.interrupt();
    }

    /** A one-off command's output. */
    private String run(String command) throws Exception {
        try (AdbStream stream = adb.open("shell:" + command)) {
            return stream.getSource().readUtf8();
        }
    }

    /** Awake or Dreaming (its screensaver) is on; Asleep or Dozing is standby. */
    @Override
    public Boolean isOn() {
        String s = state;
        if (s == null) return null;
        String[] parts = s.split("\\|", 4);
        if (parts.length < 4) return null;
        return parts[1].endsWith("=Awake") || parts[1].endsWith("=Dreaming");
    }

    @Override
    public String showing() {
        String s = state;
        if (s == null) return null;
        String[] parts = s.split("\\|", 4);
        if (parts.length < 4 || !parts[1].endsWith("=Awake")) return null;
        if (parts[2].isEmpty()) return HOME;
        return parts[2].equals(boxInput) ? BOX : parts[2];
    }

    /** The Linux key the TV's virtual keyboard maps to the same Android key, or 0. */
    private static int linuxKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return 103;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 108;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 105;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 106;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: return 353;
            case KeyEvent.KEYCODE_BACK: return 158;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE: return 164;
            case KeyEvent.KEYCODE_MEDIA_REWIND: return 168;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return 208;
            case KeyEvent.KEYCODE_MENU: return 139;
            // The remote's channel buttons: antenna TV on the Fire TV itself (amzkeyboard has both, Nov 8).
            case KeyEvent.KEYCODE_CHANNEL_UP: return 402;
            case KeyEvent.KEYCODE_CHANNEL_DOWN: return 403;
            default: return 0;
        }
    }

    @Override
    public boolean handles(int keyCode) {
        return linuxKey(keyCode) != 0;
    }

    @Override
    public void key(int keyCode, boolean down) {
        sendKey(linuxKey(keyCode), down);
    }

    private void sendKey(int code, boolean down) {
        String dev = keyDevice;
        if (code == 0 || dev == null || dev.isEmpty()) return;
        type("sendevent " + dev + " 1 " + code + (down ? " 1" : " 0") + "; sendevent " + dev + " 0 0 0");
    }

    private void type(String line) {
        sender.execute(() -> {
            BufferedSink sink = keys;
            if (sink == null) return;
            try {
                sink.writeUtf8(line + "\n");
                sink.flush();
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public List<String[]> inputs() {
        List<String[]> items = new ArrayList<>();
        try {
            if (adb != null) {
                // Connected HDMI inputs (state 0), in port order; the box's always.
                Matcher m = HDMI_STATE.matcher(run("dumpsys tv_input | grep HDMIInputService/HW"));
                List<String> hw = new ArrayList<>();
                while (m.find()) {
                    if ((m.group(2).equals("0") || m.group(1).equals(boxInput)) && !hw.contains(m.group(1))) {
                        hw.add(m.group(1));
                    }
                }
                hw.sort((a, b) -> Integer.parseInt(a.substring(2)) - Integer.parseInt(b.substring(2)));
                for (String id : hw) {
                    items.add(new String[] {id.equals(boxInput) ? BOX : id, id.equals(boxInput) ? "This box" : "HDMI " + port(id)});
                }
            }
        } catch (Exception ignored) {
        }
        if (items.isEmpty()) items.add(new String[] {BOX, "This box"});
        items.add(new String[] {HOME, "Fire TV Home"});
        return items;
    }

    /** HW4 is HDMI 1 on this TV. */
    private static int port(String hw) {
        return Integer.parseInt(hw.substring(2)) - 3;
    }

    @Override
    public void switchTo(String id) {
        if (HOME.equals(id)) {
            sendKey(172, true);
            sendKey(172, false);
            return;
        }
        String hw = BOX.equals(id) ? boxInput : id;
        // KEYCODE_TV_INPUT_HDMI_1 is 243.
        int code = 242 + port(hw);
        sender.execute(() -> {
            BufferedSink fast = fastKeys;
            try {
                if (fast != null) {
                    fast.writeUtf8(code + "\n");
                    fast.flush();
                    return;
                }
            } catch (Exception e) {
                fastKeys = null;
            }
            BufferedSink sink = keys;
            if (sink == null) return;
            try {
                sink.writeUtf8("input keyevent " + code + "\n");
                sink.flush();
            } catch (Exception ignored) {
            }
        });
    }
}
