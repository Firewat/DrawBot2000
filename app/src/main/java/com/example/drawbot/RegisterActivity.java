package com.example.drawbot;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class RegisterActivity extends AppCompatActivity {

    private EditText nameField, emailField, passwordField, confirmPasswordField;
    private Button registerButton;
    private TextView loginLink;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();

        nameField = findViewById(R.id.name);
        emailField = findViewById(R.id.email);
        passwordField = findViewById(R.id.password);
        confirmPasswordField = findViewById(R.id.confirm_password);
        registerButton = findViewById(R.id.register_button);
        loginLink = findViewById(R.id.login_link);
        progressBar = findViewById(R.id.progress_bar);

        registerButton.setOnClickListener(v -> registerUser());
        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
        });
    }

    private void setRegisterButtonEnabled(boolean enabled) {
        registerButton.setEnabled(enabled);
        registerButton.setAlpha(enabled ? 1f : 0.5f);
    }

    private void registerUser() {
        String name = nameField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();
        String confirmPassword = confirmPasswordField.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            nameField.setError("Name ist erforderlich");
            return;
        }

        if (TextUtils.isEmpty(email)) {
            emailField.setError("E-Mail ist erforderlich");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordField.setError("Passwort ist erforderlich");
            return;
        }

        if (password.length() < 6) {
            passwordField.setError("Passwort muss mindestens 6 Zeichen lang sein");
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordField.setError("Passwörter stimmen nicht überein");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        setRegisterButtonEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        if (mAuth.getCurrentUser() != null) {
                            mAuth.getCurrentUser().updateProfile(
                                new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build()
                            ).addOnCompleteListener(profileTask -> {
                                progressBar.setVisibility(View.GONE);
                                setRegisterButtonEnabled(true);
                                if (profileTask.isSuccessful()) {
                                    Toast.makeText(RegisterActivity.this, "Registrierung erfolgreich", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(RegisterActivity.this, HomeActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    Toast.makeText(RegisterActivity.this, "Profil konnte nicht gespeichert werden: " + (profileTask.getException() != null ? profileTask.getException().getMessage() : "Unbekannter Fehler"), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        setRegisterButtonEnabled(true);
                        String errorMsg = "Registrierung fehlgeschlagen.";
                        Exception e = task.getException();
                        if (e != null) {
                            String msg = e.getMessage();
                            if (msg != null) {
                                if (msg.contains("email address is already in use")) {
                                    errorMsg = "E-Mail ist bereits vergeben.";
                                } else if (msg.contains("The email address is badly formatted")) {
                                    errorMsg = "Ungültiges E-Mail-Format.";
                                } else if (msg.contains("WEAK_PASSWORD")) {
                                    errorMsg = "Passwort ist zu schwach.";
                                } else {
                                    errorMsg = msg;
                                }
                            }
                        }
                        Toast.makeText(RegisterActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }
}

