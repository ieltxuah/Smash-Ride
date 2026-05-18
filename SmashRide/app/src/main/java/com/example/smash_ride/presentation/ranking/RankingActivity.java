package com.example.smash_ride.presentation.ranking;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.smash_ride.R;
import com.example.smash_ride.core.audio.SoundManager;
import com.example.smash_ride.core.graphics.GifHardwareDecoder;
import com.example.smash_ride.core.ui.BaseActivity;
import com.example.smash_ride.data.local.LocalDatabaseHelper;
import com.example.smash_ride.data.local.PreferenceHelper;
import com.example.smash_ride.framework.translation.LocaleUtils;
import com.example.smash_ride.framework.translation.TranslationManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Actividad que muestra la clasificación global y por modos de juego.
 * Permite filtrar por diferentes criterios como bajas, vidas perdidas y golpes dados.
 * Gestiona la sincronización entre Cloud Firestore y la base de datos local SQLite.
 */
public class RankingActivity extends BaseActivity {
    private LocalDatabaseHelper dbHelper;
    private PreferenceHelper prefHelper;
    private ListView listView;
    private String currentCriteria = "totalKills";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);
        //generateDummyRankings();

        dbHelper = new LocalDatabaseHelper(this);
        prefHelper = new PreferenceHelper(this);
        listView = findViewById(R.id.ranking_list);

        View emptyView = findViewById(R.id.empty_view);
        listView.setEmptyView(emptyView);

        // Configuración estética de fondo
        GifHardwareDecoder.loadGif(this, findViewById(R.id.background_gif), R.raw.background_stars);

        setupButtons();
        currentCriteria = "totalKills";
        refreshUI(currentCriteria);
        syncFromFirestore();

        // Inicializar el sistema de traducción dinámico
        TranslationManager.getInstance().bindActivity(this);
        TranslationManager.getInstance().scanAndRegisterViews(findViewById(android.R.id.content));

        // Si el idioma no es nativo (ML Kit), forzar traducción inicial
        if (!LocaleUtils.isNativeLanguage(prefHelper.getLanguage())) {
            TranslationManager.getInstance().translateIfNeeded();
        } else {
            TranslationManager.getInstance().reloadTextsFromResources();
        }

        findViewById(R.id.back_menu).setOnClickListener(v -> finish());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TranslationManager.getInstance().unbindActivity();
    }

    // --- MÉTODOS DE CONFIGURACIÓN ---

    /**
     * Configura los listeners para los botones de filtrado de las pestañas de ranking.
     */
    private void setupButtons() {
        // GLOBAL
        findViewById(R.id.btn_kills).setOnClickListener(v -> refreshUI("totalKills"));
        findViewById(R.id.btn_lives).setOnClickListener(v -> refreshUI("totalLivesLost"));
        findViewById(R.id.btn_hits).setOnClickListener(v -> refreshUI("totalHitsDealt"));

        // MODO LIVES
        findViewById(R.id.btn_kills_live).setOnClickListener(v -> refreshUI("killsLivesMode"));
        findViewById(R.id.btn_hits_live).setOnClickListener(v -> refreshUI("hitsLivesMode"));
        findViewById(R.id.btn_lives_live).setOnClickListener(v -> refreshUI("livesLostLivesMode"));

        // MODO TIMER (Añadidos los que faltaban)
        findViewById(R.id.btn_kills_timer).setOnClickListener(v -> refreshUI("killsTimerMode"));
        findViewById(R.id.btn_hits_timer).setOnClickListener(v -> refreshUI("hitsTimerMode"));
        findViewById(R.id.btn_lives_timer).setOnClickListener(v -> refreshUI("livesLostTimerMode"));

        // ESPECIALES
        findViewById(R.id.btn_timer_king).setOnClickListener(v -> refreshUI("maxKillsInTimer"));
        findViewById(R.id.btn_perfect).setOnClickListener(v -> refreshUI("perfectVictories"));
    }

    // --- LÓGICA DE DATOS ---

    /**
     * Sincroniza la colección de rankings de Firestore y los guarda localmente.
     */
    private void syncFromFirestore() {
        FirebaseFirestore.getInstance().collection("rankings").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                List<LocalDatabaseHelper.RankingRow> rows = new ArrayList<>();
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    rows.add(new LocalDatabaseHelper.RankingRow(
                            doc.getString("userId"),
                            doc.getString("userName"),
                            safeLongToInt(doc, "totalKills"),
                            safeLongToInt(doc, "totalHitsDealt"),
                            safeLongToInt(doc, "totalLivesLost"),
                            safeLongToInt(doc, "killsLivesMode"),
                            safeLongToInt(doc, "hitsLivesMode"),
                            safeLongToInt(doc, "livesLostLivesMode"),
                            safeLongToInt(doc, "killsTimerMode"),
                            safeLongToInt(doc, "hitsTimerMode"),
                            safeLongToInt(doc, "livesLostTimerMode"),
                            safeLongToInt(doc, "maxKillsInTimer"),
                            safeLongToInt(doc, "perfectVictories")
                    ));
                }
                dbHelper.saveRankings(rows);
                refreshUI(currentCriteria);
            }
        });
    }

    /**
     * Actualiza la interfaz de usuario con los datos de ranking actuales filtrados.
     *
     * @param criteria Nombre de la columna por la cual realizar el filtrado.
     */
    private void refreshUI(String criteria) {
        currentCriteria = criteria;
        updateTabStyles(criteria);
        List<LocalDatabaseHelper.RankingRow> top50 = dbHelper.getTop50(criteria);

        // 1. Declarar myId como FINAL para que sea accesible desde el adaptador
        final String myId = prefHelper.getUserId();

        // Verificamos si el ranking global está realmente vacío
        boolean isRankingEmpty = true;
        for (LocalDatabaseHelper.RankingRow r : top50) {
            if (getValueByCriteria(r, criteria) > 0) {
                isRankingEmpty = false;
                break;
            }
        }

        if (isRankingEmpty) {
            top50.clear();
        } else {
            boolean tempInTop50 = false;
            for (LocalDatabaseHelper.RankingRow r : top50) {
                if (r.userId != null && r.userId.equals(myId)) {
                    tempInTop50 = true;
                    break;
                }
            }

            // 2. Declarar finalImInTop50 como FINAL para el adaptador
            final boolean finalImInTop50 = tempInTop50;
            LocalDatabaseHelper.RankingRow myData = dbHelper.getUserData(myId);

            if (!finalImInTop50 && myData != null) {
                // 3. Corregido: El constructor de RankingRow requiere 13 argumentos
                top50.add(new LocalDatabaseHelper.RankingRow("ellipsis", "...",
                        0, 0, 0, // global
                        0, 0, 0, // live mode
                        0, 0, 0, // timer mode
                        0, 0));  // specials
                top50.add(myData);
            }
        }

        // 4. Aseguramos que el adaptador use las variables finales
        final boolean effectivelyFinalImInTop50 = (top50.size() > 0); // Re-chequeo de seguridad

        ArrayAdapter<LocalDatabaseHelper.RankingRow> adapter = new ArrayAdapter<LocalDatabaseHelper.RankingRow>(this, R.layout.item_ranking, top50) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.item_ranking, parent, false);
                LocalDatabaseHelper.RankingRow item = getItem(position);
                if (item == null) return v;

                TextView tvPos = v.findViewById(R.id.rank_pos);
                TextView tvName = v.findViewById(R.id.rank_name);
                TextView tvVal = v.findViewById(R.id.rank_kills);

                tvPos.setAllCaps(false);
                tvName.setAllCaps(false);
                tvVal.setAllCaps(false);

                if (item.userId.equals("ellipsis")) {
                    tvPos.setText(""); tvName.setText("..."); tvVal.setText("");
                } else {
                    // Aquí usamos myId que ya es final arriba
                    int realPos = (item.userId.equals(myId) && position >= 50) ?
                            dbHelper.getMyPosition(myId, currentCriteria) : position + 1;

                    tvPos.setText(String.valueOf(realPos));
                    tvName.setText(item.userName);
                    tvVal.setText(String.valueOf(getValueByCriteria(item, currentCriteria)));

                    if (item.userId.equals(myId)) {
                        tvName.setTextColor(Color.YELLOW);

                        GradientDrawable gd = new GradientDrawable();
                        gd.setColor(Color.parseColor("#33FFFFFF"));
                        gd.setStroke(3, Color.WHITE);
                        gd.setCornerRadius(15);
                        v.setBackground(gd);
                    }
                }
                return v;
            }
        };
        listView.setAdapter(adapter);
    }

    // --- MÉTODOS AUXILIARES ---

    /**
     * Mapea el criterio seleccionado con el valor correspondiente de la fila de ranking.
     */
    private int getValueByCriteria(LocalDatabaseHelper.RankingRow item, String criteria) {
        switch (criteria) {
            case "totalKills": return item.totalKills;
            case "totalHitsDealt": return item.totalHitsDealt;
            case "totalLivesLost": return item.totalLivesLost;
            case "killsLivesMode": return item.killsLivesMode;
            case "hitsLivesMode": return item.hitsLivesMode;
            case "livesLostLivesMode": return item.livesLostLivesMode;
            case "killsTimerMode": return item.killsTimerMode;
            case "hitsTimerMode": return item.hitsTimerMode;
            case "livesLostTimerMode": return item.livesLostTimerMode;
            case "maxKillsInTimer": return item.maxKillsInTimer;
            case "perfectVictories": return item.perfectVictories;
            default: return 0;
        }
    }

    /**
     * Actualiza el estilo visual de los botones de filtrado para resaltar el seleccionado.
     */
    private void updateTabStyles(String selectedCriteriaId) {
        ViewGroup container = findViewById(R.id.selector_container);
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof Button) {
                Button btn = (Button) child;

                // Comparamos el tag del botón o su ID con el criterio seleccionado
                // Para simplificar, compararemos el ID que configuramos en setupButtons
                boolean isSelected = false;
                int id = btn.getId();

                if (id == R.id.btn_kills && selectedCriteriaId.equals("totalKills")) isSelected = true;
                else if (id == R.id.btn_lives && selectedCriteriaId.equals("totalLivesLost")) isSelected = true;
                else if (id == R.id.btn_hits && selectedCriteriaId.equals("totalHitsDealt")) isSelected = true;
                else if (id == R.id.btn_kills_live && selectedCriteriaId.equals("killsLivesMode")) isSelected = true;
                else if (id == R.id.btn_hits_live && selectedCriteriaId.equals("hitsLivesMode")) isSelected = true;
                else if (id == R.id.btn_lives_live && selectedCriteriaId.equals("livesLostLivesMode")) isSelected = true;
                else if (id == R.id.btn_kills_timer && selectedCriteriaId.equals("killsTimerMode")) isSelected = true;
                else if (id == R.id.btn_hits_timer && selectedCriteriaId.equals("hitsTimerMode")) isSelected = true;
                else if (id == R.id.btn_lives_timer && selectedCriteriaId.equals("livesLostTimerMode")) isSelected = true;
                else if (id == R.id.btn_timer_king && selectedCriteriaId.equals("maxKillsInTimer")) isSelected = true;
                else if (id == R.id.btn_perfect && selectedCriteriaId.equals("perfectVictories")) isSelected = true;

                if (isSelected) {
                    // Estilo SELECCIONADO: Fondo blanco (o amarillo) y texto negro
                    btn.setBackgroundColor(Color.YELLOW);
                    btn.setTextColor(Color.BLACK);
                } else {
                    // Estilo NORMAL: Fondo transparente y texto blanco
                    btn.setBackgroundColor(Color.TRANSPARENT);
                    btn.setTextColor(Color.WHITE);
                }
            }
        }
    }

    /**
     * Convierte de forma segura un valor Long recuperado de un documento Firestore a int.
     * Si el campo no existe o es null, devuelve 0. Esto evita NPEs al convertir Long a int.
     *
     * @param doc Documento Firestore (QueryDocumentSnapshot) desde el cual se lee el campo.
     * @param field Nombre del campo que contiene el valor Long.
     * @return El valor entero convertido desde Long, o 0 si el campo es null.
     */
    private int safeLongToInt(QueryDocumentSnapshot doc, String field) {
        Long val = doc.getLong(field);
        return val != null ? val.intValue() : 0;
    }

    /**
     * Genera y sube a Firestore 100 registros de ejemplo ("rankings") usando un WriteBatch.
     *
     * Cada documento tiene un userId con formato "GEN_XXX" (por ejemplo, "GEN_001") y un
     * userName generado combinando prefijos y sufijos. Se rellenan campos de estadísticas con
     * valores aleatorios coherentes para simular datos de juego.
     *
     * Notas importantes:
     * Se usa com.google.firebase.firestore.FirebaseFirestore.getInstance() para obtener la instancia.
     * Se prepara un WriteBatch y se añaden 100 operaciones batch.set().
     * Firestore permite un máximo de 500 operaciones por batch; aquí generamos 100, por lo que está dentro del límite.
     * Al finalizar se llama a batch.commit() y se adjuntan listeners para registrar éxito o fallo.
     *
     * Campos creados en cada documento:
     * userId: Identificador generado (String).
     * userName: Nombre de usuario generado (String), máximo 9 caracteres.
     * totalKills: Entero aleatorio (int).
     * totalHitsDealt: Entero calculado a partir de totalKills.
     * totalLivesLost: Entero aleatorio (int).
     * killsLivesMode, hitsLivesMode, livesLostLivesMode: Distribución de estadísticas por modo "Lives".
     * killsTimerMode, hitsTimerMode, livesLostTimerMode: Distribución de estadísticas por modo "Timer".
     * maxKillsInTimer: Máximo de kills en modo timer (int aleatorio).
     * perfectVictories: Número de victorias perfectas (int aleatorio).
     */
    private void generateDummyRankings() {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        com.google.firebase.firestore.WriteBatch batch = db.batch();
        java.util.Random random = new java.util.Random();

        // Nombres base para combinar y no exceder 9 caracteres
        String[] prefixes = {"Star", "Ride", "Nova", "Dark", "Ace", "Z", "Sky", "Max"};
        String[] suffixes = {"X", "01", "7", "Pro", "Go", "Win", "99"};

        for (int i = 1; i <= 100; i++) {
            String userId = "GEN_" + String.format("%03d", i); // GEN_001, GEN_002...

            // Generar nombre de máximo 9 caracteres
            String name = prefixes[random.nextInt(prefixes.length)] + suffixes[random.nextInt(suffixes.length)];
            if (name.length() > 9) name = name.substring(0, 9);

            Map<String, Object> data = new HashMap<>();
            data.put("userId", userId);
            data.put("userName", name);

            // Valores aleatorios coherentes
            int totalKills = random.nextInt(500);
            data.put("totalKills", totalKills);
            data.put("totalHitsDealt", totalKills * 3 + random.nextInt(100));
            data.put("totalLivesLost", random.nextInt(200));

            // Distribución por modos
            data.put("killsLivesMode", totalKills / 2);
            data.put("hitsLivesMode", (totalKills * 3) / 2);
            data.put("livesLostLivesMode", 50);

            data.put("killsTimerMode", totalKills / 2);
            data.put("hitsTimerMode", (totalKills * 3) / 2);
            data.put("livesLostTimerMode", 50);

            data.put("maxKillsInTimer", random.nextInt(50));
            data.put("perfectVictories", random.nextInt(10));

            com.google.firebase.firestore.DocumentReference ref = db.collection("rankings").document(userId);
            batch.set(ref, data);

            // Firestore permite máximo 500 operaciones por batch
            if (i % 500 == 0) { /* Por si escalaras a más de 500 */ }
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            Log.d("GENERATOR", "100 registros generados con éxito");
        }).addOnFailureListener(e -> {
            Log.e("GENERATOR", "Error al generar datos", e);
        });
    }
}