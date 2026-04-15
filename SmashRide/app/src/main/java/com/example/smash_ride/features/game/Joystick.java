package com.example.smash_ride.features.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class Joystick {
    private float controlX, controlY;
    private float joystickX, joystickY;
    private boolean isActive;
    private static final int CROSSHAIR_RADIUS = 100;

    public Joystick() {
        this.isActive = false;
    }

    public void touchDown(float x, float y) {
        controlX = x;
        controlY = y;
        joystickX = controlX;
        joystickY = controlY;
        isActive = true;
    }

    public void touchMove(float x, float y) {
        if (isActive) {
            float deltaX = x - controlX;
            float deltaY = y - controlY;
            float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

            if (distance > CROSSHAIR_RADIUS) {
                float ratio = CROSSHAIR_RADIUS / distance;
                joystickX = controlX + deltaX * ratio;
                joystickY = controlY + deltaY * ratio;
            } else {
                joystickX = x;
                joystickY = y;
            }
        }
    }

    public void touchUp() {
        isActive = false;
        joystickX = controlX;
        joystickY = controlY;
    }

    public float getSpeed(Player player) {
        if (player.isColliding()) return 0; // No velocidad si está colisionando

        float joystickDistanceToCenter = (float) Math.sqrt(
                Math.pow(joystickX - controlX, 2) +
                        Math.pow(joystickY - controlY, 2));

        float speed = (joystickDistanceToCenter / CROSSHAIR_RADIUS) * 10; // Velocidad máxima: 10
        return Math.min(speed, 10);
    }

    public float getAngle(Player player) {
        if (player.isColliding()) return player.getAngle(); // No cambio de ángulo si está colisionando

        return (float) Math.toDegrees(Math.atan2(joystickY - controlY, joystickX - controlX));
    }

    public boolean isActive() {
        return isActive;
    }

    public void draw(Canvas canvas) {
        if (isActive) {
            canvas.drawCircle(controlX, controlY, CROSSHAIR_RADIUS, createPaint(Color.argb(128, 128, 128, 128)));
            canvas.drawCircle(joystickX, joystickY, 30, createPaint(Color.RED));
        }
    }

    private Paint createPaint(int color) {
        Paint paint = new Paint();
        paint.setColor(color);
        return paint;
    }
}
