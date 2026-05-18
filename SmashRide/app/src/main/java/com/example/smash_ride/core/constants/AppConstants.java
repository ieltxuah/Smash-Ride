package com.example.smash_ride.core.constants;

/**
 * Clase que contiene las constantes globales de la aplicación.
 * Incluye nombres de preferencias, llaves de configuración, identificadores de intents y valores por defecto.
 */
public class AppConstants {
    /// --- Archivos de Preferencias ---
    /** Nombre del archivo de SharedPreferences principal. */
    public static final String PREFS_NAME = "smash_ride_prefs";
    /** Nombre del archivo de SharedPreferences para notificaciones. */
    public static final String PREFS_NOTIF = "notification_prefs";

    // --- Llaves de Configuración ---
    /** Llave para el idioma seleccionado. */
    public static final String KEY_LANG = "language";
    /** Llave para el modo de juego seleccionado. */
    public static final String KEY_GAME_MODE = "selected_game_mode";
    /** Llave para el color del personaje del usuario. */
    public static final String KEY_CHARACTER_COLOR = "character_color";
    /** Llave para el nivel de volumen de la música. */
    public static final String KEY_MUSIC_VOLUME = "music_volume";
    /** Llave para el nivel de volumen de los efectos. */
    public static final String KEY_EFFECTS_VOLUME = "effects_volume";

    // --- Llaves de Notificaciones (WorkManager) ---
    /** Llave para almacenar el ID del trabajo de notificación pendiente. */
    public static final String KEY_WORK_ID = "work_id";

    // --- Modos de Juego ---
    /** Identificador del modo de juego por vidas. */
    public static final String MODE_LIVES = "LIVES";
    /** Identificador del modo de juego por tiempo. */
    public static final String MODE_TIMER = "TIMER";

    // --- Intents ---
    /** Extra para pasar el modo de juego entre actividades. */
    public static final String EXTRA_GAME_MODE = "EXTRA_GAME_MODE";
    /** Extra para indicar si la partida es local (offline). */
    public static final String EXTRA_OFFLINE = "EXTRA_OFFLINE";

    // --- Valores por Defecto ---
    /** Idioma por defecto de la aplicación. */
    public static final String DEFAULT_LANG = "en";
    /** Nivel de volumen por defecto (0-4). */
    public static final int DEFAULT_VOLUME_LEVEL = 2;

    // --- Carrusel de Colores de Personaje ---
    /** Etiquetas de los colores disponibles para el personaje. */
    public static final String[] CAROUSEL_COLORS = {
            "rojo", "verde", "azul", "dorado", "plata", "morado", "naranja"
    };

    /** Valores hexadecimales de los colores del carrusel. */
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