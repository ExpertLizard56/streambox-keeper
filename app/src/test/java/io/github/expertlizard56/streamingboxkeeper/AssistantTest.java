package io.github.expertlizard56.streamingboxkeeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Reading the one shell call the check makes about Google Assistant and the mic (Assistant), as an Onn 4K Plus printed
 * it on Nov 14.
 */
public class AssistantTest {

    /** Assistant switched off, the voice engine set again: what the switch leaves behind. */
    private static final String SWITCHED_OFF = "installed=package:com.google.android.katniss\n"
            + "disabled=package:com.google.android.katniss\n"
            + "tts=package:com.google.android.tts\n"
            + "voice=" + Assistant.RECOGNITION + "\n";

    @Test
    public void switchedOffWithTheMicKeptIsDone() {
        Assistant.State s = Assistant.parse(SWITCHED_OFF);
        assertTrue(s.installed);
        assertTrue(s.disabled);
        assertTrue(s.ttsInstalled);
        assertTrue(s.done());
        assertFalse(s.needsDisable());
        assertFalse(s.needsVoice());
    }

    @Test
    public void aSystemUpdateThatUndidBothIsRedone() {
        Assistant.State s = Assistant.parse("installed=package:com.google.android.katniss\n"
                + "disabled=\n"
                + "tts=package:com.google.android.tts\n"
                + "voice=null\n");
        assertFalse(s.done());
        assertTrue(s.needsDisable());
        assertTrue(s.needsVoice());
        assertNull(s.voice);
    }

    @Test
    public void aSystemUpdateThatOnlyClearedTheEngine() {
        Assistant.State s = Assistant.parse(SWITCHED_OFF.replace(Assistant.RECOGNITION, "null"));
        assertFalse(s.done());
        assertFalse(s.needsDisable());
        assertTrue(s.needsVoice());
    }

    @Test
    public void noGoogleAssistantIsNothingToDo() {
        Assistant.State s = Assistant.parse("installed=\ndisabled=\ntts=package:com.google.android.tts\nvoice=null\n");
        assertFalse(s.installed);
        assertFalse(s.done());
        assertFalse(s.needsDisable());
        assertFalse(s.needsVoice());
    }

    @Test
    public void withoutGoogleTtsOnlyTheAppIsSwitchedOff() {
        Assistant.State s = Assistant.parse("installed=package:com.google.android.katniss\n"
                + "disabled=package:com.google.android.katniss\n"
                + "tts=\n"
                + "voice=\n");
        assertTrue(s.done());
        assertFalse(s.needsVoice());
        assertNull(s.voice);
    }

    @Test
    public void extraLinesFromThePackageCommandsAreIgnored() {
        Assistant.State s = Assistant.parse("installed=package:com.google.android.katniss\n"
                + "package:com.google.android.katniss\n"
                + "disabled=package:com.google.android.katniss\n"
                + "tts=package:com.google.android.tts\n"
                + "voice=" + Assistant.RECOGNITION + "\n");
        assertTrue(s.done());
        assertEquals(Assistant.RECOGNITION, s.voice);
        assertNull(Assistant.parse("").voice);
    }
}
