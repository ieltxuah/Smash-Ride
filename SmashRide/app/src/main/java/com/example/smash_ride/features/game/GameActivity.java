package com.example.smash_ride.features.game;

import static java.util.Collections.shuffle;

import android.content.Intent;import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.constants.AppConstants;
import com.example.smash_ride.core.graphics.GifHardwareDecoder;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.features.ranking.RankingActivity;
import com.example.smash_ride.translation.LocaleUtils;
import com.example.smash_ride.translation.TranslationManager;

import java.util.ArrayList;
import java.util.List;

public class GameActivity extends AppCompatActivity implements GameOverListener {

    private GameView gameView;
    private List<Player> players;
    int color = AppConstants.CAROUSEL_HEX[3];
    private View loadingLayout;
    private static final long LOADER_DELAY_MS = 20000; // 20 segundos
    private Handler handler;
    private float centerX;
    private float centerY;
    private float radius;
    private GameView.GameMode selectedMode = GameView.GameMode.LIVES;
    private boolean offlineMode = true;
    private boolean isGameRunning = false;

    // --- NUEVAS VARIABLES PARA CANCELACIÓN ---
    private Thread loadingThread;
    private volatile boolean isCancelled = false;

    // Traducción y Preferencias
    private TranslationManager translationManager;
    private String currentLang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. GESTIÓN DE IDIOMA
        PreferenceHelper prefHelper = new PreferenceHelper(this);
        currentLang = prefHelper.getLanguage();
        LocaleUtils.applyAppLocale(this, currentLang);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // 2. CARGAR GIF DE FONDO POR HARDWARE
        ImageView backgroundGif = findViewById(R.id.background_gif);
        if (backgroundGif != null) {
            GifHardwareDecoder.loadGif(this, backgroundGif, R.raw.background_stars);
        }

        // 3. INICIALIZAR MANAGER DE TRADUCCIÓN
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);

        isGameRunning = true;
        setupBackPressBlocker();

        loadingLayout = findViewById(R.id.loading_layout);
        players = new ArrayList<>();
        handler = new Handler(Looper.getMainLooper());

        // 4. BOTÓN CANCELAR (Actualizado con cancelación de hilo)
        Button cancelBtn = findViewById(R.id.cancel_loading_button);
        if (cancelBtn != null) {
            cancelBtn.setOnClickListener(v -> {
                isCancelled = true;
                if (loadingThread != null && loadingThread.isAlive()) {
                    loadingThread.interrupt(); // Detener el hilo inmediatamente
                }
                handler.removeCallbacksAndMessages(null); // Detener el finishLoading
                finish();
                overridePendingTransition(0, 0); // Evitar parpadeo blanco
            });
        }

        // Obtener configuración del Intent
        String modeExtra = getIntent().getStringExtra(AppConstants.EXTRA_GAME_MODE);
        if (AppConstants.MODE_TIMER.equals(modeExtra)) {
            selectedMode = GameView.GameMode.TIMER;
        } else {
            selectedMode = GameView.GameMode.LIVES;
        }
        offlineMode = getIntent().getBooleanExtra("OFFLINE", true);

        // 5. INICIAR CARGA
        initializePlayers();

        // 6. TRADUCIR UI DE CARGA
        initTranslation();
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

    private void setupBackPressBlocker() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!isGameRunning) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void initializePlayers() {
        isCancelled = false;
        loadingThread = new Thread(() -> {
            try {
                // 1. Obtener mi color de ajustes
                PreferenceHelper prefHelper = new PreferenceHelper(this);
                String myColorTag = prefHelper.getCharacterColor();

                // Lista de colores disponibles para bots (basada en AppConstants)
                List<Integer> availableColors = new ArrayList<>();
                for (int hex : AppConstants.CAROUSEL_HEX) {
                    availableColors.add(hex);
                }

                // Identificar mi color y quitarlo de la lista de bots
                for (int i = 0; i < AppConstants.CAROUSEL_COLORS.length; i++) {
                    if (AppConstants.CAROUSEL_COLORS[i].equalsIgnoreCase(myColorTag)) {
                        color = AppConstants.CAROUSEL_HEX[i];
                        availableColors.remove((Integer) color); // Eliminar de bots
                        break;
                    }
                }

                // Mezclar colores restantes para los bots
                shuffle(availableColors);

                centerX = getResources().getDisplayMetrics().widthPixels / 2f;
                centerY = getResources().getDisplayMetrics().heightPixels / 2f;
                radius = Math.min(centerX, centerY) - 200;

                // Definimos {X, Y, ÁnguloInicial}
                // Los ángulos están calculados para que miren hacia el centro del área
                int[][] initialStates = {
                        { (int) centerX, (int) (centerY - radius), 90  }, // P1: Arriba mirando abajo
                        { (int) centerX, (int) (centerY + radius), 270 }, // P2: Abajo mirando arriba
                        { (int) (centerX + radius), (int) centerY, 180 }, // P3: Derecha mirando izquierda
                        { (int) (centerX - radius), (int) centerY, 0   }  // P4: Izquierda mirando derecha
                };

                for (int i = 0; i < 4; i++) {
                    if (isCancelled || Thread.currentThread().isInterrupted()) return;

                    // Actualizamos el constructor: pasamos initialStates[i][2] como ángulo
                    Player p = new Player("Player " + (i + 1),
                            initialStates[i][0],
                            initialStates[i][1],
                            initialStates[i][2], // Nuevo parámetro de ángulo
                            0); // Velocidad inicial

                    // ASIGNACIÓN DE COLOR
                    if (i == 0) {
                        p.setAppearance(this, color);
                    } else {
                        p.setAppearance(this, availableColors.get(i - 1));
                    }
                    players.add(p);
                }

                if (!isCancelled) {
                    handler.postDelayed(this::finishLoading, LOADER_DELAY_MS);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        loadingThread.start();
    }

    private void finishLoading() {
        if (isFinishing() || isCancelled) return;

        // Ocultar UI de carga y fondos de estrellas
        loadingLayout.setVisibility(View.GONE);
        View staticBg = findViewById(R.id.background_static);
        View gifBg = findViewById(R.id.background_gif);
        if (staticBg != null) staticBg.setVisibility(View.GONE);
        if (gifBg != null) gifBg.setVisibility(View.GONE);

        // Inicializar la vista de juego real
        gameView = new GameView(this, players, color);
        gameView.setGameOverListener(this);
        gameView.setGameMode(selectedMode);
        gameView.setOffline(offlineMode);
        setContentView(gameView);
        gameView.resume();
    }

    @Override
    public void onGameOver() {
        isGameRunning = false;
        if (gameView != null) {
            gameView.pause();
        }

        int alive = 0;
        Player lastAlive = null;
        for (Player p : players) {
            if (!p.isDestroyed()) { alive++; lastAlive = p; }
        }

        if (selectedMode == GameView.GameMode.LIVES) {
            if (alive == 1 && lastAlive != null && lastAlive == players.get(0)) {
                startActivity(new Intent(this, WinActivity.class));
            } else {
                startActivity(new Intent(this, LoseActivity.class));
            }
        } else {
            Intent i = new Intent(this, RankingActivity.class);
            ArrayList<String> names = new ArrayList<>();
            ArrayList<Integer> kills = new ArrayList<>();
            for (Player p : players) {
                names.add(p.name);
                kills.add(p.getKills());
            }
            i.putStringArrayListExtra("NAMES", names);
            i.putIntegerArrayListExtra("KILLS", kills);
            startActivity(i);
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SoundManager.getInstance().resumeMusic();
        if (gameView != null) {
            gameView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SoundManager.getInstance().pauseMusic();
        if (gameView != null) {
            gameView.pause();
            if (players != null && !players.isEmpty()) {
                Player p1 = players.get(0);
                p1.destroy();
            }
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isCancelled = true;
        if (loadingThread != null && loadingThread.isAlive()) {
            loadingThread.interrupt();
        }
        if (translationManager != null) {
            translationManager.unbindActivity();
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}