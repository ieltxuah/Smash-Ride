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
    private List<Player> players;
    private Joystick joystick;
    private GameArea gameArea;

    private float centerX;
    private float centerY;
    private float radius;

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

        centerX = getWidth() / 2;
        centerY = getHeight() / 2;
        radius = 500; // Ajusta el radio según sea necesario
        gameArea = new GameArea(centerX, centerY, radius);
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
            update(); // Solo actualiza Player 1
            draw();
        }
    }

    private void update() {
        Player player1 = players.get(0); // Solo controla el jugador 1
        player1.setSpeed(joystick.getSpeed());
        player1.setAngle(joystick.getAngle(player1.getAngle()));
        player1.update(); // Actualizar posición de Player 1

        // Comprobar colisiones con los límites
        checkCollision(player1);
        // Comprobar colisiones con otros jugadores
        checkPlayerCollisions(player1);
    }

    private void draw() {
        Canvas canvas = null;
        try {
            if (surfaceHolder.getSurface().isValid()) {
                canvas = surfaceHolder.lockCanvas();
                canvas.drawColor(Color.WHITE);

                if (gameArea != null) {
                    gameArea.draw(canvas);
                } else {
                    Log.e("GameView", "GameArea is null, cannot draw");
                }

                for (Player player : players) {
                    player.draw(canvas); // Dibuja todos los jugadores
                }
                joystick.draw(canvas); // Dibuja el joystick
            }
        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void checkCollision(Player player) {
        float carCenterX = player.getXPos() + 25; // Ajusta según el tamaño del jugador
        float carCenterY = player.getYPos() + 25; // Ajusta según el tamaño del jugador

        float distanceFromCenter = (float) Math.sqrt(Math.pow(carCenterX - centerX, 2) + Math.pow(carCenterY - centerY, 2));

        if (distanceFromCenter >= radius) {
            player.resetPosition(); // Reiniciar posición si colisiona con los límites
        }
    }

    private void checkPlayerCollisions(Player playerA) {
        for (int i = 1; i < players.size(); i++) { // Comienza desde 1 para evitar el jugador 1
            Player playerB = players.get(i);

            float dx = playerA.getXPos() - playerB.getXPos();
            float dy = playerA.getYPos() - playerB.getYPos();
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            float collisionDistance = 50; // Ajusta según el tamaño del jugador

            if (distance < collisionDistance) {
                handleCollision(playerA, playerB);
            }
        }
    }

    private void handleCollision(Player playerA, Player playerB) {
        // Calcular el vector de separación
        float dx = playerA.getXPos() - playerB.getXPos();
        float dy = playerA.getYPos() - playerB.getYPos();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        // Normalizar el vector
        if (distance > 0) { // Evitar división por cero
            float pushX = dx / distance;
            float pushY = dy / distance;

            // Ajustar posiciones de los jugadores
            float pushDistance = 15; // Ajustar la intensidad del empuje
            playerA.setXPos(playerA.getXPos() + pushX * pushDistance);
            playerA.setYPos(playerA.getYPos() + pushY * pushDistance);
            playerB.setXPos(playerB.getXPos() - pushX * pushDistance);
            playerB.setYPos(playerB.getYPos() - pushY * pushDistance);
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
                joystick.touchDown(event.getX(pointerIndex), event.getY(pointerIndex));
                break;
            case MotionEvent.ACTION_MOVE:
                joystick.touchMove(event.getX(pointerIndex), event.getY(pointerIndex));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                joystick.touchUp();
                break;
        }
        return true;
    }
}
