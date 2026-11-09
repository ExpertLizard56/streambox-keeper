package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.view.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Roku TV, through its control port (ECP, http on port 8060; the TV's Settings > System > Advanced system settings >
 * Control by mobile apps > Network access must be Enabled). Answers are read to the end and connections left open, so
 * the next request reuses one instead of opening a new connection each time. ECP has presses but no holds, so a held
 * arrow is repeated here.
 */
final class Roku implements Tv {

    /** A held arrow starts repeating after this long, then moves once per REPEAT_MS, like the TV's own remote. */
    private static final long REPEAT_DELAY_MS = 350;
    private static final long REPEAT_MS = 110;
    /** A repeat that never sees its key released (a lost key-up) stops by itself. */
    private static final long REPEAT_LIMIT_MS = 20000;
    private static final Pattern APP_ID = Pattern.compile("<app id=\"([^\"]+)\"");
    private static final Pattern INPUT = Pattern.compile("<app id=\"(tvinput\\.[^\"]+)\"[^>]*>([^<]*)</app>");

    /** One queue for every command, so presses reach the TV in the order they were made. */
    private static final ExecutorService SENDER = Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService REPEATER = Executors.newSingleThreadScheduledExecutor();

    private final String base;
    private final String boxInput;
    private long lastPowerCheck;
    private Boolean tvOn;
    /** Its own lock: isOn waits on the network, and key presses (startRepeat, stopRepeat) mustn't wait with it. */
    private final Object powerLock = new Object();
    private ScheduledFuture<?> repeat;
    private long repeatStarted;
    /** One press on its way to the TV at a time: a slow answer drops repeats instead of queueing them up. */
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile long answered;
    /** The TV answered a key press with 403, or its device-info says "limited": Control by mobile apps isn't Enabled. */
    private volatile boolean limited;

    Roku(TvConfig config) {
        base = "http://" + config.address + ":8060/";
        boxInput = config.input;
    }

    @Override
    public String showing() {
        String app = get("query/active-app");
        if (app == null || !Boolean.TRUE.equals(isOn())) return null;
        Matcher m = APP_ID.matcher(app);
        String id = m.find() ? m.group(1) : null;
        if (boxInput.equals(id)) return BOX;
        return id != null && id.startsWith("tvinput.") ? id : HOME;
    }

    /** In standby the TV still answers ECP, so its power mode is asked, at most once a second. */
    @Override
    public Boolean isOn() {
        synchronized (powerLock) {
            long now = System.currentTimeMillis();
            if (now - lastPowerCheck >= 1000) {
                String info = get("query/device-info");
                tvOn = info == null ? null : info.contains("<power-mode>PowerOn</power-mode>");
                if (info != null && info.contains("<ecp-setting-mode>limited</ecp-setting-mode>")) limited = true;
                lastPowerCheck = now;
            }
            return tvOn;
        }
    }

    /** The TV's name for an arrow, OK, Back or play key, or null for every other key. */
    private static String rokuKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return "Up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "Down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "Left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "Right";
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: return "Select";
            case KeyEvent.KEYCODE_BACK: return "Back";
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE: return "Play";
            case KeyEvent.KEYCODE_MEDIA_REWIND: return "Rev";
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return "Fwd";
            case KeyEvent.KEYCODE_CHANNEL_UP: return "ChannelUp";
            case KeyEvent.KEYCODE_CHANNEL_DOWN: return "ChannelDown";
            case KeyEvent.KEYCODE_INFO: return "Info";
            default: return null;
        }
    }

    private static boolean repeats(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean handles(int keyCode) {
        return rokuKey(keyCode) != null;
    }

    @Override
    public void key(int keyCode, boolean down) {
        String key = rokuKey(keyCode);
        if (!down) {
            stopRepeat();
            return;
        }
        press(key, false);
        if (repeats(keyCode)) startRepeat(key);
    }

    // A step every REPEAT_MS, counted from the end of the last one: a press only queues the key, so the gaps are the same,
    // and a process that sat cached never catches up in a burst of repeats.
    private synchronized void startRepeat(String key) {
        stopRepeat();
        repeatStarted = System.currentTimeMillis();
        repeat = REPEATER.scheduleWithFixedDelay(() -> {
            if (System.currentTimeMillis() - repeatStarted > REPEAT_LIMIT_MS) {
                stopRepeat();
                return;
            }
            press(key, true);
        }, REPEAT_DELAY_MS, REPEAT_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopRepeat() {
        if (repeat != null) repeat.cancel(false);
        repeat = null;
    }

    /** Every tap is sent; a repeat is skipped while the last press is still on its way. */
    private void press(String key, boolean isRepeat) {
        if (!busy.compareAndSet(false, true) && isRepeat) return;
        busy.set(true);
        SENDER.execute(() -> {
            try {
                post("keypress/" + key);
            } finally {
                busy.set(false);
            }
        });
    }

    @Override
    public List<String[]> inputs() {
        List<String[]> items = new ArrayList<>();
        String apps = get("query/apps");
        if (apps != null) {
            Matcher m = INPUT.matcher(apps);
            while (m.find()) items.add(new String[] {boxInput.equals(m.group(1)) ? BOX : m.group(1), m.group(2)});
        }
        items.add(new String[] {HOME, "Roku Home"});
        return items;
    }

    @Override
    public void switchTo(String id) {
        String path = BOX.equals(id) ? "launch/" + boxInput : HOME.equals(id) ? "keypress/Home" : "launch/" + id;
        SENDER.execute(() -> post(path));
    }

    private void post(String path) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
            c.setConnectTimeout(800);
            c.setReadTimeout(800);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.getOutputStream().close();
            int code = c.getResponseCode();
            if (code == 200) {
                read(c.getInputStream());
                answered = System.currentTimeMillis();
                limited = false;
            } else if (code == 403) {
                limited = true;
            }
        } catch (Exception ignored) {
        }
    }

    /** The answer to a query, or null if the TV can't be reached. Blocks: never on the main thread. */
    private String get(String path) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
            c.setConnectTimeout(800);
            c.setReadTimeout(800);
            int code = c.getResponseCode();
            if (code == 403) limited = true;
            if (code != 200) return null;
            answered = System.currentTimeMillis();
            return read(c.getInputStream());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public long lastAnswered() {
        return answered;
    }

    @Override
    public String problem() {
        return limited ? "the Roku TV doesn't take the box's key presses: on the TV, Settings > System > Advanced system"
                + " settings > Control by mobile apps > Network access: set it to Enabled" : null;
    }

    private static String read(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
