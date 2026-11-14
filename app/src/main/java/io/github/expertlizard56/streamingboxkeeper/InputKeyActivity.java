package io.github.expertlizard56.streamingboxkeeper;

import android.app.Activity;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/**
 * For a remote without an Input or ★ button: the next button pressed here opens the input menu too (InputKey), next
 * to Input and ★. While it's open LinkService hands every other press to it ([listening()]). The arrows, OK and Back
 * still move around it, and a button that can't be added says why.
 */
public class InputKeyActivity extends Activity {

    /**
     * The screen waiting for a press (this one or BlockedKeysActivity), or null. LinkService hands it every button
     * that isn't the arrows, OK, Back, Home, power or volume, before the box acts on it: an Onn box opens an app
     * button's app (or its Play Store page) before the screen would see the press.
     */
    private static volatile WeakReference<Activity> listening = new WeakReference<>(null);

    static Activity listening() {
        return listening.get();
    }

    static void listen(Activity screen, boolean on) {
        if (on) listening = new WeakReference<>(screen);
        else if (listening.get() == screen) listening = new WeakReference<>(null);
    }

    private Screen screen;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen = new Screen(this);
        screen.start("Input Button", new SpannableStringBuilder().append(InputKey.label(this, screen.px(16), 0x99FFFFFF))
                .append(" opens the input menu now."));
        View waiting = screen.card("Using a different remote?",
                "Press the button on it that should open the input menu.", "Waiting for a button…", null);
        status = (TextView) ((LinearLayout) waiting).getChildAt(2);
        View usual = screen.card(new SpannableStringBuilder("Only ")
                        .append(InputKey.label(this, screen.px(22), 0xFFFFFFFF, false)),
                "Remove a button added from another remote.", null, v -> added(0));
        usual.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String refusal = InputKey.refusal(keyCode);
        if (refusal == null) {
            if (event.getRepeatCount() == 0) added(InputKey.isUsual(keyCode) ? 0 : keyCode);
            return true;
        }
        if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER
                && !(keyCode >= KeyEvent.KEYCODE_DPAD_UP && keyCode <= KeyEvent.KEYCODE_DPAD_RIGHT)) {
            status.setText(refusal + " Press another button.");
        }
        return super.onKeyDown(keyCode, event);
    }

    private void added(int keyCode) {
        InputKey.setExtra(this, keyCode);
        status.setText(new SpannableStringBuilder("Done: ").append(InputKey.label(this, screen.px(15), 0xFF8AB4F8))
                .append(" opens the input menu."));
        listen(this, false);
        status.postDelayed(this::finish, 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        listen(this, true);
    }

    @Override
    protected void onPause() {
        listen(this, false);
        super.onPause();
    }
}
