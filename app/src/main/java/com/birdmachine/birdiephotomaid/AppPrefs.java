package com.birdmachine.birdiephotomaid;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String PREFS = "birdie_photo_maid";
    private static final String TOP = "dead_top";
    private static final String BOTTOM = "dead_bottom";
    private static final String LEFT = "dead_left";
    private static final String RIGHT = "dead_right";
    private static final String MASCOT = "mascot_presence";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int top() { return prefs.getInt(TOP, 0); }
    public int bottom() { return prefs.getInt(BOTTOM, 34); }
    public int left() { return prefs.getInt(LEFT, 0); }
    public int right() { return prefs.getInt(RIGHT, 0); }
    public int mascotPresence() { return prefs.getInt(MASCOT, 2); }

    public void setDeadZones(int top, int bottom, int left, int right) {
        prefs.edit().putInt(TOP, top).putInt(BOTTOM, bottom)
                .putInt(LEFT, left).putInt(RIGHT, right).apply();
    }

    public void setMascotPresence(int value) {
        prefs.edit().putInt(MASCOT, Math.max(0, Math.min(3, value))).apply();
    }
}
