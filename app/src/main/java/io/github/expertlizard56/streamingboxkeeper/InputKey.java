package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.view.KeyEvent;

/**
 * Which remote button opens the input menu (LinkService). Until the person picks one (InputKeyActivity), the usual
 * ones: Input, and on an Onn box also its remote's TV and star buttons. A picked button replaces those. Buttons the
 * box can't do without (arrows, OK, Back, Home, power, volume) can't be picked.
 */
final class InputKey {

    /** No button picked: the usual ones. */
    static final int USUAL = 0;
    private static final String PREF = "inputKey";
    /** The picked button, read once; -1 until read. */
    private static volatile int picked = -1;

    private InputKey() {
    }

    static int picked(Context context) {
        int k = picked;
        if (k < 0) {
            k = context.getSharedPreferences("keeper", Context.MODE_PRIVATE).getInt(PREF, USUAL);
            picked = k;
        }
        return k;
    }

    static void pick(Context context, int keyCode) {
        context.getSharedPreferences("keeper", Context.MODE_PRIVATE).edit().putInt(PREF, keyCode).apply();
        picked = keyCode;
    }

    static boolean is(Context context, int keyCode) {
        int k = picked(context);
        return k > USUAL ? keyCode == k : isUsual(keyCode);
    }

    // MACRO_1 (341) and FEATURED_APP_1 (288) are newer than the app's minSdk, but a constant is a number copied into the
    // app at build time, so naming them needs no newer Android on the box: nothing is looked up at runtime.
    @android.annotation.SuppressLint("InlinedApi")
    static boolean isUsual(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_TV_INPUT) return true;
        // On an Onn box, also its remote's TV button and star button (MACRO_1 or FEATURED_APP_1, by remote version);
        // on other boxes those can mean something else, so they're left alone.
        return LinkService.ONN && (keyCode == KeyEvent.KEYCODE_TV || keyCode == KeyEvent.KEYCODE_MACRO_1
                || keyCode == KeyEvent.KEYCODE_FEATURED_APP_1);
    }

    /** Why this button can't be the Input button, or null if it can. */
    static String refusal(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_HOME:
                return "The arrows, OK, Back and Home are needed to use the box.";
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_TV_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return "Power and volume work the TV.";
            default:
                return null;
        }
    }

    /** The button in words. */
    static String name(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_TV_INPUT) return "Input";
        if (keyCode == KeyEvent.KEYCODE_TV) return "TV";
        if (keyCode == 341 || keyCode == 288) return "★ (star)";  // MACRO_1, FEATURED_APP_1
        String s = KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "").replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return InputPanelActivity.capitalized(s);
    }

    /** The button or buttons that open the menu now, in words. */
    static String describe(Context context) {
        int k = picked(context);
        if (k > USUAL) return name(k);
        return LinkService.ONN ? "Input, TV or ★ (the usual ones)" : "Input (the usual one)";
    }
}
