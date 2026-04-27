package com.example.smash_ride.features.game;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.graphics.GifHardwareDecoder;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.features.main.MainActivity;
import com.example.smash_ride.translation.LocaleUtils;
import com.example.smash_ride.translation.TranslationManager;

public class LoseActivity extends AppCompatActivity {

    private TranslationManager translationManager;
    private String currentLang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Cargar idioma antes de inflar la vista
        PreferenceHelper prefHelper = new PreferenceHelper(this);
        currentLang = prefHelper.getLanguage();
        LocaleUtils.applyAppLocale(this, currentLang);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lose);

        // 2. Fondo Jerárquico (GIF -> Estático -> Negro)
        ImageView gifBg = findViewById(R.id.background_gif);
        if (gifBg != null) {
            GifHardwareDecoder.loadGif(this, gifBg, R.raw.background_stars);
        }

        // 3. Inicializar Traducción
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);
        initTranslation();

        // 4. Configurar Botón Volver
        Button back = findViewById(R.id.back_menu);
        back.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void initTranslation() {
        translationManager.setTargetFromAppLang(currentLang);
        View root = findViewById(android.R.id.content);
        translationManager.scanAndRegisterViews(root);

        if (currentLang.equals("es") || currentLang.equals("eu") || currentLang.equals("en")) {
            translationManager.reloadTextsFromResources();
        } else {
            translationManager.translateIfNeeded();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SoundManager.getInstance().resumeMusic();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SoundManager.getInstance().pauseMusic();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (translationManager != null) {
            translationManager.unbindActivity();
        }
    }
}