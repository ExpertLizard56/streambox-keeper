package io.github.expertlizard56.streamingboxkeeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** Keep my settings' decisions: what's remembered, and what's put back after a firmware update. */
public class KeptSettingsTest {

    private static final String PKG = "io.github.expertlizard56.streamingboxkeeper";

    private static Map<String, String> values(String frame, String hdr, String home) {
        Map<String, String> m = new HashMap<>();
        if (frame != null) m.put(KeptSettings.FRAME_RATE, frame);
        if (hdr != null) m.put(KeptSettings.HDR, hdr);
        if (home != null) m.put(KeptSettings.HOME, home);
        return m;
    }

    @Test
    public void unchangedPutsNothingBack() {
        KeptSettings.Decision d = KeptSettings.decide("fw1", values("2", "2", "a/.Home"), "fw1", values("2", "2", "a/.Home"));
        assertTrue(d.putBack.isEmpty());
        assertEquals("2", d.remember.get(KeptSettings.FRAME_RATE));
        assertEquals("fw1", d.fingerprint);
    }

    @Test
    public void theFirstCheckRemembers() {
        KeptSettings.Decision d = KeptSettings.decide(null, new HashMap<>(), "fw1", values("2", "1", "a/.Home"));
        assertTrue(d.putBack.isEmpty());
        assertEquals("1", d.remember.get(KeptSettings.HDR));
    }

    @Test
    public void aChangeOnTheSameFirmwareIsThePersonsChoice() {
        KeptSettings.Decision d = KeptSettings.decide("fw1", values("2", "2", "a/.Home"), "fw1", values("0", "2", "b/.Other"));
        assertTrue(d.putBack.isEmpty());
        assertEquals("0", d.remember.get(KeptSettings.FRAME_RATE));
        assertEquals("b/.Other", d.remember.get(KeptSettings.HOME));
    }

    @Test
    public void aFirmwareUpdatePutsBackWhatChanged() {
        KeptSettings.Decision d = KeptSettings.decide("fw1", values("2", "2", "a/.Home"), "fw2", values("0", "2", "b/.Other"));
        assertEquals(2, d.putBack.size());
        assertEquals("2", d.putBack.get(KeptSettings.FRAME_RATE));
        assertEquals("a/.Home", d.putBack.get(KeptSettings.HOME));
        assertFalse(d.putBack.containsKey(KeptSettings.HDR));
        assertEquals("2", d.remember.get(KeptSettings.FRAME_RATE));
        assertEquals("fw2", d.fingerprint);
    }

    @Test
    public void anUnreadableValueIsSkippedAndKept() {
        KeptSettings.Decision d = KeptSettings.decide("fw1", values("2", "2", null), "fw2", values(null, "1", null));
        assertFalse(d.putBack.containsKey(KeptSettings.FRAME_RATE));
        assertEquals("2", d.remember.get(KeptSettings.FRAME_RATE));
        assertEquals("2", d.putBack.get(KeptSettings.HDR));
        assertFalse(d.remember.containsKey(KeptSettings.HOME));
    }

    @Test
    public void noFingerprintDoesNothing() {
        assertNull(KeptSettings.decide("fw1", values("2", "2", null), null, values("0", "2", null)));
    }

    @Test
    public void theRemoteHelperChangesOnlyItsOwnEntry() {
        String others = "com.a/.One:" + PKG + "/.LinkService:com.b/.Two";
        assertEquals("on", KeptSettings.helperState(others, PKG));
        assertEquals("com.a/.One:com.b/.Two", KeptSettings.withHelper(others, PKG, PKG + "/.LinkService", false));
        assertEquals(others, KeptSettings.withHelper(others, PKG, PKG + "/.LinkService", true));
        assertEquals("com.a/.One:" + PKG + "/.LinkService",
                KeptSettings.withHelper("com.a/.One", PKG, PKG + "/.LinkService", true));
        assertEquals(PKG + "/.LinkService", KeptSettings.withHelper("null", PKG, PKG + "/.LinkService", true));
        assertEquals("off", KeptSettings.helperState("com.a/.One", PKG));
    }

    @Test
    public void parsingLeavesOutUnsetValues() {
        String out = "fp=onn/ott/x:14/1.0:user/release-keys\nframe=2\nhdr=null\nsaver=\n"
                + "a11y=com.a/.One:" + PKG + "/.LinkService\nhome=com.x.launcher/.Main\n";
        Map<String, String> m = KeptSettings.parse(out, PKG);
        assertEquals("onn/ott/x:14/1.0:user/release-keys", m.get("fp"));
        assertEquals("2", m.get(KeptSettings.FRAME_RATE));
        assertFalse(m.containsKey(KeptSettings.HDR));
        assertFalse(m.containsKey(KeptSettings.SCREENSAVER));
        assertEquals("on", m.get(KeptSettings.REMOTE_HELPER));
        assertEquals("com.x.launcher/.Main", m.get(KeptSettings.HOME));
        assertFalse(KeptSettings.parse("home=android/com.android.internal.app.ResolverActivity\n", PKG)
                .containsKey(KeptSettings.HOME));
    }
}
