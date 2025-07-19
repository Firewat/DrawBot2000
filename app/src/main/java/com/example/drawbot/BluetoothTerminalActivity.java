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
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothTerminalActivity extends AppCompatActivity {
    private static final String TAG = "BluetoothTerminalActivity";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;
    private static final int REQUEST_ENABLE_BT = 101;
    private static final int REQUEST_SELECT_GCODE_FILE = 102;

    private BluetoothHelper bluetoothHelper;
    private TextView tvTerminal;
    private Button btnConnect;
    private Button btnDisconnect;
    private Button btnClearTerminal;
    private Button btnRefreshDevices;
    private Button btnUploadGCode;
    private TextView tvSelectedFile;

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
    private int bufferedCommands = 0;
    private static final int MAX_BUFFERED_COMMANDS = 200;
    private boolean isGCodeRunning = false;
    private boolean isGCodePaused = false;
    private Handler gCodeHandler = new Handler(Looper.getMainLooper());
    private Runnable gCodeRunnable;
    private int gCodeDelay = 50; // Default delay in milliseconds

    private Spinner spinnerDevices;
    private ArrayList<BluetoothDevice> deviceList = new ArrayList<>();
    private StringBuilder terminalOutput = new StringBuilder();
    private Uri selectedGCodeFileUri = null;

    // Für G-Code Bestätigungs-Handling
    private boolean waitingForOk = false;
    private long lastCommandTime = 0;
    private int commandsSent = 0;
    private int commandsProcessed = 0;
    private static final int COMMAND_TIMEOUT_MS = 10000; // 10 seconds
    private static final int INTER_COMMAND_DELAY_MS = 20; // 20ms between commands



    // Home button
    private Button btnHome;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_terminal);

        bluetoothHelper = new BluetoothHelper(this);

        // Initialize UI elements
        initializeViews();

        // Terminal should be scrollable
        tvTerminal.setMovementMethod(new ScrollingMovementMethod());

        // Check if Bluetooth is supported
        if (!bluetoothHelper.isBluetoothSupported()) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Check permissions
        checkBluetoothPermissions();

        // Check Bluetooth status and enable if needed
        if (!bluetoothHelper.isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            // Check permission based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            } else {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            }
        } else {
            // Only load devices if permissions are already granted
            if (hasBluetoothPermissions()) {
                loadPairedDevices();
            }
        }

        // Setup callbacks and listeners
        setupCallbacks();
        setupButtonListeners();
    }

    private void initializeViews() {
        tvTerminal = findViewById(R.id.tvTerminal);
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnClearTerminal = findViewById(R.id.btnClearTerminal);
        btnRefreshDevices = findViewById(R.id.btnRefreshDevices);
        btnUploadGCode = findViewById(R.id.btnUploadGCode);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        spinnerDevices = findViewById(R.id.spinnerDevices);

        // Multi-line G-code UI elements
        etGCodeInput = findViewById(R.id.etGCodeInput);
        btnSendGCode = findViewById(R.id.btnSendGCode);
        btnPauseGCode = findViewById(R.id.btnPauseGCode);
        btnStopGCode = findViewById(R.id.btnStopGCode);
        tvGCodeProgress = findViewById(R.id.tvGCodeProgress);
        tvGCodeDelay = findViewById(R.id.tvGCodeDelay);
        etGCodeDelay = findViewById(R.id.etGCodeDelay);

        // Home-Button initialisieren
        btnHome = findViewById(R.id.btnHome);
    }

    private void setupCallbacks() {
        // Callback for received messages
        bluetoothHelper.setMessageCallback(message -> {
            // Zeilenumbruch und Whitespaces entfernen
            String cleanMsg = message.replace("\r", "").replace("\n", "").trim();
            if (!cleanMsg.isEmpty()) {
                terminalOutput.append("< ").append(cleanMsg).append("\n");
                updateTerminalDisplay();
            }
            // Prüfe auf "ok", "ook" oder "k" (Bestätigung vom Arduino)
            if (isGCodeRunning && waitingForOk) {
                String lower = cleanMsg.toLowerCase();
                if (lower.equals("ok") || lower.equals("ook") || lower.equals("k")) {
                    waitingForOk = false;
                    if (bufferedCommands > 0) bufferedCommands--;
                }
            }
        });

        // Callback for connection status
        bluetoothHelper.setStatusCallback(connected -> {
            btnConnect.setEnabled(!connected);
            btnDisconnect.setEnabled(connected);
            btnSendGCode.setEnabled(connected);
            btnUploadGCode.setEnabled(connected);
            etGCodeInput.setEnabled(connected);

            // Enable multi-line G-code input and controls
            etGCodeInput.setEnabled(connected);
            btnSendGCode.setEnabled(connected);
            btnPauseGCode.setEnabled(connected && isGCodeRunning);
            btnStopGCode.setEnabled(connected && isGCodeRunning);
            etGCodeDelay.setEnabled(connected);

            if (connected) {
                Toast.makeText(BluetoothTerminalActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                terminalOutput.append("[CONNECTED]\n");
            } else {
                Toast.makeText(BluetoothTerminalActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                terminalOutput.append("[DISCONNECTED]\n");
            }
            tvTerminal.setText(terminalOutput.toString());
        });
    }

    private void setupButtonListeners() {
        // Connect button
        btnConnect.setOnClickListener(v -> {
            int position = spinnerDevices.getSelectedItemPosition();
            if (position >= 0 && position < deviceList.size()) {
                BluetoothDevice device = deviceList.get(position);
                connectToDeviceWithGrbl(device);
            } else {
                Toast.makeText(this, "Please select a device", Toast.LENGTH_SHORT).show();
            }
        });

        // Disconnect button
        btnDisconnect.setOnClickListener(v -> {
            bluetoothHelper.disconnect();
        });

        // Clear terminal
        btnClearTerminal.setOnClickListener(v -> {
            terminalOutput.setLength(0);
            tvTerminal.setText("");
            clearGCodeState();
        });

        // Refresh devices
        btnRefreshDevices.setOnClickListener(v -> {
            loadPairedDevices();
        });

        // Upload G-code file
        btnUploadGCode.setOnClickListener(v -> {
            openGCodeFileChooser();
        });

        // G-code control buttons
        btnSendGCode.setOnClickListener(v -> sendGCodeCommands());
        btnPauseGCode.setOnClickListener(v -> toggleGCodePause());
        btnStopGCode.setOnClickListener(v -> stopGCodeCommands());

        // G-code delay input
        etGCodeDelay.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                updateGCodeDelay();
            }
        });

        // Home-Button Listener
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                if (bluetoothHelper != null && bluetoothHelper.isConnected()) {
                    sendGrblCommand("G1 X0 Y0 Z0;");
                    terminalOutput.append("> G1 X0 Y0 Z0;\n");
                    updateTerminalDisplay();
                } else {
                    Toast.makeText(this, "Nicht verbunden!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void openGCodeFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Add support for common G-code file extensions
        String[] mimeTypes = {"text/plain", "application/octet-stream", "*/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        startActivityForResult(Intent.createChooser(intent, "Select G-code file"), REQUEST_SELECT_GCODE_FILE);
    }

    private void loadGCodeFromFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder gCodeContent = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                gCodeContent.append(line).append("\n");
            }

            reader.close();
            inputStream.close();

            // Set the G-code content to the input field
            etGCodeInput.setText(gCodeContent.toString());

            // Update file selection display
            String fileName = getFileName(uri);
            tvSelectedFile.setText(fileName != null ? fileName : "File loaded");

            Toast.makeText(this, "G-code file loaded successfully", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error loading G-code file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error loading G-code file", e);
        }
    }

    private String getFileName(Uri uri) {
        String fileName = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (fileName == null) {
            fileName = uri.getPath();
            int cut = fileName.lastIndexOf('/');
            if (cut != -1) {
                fileName = fileName.substring(cut + 1);
            }
        }
        return fileName;
    }

    private void sendGrblCommand(String command) {
        // ENTFERNT: Einzelner G-Code Befehl
        // Wird nur noch für Multi-Line G-Code intern verwendet
        bluetoothHelper.sendData(command + "\n");
    }

    private void goHome() {
        if (!bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Nicht verbunden!", Toast.LENGTH_SHORT).show();
            return;
        }

        terminalOutput.append("[FAHRE ZUR HOME-POSITION]\n");
        updateTerminalDisplay();

        String[] homeCommands = {"$X", "G21", "G90", "G0 Z1", "G0 X0 Y0"};

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

        // Ensure the terminal scrolls to the bottom
        tvTerminal.post(() -> {
            ScrollView scrollView = findViewById(R.id.scrollViewTerminal);
            if (scrollView != null) {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private void sendGCodeCommands() {
        String gCodeText = etGCodeInput.getText().toString().trim();
        if (gCodeText.isEmpty()) {
            Toast.makeText(this, "Please enter G-code commands or upload a file", Toast.LENGTH_SHORT).show();
            return;
        }

        // Parse G-code commands
        gCodeQueue.clear();
        String[] lines = gCodeText.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith(";")) { // Skip empty lines and comments
                gCodeQueue.add(line);
            }
        }

        if (gCodeQueue.isEmpty()) {
            Toast.makeText(this, "No valid G-code commands found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Start sending G-code
        currentGCodeIndex = 0;
        isGCodeRunning = true;
        isGCodePaused = false;
        updateGCodeProgress();
        updateGCodeButtons();
        sendNextGCodeCommandWithDelay();
    }

    private void sendNextGCodeCommandWithDelay() {
        if (!isGCodeRunning || isGCodePaused || currentGCodeIndex >= gCodeQueue.size()) {
            return;
        }
        String command = gCodeQueue.get(currentGCodeIndex);
        sendGrblCommand(command);
        currentGCodeIndex++;
        updateGCodeProgress();
        if (currentGCodeIndex < gCodeQueue.size()) {
            gCodeHandler.postDelayed(this::sendNextGCodeCommandWithDelay, gCodeDelay);
        } else {
            isGCodeRunning = false;
            updateGCodeButtons();
            Toast.makeText(this, "All G-code commands sent", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleGCodePause() {
        isGCodePaused = !isGCodePaused;

        if (isGCodePaused) {
            gCodeHandler.removeCallbacks(gCodeRunnable);
            btnPauseGCode.setText("▶ Resume");
        } else {
            btnPauseGCode.setText("⏸ Pause");
            sendNextGCodeCommandWithDelay();
        }
    }

    private void stopGCodeCommands() {
        isGCodeRunning = false;
        isGCodePaused = false;
        gCodeHandler.removeCallbacks(gCodeRunnable);

        updateGCodeButtons();
        Toast.makeText(this, "G-code sending stopped", Toast.LENGTH_SHORT).show();
    }

    private void updateGCodeProgress() {
        String progress = currentGCodeIndex + "/" + gCodeQueue.size() + " commands";
        tvGCodeProgress.setText(progress);
    }

    private void updateGCodeButtons() {
        boolean connected = bluetoothHelper.isConnected();
        btnSendGCode.setEnabled(connected && !isGCodeRunning);
        btnPauseGCode.setEnabled(connected && isGCodeRunning);
        btnStopGCode.setEnabled(connected && isGCodeRunning);

        if (!isGCodeRunning) {
            btnPauseGCode.setText("⏸ Pause");
        }
    }

    private void updateGCodeDelay() {
        try {
            String delayText = etGCodeDelay.getText().toString().trim();
            if (!delayText.isEmpty()) {
                int newDelay = Integer.parseInt(delayText);
                if (newDelay >= 10 && newDelay <= 5000) {
                    gCodeDelay = newDelay;
                    tvGCodeDelay.setText("Delay: " + gCodeDelay + "ms");
                } else {
                    Toast.makeText(this, "Delay must be between 10-5000ms", Toast.LENGTH_SHORT).show();
                    etGCodeDelay.setText(String.valueOf(gCodeDelay));
                }
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid delay value", Toast.LENGTH_SHORT).show();
            etGCodeDelay.setText(String.valueOf(gCodeDelay));
        }
    }

    private void checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+) - new Bluetooth permissions
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
            } else {
                loadPairedDevices();
            }
        } else {
            // Android 11 and earlier - classic Bluetooth permissions
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
            } else {
                loadPairedDevices();
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
                builder.setTitle("Bluetooth Permissions Required")
                        .setMessage("This app needs Bluetooth permissions to connect to your HC-06 device.")
                        .setPositiveButton("OK", (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SELECT_GCODE_FILE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                selectedGCodeFileUri = data.getData();
                loadGCodeFromFile(selectedGCodeFileUri);
            }
        } else if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            loadPairedDevices();
        }
    }

    private void connectToDeviceWithGrbl(BluetoothDevice device) {
        terminalOutput.append("[VERBINDE MIT: ").append(device.getName()).append("]\n");
        updateTerminalDisplay();

        bluetoothHelper.connectToDevice(device, new BluetoothHelper.ConnectionCallback() {
            @Override
            public void onSuccess() {
                terminalOutput.append("[VERBINDUNG ERFOLGREICH]\n");
                updateTerminalDisplay();
            }

            @Override
            public void onFailure(String error) {
                terminalOutput.append("[VERBINDUNG FEHLGESCHLAGEN: ").append(error).append("]\n");
                updateTerminalDisplay();
            }
        });
    }

    private void loadPairedDevices() {
        deviceList.clear();
        List<String> deviceNames = new ArrayList<>();

        // Add debug output to terminal
        terminalOutput.append("[SCANNING FOR PAIRED DEVICES...]\n");
        updateTerminalDisplay();

        // Check permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                terminalOutput.append("[ERROR: BLUETOOTH_CONNECT permission not granted]\n");
                updateTerminalDisplay();
                return;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                terminalOutput.append("[ERROR: BLUETOOTH permission not granted]\n");
                updateTerminalDisplay();
                return;
            }
        }

        Set<BluetoothDevice> pairedDevices = bluetoothHelper.getPairedDevices();
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            terminalOutput.append("[FOUND " + pairedDevices.size() + " PAIRED DEVICES:]\n");

            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device);
                String deviceName;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        deviceName = device.getName();
                    } else {
                        deviceName = device.getAddress();
                    }
                } else {
                    deviceName = device.getName();
                }
                String deviceAddress = device.getAddress();

                if (deviceName == null) {
                    deviceName = deviceAddress;
                }

                // Add debug info for each device
                terminalOutput.append("  • " + deviceName + " (" + deviceAddress + ")\n");

                deviceNames.add(deviceName);
            }

            terminalOutput.append("[DEVICE SCAN COMPLETE]\n");
        } else {
            terminalOutput.append("[NO PAIRED DEVICES FOUND]\n");
            terminalOutput.append("[PLEASE PAIR YOUR HC-06 DEVICE IN SYSTEM SETTINGS]\n");
        }

        updateTerminalDisplay();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDevices.setAdapter(adapter);

        if (deviceNames.isEmpty()) {
            Toast.makeText(this, "No paired devices found. Please pair your HC-06 in Android Settings.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Found " + deviceNames.size() + " paired devices", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (bluetoothHelper != null) {
            bluetoothHelper.disconnect();
        }
        super.onDestroy();
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
        }
    }


    private void clearGCodeState() {
        gCodeQueue.clear();
        currentGCodeIndex = 0;
        isGCodeRunning = false;
        isGCodePaused = false;
        waitingForOk = false;
        updateGCodeButtons();
        updateGCodeProgress();
    }
}
