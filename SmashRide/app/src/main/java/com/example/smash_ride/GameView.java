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
    public enum GameMode { LIVES, TIMER }
    private GameMode gameMode = GameMode.LIVES;
    private long timerMs = 0L;
    private static final long TIMER_TOTAL_MS = 2 * 60 * 1000L;
    private boolean offline = true;

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

        centerX = getWidth() / 2f;
        centerY = getHeight() / 2f;
        radius = 500f;
        gameArea = new GameArea(centerX, centerY, radius);

        chargeAvailableMs = FULL_CHARGE_MS;
        boostStoredMs = 0;
        boostActive = false;
        lastUpdateTimeMs = System.currentTimeMillis();
        ended = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        gameArea = new GameArea(centerX, centerY, radius);
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
                if (a.isDestroyed()) continue;
                for (int j = i + 1; j < players.size(); j++) {
                    Player b = players.get(j);
                    if (b.isDestroyed()) continue;
                    float dx = a.getXPos() - b.getXPos();
                    float dy = a.getYPos() - b.getYPos();
                    float dist = (float) Math.hypot(dx, dy);
                    if (dist < 50f) handleCollision(a, b);
                }
            }
        } else {
            checkPlayerCollisions(player1);
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
            if (aliveCount <= 1) {
                endMatchAndShowRanking();
            }
        }
    }

    private void draw() {
        Canvas canvas = null;
        try {
            if (surfaceHolder.getSurface().isValid()) {
                canvas = surfaceHolder.lockCanvas();
                canvas.drawColor(Color.WHITE);

                if (gameArea != null) gameArea.draw(canvas);
                else Log.e("GameView", "GameArea is null, cannot draw");

                for (Player p : players) {
                    if (!p.isDestroyed()) p.draw(canvas);
                }
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
            fraction = (float) boostStoredMs / (float) MAX_BOOST_MS;
            Paint fg = new Paint();
            fg.setColor(Color.YELLOW);
            canvas.drawRect(left, top, left + width * fraction, top + height, fg);
        } else {
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
        if (player == null || player.isDestroyed()) return;
        float carCenterX = player.getXPos() + 25;
        float carCenterY = player.getYPos() + 25;
        float distanceFromCenter = (float) Math.hypot(carCenterX - centerX, carCenterY - centerY);
        if (distanceFromCenter >= radius) {
            if (gameMode == GameMode.LIVES) {
                if (!player.isDestroyed()) {
                    player.loseLife();
                    if (players.indexOf(player) == 0) vibratePhoneThrottled();
                }
            } else {
                // TIMER mode: do not reduce lives or mark destroyed; just reset position
            }
            player.resetPosition();
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
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
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
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (gameOverListener != null) gameOverListener.onGameOver();
        });
    }

    private void endMatchNoWinner() {
        ended = true;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (gameOverListener != null) gameOverListener.onGameOver();
        });
    }

    private void endMatchAndShowRanking() {
        ended = true;
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
