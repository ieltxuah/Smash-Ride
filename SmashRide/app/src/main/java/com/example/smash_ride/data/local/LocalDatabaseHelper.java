package com.example.smash_ride.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class LocalDatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "rankings_db";
    private static final int DB_VERSION = 1;

    public LocalDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS rankings");
        onCreate(db);
    }

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

    public int getMyPosition(String userId, String column) {
        SQLiteDatabase db = getReadableDatabase();
        // Contamos cuántos tienen un valor mayor al mío
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM rankings WHERE " + column + " > (SELECT " + column + " FROM rankings WHERE userId = ?)", new String[]{userId});
        int pos = 0;
        if (c.moveToFirst()) pos = c.getInt(0) + 1;
        c.close();
        return pos;
    }

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