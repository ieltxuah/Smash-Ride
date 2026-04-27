package com.example.smash_ride.features.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.example.smash_ride.R;
import com.example.smash_ride.core.graphics.SpriteColorizer;

public class Player {
    // Variable para el sprite coloreado
    private Bitmap playerBitmap;
    private Bitmap borderBitmap;

    public String name;
    private float xPos;
    private float yPos;
    private final float initialX;
    private final float initialY;
    private float speed;
    private float angle;
    private final float initialAngle;
    private float collisionAngle;
    private boolean isColliding;
    private final Paint paint;

    private final float initialSpeed;

    // Estado de juego
    private int lives = 5;
    private int kills = 0;
    private boolean destroyed = false;
    private boolean isInvincible = false;
    private long invincibilityEndTime = 0;
    private Player lastHitter;

    public Player(String name, float x, float y, float initialAngle, int speed) {        this.name = name;
        this.xPos = x;
        this.yPos = y;
        this.initialX = x;
        this.initialY = y;
        this.initialAngle = initialAngle; // Guardamos el ángulo inicial
        this.angle = initialAngle;        // Aplicamos el ángulo actual
        this.speed = speed;
        this.initialSpeed = speed;
        this.paint = new Paint();
        this.paint.setColor(Color.BLUE);
        this.isColliding = false;
    }

    /**
     * Configura el aspecto visual del jugador.
     * Escala los bitmaps para que coincidan con el radio de colisión (25).
     */
    public void setAppearance(Context context, int themeColor) {
        // El círculo de fallback ahora también tendrá el color correcto
        this.paint.setColor(themeColor);

        try {
            Bitmap rawBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.player_star);
            if (rawBitmap != null) {
                // 1. Crear el Sprite Coloreado
                Bitmap tinted = SpriteColorizer.colorizeBitmap(rawBitmap, themeColor);
                // Escalar a 120x120
                this.playerBitmap = Bitmap.createScaledBitmap(tinted, 120, 120, true);

                // 2. Crear el Borde/Silueta Negra
                Bitmap blackSilhouette = SpriteColorizer.colorizeBitmap(rawBitmap, Color.BLACK);
                // Escalar un poco más grande (130x130) para que sobresalga como borde
                this.borderBitmap = Bitmap.createScaledBitmap(blackSilhouette, 150, 150, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void draw(Canvas canvas) {
        if (destroyed) return;

        // Si es invencible, parpadea (se dibuja frame sí, frame no)
        if (isInvincible() && (System.currentTimeMillis() % 200 < 100)) {
            return;
        }

        if (playerBitmap != null) {
            // 1. Guardar el estado actual del canvas
            canvas.save();

            // 2. Mover el "punto de dibujo" al centro del jugador
            canvas.translate(xPos, yPos);

            // 3. Rotar el canvas según el ángulo del jugador
            canvas.rotate(angle+40);

            // 4. Dibujar el borde (centrado en el nuevo origen 0,0)
            canvas.drawBitmap(borderBitmap,
                    - (borderBitmap.getWidth() / 2f),
                    - (borderBitmap.getHeight() / 2f),
                    null);

            // 5. Dibujar el sprite coloreado encima
            canvas.drawBitmap(playerBitmap,
                    - (playerBitmap.getWidth() / 2f),
                    - (playerBitmap.getHeight() / 2f),
                    null);

            // 6. Restaurar el canvas a su posición original para no afectar a otros elementos
            canvas.restore();
        } else {
            // Mantenemos el círculo original como "fallback" con el color de la estrella
            canvas.drawCircle(xPos, yPos, 25, paint);

            // Dibujar un borde negro al círculo de fallback por consistencia
            Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(3f);
            strokePaint.setColor(Color.BLACK);
            canvas.drawCircle(xPos, yPos, 25, strokePaint);        }
    }

    // Mantenemos este método para compatibilidad con UI extra si se usa
    public void drawWithStatus(Canvas canvas) {
        if (destroyed) return;
        draw(canvas); // Dibuja el sprite
        Paint text = new Paint();
        text.setColor(Color.WHITE); // Cambiado a blanco para que se vea sobre fondo oscuro
        text.setTextSize(25f);
        text.setFakeBoldText(true);
        canvas.drawText(String.valueOf(lives), xPos - 10, yPos - 35, text);
    }

    public void update() {
        if (destroyed) return;
        if (!isColliding) {
            xPos += Math.cos(Math.toRadians(angle)) * speed;
            yPos += Math.sin(Math.toRadians(angle)) * speed;
        } else {
            xPos += Math.cos(Math.toRadians(collisionAngle)) * speed;
            yPos += Math.sin(Math.toRadians(collisionAngle)) * speed;
        }
    }

    public void setInvincible(long durationMs) {
        this.isInvincible = true;
        this.invincibilityEndTime = System.currentTimeMillis() + durationMs;
    }

    public boolean isInvincible() {
        // Si el tiempo actual superó el final, desactivamos automáticamente
        if (isInvincible && System.currentTimeMillis() > invincibilityEndTime) {
            isInvincible = false;
        }
        return isInvincible;
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

    public void setLastHitter(Player hitter) {
        this.lastHitter = hitter;
    }

    public Player getLastHitter() {
        return lastHitter;
    }

    public void resetPosition() {
        if (destroyed) {
            this.xPos = -1000f;
            this.yPos = -1000f;
            setColliding(true);
            setSpeed(0f);
            return;
        }
        this.xPos = initialX;
        this.yPos = initialY;
        this.angle = initialAngle;
        setColliding(false);
        this.speed = initialSpeed;

        // 2 segundos de protección al reaparecer o empezar
        this.lastHitter = null; // Limpiar el agresor al resetear
        setInvincible(2000);
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
        if (colliding) this.speed = 0f;
    }

    public boolean isColliding() {
        return isColliding || destroyed;
    }

    public void disableJoystick() {
        isColliding = true;
        speed = 0f;
    }

    public void enableJoystick() {
        if (destroyed) return;
        isColliding = false;
    }

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
        isColliding = true;
        speed = 0f;
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

}