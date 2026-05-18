package com.example.smash_ride.features.main;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.constants.AppConstants;
import com.example.smash_ride.core.graphics.GifHardwareDecoder;
import com.example.smash_ride.core.graphics.SpriteColorizer;
import com.example.smash_ride.core.ui.BaseActivity;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.features.ranking.RankingActivity;
import com.example.smash_ride.notifications.NotificationScheduler;
import com.example.smash_ride.translation.LocaleUtils;
import com.example.smash_ride.translation.TranslationManager;
import com.example.smash_ride.features.settings.SettingsActivity;
import com.example.smash_ride.features.game.GameActivity;

/**
 * Actividad principal que sirve como menú de inicio del juego.
 * Gestiona la navegación, la selección del modo de juego y la inicialización de traducciones.
 */
public class MainActivity extends BaseActivity {

    private static final String TAG = "MainActivity";
    private TranslationManager translationManager;
    private PreferenceHelper prefHelper;
    private NotificationScheduler notificationScheduler;
    private String currentLang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefHelper = new PreferenceHelper(this);
        String currentId = prefHelper.getOrCreateId();

        Log.d("Auth_Setup", "ID de sesión activa: " + currentId);

        // 1. Obtener idioma y aplicar Locale ANTES de super.onCreate
        currentLang = prefHelper.getLanguage();
        Log.d(TAG, "onCreate: Idioma detectado en PreferenceHelper: " + currentLang);
        LocaleUtils.applyAppLocale(this, currentLang);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);

        // --- UX: Cargar GIF de fondo con el Decodificador abstraído ---
        ImageView gifBackground = findViewById(R.id.background_gif);
        if (gifBackground != null) {
            GifHardwareDecoder.loadGif(this, gifBackground, R.raw.background_stars);
        }

        notificationScheduler = new NotificationScheduler(this);
        notificationScheduler.requestPermissions(this, 2001);
        notificationScheduler.cancelPendingReminder();

        // 2. Inicializar sistema de traducción y UI
        initTranslation();
        setupUI();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SoundManager.getInstance().playMenuMusic(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SoundManager.getInstance().resumeMusic();

        if (!SoundManager.getInstance().isMusicPlaying()) {
            SoundManager.getInstance().playMenuMusic(this);
        }

        // Si el idioma ha cambiado mientras estábamos fuera (ej. en Settings)
        if (!prefHelper.getLanguage().equals(currentLang)) {
            Log.d(TAG, "onResume: Cambio de idioma detectado, recreando...");
            recreate();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SoundManager.getInstance().pauseMusic();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Evitar fugas de memoria del Singleton de traducción
        if (translationManager != null) {
            translationManager.unbindActivity();
        }
        notificationScheduler.scheduleReturnReminder(10);
    }

    // --- MÉTODOS DE INICIALIZACIÓN ---

    /**
     * Inicializa el sistema de traducción y registra las vistas del menú.
     */
    private void initTranslation() {
        Log.d(TAG, "--- INICIANDO SECUENCIA DE TRADUCCIÓN ---");
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);

        // Sincronizamos el Manager con el idioma de las preferencias
        translationManager.setTargetFromAppLang(currentLang);

        // Escaneamos la vista raíz para registro de textos
        View root = findViewById(android.R.id.content);

        // Registro de vistas en el Singleton
        translationManager.scanAndRegisterViews(root);

        // Aplicar la lógica de traducción (XML local o ML Kit)
        if (LocaleUtils.isNativeLanguage(currentLang)) {
            Log.d(TAG, "applyTranslationLogic: Usando recursos locales para [" + currentLang + "]");
            translationManager.reloadTextsFromResources();
        } else {
            Log.d(TAG, "applyTranslationLogic: No es nativo, invocando ML Kit para [" + currentLang + "]");
            translationManager.translateIfNeeded();
        }
    }

    /**
     * Configura los listeners de los botones y la UI del menú.
     */
    private void setupUI() {
        // Botón Jugar
        findViewById(R.id.start_button).setOnClickListener(v -> startGame());

        // Botón Ranking (Nuevo)
        findViewById(R.id.ranking_button).setOnClickListener(v -> startRanking());

        // Botón Ajustes
        findViewById(R.id.settings_button).setOnClickListener(v -> startSettings());

        // Selector de modo (Proximidad UX al botón Start)
        findViewById(R.id.mode_selector_circle).setOnClickListener(v -> showModeDialog());

        updateModeIcon();
    }

    // --- LÓGICA DE NAVEGACIÓN Y MODOS ---

    /**
     * Inicia la actividad del juego con el modo seleccionado.
     */
    private void startGame() {
        Intent intent = new Intent(this, GameActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra(AppConstants.EXTRA_GAME_MODE, prefHelper.getGameMode());
        intent.putExtra(AppConstants.EXTRA_OFFLINE, false);
        startActivity(intent);
    }

    /**
     * Navega a la pantalla de ranking global.
     */
    private void startRanking() {
        Intent intent = new Intent(this, RankingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    /**
     * Navega a la pantalla de ajustes.
     */
    private void startSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    /**
     * Muestra el diálogo para elegir entre el modo Vidas o Temporizador.
     */
    private void showModeDialog() {
        Log.d(TAG, "showModeDialog: Abriendo selector de modo");
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mode_selector, null);

        ImageView imgLives = dialogView.findViewById(R.id.img_lives);
        ImageView imgTimer = dialogView.findViewById(R.id.img_timer);
        SpriteColorizer.tintImageView(imgLives, Color.RED);
        SpriteColorizer.tintImageView(imgTimer, Color.BLACK);

        // --- ML KIT FIX: Registrar y traducir dinámicamente el diálogo ---
        translationManager.scanAndRegisterViews(dialogView);

        if (LocaleUtils.isNativeLanguage(currentLang)) {
            translationManager.reloadTextsFromResources();
        } else {
            translationManager.translateIfNeeded();
        }

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

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

    /**
     * Actualiza el icono del selector de modo según la preferencia guardada.
     */
    private void updateModeIcon() {
        ImageButton selector = findViewById(R.id.mode_selector_circle);
        if (selector == null) return;

        String mode = prefHelper.getGameMode();
        boolean isTimer = AppConstants.MODE_TIMER.equals(mode);

        selector.setImageResource(isTimer ? R.drawable.ic_clock : R.drawable.ic_heart);

        String desc = getString(isTimer ? R.string.mode_timer : R.string.mode_lives);
        Log.d(TAG, "updateModeIcon: " + mode + " | Desc: " + desc);
        selector.setContentDescription(desc);
    }
}