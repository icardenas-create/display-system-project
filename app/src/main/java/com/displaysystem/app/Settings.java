package com.displaysystem.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Saved settings. They survive restarts and unplugging the TV. */
public class Settings {
    public static final int[] SECONDS_STEPS = {0, 3, 5, 8, 10, 15, 20, 30, 45, 60, 90, 120, 300};
    public static final int[] ROTATIONS = {90, 270, 0, 180};

    private final SharedPreferences prefs;

    public Settings(Context context) {
        prefs = context.getSharedPreferences("display_settings", Context.MODE_PRIVATE);
    }

    /** Google Drive folder link or folder ID. */
    public String folder() { return prefs.getString("folder", ""); }
    public void setFolder(String v) { prefs.edit().putString("folder", v == null ? "" : v.trim()).apply(); }

    /** Seconds each image stays on screen. 0 means static: no automatic change. */
    public int seconds() { return prefs.getInt("seconds", 10); }
    public void setSeconds(int v) { prefs.edit().putInt("seconds", Math.max(0, v)).apply(); }

    /** Clockwise rotation of the picture in degrees: 0, 90, 180 or 270. */
    public int rotation() { return prefs.getInt("rotation", 90); }
    public void setRotation(int v) { prefs.edit().putInt("rotation", ((v % 360) + 360) % 360 / 90 * 90).apply(); }

    public static String rotationLabel(int r) {
        switch (r) {
            case 90: return "Portrait, TV logo on the right";
            case 270: return "Portrait, TV logo on the left";
            case 180: return "Landscape, upside down";
            default: return "Landscape";
        }
    }

    public static String secondsLabel(int s) {
        return s == 0 ? "Static (stays on one image)" : s + " seconds";
    }

    /** Next or previous value in a list, wrapping around at the ends. */
    public static int step(int[] steps, int current, int direction) {
        int index = 0;
        for (int i = 0; i < steps.length; i++) if (steps[i] == current) index = i;
        return steps[(index + direction + steps.length) % steps.length];
    }
}
