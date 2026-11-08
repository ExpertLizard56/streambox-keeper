package io.github.expertlizard56.streamingboxkeeper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Runs one job now for testing (Jobs.runOne). Only the system shell can send it: the receiver needs DUMP. */
public class TestRun extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String job = intent.getStringExtra("job");
        PendingResult done = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                Jobs.runOne(app, job);
            } finally {
                done.finish();
            }
        }, "streaming-box-keeper-test").start();
    }
}
