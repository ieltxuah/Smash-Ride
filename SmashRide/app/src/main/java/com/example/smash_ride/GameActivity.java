package com.example.smash_ride;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game); // Ensure your main layout is set

        loadingLayout = findViewById(R.id.loading_layout); // Reference to your loading layout (ProgressBar and TextView)
        players = new ArrayList<>();
        handler = new Handler(Looper.getMainLooper());

        // Start player initialization and loading
        initializePlayers();
    }

    private void initializePlayers() {
        // Create players in a separate thread
        new Thread(() -> {
            // Simulate player creation
            for (int i = 0; i < 4; i++) {
                players.add(new Player("Player " + (i + 1), getResources().getDisplayMetrics().widthPixels / 2,
                        getResources().getDisplayMetrics().heightPixels / 2, false, 5));
                try {
                    Thread.sleep(500); // Simulate delay for player creation
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Proceed to finish loading after 20 seconds
            handler.postDelayed(this::finishLoading, LOADER_DELAY_MS);
        }).start();
    }

    private void finishLoading() {
        // Hide loading layout and initialize GameView
        loadingLayout.setVisibility(View.GONE);
        gameView = new GameView(this, players);
        gameView.setGameOverListener(this);
        setContentView(gameView);
        gameView.resume();
    }

    @Override
    public void onGameOver() {
        gameView.pause();
        finish(); // Back to the menu
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
        }
    }
}
