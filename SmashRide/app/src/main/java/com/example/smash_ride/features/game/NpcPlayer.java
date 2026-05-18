package com.example.smash_ride.features.game;

import java.util.List;

// IA de NPC - trata de empujar jugadores fuera de la arena
// Estados: IDLE (espera), CHASE (persigue), CHARGE (impacto), EVADE (lejos del borde), RECOVER (después de golpe)

public class NpcPlayer extends Player {

    // Valores para la IA (pueden cambiarse si no funciona bien)
    private static final float CHASE_SPEED = 7.5f;
    private static final float CHARGE_SPEED = 12f;
    private static final float EVADE_SPEED = 8f;
    private static final float EDGE_DANGER = 0.78f; // qué tan cerca del borde
    private static final float AIM_LEAD = 80f; // punto de apuntamiento
    private static final float CHARGE_ANGLE = 25f; // alineación para CHARGE
    private static final long IDLE_TIME = 1200; // tiempo en IDLE
    private static final long TARGET_CHECK_TIME = 800; // qué tan seguido busca nueva diana

    // Estado actual
    private enum AiState {IDLE, CHASE, CHARGE, EVADE, RECOVER}
    private AiState estado = AiState.IDLE;
    private long startTime;
    private long lastTargetTime = 0;
    private Player objetivo = null;

    // Constructor
    public NpcPlayer(String name, float x, float y, float angle, int slot) {
        super(name, x, y, angle, 0, slot);
        startTime = System.currentTimeMillis();
    }

    // Actualizar la IA (llamado cada frame)
    public void updateAI(List<Player> jugadores, float cx, float cy, float radio) {
        if (isDestroyed()) return;

        long ahora = System.currentTimeMillis();
        update(); // movimiento base

        float dist = (float) Math.hypot(getXPos() - cx, getYPos() - cy);
        boolean cercaBorde = dist >= radio * EDGE_DANGER;

        // Verificar estado actual y cambiar si es necesario
        if (cercaBorde && estado != AiState.EVADE && estado != AiState.RECOVER) {
            estado = AiState.EVADE;
            startTime = ahora;
        }

        if (isColliding() && estado != AiState.RECOVER && estado != AiState.IDLE) {
            estado = AiState.RECOVER;
            startTime = ahora;
        }

        // Ejecutar acción según el estado
        switch (estado) {
            case IDLE:
                setSpeed(0f);
                if (ahora - startTime > IDLE_TIME) {
                    estado = AiState.CHASE;
                    startTime = ahora;
                }
                break;

            case RECOVER:
                setSpeed(0f);
                if (!isColliding() && ahora - startTime > 300) {
                    estado = cercaBorde ? AiState.EVADE : AiState.CHASE;
                    startTime = ahora;
                }
                break;

            case EVADE:
                doEvade(cx, cy, ahora, radio);
                break;

            case CHASE:
                doChase(jugadores, cx, cy, ahora, radio);
                break;

            case CHARGE:
                doCharge(cx, cy, ahora);
                break;
        }
    }

    // Comportamientos de estado

    // EVADE: ir al centro para evitar el borde
    private void doEvade(float cx, float cy, long ahora, float radio) {
        float dx = cx - getXPos();
        float dy = cy - getYPos();
        float angulo = (float) Math.toDegrees(Math.atan2(dy, dx));
        setAngle(angulo);
        setSpeed(EVADE_SPEED);

        float dist = (float) Math.hypot(getXPos() - cx, getYPos() - cy);
        if (dist < radio * (EDGE_DANGER - 0.10f)) {
            estado = AiState.CHASE;
            startTime = ahora;
        }
    }

    // CHASE: perseguir a la diana más cercana al borde
    private void doChase(List<Player> jugadores, float cx, float cy, long ahora, float radio) {
        // Buscar nueva diana si es necesario
        if (objetivo == null || objetivo.isDestroyed() || ahora - lastTargetTime > TARGET_CHECK_TIME) {
            objetivo = pickBestTarget(jugadores, cx, cy, radio);
            lastTargetTime = ahora;
        }

        if (objetivo == null) {
            setSpeed(0f);
            return;
        }

        // Calcular dónde apuntar
        float[] aim = computeAimPoint(objetivo, cx, cy);
        float dx = aim[0] - getXPos();
        float dy = aim[1] - getYPos();
        float angulo = (float) Math.toDegrees(Math.atan2(dy, dx));
        setAngle(angulo);
        setSpeed(CHASE_SPEED);

        // Verificar si está alineado para CHARGE
        float angleToTarget = (float) Math.toDegrees(
            Math.atan2(objetivo.getYPos() - getYPos(), objetivo.getXPos() - getXPos()));
        float diff = Math.abs(normalizeAngle(angleToTarget - getAngle()));

        if (diff < CHARGE_ANGLE) {
            estado = AiState.CHARGE;
            startTime = ahora;
        }
    }

    // CHARGE: impacto rápido
    private void doCharge(float cx, float cy, long ahora) {
        if (objetivo == null || objetivo.isDestroyed()) {
            estado = AiState.CHASE;
            startTime = ahora;
            return;
        }

        // Reapuntar
        float[] aim = computeAimPoint(objetivo, cx, cy);
        float dx = aim[0] - getXPos();
        float dy = aim[1] - getYPos();
        setAngle((float) Math.toDegrees(Math.atan2(dy, dx)));
        setSpeed(CHARGE_SPEED);

        // Verificar si se desalineó
        float angleToTarget = (float) Math.toDegrees(
            Math.atan2(objetivo.getYPos() - getYPos(), objetivo.getXPos() - getXPos()));
        float diff = Math.abs(normalizeAngle(angleToTarget - getAngle()));

        if (diff > CHARGE_ANGLE * 2.5f) {
            estado = AiState.CHASE;
            startTime = ahora;
        }
    }

    // Helpers

    // Elegir el mejor objetivo (el más cerca del borde)
    private Player pickBestTarget(List<Player> jugadores, float cx, float cy, float radio) {
        Player mejor = null;
        float mejorPuntaje = -1f;

        for (Player p : jugadores) {
            if (p == this) continue;
            if (p.isDestroyed()) continue;
            if (p.isInvincible()) continue;

            float dist = (float) Math.hypot(p.getXPos() - cx, p.getYPos() - cy);
            float puntaje = dist / radio;

            if (puntaje > mejorPuntaje) {
                mejorPuntaje = puntaje;
                mejor = p;
            }
        }
        return mejor;
    }

    // Calcular punto de apuntamiento (un poco más allá de la diana)
    private float[] computeAimPoint(Player target, float cx, float cy) {
        float dx = target.getXPos() - cx;
        float dy = target.getYPos() - cy;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) len = 1f;
        float nx = dx / len;
        float ny = dy / len;

        return new float[]{
            target.getXPos() + nx * AIM_LEAD,
            target.getYPos() + ny * AIM_LEAD
        };
    }

    // Normalizar ángulo entre -180 y 180
    private float normalizeAngle(float angle) {
        while (angle > 180f) angle -= 360f;
        while (angle < -180f) angle += 360f;
        return angle;
    }
}