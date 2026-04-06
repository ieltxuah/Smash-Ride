package com.example.smash_ride;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.smash_ride.translation.LocaleUtils;
import com.example.smash_ride.translation.TranslationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TranslationManager translationManager;
    private List<View> viewsToTranslate;
    private Map<Integer, String> originalTextsById;

    private String selectedLangPrefCode; // e.g. "en","es","eu"

    private static final String PREFS = "notification_prefs";
    private static final String KEY_WORK_ID = "work_id";
    private static final String PREFS_MODE = "game_mode_prefs";
    private static final String KEY_MODE = "selected_mode";

    private static final String PREFS_SETTINGS = "app_settings";
    private static final String KEY_LANG = "language";

    private static final int REQUEST_CODE_POST_NOTIF = 2001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Es vital aplicar el locale ANTES del super.onCreate y del setContentView
        SharedPreferences prefsSettings = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        selectedLangPrefCode = prefsSettings.getString(KEY_LANG, "en");
        LocaleUtils.applyAppLocale(this, selectedLangPrefCode);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);

        // normal setup
        ensureDefaultMode();
        requestPostNotificationsIfNeeded();
        cancelPendingReminderIfAny();
        initializeVariables();
        setupUI();
        setupModeSelector();
        updateModeSelectorIcon();

        // Setup persistent TranslationManager (must be initialized in Application)
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);
        translationManager.setTargetFromAppLang(selectedLangPrefCode);

        translationManager.scanAndRegisterViews(findViewById(android.R.id.content));

        // IMPORTANTE: Si el idioma es uno de tus locales (es, eu),
        // dile al manager que simplemente recargue desde recursos y NO use ML Kit.
        if (selectedLangPrefCode.equals("es") || selectedLangPrefCode.equals("eu") || selectedLangPrefCode.equals("en")) {
            translationManager.reloadTextsFromResources();
            // Aquí no llames a translateIfNeeded() si el manager no distingue entre local/remoto
        } else {
            translationManager.reloadTextsFromResources();
            translationManager.translateIfNeeded();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        String newPref = prefs.getString(KEY_LANG, "en");

        if (!newPref.equals(selectedLangPrefCode)) {
            selectedLangPrefCode = newPref;

            // 1. Aplicar el locale globalmente
            LocaleUtils.applyAppLocale(this, selectedLangPrefCode);

            // 2. Notificar al manager para que NO use ML Kit si es un idioma local
            // Suponiendo que tu TranslationManager tiene lógica para detectar esto:
            if (translationManager != null) {
                translationManager.setTargetFromAppLang(selectedLangPrefCode);
            }

            // 3. Recrear
            recreate();
            return;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (translationManager != null) {
            translationManager.unbindActivity();
        }
        scheduleReturnReminder();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_POST_NOTIF) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS granted");
            } else {
                Log.d(TAG, "POST_NOTIFICATIONS denied");
            }
        }
    }

    private void initializeVariables() {
        viewsToTranslate = new ArrayList<>();
        originalTextsById = new HashMap<>();
    }

    private void setupUI() {
        Button startButton = findViewById(R.id.start_button);
        if (startButton != null) {
            startButton.setOnClickListener(v -> startGame());
        }

        Button settingsButton = findViewById(R.id.settings_button);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(i);
            });
        }

        View vTitle = findViewById(R.id.title);
        if (vTitle != null) viewsToTranslate.add(vTitle);
        if (startButton != null) viewsToTranslate.add(startButton);
        if (settingsButton != null) viewsToTranslate.add(settingsButton);

        for (View view : viewsToTranslate) {
            String eng = LocaleUtils.getEnglishTextForView(this, view);
            if (eng == null) eng = "";
            originalTextsById.put(view.getId(), eng);
            if (translationManager != null) translationManager.registerViews(view);
        }
    }

    private void setupModeSelector() {
        ImageButton selector = findViewById(R.id.mode_selector_circle);
        if (selector == null) return;

        selector.setOnClickListener(v -> {
            // 1. Inflar el layout personalizado
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_mode_selector, null);

            // 2. Aplicar colores a los iconos (SpriteColorizer)
            ImageView imgLives = dialogView.findViewById(R.id.img_lives);
            ImageView imgTimer = dialogView.findViewById(R.id.img_timer);
            imgLives.setColorFilter(android.graphics.Color.RED, android.graphics.PorterDuff.Mode.SRC_IN);
            imgTimer.setColorFilter(android.graphics.Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN);

            // 3. Crear el Diálogo
            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create();

            // 4. Hacer el fondo redondeado (Transparente para ver el drawable)
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }

            // --- LÓGICA DE TRADUCCIÓN DEL SELECTOR ---
            if (translationManager != null) {
                // Registramos las vistas del diálogo (los TextViews de "Vidas" y "Tiempo")
                translationManager.scanAndRegisterViews(dialogView);

                if (selectedLangPrefCode.equals("es") || selectedLangPrefCode.equals("eu") || selectedLangPrefCode.equals("en")) {
                    // Si es un idioma local, forzamos a que lean del strings.xml directamente
                    // Esto corrige que no se queden en inglés por error
                    translationManager.reloadTextsFromResources();
                } else {
                    // Si es un idioma extranjero (ej. Francés), pedimos a ML Kit que traduzca
                    translationManager.translateIfNeeded();
                }
            }

            // 5. Eventos de clic para guardar el modo
            dialogView.findViewById(R.id.option_lives).setOnClickListener(v1 -> {
                saveGameMode("LIVES");
                dialog.dismiss();
            });

            dialogView.findViewById(R.id.option_timer).setOnClickListener(v2 -> {
                saveGameMode("TIMER");
                dialog.dismiss();
            });

            dialog.show();
        });
    }

    private void saveGameMode(String mode) {
        getSharedPreferences(PREFS_MODE, MODE_PRIVATE).edit().putString(KEY_MODE, mode).apply();
        updateModeSelectorIcon(); // Actualiza el botón principal
    }

    private void updateModeSelectorIcon() {
        ImageButton selector = findViewById(R.id.mode_selector_circle);
        if (selector == null) return;
        String current = getSharedPreferences(PREFS_MODE, MODE_PRIVATE).getString(KEY_MODE, "LIVES");
        if ("TIMER".equals(current)) {
            selector.setImageResource(R.drawable.ic_clock);
            selector.setContentDescription(getString(R.string.mode_timer));
        } else {
            selector.setImageResource(R.drawable.ic_heart);
            selector.setContentDescription(getString(R.string.mode_lives));
        }
    }

    private void startGame() {
        Intent intent = new Intent(this, GameActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        String mode = getSharedPreferences(PREFS_MODE, MODE_PRIVATE).getString(KEY_MODE, "LIVES");
        intent.putExtra("GAME_MODE", mode);
        intent.putExtra("OFFLINE", true);
        startActivity(intent);
    }

    private void scheduleReturnReminder() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                .setInitialDelay(10, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(this).enqueue(work);

        UUID id = work.getId();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_WORK_ID, id.toString()).apply();
    }

    private void cancelPendingReminderIfAny() {
        String workIdString = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_WORK_ID, null);
        if (workIdString != null) {
            try {
                java.util.UUID workId = java.util.UUID.fromString(workIdString);
                WorkManager.getInstance(this).cancelWorkById(workId);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_WORK_ID).apply();
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void ensureDefaultMode() {
        if (!getSharedPreferences(PREFS_MODE, MODE_PRIVATE).contains(KEY_MODE)) {
            getSharedPreferences(PREFS_MODE, MODE_PRIVATE).edit().putString(KEY_MODE, "LIVES").apply();
        }
    }

    private void requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_POST_NOTIF);
            }
        }
    }
}
