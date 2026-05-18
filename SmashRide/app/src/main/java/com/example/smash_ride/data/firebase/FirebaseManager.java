package com.example.smash_ride.data.firebase;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Gestor centralizado para la base de datos en tiempo real de Firebase (Realtime Database).
 * Proporciona referencias a los nodos principales utilizados para el matchmaking y las salas de juego.
 * Implementa el patrón Singleton.
 */
public class FirebaseManager {
    private static FirebaseManager instance;
    private final DatabaseReference db;

    /**
     * Constructor privado que inicializa la referencia raíz de la base de datos.
     */
    private FirebaseManager() {
        db = FirebaseDatabase.getInstance().getReference();
    }

    /**
     * Obtiene la instancia única del gestor de Firebase.
     *
     * @return Instancia de {@link FirebaseManager}.
     */
    public static synchronized FirebaseManager getInstance() {
        if (instance == null) instance = new FirebaseManager();
        return instance;
    }

    /**
     * Obtiene la referencia al nodo de salas de juego ("rooms").
     *
     * @return {@link DatabaseReference} del nodo de salas.
     */
    public DatabaseReference getRoomsRef() { return db.child("rooms"); }

    /**
     * Obtiene la referencia al nodo de emparejamiento ("matchmaking").
     *
     * @return {@link DatabaseReference} del nodo de matchmaking.
     */
    public DatabaseReference getMatchmakingRef() { return db.child("matchmaking"); }
}
