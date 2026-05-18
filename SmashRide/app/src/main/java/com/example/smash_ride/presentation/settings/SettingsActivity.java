package com.example.smash_ride.presentation.settings;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.constants.AppConstants;
import com.example.smash_ride.core.ui.BaseActivity;
import com.example.smash_ride.data.local.LocalDatabaseHelper;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.data.repository.AuthManager;
import com.example.smash_ride.presentation.main.MainActivity;
import com.example.smash_ride.framework.translation.LocaleUtils;
import com.example.smash_ride.framework.translation.TranslationManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Actividad de Ajustes de la aplicación.
 * Permite gestionar el perfil del usuario (Google Sign-In), el idioma,
 * el volumen del juego y la personalización estética del personaje.
 */
public class SettingsActivity extends BaseActivity {

    private static final String TAG = "SettingsActivity";
    private static final int RC_SIGN_IN = 9001;

    private TranslationManager translationManager;
    private PreferenceHelper prefHelper;
    private AuthManager authManager;
    private LocalDatabaseHelper dbHelper;
    private GoogleSignInClient mGoogleSignInClient;
    private String lastGuestId;

    private Spinner langSpinner;
    private SeekBar musicSeekBar;
    private SeekBar effectsSeekBar;
    private RecyclerView carouselRv;
    private EditText userNameText;
    private TextView authStatusLabel;
    private Button loginLogoutButton;
    private Button deleteDataButton;
    private Button migrateDataButton;
    private String[] labels;

    private String[] codes;
    private String selectedColorTag = "dorado";
    private String currentLang;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefHelper = new PreferenceHelper(this);
        authManager = new AuthManager(prefHelper);
        dbHelper = new LocalDatabaseHelper(this);
        currentLang = prefHelper.getLanguage();

        // 1. Aplicar idioma al contexto base antes de inflar vistas
        LocaleUtils.applyAppLocale(this, currentLang);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 2. Inicializar Manager y vincular actividad
        translationManager = TranslationManager.getInstance();
        translationManager.bindActivity(this);

        // 3. Inicializar vistas y cargar datos (Música y Volumen incluidos)
        initViews();
        loadSettings();
        setupGoogleSDK();
        setupUserSection();
        setupColorCarousel();

        // CARGAR SONIDOS DE EFECTOS PARA EL PREVIEW
        SoundManager.getInstance().loadGameSounds(this);

        // 4. EJECUTAR TRADUCCIÓN (Asegurando que los títulos con ID se registren)
        initTranslation();
    }

    @Override protected void onResume() {
        super.onResume();
        SoundManager.getInstance().resumeMusic();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SoundManager.getInstance().pauseMusic();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (translationManager != null) translationManager.unbindActivity();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                // Delegamos la validación de Firebase al AuthManager
                authManager.signInWithGoogle(account.getIdToken(), new AuthManager.AuthCallback() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        setupUserSection();
                        String rawWelcome = getString(R.string.welcome_msg, user.getDisplayName());
                        translationManager.showTranslatedToast(rawWelcome);
                    }

                    @Override
                    public void onError(String error) {
                        String errorMsg = getString(R.string.error_auth_failed, error);
                        translationManager.showTranslatedToast(errorMsg);
                    }
                });
            } catch (ApiException e) {
                Log.e(TAG, "Google sign in failed", e);
            }
        }
    }

    // --- MÉTODOS DE INICIALIZACIÓN ---

    /**
     * Obtiene las referencias de los componentes de la interfaz y configura adaptadores básicos.
     */
    private void initViews() {
        langSpinner = findViewById(R.id.settings_language_spinner);
        musicSeekBar = findViewById(R.id.settings_music_seekbar);
        effectsSeekBar = findViewById(R.id.settings_effects_seekbar);
        Button saveButton = findViewById(R.id.settings_save_button);

        userNameText = findViewById(R.id.user_name_text);
        authStatusLabel = findViewById(R.id.auth_status_label);
        loginLogoutButton = findViewById(R.id.login_logout_button);
        deleteDataButton = findViewById(R.id.delete_data_button);

        migrateDataButton = findViewById(R.id.migrate_data_button);
        lastGuestId = dbHelper.getGuestId();

        musicSeekBar.setMax(4);
        effectsSeekBar.setMax(4);

        labels = getResources().getStringArray(R.array.supported_lang_labels);
        codes = getResources().getStringArray(R.array.supported_lang_codes);

        ArrayList<String> langList = new ArrayList<>(Arrays.asList(labels));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_text_item, langList);
        adapter.setDropDownViewResource(R.layout.spinner_text_item);
        langSpinner.setAdapter(adapter);

        saveButton.setOnClickListener(v -> saveAndExit());
        deleteDataButton.setOnClickListener(v -> showDeleteConfirmation());
        migrateDataButton.setOnClickListener(v -> showMigrateConfirmation());

        setupSeekBarListeners();
    }

    /**
     * Carga los valores actuales desde las preferencias locales a la interfaz de usuario.
     */
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

    /**
     * Inicializa el sistema de traducción y gestiona la localización de textos dinámicos (hints).
     */
    private void initTranslation() {
        // Configuramos el idioma destino en ML Kit
        translationManager.setTargetFromAppLang(currentLang);
        translationManager.scanAndRegisterViews(findViewById(android.R.id.content));

        // El TranslationManager por defecto traduce el .getText()
        // Si quieres traducir el Hint manualmente en ML Kit:
        if (userNameText != null && !LocaleUtils.isNativeLanguage(currentLang)) {
            translationManager.translateRaw(getString(R.string.name_hint), translatedHint -> {
                userNameText.setHint(translatedHint);
            });
        }

        // Lógica de traducción basada en el contexto del manager
        if (LocaleUtils.isNativeLanguage(currentLang)) {
            translationManager.reloadTextsFromResources();
            userNameText.setHint(R.string.name_hint);
        } else {
            translationManager.translateIfNeeded();
        }
    }

    /**
     * Configura el cliente de inicio de sesión de Google.
     */
    private void setupGoogleSDK() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        loginLogoutButton.setOnClickListener(v -> {
            if (!authManager.isLoggedIn()) {
                signIn();
            } else {
                signOut();
            }
        });
    }

    /**
     * Actualiza el estado visual de la sección de perfil según la sesión del usuario.
     */
    private void setupUserSection() {
        String savedName = prefHelper.getUserName();
        ImageView editIcon = findViewById(R.id.edit_icon_hint);

        if (authManager.isLoggedIn()) {
            authStatusLabel.setText(R.string.status_connected);
            loginLogoutButton.setText(R.string.logout_action);

            String currentUid = prefHelper.getUserId();
            String nameFromDb = dbHelper.getLocalUserName(currentUid);
            if (nameFromDb != null) {
                savedName = nameFromDb;
                // Opcional: Actualizar SharedPreferences para que coincida con la DB
                prefHelper.setUserName(nameFromDb);
            }

            // MODO EDITABLE: Activamos foco y teclado
            userNameText.setFocusable(true);
            userNameText.setFocusableInTouchMode(true);
            userNameText.setCursorVisible(true);
            userNameText.setEnabled(true);

            if (editIcon != null) editIcon.setVisibility(View.VISIBLE);

            if (migrateDataButton != null) {
                if (dbHelper.hasGuestWithData()) {
                    migrateDataButton.setVisibility(View.VISIBLE);
                } else {
                    migrateDataButton.setVisibility(View.GONE);
                }
            }
            deleteDataButton.setVisibility(View.VISIBLE);
        } else {
            authStatusLabel.setText(R.string.status_disconnected);
            loginLogoutButton.setText(R.string.login_action);

            // MODO LECTURA: Desactivamos foco y teclado para que actúe como un Label
            userNameText.setFocusable(false);
            userNameText.setFocusableInTouchMode(false);
            userNameText.setCursorVisible(false);
            // No usamos setEnabled(false) porque eso pondría el texto en gris

            if (editIcon != null) editIcon.setVisibility(View.GONE);

            if (migrateDataButton != null) migrateDataButton.setVisibility(View.GONE);
            deleteDataButton.setVisibility(View.GONE);
        }

        userNameText.setText(savedName);
    }

    /**
     * Define el comportamiento de los deslizadores para el control de volumen.
     */
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
                if (f) {
                    // 1. Guardar el valor temporalmente en preferencias
                    prefHelper.setEffectsVolume(p);

                    // 2. Actualizar el volumen interno del SoundManager
                    SoundManager.getInstance().updateVolume(SettingsActivity.this);

                    // 3. Reproducir el sonido de choque como "preview"
                    SoundManager.getInstance().playCollisionSound();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    /**
     * Configura el carrusel horizontal para la selección de color del personaje.
     */
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

    // --- ACCIONES DE PERSISTENCIA Y NAVEGACIÓN ---

    /**
     * Almacena los cambios realizados en las preferencias y sincroniza con Firestore.
     */
    private void saveAndExit() {
        // Si estaba en modo editable, guardamos el texto actual
        String newName = userNameText.getText().toString().trim();

        if (newName.isEmpty()) {
            translationManager.showTranslatedToast(getString(R.string.error_name_empty));
            return;
        }

        // 1. Guardar en SharedPreferences para uso inmediato
        prefHelper.setUserName(newName);

        // 2. Guardar en SQLite (LocalDatabaseHelper)
        String currentId = prefHelper.getUserId();
        boolean isLoggedIn = authManager.isLoggedIn();
        dbHelper.saveLocalUser(currentId, newName, !isLoggedIn);
        dbHelper.updateRankingName(currentId, newName);

        // 3. Sincronización con Firestore (Nube)
        // Usamos SET con MERGE para evitar el error "NOT_FOUND"
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", currentId);
        userData.put("userName", newName);

        FirebaseFirestore.getInstance().collection("rankings").document(currentId)
                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Firestore sincronizado con éxito"))
                .addOnFailureListener(e -> Log.e(TAG, "Error al sincronizar Firestore", e));

        // 4. Guardar otros ajustes y salir
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

    /** Inicia el flujo de autenticación de Google. */
    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    /** Cierra la sesión de Firebase y Google, restaurando el ID de invitado si existe. */
    private void signOut() {
        authManager.logout(() -> {
            mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
                // BUSCAR GUEST PREVIO EN DB LOCAL
                String existingGuestId = dbHelper.getGuestId();

                if (existingGuestId != null) {
                    prefHelper.setUserId(existingGuestId);
                    // Recuperar el nombre real de la DB para ese Guest
                    String lastGuestName = dbHelper.getLocalUserName(existingGuestId);
                    if (lastGuestName != null) {
                        prefHelper.setUserName(lastGuestName);
                    }
                } else {
                    prefHelper.setUserId(null);
                    prefHelper.getOrCreateId();
                    prefHelper.setUserName("STAR_USER");
                }

                setupUserSection();
                translationManager.showTranslatedToast(getString(R.string.logout_msg));
            });
        });
    }

    // --- ACCIONES DE DATOS ---

    /**
     * Realiza la migración de datos acumulados como invitado a la cuenta registrada.
     */
    private void migrateGuestData() {
        FirebaseUser user = authManager.getCurrentUser();
        // Validamos que estemos logueados y que realmente exista un Guest Id en la DB local
        lastGuestId = dbHelper.getGuestId();

        if (user == null || lastGuestId == null) {
            translationManager.showTranslatedToast(getString(R.string.msg_no_guest_data));
            return;
        }

        String currentUserId = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Obtener datos del Guest desde Firestore
        db.collection("rankings").document(lastGuestId).get().addOnSuccessListener(guestDoc -> {
            if (!guestDoc.exists()) {
                // Si no hay datos en la nube, limpiamos la DB local por si acaso y ocultamos el botón
                dbHelper.deleteGuestUsers();
                setupUserSection();
                return;
            }

            // 2. Obtener datos actuales del Usuario Google para sumar estadísticas
            db.collection("rankings").document(currentUserId).get().addOnSuccessListener(userDoc -> {
                Map<String, Object> guestData = guestDoc.getData();
                Map<String, Object> mergedData = new HashMap<>(guestData);

                if (userDoc.exists()) {
                    // Sumar estadísticas numéricas de forma dinámica
                    for (String key : guestData.keySet()) {
                        Object val = guestData.get(key);
                        if (val instanceof Number) {
                            long gVal = ((Number) val).longValue();
                            long uVal = userDoc.getLong(key) != null ? userDoc.getLong(key) : 0;
                            mergedData.put(key, gVal + uVal);
                        }
                    }
                }

                // Asegurar que el ID y el nombre sean los actuales
                mergedData.put("userId", currentUserId);
                mergedData.put("userName", prefHelper.getUserName());

                // 3. Guardar en Firestore, borrar el Guest de la nube y ACTUALIZAR LOCAL
                db.collection("rankings").document(currentUserId).set(mergedData)
                        .addOnSuccessListener(aVoid -> {
                            // BORRADO EN LA NUBE DEL GUEST
                            db.collection("rankings").document(lastGuestId).delete();

                            // --- ACTUALIZACIÓN LOCAL (VITAL) ---
                            // Movemos los datos de ranking local del Guest al Usuario actual
                            dbHelper.migrateRankingDataLocally(lastGuestId, currentUserId);
                            // Borramos el rastro de la tabla local_users
                            dbHelper.deleteGuestUsers();

                            lastGuestId = null;

                            // Refrescar UI (Ocultará el botón Migrate automáticamente)
                            setupUserSection();

                            translationManager.showTranslatedToast(getString(R.string.msg_migration_success));
                        });
            });
        }).addOnFailureListener(e -> Log.e(TAG, "Migration failed", e));
    }

    /** Muestra diálogo de confirmación para migrar datos de invitado. */
    private void showMigrateConfirmation() {
        String title = getString(R.string.migrate_confirm_title);
        String msg = getString(R.string.migrate_confirm_msg);
        String posBtn = getString(R.string.migrate_action);
        String negBtn = getString(R.string.cancel_action);

        // Traducimos el cuerpo del mensaje por si el idioma es externo (ML Kit)
        translationManager.translateRaw(msg, translatedMsg -> {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(translatedMsg)
                    .setPositiveButton(posBtn, (dialog, which) -> {
                        migrateGuestData(); // Ejecuta la lógica Firestore/SQLite
                    })
                    .setNegativeButton(negBtn, null)
                    .show();
        });
    }

    /** Muestra diálogo de confirmación para eliminar permanentemente los datos del usuario. */
    private void showDeleteConfirmation() {
        // Obtenemos los textos traducidos para el diálogo
        String title = getString(R.string.delete_confirm_title);
        String msg = getString(R.string.delete_confirm_msg);
        String posBtn = getString(R.string.delete_action);
        String negBtn = getString(R.string.cancel_action);

        // Usamos translateRaw para asegurar que si el idioma es Chino/etc, ML Kit lo traduzca
        translationManager.translateRaw(msg, translatedMsg -> {
            new AlertDialog.Builder(this)
                    .setTitle(title) // El título suele ser estático o traducido por recursos
                    .setMessage(translatedMsg)
                    .setPositiveButton(posBtn, (dialog, which) -> {
                        String currentId = prefHelper.getUserId();

                        // 1. Borrar en la NUBE (Firestore)
                        FirebaseFirestore.getInstance().collection("rankings").document(currentId)
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    // 2. Borrar en la DB LOCAL (SQLite)
                                    dbHelper.deleteUserRanking(currentId);

                                    // 3. Resetear preferencias de sonido y nombre (opcional UX)
                                    prefHelper.setUserName("Star_User");
                                    userNameText.setText("Star_User");

                                    translationManager.showTranslatedToast(getString(R.string.msg_delete_success));
                                    setupUserSection();
                                })
                                .addOnFailureListener(e -> {
                                    translationManager.showTranslatedToast(getString(R.string.error_delete_cloud));
                                });
                    })
                    .setNegativeButton(negBtn, null)
                    .show();
        });
    }


    // --- ADAPTADOR INTERNO ---

    /**
     * Adaptador para el RecyclerView que muestra las opciones de color del personaje.
     */
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