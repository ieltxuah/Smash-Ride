package com.example.smash_ride.features.settings;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smash_ride.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.constants.AppConstants;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.features.main.MainActivity;
import com.example.smash_ride.translation.LocaleUtils;
import com.example.smash_ride.translation.TranslationManager;

import java.util.ArrayList;
import java.util.Arrays;

public class SettingsActivity extends AppCompatActivity {

    private TranslationManager translationManager;
    private PreferenceHelper prefHelper;

    private FirebaseAuth mAuth;

    private Spinner langSpinner;
    private SeekBar musicSeekBar;
    private SeekBar effectsSeekBar;
    private RecyclerView carouselRv;

    private TextView userNameText;
    private TextView authStatusLabel;
    private Button loginLogoutButton;

    private String[] codes;
    private String selectedColorTag = "dorado";
    private String currentLang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefHelper = new PreferenceHelper(this);
        currentLang = prefHelper.getLanguage();

        // 1. Aplicar idioma al contexto base antes de inflar vistas
        LocaleUtils.applyAppLocale(this, currentLang);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 2. Inicializar Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // 3. Inicializar Manager y vincular actividad
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);

        // 4. Inicializar vistas y cargar datos (Música y Volumen incluidos)
        initViews();
        loadSettings();
        setupUserSection();
        setupColorCarousel();

        // 5. EJECUTAR TRADUCCIÓN (Asegurando que los títulos con ID se registren)
        initTranslation();
    }

    private void initViews() {
        langSpinner = findViewById(R.id.settings_language_spinner);
        musicSeekBar = findViewById(R.id.settings_music_seekbar);
        effectsSeekBar = findViewById(R.id.settings_effects_seekbar);
        Button saveButton = findViewById(R.id.settings_save_button);

        userNameText = findViewById(R.id.user_name_text);
        authStatusLabel = findViewById(R.id.auth_status_label);
        loginLogoutButton = findViewById(R.id.login_logout_button);
        Button deleteDataButton = findViewById(R.id.delete_data_button);

        musicSeekBar.setMax(4);
        effectsSeekBar.setMax(4);

        String[] labels = getResources().getStringArray(R.array.supported_lang_labels);
        codes = getResources().getStringArray(R.array.supported_lang_codes);

        ArrayList<String> langList = new ArrayList<>(Arrays.asList(labels));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_text_item, langList);
        adapter.setDropDownViewResource(R.layout.spinner_text_item);
        langSpinner.setAdapter(adapter);

        saveButton.setOnClickListener(v -> saveAndExit());
        deleteDataButton.setOnClickListener(v -> showDeleteConfirmation());
        deleteDataButton = findViewById(R.id.delete_data_button);
        deleteDataButton.setVisibility(View.VISIBLE);

        setupSeekBarListeners();
    }

    private void initTranslation() {
        // Configuramos el idioma destino en ML Kit
        translationManager.setTargetFromAppLang(currentLang);

        // Obtenemos la vista raíz para asegurar que escanee TODO (títulos con ID incluidos)
        View root = findViewById(android.R.id.content);
        translationManager.scanAndRegisterViews(root);

        // EXCLUSIÓN UX: No traducir el nombre de usuario (usando unregisterView que añadiste)
        if (userNameText != null) {
            translationManager.unregisterView(userNameText);
        }

        // Lógica de traducción basada en el contexto del manager
        if (currentLang.equals("es") || currentLang.equals("eu") || currentLang.equals("en")) {
            translationManager.reloadTextsFromResources();
        } else {
            translationManager.translateIfNeeded();
        }
    }

    private void setupUserSection() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        boolean isLoggedIn = (currentUser != null);

        if (isLoggedIn) {
            if (authStatusLabel != null) authStatusLabel.setText(R.string.status_connected);
            if (loginLogoutButton != null) {
                loginLogoutButton.setText(R.string.logout_action);
                // Cambiamos el color a ROJO para Logout
                loginLogoutButton.setTextColor(android.graphics.Color.parseColor("#FF5252"));
                if (loginLogoutButton instanceof com.google.android.material.button.MaterialButton) {
                    ((com.google.android.material.button.MaterialButton) loginLogoutButton)
                            .setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF5252")));
                }
            }
            if (userNameText != null) userNameText.setText(getDisplayUsername(currentUser));
        } else {
            if (authStatusLabel != null) authStatusLabel.setText(R.string.status_disconnected);
            if (loginLogoutButton != null) {
                loginLogoutButton.setText(R.string.login_action);
                // Restauramos el color VERDE para Iniciar Sesión
                loginLogoutButton.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                if (loginLogoutButton instanceof com.google.android.material.button.MaterialButton) {
                    ((com.google.android.material.button.MaterialButton) loginLogoutButton)
                            .setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
                }
            }
        }

        loginLogoutButton.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() != null) {
                logout();
            } else {
                startActivity(new Intent(this, com.example.smash_ride.features.auth.AuthActivity.class));
            }
        });
    }

    // Devuelve username si existe, si no usa email
    private String getDisplayUsername(FirebaseUser user) {
        String name = user.getDisplayName();
        if (name != null && !name.isEmpty()) return name;
        String email = user.getEmail();
        return (email != null) ? email.split("@")[0] : "STAR_USER";
    }

    private void setupSeekBarListeners() {
        musicSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean f) {
                if (f) {
                    prefHelper.setMusicVolume(p);
                    // Actualización inmediata del volumen de música
                    SoundManager.getInstance().updateVolume(SettingsActivity.this);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        effectsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean f) {
                if (f) prefHelper.setEffectsVolume(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void saveAndExit() {
        String selLang = codes[langSpinner.getSelectedItemPosition()];
        prefHelper.setLanguage(selLang);
        prefHelper.setMusicVolume(musicSeekBar.getProgress());
        prefHelper.setEffectsVolume(effectsSeekBar.getProgress());
        prefHelper.setCharacterColor(selectedColorTag);

        // Asegurar que el SoundManager guarde el volumen final
        SoundManager.getInstance().updateVolume(this);

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        // UX: Evitar pantallazo blanco (Transición instantánea)
        overridePendingTransition(0, 0);
        finish();
    }

    private void logout() {
        mAuth.signOut();
        updateUI(null);
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            userNameText.setText(getDisplayUsername(user));
            loginLogoutButton.setText(R.string.logout_action);
            loginLogoutButton.setTextColor(android.graphics.Color.parseColor("#FF5252"));
            if (loginLogoutButton instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) loginLogoutButton)
                        .setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF5252")));
            }
        } else {
            userNameText.setText("STAR_USER");
            loginLogoutButton.setText(R.string.login_action);
            loginLogoutButton.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
            if (loginLogoutButton instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) loginLogoutButton)
                        .setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
            }
        }
    }

    private void loadSettings() {
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(currentLang)) {
                langSpinner.setSelection(i);
                break;
            }
        }
        musicSeekBar.setProgress(prefHelper.getMusicVolume());
        effectsSeekBar.setProgress(prefHelper.getEffectsVolume());
        selectedColorTag = prefHelper.getCharacterColor();
        SoundManager.getInstance().updateVolume(this);
    }

    private void setupColorCarousel() {
        carouselRv = findViewById(R.id.color_carousel_rv);
        if (carouselRv == null) return;

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int itemWidth = (int) (120 * dm.density);
        int padding = (dm.widthPixels / 2) - (itemWidth / 2);

        carouselRv.setPadding(padding, 0, padding, 0);
        carouselRv.setClipToPadding(false);

        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        carouselRv.setLayoutManager(lm);
        carouselRv.setAdapter(new ColorAdapter(AppConstants.CAROUSEL_COLORS, AppConstants.CAROUSEL_HEX));

        carouselRv.setOnFlingListener(null);
        LinearSnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(carouselRv);

        int initialPos = 0;
        for (int i = 0; i < AppConstants.CAROUSEL_COLORS.length; i++) {
            if (AppConstants.CAROUSEL_COLORS[i].equalsIgnoreCase(selectedColorTag)) {
                initialPos = i;
                break;
            }
        }

        final int fPos = initialPos;
        // SOLUCIÓN DESALINEACIÓN: Doble post para forzar el centrado matemático
        carouselRv.post(() -> {
            lm.scrollToPositionWithOffset(fPos, 0);
            carouselRv.post(() -> {
                View view = lm.findViewByPosition(fPos);
                if (view != null) {
                    int[] snapDistance = snapHelper.calculateDistanceToFinalSnap(lm, view);
                    if (snapDistance != null) {
                        carouselRv.scrollBy(snapDistance[0], snapDistance[1]);
                    }
                }
            });
        });

        carouselRv.clearOnScrollListeners();
        carouselRv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                View centerView = snapHelper.findSnapView(lm);
                if (centerView != null) {
                    int pos = recyclerView.getChildAdapterPosition(centerView);
                    if (pos != RecyclerView.NO_POSITION) {
                        selectedColorTag = AppConstants.CAROUSEL_COLORS[pos].toLowerCase();
                    }
                }
            }
        });
    }

    private void showDeleteConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_data))
                .setMessage("¿Qué quieres borrar?")
                .setPositiveButton("CONFIGURACIÓN", (dialog, which) -> {
                    prefHelper.resetToDefaults();
                    selectedColorTag = "dorado";
                    currentLang = "es";
                    musicSeekBar.setProgress(2);
                    effectsSeekBar.setProgress(2);
                    SoundManager.getInstance().updateVolume(this);
                    setupColorCarousel();
                    Toast.makeText(this, "Configuración restablecida", Toast.LENGTH_SHORT).show();
                })
            .setNeutralButton("CANCELAR", null);

        if (mAuth.getCurrentUser() != null) {
            builder.setNegativeButton("CUENTA", (dialog, which) -> showReauthDialog());
        }

        builder.show();
    }

    private void showReauthDialog() {
        android.widget.EditText passwordInput = new android.widget.EditText(this);
        passwordInput.setHint("Contraseña");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Confirma tu contraseña")
                .setView(passwordInput)
                .setPositiveButton("CONFIRMAR", null)
                .setNegativeButton("CANCELAR", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = passwordInput.getText().toString().trim();
            if (password.isEmpty()) {
                Toast.makeText(this, "Introduce tu contraseña", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null || user.getEmail() == null) return;

            com.google.firebase.auth.AuthCredential credential =
                    com.google.firebase.auth.EmailAuthProvider.getCredential(user.getEmail(), password);

            user.reauthenticate(credential).addOnCompleteListener(reauthTask -> {
                if (!reauthTask.isSuccessful()) {
                    Toast.makeText(this, "Contraseña incorrecta", Toast.LENGTH_SHORT).show();
                    return;
                }
                user.delete().addOnCompleteListener(deleteTask -> {
                    if (deleteTask.isSuccessful()) {
                        dialog.dismiss();
                        Toast.makeText(this, "Cuenta eliminada", Toast.LENGTH_SHORT).show();
                        updateUI(null);
                    } else {
                        Exception e = deleteTask.getException();
                        String msg = (e != null && e.getMessage() != null) ? e.getMessage() : "Error desconocido";
                        Toast.makeText(this, "Error al borrar cuenta: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }));

        dialog.show();
    }

    @Override protected void onResume() {
        super.onResume();
        SoundManager.getInstance().resumeMusic();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        updateUI(currentUser);
    }

    @Override protected void onPause() {
        super.onPause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (translationManager != null) translationManager.unbindActivity();
    }

    private class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ViewHolder> {
        private final String[] names;
        private final int[] colors;
        ColorAdapter(String[] names, int[] colors) { this.names = names; this.colors = colors; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_carousel_player, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            h.img.setColorFilter(colors[p], PorterDuff.Mode.MULTIPLY);
            h.itemView.setOnClickListener(v -> carouselRv.smoothScrollToPosition(p));
        }
        @Override public int getItemCount() { return names.length; }
        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView img;
            ViewHolder(View v) { super(v); img = v.findViewById(R.id.img_player_preview); }
        }
    }
}
