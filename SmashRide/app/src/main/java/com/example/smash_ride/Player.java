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
    private float collisionAngle; // Nueva propiedad para la dirección de colisión
    private boolean isColliding; // Nueva propiedad para estado de colisión
    private Paint paint;

    private float initialSpeed; // Almacenar velocidad inicial

    public Player(String name, float x, float y, boolean someFlag, int speed) {
        this.name = name;
        this.xPos = x;
        this.yPos = y;
        this.initialX = x; // Guardar posición inicial
        this.initialY = y; // Guardar posición inicial
        this.speed = speed;
        this.initialSpeed = speed; // Almacenar velocidad inicial
        this.paint = new Paint();
        this.paint.setColor(Color.BLUE); // Color del jugador
    }

    public void update() {
        // Actualizar posición del jugador basado en velocidad y ángulo, a menos que esté en colisión
        if (!isColliding) {
            xPos += Math.cos(Math.toRadians(angle)) * speed; // Actualizar posición X
            yPos += Math.sin(Math.toRadians(angle)) * speed; // Actualizar posición Y
        } else {
            // Aplicar retroceso basado en el ángulo de colisión
            xPos += Math.cos(Math.toRadians(collisionAngle)) * speed; // Retroceso en X
            yPos += Math.sin(Math.toRadians(collisionAngle)) * speed; // Retroceso en Y
            isColliding = false; // Reiniciar estado de colisión después del retroceso
        }
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getSpeed() {
        return speed;
    }

    public void setAngle(float angle) {
        this.angle = angle;
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
        canvas.drawCircle(xPos, yPos, 25, paint); // Representación simple como un círculo
    }

    public void resetPosition() {
        this.xPos = initialX; // Reiniciar a X inicial
        this.yPos = initialY; // Reiniciar a Y inicial
        setColliding(false); // Asegurarse de que el estado de colisión sea falso
    }

    public float getXPos() {
        return xPos;
    }

    public float getYPos() {
        return yPos;
    }

    public float getAngle() {
        return angle; // Retornar ángulo actual del jugador
    }

    public float getInitialSpeed() {
        return initialSpeed; // Recuperar velocidad inicial
    }

    public void setColliding(boolean colliding) {
        isColliding = colliding;
    }

    public boolean isColliding() {
        return isColliding;
    }

    public void disableJoystick() {
        isColliding = true; // Prevent movement
    }

    public void enableJoystick() {
        isColliding = false; // Re-enable movement
    }
}
