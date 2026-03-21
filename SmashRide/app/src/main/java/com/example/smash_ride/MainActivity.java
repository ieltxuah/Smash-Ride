package com.example.smash_ride;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private Translator translator;
    private List<View> viewsToTranslate;
    private Map<View, String> originalTexts;
    private String selectedLanguageCode = TranslateLanguage.SPANISH;
    private String previousLanguageCode = TranslateLanguage.ENGLISH;
    private static final String[] LANGUAGE_ABBREVIATIONS = {
            "en", "es", "fr", "de", "it", "zh"
    };

    private static final String PREFS = "notification_prefs";
    private static final String KEY_WORK_ID = "work_id";

    private static final int REQUEST_CODE_POST_NOTIF = 2001;

    private EditText emailInput;
    private EditText passwordInput;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);

        auth = FirebaseAuth.getInstance();

        // Solicitar permiso POST_NOTIFICATIONS solo en Android 13+ (API 33).
        // Para minSdk 31 esto no solicitará nada, pero si compilas/target=33 la solicitud se hará.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_POST_NOTIF);
            }
        }

        // Si hay un recordatorio pendiente (programado al cerrar), lo cancelamos al volver
        cancelPendingReminderIfAny();

        initializeVariables();
        setupUI();
        setupTranslator(selectedLanguageCode, TranslateLanguage.ENGLISH);
        downloadModelAndTranslate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_POST_NOTIF) {
            // Opcional: comprobar grantResults si quieres mostrar UI; el Worker también verifica permiso antes de notify()
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "POST_NOTIFICATIONS granted");
            } else {
                Log.d("MainActivity", "POST_NOTIFICATIONS denied");
            }
        }
    }

    private void initializeVariables() {
        viewsToTranslate = new ArrayList<>();
        originalTexts = new HashMap<>();
    }

    private void setupUI() {
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);

        Button loginButton = findViewById(R.id.login_button);
        loginButton.setOnClickListener(v -> login());

        Button signupButton = findViewById(R.id.signup_button);
        signupButton.setOnClickListener(v -> signUp());

        Spinner languageSpinner = findViewById(R.id.language_spinner);
        setupLanguageSpinner(languageSpinner);
        viewsToTranslate.add(findViewById(R.id.title));
        viewsToTranslate.add(loginButton);
        viewsToTranslate.add(signupButton);
    }

    private void login() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        new PlayerPositionManager(user.getUid()).createProfile(user.getEmail());
                        startGame(user.getEmail(), user.getUid());
                    } else {
                        Toast.makeText(this, "Log in failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void signUp() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        new PlayerPositionManager(user.getUid()).createProfile(user.getEmail());
                        startGame(user.getEmail(), user.getUid());
                    } else {
                        Toast.makeText(this, "Sign up failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startGame(String email, String uid) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra("username", email);
        intent.putExtra("uid", uid);
        startActivity(intent);
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
        if (translator == null) return;
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
        if (translator == null) return;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (translator != null) {
            translator.close();
        }
        // Programar notificación a 10 minutos al cerrar la app
        scheduleReturnReminder();
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
                UUID workId = UUID.fromString(workIdString);
                WorkManager.getInstance(this).cancelWorkById(workId);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_WORK_ID).apply();
            } catch (IllegalArgumentException ignored) {}
        }
    }
}