package io.github.expertlizard56.boxkeeper;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * Which TV this box is plugged into: its kind ("roku" or "fire"), its address, the TV's name for the box's input
 * (Roku "tvinput.hdmiN", Fire TV "HWn"), and what identifies the TV itself whatever its address (a Roku's serial
 * number, a Fire TV's network name; null until LinkService has learnt it), so a TV that moves is found again. Saved by TvFinder or the setup screen; until Nov 1 these were built into a
 * separate app per box. `adb shell settings put global tvlink_tv "<kind> <address> <input>"` sets it from the PC
 * (read once when nothing is saved yet).
 */
final class TvConfig {

    final String kind;
    final String address;
    final String input;
    final String id;

    TvConfig(String kind, String address, String input, String id) {
        this.kind = kind;
        this.address = address;
        this.input = input;
        this.id = id;
    }

    TvConfig withAddress(String newAddress, String newId) {
        return new TvConfig(kind, newAddress, input, newId);
    }

    static TvConfig load(Context context) {
        SharedPreferences prefs = prefs(context);
        String kind = prefs.getString("kind", null);
        if (kind != null) {
            return new TvConfig(kind, prefs.getString("address", ""), prefs.getString("input", ""), prefs.getString("id", null));
        }
        String given = Settings.Global.getString(context.getContentResolver(), "tvlink_tv");
        if (given != null) {
            String[] parts = given.trim().split("\\s+");
            if (parts.length == 3) {
                TvConfig config = new TvConfig(parts[0], parts[1], parts[2], null);
                config.save(context);
                return config;
            }
        }
        return null;
    }

    void save(Context context) {
        prefs(context).edit().putString("kind", kind).putString("address", address).putString("input", input)
                .putString("id", id).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("tv", Context.MODE_PRIVATE);
    }
}
