package com.example.smash_ride.features.game;

import static java.util.Collections.shuffle;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.constants.AppConstants;
import com.example.smash_ride.core.graphics.GifHardwareDecoder;
import com.example.smash_ride.data.firebase.FirebaseManager;
import com.example.smash_ride.data.firebase.FirestoreRankingManager;
import com.example.smash_ride.core.ui.BaseActivity;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.features.game.online.OnlineMatchmaker;
import com.example.smash_ride.translation.LocaleUtils;
import com.example.smash_ride.translation.TranslationManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    // Variables Online
    private String roomId;
    private int mySlot = -1;
    private OnlineMatchmaker matchmaker;

    // Variables Cancelación
    private Thread loadingThread;
    private volatile boolean isCancelled = false;

    // Traducción y Preferencias
    private TranslationManager translationManager;
    private String currentLang;
    private PreferenceHelper prefHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

    private void startOnlineMatchmaking() {
        loadingLayout.setVisibility(View.VISIBLE);
        matchmaker = new OnlineMatchmaker();
        String modeKey = (selectedMode == GameView.GameMode.TIMER) ? "TIMER" : "LIVES";

        matchmaker.findMatch(modeKey, prefHelper.getUserId(), prefHelper.getUserName(),
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
                            Toast.makeText(GameActivity.this, error, Toast.LENGTH_LONG).show();
                            exitToMain();
                        });
                    }
                }, prefHelper);
    }

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
    private int getColorHexByTag(String tag) {
        for (int i = 0; i < AppConstants.CAROUSEL_COLORS.length; i++) {
            if (AppConstants.CAROUSEL_COLORS[i].equalsIgnoreCase(tag)) {
                return AppConstants.CAROUSEL_HEX[i];
            }
        }
        return AppConstants.CAROUSEL_HEX[0];
    }

    private int getNextAvailableColor(List<Integer> used) {
        for (int hex : AppConstants.CAROUSEL_HEX) {
            if (!used.contains(hex)) return hex;
        }
        return 0xFFFFFFFF;
    }

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

    private void calculateScreenMetrics() {
        centerX = getResources().getDisplayMetrics().widthPixels / 2f;
        centerY = getResources().getDisplayMetrics().heightPixels / 2f;
        radius = 400f;
    }

    private int[][] getInitialStates() {
        return new int[][]{
            {(int) centerX, (int) (centerY - 400), 90},  // Norte (Slot 0)
            {(int) centerX, (int) (centerY + 400), 270}, // Sur (Slot 1)
            {(int) (centerX + 400), (int) centerY, 180}, // Este (Slot 2)
            {(int) (centerX - 400), (int) centerY, 0}    // Oeste (Slot 3)
        };
    }

    private void finishLoading() {
        if (isFinishing() || isCancelled) return;
        if (players.size() < 4) return;

        // --- LÍNEA VITAL: Detener el matchmaker y sus timers ---
        if (matchmaker != null) {
            matchmaker.cancelTimeout(); // Necesitas crear este método o llamar a cleanup sin borrar
        }

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

    private void exitToMain() {
        isCancelled = true;
        if (matchmaker != null) matchmaker.cleanUp(roomId, prefHelper.getUserId());
        if (loadingThread != null) loadingThread.interrupt();
        handler.removeCallbacksAndMessages(null);
        finish();
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
                // Bloqueado durante el juego
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    @Override
    public void onGameOver() {
        if (!isGameRunning) return;    isGameRunning = false;
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


    @Override
    protected void onResume() {
        super.onResume();
        SoundManager.getInstance().resumeMusic();
        if (gameView != null) gameView.resume();
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

    @Override
    protected void onPause() {
        SoundManager.getInstance().pauseMusic();
        // Si el usuario le da a HOME, marcamos sus vidas como 0 en Firebase antes de salir
        if (!offlineMode && gameView != null && roomId != null) {
            // En lugar de solo poner vidas a 0, vamos a eliminar el nodo para
            // forzar que los demás detecten que ya no estamos en la lista de jugadores activos
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
        SoundManager.getInstance().pauseMusic();
        // Si el usuario le da a HOME, marcamos sus vidas como 0 en Firebase antes de salir
        if (!offlineMode && gameView != null && roomId != null) {
            // En lugar de solo poner vidas a 0, vamos a eliminar el nodo para
            // forzar que los demás detecten que ya no estamos en la lista de jugadores activos
            FirebaseManager.getInstance().getRoomsRef()
                    .child(roomId).child("players")
                    .child(prefHelper.getUserId()).removeValue();
        }
        if (gameView != null) {
            gameView.pause();
        }
        // Si el juego está en marcha y no ha terminado por victoria/derrota
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
}