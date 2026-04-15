package com.example.smash_ride;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Build;

import com.example.smash_ride.translation.LocaleUtils;
import com.example.smash_ride.translation.TranslationManager;

public class MyApplication extends Application {
    public static final String CHANNEL_ID = "reminder_channel";
    private static final String PREFS_SETTINGS = "app_settings";
    private static final String KEY_LANG = "language";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Apply saved locale app-wide before any Activity is created
        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        String lang = prefs.getString(KEY_LANG, "en");
        LocaleUtils.applyAppLocale(getApplicationContext(), lang);

        // Initialize TranslationManager singleton app-wide
        TranslationManager.initialize(getApplicationContext());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Recordatorio de juego";
            String description = "Canal para notificaciones de volver a jugar";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}

