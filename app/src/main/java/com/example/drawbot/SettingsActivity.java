package com.example.drawbot;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private EditText etStepsPerMm, etDefaultSpeed, etMaxSpeed;
    private Button btnSendCalibration, btnDeleteProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etStepsPerMm = findViewById(R.id.etStepsPerMm);
        etDefaultSpeed = findViewById(R.id.etDefaultSpeed);
        etMaxSpeed = findViewById(R.id.etMaxSpeed);
        btnSendCalibration = findViewById(R.id.btnSendCalibration);
        btnDeleteProfile = findViewById(R.id.btnDeleteProfile);

        // Kalibrierwerte beim Öffnen laden
        loadCalibration();

        btnSendCalibration.setOnClickListener(v -> sendCalibration());
        btnDeleteProfile.setOnClickListener(v -> deleteProfile());
    }

    private void loadCalibration() {
        String steps = getSharedPreferences("calibration", MODE_PRIVATE).getString("steps_per_mm", "");
        String defSpeed = getSharedPreferences("calibration", MODE_PRIVATE).getString("default_speed", "");
        String maxSpeed = getSharedPreferences("calibration", MODE_PRIVATE).getString("max_speed", "");
        etStepsPerMm.setText(steps);
        etDefaultSpeed.setText(defSpeed);
        etMaxSpeed.setText(maxSpeed);
    }

    private void sendCalibration() {
        String steps = etStepsPerMm.getText().toString().trim();
        String defSpeed = etDefaultSpeed.getText().toString().trim();
        String maxSpeed = etMaxSpeed.getText().toString().trim();
        if (steps.isEmpty() || defSpeed.isEmpty() || maxSpeed.isEmpty()) {
            Toast.makeText(this, "Bitte alle Kalibrierwerte eingeben", Toast.LENGTH_SHORT).show();
            return;
        }
        // Werte lokal speichern (SharedPreferences)
        getSharedPreferences("calibration", MODE_PRIVATE)
                .edit()
                .putString("steps_per_mm", steps)
                .putString("default_speed", defSpeed)
                .putString("max_speed", maxSpeed)
                .apply();
        Toast.makeText(this, "Kalibrierung gespeichert", Toast.LENGTH_SHORT).show();
    }

    private void deleteProfile() {
        // Profil-Daten löschen (SharedPreferences clearen)
        getSharedPreferences("user_profile", MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences("calibration", MODE_PRIVATE).edit().clear().apply();
        Toast.makeText(this, "Profil gelöscht", Toast.LENGTH_SHORT).show();
        // Optional: App zurück zum Login-Screen
        finish();
    }
}
