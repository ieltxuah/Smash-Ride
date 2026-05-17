package com.example.smash_ride.features.game.online;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import com.example.smash_ride.data.firebase.FirebaseManager;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.Map;

public class OnlineMatchmaker {

    public interface OnMatchFoundListener {
        void onMatchReady(String roomId, int mySlot);
        void onError(String error);
    }

    private static final long TIMEOUT_MS = 120000; // 2 minutos
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private ValueEventListener playersListener;
    private DatabaseReference currentRoomRef;

    public void findMatch(String mode, String userId, String userName, OnMatchFoundListener listener, PreferenceHelper prefHelper) {
        // --- TIMEOUT DE SEGURIDAD ---
        timeoutRunnable = () -> {
            cleanUp(null, null);
            listener.onError("ERROR_MATCHMAKING_TIMEOUT");
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        DatabaseReference matchmakingRef = FirebaseManager.getInstance().getMatchmakingRef().child(mode);

        // 1. Obtener o crear la sala actual disponible
        matchmakingRef.child("current_room").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentRoom) {
                String roomId = currentRoom.getValue(String.class);
                if (roomId == null) {
                    roomId = "ROOM_" + System.currentTimeMillis();
                    currentRoom.setValue(roomId);
                }
                return Transaction.success(currentRoom);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (committed && snapshot.exists()) {
                    joinRoom(snapshot.getValue(String.class), mode, userId, userName, listener, prefHelper);
                } else {
                    // Si hay error, cancelamos el timer de 2 min antes de avisar
                    cancelTimeout();
                    listener.onError("Error al conectar con el servidor de matchmaking.");
                }
            }
        });
    }

    private void joinRoom(String roomId, String mode, String userId, String userName, OnMatchFoundListener listener, PreferenceHelper prefHelper) {
        DatabaseReference roomRef = FirebaseManager.getInstance().getRoomsRef().child(roomId);
        DatabaseReference playerRef = roomRef.child("players").child(userId);

        // 1. Configurar borrado automático si el jugador se desconecta
        playerRef.onDisconnect().removeValue();

        roomRef.child("players").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData playersData) {
                // Si yo ya estoy en la sala, no hago nada
                if (playersData.hasChild(userId)) {
                    return Transaction.success(playersData);
                }

                // Si ya hay 4, la sala está llena para nuevos
                if (playersData.getChildrenCount() >= 4) {
                    return Transaction.abort();
                }

                // 2. BUSCAR EL PRIMER SLOT LIBRE (0-3)
                boolean[] takenSlots = new boolean[4];
                for (MutableData p : playersData.getChildren()) {
                    Integer s = p.child("slot").getValue(Integer.class);
                    if (s != null && s >= 0 && s < 4) takenSlots[s] = true;
                }

                int mySlot = -1;
                for (int i = 0; i < 4; i++) {
                    if (!takenSlots[i]) {
                        mySlot = i;
                        break;
                    }
                }

                if (mySlot == -1) return Transaction.abort(); // Por seguridad

                // Guardar mis datos
                Map<String, Object> myData = new HashMap<>();
                myData.put("name", userName);
                myData.put("slot", mySlot);
                myData.put("color_pref", prefHelper.getCharacterColor());

                playersData.child(userId).setValue(myData);
                return Transaction.success(playersData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (committed) {
                    Integer slot = snapshot.child(userId).child("slot").getValue(Integer.class);
                    waitForPlayers(roomRef, roomId, (slot != null ? slot : 0), listener);
                } else {
                    // Sala llena o error, crear otra
                    createNewRoomAndRetry(mode, userId, userName, listener, prefHelper);
                }
            }
        });
    }

    private void waitForPlayers(DatabaseReference roomRef, String roomId, int mySlot, OnMatchFoundListener listener) {
        this.currentRoomRef = roomRef;
        Log.d("MATCH_DEBUG", "Entrado en sala: " + roomId + " con Slot: " + mySlot + ". Esperando jugadores...");

        // Listener para observar el nodo "players" de la sala
        playersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                Log.d("MATCH_DEBUG", "Actualización de sala [" + roomId + "]: " + count + "/4 jugadores presentes.");

                // SOLO notificamos a la Activity cuando hay exactamente 4
                if (count == 4) {
                    Log.d("MATCH_DEBUG", "¡SALA LLENA! Notificando inicio de partida...");

                    // Quitamos el listener para que no se repita el inicio si alguien sale/entra justo ahora
                    roomRef.child("players").removeEventListener(this);

                    // Avisamos a la Activity
                    new Handler(Looper.getMainLooper()).post(() -> {
                        listener.onMatchReady(roomId, mySlot);
                    });
                } else {
                    Log.d("MATCH_DEBUG", "Aún faltan " + (4 - count) + " jugadores.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("MATCH_DEBUG", "Error en el listener de jugadores: " + error.getMessage());
                listener.onError("Error de red al esperar jugadores.");
            }
        };

        // Empezamos a escuchar cambios en la lista de jugadores de esta sala
        roomRef.child("players").addValueEventListener(playersListener);
    }

    private void createNewRoomAndRetry(String mode, String userId, String userName, OnMatchFoundListener l, PreferenceHelper p) {
        String newId = "ROOM_" + System.currentTimeMillis();
        // Actualizamos la sala global para que el siguiente jugador cree/use esta nueva
        FirebaseManager.getInstance().getMatchmakingRef().child(mode).child("current_room").setValue(newId)
                .addOnCompleteListener(t -> findMatch(mode, userId, userName, l, p));
    }

    public void leaveRoom(String roomId, String userId) {
        if (roomId == null || userId == null) return;

        // Referencia al nodo del jugador específico
        FirebaseManager.getInstance().getRoomsRef()
                .child(roomId)
                .child("players")
                .child(userId)
                .removeValue()
                .addOnSuccessListener(aVoid -> Log.d("MATCH_DEBUG", "Jugador eliminado de la sala con éxito"))
                .addOnFailureListener(e -> Log.e("MATCH_DEBUG", "Error al eliminar jugador: " + e.getMessage()));
    }

    public void cleanUp(String roomId, String userId) {
        cancelTimeout();
        if (currentRoomRef != null && playersListener != null) {
            currentRoomRef.child("players").removeEventListener(playersListener);
        }
        if (roomId != null && userId != null) {
            leaveRoom(roomId, userId);
        }
    }

    public void cancelTimeout() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
            Log.d("MATCH_DEBUG", "Matchmaker timeout cancelado.");
        }
    }
}