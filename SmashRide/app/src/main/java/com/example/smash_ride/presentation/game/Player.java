package com.example.smash_ride.presentation.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.example.smash_ride.R;
import com.example.smash_ride.core.graphics.SpriteColorizer;

import java.util.LinkedList;

/**
 * Clase que representa a un jugador (estrella) en el campo de batalla.
 * Gestiona la física de movimiento, el sistema de vidas, las estelas visuales (clones)
 * durante el boost y la sincronización de estados para el modo multijugador.
 */
public class Player {
    private static final String TAG = "Player";

    // --- Identificación y Estado ---
    public String name;
    public int slot;
    private int lives = 5;
    private int kills = 0;
    private boolean destroyed = false;
    private int livesLostMatch = 0;
    private int hitsDealtMatch = 0;

    // --- Posicionamiento y Física ---
    private float xPos, yPos;
    private final float initialX, initialY;
    private float speed;
    private float angle;
    private final float initialAngle, initialSpeed;
    private float collisionAngle;
    private boolean isColliding;
    private boolean isBoosting = false;

    // --- Invencibilidad ---
    private boolean isInvincible = false;
    private long invincibilityEndTime = 0;
    private boolean networkInvincible = false;

    // --- Red e Interpolación ---
    private float targetX, targetY;
    private boolean isRemote = false;

    // --- Gráficos y Efectos ---
    private Bitmap playerBitmap;
    private Bitmap borderBitmap;
    private final Paint paint;
    private final Paint trailPaint = new Paint();
    private final LinkedList<TrailPoint> trailHistory = new LinkedList<>();
    private final int MAX_TRAIL_POINTS = 5;

    /**
     * Almacena la posición y ángulo para renderizar la estela del boost.
     */
    private static class TrailPoint {
        float x, y, angle;
        TrailPoint(float x, float y, float angle) {
            this.x = x; this.y = y; this.angle = angle;
        }
    }

    /**
     * Constructor del Jugador.
     *
     * @param name         Nombre del jugador.
     * @param x            Coordenada X inicial.
     * @param y            Coordenada Y inicial.
     * @param initialAngle Ángulo inicial en grados.
     * @param speed        Velocidad inicial.
     * @param slot         Posición en la sala (0-3).
     */
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

    // --- Gestión Gráfica ---

    /**
     * Carga y personaliza los recursos visuales del jugador según su color.
     *
     * @param context    Contexto para acceder a los recursos.
     * @param themeColor Color hexadecimal para tintar la estrella.
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

    /**
     * Dibuja al jugador y sus efectos asociados (estela, invencibilidad) en el lienzo.
     *
     * @param canvas Lienzo donde dibujar.
     */
    public void draw(Canvas canvas) {
        if (destroyed) return;

        // 1. DIBUJAR LA ESTELA (CLONES)
        if (!trailHistory.isEmpty()) {
            // Iteramos del más antiguo al más reciente
            for (int i = 0; i < trailHistory.size(); i++) {
                TrailPoint tp = trailHistory.get(i);

                // Calculamos transparencia: el más antiguo es más transparente
                // i=0 es el más reciente, i=4 es el más antiguo
                int alpha = 150 - (i * 25);
                if (alpha < 0) alpha = 0;
                trailPaint.setAlpha(alpha);

                canvas.save();
                canvas.translate(tp.x, tp.y);
                canvas.rotate(tp.angle + 40);

                // Dibujamos el sprite original pero con el paint transparente
                if (playerBitmap != null) {
                    canvas.drawBitmap(playerBitmap,
                            -(playerBitmap.getWidth() / 2f),
                            -(playerBitmap.getHeight() / 2f),
                            trailPaint);
                }
                canvas.restore();
            }
        }

        // Si es invencible, parpadea (se dibuja frame sí, frame no)
        if (isInvincible() && (System.currentTimeMillis() % 200 < 100)) {
            return;
        }

        // 2. DIBUJAR EL JUGADOR ORIGINAL
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

    // --- Lógica y Físicas ---

    /**
     * Actualiza la posición del jugador y gestiona la estela.
     */
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
                // El rebote ignora el joystick y se maneja el movimiento en GameView
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

        // GESTIÓN DE LA MEMORIA DE LA ESTELA
        if (isBoosting) {
            // Guardamos la posición actual al principio de la lista
            trailHistory.addFirst(new TrailPoint(xPos, yPos, angle));
            if (trailHistory.size() > MAX_TRAIL_POINTS) {
                trailHistory.removeLast();
            }
        } else {
            // Si no hay boost, vamos eliminando los puntos poco a poco para que la estela desaparezca suavemente
            if (!trailHistory.isEmpty()) {
                trailHistory.removeLast();
            }
        }
    }

    /**
     * Restablece al jugador a su estado inicial.
     */
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
     * Reposiciona instantáneamente al jugador en coordenadas específicas.
     *
     * @param x Nueva posición X.
     * @param y Nueva posición Y.
     */
    public void snapToPosition(float x, float y) {
        this.xPos = x;
        this.yPos = y;
        this.targetX = x;
        this.targetY = y;
        this.isColliding = false;
        this.speed = 0;
    }

    // --- Combate y Vidas ---

    /**
     * Reduce vidas si el jugador no es invencible.
     */
    public void loseLife() {
        if (destroyed || isInvincible()) return;
        if (lives > 0) {
            lives--;
            livesLostMatch++;
        }
        if (lives <= 0) markDestroyed();
    }

    /** Incrementa el contador de bajas. */
    public void addKill() { if (!destroyed) kills++; }

    /** Registra un impacto dado. */
    public void addHit() { hitsDealtMatch++; }

    /** Marca al jugador como fuera de combate. */
    public void destroy() { markDestroyed(); }

    private void markDestroyed() {
        destroyed = true;
        isColliding = true;
        speed = 0f;
        this.xPos = -5000f;
        this.yPos = -5000f;
    }

    // --- Getters y Setters ---

    public boolean isInvincible() {
        // Es invencible si el tiempo local no ha acabado O si la red dice que lo es
        if (isInvincible && System.currentTimeMillis() > invincibilityEndTime) {
            isInvincible = false;
        }
        return isInvincible || networkInvincible;
    }

    public void setInvincible(long durationMs) {
        this.isInvincible = true;
        this.invincibilityEndTime = System.currentTimeMillis() + durationMs;
    }

    public void setInvincibleByNetwork(boolean invincible) {
        this.networkInvincible = invincible;
    }

    public float getXPos() { return xPos; }
    public void setXPos(float x) { this.xPos = x; }
    public float getYPos() { return yPos; }
    public void setYPos(float y) { this.yPos = y; }

    public float getSpeed() { return destroyed ? 0f : speed; }
    public void setSpeed(float speed) { if (!destroyed) this.speed = speed; }

    public float getAngle() { return angle; }
    public void setAngle(float angle) { if (!destroyed) this.angle = angle; }

    public boolean getBoosting() { return isBoosting; }
    public void setBoosting(boolean boosting) { this.isBoosting = boosting; }

    public boolean isColliding() { return isColliding || destroyed; }
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

    public int getKills() { return kills; }
    public void setKills(int kills) { this.kills = kills; }

    public int getColor() { return paint.getColor(); }

    public boolean isDestroyed() { return destroyed; }

    public int getLivesLostMatch() { return livesLostMatch; }
    public void setLivesLostMatch(int lvLost) { this.livesLostMatch = lvLost; }
    public int getHitsDealtMatch() { return hitsDealtMatch; }
    public void setHitsDealtMatch(int hitsDeal) { this.hitsDealtMatch = hitsDeal; }

    public void setIsRemote(boolean remote) {
        this.isRemote = remote;
        if (!remote) {
            // Si dejo de ser remoto, limpio los objetivos de red
            // para que p.update() use la velocidad/ángulo actuales
            this.targetX = 0;
            this.targetY = 0;
        }
    }

    public void setRemoteTarget(float x, float y) {
        this.targetX = x;
        this.targetY = y;
    }
}