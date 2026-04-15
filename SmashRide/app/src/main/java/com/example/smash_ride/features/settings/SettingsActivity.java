package com.example.smash_ride.features.settings;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.constants.AppConstants;
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
    private RecyclerView carouselRv;

    private TextView userNameText;
    private TextView authStatusLabel;
    private Button loginLogoutButton;
    private Button deleteDataButton;

    private String[] labels;
    private String[] codes;
    private String selectedColorTag = "dorado";
    private String currentLang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefHelper = new PreferenceHelper(this);
        currentLang = prefHelper.getLanguage();

        // 1. Aplicar idioma al contexto base antes de inflar vistas
        LocaleUtils.applyAppLocale(this, currentLang);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 2. Inicializar Manager y vincular actividad
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);

        // 3. Inicializar vistas y cargar datos (Música y Volumen incluidos)
        initViews();
        loadSettings();
        setupUserSection();
        setupColorCarousel();

        // 4. EJECUTAR TRADUCCIÓN (Asegurando que los títulos con ID se registren)
        initTranslation();
    }

    private void initViews() {
        langSpinner = findViewById(R.id.settings_language_spinner);
        musicSeekBar = findViewById(R.id.settings_music_seekbar);
        effectsSeekBar = findViewById(R.id.settings_effects_seekbar);
        Button saveButton = findViewById(R.id.settings_save_button);

        userNameText = findViewById(R.id.user_name_text);
        authStatusLabel = findViewById(R.id.auth_status_label);
        loginLogoutButton = findViewById(R.id.login_logout_button);
        deleteDataButton = findViewById(R.id.delete_data_button);

        musicSeekBar.setMax(4);
        effectsSeekBar.setMax(4);

        labels = getResources().getStringArray(R.array.supported_lang_labels);
        codes = getResources().getStringArray(R.array.supported_lang_codes);

        ArrayList<String> langList = new ArrayList<>(Arrays.asList(labels));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_text_item, langList);
        adapter.setDropDownViewResource(R.layout.spinner_text_item);
        langSpinner.setAdapter(adapter);

        saveButton.setOnClickListener(v -> saveAndExit());
        deleteDataButton.setOnClickListener(v -> showDeleteConfirmation());

        setupSeekBarListeners();
    }

    private void initTranslation() {
        // Configuramos el idioma destino en ML Kit
        translationManager.setTargetFromAppLang(currentLang);

        // Obtenemos la vista raíz para asegurar que escanee TODO (títulos con ID incluidos)
        View root = findViewById(android.R.id.content);
        translationManager.scanAndRegisterViews(root);

        // EXCLUSIÓN UX: No traducir el nombre de usuario (usando unregisterView que añadiste)
        if (userNameText != null) {
            translationManager.unregisterView(userNameText);
        }

        // Lógica de traducción basada en el contexto del manager
        if (currentLang.equals("es") || currentLang.equals("eu") || currentLang.equals("en")) {
            translationManager.reloadTextsFromResources();
        } else {
            translationManager.translateIfNeeded();
        }
    }

    private void setupUserSection() {
        boolean isLoggedIn = false; // Cambiar por lógica real de tu Auth

        if (isLoggedIn) {
            if(authStatusLabel != null) authStatusLabel.setText(R.string.status_connected);
            if(loginLogoutButton != null) {
                loginLogoutButton.setText(R.string.logout_action);
                // Cambiamos el color a ROJO para Logout
                loginLogoutButton.setTextColor(android.graphics.Color.parseColor("#FF5252"));
                if (loginLogoutButton instanceof com.google.android.material.button.MaterialButton) {
                    ((com.google.android.material.button.MaterialButton) loginLogoutButton)
                            .setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF5252")));
                }
            }
            if(userNameText != null) userNameText.setText("STAR_USER");

            // Si está logueado, mostramos el botón de borrar datos
            if(deleteDataButton != null) deleteDataButton.setVisibility(View.VISIBLE);
        } else {
            if(authStatusLabel != null) authStatusLabel.setText(R.string.status_disconnected);
            if(loginLogoutButton != null) {
                loginLogoutButton.setText(R.string.login_action);
                // Restauramos el color VERDE para Iniciar Sesión
                loginLogoutButton.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                if (loginLogoutButton instanceof com.google.android.material.button.MaterialButton) {
                    ((com.google.android.material.button.MaterialButton) loginLogoutButton)
                            .setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
                }
            }
            if(userNameText != null) userNameText.setText("STAR_USER");

            // UX: Si no está logueado, ocultamos el botón de borrar para evitar accidentes o por privacidad
            if(deleteDataButton != null) deleteDataButton.setVisibility(View.GONE);
        }
    }

    private void setupSeekBarListeners() {
        musicSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean f) {
                if (f) {
                    prefHelper.setMusicVolume(p);
                    // Actualización inmediata del volumen de música
                    SoundManager.getInstance().updateVolume(SettingsActivity.this);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        effectsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean f) {
                if (f) prefHelper.setEffectsVolume(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void saveAndExit() {
        String selLang = codes[langSpinner.getSelectedItemPosition()];
        prefHelper.setLanguage(selLang);
        prefHelper.setMusicVolume(musicSeekBar.getProgress());
        prefHelper.setEffectsVolume(effectsSeekBar.getProgress());
        prefHelper.setCharacterColor(selectedColorTag);

        // Asegurar que el SoundManager guarde el volumen final
        SoundManager.getInstance().updateVolume(this);

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        // UX: Evitar pantallazo blanco (Transición instantánea)
        overridePendingTransition(0, 0);
        finish();
    }

    private void loadSettings() {
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(currentLang)) {
                langSpinner.setSelection(i);
                break;
            }
        }
        musicSeekBar.setProgress(prefHelper.getMusicVolume());
        effectsSeekBar.setProgress(prefHelper.getEffectsVolume());
        selectedColorTag = prefHelper.getCharacterColor();
        SoundManager.getInstance().updateVolume(this);
    }

    private void setupColorCarousel() {
        carouselRv = findViewById(R.id.color_carousel_rv);
        if (carouselRv == null) return;

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int itemWidth = (int) (120 * dm.density);
        int padding = (dm.widthPixels / 2) - (itemWidth / 2);

        carouselRv.setPadding(padding, 0, padding, 0);
        carouselRv.setClipToPadding(false);

        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        carouselRv.setLayoutManager(lm);
        carouselRv.setAdapter(new ColorAdapter(AppConstants.CAROUSEL_COLORS, AppConstants.CAROUSEL_HEX));

        LinearSnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(carouselRv);

        int initialPos = 0;
        for (int i = 0; i < AppConstants.CAROUSEL_COLORS.length; i++) {
            if (AppConstants.CAROUSEL_COLORS[i].equalsIgnoreCase(selectedColorTag)) {
                initialPos = i;
                break;
            }
        }

        final int fPos = initialPos;
        // SOLUCIÓN DESALINEACIÓN: Doble post para forzar el centrado matemático
        carouselRv.post(() -> {
            lm.scrollToPositionWithOffset(fPos, 0);
            carouselRv.post(() -> {
                View view = lm.findViewByPosition(fPos);
                if (view != null) {
                    int[] snapDistance = snapHelper.calculateDistanceToFinalSnap(lm, view);
                    if (snapDistance != null) {
                        carouselRv.scrollBy(snapDistance[0], snapDistance[1]);
                    }
                }
            });
        });

        carouselRv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                View centerView = snapHelper.findSnapView(lm);
                if (centerView != null) {
                    int pos = recyclerView.getChildAdapterPosition(centerView);
                    if (pos != RecyclerView.NO_POSITION) {
                        selectedColorTag = AppConstants.CAROUSEL_COLORS[pos].toLowerCase();
                    }
                }
            }
        });
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_all_data))
                .setMessage("¿Estás seguro de borrar todo el progreso?")
                .setPositiveButton("BORRAR", (dialog, which) -> {
                    prefHelper.setMusicVolume(2);
                    prefHelper.setEffectsVolume(2);
                    SoundManager.getInstance().updateVolume(this);
                    Toast.makeText(this, "Datos borrados", Toast.LENGTH_SHORT).show();
                    loadSettings();
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    @Override protected void onResume() {
        super.onResume();
        SoundManager.getInstance().resumeMusic();
    }

    @Override protected void onPause() {
        super.onPause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (translationManager != null) translationManager.unbindActivity();
    }

    private class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ViewHolder> {
        private final String[] names;
        private final int[] colors;
        ColorAdapter(String[] names, int[] colors) { this.names = names; this.colors = colors; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_carousel_player, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            h.img.setColorFilter(colors[p], PorterDuff.Mode.MULTIPLY);
            h.itemView.setOnClickListener(v -> carouselRv.smoothScrollToPosition(p));
        }
        @Override public int getItemCount() { return names.length; }
        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView img;
            ViewHolder(View v) { super(v); img = v.findViewById(R.id.img_player_preview); }
        }
    }
}