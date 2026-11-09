package io.github.expertlizard56.streamingboxkeeper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the 5-minute checks after the box boots and after the app is installed or updated. */
public class Start extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Edition.started(context.getApplicationContext());
        Check.schedule(context);
        Check.run(context.getApplicationContext());
    }
}
