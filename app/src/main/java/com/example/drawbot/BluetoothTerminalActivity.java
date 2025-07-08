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
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
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
    private Button btnSelectImage;
    private Button btnDrawImage;
    private Button btnPreviewGCode;
    private EditText etImageSize;
    private EditText etThreshold;
    private EditText etLineSpacing;
    private EditText etFeedRate;
    private CheckBox cbOptimizePath;
    private CheckBox cbInvertImage;
    private TextView tvSelectedImage;
    private TextView tvGCodeStats;
    private ProgressBar progressBarImage;
    private TextView tvProgressText;
    private Spinner spinnerDevices;
    private Spinner spinnerConversionMode;

    private ArrayList<BluetoothDevice> deviceList;
    private StringBuilder terminalOutput = new StringBuilder();
    private Uri selectedImageUri = null;
    private ImageToGCodeConverter.GCodeResult lastGCodeResult = null;

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
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnDrawImage = findViewById(R.id.btnDrawImage);
        btnPreviewGCode = findViewById(R.id.btnPreviewGCode);
        etImageSize = findViewById(R.id.etImageSize);
        etThreshold = findViewById(R.id.etThreshold);
        etLineSpacing = findViewById(R.id.etLineSpacing);
        etFeedRate = findViewById(R.id.etFeedRate);
        cbOptimizePath = findViewById(R.id.cbOptimizePath);
        cbInvertImage = findViewById(R.id.cbInvertImage);
        tvSelectedImage = findViewById(R.id.tvSelectedImage);
        tvGCodeStats = findViewById(R.id.tvGCodeStats);
        progressBarImage = findViewById(R.id.progressBarImage);
        tvProgressText = findViewById(R.id.tvProgressText);
        spinnerDevices = findViewById(R.id.spinnerDevices);
        spinnerConversionMode = findViewById(R.id.spinnerConversionMode);

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
            btnSelectImage.setEnabled(connected);
            btnDrawImage.setEnabled(connected);
            etCommand.setEnabled(connected);

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

        // Bild auswählen
        btnSelectImage.setOnClickListener(v -> {
            selectImage();
        });

        // Bild zeichnen
        btnDrawImage.setOnClickListener(v -> {
            drawImageAdvanced();
        });

        // Vorschau G-Code
        btnPreviewGCode.setOnClickListener(v -> {
            previewGCode();
        });

        // Diagnose-Button hinzufügen (temporär für Debugging)
        btnTestCircle.setOnLongClickListener(v -> {
            testConnection();
            return true;
        });

        // Konvertierungsmodus-Spinner initialisieren
        setupConversionModeSpinner();
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
                    tvGCodeStats.setText("Generiere Vorschau...");
                });

                ImageToGCodeConverter.GCodeResult result =
                        ImageToGCodeConverter.convertImageToGCode(this, selectedImageUri, settings);

                lastGCodeResult = result;

                runOnUiThread(() -> {
                    if (result.gCodeCommands.isEmpty() || result.gCodeCommands.get(0).startsWith("; FEHLER")) {
                        tvGCodeStats.setText("❌ Fehler bei der Konvertierung");
                        tvGCodeStats.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
                        Toast.makeText(BluetoothTerminalActivity.this, "Fehler bei der G-Code-Generierung!", Toast.LENGTH_SHORT).show();
                    } else {
                        String stats = String.format(
                                "✅ Vorschau:\n" +
                                "Modus: %s\n" +
                                "Befehle: %d\n" +
                                "Bewegungen: %d (%d zeichnend)\n" +
                                "Distanz: %.1f mm\n" +
                                "Geschätzte Zeit: %.1f Sekunden",
                                settings.mode.name().replace("_", " "),
                                result.totalCommands,
                                result.totalMoves,
                                result.drawingMoves,
                                result.totalDistance,
                                result.estimatedTime
                        );
                        tvGCodeStats.setText(stats);
                        tvGCodeStats.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));

                        // Aktiviere Draw-Button
                        btnDrawImage.setEnabled(bluetoothHelper.isConnected());

                        // Zeige ersten G-Code im Terminal
                        terminalOutput.append("[G-CODE VORSCHAU GENERIERT]\n");
                        terminalOutput.append(String.format("[MODUS: %s]\n", settings.mode.name()));
                        terminalOutput.append(String.format("[BEFEHLE: %d, ZEIT: %.1fs]\n", result.totalCommands, result.estimatedTime));

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

            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvGCodeStats.setText("❌ Fehler: " + e.getMessage());
                    tvGCodeStats.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
                    Toast.makeText(BluetoothTerminalActivity.this, "Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        if (lastGCodeResult != null) {
            sendGCodeCommandsWithProgress(lastGCodeResult.gCodeCommands);
            return;
        }

        // Ansonsten generiere neuen G-Code
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
            tvProgressText.setText("Starte erweiterte Bildverarbeitung...");
            btnDrawImage.setEnabled(false);
        });

        terminalOutput.append(String.format("[STARTE ERWEITERTE KONVERTIERUNG - Modus: %s]\n", settings.mode.name()));
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

                // Phase 2: G-Code Konvertierung (50%)
                runOnUiThread(() -> {
                    progressBarImage.setProgress(50);
                    tvProgressText.setText("Generiere " + settings.mode.name() + " G-Code...");
                });

                ImageToGCodeConverter.GCodeResult result =
                        ImageToGCodeConverter.convertImageToGCode(this, selectedImageUri, settings);

                if (result.gCodeCommands.isEmpty() || result.gCodeCommands.get(0).startsWith("; FEHLER")) {
                    runOnUiThread(() -> {
                        progressBarImage.setVisibility(View.GONE);
                        tvProgressText.setVisibility(View.GONE);
                        btnDrawImage.setEnabled(true);
                        terminalOutput.append("[FEHLER BEI DER ERWEITERTEN KONVERTIERUNG]\n");
                        updateTerminalDisplay();
                        Toast.makeText(BluetoothTerminalActivity.this, "Fehler bei der Bildkonvertierung!", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                lastGCodeResult = result;

                // Phase 3: Bereit zum Senden (75%)
                runOnUiThread(() -> {
                    progressBarImage.setProgress(75);
                    tvProgressText.setText(String.format("Bereit: %d Befehle generiert", result.totalCommands));
                    terminalOutput.append(String.format("[ERWEITERTE KONVERTIERUNG ABGESCHLOSSEN]\n"));
                    terminalOutput.append(result.summary).append("\n");
                    terminalOutput.append("[STARTE ÜBERTRAGUNG...]\n");
                    updateTerminalDisplay();
                });

                // Phase 4: G-Code senden (75-100%)
                sendGCodeCommandsWithProgress(result.gCodeCommands);

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBarImage.setVisibility(View.GONE);
                    tvProgressText.setVisibility(View.GONE);
                    btnDrawImage.setEnabled(true);
                    terminalOutput.append("[FEHLER: ").append(e.getMessage()).append("]\n");
                    updateTerminalDisplay();
                    Toast.makeText(BluetoothTerminalActivity.this, "Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Bild auswählen"), REQUEST_SELECT_IMAGE);
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
}
