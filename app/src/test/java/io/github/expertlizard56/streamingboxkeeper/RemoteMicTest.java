package io.github.expertlizard56.streamingboxkeeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The remote mic check on dumps shaped like an Onn 4K Plus's (Nov 14). */
public class RemoteMicTest {

    private static final String BLUETOOTH = "Bluetooth Status\n  enabled: true\n  Bonded devices:\n"
            + "    E8:DF:24:59:C7:96 [  LE  ] Onn-Remote\n    11:22:33:44:55:66 [BR/EDR] Pixel Phone\n"
            + "    AA:BB:CC:DD:EE:FF [  LE  ] Buds\n\n  Profile: GattService\n";
    private static final String SPLIT = "===remote service===\n";

    @Test
    public void aRemoteTheServiceDoesntKnowIsBroken() {
        String out = BLUETOOTH + SPLIT + "REMOTE SERVICE\n  BLE remotes:\n\n  Other: none\n";
        assertEquals("Onn-Remote", RemoteMic.parse(out).broken);
    }

    @Test
    public void aReadyRemoteIsFine() {
        String out = BLUETOOTH + SPLIT + "  BLE remotes:\n"
                + "    Onn-Remote (e8:df:24:59:c7:96): READY, Capability: v1.0, Battery: 80\n";
        assertNull(RemoteMic.parse(out).broken);
    }

    @Test
    public void aRemoteNotReadyIsBroken() {
        String out = BLUETOOTH + SPLIT + "  BLE remotes:\n"
                + "    Onn-Remote (E8:DF:24:59:C7:96): DISCONNECTED, Capability: v1.0\n";
        assertEquals("Onn-Remote", RemoteMic.parse(out).broken);
    }

    @Test
    public void noRemoteServiceSaysNothing() {
        assertNull(RemoteMic.parse(BLUETOOTH + SPLIT
                + "No services match: com.google.android.tv.remote.service/.RemoteService\n"));
        assertNull(RemoteMic.parse(""));
    }
}
