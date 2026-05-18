package com.example.smash_ride.data.remote;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestor para la base de datos NoSQL de Firebase (Cloud Firestore).
 * Se encarga de la persistencia y actualización de las estadísticas globales
 * de los jugadores para el sistema de ranking.
 */
public class FirestoreRankingManager {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Actualiza las estadísticas de un usuario en Firestore.
     * Utiliza incrementos atómicos para asegurar la consistencia de los datos globales.
     *
     * @param userId      ID único del usuario.
     * @param userName    Nombre de usuario a mostrar en el ranking.
     * @param mode        Modo de juego ("LIVES" o "TIMER").
     * @param kills       Número de bajas conseguidas en la partida.
     * @param livesLost   Número de vidas perdidas en la partida.
     * @param hitsDealt   Número de golpes acertados en la partida.
     * @param isWinner    Indica si el usuario ganó la partida.
     */
    public void updateStats(String userId, String userName, String mode, int kills, int livesLost, int hitsDealt, boolean isWinner) {
        // En producción, se podría filtrar para que solo usuarios registrados suban datos
        // if (userId == null || userId.startsWith("Guest_")) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("userId", userId);
        updates.put("userName", userName);

        // Actualización de estadísticas globales
        updates.put("totalKills", FieldValue.increment(kills));
        updates.put("totalHitsDealt", FieldValue.increment(hitsDealt));
        updates.put("totalLivesLost", FieldValue.increment(livesLost));

        // Estadísticas específicas por modo
        if (mode.equals("LIVES")) {
            updates.put("killsLivesMode", FieldValue.increment(kills));
            updates.put("hitsLivesMode", FieldValue.increment(hitsDealt));
            updates.put("livesLostLivesMode", FieldValue.increment(livesLost));
        } else if (mode.equals("TIMER")) {
            updates.put("killsTimerMode", FieldValue.increment(kills));
            updates.put("hitsTimerMode", FieldValue.increment(hitsDealt));
            updates.put("livesLostTimerMode", FieldValue.increment(livesLost));
        }

        // Victoria perfecta: ganar sin perder ninguna vida en modo vidas
        if (mode.equals("LIVES") && isWinner && livesLost == 0) {
            updates.put("perfectVictories", FieldValue.increment(1));
        }

        db.collection("rankings").document(userId)
                .set(updates, SetOptions.merge());
    }

    /**
     * Actualiza el récord de "Rey del Tiempo" (máximas bajas en una partida de tiempo).
     * Solo actualiza si la puntuación actual es mayor que el récord almacenado.
     *
     * @param userId      ID único del usuario.
     * @param matchKills  Bajas conseguidas en la última partida.
     */
    public void updateTimerKing(String userId, int matchKills) {
        db.collection("rankings").document(userId).get().addOnSuccessListener(doc -> {
            Long max = doc.getLong("maxKillsInTimer");
            if (max == null || matchKills > (max != null ? max : 0)) {
                db.collection("rankings").document(userId).set(
                        new HashMap<String, Object>() {{ put("maxKillsInTimer", matchKills); }},
                        SetOptions.merge()
                );
            }
        });
    }
}