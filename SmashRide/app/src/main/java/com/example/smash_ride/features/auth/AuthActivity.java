package com.example.smash_ride.features.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smash_ride.R;
import com.example.smash_ride.features.settings.SettingsActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;

public class AuthActivity extends AppCompatActivity {

    // Instancia de Firebase para manejar autenticacion
    FirebaseAuth mAuth;

    // Vistas del formulario
    RadioGroup modeRadioGroup;
    RadioButton loginRadio;
    RadioButton registerRadio;

    EditText emailInput;
    EditText passwordInput;
    EditText confirmPasswordInput;
    EditText usernameInput;

    // Estos layouts se ocultan o muestran segun el modo
    LinearLayout confirmPasswordLayout;
    LinearLayout usernameLayout;

    Button submitButton;
    ProgressBar progressBar;
    TextView errorText;
    TextView forgotPassword;

    // true = modo login, false = modo registro
    boolean isLoginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        mAuth = FirebaseAuth.getInstance();

        // Si el usuario ya inicio sesion, lo mandamos directo a settings
        if (mAuth.getCurrentUser() != null) {
            goToSettings();
            return;
        }

        bindViews();
        setupRadioGroup();
        setupSubmitButton();
    }

    // Conectar las variables con los elementos del XML
    void bindViews() {
        modeRadioGroup       = findViewById(R.id.auth_mode_radio_group);
        loginRadio           = findViewById(R.id.auth_login_radio);
        registerRadio        = findViewById(R.id.auth_register_radio);
        emailInput           = findViewById(R.id.auth_email_input);
        passwordInput        = findViewById(R.id.auth_password_input);
        confirmPasswordInput = findViewById(R.id.auth_confirm_password_input);
        usernameInput        = findViewById(R.id.auth_username_input);
        confirmPasswordLayout = findViewById(R.id.auth_confirm_password_layout);
        usernameLayout       = findViewById(R.id.auth_username_layout);
        submitButton         = findViewById(R.id.auth_submit_button);
        progressBar          = findViewById(R.id.auth_progress);
        errorText            = findViewById(R.id.auth_error_text);
        forgotPassword       = findViewById(R.id.auth_forgot_password);
    }

    // Detectar cuando el usuario cambia entre Login y Registro
    void setupRadioGroup() {
        modeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            isLoginMode = checkedId == R.id.auth_login_radio;
            applyMode();
        });
    }

    // Mostrar u ocultar campos dependiendo del modo actual
    void applyMode() {
        clearError();
        if (isLoginMode) {
            usernameLayout.setVisibility(View.GONE);
            confirmPasswordLayout.setVisibility(View.GONE);
            submitButton.setText("Login");
            forgotPassword.setVisibility(View.VISIBLE);
        } else {
            usernameLayout.setVisibility(View.VISIBLE);
            confirmPasswordLayout.setVisibility(View.VISIBLE);
            submitButton.setText("Register");
            forgotPassword.setVisibility(View.GONE);
        }
    }

    // Validar los campos y llamar login o registro segun corresponda
    void setupSubmitButton() {
        submitButton.setOnClickListener(v -> {
            String email    = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            // Validaciones basicas
            if (TextUtils.isEmpty(email)) {
                showError("Email is required");
                return;
            }
            if (TextUtils.isEmpty(password)) {
                showError("Password is required");
                return;
            }

            if (!isLoginMode) {
                String username = usernameInput.getText().toString().trim();
                String confirm  = confirmPasswordInput.getText().toString().trim();

                if (TextUtils.isEmpty(username)) {
                    showError("Username is required");
                    return;
                }
                if (username.length() < 3) {
                    showError("Username must be at least 3 characters");
                    return;
                }
                // Verificar que las contrasenas coincidan
                if (!password.equals(confirm)) {
                    showError("Passwords do not match");
                    return;
                }
                register(email, password, username);
            } else {
                login(email, password);
            }
        });
    }

    // Iniciar sesion con Firebase
    void login(String email, String password) {
        setLoading(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        goToSettings();
                    } else {
                        String msg = task.getException() != null ? task.getException().getMessage() : "Login failed";
                        showError(msg);
                    }
                });
    }

    // Crear cuenta nueva en Firebase
    void register(String email, String password, String username) {
        setLoading(true);
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        String msg = task.getException() != null ? task.getException().getMessage() : "Registration failed";
                        showError(msg);
                        return;
                    }

                    // Guardar el nombre de usuario en el perfil de Firebase Auth
                    UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                            .setDisplayName(username)
                            .build();

                    assert mAuth.getCurrentUser() != null;
                    mAuth.getCurrentUser().updateProfile(profileUpdate)
                            .addOnCompleteListener(profileTask -> {
                                setLoading(false);
                                goToSettings();
                            });
                });
    }

    // Ir a la pantalla principal después de autenticarse
    void goToSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    // Mostrar u ocultar el indicador de carga
    void setLoading(boolean loading) {
        if (loading) {
            progressBar.setVisibility(View.VISIBLE);
            submitButton.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            submitButton.setEnabled(true);
        }
    }

    void showError(String msg) {
        errorText.setText(msg);
        errorText.setVisibility(View.VISIBLE);
    }

    void clearError() {
        errorText.setVisibility(View.GONE);
    }
}