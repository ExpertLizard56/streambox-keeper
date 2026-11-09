package io.github.expertlizard56.streamingboxkeeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Reading Google TV's remote set-up as its service prints it (copied from two Onn 4K Pro boxes, Nov 9). */
public class RemoteSetupTest {

    private static final String SOUNDBAR = "SERVICE com.google.android.tv.axel/.remote.IrService 676e285 pid=1704 user=0\n"
            + "  Client:\n    Volume: LG Soundbar (SOUNDBAR:LG)\n    Power: BROADCAST\n    Input: CEC\n"
            + "    Magic: Software [Onn-Remote Keyboard]\n    IR Profile: 3000258||\n";

    @Test
    public void aSoundbarSetUpIsDone() {
        RemoteSetup r = RemoteSetup.parse(SOUNDBAR);
        assertTrue(r.done());
        assertFalse(r.inputByInfrared());
        assertEquals("by infrared to LG Soundbar", RemoteSetup.words(r.volume));
        assertEquals("by infrared and HDMI", RemoteSetup.words(r.power));
    }

    @Test
    public void noVolumeIsNotDone() {
        RemoteSetup r = RemoteSetup.parse("  Client:\n    Volume: NONE\n    Power: CEC\n    Input: IR\n");
        assertFalse(r.done());
        assertTrue(r.inputByInfrared());
        assertEquals("not set up", RemoteSetup.words(r.volume));
    }

    @Test
    public void noServiceIsUnknown() {
        assertNull(RemoteSetup.parse("No services match: com.google.android.tv.axel/.remote.IrService\n"));
    }
}
