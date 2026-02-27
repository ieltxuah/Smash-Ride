package com.example.smash_ride;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class Player {
    private String nombre;
    private float xPos;
    private float yPos;
    private boolean invincible;
    private int lives;
    private float speed;
    private float angle;

    public Player() {
    }

    public Player(String nombre, float xPos, float yPos, boolean invincible, int lives) {
        this.nombre = nombre;
        this.xPos = xPos;
        this.yPos = yPos;
        this.invincible = invincible;
        this.lives = lives;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public float getXPos() {
        return xPos;
    }

    public void setXPos(float xPos) {
        this.xPos = xPos;
    }

    public float getYPos() {
        return yPos;
    }

    public void setYPos(float yPos) {
        this.yPos = yPos;
    }

    public boolean getInvincible() {
        return invincible;
    }

    public void setInvincible(boolean invincible) {
        this.invincible = invincible;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = lives;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getAngle() {
        return angle;
    }

    public void setAngle(float angle) {
        this.angle = angle;
    }

    public void update() {
        if (speed > 0) {
            xPos += speed * Math.cos(Math.toRadians(angle));
            yPos += speed * Math.sin(Math.toRadians(angle));
        }
    }

    public void draw(Canvas canvas) {
        canvas.save();
        canvas.rotate(angle, xPos + 25, yPos + 25);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        canvas.drawRect(xPos, yPos, xPos + 50, yPos + 50, paint);
        canvas.restore();
    }

    public void resetPosition(float xPos, float yPos) {
        this.xPos = xPos;
        this.yPos = yPos;
    }
}
