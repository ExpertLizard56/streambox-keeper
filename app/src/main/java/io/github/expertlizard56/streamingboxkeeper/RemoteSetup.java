package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.content.Intent;

/**
 * The remote's own power, volume and Input buttons, as Google TV sets them up ("Set up remote buttons", the system app
 * com.google.android.tv.axel): each is NONE (does nothing), IR (infrared to the TV), CEC (over HDMI), BROADCAST (both),
 * or the name of a device it sends infrared to (a soundbar). Read from that app's service through the box's own ADB,
 * which prints them ("Power: CEC"). Boxes without it (not Google TV) skip this step.
 */
final class RemoteSetup {

    /** Opens Google TV's own set-up for the remote's TV buttons. */
    static final String WIZARD = "com.google.android.tv.IR_WIZARD";
    private static final String SERVICE = "com.google.android.tv.axel/.remote.IrService";

    final String power;
    final String volume;
    final String input;

    private RemoteSetup(String power, String volume, String input) {
        this.power = power;
        this.volume = volume;
        this.input = input;
    }

    /** This box has Google TV's remote set-up. */
    static boolean available(Context context) {
        return new Intent(WIZARD).resolveActivity(context.getPackageManager()) != null;
    }

    /** What's set up now; null if it can't be read (no system access, or no such service). Blocks; off the main thread. */
    static RemoteSetup read(Context context) {
        try {
            return parse(SelfAdb.shell(context, "dumpsys activity service " + SERVICE));
        } catch (Exception e) {
            return null;
        }
    }

    static RemoteSetup parse(String dump) {
        String power = null;
        String volume = null;
        String input = null;
        for (String line : dump.split("\n")) {
            String l = line.trim();
            if (l.startsWith("Power: ")) power = l.substring(7).trim();
            else if (l.startsWith("Volume: ")) volume = l.substring(8).trim();
            else if (l.startsWith("Input: ")) input = l.substring(7).trim();
        }
        return power == null && volume == null ? null : new RemoteSetup(power, volume, input);
    }

    static boolean isSet(String mode) {
        return mode != null && !mode.isEmpty() && !"NONE".equals(mode) && !"null".equals(mode);
    }

    boolean done() {
        return isSet(power) && isSet(volume);
    }

    /** The remote sends Input to the TV by infrared itself, so the box may never see that button. */
    boolean inputByInfrared() {
        return isSet(input) && !"CEC".equals(input);
    }

    /** A mode in words: "not set up", "over HDMI", "by infrared", or the device it sends infrared to. */
    static String words(String mode) {
        if (!isSet(mode)) return "not set up";
        switch (mode) {
            case "CEC": return "over HDMI";
            case "IR": return "by infrared";
            case "BROADCAST": return "by infrared and HDMI";
            default:
                int paren = mode.indexOf(" (");
                return "by infrared to " + (paren > 0 ? mode.substring(0, paren) : mode);
        }
    }
}
