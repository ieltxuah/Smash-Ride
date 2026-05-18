package com.example.smash_ride.features.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Representa un joystick virtual para el control del movimiento del jugador.
 * Gestiona la lógica de detección de toques y el cálculo de ángulo y velocidad.
 */
public class Joystick {
    private float controlX, controlY;
    private float joystickX, joystickY;
    private boolean isActive;
    private static final int CROSSHAIR_RADIUS = 100;
    private int themeColor = Color.RED;

    /**
     * Constructor del Joystick.
     */
    public Joystick() {
        this.isActive = false;
    }

    /**
     * Cambia el color del mando móvil del joystick.
     *
     * @param color Color en formato hexadecimal.
     */
    public void setThemeColor(int color) {
        this.themeColor = color;
    }

    /**
     * Se llama cuando el usuario toca la pantalla para posicionar el joystick.
     *
     * @param x Coordenada X del toque.
     * @param y Coordenada Y del toque.
     */
    public void touchDown(float x, float y) {
        controlX = x;
        controlY = y;
        joystickX = controlX;
        joystickY = controlY;
        isActive = true;
    }

    /**
     * Actualiza la posición del mando móvil mientras el usuario arrastra el dedo.
     * Limita el movimiento dentro del radio máximo permitido.
     *
     * @param x Nueva coordenada X del toque.
     * @param y Nueva coordenada Y del toque.
     */
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

    /**
     * Restablece el estado del joystick cuando el usuario levanta el dedo.
     */
    public void touchUp() {
        isActive = false;
        joystickX = controlX;
        joystickY = controlY;
    }

    /**
     * Calcula la velocidad basada en la distancia del mando respecto al centro.
     *
     * @param player El jugador al que afecta el control.
     * @return Valor de velocidad entre 0 y 10.
     */
    public float getSpeed(Player player) {
        if (player == null || player.isDestroyed()) return 0;

        float joystickDistanceToCenter = (float) Math.sqrt(
                Math.pow(joystickX - controlX, 2) +
                        Math.pow(joystickY - controlY, 2));

        float speed = (joystickDistanceToCenter / CROSSHAIR_RADIUS) * 10;
        return Math.min(speed, 10);
    }

    /**
     * Calcula el ángulo de dirección en grados.
     *
     * @param player El jugador al que afecta el control.
     * @return Ángulo en grados.
     */
    public float getAngle(Player player) {
        // 1. Si el jugador está bloqueado por colisión o el joystick NO está siendo tocado
        if (player.isColliding() || !isActive) {
            return player.getAngle(); // Devolvemos el ángulo actual para que no rote
        }

        float deltaX = joystickX - controlX;
        float deltaY = joystickY - controlY;

        // 2. Si el joystick está activo pero justo en el centro (distancia cero)
        // también devolvemos el ángulo actual para evitar el salto a 0 grados
        if (deltaX == 0 && deltaY == 0) {
            return player.getAngle();
        }

        // 3. Solo si hay movimiento real calculamos el nuevo ángulo
        return (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
    }

    /**
     * Dibuja los componentes visuales del joystick en el lienzo.
     *
     * @param canvas Lienzo donde realizar el dibujo.
     */
    public void draw(Canvas canvas) {
        if (isActive) {
            // 1. DIBUJO DEL CÍRCULO EXTERIOR (Base estática)
            // Usamos un blanco muy translúcido para un acabado moderno y "glassmorphism"
            Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            outerPaint.setColor(Color.WHITE);
            outerPaint.setAlpha(40); // Muy transparente (0-255)
            canvas.drawCircle(controlX, controlY, CROSSHAIR_RADIUS, outerPaint);

            // 2. DIBUJO DEL MANDO MÓVIL (Stick)
            // Usamos el color del tema seleccionado desde los ajustes
            Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            innerPaint.setColor(themeColor);
            // Opcional: añadimos un poco de transparencia para que no sea un color plano opaco
            innerPaint.setAlpha(200);

            // Dibujamos el mando con radio 35 (ligeramente más grande que antes para mejor UX)
            canvas.drawCircle(joystickX, joystickY, 35, innerPaint);
        }
    }
}