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
import androidx.appcompat.app.AppCompatActivity;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.constants.AppConstants;
import com.example.smash_ride.core.graphics.GifHardwareDecoder;
import com.example.smash_ride.core.graphics.SpriteColorizer;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.notifications.NotificationScheduler;
import com.example.smash_ride.translation.LocaleUtils;
import com.example.smash_ride.translation.TranslationManager;
import com.example.smash_ride.features.settings.SettingsActivity;
import com.example.smash_ride.features.game.GameActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DEBUG_TRANSLATION";
    private TranslationManager translationManager;
    private PreferenceHelper prefHelper;
    private NotificationScheduler notificationScheduler;
    private String currentLang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefHelper = new PreferenceHelper(this);

        // 1. Obtener idioma y aplicar Locale ANTES de super.onCreate
        currentLang = prefHelper.getLanguage();
        Log.d(TAG, "onCreate: Idioma detectado en PreferenceHelper: " + currentLang);
        LocaleUtils.applyAppLocale(this, currentLang);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);

        // --- UX: Cargar GIF de fondo con el Decodificador abstraído ---
        ImageView gifBackground = findViewById(R.id.background_gif);
        if (gifBackground != null) {
            GifHardwareDecoder.loadGif(this, gifBackground, R.drawable.background_stars);
        }

        notificationScheduler = new NotificationScheduler(this);
        notificationScheduler.requestPermissions(this, 2001);
        notificationScheduler.cancelPendingReminder();

        // 2. Inicializar sistema de traducción y UI
        initTranslation();
        setupUI();
    }

    private void initTranslation() {
        Log.d(TAG, "--- INICIANDO SECUENCIA DE TRADUCCIÓN ---");
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);

        // Sincronizamos el Manager con el idioma de las preferencias
        translationManager.setTargetFromAppLang(currentLang);

        // Escaneamos la vista raíz para registro de textos
        View root = findViewById(android.R.id.content);

        // Depuración de jerarquía
        debugViewHierarchy(root);

        // Registro de vistas en el Singleton
        translationManager.scanAndRegisterViews(root);

        // Aplicar la lógica de traducción (XML local o ML Kit)
        applyTranslationLogic();

        // Verificación final con delay
        root.postDelayed(this::verifyFinalTexts, 500);
    }

    private void applyTranslationLogic() {
        if (isNativeLanguage(currentLang)) {
            Log.d(TAG, "applyTranslationLogic: Usando recursos locales para [" + currentLang + "]");
            translationManager.reloadTextsFromResources();
        } else {
            Log.d(TAG, "applyTranslationLogic: No es nativo, invocando ML Kit para [" + currentLang + "]");
            translationManager.translateIfNeeded();
        }
    }

    private void verifyFinalTexts() {
        Log.d(TAG, "--- VERIFICACIÓN FINAL DE BOTONES ---");
        Button startBtn = findViewById(R.id.start_button);
        Button settingsBtn = findViewById(R.id.settings_button);
        Button rankingBtn = findViewById(R.id.ranking_button);

        if (startBtn != null)
            Log.d(TAG, "BOTÓN INICIO -> Texto actual: " + startBtn.getText());
        if (rankingBtn != null)
            Log.d(TAG, "BOTÓN RANKING -> Texto actual: " + rankingBtn.getText());
        if (settingsBtn != null)
            Log.d(TAG, "BOTÓN AJUSTES -> Texto actual: " + settingsBtn.getText());
    }

    private boolean isNativeLanguage(String lang) {
        return lang.equals("es") || lang.equals("eu") || lang.equals("en");
    }

    private void debugViewHierarchy(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                debugViewHierarchy(vg.getChildAt(i));
            }
        } else if (v instanceof TextView) {
            String idName = v.getId() != View.NO_ID ? getResources().getResourceEntryName(v.getId()) : "no-id";
            CharSequence text = ((TextView) v).getText();
            Log.d(TAG, "SCAN -> ID: " + idName + " | Tipo: " + v.getClass().getSimpleName() + " | Texto: " + text);
        }
    }

    private void setupUI() {
        // Botón Jugar
        findViewById(R.id.start_button).setOnClickListener(v -> startGame());

        // Botón Ranking (Nuevo)
        findViewById(R.id.ranking_button).setOnClickListener(v -> {
            Log.d(TAG, "Accediendo al Ranking...");
            // Aquí iría tu Intent: startActivity(new Intent(this, RankingActivity.class));
        });

        // Botón Ajustes
        findViewById(R.id.settings_button).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Selector de modo (Proximidad UX al botón Start)
        findViewById(R.id.mode_selector_circle).setOnClickListener(v -> showModeDialog());

        updateModeIcon();
    }

    private void showModeDialog() {
        Log.d(TAG, "showModeDialog: Abriendo selector de modo");
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mode_selector, null);

        ImageView imgLives = dialogView.findViewById(R.id.img_lives);
        ImageView imgTimer = dialogView.findViewById(R.id.img_timer);
        SpriteColorizer.tintImageView(imgLives, Color.RED);
        SpriteColorizer.tintImageView(imgTimer, Color.BLACK);

        // --- ML KIT FIX: Registrar y traducir dinámicamente el diálogo ---
        translationManager.scanAndRegisterViews(dialogView);

        if (isNativeLanguage(currentLang)) {
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

    private void startGame() {
        Intent intent = new Intent(this, GameActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra(AppConstants.EXTRA_GAME_MODE, prefHelper.getGameMode());
        intent.putExtra(AppConstants.EXTRA_OFFLINE, true);
        startActivity(intent);
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
}