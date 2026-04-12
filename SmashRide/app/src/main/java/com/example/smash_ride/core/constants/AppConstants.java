package com.example.smash_ride.core.constants;

public class AppConstants {
    // Archivos de Preferencias
    public static final String PREFS_NAME = "smash_ride_prefs";
    public static final String PREFS_NOTIF = "notification_prefs"; // <-- FALTABA ESTA

    // Llaves de configuración
    public static final String KEY_LANG = "language";
    public static final String KEY_GAME_MODE = "selected_game_mode";
    public static final String KEY_CHARACTER_COLOR = "character_color";
    public static final String KEY_MUSIC_VOLUME = "music_volume";
    public static final String KEY_EFFECTS_VOLUME = "effects_volume";

    // Llaves de Notificaciones (WorkManager)
    public static final String KEY_WORK_ID = "work_id";

    // Modos de Juego
    public static final String MODE_LIVES = "LIVES";
    public static final String MODE_TIMER = "TIMER";

    // Intents
    public static final String EXTRA_GAME_MODE = "EXTRA_GAME_MODE";
    public static final String EXTRA_OFFLINE = "EXTRA_OFFLINE";

    // Valores por defecto
    public static final String DEFAULT_LANG = "en";
    public static final int DEFAULT_VOLUME_LEVEL = 2;

    // Carousel de colores de personaje
    public static final String[] CAROUSEL_COLORS = {
            "rojo", "verde", "azul", "dorado", "plata", "morado", "naranja"
    };

    public static final int[] CAROUSEL_HEX = {
            0xFFFF5252, // Rojo (Suave/Pastel para evitar saturación)
            0xFF2ECC71, // Verde (Esmeralda)
            0xFF2979FF, // Azul: Azul Eléctrico Puro (Corregido para evitar verde)
            0xFFFACD05, // Dorado (Amarillo Cálido)
            0xFFCFD8DC, // Plata: Gris Acero Azulado (Corregido para eliminar amarillo)
            0xFFB159FF, // Morado: Púrpura Vibrante (Corregido para evitar rojo)
            0xFFFF9100  // Naranja (Vibrante)
    };
}