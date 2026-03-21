package com.example.smash_ride;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.List;

public class GameView extends SurfaceView implements Runnable {
    private GameOverListener gameOverListener;
    private Thread gameThread;
    private boolean isPlaying;
    private Paint paint;
    private SurfaceHolder surfaceHolder;
    private List<Player> players;
    private Joystick joystick;
    private GameArea gameArea;

    private float centerX;
    private float centerY;
    private float radius;

    // --- Boost configuration ---
    private static final long FULL_CHARGE_MS = 3000L;      // 3s to full charge
    private static final long MAX_BOOST_MS = 3000L;        // boost max duration when fully charged
    private static final float BOOST_MULTIPLIER = 2.0f;    // 2x joystick speed
    private static final float BOOST_SPEED_CAP = 30f;     // max speed while boosting
    private static final float RECHARGE_MULTIPLIER = 2.0f; // recharge takes double time (6s)

    // --- Boost runtime state ---
    private int joystickPointerId = -1; // pointer controlling joystick (first finger)
    private int boostPointerId = -1;    // pointer for boost (second finger)

    // chargeAvailableMs: cantidad actualmente almacenada (0..FULL_CHARGE_MS).
    // boostStoredMs: cantidad transferida desde la reserva mientras se mantiene el dedo; consumida mientras boost activo.
    private long chargeAvailableMs = FULL_CHARGE_MS; // start fully charged
    private long boostStoredMs = 0L;
    private boolean boostActive = false;

    private long lastUpdateTimeMs = System.currentTimeMillis();

    // Vibration config
    private static final long VIBRATION_DURATION_MS = 100; // duración breve en ms
    private long lastVibrationTimeMs = 0;
    private static final long VIBRATION_THROTTLE_MS = 200; // evitar vibraciones demasiado frecuentes

    public GameView(Context context, List<Player> players) {
        super(context);
        this.players = players;
        initialize();
    }

    private void initialize() {
        surfaceHolder = getHolder();
        paint = new Paint();
        paint.setColor(Color.GRAY);
        isPlaying = false;

        joystick = new Joystick();

        // center inicial (se actualizará en onSizeChanged)
        centerX = getWidth() / 2f;
        centerY = getHeight() / 2f;
        radius = 500f;
        gameArea = new GameArea(centerX, centerY, radius);

        // start fully charged
        chargeAvailableMs = FULL_CHARGE_MS;
        boostStoredMs = 0;
        boostActive = false;
        lastUpdateTimeMs = System.currentTimeMillis();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        gameArea = new GameArea(centerX, centerY, radius);
    }

    @Override
    public void run() {
        while (isPlaying) {
            update();
            draw();
            try {
                Thread.sleep(16); // ~60fps
            } catch (InterruptedException ignored) {}
        }
    }

    private void update() {
        long now = System.currentTimeMillis();
        long delta = now - lastUpdateTimeMs;
        if (delta <= 0) delta = 16;
        lastUpdateTimeMs = now;

        Player player1 = players.get(0);

        // Si el jugador está colisionando, sólo procesamos recargas (y evitamos movimiento)
        if (player1.isColliding()) {
            // recargar lentamente si no hay dedo de boost
            if (boostPointerId == -1 && chargeAvailableMs < FULL_CHARGE_MS) {
                long gain = (long) (delta / RECHARGE_MULTIPLIER);
                chargeAvailableMs = Math.min(FULL_CHARGE_MS, chargeAvailableMs + gain);
            }
            player1.setSpeed(0f);
            player1.update(); // llama al update para que salte la deteccion de parar
            return;
        }

        // 1) Gestión de transferencia de carga mientras segundo dedo esté pulsado
        if (boostPointerId != -1) {
            // Segundo dedo PRESIONADO: transferir desde reserva a boostStored (1:1) hasta MAX_BOOST_MS
            if (chargeAvailableMs > 0 && boostStoredMs < MAX_BOOST_MS) {
                long transfer = Math.min(delta, Math.min(chargeAvailableMs, MAX_BOOST_MS - boostStoredMs));
                chargeAvailableMs -= transfer;
                boostStoredMs += transfer;
            }
            // Si hay boostStored disponible => boost activo
            boostActive = boostStoredMs > 0;
        } else {
            // Segundo dedo NO PRESIONADO: detener boostStored (boost sólo mientras se mantiene pulsado)
            boostActive = false;
            boostStoredMs = 0L;
            // recarga a velocidad reducida (1/RECHARGE_MULTIPLIER)
            if (chargeAvailableMs < FULL_CHARGE_MS) {
                long gain = (long) (delta / RECHARGE_MULTIPLIER);
                chargeAvailableMs = Math.min(FULL_CHARGE_MS, chargeAvailableMs + gain);
            }
        }

        // 2) Aplicar efecto de boost sólo si boostActive (es decir, dedo pulsado y boostStoredMs>0)
        float baseSpeed = joystick.getSpeed(player1);
        float effectiveSpeed = baseSpeed;
        if (boostActive && boostStoredMs > 0) {
            effectiveSpeed = Math.min(BOOST_SPEED_CAP, baseSpeed * BOOST_MULTIPLIER);
            // consumir boostStoredMs mientras se aplica el boost
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

        player1.update();

        checkCollision(player1);
        checkPlayerCollisions(player1);
    }

    private void draw() {
        Canvas canvas = null;
        try {
            if (surfaceHolder.getSurface().isValid()) {
                canvas = surfaceHolder.lockCanvas();
                canvas.drawColor(Color.WHITE);

                if (gameArea != null) gameArea.draw(canvas);
                else Log.e("GameView", "GameArea is null, cannot draw");

                for (Player p : players) p.draw(canvas);
                joystick.draw(canvas);

                drawBoostUI(canvas);
            }
        } finally {
            if (canvas != null) surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawBoostUI(Canvas canvas) {
        float left = 20f, top = 20f, width = 200f, height = 20f;
        Paint bg = new Paint();
        bg.setColor(Color.LTGRAY);
        canvas.drawRect(left, top, left + width, top + height, bg);

        float fraction;
        if (boostPointerId != -1 && boostStoredMs > 0) {
            // mostrando fracción del boost almacenado respecto a MAX_BOOST_MS
            fraction = (float) boostStoredMs / (float) MAX_BOOST_MS;
            Paint fg = new Paint();
            fg.setColor(Color.YELLOW);
            canvas.drawRect(left, top, left + width * fraction, top + height, fg);
        } else {
            // mostrar reserva disponible
            fraction = (float) chargeAvailableMs / (float) FULL_CHARGE_MS;
            Paint fg = new Paint();
            fg.setColor(Color.GREEN);
            canvas.drawRect(left, top, left + width * fraction, top + height, fg);
        }

        Paint border = new Paint();
        border.setStyle(Paint.Style.STROKE);
        border.setColor(Color.BLACK);
        canvas.drawRect(left, top, left + width, top + height, border);
    }

    private void checkCollision(Player player) {
        float carCenterX = player.getXPos() + 25;
        float carCenterY = player.getYPos() + 25;
        float distanceFromCenter = (float) Math.hypot(carCenterX - centerX, carCenterY - centerY);
        if (distanceFromCenter >= radius) {
            player.resetPosition();
            vibratePhoneThrottled();
        }
    }

    private void checkPlayerCollisions(Player playerA) {
        for (int i = 1; i < players.size(); i++) {
            Player playerB = players.get(i);
            float dx = playerA.getXPos() - playerB.getXPos();
            float dy = playerA.getYPos() - playerB.getYPos();
            float distance = (float) Math.hypot(dx, dy);
            float collisionDistance = 50f;
            if (distance < collisionDistance) handleCollision(playerA, playerB);
        }
    }

    private void handleCollision(Player playerA, Player playerB) {
        float dx = playerA.getXPos() - playerB.getXPos();
        float dy = playerA.getYPos() - playerB.getYPos();
        float distance = (float) Math.hypot(dx, dy);

        vibratePhoneThrottled();

        float pushX = dx / distance;
        float pushY = dy / distance;
        float speedA = playerA.getSpeed();
        float speedB = playerB.getSpeed();

        if (speedA == speedB) {
            applyRetroceForBoth(playerA, playerB, pushX, pushY, speedA);
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
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    affected.setXPos(affected.getXPos() - dxPush * rSpeed);
                    affected.setYPos(affected.getYPos() - dyPush * rSpeed);
                    if (step == 10) affected.enableJoystick();
                }, delay);
            }
        }
    }

    private void applyRetroceForBoth(Player playerA, Player playerB, float pushX, float pushY, float retreatSpeed) {
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

    private void vibratePhoneThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastVibrationTimeMs < VIBRATION_THROTTLE_MS) return;
        lastVibrationTimeMs = now;
        vibratePhone();
    }

    private void vibratePhone() {
        try {
            Context ctx = getContext();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
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
                    // deprecated but kept for very old devices
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
    }

    public void pause() {
        isPlaying = false;
        if (gameThread != null) {
            try {
                gameThread.join();
            } catch (InterruptedException e) {
                Log.e("GameView", "Error while pausing: " + e.getMessage());
            } finally {
                gameThread = null;
            }
        }
    }

    public void setGameOverListener(GameOverListener listener) {
        this.gameOverListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);

        Player player1 = players.get(0);
        if (player1.isColliding()) return true;

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
                    // la transferencia de carga para boost se maneja en update()
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
                    // detener inmediatamente cualquier boostStored y efecto
                    boostStoredMs = 0L;
                    boostActive = false;
                }
                break;
        }
        return true;
    }
}
