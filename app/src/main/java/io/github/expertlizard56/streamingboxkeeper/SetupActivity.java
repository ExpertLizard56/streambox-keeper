package io.github.expertlizard56.streamingboxkeeper;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import java.util.List;

/**
 * Which TV this box is plugged into, for when TvFinder can't tell alone (several TVs on the network): lists the TVs it
 * finds and saves the one picked. Needs the box's system access for the HDMI port, so it offers that first (the box
 * asks once, "Allow USB debugging?"). Opened from the main screen's "Your TV" step.
 */
public class SetupActivity extends Activity {

    private Screen screen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        screen = new Screen(this);
        Check.schedule(this);  // opening the app once also starts the upkeep (it starts after every boot too)
        search();
    }

    private void search() {
        screen.start("Your TV", "The TV this box is plugged into. Streaming Box Keeper usually finds it by itself.");
        screen.card("Looking for TVs on the network…", null, null, null);
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
        String about = now == null ? "Not chosen yet: pick the TV this box is plugged into."
                : "Now: " + ("fire".equals(now.kind) ? "Fire TV" : "Roku TV") + " at " + now.address
                        + (port > 0 ? ", this box on its HDMI " + port + "." : ".");
        screen.start("Your TV", about);
        View first = null;
        if (!allowed) {
            first = screen.card("Allow system access first",
                    "Press OK: if the box asks \"Allow USB debugging?\", tick Always allow and press Allow. If it doesn't"
                            + " ask, turn on Network debugging in Settings > System > Developer options.",
                    null, v -> search());
        } else if (port == 0) {
            screen.card("Couldn't read which HDMI port this box is on", null, null, null);
        }
        if (port > 0) {
            for (TvFinder.Found tv : found) {
                boolean chosen = now != null && now.kind.equals(tv.kind) && now.address.equals(tv.address);
                View b = screen.card(tv.label(), tv.showingBox ? "Showing this box now" : tv.address,
                        chosen ? "✓  Chosen" : null, v -> {
                            new TvConfig(tv.kind, tv.address, TvFinder.inputFor(tv.kind, port), tv.id).save(this);
                            LinkService.reload(this);
                            search();
                        });
                if (first == null) first = b;
            }
        }
        if (found.isEmpty()) screen.card("No Roku or Fire TV found on the network", "Is the TV on?", null, null);
        View again = screen.card("Search again", null, null, v -> search());
        (first != null ? first : again).requestFocus();
    }
}
