package com.example.smash_ride;

public class Player {
    private float xPos;
    private float yPos;
    private boolean invincible;
    private int lives;

    public Player() {
    }

    public Player(float xPos, float yPos, boolean invincible, int lives) {
        this.xPos = xPos;
        this.yPos = yPos;
        this.invincible = invincible;
        this.lives = lives;
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
}
