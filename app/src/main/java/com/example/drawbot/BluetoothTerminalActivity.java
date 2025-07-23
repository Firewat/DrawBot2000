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
    private static final int COMMAND_TIMEOUT_MS = 10000;
   // private static final int INTER_COMMAND_DELAY_MS = 50; // Optimized for 28BYJ-48
    private static final int GCODE_DELAY_MS = 75;
    private static final int MAX_TERMINAL_LINES = 500; // for phone memory issues

    private static BluetoothHelper bluetoothHelper;
    private static BluetoothTerminalActivity instance;

    // UI components
    private TextView tvTerminal, tvSelectedFile, tvGCodeProgress;
    private Button btnConnect, btnDisconnect, btnClearTerminal, btnRefreshDevices,
            btnUploadGCode, btnSendGCode, btnStopGCode, btnHome;
    private EditText etGCodeInput;
    private Spinner spinnerDevices;

    // G-code execution state
    private final List<String> gCodeQueue = new ArrayList<>();
    private final ArrayList<BluetoothDevice> deviceList = new ArrayList<>();
    private final StringBuilder terminalOutput = new StringBuilder();
    private final Handler gCodeHandler = new Handler(Looper.getMainLooper());
    private int currentGCodeIndex = 0, commandsProcessed = 0;
    private boolean isGCodeRunning = false, waitingForOk = false;
    private long lastCommandTime = 0;
    private String estimatedTimeString = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_terminal);

        instance = this;

        if (bluetoothHelper == null) {
            bluetoothHelper = new BluetoothHelper(this);
        }

        initializeViews();
        tvTerminal.setMovementMethod(new ScrollingMovementMethod());

        if (!bluetoothHelper.isBluetoothSupported()) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        checkBluetoothPermissions();
        if (!bluetoothHelper.isBluetoothEnabled()) {
            requestBluetoothEnable();
        } else {
            if (hasBluetoothPermissions()) {
                loadPairedDevices();
            }
        }

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
        etGCodeInput = findViewById(R.id.etGCodeInput);
        btnSendGCode = findViewById(R.id.btnSendGCode);
        btnStopGCode = findViewById(R.id.btnStopGCode);
        tvGCodeProgress = findViewById(R.id.tvGCodeProgress);
        btnHome = findViewById(R.id.btnHome);

    }

    private void setupCallbacks() {
        if (bluetoothHelper == null) return;

        bluetoothHelper.setMessageCallback(message -> {
            String cleanMsg = message.replace("\r", "").replace("\n", "").trim();
            if (!cleanMsg.isEmpty()) {
                addToTerminal("< " + cleanMsg);

                if (cleanMsg.equals("ok") || cleanMsg.equals("ook") || cleanMsg.equals("k")) {
                    waitingForOk = false;
                    commandsProcessed++;
                    updateGCodeProgress();
                } else if (cleanMsg.startsWith("Error:") || cleanMsg.equals("error")) {
                    waitingForOk = false;
                    Log.e(TAG, "GRBL error: " + cleanMsg);
                    commandsProcessed++; // Count errors to keep progress moving
                    addToTerminal("[ERROR DETECTED - CONTINUING]");
                }
            }
        });

        bluetoothHelper.setStatusCallback(connected -> {
            if (this != null && !isFinishing() && !isDestroyed()) {
                updateUIConnectionState(connected);

                if (connected) {
                    runOnUiThread(() -> {
                        Toast.makeText(BluetoothTerminalActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                        addToTerminal("[CONNECTED - Ready for G-code commands]");
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(BluetoothTerminalActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                        addToTerminal("[DISCONNECTED]");
                        if (isGCodeRunning) {
                            stopGCodeCommands();
                        }
                    });
                }
            }
        });
    }

    private void setupButtonListeners() {
        btnConnect.setOnClickListener(v -> connectToSelectedDevice());
        btnDisconnect.setOnClickListener(v -> bluetoothHelper.disconnect());

        btnClearTerminal.setOnClickListener(v -> {
            terminalOutput.setLength(0);
            tvTerminal.setText("");
            clearGCodeState();
        });

        btnRefreshDevices.setOnClickListener(v -> loadPairedDevices());
        btnUploadGCode.setOnClickListener(v -> openGCodeFileChooser());
        btnSendGCode.setOnClickListener(v -> sendGCodeCommands());
        btnStopGCode.setOnClickListener(v -> stopGCodeCommands());
        btnHome.setOnClickListener(v -> goHome());

    }

    //terminal
    private void addToTerminal(String message) {
        terminalOutput.append(message).append("\n");

        // Prevent memory code
        String[] lines = terminalOutput.toString().split("\n");
        if (lines.length > MAX_TERMINAL_LINES) {
            terminalOutput.setLength(0);

            for (int i = lines.length - MAX_TERMINAL_LINES; i < lines.length; i++) {
                terminalOutput.append(lines[i]).append("\n");
            }
        }

        runOnUiThread(() -> {
            tvTerminal.setText(terminalOutput.toString());

            tvTerminal.post(() -> {
                ScrollView scrollView = findViewById(R.id.scrollViewTerminal);
                if (scrollView != null) {
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                }
            });
        });
    }

// g code z- axis converter to fix faulty g-code from converter
    private String convertGCodeForPenPlotter(String line) {
        String originalLine = line.trim();
        if (originalLine.isEmpty() || originalLine.startsWith(";") || originalLine.startsWith("(")) {
            return originalLine;
        }
        String upperLine = originalLine.toUpperCase();

        if (upperLine.startsWith("G0 ") || upperLine.startsWith("G00 ")) {
            String convertedLine = originalLine.replaceFirst("(?i)G0+", "G1");
            convertedLine = removeZCoordinate(convertedLine);
            return convertedLine + " Z1";
        }

        if (upperLine.startsWith("G1 ") || upperLine.startsWith("G01 ")) {
            String convertedLine = removeZCoordinate(originalLine);
            return convertedLine + " Z0";
        }

        return originalLine;
    }

// Remove Z coordinate from G-code lines incase we have different G-Code
    private String removeZCoordinate(String line) {
        // Remove any Z coordinate (Z followed by optional minus and digits/decimal)
        return line.replaceAll("(?i)\\s*Z-?[0-9]*\\.?[0-9]*", "").trim();
    }

    private void sendGCodeCommands() {
        String gCodeText = etGCodeInput.getText().toString().trim();
        if (gCodeText.isEmpty()) {
            Toast.makeText(this, "Please enter G-code commands or upload a file", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Not connected to device!", Toast.LENGTH_SHORT).show();
            return;
        }
        //pass converted g-code
        gCodeQueue.clear();
        String[] lines = gCodeText.split("\n");
        int g0Converted = 0;
        int g1Converted = 0;
        int linesUnchanged = 0;

        for (String line : lines) {
            String originalLine = line.trim();
            if (originalLine.isEmpty()) {
                continue;
            }

            String convertedLine = convertGCodeForPenPlotter(originalLine);

            String upperOriginal = originalLine.toUpperCase();
            if (upperOriginal.startsWith("G0 ") || upperOriginal.startsWith("G00 ")) {
                g0Converted++;
            } else if (upperOriginal.startsWith("G1 ") || upperOriginal.startsWith("G01 ")) {
                g1Converted++;
            } else if (!originalLine.startsWith(";") && !originalLine.startsWith("(")) {
                linesUnchanged++;
            }

            gCodeQueue.add(convertedLine);
        }

        if (gCodeQueue.isEmpty()) {
            Toast.makeText(this, "No valid G-code commands found", Toast.LENGTH_SHORT).show();
            return;
        }

        // execution
        currentGCodeIndex = 0;
        commandsProcessed = 0;
        waitingForOk = false;
        isGCodeRunning = true;

        updateGCodeProgress();
        updateGCodeButtons();

        addToTerminal("[STARTING G-CODE EXECUTION - " + gCodeQueue.size() + " commands]");

        // Calculate estimated time
        int totalMovementCommands = g0Converted + g1Converted;
        int estimatedTimeSeconds = calculateEstimatedTime(totalMovementCommands);
        estimatedTimeString = formatTime(estimatedTimeSeconds);

        // Initialize with grbl commands for better workflow
        initializePenPlotter();
        gCodeHandler.postDelayed(this::sendNextGCodeCommand, 1000);
    }

    //Initialize the DrawBot
    private void initializePenPlotter() {
        sendGrblCommand("~");   // Resume from any feed hold
        sendGrblCommand("$X");  // Clear alarm lock
        sendGrblCommand("G21"); // Millimeters
        sendGrblCommand("G90"); // Absolute positioning
        sendGrblCommand("G92 X0 Y0 Z0"); // Set current position as origin
        sendGrblCommand("M17"); // Enable steppers (if supported)
        sendGrblCommand("G1 Z1"); // Ensure pen is up initially (Z1 = pen up)

        addToTerminal("[PEN PLOTTER INITIALIZED]");
    }

    private void sendNextGCodeCommand() {
        if (!isGCodeRunning || !bluetoothHelper.isConnected()) {
            return;
        }

        // Check if more g-code to send
        if (currentGCodeIndex >= gCodeQueue.size()) {
            finishGCodeExecution();
            return;
        }

        // Wait for previous command case
        if (waitingForOk) {
            if (System.currentTimeMillis() - lastCommandTime > COMMAND_TIMEOUT_MS) {
                Log.w(TAG, "Command timeout, continuing...");
                addToTerminal("[TIMEOUT - CONTINUING]");
                waitingForOk = false;
                commandsProcessed++;
            } else {
                // Check again in 20ms
                gCodeHandler.postDelayed(this::sendNextGCodeCommand, 20);
                return;
            }
        }


        String command = gCodeQueue.get(currentGCodeIndex);
        sendGrblCommand(command);
        currentGCodeIndex++;
        gCodeHandler.postDelayed(this::sendNextGCodeCommand, GCODE_DELAY_MS);
    }


    // MAIN GRBL SENDER METHOD
    private void sendGrblCommand(String command) {
        if (!bluetoothHelper.isConnected()) {
            return;
        }

        lastCommandTime = System.currentTimeMillis();
        waitingForOk = true;

        bluetoothHelper.sendData(command + "\n");
        addToTerminal("> " + command);
    }

    private void finishGCodeExecution() {
        isGCodeRunning = false;
        waitingForOk = false;
        updateGCodeButtons();

        sendGrblCommand("G1 Z1");
        sendGrblCommand("M400");

        runOnUiThread(() -> {
            addToTerminal("[G-CODE EXECUTION COMPLETED - PEN UP (Z1)]");
            Toast.makeText(this, "Drawing completed.", Toast.LENGTH_LONG).show();
        });
    }

    private void stopGCodeCommands() {
        isGCodeRunning = false;
        waitingForOk = false;
        gCodeHandler.removeCallbacksAndMessages(null);

        updateGCodeButtons();
        addToTerminal("DrawBot stopped");

        if (bluetoothHelper.isConnected()) {
            sendGrblCommand("G1 Z1");
            sendGrblCommand("!");

            // Wait a moment then reset and re-enable motors
            gCodeHandler.postDelayed(() -> {
                sendGrblCommand("~");
                sendGrblCommand("$X");
                sendGrblCommand("M17");
                addToTerminal("[MOTORS RE-ENABLED - Ready for new commands, pen up (Z1)]");
            }, 500);
        }

        Toast.makeText(this, "Execution stopped - Motors re-enabled", Toast.LENGTH_SHORT).show();
    }


    private void goHome() {
        if (!bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Not connected!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isGCodeRunning) {
            Toast.makeText(this, "Cannot home while G-code is running", Toast.LENGTH_SHORT).show();
            return;
        }

        addToTerminal("[MOVING TO HOME POSITION]");
        sendGrblCommand("~");
        sendGrblCommand("$X");
        sendGrblCommand("G1 Z1");
        sendGrblCommand("G1 X0 Y0 Z1");
    }

    private void connectToSelectedDevice() {
        int position = spinnerDevices.getSelectedItemPosition();
        if (position >= 0 && position < deviceList.size()) {
            BluetoothDevice device = deviceList.get(position);
            connectToDeviceWithGrbl(device);
        } else {
            Toast.makeText(this, "Please select a device", Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToDeviceWithGrbl(BluetoothDevice device) {
        String deviceName = getDeviceName(device);
        addToTerminal("[CONNECTING TO: " + deviceName + "]");

        bluetoothHelper.connectToDevice(device, new BluetoothHelper.ConnectionCallback() {
            @Override
            public void onSuccess() {
                addToTerminal("[CONNECTION SUCCESSFUL]");
            }

            @Override
            public void onFailure(String error) {
                addToTerminal("[CONNECTION FAILED: " + error + "]");
            }
        });
    }

    // Add null checks to prevent crashes
    private void updateUIConnectionState(boolean connected) {
        runOnUiThread(() -> {

            if (btnConnect != null) btnConnect.setEnabled(!connected);
            if (btnDisconnect != null) btnDisconnect.setEnabled(connected);
            if (btnUploadGCode != null) btnUploadGCode.setEnabled(connected);
            if (etGCodeInput != null) etGCodeInput.setEnabled(connected);
            updateGCodeButtons();
        });
    }
    // Add null check for bluetoothHelper to prevent crash
    private void updateGCodeButtons() {

        boolean connected = (bluetoothHelper != null && bluetoothHelper.isConnected());

        runOnUiThread(() -> {
            if (btnSendGCode != null) btnSendGCode.setEnabled(connected && !isGCodeRunning);
            if (btnStopGCode != null) btnStopGCode.setEnabled(connected && isGCodeRunning);
            if (btnHome != null) btnHome.setEnabled(connected && !isGCodeRunning);
        });
    }

    private void updateGCodeProgress() {
        if (gCodeQueue.isEmpty()) return;

        int totalCommands = gCodeQueue.size();
        String progress = commandsProcessed + "/" + totalCommands + " commands processed";
        int percentage = totalCommands > 0 ? (commandsProcessed * 100) / totalCommands : 0;

        // Add estimated time to progress display
        String progressText = progress + " (" + percentage + "%)";
        if (!estimatedTimeString.isEmpty()) {
            progressText += " | Estimated: " + estimatedTimeString;
        }

        String finalProgressText = progressText;
        runOnUiThread(() -> {
            if (tvGCodeProgress != null) {
                tvGCodeProgress.setText(finalProgressText);
            }
        });

        Log.d(TAG, "Progress: " + percentage + "% (" + progress + ")");
    }

    private void openGCodeFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "application/octet-stream", "*/*"});

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

            etGCodeInput.setText(gCodeContent.toString());

            String fileName = getFileName(uri);
            tvSelectedFile.setText(fileName != null ? fileName : "File loaded");

            Toast.makeText(this, "G-code file loaded successfully", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error loading file: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

    private void requestBluetoothEnable() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    private void checkBluetoothPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            permissions = new String[]{
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_ADMIN,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            };
        }

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

    private void loadPairedDevices() {
        deviceList.clear();
        List<String> deviceNames = new ArrayList<>();

        addToTerminal("[SCANNING PAIRED DEVICES...]");

        if (!hasBluetoothPermissions()) {
            addToTerminal("[ERROR: BLUETOOTH permissions not granted]");
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothHelper.getPairedDevices();
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            addToTerminal("[FOUND " + pairedDevices.size() + " PAIRED DEVICES:]");

            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device);
                String deviceName = getDeviceName(device);
                String deviceAddress = device.getAddress();

                addToTerminal("  • " + deviceName + " (" + deviceAddress + ")");
                deviceNames.add(deviceName);
            }

            addToTerminal("[DEVICE SCAN COMPLETE]");
        } else {
            addToTerminal("[NO PAIRED DEVICES FOUND]");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDevices.setAdapter(adapter);
    }

    private String getDeviceName(BluetoothDevice device) {
        String deviceName = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                deviceName = device.getName();
            }
        } else {
            deviceName = device.getName();
        }

        return deviceName != null ? deviceName : device.getAddress();
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private int calculateEstimatedTime(int movementCommands) {
        int avgSecondsPerCommand = 2; //change this to adjust speed. 0.25 is more accurate after testing
        int commandProcessingTime = (gCodeQueue.size() * GCODE_DELAY_MS) / 1000;

        return (movementCommands * avgSecondsPerCommand) + commandProcessingTime + 5;
    }


    private String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
    private void clearGCodeState() {
        gCodeQueue.clear();
    currentGCodeIndex = 0;
    commandsProcessed = 0;
    isGCodeRunning = false;
    waitingForOk = false;
        if (gCodeHandler != null) {
        gCodeHandler.removeCallbacksAndMessages(null);
    }
    updateGCodeButtons();
    updateGCodeProgress();
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
            new AlertDialog.Builder(this)
                    .setTitle("Bluetooth Permissions Required")
                    .setMessage("This app needs Bluetooth permissions to connect to your device.")
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
            loadGCodeFromFile(data.getData());
        }
    } else if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
        loadPairedDevices();
    }
}

@Override
protected void onDestroy() {

    if (bluetoothHelper != null) {
        bluetoothHelper.setMessageCallback(null);
        bluetoothHelper.setStatusCallback(null);
    }

    // Only disconnect if the entire app is being closed
    if (isFinishing() && isTaskRoot()) {
        if (bluetoothHelper != null) {
            bluetoothHelper.disconnect();
            bluetoothHelper = null;
        }
    }

    if (gCodeHandler != null) {
        gCodeHandler.removeCallbacksAndMessages(null);
    }

    // Only clear static references when the app is truly finishing
    if (isFinishing() && isTaskRoot()) {
        instance = null;
    }

    super.onDestroy();
}

@Override
protected void onPause() {
    super.onPause();
}

@Override
protected void onResume() {
    super.onResume();
    if (bluetoothHelper != null && this == instance) {
        setupCallbacks(); // Re-establish callbacks
        updateUIConnectionState(bluetoothHelper.isConnected());
    }
}

public static BluetoothHelper getBluetoothHelper() {
    return bluetoothHelper;
}

public static boolean isBluetoothConnected() {
    try {
        return bluetoothHelper != null && bluetoothHelper.isConnected();
    } catch (Exception e) {
        return false;
    }
}


public static String getConnectionStatus() {
    if (bluetoothHelper == null) {
        return "BluetoothHelper not initialized";
    }
    try {
        return bluetoothHelper.isConnected() ? "Connected" : "Disconnected";
    } catch (Exception e) {
        return "Connection check failed: " + e.getMessage();
    }
}
}