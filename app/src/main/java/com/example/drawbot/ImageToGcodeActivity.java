package com.example.drawbot;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class ImageToGcodeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_to_gcode);

        Button btnToSvg = findViewById(R.id.btnToSvg);
        Button btnToGcode = findViewById(R.id.btnToGcode);

        btnToSvg.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://picsvg.com/"));
            startActivity(browserIntent);
        });
        btnToGcode.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://sameer.github.io/svg2gcode/"));
            startActivity(browserIntent);
        });
    }
}

