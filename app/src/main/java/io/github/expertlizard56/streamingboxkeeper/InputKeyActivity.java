package io.github.expertlizard56.streamingboxkeeper;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Picking which button opens the input menu (InputKey): Input, ★, or both, like Google TV's own settings, which only
 * let the ★ button be changed.
 */
public class InputKeyActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(96), dp(48), dp(96), dp(48));
        page.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF161B22, 0xFF0B0E12}));
        page.addView(text("Which button opens the input menu?", 30, Color.WHITE));
        page.addView(text("The other one keeps doing what Google TV set it to.", 18, 0x99FFFFFF));
        int now = InputKey.choice(this);
        View focus = null;
        for (int c : new int[] {InputKey.BOTH, InputKey.INPUT, InputKey.STAR}) {
            TextView b = text((c == now ? "✓  " : "") + InputKey.words(c), 20, Color.WHITE);
            b.setPadding(dp(32), dp(12), dp(32), dp(12));
            b.setFocusable(true);
            b.setBackground(buttonBackground());
            b.setOnClickListener(v -> {
                InputKey.choose(this, c);
                finish();
            });
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(360), LinearLayout.LayoutParams.WRAP_CONTENT);
            size.topMargin = dp(16);
            page.addView(b, size);
            if (c == now) focus = b;
        }
        setContentView(page);
        if (focus != null) focus.requestFocus();
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
