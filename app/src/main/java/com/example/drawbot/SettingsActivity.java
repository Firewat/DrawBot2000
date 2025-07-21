package com.example.drawbot;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

public class SettingsActivity extends AppCompatActivity {
    private EditText etStepsPerMm, etDefaultSpeed, etMaxSpeed;
    private Button btnSendCalibration, btnDeleteProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initializeViews();
        loadCalibration();
        setupButtonListeners();

        String status = BluetoothTerminalActivity.getConnectionStatus();
        Log.d("SettingsActivity", "onCreate - Connection status: " + status);
    }

    private void initializeViews() {
        etStepsPerMm = findViewById(R.id.etStepsPerMm);
        etDefaultSpeed = findViewById(R.id.etDefaultSpeed);
        etMaxSpeed = findViewById(R.id.etMaxSpeed);
        btnSendCalibration = findViewById(R.id.btnSendCalibration);
        btnDeleteProfile = findViewById(R.id.btnDeleteProfile);
    }

    // created with AI for debugging purposes
    private void setupButtonListeners() {
        btnSendCalibration.setOnClickListener(v -> sendCalibrationToArduino());
        btnDeleteProfile.setOnClickListener(v -> deleteProfile());

        btnSendCalibration.setOnLongClickListener(v -> {
            BluetoothHelper helper = BluetoothTerminalActivity.getBluetoothHelper();
            String debugInfo = "BluetoothHelper: " + (helper != null ? "exists" : "null") + "\n" +
                    "Connection: " + BluetoothTerminalActivity.getConnectionStatus() + "\n" +
                    "isConnected(): " + BluetoothTerminalActivity.isBluetoothConnected();

            new AlertDialog.Builder(this)
                    .setTitle("Bluetooth Debug Info")
                    .setMessage(debugInfo)
                    .setPositiveButton("OK", null)
                    .show();
            return true;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void loadCalibration() {
        String steps = getSharedPreferences("calibration", MODE_PRIVATE).getString("steps_per_mm", "65.0");
        String defSpeed = getSharedPreferences("calibration", MODE_PRIVATE).getString("default_speed", "800.0");
        String maxSpeed = getSharedPreferences("calibration", MODE_PRIVATE).getString("max_speed", "10.0");

        etStepsPerMm.setText(steps);
        etDefaultSpeed.setText(defSpeed);
        etMaxSpeed.setText(maxSpeed);
    }

    private void sendCalibrationToArduino() {
        BluetoothHelper helper = BluetoothTerminalActivity.getBluetoothHelper();
        boolean isConnected = BluetoothTerminalActivity.isBluetoothConnected();
        String status = BluetoothTerminalActivity.getConnectionStatus();

        Log.d("SettingsActivity", "BluetoothHelper: " + (helper != null ? "exists" : "null"));
        Log.d("SettingsActivity", "Connection status: " + status);
        Log.d("SettingsActivity", "isConnected: " + isConnected);

        if (!isConnected) {
            new AlertDialog.Builder(this)
                    .setTitle("Not Connected")
                    .setMessage("Connection status: " + status + "\n\nPlease connect to your Arduino first in the main terminal.")
                    .setPositiveButton("Go to Terminal", (dialog, which) -> finish())
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        String steps = etStepsPerMm.getText().toString().trim();
        String defSpeed = etDefaultSpeed.getText().toString().trim();
        String maxSpeed = etMaxSpeed.getText().toString().trim();

        if (steps.isEmpty() || defSpeed.isEmpty() || maxSpeed.isEmpty()) {
            Toast.makeText(this, "Please enter all calibration values", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double stepsVal = Double.parseDouble(steps);
            double speedVal = Double.parseDouble(defSpeed);
            double accelVal = Double.parseDouble(maxSpeed);

            if (stepsVal <= 0 || speedVal <= 0 || accelVal <= 0) {
                Toast.makeText(this, "Please enter positive numbers only", Toast.LENGTH_SHORT).show();
                return;
            }

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid numbers only", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Send Calibration to Arduino")
                .setMessage("This will update your GRBL motor settings:\n\n" +
                        "Steps per mm: " + steps + "\n" +
                        "Max speed: " + defSpeed + " mm/min\n" +
                        "Acceleration: " + maxSpeed + " mm/sec²\n\n" +
                        "Send these settings to Arduino?")
                .setPositiveButton("Send Now", (dialog, which) -> {
                    sendGRBLCommands(steps, defSpeed, maxSpeed);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendGRBLCommands(String steps, String maxRate, String acceleration) {
        try {
            Toast.makeText(this, "Sending calibration to Arduino...", Toast.LENGTH_LONG).show();

            BluetoothHelper helper = BluetoothTerminalActivity.getBluetoothHelper();
            if (helper == null || !helper.isConnected()) {
                Toast.makeText(this, "Bluetooth connection lost", Toast.LENGTH_SHORT).show();
                return;
            }

            // Send GRBL configuration commands for 28BYJ-48 steppers
            // Steps per mm (your input applies to X and Y axes)
            helper.sendData("$100=" + steps + "\n");      // X steps/mm
            helper.sendData("$101=" + steps + "\n");      // Y steps/mm
            helper.sendData("$102=200.0\n");              // Z steps/mm (fixed for pen control)

            // Max rates (your speed input applies to all axes)
            helper.sendData("$110=" + maxRate + "\n");    // X max rate mm/min
            helper.sendData("$111=" + maxRate + "\n");    // Y max rate mm/min
            helper.sendData("$112=" + maxRate + "\n");    // Z max rate mm/min

            // Acceleration (your acceleration input applies to all axes)
            helper.sendData("$120=" + acceleration + "\n"); // X acceleration mm/sec²
            helper.sendData("$121=" + acceleration + "\n"); // Y acceleration mm/sec²
            helper.sendData("$122=" + acceleration + "\n"); // Z acceleration mm/sec²

            getSharedPreferences("calibration", MODE_PRIVATE)
                    .edit()
                    .putString("steps_per_mm", steps)
                    .putString("default_speed", maxRate)
                    .putString("max_speed", acceleration)
                    .apply();

            Toast.makeText(this, "Calibration sent successfully!\nSettings saved locally.", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error sending calibration: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteProfile() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Profile")
                .setMessage("This will delete all saved calibration settings and reset to defaults.\n\nAre you sure?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Clear all saved preferences
                    getSharedPreferences("user_profile", MODE_PRIVATE).edit().clear().apply();
                    getSharedPreferences("calibration", MODE_PRIVATE).edit().clear().apply();

                    // Reset fields to optimized defaults for 28BYJ-48
                    etStepsPerMm.setText("65.0");    // Optimized for 28BYJ-48 steppers
                    etDefaultSpeed.setText("800.0"); // Good balance of speed and accuracy
                    etMaxSpeed.setText("10.0");      // Smooth acceleration

                    Toast.makeText(this, "Profile deleted - Default values loaded", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}