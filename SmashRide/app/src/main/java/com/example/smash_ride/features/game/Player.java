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
    public int slot;
    private float targetX, targetY;
    private boolean isRemote = false;
    private boolean networkInvincible = false;

    private int livesLostMatch = 0; // Vidas perdidas en esta partida
    private int hitsDealtMatch = 0; // Golpes dados a otros en esta partida

    public Player(String name, float x, float y, float initialAngle, int speed, int slot) {
        this.name = name;
        this.xPos = x;
        this.yPos = y;
        this.initialX = x;
        this.initialY = y;
        this.initialAngle = initialAngle; // Guardamos el ángulo inicial
        this.angle = initialAngle;        // Aplicamos el ángulo actual
        this.speed = speed;
        this.initialSpeed = speed;
        this.slot = slot;
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
            canvas.drawCircle(xPos, yPos, 25, strokePaint);
        }
    }

    public void update() {
        if (destroyed) return;

        if (isRemote) {
            // MOVIMIENTO POR RED (Otros jugadores)
            float dX = targetX - xPos;
            float dY = targetY - yPos;
            float distance = (float) Math.hypot(dX, dY);

            if (distance > 500f) {
                xPos = targetX;
                yPos = targetY;
            } else if (distance > 0.5f) {
                // Factor de interpolación (Lerp) equilibrado para 60fps
                xPos += dX * 0.3f;
                yPos += dY * 0.3f;
            }
        } else {
            // Lógica normal para tu jugador local
            if (isColliding) {
                // El rebote ignora el joystick
                xPos += Math.cos(Math.toRadians(collisionAngle)) * speed;
                yPos += Math.sin(Math.toRadians(collisionAngle)) * speed;
                speed *= 0.92f; // Rozamiento
                if (speed < 0.5f) isColliding = false;
            } else {
                // Movimiento por joystick
                xPos += Math.cos(Math.toRadians(angle)) * speed;
                yPos += Math.sin(Math.toRadians(angle)) * speed;

                // Reconciliación suave con el Maestro
                if (targetX != 0 && targetY != 0) {
                    float diffX = targetX - xPos;
                    float diffY = targetY - yPos;
                    if (Math.hypot(diffX, diffY) > 8f) { // Umbral de corrección
                        xPos += diffX * 0.15f;
                        yPos += diffY * 0.15f;
                    }
                }
            }
        }
    }

    public void setInvincible(long durationMs) {
        this.isInvincible = true;
        this.invincibilityEndTime = System.currentTimeMillis() + durationMs;
    }

    public void setInvincibleByNetwork(boolean invincible) {
        this.networkInvincible = invincible;
    }

    // Modifica el método isInvincible() existente para que considere ambos estados
    public boolean isInvincible() {
        // Es invencible si el tiempo local no ha acabado O si la red dice que lo es
        if (isInvincible && System.currentTimeMillis() > invincibilityEndTime) {
            isInvincible = false;
        }
        return isInvincible || networkInvincible;
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

    public void setXPos(float x) {
        this.xPos = x;
    }

    public void setYPos(float y) {
        this.yPos = y;
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
        setInvincible(2000);
    }


    /**
     * Fuerza la posición del jugador ignorando la interpolación de red.
     * Útil para respawns o teletransportes.
     */
    public void snapToPosition(float x, float y) {
        this.xPos = x;
        this.yPos = y;
        this.targetX = x;
        this.targetY = y;
        this.isColliding = false;
        this.speed = 0;
    }

    public float getXPos() {
        return xPos;
    }

    public float getYPos() {
        return yPos;
    }

    public void setColliding(boolean colliding) {
        if (destroyed) {
            this.isColliding = true;
            this.speed = 0f;
            return;
        }
        this.isColliding = colliding;
        if (colliding) {
            this.speed = 0f;
            // Bloqueamos red localmente también por si acaso
        }
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

    public int getColor() {
        return paint.getColor();
    }

    public void loseLife() {
        if (destroyed || isInvincible()) return;
        if (lives > 0) {
            lives--;
            livesLostMatch++;
        }
        if (lives <= 0) {
            markDestroyed();
        }
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public void addHit() { hitsDealtMatch++; }
    public int getLivesLostMatch() { return livesLostMatch; }
    public int getHitsDealtMatch() { return hitsDealtMatch; }

    private void markDestroyed() {
        destroyed = true;
        isColliding = true;
        speed = 0f;
        this.xPos = -5000f;
        this.yPos = -5000f;
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

    // Método para que GameView marque si es un clon remoto
    public void setIsRemote(boolean remote) {
        this.isRemote = remote;
        if (!remote) {
            // Si dejo de ser remoto, limpio los objetivos de red
            // para que p.update() use la velocidad/ángulo actuales
            this.targetX = 0;
            this.targetY = 0;
        }
    }

    // Método para recibir la posición de red
    public void setRemoteTarget(float x, float y) {
        this.targetX = x;
        this.targetY = y;
    }

    public void setLivesLostMatch(int lvLost) {
        this.livesLostMatch = lvLost;
    }

    public void setHitsDealtMatch(int hitsDeal) {
        this.hitsDealtMatch = hitsDeal;
    }
}