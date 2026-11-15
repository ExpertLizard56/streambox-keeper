package io.github.expertlizard56.streamingboxkeeper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * The decision half of Keep my settings (KeepSettings does the reading and writing): plain Java, so it's unit tested.
 *
 * The box's own values are what's kept; nothing is built in. Each check reads the firmware's fingerprint
 * (ro.build.fingerprint) and the watched settings:
 *  - same fingerprint as remembered (or nothing remembered yet): the values read now become the remembered ones, since
 *    a change made on the box is the person's own choice;
 *  - a different fingerprint (a firmware update): every watched setting that differs from what's remembered gets the
 *    remembered value back, then the new fingerprint is remembered.
 * A setting that couldn't be read (null) is never put back, never remembered over and never written blank.
 *
 * The apps the person turned off (APPS_OFF, "pm disable-user") are kept the same way, as a set: after a firmware
 * update, any remembered one that's on again is turned off again; one the person turns back on stays on. Apps the
 * firmware itself keeps off (enabled=2) aren't the person's choice and aren't remembered.
 */
final class KeptSettings {

    /** settings secure match_content_frame_rate */
    static final String FRAME_RATE = "frameRate";
    /** settings global hdr_conversion_mode: only read at boot, so a put-back value takes effect at the next restart. */
    static final String HDR = "hdr";
    /** settings secure screensaver_components */
    static final String SCREENSAVER = "screensaver";
    /** The Home app (cmd package resolve-activity for the HOME intent). */
    static final String HOME = "home";
    /** StreamBox Keeper's own TV remote in enabled_accessibility_services: "on" or "off"; other services aren't ours. */
    static final String REMOTE_HELPER = "remoteHelper";
    /** The apps the person turned off (enabled=3 for user 0), comma-separated and sorted; "" for none. */
    static final String APPS_OFF = "appsOff";
    static final String[] ALL = {FRAME_RATE, HDR, SCREENSAVER, HOME, REMOTE_HELPER, APPS_OFF};

    private KeptSettings() {
    }

    /** What one check decided. */
    static final class Decision {
        /** The values to remember from now on. */
        final Map<String, String> remember;
        /** The settings to put back, with their remembered values; empty unless the firmware changed. */
        final Map<String, String> putBack;
        /** The fingerprint to remember. */
        final String fingerprint;

        Decision(Map<String, String> remember, Map<String, String> putBack, String fingerprint) {
            this.remember = remember;
            this.putBack = putBack;
            this.fingerprint = fingerprint;
        }
    }

    /**
     * Compares what's remembered with what was read now. Returns null when the fingerprint couldn't be read: without
     * it a firmware update can't be told from the person's own change, so nothing is done.
     */
    static Decision decide(String rememberedFingerprint, Map<String, String> remembered, String fingerprint,
            Map<String, String> now) {
        if (fingerprint == null || fingerprint.isEmpty()) return null;
        Map<String, String> remember = new LinkedHashMap<>();
        Map<String, String> putBack = new LinkedHashMap<>();
        boolean firmwareChanged = rememberedFingerprint != null && !rememberedFingerprint.equals(fingerprint);
        for (String key : ALL) {
            String was = remembered.get(key);
            String is = now.get(key);
            if (is == null) {
                if (was != null) remember.put(key, was);  // unreadable now: keep what we knew, write nothing
                continue;
            }
            if (APPS_OFF.equals(key)) {
                TreeSet<String> back = set(was);
                back.removeAll(set(is));
                if (firmwareChanged && !back.isEmpty()) {
                    putBack.put(key, join(back));
                    // Until KeepSettings knows which went off again (an uninstalled one can't), keep them all.
                    TreeSet<String> all = set(is);
                    all.addAll(back);
                    remember.put(key, join(all));
                } else {
                    remember.put(key, is);
                }
                continue;
            }
            if (firmwareChanged && was != null && !was.equals(is)) {
                putBack.put(key, was);
                remember.put(key, was);
            } else {
                remember.put(key, is);
            }
        }
        return new Decision(remember, putBack, fingerprint);
    }

    /** A comma-separated list as a sorted set; empty for null or "". */
    static TreeSet<String> set(String list) {
        TreeSet<String> out = new TreeSet<>();
        if (list == null) return out;
        for (String p : list.split(",")) {
            if (!p.trim().isEmpty()) out.add(p.trim());
        }
        return out;
    }

    static String join(Iterable<String> names) {
        return String.join(",", names);
    }

    /** "on" when the accessibility list has this app's own service in it, else "off"; null when the list wasn't read. */
    static String helperState(String services, String pkg) {
        if (services == null) return null;
        for (String s : services.split(":")) {
            if (s.trim().startsWith(pkg + "/")) return "on";
        }
        return "off";
    }

    /**
     * The accessibility list with this app's own service added (on) or taken out (off), every other service kept as
     * it was, in its place.
     */
    static String withHelper(String services, String pkg, String service, boolean on) {
        StringBuilder out = new StringBuilder();
        boolean had = false;
        for (String s : (services == null ? "" : services).split(":")) {
            String t = s.trim();
            if (t.isEmpty() || "null".equals(t)) continue;
            if (t.startsWith(pkg + "/")) {
                if (!on || had) continue;
                had = true;
            }
            if (out.length() > 0) out.append(':');
            out.append(t);
        }
        if (on && !had) {
            if (out.length() > 0) out.append(':');
            out.append(service);
        }
        return out.toString();
    }

    /**
     * Reads KeepSettings' one shell call: lines "fp=", "frame=", "hdr=", "saver=", "a11y=", "home=", "off=". Gives the
     * fingerprint under "fp" and the watched settings by their keys; a value that's empty or "null" (unset, or the read
     * failed) is left out, so it's never written back blank.
     */
    static Map<String, String> parse(String out, String pkg) {
        Map<String, String> raw = new LinkedHashMap<>();
        for (String line : (out == null ? "" : out).split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) raw.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        Map<String, String> found = new LinkedHashMap<>();
        put(found, "fp", raw.get("fp"));
        put(found, FRAME_RATE, raw.get("frame"));
        put(found, HDR, raw.get("hdr"));
        put(found, SCREENSAVER, raw.get("saver"));
        String home = raw.get("home");
        // No Home chosen yet (the system's chooser), or no component at all: nothing to keep.
        if (home != null && home.contains("/") && !home.contains("ResolverActivity") && !home.contains(" ")) {
            found.put(HOME, home);
        }
        // An empty list reads as "null": that's a real answer (no services on), unlike a line that's missing.
        String a11y = raw.get("a11y");
        if (a11y != null) found.put(REMOTE_HELPER, helperState("null".equals(a11y) ? "" : a11y, pkg));
        // An empty "off=" is a real answer (none turned off); no line at all means the app list wasn't read.
        String off = raw.get("off");
        if (off != null) found.put(APPS_OFF, join(set(off)));
        return found;
    }

    /** The remembered value as the screen shows it: the apps turned off as a count, the rest as they are. */
    static String shown(String key, String value) {
        if (!APPS_OFF.equals(key)) return value;
        int n = set(value).size();
        return n == 0 ? "none" : n + (n == 1 ? " app" : " apps");
    }

    private static void put(Map<String, String> found, String key, String value) {
        if (value != null && !value.isEmpty() && !"null".equals(value)) found.put(key, value);
    }

    /** The setting's name in words, for the screen. */
    static String label(String key) {
        switch (key) {
            case FRAME_RATE: return "Match frame rate";
            case HDR: return "HDR output";
            case SCREENSAVER: return "Screen saver";
            case HOME: return "Home screen";
            case REMOTE_HELPER: return "TV remote helper";
            case APPS_OFF: return "Apps you turned off";
            default: return key;
        }
    }
}
