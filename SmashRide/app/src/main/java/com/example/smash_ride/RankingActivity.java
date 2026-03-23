package com.example.smash_ride;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class RankingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);

        ArrayList<String> names = getIntent().getStringArrayListExtra("NAMES");
        ArrayList<Integer> kills = getIntent().getIntegerArrayListExtra("KILLS");

        ArrayList<String> display = new ArrayList<>();
        if (names != null && kills != null && names.size() == kills.size()) {
            ArrayList<Integer> idx = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) idx.add(i);
            Collections.sort(idx, Comparator.comparingInt((Integer i) -> kills.get(i)).reversed());
            for (int i = 0; i < idx.size(); i++) {
                int j = idx.get(i);
                display.add((i + 1) + ". " + names.get(j) + " — Kills: " + kills.get(j));
            }
        }

        ListView lv = findViewById(R.id.ranking_list);
        lv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, display));

        Button back = findViewById(R.id.back_menu);
        back.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}