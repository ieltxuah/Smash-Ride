package com.example.smash_ride.presentation.game;

import static java.util.Collections.shuffle;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.constants.AppConstants;
import com.example.smash_ride.core.graphics.GifHardwareDecoder;
import com.example.smash_ride.data.remote.FirebaseManager;
import com.example.smash_ride.data.remote.FirestoreRankingManager;
import com.example.smash_ride.core.ui.BaseActivity;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.framework.matchmaking.OnlineMatchmaker;
import com.example.smash_ride.framework.translation.LocaleUtils;
import com.example.smash_ride.framework.translation.TranslationManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Actividad principal que orquesta el flujo de una partida de juego.
 * Gestiona el ciclo de vida del juego, el emparejamiento en línea (matchmaking),
 * la inicialización de los jugadores (locales o remotos) y la persistencia de resultados.
 */
public class GameActivity extends BaseActivity implements GameOverListener {

    private GameView gameView;
    private List<Player> players;
    private int color;
    private View loadingLayout;
    private Handler handler;
    private float centerX, centerY, radius;
    private GameView.GameMode selectedMode = GameView.GameMode.LIVES;
    private boolean offlineMode = true;
    private boolean isGameRunning = false;

    // --- Variables de Conectividad Online ---
    private String roomId;
    private int mySlot = -1;
    private OnlineMatchmaker matchmaker;

    // --- Gestión de Carga y Cancelación ---
    private Thread loadingThread;
    private volatile boolean isCancelled = false;

    // --- Traducción y Preferencias ---
    private TranslationManager translationManager;
    private String currentLang;
    private PreferenceHelper prefHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Asegurar que la pantalla no se apague durante la partida
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefHelper = new PreferenceHelper(this);
        currentLang = prefHelper.getLanguage();
        LocaleUtils.applyAppLocale(this, currentLang);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // Fondo Estelar
        ImageView backgroundGif = findViewById(R.id.background_gif);
        if (backgroundGif != null) {
            GifHardwareDecoder.loadGif(this, backgroundGif, R.raw.background_stars);
        }

        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);

        isGameRunning = true;
        setupBackPressBlocker();

        loadingLayout = findViewById(R.id.loading_layout);
        players = new ArrayList<>();
        handler = new Handler(Looper.getMainLooper());

        // Botón cancelar carga
        Button cancelBtn = findViewById(R.id.cancel_loading_button);
        if (cancelBtn != null) {
            cancelBtn.setOnClickListener(v -> exitToMain());
        }

        // Leer configuración de la partida
        String modeExtra = getIntent().getStringExtra(AppConstants.EXTRA_GAME_MODE);
        selectedMode = AppConstants.MODE_TIMER.equals(modeExtra) ? GameView.GameMode.TIMER : GameView.GameMode.LIVES;
        offlineMode = getIntent().getBooleanExtra(AppConstants.EXTRA_OFFLINE, true);

        if (offlineMode) {
            initializePlayersOffline();
        } else {
            startOnlineMatchmaking();
        }

        initTranslation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SoundManager.getInstance().resumeMusic();
        if (gameView != null) gameView.resume();
    }

    @Override
    protected void onPause() {
        SoundManager.getInstance().pauseMusic();
        // Si el usuario sale de la app durante una partida online, notificamos la salida
        if (!offlineMode && gameView != null && roomId != null) {
            FirebaseManager.getInstance().getRoomsRef()
                    .child(roomId).child("players")
                    .child(prefHelper.getUserId()).removeValue();
        }
        if (gameView != null) {
            gameView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        // Si la partida está activa y la app se detiene, salimos al menú
        if (isGameRunning) {
            exitToMain();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        isCancelled = true;
        if (!offlineMode && roomId != null) {
            // Borramos rastro
            FirebaseManager.getInstance().getRoomsRef().child(roomId).child("players")
                    .child(prefHelper.getUserId()).removeValue();

            // Si somos el maestro, cerramos la sala en el matchmaking por si acaso
            String modeKey = (selectedMode == GameView.GameMode.TIMER) ? "TIMER" : "LIVES";
            FirebaseManager.getInstance().getMatchmakingRef().child(modeKey).child("current_room")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot s) {
                            if (roomId.equals(s.getValue(String.class))) s.getRef().removeValue();
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {}
                    });
        }
        if (matchmaker != null) matchmaker.cleanUp(roomId, prefHelper.getUserId());
        if (loadingThread != null && loadingThread.isAlive()) loadingThread.interrupt();
        if (translationManager != null) translationManager.unbindActivity();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onUserLeaveHint() {
        // Se ejecuta cuando el usuario pulsa HOME
        if (!offlineMode && gameView != null && roomId != null) {
            // En lugar de solo poner vidas a 0, vamos a eliminar el nodo para
            // forzar que los demás detecten que ya no estamos en la lista de jugadores activos
            FirebaseManager.getInstance().getRoomsRef()
                    .child(roomId).child("players")
                    .child(prefHelper.getUserId()).removeValue();
        }
        SoundManager.getInstance().pauseMusic();
        exitToMain();
        super.onUserLeaveHint();
    }

    // --- MÉTODOS DE INICIALIZACIÓN ---

    /**
     * Inicia el proceso de emparejamiento online con Firebase.
     */
    private void startOnlineMatchmaking() {
        loadingLayout.setVisibility(View.VISIBLE);
        matchmaker = new OnlineMatchmaker();
        String modeKey = (selectedMode == GameView.GameMode.TIMER) ? "TIMER" : "LIVES";

        matchmaker.findMatch(modeKey, prefHelper.getOrCreateId(), prefHelper.getUserName(),
                new OnlineMatchmaker.OnMatchFoundListener() {
                    @Override
                    public void onMatchReady(String id, int slot) {
                        if (isCancelled) return;
                        roomId = id;
                        mySlot = slot;
                        setupOnlinePlayers();
                    }

                    @Override
                    public void onError(String error) {
                        handler.post(() -> {
                            String finalMessage;
                            if ("ERROR_MATCHMAKING_TIMEOUT".equals(error)) {
                                finalMessage = getString(R.string.error_matchmaking_timeout);
                            } else {
                                finalMessage = error;
                            }

                            // 2. Mostrar el diálogo estético (que traduce internamente)
                            translationManager.showTranslatedToast(finalMessage);
                            exitToMain();
                        });
                    }
                }, prefHelper);
    }

    /**
     * Descarga y valida los jugadores de la sala online antes de iniciar la partida.
     */
    private void setupOnlinePlayers() {
        Log.d("MATCH_DEBUG", "setupOnlinePlayers: Validando slots únicos...");
        calculateScreenMetrics();
        int[][] states = getInitialStates();

        FirebaseManager.getInstance().getRoomsRef().child(roomId).child("players")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        players.clear();
                        boolean[] slotCheck = new boolean[4];
                        int uniqueSlotsCount = 0;

                        for (DataSnapshot pSnap : snapshot.getChildren()) {
                            Integer slot = pSnap.child("slot").getValue(Integer.class);
                            String name = pSnap.child("name").getValue(String.class);
                            String colorPref = pSnap.child("color_pref").getValue(String.class);

                            if (slot != null && slot >= 0 && slot < 4 && !slotCheck[slot]) {
                                slotCheck[slot] = true;
                                uniqueSlotsCount++;

                                Player p = new Player(name, states[slot][0], states[slot][1], states[slot][2], 0, slot);
                                int finalColor = (slot == mySlot) ?
                                        getColorHexByTag(prefHelper.getCharacterColor()) :
                                        getColorHexByTag(colorPref);
                                p.setAppearance(GameActivity.this, finalColor);
                                players.add(p);
                            } else {
                                Log.w("MATCH_DEBUG", "Slot duplicado o inválido detectado: " + slot + ". Ignorando entrada antigua.");
                            }
                        }

                        // SOLO INICIAMOS SI TENEMOS 4 SLOTS DIFERENTES (0, 1, 2, 3)
                        if (uniqueSlotsCount == 4) {
                            players.sort(Comparator.comparingInt(p -> p.slot));
                            Log.d("MATCH_DEBUG", "¡Sala validada con 4 jugadores únicos! Iniciando...");
                            finishLoading();
                        } else {
                            Log.d("MATCH_DEBUG", "Sala no válida aún (Jugadores reales únicos: " + uniqueSlotsCount + "/4). Reintentando...");
                            // Si no son 4, volvemos a llamar a este método en un momento
                            handler.postDelayed(() -> setupOnlinePlayers(), 1500);
                        }
                    }

                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    /**
     * Configura la partida local con bots.
     */
    private void initializePlayersOffline() {
        isCancelled = false;
        players.clear();
        loadingThread = new Thread(() -> {
            try {
                calculateScreenMetrics();
                int myColorHex = getColorHexByTag(prefHelper.getCharacterColor());
                this.color = myColorHex;

                List<Integer> availableColors = new ArrayList<>();
                for (int hex : AppConstants.CAROUSEL_HEX) {
                    if (hex != myColorHex) availableColors.add(hex);
                }
                shuffle(availableColors);

                int[][] states = getInitialStates();
                for (int i = 0; i < 4; i++) {
                    if (isCancelled || Thread.currentThread().isInterrupted()) return;
                    String pName = (i == 0) ? prefHelper.getUserName() : "Bot " + i;
                    Player p = new Player(pName, states[i][0], states[i][1], states[i][2], 0, i);
                    int pColor = (i == 0) ? this.color : availableColors.get(i - 1);
                    p.setAppearance(this, pColor);
                    players.add(p);
                }
                if (!isCancelled) handler.post(this::finishLoading);
            } catch (Exception e) {
                handler.post(this::exitToMain);
            }
        });
        loadingThread.start();
    }

    /**
     * Finaliza la fase de carga y establece la vista del juego activa.
     */
    private void finishLoading() {
        if (isFinishing() || isCancelled) return;
        if (players.size() < 4) return;

        // --- LÍNEA VITAL: Detener el matchmaker y sus timers ---
        if (matchmaker != null) matchmaker.cancelTimeout();

        if (!offlineMode && mySlot == 0) {
            String modeKey = (selectedMode == GameView.GameMode.TIMER) ? "TIMER" : "LIVES";
            DatabaseReference matchRef = FirebaseManager.getInstance().getMatchmakingRef().child(modeKey);

            // Borramos la sala de 'current_room' para que nadie más intente entrar
            // mientras nosotros estamos jugando.
            matchRef.child("current_room").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String currentId = snapshot.getValue(String.class);
                    if (roomId != null && roomId.equals(currentId)) {
                        matchRef.child("current_room").removeValue();
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        }

        loadingLayout.setVisibility(View.GONE);
        int myColorHex = getColorHexByTag(prefHelper.getCharacterColor());
        gameView = new GameView(this, players, myColorHex);
        gameView.setGameOverListener(this);
        gameView.setGameMode(selectedMode);
        gameView.setOffline(offlineMode);

        if (!offlineMode) {
            gameView.setOnlineData(roomId, mySlot);
        }

        setContentView(gameView);

        if (!offlineMode) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (gameView != null) gameView.resume();
            }, 500);
        } else {
            gameView.resume();
        }
    }


    // --- EVENTOS DE JUEGO ---

    /**
     * Llamado cuando la partida termina.
     * - Pausa la vista del juego y evita reentradas.
     * - Extrae los datos del jugador local.
     * - Actualiza estadísticas en Firestore.
     * - Redirige a la pantalla de victoria/derrota o al ranking según el modo.
     */
    @Override
    public void onGameOver() {
        if (!isGameRunning) return;
        isGameRunning = false;
        if (gameView != null) gameView.pause();

        // 1. Extraer datos del jugador LOCAL
        Player localPlayer = null;
        int myActualSlot = offlineMode ? 0 : mySlot;
        for (Player p : players) {
            if (p.slot == myActualSlot) {
                localPlayer = p;
                break;
            }
        }

        if (localPlayer != null) {
            Log.d("FIRESTORE_CHECK", "Subiendo datos de: " + prefHelper.getUserName());

            String uId = prefHelper.getUserId();
            String uName = prefHelper.getUserName();
            String modeStr = (selectedMode == GameView.GameMode.TIMER) ? "TIMER" : "LIVES";

            // Calcular si soy ganador
            int alive = 0;
            for (Player p : players) if (!p.isDestroyed()) alive++;
            boolean winner = (selectedMode == GameView.GameMode.LIVES && alive == 1 && !localPlayer.isDestroyed());

            // Llamada al manager
            FirestoreRankingManager rankingMgr = new FirestoreRankingManager();
            rankingMgr.updateStats(uId, uName, modeStr,
                    localPlayer.getKills(),
                    localPlayer.getLivesLostMatch(),
                    localPlayer.getHitsDealtMatch(),
                    winner);

            if (selectedMode == GameView.GameMode.TIMER) {
                rankingMgr.updateTimerKing(uId, localPlayer.getKills());
            }
        }

        // 2. Navegación
        if (selectedMode == GameView.GameMode.LIVES) {
            boolean amIAlive = false;
            for(Player p : players) if(p.slot == (offlineMode?0:mySlot) && !p.isDestroyed()) amIAlive = true;

            if (amIAlive) {
                startActivity(new Intent(this, WinActivity.class));
            } else {
                startActivity(new Intent(this, LoseActivity.class));
            }
        } else {
            // Modo Timer -> Ranking
            Intent i = new Intent(this, RankingGameActivity.class);
            ArrayList<String> names = new ArrayList<>();
            ArrayList<Integer> kills = new ArrayList<>();
            ArrayList<Integer> colors = new ArrayList<>();
            for (Player p : players) {
                names.add(p.name);
                kills.add(p.getKills());
                colors.add(p.getColor());
            }
            i.putStringArrayListExtra("NAMES", names);
            i.putIntegerArrayListExtra("KILLS", kills);
            i.putIntegerArrayListExtra("COLORS", colors);
            startActivity(i);
        }
        finish();
    }

    // --- UTILIDADES ---

    /**
     * Obtiene el color hexadecimal asociado a una etiqueta del carrusel.
     *
     * @param tag etiqueta del color (ej.: "red", "blue")
     * @return valor hexadecimal del color correspondiente; si no se encuentra, devuelve el primer color del carrusel.
     */
    private int getColorHexByTag(String tag) {
        for (int i = 0; i < AppConstants.CAROUSEL_COLORS.length; i++) {
            if (AppConstants.CAROUSEL_COLORS[i].equalsIgnoreCase(tag)) {
                return AppConstants.CAROUSEL_HEX[i];
            }
        }
        return AppConstants.CAROUSEL_HEX[0];
    }

    /**
     * Calcula métricas de pantalla necesarias para posicionamiento inicial.
     * - centerX / centerY: centro de la pantalla.
     * - radius: radio por defecto usado para posicionamiento.
     */
    private void calculateScreenMetrics() {
        centerX = getResources().getDisplayMetrics().widthPixels / 2f;
        centerY = getResources().getDisplayMetrics().heightPixels / 2f;
        radius = 400f;
    }

    /**
     * Obtiene los estados/posiciones angulares iniciales para los 4 slots de jugador.
     *
     * @return matriz 4x3 con [x, y, rotation] para los slots 0..3 en orden (Norte, Sur, Este, Oeste).
     */
    private int[][] getInitialStates() {
        return new int[][]{
            {(int) centerX, (int) (centerY - 400), 90},  // Norte (Slot 0)
            {(int) centerX, (int) (centerY + 400), 270}, // Sur (Slot 1)
            {(int) (centerX + 400), (int) centerY, 180}, // Este (Slot 2)
            {(int) (centerX - 400), (int) centerY, 0}    // Oeste (Slot 3)
        };
    }

    /**
     * Cancela operaciones pendientes relacionadas con matchmaking y carga, y cierra la Activity.
     * - Marca la carga como cancelada.
     * - Limpia el matchmaker de Firebase.
     * - Interrumpe el thread de carga si está activo.
     * - Elimina callbacks pendientes del handler.
     * - Finaliza la Activity.
     */
    private void exitToMain() {
        isCancelled = true;
        if (matchmaker != null) matchmaker.cleanUp(roomId, prefHelper.getUserId());
        if (loadingThread != null) loadingThread.interrupt();
        handler.removeCallbacksAndMessages(null);
        finish();
    }

    /**
     * Inicializa el sistema de traducción para la Activity:
     * - Establece el idioma objetivo.
     * - Registra vistas del layout para traducción dinámica.
     * - Si el idioma es nativo, recarga textos desde recursos; en caso contrario, traduce según sea necesario.
     */
    private void initTranslation() {
        translationManager.setTargetFromAppLang(currentLang);
        View root = findViewById(android.R.id.content);
        translationManager.scanAndRegisterViews(root);

        if (LocaleUtils.isNativeLanguage(currentLang)) {
            translationManager.reloadTextsFromResources();
        } else {
            translationManager.translateIfNeeded();
        }
    }

    /** Evita que el botón "Atrás" haga efecto durante la partida. */
    private void setupBackPressBlocker() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Bloqueado durante el juego
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }
}