package io.github.expertlizard56.streamingboxkeeper;

import android.app.Activity;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * For a remote without an Input or ★ button: the next button pressed here opens the input menu too (InputKey), next
 * to Input and ★. While it's open LinkService lets every press through to it ([listening]). The arrows, OK and Back
 * still move around it, and a button that can't be added says why.
 */
public class InputKeyActivity extends Activity {

    /** This screen is waiting for a press: LinkService takes no keys meanwhile. */
    static volatile boolean listening;

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
        listening = false;
        status.postDelayed(this::finish, 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        listening = true;
    }

    @Override
    protected void onPause() {
        listening = false;
        super.onPause();
    }
}
