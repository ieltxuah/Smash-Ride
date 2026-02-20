package com.example.smash_ride;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class testFirebaseRTDB extends AppCompatActivity {
    private EditText nombreEdt, xPosEdt, yPosEdt, livesEdt;
    private ToggleButton invincibleTB;
    private DatabaseReference databaseReference;
    private Player player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_rtdb);

        nombreEdt   = findViewById(R.id.txtNombre);
        xPosEdt     = findViewById(R.id.xPos);
        yPosEdt     = findViewById(R.id.yPos);
        invincibleTB = findViewById(R.id.invincible);
        livesEdt    = findViewById(R.id.lives);
        Button button = findViewById(R.id.button);

        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance(
                "https://kirby-smash-ride-default-rtdb.europe-west1.firebasedatabase.app"
        );
        player = new Player();

        button.setOnClickListener(v -> {
            String nombre   = nombreEdt.getText().toString();
            String xPosStr  = xPosEdt.getText().toString();
            String yPosStr  = yPosEdt.getText().toString();
            String livesStr = livesEdt.getText().toString();
            boolean isInvincible = invincibleTB.isChecked();

            if (nombre.isEmpty() || xPosStr.isEmpty() || yPosStr.isEmpty() || livesStr.isEmpty()) {
                Toast.makeText(testFirebaseRTDB.this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            } else {
                float xPos = Float.parseFloat(xPosStr);
                float yPos = Float.parseFloat(yPosStr);
                int lives  = Integer.parseInt(livesStr);

                databaseReference = firebaseDatabase.getReference("Player/" + nombre);

                addDataToFirebase(xPos, yPos, isInvincible, lives);
            }
        });
    }

    private void addDataToFirebase(float xPos, float yPos, boolean invincible, int lives) {
        player.setXPos(xPos);
        player.setYPos(yPos);
        player.setInvincible(invincible);
        player.setLives(lives);

        databaseReference.setValue(player)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(testFirebaseRTDB.this, "Data added successfully!", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(error ->
                        Toast.makeText(testFirebaseRTDB.this, "Failed to add data: " + error.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}