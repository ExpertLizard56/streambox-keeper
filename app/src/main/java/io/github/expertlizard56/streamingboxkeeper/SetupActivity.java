package io.github.expertlizard56.streamingboxkeeper;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/**
 * The app's tile on the home screen: what Streaming Box Keeper last did for the box, and which TV this box is plugged into, for when TvFinder can't tell alone (several TVs on the network): lists the TVs it
 * finds and saves the one picked. Also the app's tile on the home screen, showing what's set up now. Needs the box's
 * system access for the HDMI port, so it offers that first (the box asks once, "Allow USB debugging?").
 */
public class SetupActivity extends Activity {

    private LinearLayout page;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(Color.rgb(16, 18, 24));
        page.setPadding(96, 48, 96, 48);
        setContentView(page);
        Check.schedule(this);  // opening the app once also starts the upkeep (it starts after every boot too)
        search();
    }

    private void search() {
        page.removeAllViews();
        page.addView(text(getString(R.string.app_name), 36));
        TextView about = text(getString(R.string.keeper), 18);
        about.setTextColor(Color.rgb(180, 186, 200));
        page.addView(about);
        status = text(getString(R.string.searching), 20);
        page.addView(status);
        new Thread(() -> {
            boolean allowed = SelfAdb.allowed(this);
            int port = allowed ? TvFinder.hdmiPort(this) : 0;
            List<TvFinder.Found> found = TvFinder.search(this, port, allowed && TvFinder.boxOnScreen(this),
                    allowed ? TvFinder.tvHdmiName(this) : null);
            runOnUiThread(() -> show(allowed, port, found));
        }, "tv-search").start();
    }

    private void show(boolean allowed, int port, List<TvFinder.Found> found) {
        TvConfig now = TvConfig.load(this);
        StringBuilder s = new StringBuilder(keeperState()).append("\n\n");
        s.append(now != null ? getString(R.string.set_up_for, now.kind, now.address, now.input)
                : getString(R.string.not_set_up));
        s.append("\n").append(!allowed ? getString(R.string.allow_first)
                : port > 0 ? getString(R.string.on_port, port) : getString(R.string.no_port));
        if (found.isEmpty()) s.append("\n").append(getString(R.string.none_found));
        else if (port > 0) s.append("\n").append(getString(R.string.pick));
        status.setText(s);
        Button first = null;
        if (port > 0) {
            for (TvFinder.Found tv : found) {
                Button b = button(tv.label() + (tv.showingBox ? getString(R.string.showing_box) : ""));
                b.setOnClickListener(v -> {
                    new TvConfig(tv.kind, tv.address, TvFinder.inputFor(tv.kind, port), tv.id).save(this);
                    LinkService.reload(this);
                    search();
                });
                page.addView(b);
                if (first == null) first = b;
            }
        }
        Button again = button(getString(allowed ? R.string.search_again : R.string.allow_and_search));
        again.setOnClickListener(v -> search());
        page.addView(again);
        (first != null ? first : again).requestFocus();
    }

    /** The connection, this version, and what the nightly jobs last did (Jobs notes them). */
    private String keeperState() {
        StringBuilder s = new StringBuilder(getString(Check.connected(this) ? R.string.online : R.string.offline));
        s.append("\n").append(getString(R.string.tv_remote)).append(" ").append(LinkService.describe(this));
        s.append("\n").append(getString(R.string.version, Jobs.installedVersion(this, getPackageName())));
        android.content.SharedPreferences prefs = getSharedPreferences("keeper", MODE_PRIVATE);
        String[][] items = {{"lastTidy", getString(R.string.last_tidy)}, {"lastRestart", getString(R.string.last_restart)},
                {"lastAppUpdate", getString(R.string.last_app_update)}};
        for (String[] item : items) {
            String v = prefs.getString(item[0], null);
            if (v != null) s.append("\n").append(item[1]).append(" ").append(v);
        }
        return s.toString();
    }

    private TextView text(String s, int size) {
        TextView view = new TextView(this);
        view.setText(s);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, 12, 0, 12);
        return view;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }
}
