package io.github.expertlizard56.streamingboxkeeper;

import android.content.Context;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Disabled remote buttons (LinkService takes them before the box sees them): an app button for an app that
 * isn't installed opens the Play Store instead, say. Added on the app's screen by pressing them (BlockedKeysActivity);
 * the buttons InputKey.refusal names can't be, nor the Input button. Kept as a comma list in the "keeper" settings.
 */
final class BlockedKeys {

    private static final String PREF = "blockedKeys";
    /** Read once; null until read. */
    private static volatile Set<Integer> keys;

    private BlockedKeys() {
    }

    static Set<Integer> all(Context context) {
        Set<Integer> k = keys;
        if (k == null) {
            k = parse(context.getSharedPreferences("keeper", Context.MODE_PRIVATE).getString(PREF, ""));
            keys = k;
        }
        return k;
    }

    static boolean is(Context context, int keyCode) {
        return all(context).contains(keyCode);
    }

    static void add(Context context, int keyCode) {
        Set<Integer> k = new LinkedHashSet<>(all(context));
        k.add(keyCode);
        save(context, k);
    }

    static void remove(Context context, int keyCode) {
        Set<Integer> k = new LinkedHashSet<>(all(context));
        k.remove(keyCode);
        save(context, k);
    }

    private static void save(Context context, Set<Integer> k) {
        context.getSharedPreferences("keeper", Context.MODE_PRIVATE).edit().putString(PREF, join(k)).apply();
        keys = k;
    }

    /** "131,132" to its key codes; anything not a positive number is skipped. */
    static Set<Integer> parse(String text) {
        Set<Integer> k = new LinkedHashSet<>();
        for (String part : text.split(",")) {
            try {
                int code = Integer.parseInt(part.trim());
                if (code > 0) k.add(code);
            } catch (NumberFormatException ignored) {
                // an empty or damaged entry
            }
        }
        return k;
    }

    static String join(Set<Integer> k) {
        StringBuilder s = new StringBuilder();
        for (int code : k) s.append(s.length() == 0 ? "" : ",").append(code);
        return s.toString();
    }

    /** The buttons in words, for the main screen: "None." or "Button 9, Button 10." */
    static String describe(Context context) {
        Set<Integer> k = all(context);
        if (k.isEmpty()) return "None.";
        StringBuilder s = new StringBuilder();
        for (int code : k) s.append(s.length() == 0 ? "" : ", ").append(InputKey.name(code));
        return s.append('.').toString();
    }
}
