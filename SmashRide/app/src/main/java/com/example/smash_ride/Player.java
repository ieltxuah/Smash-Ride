package com.example.smash_ride;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class Player {
    private String name;
    private float xPos;
    private float yPos;
    private float initialX;
    private float initialY;
    private float speed;
    private float angle;
    private Paint paint;

    public Player(String name, float x, float y, boolean someFlag, int speed) {
        this.name = name;
        this.xPos = x;
        this.yPos = y;
        this.initialX = x; // Save initial position
        this.initialY = y; // Save initial position
        this.speed = speed;
        this.paint = new Paint();
        this.paint.setColor(Color.BLUE); // Player color
    }

    public void update() {
        // Update player position based on speed and angle
        xPos += Math.cos(Math.toRadians(angle)) * speed; // Update X position
        yPos += Math.sin(Math.toRadians(angle)) * speed; // Update Y position
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public void setAngle(float angle) {
        this.angle = angle;
    }

    public void setXPos(float x) {
        this.xPos = x;
    }

    public void setYPos(float y) {
        this.yPos = y;
    }


    public void draw(Canvas canvas) {
        canvas.drawCircle(xPos, yPos, 25, paint); // Simple circle representation
    }

    public void resetPosition() {
        this.xPos = initialX; // Reset to initial X
        this.yPos = initialY; // Reset to initial Y
    }

    public float getXPos() {
        return xPos;
    }

    public float getYPos() {
        return yPos;
    }

    public float getAngle() {
        return angle; // Return current angle of the player
    }
}
