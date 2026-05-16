package com.example.smash_ride.data.firebase;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class FirebaseManager {
    private static FirebaseManager instance;
    private final DatabaseReference db;

    private FirebaseManager() {
        db = FirebaseDatabase.getInstance().getReference();
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) instance = new FirebaseManager();
        return instance;
    }

    public DatabaseReference getRoomsRef() { return db.child("rooms"); }
    public DatabaseReference getMatchmakingRef() { return db.child("matchmaking"); }
}
