package com.example.drawbot;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class HomeActivity extends AppCompatActivity {

    private Button bluetoothTerminalButton;
    private TextView welcomeTextView;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Check if user is authenticated
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // User is not authenticated, redirect to login
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            finish();
            return;
        }

        // Initialize views
        bluetoothTerminalButton = findViewById(R.id.bluetooth_terminal_button);
        welcomeTextView = findViewById(R.id.welcome_text_view);
        Button settingsButton = findViewById(R.id.settings_button);
        Button imageToGcodeButton = findViewById(R.id.image_to_gcode_button);

        // Set welcome message
        String email = currentUser.getEmail();
        if (email != null) {
            welcomeTextView.setText("Welcome, " + email);
        } else {
            welcomeTextView.setText("Welcome to DrawBot");
        }

        // Set up button listener - only Bluetooth Terminal now
        bluetoothTerminalButton.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, BluetoothTerminalActivity.class));
        });
        settingsButton.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
        });
        imageToGcodeButton.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, ImageToGcodeActivity.class));
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_logout) {
            // Logout user
            mAuth.signOut();
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
