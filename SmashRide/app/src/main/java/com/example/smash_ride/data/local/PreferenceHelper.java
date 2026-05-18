package com.example.smash_ride.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.smash_ride.core.constants.AppConstants;

/**
 * Clase de ayuda para gestionar las preferencias compartidas (SharedPreferences) de la aplicación.
 * Centraliza el acceso a la configuración del usuario, idioma, sonido y datos de sesión.
 */
public class PreferenceHelper {
    private final SharedPreferences prefs;

    /**
     * Constructor de la clase.
     *
     * @param context Contexto de la aplicación para acceder a las preferencias.
     */
    public PreferenceHelper(Context context) {
        // Usamos el nombre de archivo centralizado en AppConstants
        this.prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Configuración de Idioma ---

    /**
     * Obtiene el código del idioma actual del usuario.
     *
     * @return El código del idioma (ej: "es", "en") o el valor por defecto.
     */
    public String getLanguage() {
        return prefs.getString(AppConstants.KEY_LANG, AppConstants.DEFAULT_LANG);
    }

    /**
     * Guarda el código del idioma seleccionado.
     *
     * @param lang El código del idioma a guardar.
     */
    public void setLanguage(String lang) {
        prefs.edit().putString(AppConstants.KEY_LANG, lang).apply();
    }

    // --- Configuración de Audio ---

    /**
     * Obtiene el nivel de volumen de la música.
     *
     * @return Nivel de volumen (entero).
     */
    public int getMusicVolume() {
        return prefs.getInt(AppConstants.KEY_MUSIC_VOLUME, AppConstants.DEFAULT_VOLUME_LEVEL);
    }

    /**
     * Guarda el nivel de volumen de la música.
     *
     * @param level Nivel de volumen a establecer.
     */
    public void setMusicVolume(int level) {
        prefs.edit().putInt(AppConstants.KEY_MUSIC_VOLUME, level).apply();
    }

    /**
     * Obtiene el nivel de volumen de los efectos de sonido.
     *
     * @return Nivel de volumen (entero).
     */
    public int getEffectsVolume() {
        return prefs.getInt(AppConstants.KEY_EFFECTS_VOLUME, AppConstants.DEFAULT_VOLUME_LEVEL);
    }

    /**
     * Guarda el nivel de volumen de los efectos de sonido.
     *
     * @param level Nivel de volumen a establecer.
     */
    public void setEffectsVolume(int level) {
        prefs.edit().putInt(AppConstants.KEY_EFFECTS_VOLUME, level).apply();
    }

    // --- Configuración del Jugador ---

    /**
     * Obtiene la etiqueta del color del personaje seleccionado por el usuario.
     *
     * @return Etiqueta del color (ej: "rojo").
     */
    public String getCharacterColor() {
        return prefs.getString(AppConstants.KEY_CHARACTER_COLOR, "dorado");
    }

    /**
     * Guarda la etiqueta del color del personaje.
     *
     * @param color Etiqueta del color a guardar.
     */
    public void setCharacterColor(String color) {
        prefs.edit().putString(AppConstants.KEY_CHARACTER_COLOR, color).apply();
    }

    // --- Configuración del Modo de Juego ---

    /**
     * Obtiene el modo de juego seleccionado actualmente.
     *
     * @return Identificador del modo de juego (ej: "LIVES", "TIMER").
     */
    public String getGameMode() {
        return prefs.getString(AppConstants.KEY_GAME_MODE, AppConstants.MODE_LIVES);
    }

    /**
     * Guarda el modo de juego seleccionado.
     *
     * @param mode Identificador del modo de juego.
     */
    public void setGameMode(String mode) {
        prefs.edit().putString(AppConstants.KEY_GAME_MODE, mode).apply();
    }

    // --- Identificación de Usuario ---

    /**
     * Obtiene el ID único del usuario actual.
     *
     * @return El ID de usuario o null si no existe.
     */
    public String getUserId() {
        return prefs.getString("user_id", null);
    }

    /**
     * Guarda el ID único del usuario.
     *
     * @param id ID de usuario a guardar.
     */
    public void setUserId(String id) {
        prefs.edit().putString("user_id", id).apply();
    }

    /**
     * Obtiene el ID del usuario actual o crea uno nuevo de invitado si no existe.
     *
     * @return El ID de usuario existente o generado.
     */
    public String getOrCreateId() {
        String id = getUserId();
        if (id == null) {
            id = "Guest_" + System.currentTimeMillis();
            setUserId(id);
        }
        return id;
    }

    /**
     * Obtiene el nombre de pantalla del usuario.
     *
     * @return Nombre del usuario.
     */
    public String getUserName() {
        return prefs.getString("user_name", "Star_User");
    }

    /**
     * Guarda el nombre de pantalla del usuario.
     *
     * @param name Nombre de usuario a establecer.
     */
    public void setUserName(String name) {
        prefs.edit().putString("user_name", name).apply();
    }
}