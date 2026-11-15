package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A remote whose buttons work but whose mic Google TV never set up. Seen on an Onn 4K Plus, Nov 14: the remote was
 * paired during the box's first set-up, so Bluetooth has it bonded and connected and every button works, but Google
 * TV's remote service (com.google.android.tv.remote.service) has no record of it, so its mic sends no sound: voice
 * search hears nothing in apps and in Google Assistant (logcat: "AtvRemote.AudioProviderService: No active audio device
 * available"). Restarting that service doesn't help; forgetting the remote and pairing it again (hold Back + Home)
 * does. Nothing here can pair a remote, so this is a check and a message on the main screen, not a fix.
 *
 * Both lists come from one shell call through the box's own ADB (SelfAdb):
 *  - `dumpsys bluetooth_manager`, the "Bonded devices:" section, lines like
 *    "E8:DF:24:59:C7:96 [  LE  ] Onn-Remote";
 *  - `dumpsys activity service com.google.android.tv.remote.service/.RemoteService`, the "BLE remotes:" section, lines
 *    like "Onn-Remote (E8:DF:24:59:C7:96): READY, Capability: v1.0, ...".
 * Broken is a bonded LE remote whose address is missing there, or listed as anything but READY. Addresses are matched,
 * never names (the two lists spell a name differently). A box with no remote service, or whose dump has no "BLE
 * remotes:" section, gets no message at all: there's nothing to compare with.
 *
 * Which bonded devices count as remotes: this uses only the name, a device whose name says "Remote" (Onn-Remote,
 * RemoteG10, RemoteG20). That's the simple half of the rule, because the bonded list doesn't say what a device is; a
 * bonded phone, keyboard or pair of headphones isn't named "Remote", so it stays quiet.
 */
final class RemoteMic {

    private static final String SERVICE = "com.google.android.tv.remote.service/.RemoteService";
    /** Where the two dumps meet: everything before this is the Bluetooth one, everything after the remote service's. */
    private static final String SPLIT = "===remote service===";
    private static final String READ = "dumpsys bluetooth_manager; echo " + SPLIT
            + "; dumpsys activity service " + SERVICE;
    /** A MAC address. */
    private static final String ADDRESS = "([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})";
    /** A line of the Bluetooth dump's bonded list: address, transports in brackets, then the name. */
    private static final Pattern BONDED = Pattern.compile("^" + ADDRESS + "\\s*\\[([^\\]]*)\\]\\s*(.*)$");
    /** A line of the remote service's BLE remotes list: name (address): state, ... */
    private static final Pattern KNOWN = Pattern.compile("^(.+?)\\s*\\(\\s*" + ADDRESS + "\\s*\\)\\s*:\\s*([^,]+)");

    private RemoteMic() {
    }

    /** What the check found. */
    static final class State {
        /** The bonded remote the remote service doesn't know, by its Bluetooth name; null when every remote is set up. */
        final String broken;

        State(String broken) {
            this.broken = broken;
        }
    }

    /** Reads both lists in one shell call; null when it can't be read (no system access) or there's no remote service. */
    static State read(Context context) {
        try {
            return parse(SelfAdb.shell(context, READ));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * What the one shell call printed: the box's Bluetooth dump, the split, then the remote service's. Null when there's
     * no "BLE remotes:" section to compare with (no remote service on this box, or not Google TV): nothing to say.
     */
    static State parse(String out) {
        String dump = out == null ? "" : out;
        int split = dump.indexOf(SPLIT);
        if (split < 0) return null;
        Map<String, String> known = knownRemotes(dump.substring(split + SPLIT.length()));
        if (known == null) return null;
        for (String[] remote : bondedRemotes(dump.substring(0, split))) {
            // The address is the key: the remote service has its own idea of the name, which needn't match.
            String state = known.get(remote[0]);
            if (state == null || !"READY".equalsIgnoreCase(state)) return new State(remote[1]);
        }
        return new State(null);
    }

    /** The bonded LE devices named like a remote, as {address, name}, in the dump's order. */
    private static List<String[]> bondedRemotes(String dump) {
        List<String[]> found = new ArrayList<>();
        boolean bonded = false;
        for (String line : dump.split("\n")) {
            String l = line.trim();
            if (l.startsWith("Bonded devices:")) {
                bonded = true;
                continue;
            }
            if (!bonded || l.isEmpty()) continue;
            Matcher m = BONDED.matcher(l);
            if (!m.matches()) {
                bonded = false;  // the list ended: another section's heading, or anything else
                continue;
            }
            // BR/EDR alone is a phone, a soundbar or an older remote: its mic isn't this service's business.
            if (!m.group(2).contains("LE")) continue;
            String name = m.group(3).trim();
            if (!name.toLowerCase(Locale.ROOT).contains("remote")) continue;
            found.add(new String[] {address(m.group(1)), name});
        }
        return found;
    }

    /** The remotes the remote service knows, address to state ("READY", ...); null when it has no "BLE remotes:" list. */
    private static Map<String, String> knownRemotes(String dump) {
        Map<String, String> found = null;
        boolean section = false;
        for (String line : dump.split("\n")) {
            String l = line.trim();
            if (l.startsWith("BLE remotes:")) {
                found = new HashMap<>();
                section = true;
                continue;
            }
            if (!section || l.isEmpty()) continue;
            Matcher m = KNOWN.matcher(l);
            if (!m.lookingAt()) {
                section = false;  // another list's heading, or the dump's end
                continue;
            }
            found.put(address(m.group(2)), m.group(3).trim());
        }
        return found;
    }

    /** One spelling for an address, so the two dumps' upper and lower case don't matter. */
    private static String address(String address) {
        return address.toUpperCase(Locale.ROOT);
    }
}
