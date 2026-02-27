package com.example.smash_ride;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
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
    private List<Player> players; // List to hold players
    private Joystick joystick;
    private GameArea gameArea;

    private float centerX;
    private float centerY;
    private final float radius = 500; // Fixed radius

    private static final long REDUCTION_DURATION = 750; // in milliseconds
    private static final int REDUCTION_STEPS = 30;
    public Handler uiHandler;

    public GameView(Context context, List<Player> players) {
        super(context);
        this.players = players; // Set player list
        initialize();
    }

    private void initialize() {
        surfaceHolder = getHolder();
        paint = new Paint();
        paint.setColor(Color.GRAY);
        isPlaying = false;
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
        for (Player player : players) {
            player.update();
        }
        checkCollision();
    }

    private void draw() {
        Canvas canvas = null;
        try {
            if (surfaceHolder.getSurface().isValid()) {
                canvas = surfaceHolder.lockCanvas();
                canvas.drawColor(Color.WHITE);
                gameArea.draw(canvas);
                for (Player player : players) {
                    player.draw(canvas);
                }
                joystick.draw(canvas);
            }
        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void checkCollision() {
        Player firstPlayer = players.get(0); // Only check collision for the first player
        float carCenterX = firstPlayer.getXPos() + 25;
        float carCenterY = firstPlayer.getYPos() + 25;

        float distanceFromCenter = (float) Math.sqrt(Math.pow(carCenterX - centerX, 2) + Math.pow(carCenterY - centerY, 2));
        if (distanceFromCenter >= radius) {
            firstPlayer.resetPosition(centerX, centerY);
            // Alternatively, you can increment a collision counter for the first player here
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
        joystick.touchDown(event.getX(pointerIndex), event.getY(pointerIndex));
    }

    private void handleTouchMove(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            int id = event.getPointerId(i);
            float x = event.getX(i);
            float y = event.getY(i);

            // Control only the first player
            if (id == 0) {
                joystick.touchMove(x, y);
                players.get(0).setSpeed(joystick.getSpeed());
                players.get(0).setAngle(joystick.getAngle(players.get(0).getAngle()));
            }
        }
    }

    private void handleTouchUp() {
        joystick.touchUp(); // Release joystick controls
        players.get(0).setSpeed(0); // Stop the first player's speed
    }
}
