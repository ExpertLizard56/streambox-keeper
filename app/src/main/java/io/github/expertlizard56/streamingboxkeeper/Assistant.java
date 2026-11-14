package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.util.Log;

/**
 * Google Assistant switched off, so the remote's mic button stops starting it, while apps keep a mic to search with
 * (Features.ASSISTANT).
 *
 * On Google TV the remote's mic button starts Google Assistant (com.google.android.katniss) from the remote's own voice
 * signal, so LinkService's key filter never sees it and can't stop it. Switching that app off does, but switching it off
 * alone also clears the speech engine apps use, which takes the mic away in apps like Stremio. Google TV keeps the
 * engine in another app (com.google.android.tts), whose recognition service is put back by name, and then apps'
 * voice search works with Assistant gone. Tested by hand on an Onn 4K Plus, Nov 14.
 *
 * All of it goes through the box's own ADB (SelfAdb), allowed once on the box:
 *  - off: `pm disable-user --user 0 com.google.android.katniss`, then, only with com.google.android.tts installed,
 *    `settings put secure voice_recognition_service <Google TTS's recognition service>`;
 *  - back on: `pm enable com.google.android.katniss` (the box fills its voice settings back in itself).
 * A system update can undo either half, so Check re-reads both at every wake-up (keepOff) and redoes what was undone.
 */
final class Assistant {

    private static final String TAG = "BoxKeeper";
    static final String KATNISS = "com.google.android.katniss";
    static final String TTS = "com.google.android.tts";
    /** Google's speech engine in com.google.android.tts: what apps' voice search uses once Assistant is off. */
    static final String RECOGNITION = TTS
            + "/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService";

    /** One shell call that reads everything the check needs: what's installed, and how the voice settings stand. */
    private static final String READ = "echo installed=$(pm list packages " + KATNISS + ")"
            + "; echo disabled=$(pm list packages -d --user 0 " + KATNISS + ")"
            + "; echo tts=$(pm list packages " + TTS + ")"
            + "; echo voice=$(settings get secure voice_recognition_service)";

    private Assistant() {
    }

    /** What the box has and how it's set now. */
    static final class State {
        /** com.google.android.katniss (Google Assistant) is on the box. */
        final boolean installed;
        /** Google Assistant is switched off for the box's user. */
        final boolean disabled;
        /** com.google.android.tts is on the box: the engine apps' voice search then uses. */
        final boolean ttsInstalled;
        /** voice_recognition_service as it stands; null when the system has none set. */
        final String voice;

        State(boolean installed, boolean disabled, boolean ttsInstalled, String voice) {
            this.installed = installed;
            this.disabled = disabled;
            this.ttsInstalled = ttsInstalled;
            this.voice = voice;
        }

        /** Google Assistant is off and apps keep a mic: what the switch leaves the box in. */
        boolean done() {
            return installed && disabled && !needsVoice();
        }

        /** Assistant is there and back on (a system update puts it back): switch it off again. */
        boolean needsDisable() {
            return installed && !disabled;
        }

        /** Assistant is on the box and apps' voice search lost its engine (a system update clears it): set it again. */
        boolean needsVoice() {
            return installed && ttsInstalled && !RECOGNITION.equals(voice);
        }
    }

    /** Reads both halves in one shell call; null when it can't be read (no system access, or no such app). Blocks. */
    static State read(Context context) {
        try {
            return parse(SelfAdb.shell(context, READ));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * What the check's one shell call printed: a line for each of installed, disabled, tts and voice, then any other
     * lines the package commands printed, which are ignored.
     */
    static State parse(String out) {
        boolean installed = false;
        boolean disabled = false;
        boolean tts = false;
        String voice = null;
        for (String line : (out == null ? "" : out).split("\n")) {
            String l = line.trim();
            if (l.startsWith("installed=")) installed |= l.length() > "installed=".length();
            else if (l.startsWith("disabled=")) disabled |= l.length() > "disabled=".length();
            else if (l.startsWith("tts=")) tts |= l.length() > "tts=".length();
            else if (l.startsWith("voice=")) {
                String v = l.substring("voice=".length()).trim();
                voice = v.isEmpty() || "null".equals(v) ? null : v;
            }
        }
        return new State(installed, disabled, tts, voice);
    }

    /**
     * Switches Google Assistant off for the box's user, and puts the voice engine back so apps can still use the mic.
     * One shell call; does nothing on a box without Google Assistant. Blocks.
     */
    static void turnOff(Context context) throws Exception {
        SelfAdb.shell(context, "pm disable-user --user 0 " + KATNISS
                + "; if pm list packages " + TTS + " | grep -q package:; then settings put secure"
                + " voice_recognition_service " + RECOGNITION + "; fi");
    }

    /** Switches Google Assistant back on; the box fills its voice settings back in itself. Blocks. */
    static void turnOn(Context context) throws Exception {
        SelfAdb.shell(context, "pm enable " + KATNISS);
    }

    /**
     * Check's re-check while the switch is on: one shell call reads both halves, and only when a system update undid one
     * is another call made, to redo it. Blocks; never throws (the rest of the check goes on).
     */
    static void keepOff(Context context) {
        try {
            State now = read(context);
            if (now == null || !now.installed) return;
            StringBuilder fix = new StringBuilder();
            if (now.needsDisable()) fix.append("pm disable-user --user 0 ").append(KATNISS);
            if (now.needsVoice()) {
                if (fix.length() > 0) fix.append("; ");
                fix.append("settings put secure voice_recognition_service ").append(RECOGNITION);
            }
            if (fix.length() == 0) return;
            SelfAdb.shell(context, fix.toString());
            Log.i(TAG, "Assistant or the mic was undone, put back: " + fix);
        } catch (Exception e) {
            Log.w(TAG, "Google Assistant and the mic not re-checked: " + e);
        }
    }
}
