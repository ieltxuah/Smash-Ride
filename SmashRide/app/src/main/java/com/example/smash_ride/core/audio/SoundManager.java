package com.example.smash_ride.core.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.Log;
import com.example.smash_ride.R;
import com.example.smash_ride.data.local.PreferenceHelper;

public class SoundManager {
    private static final String TAG = "SoundManager";
    private static SoundManager instance;
    private MediaPlayer bgmPlayer;
    private int currentResource = -1;
    private boolean isPausedBySystem = false;

    private SoundPool soundPool;
    private int collisionSoundId;
    private float effectsVolume = 1.0f;

    private SoundManager() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(5) // Hasta 5 sonidos a la vez
                .setAudioAttributes(attrs)
                .build();
    }

    public static synchronized SoundManager getInstance() {
        if (instance == null) instance = new SoundManager();
        return instance;
    }

    public void playMenuMusic(Context context) {
        // bgm_menu es la misma para Main y Settings, así que fluirá sin cortes
        playMusic(context, R.raw.bgm_menu);
    }

    public void playGameMusic(Context context) {
        playMusic(context, R.raw.bgm_game);
    }

    private void playMusic(Context context, int resId) {
        // --- CLAVE PARA LA FLUIDEZ ---
        // Si ya tenemos cargado el mismo recurso:
        if (currentResource == resId && bgmPlayer != null) {
            if (!bgmPlayer.isPlaying()) {
                bgmPlayer.start(); // Simplemente reanudamos si estaba pausado
                Log.d(TAG, "Música reanudada (mismo recurso)");
                isPausedBySystem = false;
            }
            return; // Salimos sin destruir nada para que no haya micro-cortes
        }

        try {
            // Solo destruimos el anterior si el recurso es REALMENTE diferente (ej: pasar de menú a juego)
            stopMusic();

            currentResource = resId;
            bgmPlayer = MediaPlayer.create(context.getApplicationContext(), resId);

            if (bgmPlayer == null) {
                Log.e(TAG, "Error: No se pudo crear el MediaPlayer");
                return;
            }

            bgmPlayer.setLooping(true);
            updateVolume(context);
            bgmPlayer.start();
            Log.d(TAG, "Nueva música iniciada: " + resId);
        } catch (Exception e) {
            Log.e(TAG, "Error al reproducir música: " + e.getMessage());
        }
    }

    public void loadGameSounds(Context context) {
        // Asegúrate de tener un archivo llamado 'collision.mp3' o 'collision.wav' en res/raw
        collisionSoundId = soundPool.load(context.getApplicationContext(), R.raw.collision, 1);
        updateVolume(context);
    }

    public void playCollisionSound() {
        if (collisionSoundId != 0) {
            // Reproducir con el volumen de efectos guardado
            soundPool.play(collisionSoundId, effectsVolume, effectsVolume, 1, 0, 1.0f);
        }
    }

    public void updateVolume(Context context) {
        PreferenceHelper pref = new PreferenceHelper(context);

        // Volumen Música
        float musicVol = pref.getMusicVolume() * 0.25f;
        if (bgmPlayer != null) bgmPlayer.setVolume(musicVol, musicVol);

        // Volumen Efectos (Nuevo)
        this.effectsVolume = pref.getEffectsVolume() * 0.25f;
    }

    public void stopMusic() {
        if (bgmPlayer != null) {
            try {
                if (bgmPlayer.isPlaying()) bgmPlayer.stop();
                bgmPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error al liberar MediaPlayer: " + e.getMessage());
            }
            bgmPlayer = null;
            currentResource = -1;
        }
    }

    public void pauseMusic() {
        if (bgmPlayer != null && bgmPlayer.isPlaying()) {
            bgmPlayer.pause();
            isPausedBySystem = true;
        }
    }

    public void resumeMusic() {
        // Solo reanudamos si hay algo cargado y no está sonando
        if (bgmPlayer != null && !bgmPlayer.isPlaying() && isPausedBySystem) {
            bgmPlayer.start();
            isPausedBySystem = false;
        }
    }

    public boolean isMusicPlaying() {
        try {
            return bgmPlayer != null && bgmPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }
}