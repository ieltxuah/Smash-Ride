package com.example.smash_ride.features.settings;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;

import androidx.annotation.NonNull;
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

    private static final String TAG = "DEBUG_TRANSLATION";
    private TranslationManager translationManager;
    private PreferenceHelper prefHelper;

    private Spinner langSpinner;
    private SeekBar musicSeekBar;
    private SeekBar effectsSeekBar;
    private RecyclerView carouselRv;

    private String[] labels;
    private String[] codes;
    private String selectedColorTag = "dorado";
    private String currentLang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefHelper = new PreferenceHelper(this);
        currentLang = prefHelper.getLanguage();

        // 1. Aplicar el idioma al contexto antes de inflar la vista
        LocaleUtils.applyAppLocale(this, currentLang);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 2. Configurar el TranslationManager
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);

        initViews();
        loadSettings();
        setupColorCarousel();

        // 3. Iniciar secuencia de traducción
        initTranslation();
    }

    private void initTranslation() {
        translationManager.setTargetFromAppLang(currentLang);
        View root = findViewById(android.R.id.content);
        translationManager.scanAndRegisterViews(root);

        if (isNativeLanguage(currentLang)) {
            translationManager.reloadTextsFromResources();
        } else {
            translationManager.translateIfNeeded();
        }
    }

    private boolean isNativeLanguage(String lang) {
        return lang.equals("es") || lang.equals("eu") || lang.equals("en");
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

    private void initViews() {
        langSpinner = findViewById(R.id.settings_language_spinner);
        musicSeekBar = findViewById(R.id.settings_music_seekbar);
        effectsSeekBar = findViewById(R.id.settings_effects_seekbar);
        Button saveButton = findViewById(R.id.settings_save_button);

        musicSeekBar.setMax(4);
        effectsSeekBar.setMax(4);

        // Listener Música
        musicSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    prefHelper.setMusicVolume(progress);
                    SoundManager.getInstance().updateVolume(SettingsActivity.this);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Listener Efectos
        effectsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    prefHelper.setEffectsVolume(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        labels = getResources().getStringArray(R.array.supported_lang_labels);
        codes = getResources().getStringArray(R.array.supported_lang_codes);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(labels)));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        langSpinner.setAdapter(adapter);

        saveButton.setOnClickListener(v -> saveAndExit());
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
    }

    private void setupColorCarousel() {
        carouselRv = findViewById(R.id.color_carousel_rv);
        if (carouselRv == null) return;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int itemWidth = (int) (120 * displayMetrics.density);
        int padding = (displayMetrics.widthPixels / 2) - (itemWidth / 2);

        carouselRv.setPadding(padding, 0, padding, 0);
        carouselRv.setClipToPadding(false);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        carouselRv.setLayoutManager(layoutManager);
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

        final int finalPos = initialPos;
        carouselRv.post(() -> {
            layoutManager.scrollToPositionWithOffset(finalPos, 0);
            carouselRv.postDelayed(() -> {
                View view = layoutManager.findViewByPosition(finalPos);
                if (view != null) {
                    int[] snapDistance = snapHelper.calculateDistanceToFinalSnap(layoutManager, view);
                    if (snapDistance != null) carouselRv.smoothScrollBy(snapDistance[0], snapDistance[1]);
                }
            }, 100);
        });

        carouselRv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                View centerView = snapHelper.findSnapView(layoutManager);
                if (centerView != null) {
                    int pos = recyclerView.getChildAdapterPosition(centerView);
                    if (pos != RecyclerView.NO_POSITION) {
                        selectedColorTag = AppConstants.CAROUSEL_COLORS[pos].toLowerCase();
                    }
                }
            }
        });
    }

    private void saveAndExit() {
        String selLang = codes[langSpinner.getSelectedItemPosition()];
        prefHelper.setLanguage(selLang);
        prefHelper.setMusicVolume(musicSeekBar.getProgress());
        prefHelper.setEffectsVolume(effectsSeekBar.getProgress());
        prefHelper.setCharacterColor(selectedColorTag);

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ViewHolder> {
        private final String[] names;
        private final int[] colors;

        ColorAdapter(String[] names, int[] colors) {
            this.names = names;
            this.colors = colors;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_carousel_player, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.img.setColorFilter(colors[position], PorterDuff.Mode.MULTIPLY);
            holder.itemView.setOnClickListener(v -> carouselRv.smoothScrollToPosition(position));
        }

        @Override public int getItemCount() { return names.length; }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView img;
            ViewHolder(View v) { super(v); img = v.findViewById(R.id.img_player_preview); }
        }
    }
}