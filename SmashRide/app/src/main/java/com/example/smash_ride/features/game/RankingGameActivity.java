package com.example.smash_ride.features.game;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.graphics.GifHardwareDecoder;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.features.main.MainActivity;
import com.example.smash_ride.translation.LocaleUtils;
import com.example.smash_ride.translation.TranslationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class RankingGameActivity extends AppCompatActivity {

    private TranslationManager translationManager;
    private String currentLang;
    private Typeface kirbyFont;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Idioma
        PreferenceHelper prefHelper = new PreferenceHelper(this);
        currentLang = prefHelper.getLanguage();
        LocaleUtils.applyAppLocale(this, currentLang);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking_game);

        // Cargar fuente para los items de la lista
        try {
            kirbyFont = ResourcesCompat.getFont(this, R.font.kirby_classic);
        } catch (Exception e) {
            kirbyFont = Typeface.DEFAULT_BOLD;
        }

        // 2. Fondo
        ImageView gifBg = findViewById(R.id.background_gif);
        if (gifBg != null) {
            GifHardwareDecoder.loadGif(this, gifBg, R.raw.background_stars);
        }

        // 3. Traducción
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);
        initTranslation();

        // 4. Lógica de Ranking
        ArrayList<String> names = getIntent().getStringArrayListExtra("NAMES");
        ArrayList<Integer> kills = getIntent().getIntegerArrayListExtra("KILLS");

        ArrayList<String> displayList = new ArrayList<>();
        if (names != null && kills != null && names.size() == kills.size()) {
            ArrayList<Integer> indices = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) indices.add(i);

            // Ordenar por Kills de mayor a menor
            Collections.sort(indices, (i1, i2) -> kills.get(i2).compareTo(kills.get(i1)));

            for (int i = 0; i < indices.size(); i++) {
                int originalIdx = indices.get(i);
                displayList.add((i + 1) + ". " + names.get(originalIdx) + " - " + kills.get(originalIdx) + " Kills");
            }
        }

        ListView lv = findViewById(R.id.ranking_list);

        // Usamos un Adapter personalizado para aplicar la fuente y el color blanco
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, displayList) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(kirbyFont);
                tv.setTextSize(18f);
                tv.setShadowLayer(4f, 2f, 2f, Color.BLACK);
                return tv;
            }
        };

        lv.setAdapter(adapter);

        // 5. Botón Volver
        findViewById(R.id.back_menu).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
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
        // 1. Reanudar o iniciar la música (puedes usar la del menú o una de victoria)
        SoundManager.getInstance().resumeMusic();

        // Si quieres asegurar que suene la música del menú en el ranking:
        if (!SoundManager.getInstance().isMusicPlaying()) {
            SoundManager.getInstance().playMenuMusic(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 2. Pausar la música al salir de la actividad
        SoundManager.getInstance().pauseMusic();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (translationManager != null) translationManager.unbindActivity();
    }
}