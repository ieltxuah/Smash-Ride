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

import androidx.core.content.res.ResourcesCompat;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.constants.AppConstants;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.translation.TranslationManager;

import java.util.List;

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

    public GameView(Context context, List<Player> players, int color) {
        super(context);
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

        if (ended) return;

        if (gameMode == GameMode.TIMER) {
            timerMs -= delta;
            if (timerMs <= 0) {
                endMatchAndShowRanking();
                return;
            }
        }

        Player player1 = null;
        if (!players.isEmpty() && !players.get(0).isDestroyed()) player1 = players.get(0);
        if (player1 == null) {
            if (gameMode == GameMode.LIVES) {
                endMatchNoWinner();
                return;
            } else {
                endMatchAndShowRanking();
                return;
            }
        }

        if (player1.isColliding()) {
            if (boostPointerId == -1 && chargeAvailableMs < FULL_CHARGE_MS) {
                long gain = (long) (delta / RECHARGE_MULTIPLIER);
                chargeAvailableMs = Math.min(FULL_CHARGE_MS, chargeAvailableMs + gain);
            }
            player1.setSpeed(0f);
        } else {
            // Gestión de carga de boost
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
                    long gain = (long) (delta / RECHARGE_MULTIPLIER);
                    chargeAvailableMs = Math.min(FULL_CHARGE_MS, chargeAvailableMs + gain);
                }
            }

            float baseSpeed = joystick.getSpeed(player1);
            float effectiveSpeed = baseSpeed;
            if (boostActive && boostStoredMs > 0) {
                effectiveSpeed = Math.min(BOOST_SPEED_CAP, baseSpeed * BOOST_MULTIPLIER);
                long consume = Math.min(delta, boostStoredMs);
                boostStoredMs -= consume;
                if (boostStoredMs <= 0) {
                    boostStoredMs = 0;
                    boostActive = false;
                }
            }

            if (!player1.isColliding()) {
                player1.setSpeed(effectiveSpeed);
                player1.setAngle(joystick.getAngle(player1));
            } else {
                player1.setSpeed(0f);
            }
        }

        for (Player p : players) {
            if (p.isDestroyed()) continue;
            p.update();
        }

        if (offline) {
            for (int i = 0; i < players.size(); i++) {
                Player a = players.get(i);
                // Si el jugador 'a' está destruido o es invencible, no puede chocar ni ser chocado
                if (a.isDestroyed() || a.isInvincible()) continue;

                for (int j = i + 1; j < players.size(); j++) {
                    Player b = players.get(j);
                    // Si el jugador 'b' es invencible o está destruido, ignoramos
                    if (b.isDestroyed() || b.isInvincible()) continue;

                    float dx = a.getXPos() - b.getXPos();
                    float dy = a.getYPos() - b.getYPos();
                    float dist = (float) Math.hypot(dx, dy);
                    if (dist < 50f) handleCollision(a, b);
                }
            }
        } else {
            if (player1 != null && !player1.isInvincible()) {
                checkPlayerCollisions(player1);
            }
        }

        for (Player p : players) {
            if (p.isDestroyed()) continue;
            checkCollision(p);
        }

        int aliveCount = 0;
        Player lastAlive = null;
        for (Player p : players) {
            if (!p.isDestroyed()) { aliveCount++; lastAlive = p; }
        }

        if (gameMode == GameMode.LIVES) {
            if (aliveCount <= 1) {
                if (aliveCount == 1 && lastAlive != null) {
                    endMatchWithWinner(lastAlive);
                } else {
                    endMatchNoWinner();
                }
            }
        } else if (gameMode == GameMode.TIMER) {
            // in TIMER mode we only end when timer runs out OR if <=1 alive we go to ranking
            timerMs -= delta;
            if (timerMs <= 0) {
                timerMs = 0;
                // Aquí puedes llamar a tu lógica de fin de partida por tiempo
                endMatchAndShowRanking();
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
        Player p1 = players.get(0);

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
            leftText = labelLives + p1.getLives();
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
        String killsText = labelKills + p1.getKills();
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

    private void checkCollision(Player player) {
        if (player == null || player.isDestroyed()) return;

        // Calculamos el centro del sprite (asumiendo tamaño 50x50 como en tus hitboxes)
        float carCenterX = player.getXPos();
        float carCenterY = player.getYPos();

        float dx = carCenterX - centerX;
        float dy = carCenterY - centerY;
        float distanceFromCenter = (float) Math.hypot(dx, dy);

        if (distanceFromCenter >= radius) {
            if (player.isInvincible()) {
                // --- NUEVA LÓGICA: Pared física para invencibles ---
                // Calculamos el ángulo desde el centro de la luna hacia el jugador
                double angle = Math.atan2(dy, dx);

                // Reposicionamos al jugador exactamente en el borde del radio
                float newX = centerX + (float) (Math.cos(angle) * (radius - 1));
                float newY = centerY + (float) (Math.sin(angle) * (radius - 1));

                player.setXPos(newX);
                player.setYPos(newY);

                // Opcional: Detener la velocidad para que no "vibre" contra la pared
                player.setSpeed(0);

            } else {
                // --- Lógica normal: Morir o resetear ---
                // Si alguien lo golpeó antes de caer, ese alguien se lleva la kill
                Player killer = player.getLastHitter();
                if (killer != null && killer != player) {
                    killer.addKill();
                }

                if (gameMode == GameMode.LIVES) {
                    if (!player.isDestroyed()) {
                        player.loseLife();
                        if (players.indexOf(player) == 0) vibratePhoneThrottled();
                    }
                }
                player.resetPosition(); // Esto resetea lastHitter a null
            }
        }
    }

    private void checkPlayerCollisions(Player playerA) {
        if (playerA == null || playerA.isDestroyed()) return;
        for (int i = 1; i < players.size(); i++) {
            Player playerB = players.get(i);
            if (playerB.isDestroyed()) continue;
            float dx = playerA.getXPos() - playerB.getXPos();
            float dy = playerA.getYPos() - playerB.getYPos();
            float distance = (float) Math.hypot(dx, dy);
            float collisionDistance = 50f;
            if (distance < collisionDistance) handleCollision(playerA, playerB);
        }
    }

    private void handleCollision(Player playerA, Player playerB) {
        if (playerA == null || playerB == null) return;
        if (playerA.isDestroyed() || playerB.isDestroyed()) return;

        float dx = playerA.getXPos() - playerB.getXPos();
        float dy = playerA.getYPos() - playerB.getYPos();
        float distance = (float) Math.hypot(dx, dy);
        if (distance == 0f) distance = 0.001f;

        int idxA = players.indexOf(playerA);
        int idxB = players.indexOf(playerB);
        if (idxA == 0 || idxB == 0) vibratePhoneThrottled();

        float pushX = dx / distance;
        float pushY = dy / distance;
        float speedA = playerA.getSpeed();
        float speedB = playerB.getSpeed();

        if (playerA.getSpeed() > playerB.getSpeed()) {
            playerB.setLastHitter(playerA); // A golpeó a B
        } else if (playerB.getSpeed() > playerA.getSpeed()) {
            playerA.setLastHitter(playerB); // B golpeó a A
        } else {
            playerA.setLastHitter(playerB);
            playerB.setLastHitter(playerA);
        }

        if (speedA == speedB) {
            applyRetroceForBoth(playerA, playerB, pushX, pushY, Math.max(speedA, speedB));
        } else {
            Player affected = playerA.getSpeed() < playerB.getSpeed() ? playerA : playerB;
            Player faster = playerA.getSpeed() >= playerB.getSpeed() ? playerA : playerB;
            float retreatSpeed = faster.getSpeed();
            affected.setAngle((float) Math.toDegrees(Math.atan2(pushY, pushX)));
            affected.disableJoystick();
            for (int i = 0; i <= 10; i++) {
                final int step = i;
                final long delay = step * 10L;
                final float dxPush = pushX;
                final float dyPush = pushY;
                final float rSpeed = retreatSpeed;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (affected.isDestroyed()) return;
                    affected.setXPos(affected.getXPos() - dxPush * rSpeed);
                    affected.setYPos(affected.getYPos() - dyPush * rSpeed);
                    float cx = affected.getXPos() + 25;
                    float cy = affected.getYPos() + 25;
                    float dist = (float) Math.hypot(cx - centerX, cy - centerY);
                    if (dist >= radius) {
                        if (!affected.isDestroyed()) {
                            if (gameMode == GameMode.LIVES) {
                                affected.loseLife();
                                if (affected.isDestroyed() && !faster.isDestroyed() && faster != affected) {
                                    faster.addKill();
                                }
                            } else {
                                // TIMER: do not destroy, just addkill
                                if (!faster.isDestroyed() && faster != affected)
                                    faster.addKill();
                            }
                        }
                        affected.resetPosition();
                    }
                    if (step == 10) affected.enableJoystick();
                }, delay);
            }
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

    private void endMatchWithWinner(Player winner) {
        ended = true;
        isPlaying = false;

        // Al terminar el juego, volvemos a la música de menú
        SoundManager.getInstance().playMenuMusic(getContext());

        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (gameOverListener != null) gameOverListener.onGameOver();
        });
    }

    private void endMatchNoWinner() {
        ended = true;
        isPlaying = false;

        // Al terminar el juego, volvemos a la música de menú
        SoundManager.getInstance().playMenuMusic(getContext());

        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (gameOverListener != null) gameOverListener.onGameOver();
        });
    }

    private void endMatchAndShowRanking() {
        ended = true;
        isPlaying = false;

        // Al terminar el juego, volvemos a la música de menú
        SoundManager.getInstance().playMenuMusic(getContext());

        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (gameOverListener != null) gameOverListener.onGameOver();
        });
    }

    private void vibratePhoneThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastVibrationTimeMs < VIBRATION_THROTTLE_MS) return;
        lastVibrationTimeMs = now;
        vibratePhone();
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

        Player player1 = (!players.isEmpty()) ? players.get(0) : null;
        if (player1 == null || player1.isDestroyed()) return true;

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
}
