package com.example.drawbot;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class HomeActivity extends AppCompatActivity {

    private Button bluetoothTerminalButton;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Nur noch der Bluetooth Terminal Button
        bluetoothTerminalButton = findViewById(R.id.bluetooth_terminal_button);

        bluetoothTerminalButton.setOnClickListener(v -> {
            if (checkBluetoothPermissions()) {
                startActivity(new Intent(HomeActivity.this, BluetoothTerminalActivity.class));
            } else {
                requestBluetoothPermissions();
            }
        });
    }

    private boolean checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+) - neue Bluetooth-Berechtigungen
            boolean bluetoothConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            boolean bluetoothScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            boolean location = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

            // Debug-Information - zeige genau welche Berechtigung fehlt
            String missingPermissions = "";
            if (!bluetoothConnect) missingPermissions += "BLUETOOTH_CONNECT ";
            if (!bluetoothScan) missingPermissions += "BLUETOOTH_SCAN ";
            if (!location) missingPermissions += "ACCESS_FINE_LOCATION ";

            if (!missingPermissions.isEmpty()) {
                Toast.makeText(this, "Fehlende Berechtigungen: " + missingPermissions, Toast.LENGTH_LONG).show();
                return false;
            }

            return true;
        } else {
            // Android 11 und früher - klassische Bluetooth-Berechtigungen
            boolean bluetooth = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
            boolean bluetoothAdmin = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
            boolean location = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

            return bluetooth && bluetoothAdmin && location;
        }
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ - neue Bluetooth-Berechtigungen
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            // Android 11 und früher - klassische Bluetooth-Berechtigungen
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Bluetooth-Berechtigungen erteilt", Toast.LENGTH_SHORT).show();
                // Terminal öffnen, nachdem Berechtigungen erteilt wurden
                startActivity(new Intent(HomeActivity.this, BluetoothTerminalActivity.class));
            } else {
                Toast.makeText(this, "Bluetooth-Berechtigungen sind erforderlich für das Terminal", Toast.LENGTH_LONG).show();
            }
        }
    }
}
