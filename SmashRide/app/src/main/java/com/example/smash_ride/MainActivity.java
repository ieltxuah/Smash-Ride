package com.example.smash_ride;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TranslationManager translationManager;
    private List<View> viewsToTranslate;
    private Map<Integer, String> originalTextsById; // retained for compatibility if needed

    private String selectedLangPrefCode; // e.g. "en","es","eu"

    private static final String PREFS = "notification_prefs";
    private static final String KEY_WORK_ID = "work_id";
    private static final String PREFS_MODE = "game_mode_prefs";
    private static final String KEY_MODE = "selected_mode";

    private static final String PREFS_SETTINGS = "app_settings";
    private static final String KEY_LANG = "language";
    private static final String KEY_SOUND = "sound_enabled";
    private static final String KEY_COLOR = "character_color";

    private static final int REQUEST_CODE_POST_NOTIF = 2001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Application already applied locale in MyApplication.onCreate
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

        // Setup persistent TranslationManager (initialized in Application)
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);

        // use saved lang from prefs
        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        selectedLangPrefCode = prefs.getString(KEY_LANG, "en");

        translationManager.setTargetFromAppLang(selectedLangPrefCode);

        // Register views after setContentView and after target set
        translationManager.scanAndRegisterViews(findViewById(android.R.id.content));

        // Ensure resources are loaded for current locale, then trigger translations
        translationManager.reloadTextsFromResources();
        translationManager.translateIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        String newPref = prefs.getString(KEY_LANG, "en");
        if (!newPref.equals(selectedLangPrefCode)) {
            // Update cached code
            selectedLangPrefCode = newPref;

            // Apply locale app-wide via application context to avoid transient resets
            // MyApplication already applied on startup; here we ensure app resources match new selection
            // (LocaleUtils is safe to call with application context if needed)
            LocaleUtils.applyAppLocale(getApplicationContext(), selectedLangPrefCode);

            // reload resources and translate
            translationManager.reloadTextsFromResources();
            translationManager.setTargetFromAppLang(selectedLangPrefCode);
            translationManager.translateIfNeeded();
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
        startButton.setOnClickListener(v -> startGame());

        Button settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(i);
        });

        viewsToTranslate.add(findViewById(R.id.title));
        viewsToTranslate.add(findViewById(R.id.start_button));
        viewsToTranslate.add(findViewById(R.id.settings_button));

        // Save original (English) texts from resources as source of truth
        for (View view : viewsToTranslate) {
            String eng = LocaleUtils.getEnglishTextForView(this, view);
            if (eng == null) eng = "";
            originalTextsById.put(view.getId(), eng);
            // also explicitly register these views with the manager (optional since we scanned root)
            if (translationManager != null) translationManager.registerViews(view);
        }
    }

    private void setupModeSelector() {
        ImageButton selector = findViewById(R.id.mode_selector_circle);
        if (selector == null) return;

        selector.setOnClickListener(v -> {
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
            String[] items = new String[] {getString(R.string.mode_lives), getString(R.string.mode_timer)};
            String current = getSharedPreferences(PREFS_MODE, MODE_PRIVATE).getString(KEY_MODE, "LIVES");
            int checked = "TIMER".equals(current) ? 1 : 0;
            b.setTitle(getString(R.string.select_mode))
                    .setSingleChoiceItems(items, checked, null)
                    .setPositiveButton(getString(R.string.ok), (d, which) -> {
                        int sel = ((android.app.AlertDialog)d).getListView().getCheckedItemPosition();
                        String mode = sel == 1 ? "TIMER" : "LIVES";
                        getSharedPreferences(PREFS_MODE, MODE_PRIVATE).edit().putString(KEY_MODE, mode).apply();
                        updateModeSelectorIcon();
                    })
                    .setCancelable(false)
                    .show();
        });
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

    // --- The remaining methods copied from your original MainActivity (schedule/cancel/etc) ---

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
                UUID workId = UUID.fromString(workIdString);
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
