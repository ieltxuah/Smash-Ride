package com.example.smash_ride.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestor de la base de datos local SQLite para el almacenamiento de rankings y datos de usuario.
 * Se encarga de la persistencia de datos cuando la aplicación está fuera de línea o para caché.
 */
public class LocalDatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "rankings_db";
    private static final int DB_VERSION = 1;

    /**
     * Constructor de la clase.
     *
     * @param context Contexto de la aplicación.
     */
    public LocalDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Tabla para almacenar los rankings sincronizados desde Firebase
        db.execSQL("CREATE TABLE rankings (" +
                "userId TEXT PRIMARY KEY, " +
                "userName TEXT, " +
                "totalKills INTEGER, " +
                "totalHitsDealt INTEGER, " +
                "totalLivesLost INTEGER, " +
                "killsLivesMode INTEGER, " +
                "hitsLivesMode INTEGER, " +
                "livesLostLivesMode INTEGER, " +
                "killsTimerMode INTEGER, " +
                "hitsTimerMode INTEGER, " +
                "livesLostTimerMode INTEGER, " +
                "maxKillsInTimer INTEGER, " +
                "perfectVictories INTEGER)");

        // Tabla para gestionar usuarios locales y cuentas de invitado
        db.execSQL("CREATE TABLE local_users (userId TEXT PRIMARY KEY, userName TEXT, isGuest INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS rankings");
        db.execSQL("DROP TABLE IF EXISTS local_users");
        onCreate(db);
    }

    // --- Gestión de Rankings ---

    /**
     * Guarda una lista de rankings en la base de datos local, reemplazando los existentes.
     *
     * @param list Lista de filas de ranking a persistir.
     */
    public void saveRankings(List<RankingRow> list) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("rankings", null, null);
            for (RankingRow r : list) {
                ContentValues cv = new ContentValues();
                cv.put("userId", r.userId);
                cv.put("userName", r.userName);
                cv.put("totalKills", r.totalKills);
                cv.put("totalHitsDealt", r.totalHitsDealt);
                cv.put("totalLivesLost", r.totalLivesLost);
                cv.put("killsLivesMode", r.killsLivesMode);
                cv.put("hitsLivesMode", r.hitsLivesMode);
                cv.put("livesLostLivesMode", r.livesLostLivesMode);
                cv.put("killsTimerMode", r.killsTimerMode);
                cv.put("hitsTimerMode", r.hitsTimerMode);
                cv.put("livesLostTimerMode", r.livesLostTimerMode);
                cv.put("maxKillsInTimer", r.maxKillsInTimer);
                cv.put("perfectVictories", r.perfectVictories);
                db.insert("rankings", null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Obtiene los 50 mejores jugadores según un criterio (columna) específico.
     *
     * @param column Nombre de la columna por la cual ordenar (ej: "totalKills").
     * @return Lista de los 50 mejores resultados.
     */
    public List<RankingRow> getTop50(String column) {
        List<RankingRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        // Importante: column debe ser una de las constantes de la tabla
        Cursor c = db.query("rankings", null, null, null, null, null, column + " DESC", "50");
        while (c.moveToNext()) {
            list.add(new RankingRow(
                    c.getString(0), c.getString(1), c.getInt(2), c.getInt(3), c.getInt(4),
                    c.getInt(5), c.getInt(6), c.getInt(7), c.getInt(8), c.getInt(9),
                    c.getInt(10), c.getInt(11), c.getInt(12)
            ));
        }
        c.close();
        return list;
    }

    /**
     * Calcula la posición de un usuario específico en un ranking determinado.
     *
     * @param userId Identificador único del usuario.
     * @param column Columna de puntuación.
     * @return Posición numérica (1-based).
     */
    public int getMyPosition(String userId, String column) {
        SQLiteDatabase db = getReadableDatabase();
        // Contamos cuántos tienen un valor mayor al mío
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM rankings WHERE " + column + " > (SELECT " + column + " FROM rankings WHERE userId = ?)", new String[]{userId});
        int pos = 0;
        if (c.moveToFirst()) pos = c.getInt(0) + 1;
        c.close();
        return pos;
    }

    /**
     * Recupera los datos de ranking de un usuario específico de la base de datos local.
     *
     * @param userId Identificador del usuario.
     * @return Objeto {@link RankingRow} con los datos o null si no existe.
     */
    public RankingRow getUserData(String userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("rankings", null, "userId=?", new String[]{userId}, null, null, null);
        RankingRow row = null;
        if (c.moveToFirst()) {
            row = new RankingRow(
                    c.getString(0), c.getString(1), // id, name
                    c.getInt(2), c.getInt(3), c.getInt(4), // global: kills, hits, lives
                    c.getInt(5), c.getInt(6), c.getInt(7), // live mode: kills, hits, lives
                    c.getInt(8), c.getInt(9), c.getInt(10), // timer mode: kills, hits, lives
                    c.getInt(11), c.getInt(12) // timer king, perfect
            );
        }
        c.close();
        return row;
    }

    /**
     * Elimina el registro de ranking de un usuario.
     *
     * @param userId ID del usuario a eliminar.
     */
    public void deleteUserRanking(String userId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("rankings", "userId = ?", new String[]{userId});
    }

    // --- Gestión de Usuarios Locales ---

    /**
     * Guarda o actualiza un usuario en la tabla de usuarios locales.
     *
     * @param id      ID del usuario.
     * @param name    Nombre a mostrar.
     * @param isGuest Indica si es una cuenta de invitado.
     */
    public void saveLocalUser(String id, String name, boolean isGuest) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("userId", id);
        cv.put("userName", name);
        cv.put("isGuest", isGuest ? 1 : 0);
        // CONFLICT_REPLACE hace el "update" si el userId ya existe
        db.insertWithOnConflict("local_users", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Obtiene el nombre guardado localmente para un usuario.
     *
     * @param userId ID del usuario.
     * @return El nombre del usuario o null si no se encuentra.
     */
    public String getLocalUserName(String userId) {
        if (userId == null) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("local_users", new String[]{"userName"}, "userId=?", new String[]{userId}, null, null, null);
        String name = null;
        if (c.moveToFirst()) {
            name = c.getString(0);
        }
        c.close();
        return name;
    }

    /**
     * Busca el ID de una cuenta de invitado existente.
     *
     * @return El ID del invitado o null si no hay ninguno.
     */
    public String getGuestId() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("local_users", new String[]{"userId"}, "isGuest=1", null, null, null, null);
        String id = null;
        if (c.moveToFirst()) id = c.getString(0);
        c.close();
        return id;
    }

    /**
     * Verifica si existe una cuenta de invitado que tenga datos de ranking registrados.
     *
     * @return true si hay un invitado con estadísticas, false en caso contrario.
     */
    public boolean hasGuestWithData() {
        SQLiteDatabase db = getReadableDatabase();

        // Comprobamos si hay algún usuario marcado como isGuest=1
        // que tenga una fila vinculada en la tabla 'rankings'
        String query = "SELECT COUNT(*) FROM local_users u " +
                "INNER JOIN rankings r ON u.userId = r.userId " +
                "WHERE u.isGuest = 1";

        Cursor c = db.rawQuery(query, null);
        boolean exists = false;
        if (c.moveToFirst()) {
            exists = c.getInt(0) > 0;
        }
        c.close();
        return exists;
    }

    /**
     * Elimina todos los usuarios marcados como invitados.
     */
    public void deleteGuestUsers() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("local_users", "isGuest = 1", null);
    }

    /**
     * Actualiza el nombre de un usuario en la tabla de rankings.
     *
     * @param userId  ID del usuario.
     * @param newName Nuevo nombre.
     */
    public void updateRankingName(String userId, String newName) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("userName", newName);
        db.update("rankings", cv, "userId = ?", new String[]{userId});
    }

    /**
     * Migra los datos de ranking de un ID de invitado a un nuevo ID de usuario registrado.
     *
     * @param guestId ID original de invitado.
     * @param userId  Nuevo ID de usuario (ej: Firebase UID).
     */
    public void migrateRankingDataLocally(String guestId, String userId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // 1. Borrar si el usuario ya tenía un registro previo (para evitar conflicto de PK)
            db.delete("rankings", "userId = ?", new String[]{userId});

            // 2. Cambiar el ID del registro del Guest por el del Usuario nuevo
            ContentValues cv = new ContentValues();
            cv.put("userId", userId);
            db.update("rankings", cv, "userId = ?", new String[]{guestId});

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Estructura de datos para representar una fila en el ranking.
     */
    public static class RankingRow {
        public String userId, userName;
        public int totalKills, totalHitsDealt, totalLivesLost;
        public int killsLivesMode, hitsLivesMode, livesLostLivesMode;
        public int killsTimerMode, hitsTimerMode, livesLostTimerMode;
        public int maxKillsInTimer, perfectVictories;

        public RankingRow(String id, String name, int tk, int th, int tl, int kl, int hl, int ll, int kt, int ht, int lt, int mkt, int pv) {
            this.userId = id; this.userName = name;
            this.totalKills = tk; this.totalHitsDealt = th; this.totalLivesLost = tl;
            this.killsLivesMode = kl; this.hitsLivesMode = hl; this.livesLostLivesMode = ll;
            this.killsTimerMode = kt; this.hitsTimerMode = ht; this.livesLostTimerMode = lt;
            this.maxKillsInTimer = mkt; this.perfectVictories = pv;
        }
    }
}