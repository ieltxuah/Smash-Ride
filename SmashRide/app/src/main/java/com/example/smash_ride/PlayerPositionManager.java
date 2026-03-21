package com.example.smash_ride;

import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class PlayerPositionManager {

    private static final String TAG = "PlayerPositionManager";

    private final DatabaseReference databaseReference;

    public PlayerPositionManager(String uid) {
        databaseReference = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DB_URL)
                .getReference("players/" + uid);
    }

    public void createProfile(String username) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("username", username);
        profile.put("xpos", 0f);
        profile.put("ypos", 0f);
        profile.put("invincible", false);
        profile.put("lives", 5);

        databaseReference.updateChildren(profile)
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Perfil creado: " + username))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error creando perfil: " + e.getMessage()));
    }

    public void savePosition(float x, float y) {
        Map<String, Object> position = new HashMap<>();
        position.put("xpos", x);
        position.put("ypos", y);

        Log.d(TAG, "Intentando cuardar posición: xpos=" + x + " ypos=" + y);

        databaseReference.updateChildren(position)
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Posición guardada: xpos=" + x + " ypos=" + y))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error guardando posición: " + e.getMessage()));
    }
}