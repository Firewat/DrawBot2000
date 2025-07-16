package com.example.drawbot;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class BluetoothTerminalActivity extends AppCompatActivity {
    private static final String TAG = "BluetoothTerminalActivity";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;
    private static final int REQUEST_ENABLE_BT = 101;
    private static final int REQUEST_SELECT_IMAGE = 102;

    private BluetoothHelper bluetoothHelper;
    private TextView tvTerminal;
    private EditText etCommand;
    private Button btnSend;
    private Button btnConnect;
    private Button btnDisconnect;
    private Button btnClearTerminal;
    private Button btnRefreshDevices;
    private Button btnTestCircle;
    private Button btnHome;
    private Button btnPreviewGCode;
    private Button btnDrawImage;
    private EditText etImageSize;
    private EditText etThreshold;

    // Multi-line G-code variables
    private EditText etGCodeInput;
    private Button btnSendGCode;
    private Button btnPauseGCode;
    private Button btnStopGCode;
    private TextView tvGCodeProgress;
    private TextView tvGCodeDelay;
    private EditText etGCodeDelay;

    // G-code sending control
    private List<String> gCodeQueue = new ArrayList<>();
    private int currentGCodeIndex = 0;
    private boolean isGCodeRunning = false;
    private boolean isGCodePaused = false;
    private Handler gCodeHandler = new Handler(Looper.getMainLooper());
    private Runnable gCodeRunnable;
    private int gCodeDelay = 100; // Default delay in milliseconds

    // SCARA-spezifische UI-Elemente
    private CheckBox cbScaraMode;
    private EditText etArm1Length;
    private EditText etArm2Length;
    private EditText etOffsetX;
    private EditText etOffsetY;

    private TextView tvSelectedImage;
    private TextView tvGCodeStats;
    private ProgressBar progressBarImage;
    private TextView tvProgressText;
    private Spinner spinnerDevices;
    private Spinner spinnerConversionMode;
    private ImageView ivImagePreview; // Vorschau-ImageView hinzufügen

    private ArrayList<BluetoothDevice> deviceList;
    private StringBuilder terminalOutput = new StringBuilder();
    private Uri selectedImageUri = null;
    private ImageToGCodeConverter.GCodeResult lastGCodeResult = null;
    private SimpleImageToGCodeConverter.SimpleResult lastSimpleResult = null; // Neue Variable für SimpleResult

    // Weitere UI-Elemente
    private EditText etLineSpacing;
    private EditText etFeedRate;
    private CheckBox cbOptimizePath;
    private CheckBox cbInvertImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_terminal);

        bluetoothHelper = new BluetoothHelper(this);

        // UI-Elemente initialisieren
        tvTerminal = findViewById(R.id.tvTerminal);
        etCommand = findViewById(R.id.etCommand);
        btnSend = findViewById(R.id.btnSend);
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnClearTerminal = findViewById(R.id.btnClearTerminal);
        btnRefreshDevices = findViewById(R.id.btnRefreshDevices);
        btnTestCircle = findViewById(R.id.btnTestCircle);
        btnHome = findViewById(R.id.btnHome);
        btnPreviewGCode = findViewById(R.id.btnPreviewGCode);
        btnDrawImage = findViewById(R.id.btnDrawImage);
        Button btnSelectSvg = findViewById(R.id.btnSelectSvg);
        btnSelectSvg.setOnClickListener(v -> selectImage());
        // Button zum Auswählen/Hochladen immer aktivieren
        btnPreviewGCode.setEnabled(true);
        btnDrawImage.setEnabled(true);
        etImageSize = findViewById(R.id.etImageSize);
        etThreshold = findViewById(R.id.etThreshold);
        etLineSpacing = findViewById(R.id.etLineSpacing);
        etFeedRate = findViewById(R.id.etFeedRate);
        cbOptimizePath = findViewById(R.id.cbOptimizePath);
        cbInvertImage = findViewById(R.id.cbInvertImage);

        // SCARA-spezifische UI-Elemente
        cbScaraMode = findViewById(R.id.cbScaraMode);
        etArm1Length = findViewById(R.id.etArm1Length);
        etArm2Length = findViewById(R.id.etArm2Length);
        etOffsetX = findViewById(R.id.etOffsetX);
        etOffsetY = findViewById(R.id.etOffsetY);

        tvSelectedImage = findViewById(R.id.tvSelectedImage);
        tvGCodeStats = findViewById(R.id.tvGCodeStats);
        progressBarImage = findViewById(R.id.progressBarImage);
        tvProgressText = findViewById(R.id.tvProgressText);
        spinnerDevices = findViewById(R.id.spinnerDevices);
        spinnerConversionMode = findViewById(R.id.spinnerConversionMode);
        ivImagePreview = findViewById(R.id.ivImagePreview); // Vorschau-ImageView initialisieren
        btnPreviewGCode = findViewById(R.id.btnPreviewGCode); // Vorschau-Button initialisieren

        // Multi-line G-code UI elements
        etGCodeInput = findViewById(R.id.etGCodeInput);
        btnSendGCode = findViewById(R.id.btnSendGCode);
        btnPauseGCode = findViewById(R.id.btnPauseGCode);
        btnStopGCode = findViewById(R.id.btnStopGCode);
        tvGCodeProgress = findViewById(R.id.tvGCodeProgress);
        tvGCodeDelay = findViewById(R.id.tvGCodeDelay);
        etGCodeDelay = findViewById(R.id.etGCodeDelay);

        // Terminal sollte scrollbar sein
        tvTerminal.setMovementMethod(new ScrollingMovementMethod());

        // Prüfen, ob Bluetooth unterstützt wird
        if (!bluetoothHelper.isBluetoothSupported()) {
            Toast.makeText(this, "Bluetooth wird auf diesem Gerät nicht unterstützt", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Berechtigungen prüfen
        checkBluetoothPermissions();

        // Bluetooth-Status prüfen und ggf. aktivieren
        if (!bluetoothHelper.isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        } else {
            loadPairedDevices();
        }

        // Callbacks einrichten
        setupCallbacks();

        // Listeners einrichten
        setupButtonListeners();
    }

    private void setupCallbacks() {
        // Callback für empfangene Nachrichten
        bluetoothHelper.setMessageCallback(message -> {
            // Zeige ALLE empfangenen Daten im Terminal
            terminalOutput.append("< ").append(message.replace("\r", "\\r").replace("\n", "\\n")).append("\n");
            updateTerminalDisplay();
        });

        // Callback für Verbindungsstatus
        bluetoothHelper.setStatusCallback(connected -> {
            btnConnect.setEnabled(!connected);
            btnDisconnect.setEnabled(connected);
            btnSend.setEnabled(connected);
            btnTestCircle.setEnabled(connected);
            btnHome.setEnabled(connected);
            cbOptimizePath.setEnabled(connected);
            cbInvertImage.setEnabled(connected);
            etCommand.setEnabled(connected);
            etImageSize.setEnabled(connected);
            etThreshold.setEnabled(connected);
            etLineSpacing.setEnabled(connected);
            etFeedRate.setEnabled(connected);
            spinnerConversionMode.setEnabled(connected);

            // Enable multi-line G-code input and controls
            etGCodeInput.setEnabled(connected);
            btnSendGCode.setEnabled(connected);
            btnPauseGCode.setEnabled(connected && isGCodeRunning);
            btnStopGCode.setEnabled(connected && isGCodeRunning);
            etGCodeDelay.setEnabled(connected);

            if (connected) {
                Toast.makeText(BluetoothTerminalActivity.this, "Verbunden", Toast.LENGTH_SHORT).show();
                terminalOutput.append("[VERBUNDEN]\n");
            } else {
                Toast.makeText(BluetoothTerminalActivity.this, "Getrennt", Toast.LENGTH_SHORT).show();
                terminalOutput.append("[GETRENNT]\n");
            }
            tvTerminal.setText(terminalOutput.toString());
        });
    }

    private void setupButtonListeners() {
        // Verbinden-Button
        btnConnect.setOnClickListener(v -> {
            int position = spinnerDevices.getSelectedItemPosition();
            if (position >= 0 && position < deviceList.size()) {
                BluetoothDevice device = deviceList.get(position);
                connectToDeviceWithGrbl(device);
            } else {
                Toast.makeText(this, "Bitte wählen Sie ein Gerät aus", Toast.LENGTH_SHORT).show();
            }
        });

        // Trennen-Button
        btnDisconnect.setOnClickListener(v -> {
            bluetoothHelper.disconnect();
        });

        // Senden-Button
        btnSend.setOnClickListener(v -> {
            String command = etCommand.getText().toString().trim();
            if (!command.isEmpty()) {
                sendGrblCommand(command);
                etCommand.setText("");
            }
        });

        // Terminal löschen
        btnClearTerminal.setOnClickListener(v -> {
            terminalOutput.setLength(0);
            tvTerminal.setText("");
        });

        // Geräte aktualisieren
        btnRefreshDevices.setOnClickListener(v -> {
            loadPairedDevices();
        });

        // Test Kreis zeichnen
        btnTestCircle.setOnClickListener(v -> {
            drawTestCircle();
        });

        // Home Position fahren
        btnHome.setOnClickListener(v -> {
            goHome();
        });


        // Diagnose-Button hinzufügen (temporär für Debugging)
        btnTestCircle.setOnLongClickListener(v -> {
            if (cbScaraMode.isChecked()) {
                runScaraCalibration();
            } else {
                testConnection();
            }
            return true;
        });

        // Konvertierungsmodus-Spinner initialisieren
        setupConversionModeSpinner();

        // Bild zeichnen Button
        btnDrawImage.setOnClickListener(v -> drawImageAdvanced());

        // G-Code senden Button
        btnSendGCode.setOnClickListener(v -> sendGCodeCommands());
        btnPauseGCode.setOnClickListener(v -> toggleGCodePause());
        btnStopGCode.setOnClickListener(v -> stopGCodeCommands());

        // SCARA Calibration Button
        btnTestCircle.setOnLongClickListener(v -> {
            if (cbScaraMode.isChecked()) {
                runScaraCalibration();
            } else {
                testConnection();
            }
            return true;
        });

        // Add SCARA line test button functionality
        btnHome.setOnLongClickListener(v -> {
            if (cbScaraMode.isChecked()) {
                testScaraLineDrawing();
            }
            return true;
        });
    }

    private void setupConversionModeSpinner() {
        ArrayList<String> modes = new ArrayList<>();
        for (ImageToGCodeConverter.ConversionMode mode : ImageToGCodeConverter.ConversionMode.values()) {
            modes.add(mode.name().replace("_", " "));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, modes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerConversionMode.setAdapter(adapter);
    }

    /**
     * Erstellt ConversionSettings aus UI-Eingaben
     */
    private ImageToGCodeConverter.ConversionSettings createConversionSettings() {
        ImageToGCodeConverter.ConversionSettings settings = new ImageToGCodeConverter.ConversionSettings();

        // Modus aus Spinner
        int selectedModeIndex = spinnerConversionMode.getSelectedItemPosition();
        if (selectedModeIndex >= 0 && selectedModeIndex < ImageToGCodeConverter.ConversionMode.values().length) {
            settings.mode = ImageToGCodeConverter.ConversionMode.values()[selectedModeIndex];
        }

        // Werte aus UI-Elementen
        try {
            String sizeText = etImageSize.getText().toString().trim();
            settings.targetWidthMM = sizeText.isEmpty() ? 50 : Integer.parseInt(sizeText);
            settings.targetHeightMM = settings.targetWidthMM; // Quadratisch

            String thresholdText = etThreshold.getText().toString().trim();
            settings.threshold = thresholdText.isEmpty() ? 128 : Integer.parseInt(thresholdText);

            String spacingText = etLineSpacing.getText().toString().trim();
            settings.lineSpacing = spacingText.isEmpty() ? 0.5f : Float.parseFloat(spacingText);

            String feedText = etFeedRate.getText().toString().trim();
            settings.feedRate = feedText.isEmpty() ? 800 : Float.parseFloat(feedText);

            settings.optimizePath = cbOptimizePath.isChecked();
            settings.invertImage = cbInvertImage.isChecked();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Ungültige Zahlenwerte in den Einstellungen", Toast.LENGTH_SHORT).show();
        }

        return settings;
    }

    /**
     * Zeigt eine G-Code-Vorschau ohne zu senden
     */
    private void previewGCode() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "Bitte zuerst ein Bild auswählen!", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageToGCodeConverter.ConversionSettings settings = createConversionSettings();

        // Validierung
        if (settings.targetWidthMM < 10 || settings.targetWidthMM > 200) {
            Toast.makeText(this, "Größe muss zwischen 10 und 200 mm liegen!", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                runOnUiThread(() -> {
                    tvGCodeStats.setVisibility(View.VISIBLE);
                    tvGCodeStats.setText("Generiere G-Code...");
                });

                // Prüfe ob es sich um eine SVG-Datei handelt
                String fileName = selectedImageUri.getLastPathSegment();
                boolean isSvg = fileName != null && (fileName.toLowerCase().endsWith(".svg") ||
                                fileName.toLowerCase().contains("svg"));

                if (isSvg) {
                    // SVG-CONVERTER verwenden
                    terminalOutput.append("[SVG-DATEI ERKANNT - VERWENDE SVG-CONVERTER]\n");
                    updateTerminalDisplay();

                    SimpleSvgToGCodeConverter.Settings svgSettings = new SimpleSvgToGCodeConverter.Settings();
                    svgSettings.drawingWidthMM = settings.targetWidthMM;
                    svgSettings.drawingHeightMM = settings.targetHeightMM;
                    svgSettings.feedRateMM_Min = settings.feedRate;
                    svgSettings.penUpZ = settings.penUpZ;
                    svgSettings.penDownZ = settings.penDownZ;

                    // SCARA-spezifische Einstellungen aus UI übernehmen
                    if (cbScaraMode != null && cbScaraMode.isChecked()) {
                        svgSettings.isScaraMode = true;
                        try {
                            svgSettings.arm1Length = Float.parseFloat(etArm1Length.getText().toString().trim());
                            svgSettings.arm2Length = Float.parseFloat(etArm2Length.getText().toString().trim());
                            svgSettings.offsetX = Float.parseFloat(etOffsetX.getText().toString().trim());
                            svgSettings.offsetY = Float.parseFloat(etOffsetY.getText().toString().trim());
                        } catch (NumberFormatException e) {
                            // Fallback-Werte verwenden
                            svgSettings.arm1Length = 100.0f;
                            svgSettings.arm2Length = 100.0f;
                            svgSettings.offsetX = 0.0f;
                            svgSettings.offsetY = 100.0f;
                        }
                    } else {
                        svgSettings.isScaraMode = false;
                    }

                    SimpleSvgToGCodeConverter.Result svgResult =
                            SimpleSvgToGCodeConverter.convertSvgToGCode(this, selectedImageUri, svgSettings);

                    // Konvertiere zu SimpleResult für Kompatibilität
                    SimpleImageToGCodeConverter.SimpleResult result = new SimpleImageToGCodeConverter.SimpleResult(svgResult.gCodeLines);
                    result.totalCommands = svgResult.totalCommands;
                    result.estimatedTime = 0; // SimpleSvgToGCodeConverter hat keine Zeitschätzung
                    result.summary = svgResult.summary;

                    lastSimpleResult = result;

                    runOnUiThread(() -> {
                        if (!svgResult.success || svgResult.gCodeLines.isEmpty() || svgResult.gCodeLines.get(0).startsWith("; FEHLER")) {
                            tvGCodeStats.setText("❌ Fehler bei der SVG-Konvertierung: " + svgResult.errorMessage);
                            tvGCodeStats.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
                            Toast.makeText(BluetoothTerminalActivity.this, "Fehler bei der SVG-Konvertierung!", Toast.LENGTH_SHORT).show();

                            // Zeige Debug-Info im Terminal
                            terminalOutput.append("[SVG-KONVERTIERUNGSFEHLER]\n");
                            terminalOutput.append("[GRUND: " + svgResult.errorMessage + "]\n");
                            if (!svgResult.gCodeLines.isEmpty()) {
                                terminalOutput.append("[DEBUG-INFO:]\n");
                                for (int i = 0; i < Math.min(3, svgResult.gCodeLines.size()); i++) {
                                    terminalOutput.append("  " + svgResult.gCodeLines.get(i) + "\n");
                                }
                            }
                            updateTerminalDisplay();
                        } else {
                            String stats = String.format(
                                    "✅ SVG-G-Code erstellt:\n" +
                                    "Befehle: %d\n" +
                                    "Größe: %d x %d mm\n" +
                                    "Distanz: %.1f mm\n" +
                                    "Geschätzte Zeit: %.1f Sekunden",
                                    result.totalCommands,
                                    (int)svgSettings.drawingWidthMM,
                                    (int)svgSettings.drawingHeightMM,
                                    svgResult.totalDistance,
                                    result.estimatedTime
                            );
                            tvGCodeStats.setText(stats);
                            tvGCodeStats.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));


                            // Zeige G-Code im Terminal
                            terminalOutput.append("[SVG-G-CODE ERFOLGREICH GENERIERT]\n");
                            terminalOutput.append(String.format("[BEFEHLE: %d]\n", result.totalCommands));
                            terminalOutput.append(String.format("[GRÖßE: %dx%d mm, DISTANZ: %.1fmm]\n",
                                (int)svgSettings.drawingWidthMM, (int)svgSettings.drawingHeightMM, svgResult.totalDistance));

                            // Zeige erste 5 G-Code-Zeilen als Beispiel
                            terminalOutput.append("[ERSTE 5 BEFEHLE:]\n");
                            for (int i = 0; i < Math.min(5, result.gCodeCommands.size()); i++) {
                                terminalOutput.append("  ").append(result.gCodeCommands.get(i)).append("\n");
                            }
                            if (result.gCodeCommands.size() > 5) {
                                terminalOutput.append("  ... (").append(result.gCodeCommands.size() - 5).append(" weitere)\n");
                            }
                            updateTerminalDisplay();
                        }
                    });

                } else {
                    // BITMAP-CONVERTER verwenden (wie vorher)
                    SimpleImageToGCodeConverter.SimpleSettings simpleSettings = new SimpleImageToGCodeConverter.SimpleSettings();
                    simpleSettings.targetWidthMM = settings.targetWidthMM;
                    simpleSettings.targetHeightMM = settings.targetHeightMM;
                    simpleSettings.threshold = settings.threshold;
                    simpleSettings.lineSpacing = settings.lineSpacing;
                    simpleSettings.feedRate = settings.feedRate;
                    simpleSettings.travelSpeed = settings.travelSpeed;
                    simpleSettings.penUpZ = settings.penUpZ;
                    simpleSettings.penDownZ = settings.penDownZ;
                    simpleSettings.invertImage = settings.invertImage;

                    SimpleImageToGCodeConverter.SimpleResult result =
                            SimpleImageToGCodeConverter.convertImageToGCode(this, selectedImageUri, simpleSettings);

                    lastGCodeResult = null;
                    lastSimpleResult = result;

                    runOnUiThread(() -> {
                        if (result.gCodeCommands.isEmpty() || result.gCodeCommands.get(0).startsWith("; FEHLER")) {
                            tvGCodeStats.setText("❌ Fehler bei der Konvertierung");
                            tvGCodeStats.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
                            Toast.makeText(BluetoothTerminalActivity.this, "Fehler bei der G-Code-Generierung!", Toast.LENGTH_SHORT).show();
                        } else {
                            String stats = String.format(
                                    "✅ G-Code erstellt:\n" +
                                    "Befehle: %d\n" +
                                    "Größe: %d x %d mm\n" +
                                    "Linienabstand: %.1f mm\n" +
                                    "Schwellwert: %d\n" +
                                    "Geschätzte Zeit: %.1f Sekunden",
                                    result.totalCommands,
                                    simpleSettings.targetWidthMM,
                                    simpleSettings.targetHeightMM,
                                    simpleSettings.lineSpacing,
                                    simpleSettings.threshold,
                                    result.estimatedTime
                            );
                            tvGCodeStats.setText(stats);
                            tvGCodeStats.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));


                            terminalOutput.append("[G-CODE ERFOLGREICH GENERIERT]\n");
                            terminalOutput.append(String.format("[BEFEHLE: %d]\n", result.totalCommands));
                            terminalOutput.append(String.format("[GRÖßE: %dx%d mm]\n", simpleSettings.targetWidthMM, simpleSettings.targetHeightMM));

                            terminalOutput.append("[ERSTE 5 BEFEHLE:]\n");
                            for (int i = 0; i < Math.min(5, result.gCodeCommands.size()); i++) {
                                terminalOutput.append("  ").append(result.gCodeCommands.get(i)).append("\n");
                            }
                            if (result.gCodeCommands.size() > 5) {
                                terminalOutput.append("  ... (").append(result.gCodeCommands.size() - 5).append(" weitere)\n");
                            }
                            updateTerminalDisplay();
                        }
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvGCodeStats.setText("❌ Fehler: " + e.getMessage());
                    tvGCodeStats.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
                    Toast.makeText(BluetoothTerminalActivity.this, "Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    terminalOutput.append("[FEHLER bei G-Code-Generierung: " + e.getMessage() + "]\n");
                    updateTerminalDisplay();
                });
            }
        }).start();
    }

    /**
     * Erweiterte Bildzeichnung mit allen neuen Features
     */
    private void drawImageAdvanced() {
        if (!bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Nicht verbunden!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUri == null) {
            Toast.makeText(this, "Bitte zuerst ein Bild auswählen!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Verwende bereits generierten G-Code wenn verfügbar
        if (lastSimpleResult != null) {
            sendGCodeCommandsWithProgress(lastSimpleResult.gCodeCommands);
            return;
        }

        // Ansonsten generiere neuen G-Code mit dem einfachen Converter
        ImageToGCodeConverter.ConversionSettings settings = createConversionSettings();

        // Validierung
        if (settings.targetWidthMM < 10 || settings.targetWidthMM > 200) {
            Toast.makeText(this, "Größe muss zwischen 10 und 200 mm liegen!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Progress Bar anzeigen
        runOnUiThread(() -> {
            progressBarImage.setVisibility(View.VISIBLE);
            tvProgressText.setVisibility(View.VISIBLE);
            progressBarImage.setProgress(0);
            tvProgressText.setText("Starte Bildkonvertierung...");
        });

        terminalOutput.append("[STARTE BILDKONVERTIERUNG]\n");
        terminalOutput.append(String.format("[EINSTELLUNGEN: %dmm, Schwellwert: %d, Linienabstand: %.1fmm]\n",
                settings.targetWidthMM, settings.threshold, settings.lineSpacing));
        updateTerminalDisplay();

        // Konvertierung in separatem Thread
        new Thread(() -> {
            try {
                // Phase 1: Bildanalyse (25%)
                runOnUiThread(() -> {
                    progressBarImage.setProgress(25);
                    tvProgressText.setText("Analysiere Bild...");
                });

                // Prüfe ob es sich um eine SVG-Datei handelt
                String fileName = selectedImageUri.getLastPathSegment();
                boolean isSvg = fileName != null && (fileName.toLowerCase().endsWith(".svg") || fileName.toLowerCase().contains("svg"));

                if (isSvg) {
                    // SVG-Konvertierung
                    SimpleSvgToGCodeConverter.Settings svgSettings = new SimpleSvgToGCodeConverter.Settings();
                    svgSettings.drawingWidthMM = settings.targetWidthMM;
                    svgSettings.drawingHeightMM = settings.targetHeightMM;
                    svgSettings.feedRateMM_Min = settings.feedRate;
                    svgSettings.penUpZ = settings.penUpZ;
                    svgSettings.penDownZ = settings.penDownZ;

                    // SCARA-spezifische Einstellungen aus UI übernehmen
                    if (cbScaraMode != null && cbScaraMode.isChecked()) {
                        svgSettings.isScaraMode = true;
                        try {
                            svgSettings.arm1Length = Float.parseFloat(etArm1Length.getText().toString().trim());
                            svgSettings.arm2Length = Float.parseFloat(etArm2Length.getText().toString().trim());
                            svgSettings.offsetX = Float.parseFloat(etOffsetX.getText().toString().trim());
                            svgSettings.offsetY = Float.parseFloat(etOffsetY.getText().toString().trim());
                        } catch (NumberFormatException e) {
                            // Fallback-Werte verwenden
                            svgSettings.arm1Length = 100.0f;
                            svgSettings.arm2Length = 100.0f;
                            svgSettings.offsetX = 0.0f;
                            svgSettings.offsetY = 100.0f;
                        }
                    } else {
                        svgSettings.isScaraMode = false;
                    }

                    SimpleSvgToGCodeConverter.Result svgResult =
                        SimpleSvgToGCodeConverter.convertSvgToGCode(this, selectedImageUri, svgSettings);

                    SimpleImageToGCodeConverter.SimpleResult result = new SimpleImageToGCodeConverter.SimpleResult(svgResult.gCodeLines);
                    result.totalCommands = svgResult.totalCommands;
                    result.estimatedTime = 0; // SimpleSvgToGCodeConverter hat keine Zeitschätzung
                    result.summary = svgResult.summary;

                    lastSimpleResult = result;

                    if (result.gCodeCommands.isEmpty() || result.gCodeCommands.get(0).startsWith("; FEHLER")) {
                        runOnUiThread(() -> {
                            progressBarImage.setVisibility(View.GONE);
                            tvProgressText.setVisibility(View.GONE);
                            terminalOutput.append("[FEHLER BEI DER SVG-KONVERTIERUNG]\n");
                            updateTerminalDisplay();
                            Toast.makeText(BluetoothTerminalActivity.this, "Fehler bei der SVG-Konvertierung!", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    // Phase 3: Bereit zum Senden (75%)
                    runOnUiThread(() -> {
                        progressBarImage.setProgress(75);
                        tvProgressText.setText(String.format("Bereit: %d Befehle generiert", result.totalCommands));
                        terminalOutput.append("[SVG-KONVERTIERUNG ABGESCHLOSSEN]\n");
                        terminalOutput.append(String.format("SVG-G-Code: %d Befehle generiert\n", result.totalCommands));
                        terminalOutput.append("[STARTE ÜBERTRAGUNG...]\n");
                        updateTerminalDisplay();
                    });

                    // Phase 4: G-Code senden (75-100%)
                    sendGCodeCommandsWithProgress(result.gCodeCommands);
                    return;
                }

                // Phase 2: G-Code Konvertierung (50%)
                runOnUiThread(() -> {
                    progressBarImage.setProgress(50);
                    tvProgressText.setText("Generiere G-Code...");
                });

                // Verwende den einfachen Converter
                SimpleImageToGCodeConverter.SimpleSettings simpleSettings = new SimpleImageToGCodeConverter.SimpleSettings();
                simpleSettings.targetWidthMM = settings.targetWidthMM;
                simpleSettings.targetHeightMM = settings.targetHeightMM;
                simpleSettings.threshold = settings.threshold;
                simpleSettings.lineSpacing = settings.lineSpacing;
                simpleSettings.feedRate = settings.feedRate;
                simpleSettings.travelSpeed = settings.travelSpeed;
                simpleSettings.penUpZ = settings.penUpZ;
                simpleSettings.penDownZ = settings.penDownZ;
                simpleSettings.invertImage = settings.invertImage;

                SimpleImageToGCodeConverter.SimpleResult result =
                        SimpleImageToGCodeConverter.convertImageToGCode(this, selectedImageUri, simpleSettings);

                if (result.gCodeCommands.isEmpty() || result.gCodeCommands.get(0).startsWith("; FEHLER")) {
                    runOnUiThread(() -> {
                        progressBarImage.setVisibility(View.GONE);
                        tvProgressText.setVisibility(View.GONE);
                        terminalOutput.append("[FEHLER BEI DER KONVERTIERUNG]\n");
                        updateTerminalDisplay();
                        Toast.makeText(BluetoothTerminalActivity.this, "Fehler bei der Bildkonvertierung!", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                lastSimpleResult = result;

                // Phase 3: Bereit zum Senden (75%)
                runOnUiThread(() -> {
                    progressBarImage.setProgress(75);
                    tvProgressText.setText(String.format("Bereit: %d Befehle generiert", result.totalCommands));
                    terminalOutput.append("[KONVERTIERUNG ABGESCHLOSSEN]\n");
                    terminalOutput.append(String.format("G-Code: %d Befehle generiert\n", result.totalCommands));
                    terminalOutput.append("[STARTE ÜBERTRAGUNG...]\n");
                    updateTerminalDisplay();
                });

                // Phase 4: G-Code senden (75-100%)
                sendGCodeCommandsWithProgress(result.gCodeCommands);

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBarImage.setVisibility(View.GONE);
                    tvProgressText.setVisibility(View.GONE);
                    terminalOutput.append("[FEHLER: ").append(e.getMessage()).append("]\n");
                    updateTerminalDisplay();
                    Toast.makeText(BluetoothTerminalActivity.this, "Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * Führt die SCARA-Kalibrierung durch
     */
    private void runScaraCalibration() {
        if (!bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Nicht verbunden!", Toast.LENGTH_SHORT).show();
            return;
        }

        terminalOutput.append("[STARTE SCARA-KALIBRIERUNG]\n");
        updateTerminalDisplay();

        // Create SCARA configuration from UI
        ScaraKinematics.ScaraConfig config = createScaraConfig();

        // Generate calibration sequence
        List<String> calibrationCommands = ScaraKinematics.generateCalibrationSequence(config);

        terminalOutput.append(String.format("[SCARA-KONFIGURATION: Arm1=%.1fmm, Arm2=%.1fmm, Offset=(%.1f,%.1f)]\n",
                config.arm1Length, config.arm2Length, config.offsetX, config.offsetY));
        updateTerminalDisplay();

        // Send calibration commands
        sendCommandSequence(calibrationCommands, "SCARA-Kalibrierung");
    }

    /**
     * Testet das Zeichnen einer glatten Linie mit SCARA-Kinematics
     */
    private void testScaraLineDrawing() {
        if (!bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Nicht verbunden!", Toast.LENGTH_SHORT).show();
            return;
        }

        terminalOutput.append("[STARTE SCARA SMOOTH LINE TEST]\n");
        updateTerminalDisplay();

        // Create SCARA configuration from UI
        ScaraKinematics.ScaraConfig config = createScaraConfig();

        List<String> testCommands = new ArrayList<>();
        testCommands.add("; SCARA Smooth Line Drawing Test");
        testCommands.add("$X ; Unlock");
        testCommands.add("G21 ; mm");
        testCommands.add("G90 ; Absolute positioning");
        testCommands.add("G0 Z" + config.penUpZ + " ; Pen up");

        // Test different line types
        ScaraKinematics.Point[] testLines = {
            // Horizontal line
            new ScaraKinematics.Point(config.offsetX + 50, config.offsetY + 50, config.penUpZ),
            new ScaraKinematics.Point(config.offsetX + 50, config.offsetY + 50, config.penDownZ),
            new ScaraKinematics.Point(config.offsetX + 100, config.offsetY + 50, config.penDownZ),
            new ScaraKinematics.Point(config.offsetX + 100, config.offsetY + 50, config.penUpZ),

            // Vertical line
            new ScaraKinematics.Point(config.offsetX + 75, config.offsetY + 30, config.penUpZ),
            new ScaraKinematics.Point(config.offsetX + 75, config.offsetY + 30, config.penDownZ),
            new ScaraKinematics.Point(config.offsetX + 75, config.offsetY + 80, config.penDownZ),
            new ScaraKinematics.Point(config.offsetX + 75, config.offsetY + 80, config.penUpZ),

            // Diagonal line
            new ScaraKinematics.Point(config.offsetX + 50, config.offsetY + 30, config.penUpZ),
            new ScaraKinematics.Point(config.offsetX + 50, config.offsetY + 30, config.penDownZ),
            new ScaraKinematics.Point(config.offsetX + 100, config.offsetY + 80, config.penDownZ),
            new ScaraKinematics.Point(config.offsetX + 100, config.offsetY + 80, config.penUpZ)
        };

        // Process each line segment
        for (int i = 0; i < testLines.length - 1; i++) {
            ScaraKinematics.Point start = testLines[i];
            ScaraKinematics.Point end = testLines[i + 1];

            if (start.z != end.z) {
                // Z-axis movement only
                ScaraKinematics.JointAngles angles = ScaraKinematics.inverseKinematics(start.x, start.y, config);
                if (angles.valid) {
                    testCommands.add(String.format(java.util.Locale.US,
                        "G1 A%.3f B%.3f Z%.1f F500", angles.angle1, angles.angle2, end.z));
                }
            } else {
                // Smooth line interpolation
                List<String> lineCommands = ScaraKinematics.interpolateLine(start, end, config, 800, 15);
                testCommands.addAll(lineCommands);
            }
        }

        // Return to home
        testCommands.add("G0 A0 B0 Z" + config.penUpZ + " ; Return to home");

        // Send test commands
        sendCommandSequence(testCommands, "SCARA Smooth Line Test");
    }

    /**
     * Erstellt SCARA-Konfiguration aus UI-Eingaben
     */
    private ScaraKinematics.ScaraConfig createScaraConfig() {
        ScaraKinematics.ScaraConfig config = new ScaraKinematics.ScaraConfig();

        try {
            config.arm1Length = Float.parseFloat(etArm1Length.getText().toString().trim());
            config.arm2Length = Float.parseFloat(etArm2Length.getText().toString().trim());
            config.offsetX = Float.parseFloat(etOffsetX.getText().toString().trim());
            config.offsetY = Float.parseFloat(etOffsetY.getText().toString().trim());
        } catch (NumberFormatException e) {
            // Use default values if parsing fails
            terminalOutput.append("[WARNUNG: Ungültige SCARA-Parameter, verwende Standardwerte]\n");
            updateTerminalDisplay();
        }

        return config;
    }

    /**
     * Sendet eine Befehlssequenz mit Fortschrittsanzeige
     */
    private void sendCommandSequence(List<String> commands, String sequenceName) {
        new Thread(() -> {
            try {
                int totalCommands = commands.size();
                int sentCommands = 0;

                runOnUiThread(() -> {
                    terminalOutput.append(String.format("[STARTE %s - %d Befehle]\n", sequenceName, totalCommands));
                    updateTerminalDisplay();
                });

                for (String command : commands) {
                    if (command.startsWith(";")) {
                        // Comment - just show in terminal
                        runOnUiThread(() -> {
                            terminalOutput.append(command).append("\n");
                            updateTerminalDisplay();
                        });
                        continue;
                    }

                    final String currentCommand = command;
                    final int currentNum = ++sentCommands;

                    runOnUiThread(() -> {
                        terminalOutput.append(String.format("> %s [%d/%d]\n",
                            currentCommand, currentNum, totalCommands));
                        updateTerminalDisplay();
                    });

                    bluetoothHelper.sendData(command + "\n");

                    // Different delays for different command types
                    if (command.startsWith("G0") && command.contains("Z")) {
                        Thread.sleep(800);  // Z movements need more time
                    } else if (command.startsWith("G1") && command.contains("A") && command.contains("B")) {
                        Thread.sleep(150);  // SCARA joint movements
                    } else if (command.startsWith("G0") || command.startsWith("G1")) {
                        Thread.sleep(400);  // Regular movements
                    } else {
                        Thread.sleep(200);  // Other commands
                    }
                }

                runOnUiThread(() -> {
                    terminalOutput.append(String.format("[%s ABGESCHLOSSEN!]\n", sequenceName));
                    updateTerminalDisplay();
                    Toast.makeText(BluetoothTerminalActivity.this, sequenceName + " abgeschlossen!",
                        Toast.LENGTH_SHORT).show();
                });

            } catch (InterruptedException e) {
                runOnUiThread(() -> {
                    terminalOutput.append(String.format("[FEHLER: %s unterbrochen]\n", sequenceName));
                    updateTerminalDisplay();
                });
            }
        }).start();
    }

    private void checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+) - neue Bluetooth-Berechtigungen
            String[] permissions = {
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            };

            ArrayList<String> permissionsToRequest = new ArrayList<>();

            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }

            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsToRequest.toArray(new String[0]),
                        REQUEST_BLUETOOTH_PERMISSIONS);
            }
        } else {
            // Android 11 und früher - klassische Bluetooth-Berechtigungen
            String[] permissions = {
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_ADMIN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            };

            ArrayList<String> permissionsToRequest = new ArrayList<>();

            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }

            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsToRequest.toArray(new String[0]),
                        REQUEST_BLUETOOTH_PERMISSIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
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
                loadPairedDevices();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Berechtigungen erforderlich")
                        .setMessage("Die App benötigt Bluetooth-Berechtigungen, um zu funktionieren.")
                        .setPositiveButton("OK", (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                loadPairedDevices();
            } else {
                Toast.makeText(this, "Bluetooth muss aktiviert werden", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == REQUEST_SELECT_IMAGE) {
            if (resultCode == RESULT_OK && data != null) {
                selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    String filePath = selectedImageUri.getPath();
                    tvSelectedImage.setText("Ausgewähltes Bild: " + filePath);
                    btnPreviewGCode.setEnabled(true);

                    // Zeige Bildvorschau
                    ivImagePreview.setImageURI(selectedImageUri);
                    ivImagePreview.setVisibility(View.VISIBLE);
                } else {
                    tvSelectedImage.setText("Kein Bild ausgewählt");
                    btnPreviewGCode.setEnabled(false);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        bluetoothHelper.disconnect();
        super.onDestroy();
    }

    /**
     * Öffnet die Bildauswahl
     */
    private void selectImage() {
        // Zeige Dialog für Dateiauswahl
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Datei auswählen")
               .setItems(new String[]{"Aus Speicher wählen", "Test-SVG verwenden"}, (dialog, which) -> {
                   if (which == 0) {
                       // Normale Dateiauswahl
                       Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                       intent.setType("*/*");
                       intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "image/svg+xml", "text/plain"});
                       intent.addCategory(Intent.CATEGORY_OPENABLE);
                       startActivityForResult(Intent.createChooser(intent, "Bild oder SVG auswählen"), REQUEST_SELECT_IMAGE);
                   } else {
                       // Test-SVG aus Assets verwenden
                       useTestSvgFromAssets();
                   }
               })
               .show();
    }

    private void useTestSvgFromAssets() {
        try {
            // Erstelle Test-SVG-Inhalt direkt als String
            String testSvgContent =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<svg width=\"100\" height=\"100\" viewBox=\"0 0 100 100\" xmlns=\"http://www.w3.org/2000/svg\">\n" +
                "  <!-- Test-Rechteck -->\n" +
                "  <rect x=\"10\" y=\"10\" width=\"30\" height=\"20\" fill=\"none\" stroke=\"black\"/>\n" +
                "  \n" +
                "  <!-- Test-Kreis -->\n" +
                "  <circle cx=\"70\" cy=\"25\" r=\"15\" fill=\"none\" stroke=\"black\"/>\n" +
                "  \n" +
                "  <!-- Test-Linie -->\n" +
                "  <line x1=\"10\" y1=\"50\" x2=\"90\" y2=\"50\" stroke=\"black\"/>\n" +
                "  \n" +
                "  <!-- Test-Pfad -->\n" +
                "  <path d=\"M 20 70 L 40 70 L 30 85 Z\" fill=\"none\" stroke=\"black\"/>\n" +
                "</svg>";

            // Erstelle temporäre Datei im app-eigenen Verzeichnis
            java.io.File tempFile = new java.io.File(getCacheDir(), "embedded_test.svg");
            java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile);

            // Schreibe SVG-Inhalt in Datei
            outputStream.write(testSvgContent.getBytes("UTF-8"));
            outputStream.close();

            // URI für die temporäre Datei setzen
            selectedImageUri = Uri.fromFile(tempFile);

            tvSelectedImage.setText("Eingebaute Test-SVG geladen");
            btnPreviewGCode.setEnabled(true);

            // Zeige SVG-Inhalt im Terminal für Debug
            terminalOutput.append("[TEST-SVG GELADEN]\n");
            terminalOutput.append("[INHALT: Rechteck, Kreis, Linie, Dreieck]\n");
            updateTerminalDisplay();

            Toast.makeText(this, "Test-SVG erfolgreich erstellt und geladen!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Fehler beim Erstellen der Test-SVG: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Fehler beim Erstellen der Test-SVG", e);
        }
    }

    private void loadPairedDevices() {
        Toast.makeText(this, "Lade gekoppelte Geräte...", Toast.LENGTH_SHORT).show();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "BLUETOOTH_CONNECT Berechtigung fehlt", Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "BLUETOOTH Berechtigung fehlt", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Set<BluetoothDevice> pairedDevices = bluetoothHelper.getPairedDevices();

        if (pairedDevices == null) {
            Toast.makeText(this, "Bluetooth nicht verfügbar", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "Keine gekoppelten Geräte gefunden. Bitte koppeln Sie zuerst ein HC-Modul in den Bluetooth-Einstellungen.", Toast.LENGTH_LONG).show();
            deviceList = new ArrayList<>();
            ArrayList<String> deviceNames = new ArrayList<>();
            deviceNames.add("Keine Geräte verfügbar");

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, deviceNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerDevices.setAdapter(adapter);
            return;
        }

        deviceList = new ArrayList<>(pairedDevices);
        ArrayList<String> deviceNames = new ArrayList<>();

        for (BluetoothDevice device : deviceList) {
            String deviceName = device.getName();
            String deviceAddress = device.getAddress();
            if (deviceName == null) deviceName = "Unbekanntes Gerät";
            deviceNames.add(deviceName + " - " + deviceAddress);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, deviceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDevices.setAdapter(adapter);

        Toast.makeText(this, "Gefunden: " + deviceList.size() + " Gerät(e)", Toast.LENGTH_SHORT).show();
    }

    private void connectToDeviceWithGrbl(BluetoothDevice device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                terminalOutput.append("[FEHLER: BLUETOOTH_CONNECT Berechtigung fehlt]\n");
                updateTerminalDisplay();
                return;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                terminalOutput.append("[FEHLER: BLUETOOTH Berechtigung fehlt]\n");
                updateTerminalDisplay();
                return;
            }
        }

        if (device.getName() != null) {
            terminalOutput.append("[VERBINDE MIT DRAWBOT ").append(device.getName()).append("...]\n");
        } else {
            terminalOutput.append("[VERBINDE MIT DRAWBOT ").append(device.getAddress()).append("...]\n");
        }
        updateTerminalDisplay();

        bluetoothHelper.connectToDevice(device, new BluetoothHelper.ConnectionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    terminalOutput.append("[VERBINDUNG ERFOLGREICH]\n");
                    bluetoothHelper.sendData("$X\n");
                    terminalOutput.append("[DRAWBOT BEREIT]\n");
                    updateTerminalDisplay();
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    terminalOutput.append("[VERBINDUNGSFEHLER: ").append(error).append("]\n");
                    updateTerminalDisplay();
                });
            }
        });
    }

    private void sendGrblCommand(String command) {
        terminalOutput.append("> ").append(command).append("\n");
        updateTerminalDisplay();

        if (command.equalsIgnoreCase("home")) {
            bluetoothHelper.sendData("$X\n");
            bluetoothHelper.sendData("G28\n");
        } else {
            bluetoothHelper.sendData(command + "\n");
        }
    }

    private void drawTestCircle() {
        if (!bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Nicht verbunden!", Toast.LENGTH_SHORT).show();
            return;
        }

        terminalOutput.append("[STARTE TEST-KREIS (3cm Durchmesser)]\n");
        updateTerminalDisplay();

        String[] circleCommands = {
                "$X", "G21", "G90", "G1 F1000",
                "G0 X35 Y20", "G0 Z-1", "G02 X35 Y20 I-15 J0 F500",
                "G0 Z5", "G0 X0 Y0"
        };

        new Thread(() -> {
            try {
                for (int i = 0; i < circleCommands.length; i++) {
                    final String command = circleCommands[i];
                    final int commandNum = i + 1;

                    runOnUiThread(() -> {
                        terminalOutput.append("> ").append(command).append(" [").append(commandNum).append("/").append(circleCommands.length).append("]\n");
                        updateTerminalDisplay();
                    });

                    bluetoothHelper.sendData(command + "\n");
                    Thread.sleep(300);
                }

                runOnUiThread(() -> {
                    terminalOutput.append("[TEST-KREIS ABGESCHLOSSEN]\n");
                    updateTerminalDisplay();
                    Toast.makeText(BluetoothTerminalActivity.this, "Test-Kreis gesendet!", Toast.LENGTH_SHORT).show();
                });

            } catch (InterruptedException e) {
                runOnUiThread(() -> {
                    terminalOutput.append("[FEHLER: Test-Kreis unterbrochen]\n");
                    updateTerminalDisplay();
                });
            }
        }).start();
    }

    private void goHome() {
        if (!bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Nicht verbunden!", Toast.LENGTH_SHORT).show();
            return;
        }

        terminalOutput.append("[FAHRE ZUR HOME-POSITION]\n");
        updateTerminalDisplay();

        String[] homeCommands = {"$X", "G21", "G90", "G0 Z5", "G0 X0 Y0"};

        new Thread(() -> {
            try {
                for (String command : homeCommands) {
                    runOnUiThread(() -> {
                        terminalOutput.append("> ").append(command).append("\n");
                        updateTerminalDisplay();
                    });

                    bluetoothHelper.sendData(command + "\n");
                    Thread.sleep(300);
                }

                runOnUiThread(() -> {
                    terminalOutput.append("[HOME-POSITION ERREICHT]\n");
                    updateTerminalDisplay();
                    Toast.makeText(BluetoothTerminalActivity.this, "Home-Position angefahren!", Toast.LENGTH_SHORT).show();
                });

            } catch (InterruptedException e) {
                runOnUiThread(() -> {
                    terminalOutput.append("[FEHLER: Home-Bewegung unterbrochen]\n");
                    updateTerminalDisplay();
                });
            }
        }).start();
    }

    private void updateTerminalDisplay() {
        tvTerminal.setText(terminalOutput.toString());
        tvTerminal.post(() -> {
            int scrollAmount = tvTerminal.getLayout() != null ?
                tvTerminal.getLayout().getLineTop(tvTerminal.getLineCount()) - tvTerminal.getHeight() : 0;
            if (scrollAmount > 0) {
                tvTerminal.scrollTo(0, scrollAmount);
            } else {
                tvTerminal.scrollTo(0, 0);
            }
        });
    }

    private void sendGCodeCommandsWithProgress(java.util.List<String> commands) {
        new Thread(() -> {
            try {
                int totalCommands = commands.size();
                int sentCommands = 0;

                runOnUiThread(() -> {
                    progressBarImage.setProgress(75);
                    tvProgressText.setText("Sende G-Code... (0%)");
                });

                bluetoothHelper.sendData("$X\n");
                Thread.sleep(1000);

                runOnUiThread(() -> {
                    terminalOutput.append("[DRAWBOT BEREIT - STARTE ZEICHNUNG]\n");
                    updateTerminalDisplay();
                });

                for (String command : commands) {
                    if (command.startsWith(";")) {
                        final String comment = command;
                        runOnUiThread(() -> {
                            terminalOutput.append(comment).append("\n");
                            updateTerminalDisplay();
                        });
                        continue;
                    }

                    final String currentCommand = command;
                    final int currentNum = ++sentCommands;

                    runOnUiThread(() -> {
                        terminalOutput.append(String.format("> %s [%d/%d]\n",
                                                           currentCommand, currentNum, totalCommands));
                        updateTerminalDisplay();
                    });

                    bluetoothHelper.sendData(command + "\n");

                    if (command.startsWith("G0") && command.contains("Z")) {
                        Thread.sleep(500);
                    } else if (command.startsWith("G0") || command.startsWith("G1")) {
                        Thread.sleep(1000);
                    } else {
                        Thread.sleep(200);
                    }

                    final int commandProgress = (sentCommands * 25) / totalCommands;
                    final int totalProgress = 75 + commandProgress;
                    final int percentage = (sentCommands * 100) / totalCommands;

                    runOnUiThread(() -> {
                        progressBarImage.setProgress(totalProgress);
                        tvProgressText.setText(String.format("Sende G-Code... (%d%%)", percentage));
                    });
                }

                runOnUiThread(() -> {
                    progressBarImage.setProgress(100);
                    tvProgressText.setText("Abgeschlossen!");
                    progressBarImage.postDelayed(() -> {
                        progressBarImage.setVisibility(View.GONE);
                        tvProgressText.setVisibility(View.GONE);
                        btnDrawImage.setEnabled(true);
                    }, 3000);

                    terminalOutput.append(String.format("[ZEICHNUNG ABGESCHLOSSEN! %d Befehle gesendet]\n", totalCommands));
                    updateTerminalDisplay();
                    Toast.makeText(BluetoothTerminalActivity.this, "Zeichnung abgeschlossen!", Toast.LENGTH_LONG).show();
                });

            } catch (InterruptedException e) {
                runOnUiThread(() -> {
                    terminalOutput.append("[FEHLER: Übertragung unterbrochen]\n");
                    progressBarImage.setVisibility(View.GONE);
                    tvProgressText.setVisibility(View.GONE);
                    btnDrawImage.setEnabled(true);
                    updateTerminalDisplay();
                });
            }
        }).start();
    }

    private void testConnection() {
        if (!bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Nicht verbunden!", Toast.LENGTH_SHORT).show();
            return;
        }

        terminalOutput.append("[STARTE VERBINDUNGSTEST]\n");
        updateTerminalDisplay();

        String[] testCommands = {"?", "$X", "G21", "G90", "G0 X1 Y1"};

        new Thread(() -> {
            try {
                for (int i = 0; i < testCommands.length; i++) {
                    final String command = testCommands[i];
                    final int commandNum = i + 1;

                    runOnUiThread(() -> {
                        terminalOutput.append(String.format("[TEST %d/%d] > %s\n", commandNum, testCommands.length, command));
                        updateTerminalDisplay();
                    });

                    bluetoothHelper.sendData(command + "\n");
                    Thread.sleep(1000);
                }

                runOnUiThread(() -> {
                    terminalOutput.append("[VERBINDUNGSTEST ABGESCHLOSSEN]\n");
                    updateTerminalDisplay();
                    Toast.makeText(BluetoothTerminalActivity.this, "Verbindungstest abgeschlossen", Toast.LENGTH_LONG).show();
                });

            } catch (InterruptedException e) {
                runOnUiThread(() -> {
                    terminalOutput.append("[VERBINDUNGSTEST UNTERBROCHEN]\n");
                    updateTerminalDisplay();
                });
            }
        }).start();
    }

    /**
     * Sendet die G-Code-Befehle aus dem mehrzeiligen Eingabefeld
     */
    private void sendGCodeCommands() {
        String gCodeInput = etGCodeInput.getText().toString().trim();
        if (gCodeInput.isEmpty()) {
            Toast.makeText(this, "Bitte G-Code eingeben", Toast.LENGTH_SHORT).show();
            return;
        }

        // Teile den G-Code in einzelne Zeilen auf
        String[] gCodeLines = gCodeInput.split("\n");
        gCodeQueue = new ArrayList<>(Arrays.asList(gCodeLines));
        currentGCodeIndex = 0;

        // Starte das Senden der G-Code-Befehle
        isGCodeRunning = true;
        isGCodePaused = false;
        sendNextGCodeCommand();
    }

    /**
     * Sendet den nächsten G-Code-Befehl in der Warteschlange
     */
    private void sendNextGCodeCommand() {
        if (!isGCodeRunning || currentGCodeIndex >= gCodeQueue.size()) {
            // Stoppe den G-Code-Versand, wenn wir am Ende der Warteschlange sind
            isGCodeRunning = false;
            Toast.makeText(this, "G-Code Übertragung abgeschlossen", Toast.LENGTH_SHORT).show();
            return;
        }

        String command = gCodeQueue.get(currentGCodeIndex);
        currentGCodeIndex++;

        // Sende den Befehl über Bluetooth
        bluetoothHelper.sendData(command + "\n");

        // Aktualisiere die Terminal-Anzeige
        terminalOutput.append(String.format("> %s [%d/%d]\n", command, currentGCodeIndex, gCodeQueue.size()));
        updateTerminalDisplay();

        // Plane den nächsten Befehl mit der aktuellen Verzögerung
        gCodeHandler.postDelayed(this::sendNextGCodeCommand, gCodeDelay);
    }

    /**
     * Pausiert oder setzt den G-Code-Versand fort
     */
    private void toggleGCodePause() {
        if (!isGCodeRunning) {
            Toast.makeText(this, "G-Code wird gerade nicht gesendet", Toast.LENGTH_SHORT).show();
            return;
        }

        isGCodePaused = !isGCodePaused;

        if (isGCodePaused) {
            // Pausiere den Versand
            gCodeHandler.removeCallbacks(gCodeRunnable);
            terminalOutput.append("[G-Code Versand pausiert]\n");
        } else {
            // Setze den Versand fort
            terminalOutput.append("[Setze G-Code Versand fort]\n");
            sendNextGCodeCommand();
        }

        updateTerminalDisplay();
    }

    /**
     * Stoppt den G-Code-Versand
     */
    private void stopGCodeCommands() {
        isGCodeRunning = false;
        isGCodePaused = false;
        gCodeHandler.removeCallbacks(gCodeRunnable);
        terminalOutput.append("[G-Code Versand gestoppt]\n");
        updateTerminalDisplay();
    }
}
