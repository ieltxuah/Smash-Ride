package com.example.smash_ride.features.auth;

import com.example.smash_ride.data.local.PreferenceHelper;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class AuthManager {
    private final FirebaseAuth mAuth;
    private final PreferenceHelper prefHelper;

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String error);
    }

    public AuthManager(PreferenceHelper prefHelper) {
        this.mAuth = FirebaseAuth.getInstance();
        this.prefHelper = prefHelper;
    }

    public boolean isLoggedIn() {
        return mAuth.getCurrentUser() != null;
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

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

    public void logout(Runnable onComplete) {
        mAuth.signOut();
        onComplete.run();
    }
}