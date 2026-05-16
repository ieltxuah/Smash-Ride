package com.example.smash_ride.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.smash_ride.core.constants.AppConstants;

public class PreferenceHelper {
    private final SharedPreferences prefs;

    public PreferenceHelper(Context context) {
        // Usamos el nombre de archivo centralizado en AppConstants
        this.prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getLanguage() {
        return prefs.getString(AppConstants.KEY_LANG, AppConstants.DEFAULT_LANG);
    }

    public void setLanguage(String lang) {
        prefs.edit().putString(AppConstants.KEY_LANG, lang).apply();
    }

    public int getMusicVolume() {
        return prefs.getInt(AppConstants.KEY_MUSIC_VOLUME, AppConstants.DEFAULT_VOLUME_LEVEL);
    }

    public void setMusicVolume(int level) {
        prefs.edit().putInt(AppConstants.KEY_MUSIC_VOLUME, level).apply();
    }

    public int getEffectsVolume() {
        return prefs.getInt(AppConstants.KEY_EFFECTS_VOLUME, AppConstants.DEFAULT_VOLUME_LEVEL);
    }

    public void setEffectsVolume(int level) {
        prefs.edit().putInt(AppConstants.KEY_EFFECTS_VOLUME, level).apply();
    }

    public String getCharacterColor() {
        return prefs.getString(AppConstants.KEY_CHARACTER_COLOR, "red");
    }

    public void setCharacterColor(String color) {
        prefs.edit().putString(AppConstants.KEY_CHARACTER_COLOR, color).apply();
    }

    public String getGameMode() {
        return prefs.getString(AppConstants.KEY_GAME_MODE, AppConstants.MODE_LIVES);
    }

    public void setGameMode(String mode) {
        prefs.edit().putString(AppConstants.KEY_GAME_MODE, mode).apply();
    }

    public String getUserId() {
        return prefs.getString("user_id", null);
    }

    public String getOrCreateId() {
        String id = getUserId();
        if (id == null) {
            id = "Guest_" + System.currentTimeMillis();
            setUserId(id);
        }
        return id;
    }

    public void setUserId(String id) {
        prefs.edit().putString("user_id", id).apply();
    }

    public String getUserName() {
        return prefs.getString("user_name", "Star_User");
    }

    public void setUserName(String name) {
        prefs.edit().putString("user_name", name).apply();
    }
}