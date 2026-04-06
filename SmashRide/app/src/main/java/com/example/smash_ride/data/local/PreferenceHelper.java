package com.example.smash_ride.data.local;

import static com.example.smash_ride.core.constants.AppConstants.*;
import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceHelper {
    private final SharedPreferences settingsPrefs;
    private final SharedPreferences modePrefs;

    public PreferenceHelper(Context context) {
        settingsPrefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        modePrefs = context.getSharedPreferences(PREFS_MODE, Context.MODE_PRIVATE);
    }

    public String getLanguage() { return settingsPrefs.getString(KEY_LANG, "en"); }

    public String getGameMode() { return modePrefs.getString(KEY_MODE, "LIVES"); }

    public void setGameMode(String mode) {
        modePrefs.edit().putString(KEY_MODE, mode).apply();
    }
}