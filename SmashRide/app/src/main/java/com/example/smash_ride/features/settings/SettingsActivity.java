package com.example.smash_ride.features.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smash_ride.R;
import com.example.smash_ride.features.main.MainActivity;
import com.example.smash_ride.translation.LocaleUtils;
import com.example.smash_ride.translation.TranslationManager;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_SETTINGS = "app_settings";
    private static final String KEY_LANG = "language";
    private static final String KEY_SOUND = "sound_enabled";
    private static final String KEY_COLOR = "character_color";

    private TranslationManager translationManager;
    private Spinner langSpinner;
    private String[] labels;
    private String[] codes;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved locale BEFORE inflating views
        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        String lang = prefs.getString(KEY_LANG, "en");
        LocaleUtils.applyAppLocale(this, lang);

        super.onCreate(savedInstanceState);

        // Ensure TranslationManager singleton already initialized in Application.onCreate()
        translationManager = TranslationManager.getInstance();

        // bind activity early so updates run on UI thread correctly
        translationManager.bindActivity(this);

        // set target language on manager before inflating and registering views
        translationManager.setTargetFromAppLang(lang);

        setContentView(R.layout.activity_settings);

        langSpinner = findViewById(R.id.settings_language_spinner);

        labels = getResources().getStringArray(R.array.supported_lang_labels);
        codes = getResources().getStringArray(R.array.supported_lang_codes);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<String>());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        langSpinner.setAdapter(adapter);

        // populate adapter from resources (will be updated after translation)
        populateLangSpinnerAdapter();

        SharedPreferences prefs2 = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        String curLangCode = prefs2.getString(KEY_LANG, "en");

        int pos = 0;
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(curLangCode)) {
                pos = i;
                break;
            }
        }
        langSpinner.setSelection(pos);

        Switch soundSwitch = findViewById(R.id.settings_sound_switch);
        soundSwitch.setChecked(prefs2.getBoolean(KEY_SOUND, true));

        RadioGroup colorGroup = findViewById(R.id.settings_color_group);
        String curColor = prefs2.getString(KEY_COLOR, "red");
        for (int i = 0; i < colorGroup.getChildCount(); i++) {
            View child = colorGroup.getChildAt(i);
            if (child instanceof RadioButton) {
                RadioButton rb = (RadioButton) child;
                Object tag = rb.getTag();
                if (tag != null && tag.equals(curColor)) {
                    rb.setChecked(true);
                    break;
                }
            }
        }

        Button save = findViewById(R.id.settings_save_button);

        // Register views for translation AFTER content view is set and manager target set
        translationManager.scanAndRegisterViews(findViewById(android.R.id.content));

        // Add listener for debug/updates (recreate adapter when translations arrive)
        translationManager.setListener(new TranslationManager.TranslationListener() {
            @Override
            public void onModelDownloaded(String targetLanguage) {
                Log.d("SettingsActivity", "Model downloaded: " + targetLanguage);
            }

            @Override
            public void onTranslated(View view, String translatedText) {
                // if spinner items were translated we will refresh adapter here
                if (view == langSpinner) {
                    // not expected; spinner root rarely passed - safe no-op
                }
            }

            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e("SettingsActivity", "Translation failure: " + e.getMessage());
            }

            @Override
            public void onRestoredFromResources() {
                // update adapter texts from resources (localized)
                runOnUiThread(() -> populateLangSpinnerAdapter());
            }

            @Override
            public void onNoTranslationNeeded() {
                runOnUiThread(() -> populateLangSpinnerAdapter());
            }
        });

        // Trigger translation (will download model if needed)
        translationManager.reloadTextsFromResources();
        translationManager.translateIfNeeded();

        save.setOnClickListener(v -> {
            int selPos = langSpinner.getSelectedItemPosition();
            String selLangCode = "en";
            if (selPos >= 0 && selPos < codes.length) {
                selLangCode = codes[selPos];
            }

            boolean soundOn = soundSwitch.isChecked();
            String selColor = "red";
            int checkedId = colorGroup.getCheckedRadioButtonId();
            View checked = findViewById(checkedId);
            if (checked instanceof RadioButton && ((RadioButton) checked).getTag() != null) {
                selColor = ((RadioButton) checked).getTag().toString();
            }

            prefs2.edit()
                    .putString(KEY_LANG, selLangCode)
                    .putBoolean(KEY_SOUND, soundOn)
                    .putString(KEY_COLOR, selColor)
                    .apply();

            // Apply selected locale immediately so resources update when returning
            LocaleUtils.applyAppLocale(this, selLangCode);

            // Update TranslationManager: reload resources and translations
            translationManager.setTargetFromAppLang(selLangCode);
            translationManager.reloadTextsFromResources();
            translationManager.translateIfNeeded();

            // Restart MainActivity to apply language/resources and translations properly
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (translationManager != null) {
            translationManager.unbindActivity();
            translationManager.setListener(null);
        }
    }

    private void populateLangSpinnerAdapter() {
        // Build display labels from resources (current locale or translated texts)
        List<String> list = new ArrayList<>();
        for (String label : labels) {
            list.add(label);
        }
        adapter.clear();
        adapter.addAll(list);
        adapter.notifyDataSetChanged();
    }
}
