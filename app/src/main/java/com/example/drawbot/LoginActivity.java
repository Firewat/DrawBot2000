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

public class LoginActivity extends AppCompatActivity {

    private EditText emailField, passwordField;
    private Button loginButton;
    private TextView registerLink;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        emailField = findViewById(R.id.email);
        passwordField = findViewById(R.id.password);
        loginButton = findViewById(R.id.login_button);
        registerLink = findViewById(R.id.register_link);
        progressBar = findViewById(R.id.progress_bar);

        loginButton.setOnClickListener(v -> loginUser());
        registerLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void loginUser() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailField.setError("E-Mail ist erforderlich");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordField.setError("Passwort ist erforderlich");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        loginButton.setEnabled(false);
        loginButton.setAlpha(0.5f);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    loginButton.setEnabled(true);
                    loginButton.setAlpha(1f);
                    if (task.isSuccessful()) {
                        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        String errorMsg = "Anmeldung fehlgeschlagen.";
                        Exception e = task.getException();
                        if (e != null) {
                            String msg = e.getMessage();
                            if (msg != null) {
                                if (msg.contains("There is no user record")) {
                                    errorMsg = "Kein Benutzer mit dieser E-Mail gefunden.";
                                } else if (msg.contains("The password is invalid")) {
                                    errorMsg = "Falsches Passwort.";
                                } else if (msg.contains("The email address is badly formatted")) {
                                    errorMsg = "Ungültiges E-Mail-Format.";
                                } else {
                                    errorMsg = msg;
                                }
                            }
                        }
                        Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }
}

