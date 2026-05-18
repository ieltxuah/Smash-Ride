package com.example.smash_ride.features.game;

/**
 * Interfaz para escuchar el evento de fin de partida.
 * Define el contrato para notificar a otros componentes cuando el juego termina.
 */
public interface GameOverListener {
    /**
     * Se ejecuta cuando se cumplen las condiciones de fin de juego (muerte del jugador o tiempo agotado).
     */
    void onGameOver();
}
