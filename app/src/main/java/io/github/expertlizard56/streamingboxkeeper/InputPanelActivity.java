package io.github.expertlizard56.streamingboxkeeper;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The TV's inputs, full screen: tiles in rows of three sized to the screen, this box first, each with what it is
 * under its name (HDMI 2, Antenna...). The input the TV was on when the button was pressed is marked "On now" and
 * focused. Opened by the remote's Input or star button (LinkService, or the system's VIEW_INPUTS that
 * the launcher's InputKeyService starts); from another input or the TV's own home screen LinkService first brings the
 * TV back to the box (the box can only draw over its own input) and says where it was ("from"). Arrows pick, OK
 * switches the TV; Back or the same button again closes, and takes the TV back to where it was. `am start -n io.github.expertlizard56.streamingboxkeeper/.InputPanelActivity --ez demo true` shows sample inputs on a
 * box with no TV (the emulator), to check the look.
 */
public class InputPanelActivity extends Activity {

    private static final int COLUMNS = 3;
    private static final int SIDE_DP = 64;
    private static final int GAP_DP = 20;
    private LinearLayout grid;
    private View page;
    /** The input the TV was on when the menu was asked for: the box's, or another the TV was brought back from. */
    private String current = Tv.BOX;
    /** The menu on screen, if any; touched only on the main thread. Weak, so a menu that's gone can always be freed. */
    private static WeakReference<InputPanelActivity> shown;

    static InputPanelActivity shown() {
        return shown == null ? null : shown.get();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String from = getIntent().getStringExtra("from");
        if (from != null) current = from;
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF161B22, 0xFF0B0E12});
        root.setBackground(bg);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(SIDE_DP), dp(48), dp(SIDE_DP), dp(32));
        // The header: "Inputs" on the left, which TV it switches on the right (Nov 8: no line of text under it).
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Inputs", 40, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView tvName = text(capitalized(tvName()), 16, 0xCCFFFFFF);
        tvName.setPadding(dp(18), dp(8), dp(18), dp(8));
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(20));
        pill.setColor(0xFF1C222B);
        pill.setStroke(dp(1), 0x33FFFFFF);
        tvName.setBackground(pill);
        header.addView(tvName);
        LinearLayout.LayoutParams headerSize = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerSize.bottomMargin = dp(36);
        column.addView(header, headerSize);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        column.addView(grid);
        root.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        page = root;
        setContentView(root);
        page.setAlpha(0f);
        page.animate().alpha(1f).setDuration(150).start();
        new Thread(this::load).start();
    }

    private void load() {
        Tv tv = LinkService.tv;
        List<String[]> items;
        if (tv != null) {
            items = tv.inputs();
        } else if (getIntent().getBooleanExtra("demo", false)) {
            items = Arrays.asList(new String[] {Tv.BOX, "Streaming Box"}, new String[] {"tvinput.hdmi2", "Game Console"},
                    new String[] {"tvinput.hdmi3", "PC"}, new String[] {"tvinput.cvbs", "AV"},
                    new String[] {"tvinput.dtv", "Live TV"}, new String[] {Tv.HOME, "Roku Home"});
        } else {
            runOnUiThread(this::finish);
            return;
        }
        runOnUiThread(() -> show(items));
    }

    /** The box's tile first; the one the TV was on marked "On now" and focused. */
    private void show(List<String[]> all) {
        if (isFinishing()) return;
        List<String[]> items = new ArrayList<>();
        for (String[] item : all) if (Tv.BOX.equals(item[0])) items.add(item);
        for (String[] item : all) if (!Tv.BOX.equals(item[0])) items.add(item);
        int screenDp = Math.round(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        int tileDp = (screenDp - 2 * SIDE_DP - (COLUMNS - 1) * GAP_DP) / COLUMNS;
        View focus = null;
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            String[] item = items.get(i);
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowSize = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowSize.bottomMargin = dp(GAP_DP);
                grid.addView(row, rowSize);
            }
            boolean box = Tv.BOX.equals(item[0]);
            boolean here = current.equals(item[0]);
            String kind = kindOf(box ? boxInput() : item[0]);
            if (here) kind = kind.isEmpty() || kind.equalsIgnoreCase(item[1]) ? "On now" : kind + "  ·  On now";
            View tile = tile(box ? "This box" : item[1], kind, here);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(tileDp), dp(124));
            if (i % COLUMNS != COLUMNS - 1) size.rightMargin = dp(GAP_DP);
            tile.setOnClickListener(v -> choose(item[0]));
            row.addView(tile, size);
            if (here || focus == null) focus = tile;
        }
        if (focus != null) focus.requestFocus();
    }

    /** Which TV this menu switches, for the header: "Roku TV" or "Fire TV" (a Roku in the demo). */
    private String tvName() {
        TvConfig config = TvConfig.load(this);
        return config != null && "fire".equals(config.kind) ? "Fire TV" : "Roku TV";
    }

    /** The TV's name for the box's own input (tvinput.hdmi1, HW7), from the saved set-up; HDMI 1 in the demo. */
    private String boxInput() {
        TvConfig config = TvConfig.load(this);
        return config != null ? config.input : "tvinput.hdmi1";
    }

    /** What an input is, in words: HDMI 2, AV, Antenna, the TV's home screen. */
    private static String kindOf(String id) {
        if (Tv.BOX.equals(id)) return "";
        if (Tv.HOME.equals(id)) return "TV Home Screen";
        if (id.startsWith("tvinput.hdmi")) return "HDMI " + id.substring("tvinput.hdmi".length());
        if (id.equals("tvinput.dtv")) return "Antenna";
        if (id.equals("tvinput.cvbs")) return "AV";
        if (id.matches("HW\\d+")) return "HDMI " + (Integer.parseInt(id.substring(2)) - 3);
        return "";
    }

    /** A tile: the input's name, what it is under it (blue "On now" on the box's), a white edge and lighter fill when focused. */
    private View tile(String name, String kind, boolean here) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(24), 0, dp(20), 0);
        tile.setFocusable(true);
        tile.setBackground(tileBackground());
        TextView label = text(name, 24, Color.WHITE);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(label);
        if (!kind.isEmpty() && !kind.equalsIgnoreCase(name)) {
            TextView under = text(capitalized(kind), 15, here ? 0xFF8AB4F8 : 0x8CFFFFFF);
            under.setPadding(0, dp(4), 0, 0);
            tile.addView(under);
        }
        // No grow on focus: a grown tile spills past its row and gets cut off (Nov 8, the right column's edge).
        return tile;
    }

    private void choose(String id) {
        if (!Tv.BOX.equals(id) && LinkService.tv != null) LinkService.tv.switchTo(id);
        close();
    }

    /** Closed without a choice: the TV goes back to the input it was on. */
    private void cancel() {
        choose(current);
    }

    private void close() {
        page.animate().alpha(0f).setDuration(120).withEndAction(this::finish).start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || LinkService.isInputKey(keyCode)) {
            dismiss();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Back, or Input/star again (LinkService calls this, as it takes those keys first). */
    void dismiss() {
        cancel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        shown = new WeakReference<>(this);
    }

    @Override
    protected void onPause() {
        if (shown() == this) shown = null;
        super.onPause();
        // Home or another app took over: the menu is done, and the TV stays on the box.
        if (!isFinishing()) finish();
    }

    private StateListDrawable tileBackground() {
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(dp(18));
        focused.setColor(0xFF2A3340);
        focused.setStroke(dp(3), Color.WHITE);
        GradientDrawable normal = new GradientDrawable();
        normal.setCornerRadius(dp(18));
        normal.setColor(0xFF1C222B);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[0], normal);
        return states;
    }

    /**
     * Every subtext starts each word with a capital ("On Now", "TV Home Screen",
     * "Choose What The TV Shows"). Words already in capitals (HDMI, TV) stay as they are.
     */
    static String capitalized(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean start = true;
        for (char c : s.toCharArray()) {
            out.append(start ? Character.toUpperCase(c) : c);
            start = Character.isWhitespace(c);
        }
        return out.toString();
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
