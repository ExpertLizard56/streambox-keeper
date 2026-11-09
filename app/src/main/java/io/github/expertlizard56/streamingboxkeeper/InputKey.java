package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.view.KeyEvent;

/**
 * Which remote button opens the input menu (LinkService), limited like Google TV's own settings: the Input button, the
 * ★ button (Google's one customizable button), or both (the start). The Input button includes the TV button some
 * remotes have instead. Picked on InputKeyActivity; until then both on an Onn box, Input elsewhere.
 */
final class InputKey {

    /** Input and ★ both open the menu. */
    static final int BOTH = 0;
    static final int INPUT = 1;
    static final int STAR = 2;
    private static final String PREF = "inputButton";
    /** The choice, read once; -1 until read. */
    private static volatile int choice = -1;

    private InputKey() {
    }

    static int choice(Context context) {
        int c = choice;
        if (c < 0) {
            c = context.getSharedPreferences("keeper", Context.MODE_PRIVATE).getInt(PREF, usual());
            if (c != INPUT && c != STAR && c != BOTH) c = usual();
            choice = c;
        }
        return c;
    }

    /**
     * Before the person picks: both on an Onn box (its remote's ★ has always opened the menu); elsewhere only Input, as
     * ★ and TV may mean something else on another remote.
     */
    private static int usual() {
        return LinkService.ONN ? BOTH : INPUT;
    }

    static void choose(Context context, int c) {
        context.getSharedPreferences("keeper", Context.MODE_PRIVATE).edit().putInt(PREF, c).apply();
        choice = c;
    }

    static boolean is(Context context, int keyCode) {
        int c = choice(context);
        return (c != STAR && isInput(keyCode)) || (c != INPUT && isStar(keyCode));
    }

    static boolean isInput(int keyCode) {
        // The TV button stands in for Input only on an Onn remote; on other remotes it can mean live TV.
        return keyCode == KeyEvent.KEYCODE_TV_INPUT || (LinkService.ONN && keyCode == KeyEvent.KEYCODE_TV);
    }

    // MACRO_1 (341) and FEATURED_APP_1 (288) are newer than the app's minSdk, but a constant is a number copied into the
    // app at build time, so naming them needs no newer Android on the box: nothing is looked up at runtime.
    /** The ★ button: MACRO_1 or FEATURED_APP_1, by remote version. */
    @android.annotation.SuppressLint("InlinedApi")
    static boolean isStar(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MACRO_1 || keyCode == KeyEvent.KEYCODE_FEATURED_APP_1;
    }

    static String words(int c) {
        switch (c) {
            case INPUT: return "Input";
            case STAR: return "★";
            default: return "Input or ★";
        }
    }

    /** The button or buttons that open the menu now, in words. */
    static String describe(Context context) {
        return words(choice(context));
    }
}
