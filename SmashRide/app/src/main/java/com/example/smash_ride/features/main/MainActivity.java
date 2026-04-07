package com.example.smash_ride.features.main;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.constants.AppConstants; // Importante
import com.example.smash_ride.core.graphics.SpriteColorizer;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.notifications.NotificationScheduler;
import com.example.smash_ride.translation.LocaleUtils;
import com.example.smash_ride.translation.TranslationManager;
import com.example.smash_ride.features.settings.SettingsActivity;
import com.example.smash_ride.features.game.GameActivity;

public class MainActivity extends AppCompatActivity {

    private TranslationManager translationManager;
    private PreferenceHelper prefHelper;
    private NotificationScheduler notificationScheduler;
    private String currentLang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefHelper = new PreferenceHelper(this);

        // 1. Aplicar idioma y cargar configuración
        currentLang = prefHelper.getLanguage();
        LocaleUtils.applyAppLocale(this, currentLang);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);

        // 2. Notificaciones y permisos
        notificationScheduler = new NotificationScheduler(this);
        notificationScheduler.requestPermissions(this, 2001);
        notificationScheduler.cancelPendingReminder();

        // 3. Sistema de traducción y UI
        initTranslation();
        setupUI();
    }

    private void initTranslation() {
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);
        translationManager.setTargetFromAppLang(currentLang);
        translationManager.scanAndRegisterViews(findViewById(android.R.id.content));
        applyTranslationLogic();
    }

    private void applyTranslationLogic() {
        translationManager.reloadTextsFromResources();
        if (!isNativeLanguage(currentLang)) {
            translationManager.translateIfNeeded();
        }
    }

    private boolean isNativeLanguage(String lang) {
        return lang.equals("es") || lang.equals("eu") || lang.equals("en");
    }

    private void setupUI() {
        findViewById(R.id.start_button).setOnClickListener(v -> startGame());
        findViewById(R.id.settings_button).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.mode_selector_circle).setOnClickListener(v -> showModeDialog());

        // Aseguramos que el icono sea correcto según lo guardado
        updateModeIcon();
    }

    private void showModeDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mode_selector, null);

        // Estilizar iconos con SpriteColorizer
        ImageView imgLives = dialogView.findViewById(R.id.img_lives);
        ImageView imgTimer = dialogView.findViewById(R.id.img_timer);
        SpriteColorizer.tintImageView(imgLives, Color.RED);
        SpriteColorizer.tintImageView(imgTimer, Color.BLACK);

        translationManager.scanAndRegisterViews(dialogView);
        applyTranslationLogic();

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Selección de modo usando constantes para evitar errores
        dialogView.findViewById(R.id.option_lives).setOnClickListener(v -> {
            prefHelper.setGameMode(AppConstants.MODE_LIVES);
            updateModeIcon();
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.option_timer).setOnClickListener(v -> {
            prefHelper.setGameMode(AppConstants.MODE_TIMER);
            updateModeIcon();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateModeIcon() {
        ImageButton selector = findViewById(R.id.mode_selector_circle);
        if (selector == null) return;

        String mode = prefHelper.getGameMode();

        // Comprobación lógica basada en constantes
        boolean isTimer = AppConstants.MODE_TIMER.equals(mode);

        selector.setImageResource(isTimer ? R.drawable.ic_clock : R.drawable.ic_heart);
        selector.setContentDescription(getString(isTimer ? R.string.mode_timer : R.string.mode_lives));
    }

    private void startGame() {
        Intent intent = new Intent(this, GameActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

        // Pasamos el modo de juego exacto que tiene el PreferenceHelper
        intent.putExtra(AppConstants.EXTRA_GAME_MODE, prefHelper.getGameMode());
        intent.putExtra(AppConstants.EXTRA_OFFLINE, true);

        startActivity(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Al entrar a cualquier pantalla de menú, suena música de menú
        SoundManager.getInstance().playMenuMusic(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SoundManager.getInstance().playMenuMusic(this);
        // Si el idioma cambió en Settings, recreamos la actividad
        if (!prefHelper.getLanguage().equals(currentLang)) {
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        translationManager.unbindActivity();
        notificationScheduler.scheduleReturnReminder(10);
    }
}