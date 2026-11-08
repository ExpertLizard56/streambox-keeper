package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;
import java.net.DatagramPacket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which TV this box is plugged into, so one app fits any box. The box's HDMI port comes from its HDMI-CEC
 * address (the box's own ADB, `dumpsys hdmi_control`: 0x4000 is HDMI 4). The TVs come from the network, which knows
 * them better than HDMI does: Roku TVs answer an SSDP search and report their model, Fire TV smart TVs announce
 * themselves (mDNS _amzn-wplay._tcp). Only TVs that are on are found (a Roku fully off stops answering, a Fire TV in
 * standby stops announcing). While its TV is on, HDMI-CEC does give the TV's name (a Hisense Roku TV says
 * "6Series50", its model being 6Series-50; a Fire TV "Amazon FireTV"), so a TV whose kind or model doesn't
 * match it isn't this box's. Ruled out too, while the box is on screen (the HDMI-CEC active source): a Roku that's off
 * or showing something else. Of the rest, one TV left is the one; else a Roku showing this box's input, else the one TV
 * the HDMI name matches. Otherwise the setup screen asks, once (a lone TV found is never taken on trust: a box once missed
 * its just-switched-on Roku and found only another room's Fire TV). Fire TV input names are HW(port + 3), HDMI 4 = HW7 on the Fire TVs tested.
 */
final class TvFinder {

    private static final String TAG = "TvFinder";

    /** A TV found on the network. */
    static final class Found {
        final String kind;
        final String address;
        final String name;
        /** The TV itself, whatever its address: a Roku's serial number, a Fire TV's network name. */
        final String id;
        final boolean showingBox;
        /** Whether it's the TV HDMI-CEC names (its kind, a Roku's model), or null when HDMI gave no name. */
        final Boolean hdmiMatch;
        /** Seen not to be this box's TV: HDMI names another, or while the box was on screen it was off or elsewhere. */
        final boolean notThisBox;

        Found(String kind, String address, String name, String id, boolean showingBox, Boolean hdmiMatch,
                boolean notThisBox) {
            this.kind = kind;
            this.address = address;
            this.name = name;
            this.id = id;
            this.showingBox = showingBox;
            this.hdmiMatch = hdmiMatch;
            this.notThisBox = notThisBox || Boolean.FALSE.equals(hdmiMatch);
        }

        String label() {
            return name + " (" + address + ")";
        }
    }

    private TvFinder() {
    }

    /** The HDMI port this box is plugged into (1-4), or 0 if it can't be read. */
    static int hdmiPort(Context context) {
        try {
            String dump = SelfAdb.shell(context, "dumpsys hdmi_control | grep -m1 mDeviceInfo");
            Matcher m = Pattern.compile("physical_address: 0x([0-9A-Fa-f])000").matcher(dump);
            if (m.find()) return Integer.parseInt(m.group(1), 16);
        } catch (Exception e) {
            Log.w(TAG, "HDMI port not read: " + e);
        }
        return 0;
    }

    /** Whether a TV is showing this box now: it's the HDMI-CEC active source. False if that can't be read. */
    static boolean boxOnScreen(Context context) {
        try {
            return SelfAdb.shell(context, "dumpsys hdmi_control | grep -m1 'isActiveSource()'").contains("true");
        } catch (Exception e) {
            return false;
        }
    }

    /** The TV's name over HDMI-CEC ("6Series50", "Amazon FireTV"), or null: the TV is off, says only "TV", or can't be read. */
    static String tvHdmiName(Context context) {
        try {
            String dump = SelfAdb.shell(context, "dumpsys hdmi_control | grep -m1 'logical_address: 0x00 '");
            Matcher m = Pattern.compile("display_name: (.*?) power_status: (-?\\d+)").matcher(dump);
            if (!m.find() || !m.group(2).equals("0")) return null;
            String name = m.group(1).trim();
            return name.isEmpty() || name.equalsIgnoreCase("TV") ? null : name;
        } catch (Exception e) {
            return null;
        }
    }

    /** Letters and digits only, lower case: "6Series-50" and "6Series50" are the same. */
    private static String plain(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** A Fire TV's network name ends in a number that can change; its first two parts name the TV itself. */
    static String sameTvKey(String kind, String id) {
        if (id == null || !"fire".equals(kind)) return id;
        String[] parts = id.split(":");
        return parts.length >= 2 ? parts[0] + ":" + parts[1] : id;
    }

    /** The TV's name for the box's input on this kind of TV. */
    static String inputFor(String kind, int port) {
        return "fire".equals(kind) ? "HW" + (port + 3) : "tvinput.hdmi" + port;
    }

    /** Looks on the network for a few seconds. Blocks. */
    static List<Found> search(Context context, int port, boolean boxOnScreen, String hdmiName) {
        String hdmi = plain(hdmiName);
        List<Found> found = new ArrayList<>();
        for (String address : rokus()) {
            String info = get("http://" + address + ":8060/query/device-info");
            String name = tag(info, "friendly-model-name");
            String room = tag(info, "user-device-name");
            String app = get("http://" + address + ":8060/query/active-app");
            boolean showing = port > 0 && app != null && app.contains("tvinput.hdmi" + port);
            boolean on = info != null && info.contains("<power-mode>PowerOn</power-mode>");
            boolean notThis = boxOnScreen && port > 0 && info != null && app != null && (!on || !showing);
            String model = plain(tag(info, "model-name"));
            Boolean match = hdmi.isEmpty() ? null : !model.isEmpty() && (hdmi.equals(model) || hdmi.contains(model));
            found.add(new Found("roku", address, (room != null ? room : name != null ? name : "Roku TV"),
                    tag(info, "serial-number"), showing, match, notThis));
        }
        for (Map.Entry<String, String> fire : fireTvs(context).entrySet()) {
            Boolean match = hdmi.isEmpty() ? null : hdmi.contains("fire") || hdmi.contains("amazon");
            found.add(new Found("fire", fire.getKey(), "Fire TV", sameTvKey("fire", fire.getValue()), false, match, false));
        }
        return found;
    }

    /** The one TV this box belongs to if that's clear, or null. */
    static Found pick(List<Found> found) {
        List<Found> left = new ArrayList<>();
        for (Found tv : found) if (!tv.notThisBox) left.add(tv);
        if (left.size() == 1) return left.get(0);
        Found showing = only(left, true);
        return showing != null ? showing : only(left, false);
    }

    /** The one TV left that shows this box (showing) or that HDMI names (else), or null if none or several. */
    private static Found only(List<Found> left, boolean showing) {
        Found one = null;
        for (Found tv : left) {
            if (showing ? !tv.showingBox : !Boolean.TRUE.equals(tv.hdmiMatch)) continue;
            if (one != null) return null;
            one = tv;
        }
        return one;
    }

    /** The saved TV's new address if it has moved (the router gave it another), or null. Blocks. */
    static String movedTo(Context context, TvConfig config) {
        if (config.id == null) return null;
        String key = sameTvKey(config.kind, config.id);
        for (Found tv : search(context, 0, false, null)) {
            if (tv.kind.equals(config.kind) && key.equals(tv.id) && !tv.address.equals(config.address)) return tv.address;
        }
        return null;
    }

    /** The saved TV's id, found by its address, or null. Blocks. */
    static String idOf(Context context, TvConfig config) {
        for (Found tv : search(context, 0, false, null)) {
            if (tv.kind.equals(config.kind) && tv.address.equals(config.address)) return tv.id;
        }
        return null;
    }

    /** First start with nothing saved: find and save the TV if that's clear. True if saved. Blocks. */
    static boolean setUpAlone(Context context) {
        int port = hdmiPort(context);
        if (port == 0) return false;
        Found tv = pick(search(context, port, boxOnScreen(context), tvHdmiName(context)));
        if (tv == null) return false;
        new TvConfig(tv.kind, tv.address, inputFor(tv.kind, port), tv.id).save(context);
        Log.i(TAG, "set up for " + tv.label() + " on HDMI " + port);
        return true;
    }

    private static List<String> rokus() {
        List<String> addresses = new ArrayList<>();
        // MX: how long a TV may wait before answering (strict devices ignore a search without it).
        String search = "M-SEARCH * HTTP/1.1\r\nHost: 239.255.255.250:1900\r\nMan: \"ssdp:discover\"\r\nMX: 2\r\n"
                + "ST: roku:ecp\r\n\r\n";
        try (MulticastSocket socket = new MulticastSocket()) {
            socket.setSoTimeout(500);
            byte[] out = search.getBytes(StandardCharsets.US_ASCII);
            DatagramPacket ask = new DatagramPacket(out, out.length, InetAddress.getByName("239.255.255.250"), 1900);
            byte[] in = new byte[2048];
            long start = System.currentTimeMillis();
            int asked = 0;
            // Asked three times over 3 seconds, listening for 6: a TV just switched on can miss one or answer late
            // (seen with a Roku TV).
            while (System.currentTimeMillis() - start < 6000) {
                if (asked < 3 && System.currentTimeMillis() - start >= asked * 1500L) {
                    socket.send(ask);
                    asked++;
                }
                DatagramPacket reply = new DatagramPacket(in, in.length);
                try {
                    socket.receive(reply);
                } catch (SocketTimeoutException e) {
                    continue;
                }
                Matcher m = Pattern.compile("(?i)LOCATION:\\s*http://([0-9.]+):8060")
                        .matcher(new String(reply.getData(), 0, reply.getLength(), StandardCharsets.US_ASCII));
                if (m.find() && !addresses.contains(m.group(1))) addresses.add(m.group(1));
            }
        } catch (Exception e) {
            Log.w(TAG, "Roku search failed: " + e);
        }
        return addresses;
    }

    /** Fire TVs by address, with the name each announces. */
    private static Map<String, String> fireTvs(Context context) {
        Map<String, String> found = new LinkedHashMap<>();
        NsdManager nsd = context.getSystemService(NsdManager.class);
        CountDownLatch done = new CountDownLatch(1);
        List<NsdServiceInfo> services = new ArrayList<>();
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onServiceFound(NsdServiceInfo info) {
                synchronized (services) {
                    services.add(info);
                }
            }
            @Override public void onStartDiscoveryFailed(String type, int error) {
                done.countDown();
            }
            @Override public void onDiscoveryStarted(String type) { }
            @Override public void onDiscoveryStopped(String type) { }
            @Override public void onServiceLost(NsdServiceInfo info) { }
            @Override public void onStopDiscoveryFailed(String type, int error) { }
        };
        try {
            nsd.discoverServices("_amzn-wplay._tcp", NsdManager.PROTOCOL_DNS_SD, listener);
            done.await(4, TimeUnit.SECONDS);
            nsd.stopServiceDiscovery(listener);
        } catch (Exception e) {
            Log.w(TAG, "Fire TV search failed: " + e);
        }
        List<NsdServiceInfo> copy;
        synchronized (services) {
            copy = new ArrayList<>(services);
        }
        for (NsdServiceInfo info : copy) {
            CountDownLatch resolved = new CountDownLatch(1);
            nsd.resolveService(info, new NsdManager.ResolveListener() {
                @Override public void onServiceResolved(NsdServiceInfo r) {
                    if (r.getHost() != null) found.put(r.getHost().getHostAddress(), r.getServiceName());
                    resolved.countDown();
                }
                @Override public void onResolveFailed(NsdServiceInfo r, int error) {
                    resolved.countDown();
                }
            });
            try {
                resolved.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
        return found;
    }

    private static String get(String url) {
        try {
            HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
            http.setConnectTimeout(1500);
            http.setReadTimeout(2000);
            try (java.io.InputStream in = http.getInputStream()) {
                return new String(Streams.readAll(in), StandardCharsets.UTF_8);
            } finally {
                http.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String tag(String xml, String name) {
        if (xml == null) return null;
        Matcher m = Pattern.compile("<" + name + ">([^<]*)</" + name + ">").matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }
}
