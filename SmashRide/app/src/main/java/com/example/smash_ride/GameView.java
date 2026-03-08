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
        radius = 500; // Adjust the radius as needed
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
            update(); // Only update Player 1
            draw();
        }
    }

    private void update() {
        Player player1 = players.get(0); // Control only player 1

        if (!player1.isColliding()) {
            player1.setSpeed(joystick.getSpeed(player1)); // Update speed if not colliding
            player1.setAngle(joystick.getAngle(player1)); // Update angle if not colliding
        }

        player1.update(); // Update position of Player 1

        // Check collisions with boundaries
        checkCollision(player1);
        // Check collisions with other players
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
                    player.draw(canvas); // Draw all players
                }
                joystick.draw(canvas); // Draw the joystick
            }
        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void checkCollision(Player player) {
        float carCenterX = player.getXPos() + 25; // Adjust based on player size
        float carCenterY = player.getYPos() + 25; // Adjust based on player size

        float distanceFromCenter = (float) Math.sqrt(Math.pow(carCenterX - centerX, 2) + Math.pow(carCenterY - centerY, 2));

        if (distanceFromCenter >= radius) {
            player.resetPosition(); // Reset position if colliding with boundaries
        }
    }

    private void checkPlayerCollisions(Player playerA) {
        for (int i = 1; i < players.size(); i++) { // Start from index 1 to avoid player 1
            Player playerB = players.get(i);

            float dx = playerA.getXPos() - playerB.getXPos();
            float dy = playerA.getYPos() - playerB.getYPos();
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            float collisionDistance = 50; // Adjust based on player size

            if (distance < collisionDistance) {
                handleCollision(playerA, playerB); // Handle collision between players
            }
        }
    }

    private void handleCollision(Player playerA, Player playerB) {
        float dx = playerA.getXPos() - playerB.getXPos();
        float dy = playerA.getYPos() - playerB.getYPos();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        // Prevent division by zero
        if (distance == 0) return;

        // Calculate normalized direction vector
        float pushX = dx / distance;
        float pushY = dy / distance;

        // Determine speeds
        float speedA = playerA.getSpeed();
        float speedB = playerB.getSpeed();

        // Check if both players have the same speed
        if (speedA == speedB) {
            // Take the same retreat action for both players
            applyRetroceForBoth(playerA, playerB, pushX, pushY, speedA);
        } else {
            // Determine which player has the lesser speed and the greater speed
            Player affectedPlayer = playerA.getSpeed() < playerB.getSpeed() ? playerA : playerB;
            Player fasterPlayer = playerA.getSpeed() >= playerB.getSpeed() ? playerA : playerB;

            // Use the speed of the faster player for retreat calculation
            float retreatSpeed = fasterPlayer.getSpeed(); // Adjust factor for tuning

            // Change the angle of the affected player to the direction they'll move
            affectedPlayer.setAngle((float) Math.toDegrees(Math.atan2(pushY, pushX)));
            affectedPlayer.disableJoystick(); // Disable joystick control

            // Apply the push effect over 1 second (11 steps)
            for (int i = 0; i <= 10; i++) {
                final int step = i;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    affectedPlayer.setXPos(affectedPlayer.getXPos() - pushX * retreatSpeed);
                    affectedPlayer.setYPos(affectedPlayer.getYPos() - pushY * retreatSpeed);

                    // Re-enable joystick control after finishing the push
                    if (step == 10) {
                        affectedPlayer.enableJoystick(); // Enable joystick control back
                    }
                }, step * 10); // Each step is 10 ms apart
            }
        }
    }

    // Method to handle retroce for both players
    private void applyRetroceForBoth(Player playerA, Player playerB, float pushX, float pushY, float retreatSpeed) {
        playerA.setAngle((float) Math.toDegrees(Math.atan2(pushY, pushX)));
        playerB.setAngle((float) Math.toDegrees(Math.atan2(-pushY, -pushX))); // Reverse angle for playerB

        playerA.disableJoystick();
        playerB.disableJoystick();

        // Using a handler to apply retreat over 2.5 seconds
        for (int i = 0; i <= 10; i++) { // 25 steps for 2.5 seconds
            final int step = i;

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                playerA.setXPos(playerA.getXPos() + pushX * retreatSpeed * 0.1f); // Retreat by 0.1 * speed
                playerA.setYPos(playerA.getYPos() + pushY * retreatSpeed * 0.1f);

                playerB.setXPos(playerB.getXPos() - pushX * retreatSpeed * 0.1f); // Retreat in opposite direction
                playerB.setYPos(playerB.getYPos() - pushY * retreatSpeed * 0.1f);

                // Re-enable joystick control after finishing the push for both players
                if (step == 10) {
                    playerA.enableJoystick();
                    playerB.enableJoystick();
                }
            }, step * 10); // Each step is 10 ms apart
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

        // Assuming control only for the first player using the joystick
        Player player1 = players.get(0);

        if (player1.isColliding()) return true; // Ignore touch events if player is colliding

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
