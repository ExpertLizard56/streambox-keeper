package io.github.expertlizard56.streamingboxkeeper;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Picking the Input button (InputKey): the next remote button pressed on this screen opens the input menu from now on.
 * While it's open, LinkService lets every press through to it ([listening]). The arrows, OK and Back still move
 * around it, and a button that can't be picked says why.
 */
public class InputKeyActivity extends Activity {

    /** This screen is waiting for a press: LinkService takes no keys meanwhile. */
    static volatile boolean listening;

    private TextView said;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(96), dp(48), dp(96), dp(48));
        page.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF161B22, 0xFF0B0E12}));
        page.addView(text("Press the button that should open the input menu", 30, Color.WHITE));
        page.addView(text("Now: " + InputKey.describe(this), 18, 0x99FFFFFF));
        said = text("", 20, 0xFF8AB4F8);
        page.addView(said);
        TextView usual = text("Use the usual " + (LinkService.ONN ? "ones (Input, TV, ★)" : "one (Input)"), 18, Color.WHITE);
        usual.setPadding(dp(24), dp(12), dp(24), dp(12));
        usual.setFocusable(true);
        usual.setBackground(buttonBackground());
        usual.setOnClickListener(v -> picked(InputKey.USUAL));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        size.topMargin = dp(28);
        page.addView(usual, size);
        setContentView(page);
        usual.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String refusal = InputKey.refusal(keyCode);
        if (refusal == null) {
            if (event.getRepeatCount() == 0) picked(keyCode);
            return true;
        }
        if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER
                && !(keyCode >= KeyEvent.KEYCODE_DPAD_UP && keyCode <= KeyEvent.KEYCODE_DPAD_RIGHT)) {
            said.setText(refusal + " Pick another button.");
        }
        return super.onKeyDown(keyCode, event);
    }

    private void picked(int keyCode) {
        InputKey.pick(this, keyCode);
        said.setText("The input menu now opens with " + InputKey.describe(this) + ".");
        listening = false;
        said.postDelayed(this::finish, 1500);
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

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }

    private StateListDrawable buttonBackground() {
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(dp(16));
        focused.setColor(0xFF2A3340);
        focused.setStroke(dp(3), Color.WHITE);
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(dp(16));
        normal.setColor(0xFF263040);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[0], normal);
        return states;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
