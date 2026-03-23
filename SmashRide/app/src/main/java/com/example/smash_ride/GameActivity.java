package com.example.smash_ride;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class GameActivity extends AppCompatActivity implements GameOverListener {

    private GameView gameView;
    private List<Player> players; // List to hold multiple players
    private View loadingLayout; // Reference to the loading layout
    private static final long LOADER_DELAY_MS = 20000; // 20 seconds
    private Handler handler;
    private float centerX;
    private float centerY;
    private float radius;
    private GameView.GameMode selectedMode = GameView.GameMode.LIVES;
    private boolean offlineMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        loadingLayout = findViewById(R.id.loading_layout);
        players = new ArrayList<>();
        handler = new Handler(Looper.getMainLooper());

        String mode = getIntent().getStringExtra("GAME_MODE");
        if ("TIMER".equals(mode)) selectedMode = GameView.GameMode.TIMER;
        else selectedMode = GameView.GameMode.LIVES;
        offlineMode = getIntent().getBooleanExtra("OFFLINE", true);

        initializePlayers();
    }

    private void initializePlayers() {
        new Thread(() -> {
            // Use display metrics as initial approximate center; final correct positions will be set by GameView.onSizeChanged
            centerX = getResources().getDisplayMetrics().widthPixels / 2f;
            centerY = getResources().getDisplayMetrics().heightPixels / 2f;
            radius = Math.min(centerX, centerY) - 100;

            int[][] positions = {
                    { (int) centerX, (int) (centerY - radius) }, // North
                    { (int) centerX, (int) (centerY + radius) }, // South
                    { (int) (centerX + radius), (int) centerY }, // East
                    { (int) (centerX - radius), (int) centerY }  // West
            };

            // Start players with speed 0 so they don't move until joystick input
            for (int i = 0; i < 4; i++) {
                players.add(new Player("Player " + (i + 1), positions[i][0], positions[i][1], false, 0));
            }

            handler.postDelayed(this::finishLoading, LOADER_DELAY_MS);
        }).start();
    }

    private void finishLoading() {
        loadingLayout.setVisibility(View.GONE);
        gameView = new GameView(this, players);
        gameView.setGameOverListener(this);
        gameView.setGameMode(selectedMode);
        gameView.setOffline(offlineMode);
        setContentView(gameView);
        gameView.resume();
    }

    @Override
    public void onGameOver() {
        if (gameView != null) {
            gameView.pause();
        }

        int alive = 0;
        Player lastAlive = null;
        for (Player p : players) {
            if (!p.isDestroyed()) { alive++; lastAlive = p; }
        }

        if (selectedMode == GameView.GameMode.LIVES) {
            if (alive == 1 && lastAlive != null && lastAlive == players.get(0)) {
                startActivity(new Intent(this, WinActivity.class));
            } else {
                startActivity(new Intent(this, LoseActivity.class));
            }
        } else {
            Intent i = new Intent(this, RankingActivity.class);
            ArrayList<String> names = new ArrayList<>();
            ArrayList<Integer> kills = new ArrayList<>();
            for (Player p : players) {
                names.add(p.name);
                kills.add(p.getKills());
            }
            i.putStringArrayListExtra("NAMES", names);
            i.putIntegerArrayListExtra("KILLS", kills);
            startActivity(i);
        }

        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) {
            gameView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) {
            gameView.pause();
            if (players != null && players.size() > 0) {
                Player p1 = players.get(0);
                p1.destroy();
            }
            finish();
        }
    }
}
