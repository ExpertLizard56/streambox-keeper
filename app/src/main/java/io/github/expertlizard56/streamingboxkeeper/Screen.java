package io.github.expertlizard56.streamingboxkeeper;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The one look of the app's own pages (like Apps To Keep Updated): a dark page, a title and a line under it, then
 * cards, each a title, a detail and maybe a status, that light up with a white edge when chosen with the remote.
 */
final class Screen {

    private final Activity activity;
    final LinearLayout column;

    Screen(Activity activity) {
        this.activity = activity;
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF161B22, 0xFF0B0E12}));
        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(64), dp(40), dp(64), dp(40));
        scroll.addView(column);
        activity.setContentView(scroll);
    }

    /** Clears the page and puts the title and the line under it. */
    void start(String title, CharSequence about) {
        column.removeAllViews();
        TextView t = text(title, 34, Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        column.addView(t);
        TextView a = text(about, 16, 0x99FFFFFF);
        a.setPadding(0, dp(6), 0, dp(24));
        column.addView(a);
    }

    /** A card; with onClick it can be chosen. Returns it. */
    View card(CharSequence title, CharSequence detail, CharSequence status, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(24), dp(16), dp(24), dp(16));
        if (onClick != null) {
            row.setFocusable(true);
            row.setOnClickListener(onClick);
            row.setBackground(chosenEdge());
        } else {
            GradientDrawable plain = new GradientDrawable();
            plain.setCornerRadius(dp(16));
            plain.setColor(0xFF1C222B);
            row.setBackground(plain);
        }
        row.addView(text(title, 22, Color.WHITE));
        if (detail != null) row.addView(text(detail, 15, 0x99FFFFFF));
        if (status != null) row.addView(text(status, 15, 0xFF8AB4F8));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        size.bottomMargin = dp(12);
        column.addView(row, size);
        return row;
    }

    /** Text size in pixels, for a symbol drawn into text (InputKey.label). */
    float px(int sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, activity.getResources().getDisplayMetrics());
    }

    private StateListDrawable chosenEdge() {
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

    private TextView text(CharSequence s, int sp, int color) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }
}
