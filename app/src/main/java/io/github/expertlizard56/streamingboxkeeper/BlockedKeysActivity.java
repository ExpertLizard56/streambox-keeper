package io.github.expertlizard56.streamingboxkeeper;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Disabling remote buttons (BlockedKeys): the next button pressed here is added. While it's open
 * LinkService hands every other press to it (InputKeyActivity.listening()). Each one added is a card that removes it.
 * The arrows, OK and Back still move around, and a button that can't be added says why.
 */
public class BlockedKeysActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        show(null);
    }

    private void show(String message) {
        Screen screen = new Screen(this);
        screen.start("Disable Remote Buttons",
                "Press a remote button to disable it, such as an app button for an app you don't have.");
        View waiting = screen.card("Add a button", "Press it on the remote.",
                message != null ? message : "Waiting for a button…", null);
        status = (TextView) ((LinearLayout) waiting).getChildAt(2);
        View first = null;
        for (int code : BlockedKeys.all(this)) {
            View card = screen.card(InputKey.name(code), "Disabled. Choose to turn it back on.", null, v -> {
                BlockedKeys.remove(this, code);
                show(InputKey.name(code) + " works again.");
            });
            if (first == null) first = card;
        }
        if (first != null) first.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String refusal = InputKey.refusal(keyCode);
        if (refusal == null && InputKey.is(this, keyCode)) refusal = "That's the Input button.";
        if (refusal == null) {
            if (event.getRepeatCount() == 0) {
                BlockedKeys.add(this, keyCode);
                show(InputKey.name(keyCode) + " is disabled now. Press another to disable it too.");
            }
            return true;
        }
        if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER
                && !(keyCode >= KeyEvent.KEYCODE_DPAD_UP && keyCode <= KeyEvent.KEYCODE_DPAD_RIGHT)) {
            status.setText(refusal + " Press another button.");
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        InputKeyActivity.listen(this, true);
    }

    @Override
    protected void onPause() {
        InputKeyActivity.listen(this, false);
        super.onPause();
    }
}
