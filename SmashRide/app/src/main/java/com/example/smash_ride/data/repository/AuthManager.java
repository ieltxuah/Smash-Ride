package com.example.smash_ride.data.repository;

import com.example.smash_ride.data.local.PreferenceHelper;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

/**
 * Gestor de autenticación de la aplicación.
 * Centraliza las operaciones relacionadas con Firebase Auth y Google Sign-In.
 */
public class AuthManager {
    private final FirebaseAuth mAuth;
    private final PreferenceHelper prefHelper;

    /**
     * Interfaz para manejar el resultado de los procesos de autenticación.
     */
    public interface AuthCallback {
        /**
         * Llamado cuando la autenticación se completa con éxito.
         *
         * @param user El objeto {@link FirebaseUser} autenticado.
         */
        void onSuccess(FirebaseUser user);

        /**
         * Llamado cuando ocurre un error durante la autenticación.
         *
         * @param error Mensaje descriptivo del error.
         */
        void onError(String error);
    }

    /**
     * Constructor del gestor de autenticación.
     *
     * @param prefHelper Instancia de {@link PreferenceHelper} para persistir datos de sesión.
     */
    public AuthManager(PreferenceHelper prefHelper) {
        this.mAuth = FirebaseAuth.getInstance();
        this.prefHelper = prefHelper;
    }

    /**
     * Comprueba si el usuario tiene una sesión activa en Firebase.
     *
     * @return true si el usuario está logueado, false en caso contrario.
     */
    public boolean isLoggedIn() {
        return mAuth.getCurrentUser() != null;
    }

    /**
     * Obtiene el usuario de Firebase que ha iniciado sesión actualmente.
     *
     * @return El {@link FirebaseUser} actual o null si no hay sesión activa.
     */
    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    /**
     * Inicia sesión en Firebase utilizando una credencial de Google.
     * Al tener éxito, actualiza el ID de usuario en las preferencias locales.
     *
     * @param idToken  Token de ID obtenido de Google Sign-In.
     * @param callback Callback para retornar el éxito o error de la operación.
     */
    public void signInWithGoogle(String idToken, AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            prefHelper.setUserId(user.getUid());
                            //prefHelper.setUserName(user.getDisplayName());
                            callback.onSuccess(user);
                        }
                    } else {
                        callback.onError(task.getException() != null ?
                                task.getException().getMessage() : "Auth Failed");
                    }
                });
    }

    /**
     * Cierra la sesión del usuario actual en Firebase.
     *
     * @param onComplete Acción a ejecutar tras completar el cierre de sesión (ej: actualizar la UI).
     */
    public void logout(Runnable onComplete) {
        mAuth.signOut();
        onComplete.run();
    }
}