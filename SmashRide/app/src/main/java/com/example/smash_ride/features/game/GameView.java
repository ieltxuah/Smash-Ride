package com.example.smash_ride.features.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.constants.AppConstants;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.translation.TranslationManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameView extends SurfaceView implements Runnable {
    private GameOverListener gameOverListener;
    public enum GameMode { LIVES, TIMER }
    private GameMode gameMode = GameMode.LIVES;
    private long timerMs = 0L;
    private static final long TIMER_TOTAL_MS = 3 * 60 * 1000L;
    private boolean offline = true;

    private Thread gameThread;
    private boolean isPlaying;
    private Paint paint;
    private SurfaceHolder surfaceHolder;
    private List<Player> players;
    private Joystick joystick;
    private GameArea gameArea;
    private Movie backgroundGif;
    private long gifStartTime;
    private Bitmap backgroundStatic;
    private Rect screenRect;
    private Typeface hudTypeface;
    private String labelKills = "Kills: ";
    private String labelLives = "Lives: ";
    private String labelTime = "Time: ";

    private float centerX;
    private float centerY;
    private float radius;

    private static final long FULL_CHARGE_MS = 3000L;
    private static final long MAX_BOOST_MS = 3000L;
    private static final float BOOST_MULTIPLIER = 2.0f;
    private static final float BOOST_SPEED_CAP = 30f;
    private static final float RECHARGE_MULTIPLIER = 2.0f;

    private int joystickPointerId = -1;
    private int boostPointerId = -1;

    private long chargeAvailableMs = FULL_CHARGE_MS;
    private long boostStoredMs = 0L;
    private boolean boostActive = false;

    private long lastUpdateTimeMs = System.currentTimeMillis();
    private boolean ended = false;

    private static final long VIBRATION_DURATION_MS = 100;
    private long lastVibrationTimeMs = 0;
    private static final long VIBRATION_THROTTLE_MS = 200;

    private DatabaseReference roomRef;
    private int mySlot = -1;
    private String roomId;
    private PreferenceHelper prefHelper;
    private final Map<Integer, String> slotToIdMap = new HashMap<>();
    int currentHostSlot = 0;
    private boolean resultTriggered = false; // Nueva variable de clase para evitar abrir 20 activities
    private long lastProcessedHitTime = 0;

    public GameView(Context context, List<Player> players, int color) {
        super(context);
        this.setKeepScreenOn(true);
        this.prefHelper = new PreferenceHelper(context);
        this.players = players;
        this.joystick = new Joystick();
        joystick.setThemeColor(color);
        try {
            hudTypeface = ResourcesCompat.getFont(context, R.font.kirby_classic);
        } catch (Exception e) {
            hudTypeface = Typeface.DEFAULT_BOLD;
        }
        loadBackgrounds(context); // Cargar recursos de fondo
        initHudTranslations();
        SoundManager.getInstance().loadGameSounds(context); // Cargar el sonido de choque
        initialize();
    }

    private void loadBackgrounds(Context context) {
        // 1. Intentar cargar GIF
        try {
            // Reemplaza 'background_stars' con el nombre real de tu recurso GIF
            backgroundGif = Movie.decodeStream(context.getResources().openRawResource(R.raw.background_game));
            gifStartTime = 0;
        } catch (Exception e) {
            Log.e("GameView", "No se pudo cargar el GIF de fondo");
            backgroundGif = null;
        }

        // 2. Intentar cargar Imagen Estática (Respaldo 1)
        try {
            // Reemplaza 'background_stars_static' con tu imagen PNG/JPG
            backgroundStatic = BitmapFactory.decodeResource(context.getResources(), R.drawable.background_game_static);
        } catch (Exception e) {
            Log.e("GameView", "No se pudo cargar la imagen estática de fondo");
            backgroundStatic = null;
        }
    }

    private void initialize() {
        surfaceHolder = getHolder();
        paint = new Paint();
        paint.setColor(Color.GRAY);
        isPlaying = false;

        chargeAvailableMs = FULL_CHARGE_MS;
        boostStoredMs = 0;
        boostActive = false;

        if (players != null) {
            for (Player p : players) {
                p.setInvincible(2000);
            }
        }
        lastUpdateTimeMs = System.currentTimeMillis();
        ended = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // AQUÍ es donde el tamaño es real
        centerX = w / 2f;
        centerY = h / 2f;

        // Definimos el radio de la luna (puedes ajustarlo según el diseño)
        radius = 500f;

        // Rectángulo que ocupa toda la pantalla para estirar el fondo
        screenRect = new Rect(0, 0, w, h);

        // Creamos el área de juego aquí con el tamaño correcto
        gameArea = new GameArea(getContext(), centerX, centerY, radius);

        // Reposicionamos a los jugadores para que no se queden en 0,0 al iniciar
        // Solo si es la primera vez que se dimensiona
        if (oldw == 0 && oldh == 0) {
            for (Player p : players) {
                p.resetPosition();
                p.setInvincible(2000); // 2 segundos de gracia al empezar
            }
        }
    }

    public void setGameMode(GameMode mode) {
        this.gameMode = mode;
        if (mode == GameMode.TIMER) timerMs = TIMER_TOTAL_MS;
        // ensure players don't start moving automatically when mode changes
        if (players != null) {
            for (Player p : players) {
                if (!p.isDestroyed()) p.setSpeed(0f);
            }
        }
    }

    public void setOffline(boolean off) {
        this.offline = off;
    }

    @Override
    public void run() {
        while (isPlaying) {
            update();
            draw();
            try {
                Thread.sleep(16);
            } catch (InterruptedException ignored) {}
        }
    }

    private void update() {
        long now = System.currentTimeMillis();
        long delta = now - lastUpdateTimeMs;
        if (delta <= 0) delta = 16;
        lastUpdateTimeMs = now;

        if (ended || radius <= 0 || players == null || players.isEmpty()) return;

        // --- AÑADE ESTO AQUÍ: Actualizar el cronómetro ---
        if (gameMode == GameMode.TIMER && !offline && mySlot == currentHostSlot) {
            timerMs -= delta;
            if (timerMs < 0) timerMs = 0;
            // El maestro sincroniza el tiempo para todos en Firebase
            roomRef.child("status").child("timer").setValue(timerMs);
        } else if (gameMode == GameMode.TIMER && offline) {
            // En modo offline el tiempo baja localmente
            timerMs -= delta;
            if (timerMs < 0) timerMs = 0;
        }
        // ------------------------------------------------

        // --- ENCONTRAR JUGADOR LOCAL ---
        Player player1 = getPlayerBySlot(offline ? 0 : mySlot);
        if (player1 == null) return;

        // 1. GESTIÓN DE MUERTE LOCAL
        if (!offline && player1.isDestroyed() && !resultTriggered) {
            // Solo disparamos el GameOver si NO es modo TIMER
            if (gameMode != GameMode.TIMER) {
                resultTriggered = true;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (gameOverListener != null) gameOverListener.onGameOver();
                });

                if (mySlot != currentHostSlot) {
                    ended = true;
                    return;
                }
            } else {
                // En modo TIMER, si muero, simplemente reseteo mi posición localmente
                // El Maestro se encargará de sincronizar esto a través de syncMasterTruth
                player1.resetPosition();
            }
        }

        // 2. INPUT LOCAL (JOYSTICK)
        if (!player1.isDestroyed()) {
            processLocalInput(delta, player1);
            if (!offline && mySlot != currentHostSlot) {
                syncMyMovement(player1.getAngle(), player1.getSpeed(), boostActive);
            }
        }

        // 3. SIMULACIÓN (MAESTRO vs ESCLAVO)
        if (offline || mySlot == currentHostSlot) {
            // --- ROL MAESTRO ---
            for (Player p : players) {
                if (p.isDestroyed()) continue;
                p.update(); // Física local
                checkCollision(p);
            }

            // Colisiones entre jugadores (Sólo Maestro)
            for (int i = 0; i < players.size(); i++) {
                for (int j = i + 1; j < players.size(); j++) {
                    Player a = players.get(i); Player b = players.get(j);
                    if (a.isDestroyed() || b.isDestroyed() || a.isInvincible() || b.isInvincible()) continue;
                    if (Math.hypot(a.getXPos()-b.getXPos(), a.getYPos()-b.getYPos()) < 55f) {
                        handleCollision(a, b);
                    }
                }
            }
            if (!offline) syncMasterTruth(currentHostSlot);
            checkMatchEndConditions();

        } else {
            // --- ROL ESCLAVO ---
            for (Player p : players) {
                p.update(); // Interpolación
                if (p.slot == mySlot && !p.isDestroyed()) {
                    checkCollision(p);
                }
            }
        }
    }

    private void checkMatchEndConditions() {
        if (ended) return;    int aliveCount = 0;
        Player winner = null;

        for (Player p : players) {
            if (!p.isDestroyed()) {
                aliveCount++;
                winner = p;
            }
        }

        // --- CORRECCIÓN AQUÍ ---
        // En modo VIDAS, la partida acaba si queda 1 o 0 vivos
        if (gameMode == GameMode.LIVES) {
            if (aliveCount == 1 && winner != null) {
                // Avisamos a Firebase del ganador
                roomRef.child("winner_slot").setValue(winner.slot);
            }

            if (aliveCount <= 1) {
                ended = true;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (gameOverListener != null) gameOverListener.onGameOver();
                });
            }
        }
        // En modo TIEMPO, la partida SOLO acaba cuando el tiempo llega a 0
        else if (gameMode == GameMode.TIMER && timerMs <= 0) {
            ended = true;
            new Handler(Looper.getMainLooper()).post(this::endMatchAndShowRanking);
        }
    }

    // Lo que envían TODOS (incluido el maestro para sus propios datos de input)
    private void syncMyMovement(float angle, float speed, boolean boost) {
        if (roomRef == null) return;
        String myUid = getUserIdBySlot(mySlot);
        if (myUid != null) {
            Map<String, Object> input = new HashMap<>();
            input.put("angle", angle);
            input.put("speed", speed);
            input.put("boost", boost);
            roomRef.child("players").child(myUid).child("input").updateChildren(input);
        }
    }


    // SOLO el Maestro llama a esto para todos los jugadores
    private void syncMasterTruth(int hostSlot) {
        if (roomRef == null) return;

        for (Player p : players) {
            // Obtenemos el UID de Firebase de este jugador desde el mapa que llenamos en listenToOtherPlayers
            String uid = slotToIdMap.get(p.slot);

            if (uid != null) {
                Map<String, Object> state = new HashMap<>();
                // Posición relativa al centro para que sea independiente de la resolución
                state.put("relX", p.getXPos() - centerX);
                state.put("relY", p.getYPos() - centerY);
                state.put("angle", p.getAngle());
                state.put("lives", p.getLives());
                state.put("kills", p.getKills());
                state.put("inv", p.isInvincible());
                state.put("livesLost", p.getLivesLostMatch());
                state.put("hitsDeal", p.getHitsDealtMatch());

                // El maestro escribe la "Verdad" en el nodo state de CADA jugador
                roomRef.child("players").child(uid).child("state").updateChildren(state);
            }
        }
    }

    private void draw() {
        Canvas canvas = null;
        try {
            if (surfaceHolder.getSurface().isValid()) {
                canvas = surfaceHolder.lockCanvas();

                // Nivel 3: Color Negro (Base siempre presente)
                canvas.drawColor(Color.BLACK);

                if (backgroundGif != null) {
                    // Nivel 1: Dibujar GIF
                    long now = System.currentTimeMillis();
                    if (gifStartTime == 0) gifStartTime = now;

                    int relTime = (int) ((now - gifStartTime) % backgroundGif.duration());
                    backgroundGif.setTime(relTime);

                    // Calcular escala para cubrir pantalla (Center Crop)
                    float scale = Math.max((float) getWidth() / backgroundGif.width(),
                            (float) getHeight() / backgroundGif.height());

                    canvas.save();
                    // Centrar y escalar
                    canvas.translate((getWidth() - backgroundGif.width() * scale) / 2f,
                            (getHeight() - backgroundGif.height() * scale) / 2f);
                    canvas.scale(scale, scale);
                    backgroundGif.draw(canvas, 0, 0);
                    canvas.restore();

                } else if (backgroundStatic != null) {
                    // Nivel 2: Imagen estática con Center Crop
                    float scale = Math.max((float) getWidth() / backgroundStatic.getWidth(),
                            (float) getHeight() / backgroundStatic.getHeight());
                    float w = backgroundStatic.getWidth() * scale;
                    float h = backgroundStatic.getHeight() * scale;
                    Rect dest = new Rect((int)((getWidth()-w)/2), (int)((getHeight()-h)/2),
                            (int)((getWidth()+w)/2), (int)((getHeight()+h)/2));
                    canvas.drawBitmap(backgroundStatic, null, dest, null);
                }

                if (gameArea != null) gameArea.draw(canvas);
                else Log.e("GameView", "GameArea is null, cannot draw");

                for (Player p : players) {
                    if (!p.isDestroyed()) p.draw(canvas);
                }
                joystick.draw(canvas);

                drawHUD(canvas);
            }
        } finally {
            if (canvas != null) surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void initHudTranslations() {
        Context ctx = getContext();
        // 1. Cargar base desde strings.xml (Nativo: ES, EU, EN)
        labelKills = ctx.getString(R.string.hud_kills).replace(": %d", ": ");
        labelLives = ctx.getString(R.string.hud_lives).replace(": %d", ": ");
        labelTime = ctx.getString(R.string.hud_time).replace(": %s", ": ");

        // 2. Si el idioma no es nativo, pedir traducción a ML Kit
        PreferenceHelper pf = new PreferenceHelper(ctx);
        String lang = pf.getLanguage();
        if (!lang.equals("es") && !lang.equals("eu") && !lang.equals("en")) {
            TranslationManager tm = TranslationManager.getInstance();
            tm.translateRaw(labelKills, translated -> labelKills = translated);
            tm.translateRaw(labelLives, translated -> labelLives = translated);
            tm.translateRaw(labelTime, translated -> labelTime = translated);
        }
    }

    // 1. Asegúrate de que las variables de HUD y Paint estén bien definidas
    private void drawHUD(Canvas canvas) {
        if (players == null || players.isEmpty()) return;
        int localIdx = (offline) ? 0 : mySlot;
        if (localIdx < 0 || localIdx >= players.size()) return;

        Player me = players.get(localIdx);

        // Margenes de seguridad para evitar bordes y notches
        float marginX = 80f;
        float marginTop = 120f;

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(50f);
        textPaint.setFakeBoldText(true);
        textPaint.setTypeface(hudTypeface);
        textPaint.setShadowLayer(8f, 3f, 3f, Color.BLACK);

        // --- PARTE IZQUIERDA: VIDAS O TIEMPO ---
        String leftText;
        if (gameMode == GameMode.LIVES) {
            leftText = labelLives + me.getLives();
        } else {
            // Formateo de tiempo mm:ss
            long timeToDisplay = Math.max(0, timerMs);
            int seconds = (int) (timeToDisplay / 1000) % 60;
            int minutes = (int) (timeToDisplay / (1000 * 60)) % 60;
            String timeFormatted = String.format("%02d:%02d", minutes, seconds);
            leftText = labelTime + String.format("%02d:%02d", minutes, seconds);
        }
        canvas.drawText(leftText, marginX, marginTop, textPaint);

        // --- PARTE DERECHA: KILLS ---
        String killsText = labelKills + me.getKills();
        float killsWidth = textPaint.measureText(killsText);
        canvas.drawText(killsText, getWidth() - marginX - killsWidth, marginTop, textPaint);

        // --- BARRA DE BOOST (Debajo del texto de la izquierda) ---
        float barWidth = 350f;
        float barHeight = 30f;
        float barTop = marginTop + 50f;

        // Fondo de la barra (Gris oscuro translúcido)
        Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(Color.DKGRAY);
        barPaint.setAlpha(180);
        canvas.drawRect(marginX, barTop, marginX + barWidth, barTop + barHeight, barPaint);

        // Progreso
        float fraction;
        if (boostActive && boostStoredMs > 0) {
            fraction = (float) boostStoredMs / (float) MAX_BOOST_MS;
            barPaint.setColor(Color.YELLOW); // Color Boost activo
        } else {
            fraction = (float) chargeAvailableMs / (float) FULL_CHARGE_MS;
            barPaint.setColor(Color.parseColor("#4CAF50")); // Verde carga
        }
        barPaint.setAlpha(255);
        canvas.drawRect(marginX, barTop, marginX + (barWidth * fraction), barTop + barHeight, barPaint);

        // Borde negro fino para la barra
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setColor(Color.BLACK);
        barPaint.setStrokeWidth(4f);
        canvas.drawRect(marginX, barTop, marginX + barWidth, barTop + barHeight, barPaint);
    }

    public void setOnlineData(String roomId, int mySlot) {
        this.roomId = roomId;
        this.mySlot = mySlot;
        this.roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);
        roomRef.child("status").child("timer").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && mySlot != currentHostSlot) {
                    Long serverTime = snapshot.getValue(Long.class);
                    if (serverTime != null) timerMs = serverTime;
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
        listenToOtherPlayers();
    }

    private String getUserIdBySlot(int slot) {
        return slotToIdMap.get(slot);
    }

    private void listenToOtherPlayers() {
        if (roomRef == null) return;
        roomRef.child("players").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                int lowestSlot = 99;
                Set<Integer> activeSlots = new HashSet<>();

                // 1. RE-MAPEO TOTAL DE IDs (Crítico para que el nuevo Maestro sepa a quién escribir)
                slotToIdMap.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Integer s = ds.child("slot").getValue(Integer.class);
                    if (s != null) {
                        activeSlots.add(s);
                        slotToIdMap.put(s, ds.getKey()); // Slot -> UID de Firebase
                        if (s < lowestSlot) lowestSlot = s;
                    }
                }

                // 2. DETECTAR MIGRACIÓN
                if (currentHostSlot != lowestSlot) {
                    Log.d("HOST_DEBUG", "MIGRACIÓN DETECTADA: " + currentHostSlot + " -> " + lowestSlot);
                    currentHostSlot = lowestSlot;

                    for (Player p : players) {
                        if (!activeSlots.contains(p.slot)) {
                            if (!p.isDestroyed()) p.destroy();
                            continue;
                        }

                        if (mySlot == currentHostSlot) {
                            // ¡SOY EL NUEVO MAESTRO!
                            // Desactivo modo remoto para TODOS en mi pantalla.
                            // Ahora YO calculo la física de todos los jugadores.
                            p.setIsRemote(false);
                            p.setColliding(false); // Desbloquea el joystick por si acaso
                        } else {
                            // Sigo siendo esclavo: Solo mi estrella es física local
                            p.setIsRemote(p.slot != mySlot);
                        }
                    }
                    if (mySlot == currentHostSlot) Log.d("HOST_DEBUG", "¡HE TOMADO EL MANDO FÍSICO!");
                }

                // 3. ACTUALIZACIÓN DE DATOS SEGÚN ROL
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Integer slot = ds.child("slot").getValue(Integer.class);
                    if (slot == null) continue;
                    Player p = getPlayerBySlot(slot);
                    if (p == null) continue;

                    if (slot == mySlot && mySlot != currentHostSlot) {
                        // Soy esclavo: escucho mi estado oficial del Maestro
                        updatePlayerFromState(p, ds.child("state"));
                    } else if (slot != mySlot) {
                        if (mySlot == currentHostSlot) {
                            // Soy Maestro: leo el joystick de los esclavos para moverlos físicamente
                            DataSnapshot input = ds.child("input");
                            if (input.exists()) {
                                Float ang = input.child("angle").getValue(Float.class);
                                Float spd = input.child("speed").getValue(Float.class);
                                if (ang != null) p.setAngle(ang);
                                if (spd != null) p.setSpeed(spd);
                            }
                        } else {
                            // Soy Esclavo: leo dónde puso el Maestro a los otros jugadores
                            updatePlayerFromState(p, ds.child("state"));
                        }
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void checkCollision(Player player) {
        if (player == null || player.isDestroyed()) return;
        float dx = player.getXPos() - centerX;
        float dy = player.getYPos() - centerY;
        float dist = (float) Math.hypot(dx, dy);

        if (dist >= radius) {
            double angle = Math.atan2(dy, dx);

            // 1. LÓGICA DE BLOQUEO (Para todos: Maestro, Esclavo o Invencible)
            // Bloqueamos la posición si: es invencible O si es mi propio personaje en mi pantalla
            if (player.isInvincible() || (player.slot == mySlot)) {
                player.setXPos(centerX + (float) (Math.cos(angle) * (radius - 5)));
                player.setYPos(centerY + (float) (Math.sin(angle) * (radius - 5)));
                player.setSpeed(0);
            }

            // 2. LÓGICA DE MUERTE (Sólo el Maestro o en Offline)
            if (offline || mySlot == currentHostSlot) {
                if (!player.isInvincible()) {
                    if (gameMode == GameMode.LIVES) {
                        player.loseLife();
                    }
                    player.resetPosition();
                    vibratePhoneThrottled();
                }
            }
        }
    }

    private void handleCollision(Player playerA, Player playerB) {
        if (playerA == null || playerB == null) return;
        if (playerA.isDestroyed() || playerB.isDestroyed()) return;

        playerA.addHit();
        playerB.addHit();

        float dx = playerA.getXPos() - playerB.getXPos();
        float dy = playerA.getYPos() - playerB.getYPos();
        float distance = (float) Math.hypot(dx, dy);
        if (distance == 0f) distance = 0.001f;

        // Vibrar si el jugador local está involucrado
        if (playerA.slot == mySlot || playerB.slot == mySlot) vibratePhoneThrottled();

        float pushX = dx / distance;
        float pushY = dy / distance;

        // Determinar quién es el agresor (más rápido) y quién el afectado (más lento)
        Player affected;
        Player faster;

        if (playerA.getSpeed() > playerB.getSpeed()) {
            affected = playerB;
            faster = playerA;
        } else if (playerB.getSpeed() > playerA.getSpeed()) {
            affected = playerA;
            faster = playerB;
        } else {
            // Empate de velocidad: ambos retroceden, no hay "killer" claro
            applyRetroceForBoth(playerA, playerB, pushX, pushY, playerA.getSpeed());
            return;
        }

        // Aplicar retroceso físico al jugador afectado
        float retreatSpeed = faster.getSpeed();
        // Invertimos el ángulo del afectado para que salga despedido en dirección opuesta al choque
        float angleToPush = (affected == playerA) ? (float) Math.atan2(dy, dx) : (float) Math.atan2(-dy, -dx);

        affected.disableJoystick();

        for (int i = 0; i <= 10; i++) {
            final int step = i;
            final long delay = step * 10L;
            final float moveX = (float) Math.cos(angleToPush) * retreatSpeed;
            final float moveY = (float) Math.sin(angleToPush) * retreatSpeed;

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (affected.isDestroyed()) return;

                // Mover por impacto
                affected.setXPos(affected.getXPos() + moveX);
                affected.setYPos(affected.getYPos() + moveY);

                // Comprobar si se sale de la luna DURANTE el retroceso
                float dist = (float) Math.hypot(affected.getXPos() - centerX, affected.getYPos() - centerY);
                if (dist >= radius) {
                    // --- CAMBIO CLAVE AQUÍ ---
                    // Si hay un agresor, le damos la Kill CADA VEZ que el otro pierda una vida
                    if (!faster.isDestroyed()) {
                        faster.addKill();
                    }

                    if (gameMode == GameMode.LIVES) {
                        affected.loseLife();
                    }

                    // Reposicionar al jugador (esto limpia el lastHitter internamente)
                    affected.resetPosition();
                }

                if (step == 10) affected.enableJoystick();
            }, delay);
        }
    }

    private void applyRetroceForBoth(Player playerA, Player playerB, float pushX, float pushY, float retreatSpeed) {
        if (playerA.isDestroyed() || playerB.isDestroyed()) return;
        playerA.setAngle((float) Math.toDegrees(Math.atan2(pushY, pushX)));
        playerB.setAngle((float) Math.toDegrees(Math.atan2(-pushY, -pushX)));
        playerA.disableJoystick();
        playerB.disableJoystick();
        for (int i = 0; i <= 10; i++) {
            final int step = i;
            final long delay = step * 10L;
            final float dxPush = pushX;
            final float dyPush = pushY;
            final float rSpeed = retreatSpeed;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (playerA.isDestroyed() || playerB.isDestroyed()) return;
                playerA.setXPos(playerA.getXPos() + dxPush * rSpeed * 0.1f);
                playerA.setYPos(playerA.getYPos() + dyPush * rSpeed * 0.1f);
                playerB.setXPos(playerB.getXPos() - dxPush * rSpeed * 0.1f);
                playerB.setYPos(playerB.getYPos() - dyPush * rSpeed * 0.1f);
                if (step == 10) {
                    playerA.enableJoystick();
                    playerB.enableJoystick();
                }
            }, delay);
        }
    }

    private Player getPlayerBySlot(int slot) {
        for (Player p : players) {
            if (p.slot == slot) return p;
        }
        return null;
    }

    // En app/src/main/java/com/example/smash_ride/features/game/GameView.java

    private void updatePlayerFromState(Player p, DataSnapshot state) {
        if (!state.exists()) return;

        // Actualizar vidas y kills
        Integer lv = state.child("lives").getValue(Integer.class);
        if (lv != null) p.setLives(lv);

        Integer kl = state.child("kills").getValue(Integer.class);
        if (kl != null) p.setKills(kl);

        Boolean inv = state.child("inv").getValue(Boolean.class);
        if (inv != null) p.setInvincibleByNetwork(inv);

        Integer lvLost = state.child("livesLost").getValue(Integer.class);
        if (lvLost != null && lvLost != p.getLivesLostMatch() && p.slot == mySlot) vibratePhoneThrottled();
        if (lvLost != null) p.setLivesLostMatch(lvLost);

        Integer hitsDeal = state.child("hitsDeal").getValue(Integer.class);
        if (hitsDeal != null && hitsDeal != p.getHitsDealtMatch() && p.slot == mySlot) vibratePhoneThrottled();
        if (hitsDeal != null) p.setHitsDealtMatch(hitsDeal);

        // Posición
        Float rx = state.child("relX").getValue(Float.class);
        Float ry = state.child("relY").getValue(Float.class);
        Float ang = state.child("angle").getValue(Float.class);

        if (rx != null && ry != null) {
            float newTargetX = centerX + rx;
            float newTargetY = centerY + ry;

            // --- SOLUCIÓN AL TELETRANSPORTE ---
            // Calculamos la distancia entre donde está el dibujo y donde dice el maestro que debe estar
            float distance = (float) Math.hypot(newTargetX - p.getXPos(), newTargetY - p.getYPos());

            if (distance > 150f) {
                // Si la distancia es mayor a 150px, es un respawn. Teletransportamos.
                p.snapToPosition(newTargetX, newTargetY);
            } else {
                // Si es un movimiento corto, seguimos usando interpolación suave
                p.setRemoteTarget(newTargetX, newTargetY);
            }
        }

        if (ang != null) p.setAngle(ang);
    }

    // Mueve la lógica de input aquí para que update() sea legible
    private void processLocalInput(long delta, Player player1) {
        if (player1.isColliding()) {
            if (boostPointerId == -1 && chargeAvailableMs < FULL_CHARGE_MS) {
                chargeAvailableMs = Math.min(FULL_CHARGE_MS, chargeAvailableMs + (long)(delta / RECHARGE_MULTIPLIER));
            }
            player1.setSpeed(0f);
        } else {
            if (boostPointerId != -1) {
                if (chargeAvailableMs > 0 && boostStoredMs < MAX_BOOST_MS) {
                    long transfer = Math.min(delta, Math.min(chargeAvailableMs, MAX_BOOST_MS - boostStoredMs));
                    chargeAvailableMs -= transfer;
                    boostStoredMs += transfer;
                }
                boostActive = boostStoredMs > 0;
            } else {
                boostActive = false;
                boostStoredMs = 0L;
                if (chargeAvailableMs < FULL_CHARGE_MS) {
                    chargeAvailableMs = Math.min(FULL_CHARGE_MS, chargeAvailableMs + (long)(delta / RECHARGE_MULTIPLIER));
                }
            }
            float baseSpeed = joystick.getSpeed(player1);
            float effectiveSpeed = (boostActive && boostStoredMs > 0) ?
                    Math.min(BOOST_SPEED_CAP, baseSpeed * BOOST_MULTIPLIER) : baseSpeed;
            if (boostActive) boostStoredMs -= Math.min(delta, boostStoredMs);
            player1.setSpeed(effectiveSpeed);
            player1.setAngle(joystick.getAngle(player1));
        }
    }

    private void endMatchAndShowRanking() {
        ended = true;
        isPlaying = false;

        // Al terminar el juego, volvemos a la música de menú
        SoundManager.getInstance().playMenuMusic(getContext());

        new Handler(Looper.getMainLooper()).post(() -> {
            if (gameOverListener != null) gameOverListener.onGameOver();
        });
    }

    private void vibratePhoneThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastVibrationTimeMs < VIBRATION_THROTTLE_MS) return;
        lastVibrationTimeMs = now;
        vibratePhone();

        SoundManager.getInstance().playCollisionSound();
    }

    private void vibratePhone() {
        try {
            Context ctx = getContext();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    Vibrator vibrator = vm.getDefaultVibrator();
                    if (vibrator != null && vibrator.hasVibrator()) {
                        vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                    return;
                }
            }

            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(VIBRATION_DURATION_MS);
                }
            }
        } catch (Exception e) {
            Log.w("GameView", "Vibration failed: " + e.getMessage());
        }
    }

    public void resume() {
        isPlaying = true;
        gameThread = new Thread(this);
        gameThread.start();

        // REGLA ARQUITECTÓNICA: La música de juego empieza AQUÍ,
        // cuando el hilo de ejecución del juego arranca de verdad.
        SoundManager.getInstance().playGameMusic(getContext());
    }

    public void pause() {
        isPlaying = false;
        if (gameThread != null) {
            try {
                gameThread.join();
            } catch (InterruptedException e) {
                Log.e("GameView", "Error while pausing: " + e.getMessage());
            }
        }
        // Pausamos la música si el juego se pausa
        SoundManager.getInstance().pauseMusic();
    }

    public void setGameOverListener(GameOverListener listener) {
        this.gameOverListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);

//        Player player1 = getPlayerBySlot(offline ? 0 : mySlot);
//        if (player1 == null || player1.isDestroyed()) return true;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float x = event.getX(pointerIndex);
                float y = event.getY(pointerIndex);
                if (joystickPointerId == -1) {
                    joystickPointerId = pointerId;
                    joystick.touchDown(x, y);
                } else if (boostPointerId == -1) {
                    boostPointerId = pointerId;
                }
            } break;

            case MotionEvent.ACTION_MOVE:
                if (joystickPointerId != -1) {
                    int idx = event.findPointerIndex(joystickPointerId);
                    if (idx != -1) {
                        float mx = event.getX(idx);
                        float my = event.getY(idx);
                        joystick.touchMove(mx, my);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (pointerId == joystickPointerId) {
                    joystick.touchUp();
                    joystickPointerId = -1;
                } else if (pointerId == boostPointerId) {
                    boostPointerId = -1;
                    boostStoredMs = 0L;
                    boostActive = false;
                }
                break;
        }
        return true;
    }

    public Player getLocalPlayer() {
        return getPlayerBySlot(offline ? 0 : mySlot);
    }
}
