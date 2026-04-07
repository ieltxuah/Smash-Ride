package com.example.smash_ride.core.audio;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;
import com.example.smash_ride.R;
import com.example.smash_ride.data.local.PreferenceHelper;

public class SoundManager {
    private static final String TAG = "SoundManager";
    private static SoundManager instance;
    private MediaPlayer bgmPlayer;
    private int currentResource = -1;

    private SoundManager() {}

    public static synchronized SoundManager getInstance() {
        if (instance == null) instance = new SoundManager();
        return instance;
    }

    public void playMenuMusic(Context context) {
        playMusic(context, R.raw.bgm_menu); // Asegúrate que el archivo se llame bgm_menu.mp3
    }

    public void playGameMusic(Context context) {
        playMusic(context, R.raw.bgm_game); // Asegúrate que el archivo se llame bgm_game.mp3
    }

    private void playMusic(Context context, int resId) {
        if (currentResource == resId && bgmPlayer != null && bgmPlayer.isPlaying()) return;

        try {
            stopMusic();
            currentResource = resId;
            // IMPORTANTE: Usar getApplicationContext() para evitar fugas de memoria
            bgmPlayer = MediaPlayer.create(context.getApplicationContext(), resId);

            if (bgmPlayer == null) {
                Log.e(TAG, "Error: No se pudo crear el MediaPlayer para el recurso: " + resId);
                return;
            }

            bgmPlayer.setLooping(true);
            updateVolume(context);
            bgmPlayer.start();
            Log.d(TAG, "Música iniciada correctamente");
        } catch (Exception e) {
            Log.e(TAG, "Error al reproducir música: " + e.getMessage());
        }
    }

    public void updateVolume(Context context) {
        if (bgmPlayer == null) return;

        PreferenceHelper pref = new PreferenceHelper(context);
        int level = pref.getMusicVolume(); // 0, 1, 2, 3, 4

        // Android usa escala 0.0 a 1.0. Si level es 0, volumen es 0.
        // Si level es 4, volumen es 1.0.
        float vol = level * 0.25f;

        Log.d(TAG, "Cambiando volumen a nivel: " + level + " (float: " + vol + ")");
        bgmPlayer.setVolume(vol, vol);
    }

    public void stopMusic() {
        if (bgmPlayer != null) {
            if (bgmPlayer.isPlaying()) bgmPlayer.stop();
            bgmPlayer.release();
            bgmPlayer = null;
            currentResource = -1;
        }
    }

    public void pauseMusic() {
        if (bgmPlayer != null && bgmPlayer.isPlaying()) bgmPlayer.pause();
    }

    public void resumeMusic() {
        if (bgmPlayer != null && !bgmPlayer.isPlaying()) bgmPlayer.start();
    }
}