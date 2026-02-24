package com.example.smash_ride;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GameView extends SurfaceView implements Runnable {
    private GameOverListener gameOverListener;
    private Thread gameThread;
    private boolean isPlaying;
    private Paint paint;
    private SurfaceHolder surfaceHolder;
    private Player player;
    private Joystick joystick;
    private GameArea gameArea;

    private float centerX;
    private float centerY;
    private final float radius = 500; // Fixed radius

    private static final int MAX_TOUCHES = 2;
    private boolean[] isTouchActive = new boolean[MAX_TOUCHES];
    private int[] touchPointers = new int[MAX_TOUCHES];
    private long secondFingerDownTime;
    private boolean isReducingSpeed = false;
    private int collisionCount = 0;

    private static final long REDUCTION_DURATION = 750; // in milliseconds
    private static final int REDUCTION_STEPS = 30;
    private float initialSpeed;
    private float speedReductionStep;
    private long reductionStartTime;
    private Handler uiHandler;

    public GameView(Context context) {
        super(context);
        initialize(context);
    }

    private void initialize(Context context) {
        surfaceHolder = getHolder();
        paint = new Paint();
        paint.setColor(Color.GRAY);
        isPlaying = false;
        player = new Player(getWidth() / 2, getHeight() / 2, false, 5);
        joystick = new Joystick();
        gameArea = new GameArea(centerX, centerY, radius);
        uiHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2;
        centerY = h / 2;
        gameArea = new GameArea(centerX, centerY, radius);
    }

    @Override
    public void run() {
        while (isPlaying) {
            update();
            draw();
        }
    }

    private void update() {
        player.update();
        checkCollision();
    }

    private void draw() {
        Canvas canvas = null;
        try {
            if (surfaceHolder.getSurface().isValid()) {
                canvas = surfaceHolder.lockCanvas();
                canvas.drawColor(Color.WHITE);
                gameArea.draw(canvas);
                player.draw(canvas);
                joystick.draw(canvas);
            }
        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void checkCollision() {
        float carCenterX = player.getXPos() + 25;
        float carCenterY = player.getYPos() + 25;

        float distanceFromCenter = (float) Math.sqrt(Math.pow(carCenterX - centerX, 2) + Math.pow(carCenterY - centerY, 2));
        if (distanceFromCenter >= radius) {
            player.resetPosition(centerX, centerY);
            collisionCount++;

            if (collisionCount >= 5 && gameOverListener != null) {
                uiHandler.post(gameOverListener::onGameOver);
            }
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

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handleTouchDown(event, pointerIndex, pointerId);
                break;
            case MotionEvent.ACTION_MOVE:
                handleTouchMove(event);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                handleTouchUp();
                break;
        }
        return true;
    }

    private void handleTouchDown(MotionEvent event, int pointerIndex, int pointerId) {
        if (!isTouchActive[0]) {
            joystick.touchDown(event.getX(pointerIndex), event.getY(pointerIndex));
            touchPointers[0] = pointerId; // Save pointer ID
            isTouchActive[0] = true;
        } else if (!isTouchActive[1]) {
            touchPointers[1] = pointerId; // Save pointer ID for second finger
            secondFingerDownTime = SystemClock.elapsedRealtime();
            isReducingSpeed = true;
            startSpeedReduction();
            isTouchActive[1] = true;
        }
    }

    private void handleTouchMove(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            int id = event.getPointerId(i);
            float x = event.getX(i);
            float y = event.getY(i);

            // Check if the first touch is active
            if (id == touchPointers[0]) {
                joystick.touchMove(x, y);
                if (isReducingSpeed)
                    reduceSpeedProgressively();
                else
                    player.setSpeed(joystick.getSpeed());
                Log.d("joystick.getAngle(player.getAngle())", "handleTouchMove: "+joystick.getAngle(player.getAngle()));
                player.setAngle(joystick.getAngle(player.getAngle()));
            }
            // Check if the second touch is active
            else if (id == touchPointers[1]) {
                if (!isReducingSpeed) {
                    isReducingSpeed = true;
                    startSpeedReduction();
                } else
                    reduceSpeedProgressively();
            }
        }
    }

    private void handleTouchUp() {
        if (isTouchActive[1]) {
            processSecondTouchUp();
        } else {
            joystick.touchUp();
            isTouchActive[0] = false;
            if (!isReducingSpeed) {
                player.setSpeed(0);
            }
        }
    }

    private void processSecondTouchUp() {
        long elapsedTime = SystemClock.elapsedRealtime() - secondFingerDownTime;
        float impulseDuration = Math.min(elapsedTime / 300f, 1f);
        activateImpulse(impulseDuration);
        isReducingSpeed = false;
        isTouchActive[1] = false;
    }

    private void startSpeedReduction() {
        initialSpeed = player.getSpeed();
        speedReductionStep = initialSpeed / REDUCTION_STEPS;
        reductionStartTime = SystemClock.elapsedRealtime();
        reduceSpeedProgressively();
    }

    private void reduceSpeedProgressively() {
        uiHandler.postDelayed(() -> {
            long elapsedTime = SystemClock.elapsedRealtime() - reductionStartTime;
            float proportion = (float) elapsedTime / REDUCTION_DURATION;

            if (proportion < 1) {
                float newSpeed = Math.max(0, initialSpeed - (speedReductionStep * (elapsedTime / (REDUCTION_DURATION / REDUCTION_STEPS))));
                player.setSpeed(newSpeed);
                reduceSpeedProgressively();
            } else {
                player.setSpeed(0);
                isReducingSpeed = false;
            }
        }, REDUCTION_DURATION / REDUCTION_STEPS);
    }

    private void activateImpulse(float duration) {
        final float impulseSpeed = 20f;
        player.setSpeed(impulseSpeed);

        uiHandler.postDelayed(() -> {
            player.setSpeed(joystick.getSpeed());
        }, (long) (duration * 300));
    }
}
