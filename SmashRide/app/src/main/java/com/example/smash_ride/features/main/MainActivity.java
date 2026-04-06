package com.example.smash_ride.features.main;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smash_ride.R;
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
        currentLang = prefHelper.getLanguage();
        LocaleUtils.applyAppLocale(this, currentLang);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);

        notificationScheduler = new NotificationScheduler(this);
        notificationScheduler.requestPermissions(this, 2001);
        notificationScheduler.cancelPendingReminder();

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
        updateModeIcon();
    }

    private void showModeDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mode_selector, null);

        // Estilizar iconos
        ImageView imgLives = dialogView.findViewById(R.id.img_lives);
        ImageView imgTimer = dialogView.findViewById(R.id.img_timer);

        SpriteColorizer.tintImageView(imgLives, Color.RED);
        SpriteColorizer.tintImageView(imgTimer, Color.BLACK);

        translationManager.scanAndRegisterViews(dialogView);
        applyTranslationLogic();

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        dialogView.findViewById(R.id.option_lives).setOnClickListener(v -> {
            prefHelper.setGameMode("LIVES");
            updateModeIcon();
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.option_timer).setOnClickListener(v -> {
            prefHelper.setGameMode("TIMER");
            updateModeIcon();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateModeIcon() {
        ImageButton selector = findViewById(R.id.mode_selector_circle);
        String mode = prefHelper.getGameMode();
        boolean isTimer = "TIMER".equals(mode);

        selector.setImageResource(isTimer ? R.drawable.ic_clock : R.drawable.ic_heart);
        selector.setContentDescription(getString(isTimer ? R.string.mode_timer : R.string.mode_lives));
    }

    private void startGame() {
        Intent intent = new Intent(this, GameActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra("GAME_MODE", prefHelper.getGameMode());
        intent.putExtra("OFFLINE", true);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!prefHelper.getLanguage().equals(currentLang)) recreate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        translationManager.unbindActivity();
        notificationScheduler.scheduleReturnReminder(10);
    }
}