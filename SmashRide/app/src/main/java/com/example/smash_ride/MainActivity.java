package com.example.smash_ride;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Translator translator;
    private List<View> viewsToTranslate;
    private Map<View, String> originalTexts;
    private String selectedLanguageCode = TranslateLanguage.SPANISH;
    private String previousLanguageCode = TranslateLanguage.ENGLISH;
    private static final String[] LANGUAGE_ABBREVIATIONS = {
            "en", "es", "fr", "de", "it", "zh"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);

        initializeVariables();
        setupUI();
        setupTranslator(selectedLanguageCode, TranslateLanguage.ENGLISH);
        downloadModelAndTranslate();
    }

    private void initializeVariables() {
        viewsToTranslate = new ArrayList<>();
        originalTexts = new HashMap<>();
    }

    private void setupUI() {
        Button startButton = findViewById(R.id.start_button);
        startButton.setOnClickListener(v -> startGame());

        Spinner languageSpinner = findViewById(R.id.language_spinner);
        setupLanguageSpinner(languageSpinner);
        viewsToTranslate.add(findViewById(R.id.title));
        viewsToTranslate.add(startButton);
    }

    private void setupLanguageSpinner(Spinner spinner) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, LANGUAGE_ABBREVIATIONS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(1); // Default to Spanish (es)

        spinner.setOnItemSelectedListener(new LanguageSelectionListener());
    }

    private class LanguageSelectionListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
            String selectedLanguage = LANGUAGE_ABBREVIATIONS[position];
            updateTranslatorLanguage(selectedLanguage);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parentView) {}
    }

    private void downloadModelAndTranslate() {
        translator.downloadModelIfNeeded()
                .addOnSuccessListener(aVoid -> {
                    Log.d("MainActivity", "Model downloaded successfully.");
                    translateAllViews();
                })
                .addOnFailureListener(e -> handleTranslationError("Model download failed", e));
    }

    private void translateAllViews() {
        for (View view : viewsToTranslate) {
            String originalText = getViewText(view);
            originalTexts.put(view, originalText);
            translateText(originalText, view);
            Log.d("MainActivity", "Translating: " + originalText);
        }
    }

    private String getViewText(View view) {
        if (view instanceof TextView) {
            return ((TextView) view).getText().toString();
        } else if (view instanceof Button) {
            return ((Button) view).getText().toString();
        }
        return "";
    }

    private void translateText(String textToTranslate, View view) {
        translator.translate(textToTranslate)
                .addOnSuccessListener(translatedText -> updateViewText(view, translatedText))
                .addOnFailureListener(e -> handleTranslationError("Translation failed", e));
    }

    private void updateViewText(View view, String translatedText) {
        if (view instanceof TextView) {
            ((TextView) view).setText(translatedText);
            Log.d("MainActivity", "Translated Text: " + translatedText);
        } else if (view instanceof Button) {
            ((Button) view).setText(translatedText);
            Log.d("MainActivity", "Translated Button Text: " + translatedText);
        }
    }

    private void handleTranslationError(String message, Exception e) {
        Log.e("MainActivity", message + ": " + e.getMessage());
        Toast.makeText(this, message + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    private void updateTranslatorLanguage(String selectedLanguage) {
        String newLanguageCode = getLanguageCode(selectedLanguage);
        if (!newLanguageCode.equals(previousLanguageCode)) {
            selectedLanguageCode = newLanguageCode;
            setupTranslator(selectedLanguageCode, previousLanguageCode);
            downloadModelAndTranslate();
            previousLanguageCode = newLanguageCode;
        }
    }

    private void setupTranslator(String targetLanguage, String sourceLanguage) {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build();
        translator = Translation.getClient(options);
    }

    private String getLanguageCode(String selectedLanguage) {
        switch (selectedLanguage) {
            case "es": return TranslateLanguage.SPANISH;
            case "fr": return TranslateLanguage.FRENCH;
            case "de": return TranslateLanguage.GERMAN;
            case "it": return TranslateLanguage.ITALIAN;
            case "zh": return TranslateLanguage.CHINESE;
            default: return TranslateLanguage.ENGLISH; // English (en)
        }
    }

    private void startGame() {
        Intent intent = new Intent(this, GameActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (translator != null) {
            translator.close();
        }
    }
}
