package io.github.expertlizard56.streamingboxkeeper;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Makes the box's remote drive the TV it is plugged into, without a mode button. The TV is asked twice a second what
 * it shows. While it shows this box, every key works as usual. While it shows anything else (its home screen, its
 * apps, antenna TV, another input), the arrows, OK, Back and play controls go to the TV (Tv.handles), and any other
 * button (Home, YouTube, Settings...) works on the box as usual and switches the TV back to the box. The Input button
 * (InputKey: Input, or TV/star on an Onn, or the one the person picked) opens InputPanelActivity over the box's picture. Volume,
 * mute and power are left alone: the remote sends those to the TV by infrared. When the TV comes on (off to on, never
 * a power-off) to its own home screen, it is sent to the box. If the TV can't be reached, or is off, nothing changes.
 */
public class LinkService extends AccessibilityService {

    /** The TV, shared with the input panel; null until the service starts. */
    static volatile Tv tv;

    private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(1);
    /** What the TV shows (Tv.showing), or null if unknown. */
    private volatile String tvShowing;
    private volatile long settleUntil;
    /** After the TV comes on, this long during which its home screen is sent on to the box. */
    private static final long WAKE_WINDOW_MS = 4000;
    /** Whether the TV was on at the last check (null: not known yet), and when it last came on. */
    private Boolean wasOn;
    private long wokeAt;
    /** Keys whose press went to the TV, so their release goes there too even if the TV changed in between. */
    private final Set<Integer> forwarded = new HashSet<>();
    /** Keys whose press was ignored (Back on an input), so their release is ignored too. */
    private final Set<Integer> ignored = new HashSet<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (tv == null) tv = Tv.create(getApplicationContext());
        timer.scheduleWithFixedDelay(this::checkTv, 0, 500, TimeUnit.MILLISECONDS);
        // Nothing set up yet: find the TV alone if that's clear (TvFinder), else the setup screen asks.
        if (tv == null) timer.execute(() -> {
            if (TvFinder.setUpAlone(getApplicationContext())) reload(getApplicationContext());
        });
        timer.scheduleWithFixedDelay(() -> keepTvFound(getApplicationContext()), 2, FIND_EVERY_MIN, TimeUnit.MINUTES);
        // A button-remapping app switched on or off: the home screen's own button helpers step aside or come back.
        timer.scheduleWithFixedDelay(() -> RemapApps.check(getApplicationContext()), 10, 60, TimeUnit.SECONDS);
        running = true;
    }

    /** Switches to the TV now set up (TvConfig), after the setup screen or TvFinder saved one. */
    static void reload(Context context) {
        Tv old = tv;
        tv = Tv.create(context.getApplicationContext());
        if (old != null) old.close();
    }

    private static final long FIND_EVERY_MIN = 15;
    private static volatile boolean running;

    /**
     * Learns what identifies the saved TV (once), and follows it to a new address when it hasn't answered for a while
     * and is found elsewhere on the network (the router gave it another address). An off TV is just looked for again.
     */
    private static void keepTvFound(Context context) {
        Tv now = tv;
        TvConfig config = TvConfig.load(context);
        if (config == null) {
            // Not set up yet: the TV may have been off at the first look (an off TV isn't found), so look again.
            if (TvFinder.setUpAlone(context)) reload(context);
            return;
        }
        if (now == null) return;
        boolean answering = System.currentTimeMillis() - now.lastAnswered() < FIND_EVERY_MIN * 60_000;
        if (config.id == null) {
            if (!answering) return;
            String id = TvFinder.idOf(context, config);
            if (id != null) config.withAddress(config.address, id).save(context);
            return;
        }
        if (answering) return;
        String moved = TvFinder.movedTo(context, config);
        if (moved == null) return;
        config.withAddress(moved, config.id).save(context);
        android.util.Log.i("TvFinder", config.kind + " TV moved from " + config.address + " to " + moved);
        reload(context);
    }

    /** The remote's state in a line: which TV, whether it answers, and a setting that stops it working. */
    static String describe(Context context) {
        TvConfig config = TvConfig.load(context);
        if (config == null) return "not set up yet (open Streaming Box Keeper on this box to pick the TV)";
        String which = ("fire".equals(config.kind) ? "Fire TV" : "Roku TV") + " at " + config.address;
        Tv now = tv;
        if (!running || now == null) return which + ", but the remote part is off (its accessibility service isn't on)";
        String problem = now.problem();
        if (problem != null) return which + ": " + problem;
        long answered = now.lastAnswered();
        if (answered == 0) return which + ", not answering yet";
        if (System.currentTimeMillis() - answered < 120_000) return which + ", answering";
        return which + ", not answering since "
                + android.text.format.DateFormat.format("MMM d, h:mm a", answered) + " (off at the wall?)";
    }

    /** A setting that stops the remote working (null if none); the status file reports it as a problem. */
    static String problem(Context context) {
        if (TvConfig.load(context) == null) return null;
        Tv now = tv;
        if (!running || now == null) return "the TV remote part is off: turn Streaming Box Keeper on in Settings > Accessibility";
        return now.problem();
    }

    private void checkTv() {
        if (tv == null) return;
        long now = System.currentTimeMillis();
        Boolean on = tv.isOn();
        if (Boolean.TRUE.equals(on) && Boolean.FALSE.equals(wasOn)) wokeAt = now;
        if (on != null) wasOn = on;
        String showing = tv.showing();
        // The TV just came on to its own home screen: send it to the box, and again if the home screen takes over.
        if (now - wokeAt < WAKE_WINDOW_MS && Tv.HOME.equals(showing)) {
            switchToBox();
            return;
        }
        // Just sent the TV to the box: an answer from before it got there would hand the keys back to the TV.
        if (now < settleUntil) return;
        tvShowing = showing;
    }

    /** The TV is on and showing something other than this box. */
    private boolean tvElsewhere() {
        String s = tvShowing;
        return s != null && !Tv.BOX.equals(s);
    }

    private static boolean isTvOwnKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_TV_POWER:
                return true;
            default:
                return false;
        }
    }

    /** Running on an Onn box (Walmart's), whose remote has its own TV and star buttons. */
    static final boolean ONN = "onn".equalsIgnoreCase(android.os.Build.BRAND);

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean ready = tv != null && Features.on(this, Features.REMOTE);
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        // The Input button comes first, even before this build's own keys, and is taken so nothing else on the box acts on it.
        if (ready && InputKey.is(this, code)) {
            if (down && event.getRepeatCount() == 0) inputKey();
            return true;
        }
        if (Edition.takesKey(code)) return true;
        if (!ready) return false;
        if (!down) {
            if (ignored.remove(code)) return true;
            if (!forwarded.remove(code)) return false;
            tv.key(code, false);
            // OK or Back may have opened an input or app: look again soon instead of waiting for the next check.
            timer.schedule(this::checkTv, 400, TimeUnit.MILLISECONDS);
            return true;
        }
        if (forwarded.contains(code)) return true; // the box's own repeat of a key the TV repeats itself
        if (!tvElsewhere()) return false;
        if (code == KeyEvent.KEYCODE_BACK && !Tv.HOME.equals(tvShowing)) {
            // On an input (the PC, antenna TV...) the TV takes Back as "go to my home screen": unwanted
            // (Nov 8). Back still goes to the TV on its own home screen and in its apps.
            ignored.add(code);
            return true;
        }
        if (ignored.contains(code)) return true;
        if (tv.handles(code)) {
            forwarded.add(code);
            tv.key(code, true);
            return true;
        }
        if (!isTvOwnKey(code) && event.getRepeatCount() == 0) {
            // Home, YouTube, Settings...: the box answers as usual, and the TV comes back to it.
            switchToBox();
        }
        return false;
    }

    private void switchToBox() {
        tvShowing = Tv.BOX;
        settleUntil = System.currentTimeMillis() + 2000;
        tv.switchTo(Tv.BOX);
    }

    /**
     * Opens the full-screen input menu, or closes it if it's open. The box can only draw over its own input, so from
     * another input or the TV's home screen the TV first comes back to the box (until Nov 8 the
     * button stepped to the next input there).
     */
    private void inputKey() {
        InputPanelActivity open = InputPanelActivity.shown();
        if (open != null) {
            open.dismiss();
            return;
        }
        Intent menu = new Intent(this, InputPanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tvElsewhere()) {
            menu.putExtra("from", tvShowing);  // marked "On now" in the menu, and where Back takes the TV
            switchToBox();
        }
        startActivity(menu);
        // A button-remapping app gets every press too (Android hands keys to all such apps at once), so one mapped to
        // this button may act on it as well and push the menu away: then the menu comes back. The Input button stays
        // this app's.
        if (RemapApps.on(this)) {
            long opened = System.currentTimeMillis();
            timer.schedule(() -> {
                if (InputPanelActivity.shown() == null && InputPanelActivity.closedAt < opened) startActivity(menu);
            }, 900, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        timer.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
