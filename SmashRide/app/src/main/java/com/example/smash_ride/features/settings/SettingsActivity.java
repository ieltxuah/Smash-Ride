package com.example.smash_ride.features.settings;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.features.main.MainActivity;
import com.example.smash_ride.translation.LocaleUtils;
import com.example.smash_ride.translation.TranslationManager;

import java.util.ArrayList;
import java.util.Arrays;

public class SettingsActivity extends AppCompatActivity {

    private TranslationManager translationManager;
    private PreferenceHelper prefHelper;

    private Spinner langSpinner;
    private SeekBar musicSeekBar;
    private SeekBar effectsSeekBar;
    private RadioGroup colorGroup;

    private String[] labels;
    private String[] codes;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefHelper = new PreferenceHelper(this);

        // 1. Aplicar Localización antes de inflar
        LocaleUtils.applyAppLocale(this, prefHelper.getLanguage());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 2. Inicializar Traductor
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);
        translationManager.setTargetFromAppLang(prefHelper.getLanguage());

        // 3. Configurar UI
        initViews();
        loadSettings();
        setupTranslationListener();

        // Registrar vistas para traducción
        translationManager.scanAndRegisterViews(findViewById(android.R.id.content));
        translationManager.reloadTextsFromResources();
        translationManager.translateIfNeeded();
    }

    private void initViews() {
        langSpinner = findViewById(R.id.settings_language_spinner);
        musicSeekBar = findViewById(R.id.settings_music_seekbar); // ID sugerido en XML
        effectsSeekBar = findViewById(R.id.settings_effects_seekbar); // ID sugerido en XML
        colorGroup = findViewById(R.id.settings_color_group);
        Button saveButton = findViewById(R.id.settings_save_button);

        // Configurar SeekBars para 5 niveles (0 a 4)
        musicSeekBar.setMax(4);
        effectsSeekBar.setMax(4);

        // EVENTO PARA LA MÚSICA: Cambio en tiempo real
        musicSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Guardamos temporalmente en el helper
                    prefHelper.setMusicVolume(progress);
                    // Actualizamos el SoundManager para que el usuario oiga el cambio
                    SoundManager.getInstance().updateVolume(SettingsActivity.this);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // EVENTO PARA EFECTOS: Aquí podrías reproducir un "beep" de prueba
        effectsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    prefHelper.setEffectsVolume(progress);
                    // Aquí podrías llamar a un SoundManager.playTestSound()
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Configurar Spinner
        labels = getResources().getStringArray(R.array.supported_lang_labels);
        codes = getResources().getStringArray(R.array.supported_lang_codes);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(labels)));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        langSpinner.setAdapter(adapter);

        saveButton.setOnClickListener(v -> saveAndExit());
    }

    private void loadSettings() {
        // Cargar Idioma
        String curLang = prefHelper.getLanguage();
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(curLang)) {
                langSpinner.setSelection(i);
                break;
            }
        }

        // Cargar Niveles de Sonido (0-4)
        musicSeekBar.setProgress(prefHelper.getMusicVolume());
        effectsSeekBar.setProgress(prefHelper.getEffectsVolume());

        // Cargar Color
        String curColor = prefHelper.getCharacterColor();
        for (int i = 0; i < colorGroup.getChildCount(); i++) {
            View child = colorGroup.getChildAt(i);
            if (child instanceof RadioButton && curColor.equals(child.getTag())) {
                ((RadioButton) child).setChecked(true);
            }
        }
    }

    private void saveAndExit() {
        // 1. Obtener idioma
        String selLang = codes[langSpinner.getSelectedItemPosition()];

        // 2. Obtener Color
        String selColor = "red";
        int checkedId = colorGroup.getCheckedRadioButtonId();
        View checked = findViewById(checkedId);
        if (checked != null && checked.getTag() != null) {
            selColor = checked.getTag().toString();
        }

        // 3. Guardar todo mediante el Helper
        prefHelper.setLanguage(selLang);
        prefHelper.setMusicVolume(musicSeekBar.getProgress());
        prefHelper.setEffectsVolume(effectsSeekBar.getProgress());
        prefHelper.setCharacterColor(selColor);

        // 4. Aplicar cambios de sistema
        LocaleUtils.applyAppLocale(this, selLang);
        translationManager.setTargetFromAppLang(selLang);

        // 5. Reiniciar App para aplicar cambios globales
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void setupTranslationListener() {
        translationManager.setListener(new TranslationManager.TranslationListener() {
            @Override
            public void onModelDownloaded(String lang) { Log.d("Settings", "Model: " + lang); }

            @Override
            public void onTranslated(View v, String text) {}

            @Override
            public void onFailure(@NonNull Exception e) { Log.e("Settings", e.getMessage()); }

            @Override
            public void onRestoredFromResources() {
                runOnUiThread(() -> {
                    labels = getResources().getStringArray(R.array.supported_lang_labels);
                    adapter.clear();
                    adapter.addAll(Arrays.asList(labels));
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onNoTranslationNeeded() { onRestoredFromResources(); }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Al entrar a cualquier pantalla de menú, suena música de menú
        SoundManager.getInstance().playMenuMusic(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        translationManager.unbindActivity();
        translationManager.setListener(null);
    }
}