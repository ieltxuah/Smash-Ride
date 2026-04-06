package com.example.smash_ride.features.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class Player {
    public String name;
    private float xPos;
    private float yPos;
    private final float initialX;
    private final float initialY;
    private float speed;
    private float angle;
    private float collisionAngle; // dirección de colisión
    private boolean isColliding; // estado de colisión (también usado para bloquear input/movimiento)
    private final Paint paint;

    private final float initialSpeed; // velocidad inicial

    // Estado de juego
    private int lives = 6;
    private int kills = 0;
    private boolean destroyed = false; // fuera del juego

    public Player(String name, float x, float y, boolean someFlag, int speed) {
        this.name = name;
        this.xPos = x;
        this.yPos = y;
        this.initialX = x;
        this.initialY = y;
        this.speed = speed;
        this.initialSpeed = speed;
        this.paint = new Paint();
        this.paint.setColor(Color.BLUE);
        this.isColliding = false;
    }

    // helper to draw extra UI (lives)
    public void drawWithStatus(Canvas canvas) {
        if (destroyed) return;
        canvas.drawCircle(xPos, yPos, 25, paint);
        Paint text = new Paint();
        text.setColor(Color.BLACK);
        text.setTextSize(20f);
        canvas.drawText(String.valueOf(lives), xPos - 6, yPos + 6, text);
    }

    public void update() {
        if (destroyed) return; // jugador eliminado no se actualiza
        if (!isColliding) {
            xPos += Math.cos(Math.toRadians(angle)) * speed;
            yPos += Math.sin(Math.toRadians(angle)) * speed;
        } else {
            // retroceso basado en collisionAngle (usado temporalmente)
            xPos += Math.cos(Math.toRadians(collisionAngle)) * speed;
            yPos += Math.sin(Math.toRadians(collisionAngle)) * speed;
            // no reiniciamos isColliding aquí automáticamente: controlarlo desde quien provoca la colisión
        }
    }

    public void setSpeed(float speed) {
        if (destroyed) return;
        this.speed = speed;
    }

    public float getSpeed() {
        return destroyed ? 0f : speed;
    }

    public void setAngle(float angle) {
        if (destroyed) return;
        this.angle = angle;
    }

    public float getAngle() {
        return angle;
    }

    public void setCollisionAngle(float angle) {
        this.collisionAngle = angle;
    }

    public void setXPos(float x) {
        this.xPos = x;
    }

    public void setYPos(float y) {
        this.yPos = y;
    }

    public void draw(Canvas canvas) {
        if (destroyed) return; // no dibujar si eliminado
        canvas.drawCircle(xPos, yPos, 25, paint);

//        // Cómo usarlo (ejemplo en Activity o donde cargues sprites):
//        Bitmap original = BitmapFactory.decodeResource(getResources(), R.drawable.star_sprite);
//
//        // Ejemplos de hues: rojo ~0, verde ~120, azul ~240
//        Bitmap rojo   = SpriteColorizer.recolorByHue(original, 0f, Color.rgb(96,24,0), 20f);
//        Bitmap verde  = SpriteColorizer.recolorByHue(original, 120f, Color.rgb(96,24,0), 20f);
//        Bitmap azul   = SpriteColorizer.recolorByHue(original, 240f, Color.rgb(96,24,0), 20f);
    }

    public void resetPosition() {
        // Si el jugador está destruido, lo sacamos del área visible para evitar colisiones visuales
        if (destroyed) {
            this.xPos = -1000f;
            this.yPos = -1000f;
            setColliding(true);
            setSpeed(0f);
            return;
        }
        this.xPos = initialX;
        this.yPos = initialY;
        setColliding(false);
        // opcional: restaurar velocidad base al resetear (no al morir)
        this.speed = initialSpeed;
    }

    public float getXPos() {
        return xPos;
    }

    public float getYPos() {
        return yPos;
    }

    public float getInitialSpeed() {
        return initialSpeed;
    }

    public void setColliding(boolean colliding) {
        if (destroyed) {
            this.isColliding = true;
            this.speed = 0f;
            return;
        }
        this.isColliding = colliding;
        if (colliding) this.speed = 0f; // bloquear movimiento inmediato cuando colisiona
    }

    public boolean isColliding() {
        return isColliding || destroyed;
    }

    public void disableJoystick() {
        // marcar como colisionado / sin control
        isColliding = true;
        speed = 0f;
    }

    public void enableJoystick() {
        if (destroyed) return;
        isColliding = false;
        // no restauramos speed aquí; quien habilite el movimiento puede setSpeed según joystick
    }

    // Lives / kills API
    public int getLives() { return lives; }
    public void setLives(int l) {
        lives = l;
        if (l <= 0) markDestroyed();
    }

    public void loseLife() {
        if (destroyed) return;
        if (lives > 0) lives--;
        if (lives <= 0) {
            markDestroyed();
        }
    }

    private void markDestroyed() {
        destroyed = true;
        // asegurar que no pueda moverse ni colisionar con lógica del juego
        isColliding = true;
        speed = 0f;
        // quitar del área visible
        this.xPos = -1000f;
        this.yPos = -1000f;
    }

    public int getKills() { return kills; }
    public void addKill() {
        if (destroyed) return;
        kills++;
    }

    public boolean isDestroyed() { return destroyed; }

    public void destroy() {
        markDestroyed();
    }

    public void reviveToInitial() {
        destroyed = false;
        lives = 6;
        kills = 0;
        this.xPos = initialX;
        this.yPos = initialY;
        this.speed = initialSpeed;
        this.isColliding = false;
    }
}
