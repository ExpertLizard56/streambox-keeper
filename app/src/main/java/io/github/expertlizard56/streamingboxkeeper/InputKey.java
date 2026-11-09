package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.KeyEvent;

/**
 * Which remote button opens the input menu (LinkService). The Input button (the screen-with-an-arrow symbol) and the ★
 * button are the same button on different remotes, so both always do (and an Onn remote's TV button). Someone with
 * another remote can add its button (InputKeyActivity: press it); arrows, OK, Back, Home, power and volume can't be.
 */
final class InputKey {

    private static final String PREF = "inputExtra";
    /** The added button, read once; -1 until read, 0 for none. */
    private static volatile int extra = -1;

    private InputKey() {
    }

    static int extra(Context context) {
        int k = extra;
        if (k < 0) {
            k = context.getSharedPreferences("keeper", Context.MODE_PRIVATE).getInt(PREF, 0);
            extra = k;
        }
        return k;
    }

    /** Adds another remote's button (0: none). */
    static void setExtra(Context context, int keyCode) {
        context.getSharedPreferences("keeper", Context.MODE_PRIVATE).edit().putInt(PREF, keyCode).apply();
        extra = keyCode;
    }

    static boolean is(Context context, int keyCode) {
        if (isUsual(keyCode)) return true;
        int k = extra(context);
        return k > 0 && keyCode == k;
    }

    // MACRO_1 (341) and FEATURED_APP_1 (288) are newer than the app's minSdk, but a constant is a number copied into the
    // app at build time, so naming them needs no newer Android on the box: nothing is looked up at runtime.
    /** Input, ★ (MACRO_1 or FEATURED_APP_1, by remote version), or an Onn remote's TV button. */
    @android.annotation.SuppressLint("InlinedApi")
    static boolean isUsual(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_TV_INPUT || keyCode == KeyEvent.KEYCODE_MACRO_1
                || keyCode == KeyEvent.KEYCODE_FEATURED_APP_1
                // The TV button stands in for Input only on an Onn remote; on other remotes it can mean live TV.
                || (LinkService.ONN && keyCode == KeyEvent.KEYCODE_TV);
    }

    /** Why this button can't be added, or null if it can. */
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

    /** A button in words, for one that was added. */
    static String name(int keyCode) {
        String s = KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "").replace('_', ' ')
                .toLowerCase(java.util.Locale.ROOT);
        return InputPanelActivity.capitalized(s);
    }

    /** "[Input symbol] or ★", plus the added button, drawn at this text size and color. */
    static CharSequence label(Context context, float textPx, int color) {
        return label(context, textPx, color, true);
    }

    static CharSequence label(Context context, float textPx, int color, boolean withExtra) {
        SpannableStringBuilder s = new SpannableStringBuilder();
        Drawable icon = context.getDrawable(R.drawable.input_button);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(color);
            int size = Math.round(textPx * 1.1f);
            icon.setBounds(0, 0, size, size);
            s.append("￼", new ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            s.append("Input");
        }
        s.append(" or ★");
        int k = withExtra ? extra(context) : 0;
        if (k > 0) s.append(" or ").append(name(k));
        return s;
    }
}
