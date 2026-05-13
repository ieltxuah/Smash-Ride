package com.example.smash_ride.core.network;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;

public class FirestoreRankingManager {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void updateStats(String userId, String userName, String mode, int kills, int livesLost, int hitsDealt, boolean isWinner) {
        // COMENTADO PARA PRUEBAS: permitimos que los Guest suban datos para que veas que funciona
        // if (userId == null || userId.startsWith("Guest_")) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("userId", userId);
        updates.put("userName", userName);

        updates.put("totalKills", FieldValue.increment(kills));
        updates.put("totalHitsDealt", FieldValue.increment(hitsDealt));
        updates.put("totalLivesLost", FieldValue.increment(livesLost));

        if (mode.equals("LIVES") && isWinner && livesLost == 0) {
            updates.put("perfectVictories", FieldValue.increment(1));
        }

        db.collection("rankings").document(userId)
                .set(updates, SetOptions.merge());
    }

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