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
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class BluetoothTerminalActivity extends AppCompatActivity {
    private static final String TAG = "BluetoothTerminalActivity";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;
    private static final int REQUEST_ENABLE_BT = 101;
    private static final int REQUEST_SELECT_GCODE_FILE = 102;
    private static final int COMMAND_TIMEOUT_MS = 45000;
    private static final int INTER_COMMAND_DELAY_MS = 50;
    private static final int DEFAULT_GCODE_DELAY_MS = 1000; // Default delay for G-code commands
    private BluetoothHelper bluetoothHelper;


    // GRBL constants

    private static final int GRBL_BUFFER_SIZE = 128;          // Grbl's RX buffer size
    private static final int GRBL_PLANNER_BLOCKS = 16;        // Grbl's planner buffer
    private static final int SETUP_COMMAND_DELAY_MS = 100;    // G21, G90, G92 commands
    private static final int MOVEMENT_COMMAND_DELAY_MS = 50;   // G0, G1 commands
    private static final int MOTOR_COMMAND_DELAY_MS = 200;     // M17, M18 commands
    private static final int HOME_COMMAND_DELAY_MS = 500;      // G28 homing
    /////////
    ///
    private static final float PEN_UP_Z = 5.0f;     // Z coordinate for pen up
    private static final float PEN_DOWN_Z = 0.0f;



    // UI
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
    private int currentGCodeIndex = 0, commandsSent = 0, commandsProcessed = 0;
    private boolean isGCodeRunning = false, waitingForOk = false;
    private long lastCommandTime = 0;
    private Uri selectedGCodeFileUri = null;









    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_terminal);

        bluetoothHelper = new BluetoothHelper(this);

        initializeViews();
        tvTerminal.setMovementMethod(new ScrollingMovementMethod());

        // Check BT
        if (!bluetoothHelper.isBluetoothSupported()) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }


        checkBluetoothPermissions();
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

        // Multi-line G-code UI elements
        etGCodeInput = findViewById(R.id.etGCodeInput);
        btnSendGCode = findViewById(R.id.btnSendGCode);
        btnStopGCode = findViewById(R.id.btnStopGCode);
        tvGCodeProgress = findViewById(R.id.tvGCodeProgress);

        // Home-Button initialisieren
        btnHome = findViewById(R.id.btnHome);
    }

    private void setupCallbacks() {
        // Callback for received messages with proper flow control
        bluetoothHelper.setMessageCallback(message -> {
            String cleanMsg = message.replace("\r", "").replace("\n", "").trim();
            if (!cleanMsg.isEmpty()) {
                terminalOutput.append("< ").append(cleanMsg).append("\n");
                updateTerminalDisplay();

                // Handle "ok" responses for flow control
                if (cleanMsg.equals("ok")) {
                    waitingForOk = false;
                    commandsProcessed++;
                    updateGCodeProgress();
                }
                // Handle errors
                else if (cleanMsg.startsWith("Error:")) {
                    waitingForOk = false;
                    Log.e(TAG, "Arduino error: " + cleanMsg);
                    commandsProcessed++; // Count errors to keep progress moving
                }
                // Handle other acknowledgments (ook, k)
                else if (cleanMsg.equals("ook") || cleanMsg.equals("k")) {
                    waitingForOk = false;
                    commandsProcessed++;
                    updateGCodeProgress();
                }
                // Handle simple "error" response too
                else if (cleanMsg.equals("error")) {
                    waitingForOk = false;
                    Log.e(TAG, "Arduino error received");
                    commandsProcessed++; // Count errors to keep progress moving
                }
            }
        });

        // Callback for connection status
        bluetoothHelper.setStatusCallback(connected -> {
            if (btnConnect != null) btnConnect.setEnabled(!connected);
            if (btnDisconnect != null) btnDisconnect.setEnabled(connected);
            if (btnSendGCode != null) btnSendGCode.setEnabled(connected);
            if (btnUploadGCode != null) btnUploadGCode.setEnabled(connected);
            if (etGCodeInput != null) etGCodeInput.setEnabled(connected);

            // Enable multi-line G-code input and controls
            if (etGCodeInput != null) etGCodeInput.setEnabled(connected);
            if (btnSendGCode != null) btnSendGCode.setEnabled(connected && !isGCodeRunning);
            if (btnStopGCode != null) btnStopGCode.setEnabled(connected && isGCodeRunning);
            if (btnHome != null) btnHome.setEnabled(connected);

            if (connected) {
                Toast.makeText(BluetoothTerminalActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                terminalOutput.append("[CONNECTED]\n");
                terminalOutput.append("[Send 'TEST' for motor test, 'CAL' for calibration]\n");
            } else {
                Toast.makeText(BluetoothTerminalActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                terminalOutput.append("[DISCONNECTED]\n");
                // Stop G-code execution if disconnected
                if (isGCodeRunning) {
                    stopGCodeCommands();
                }
            }
            updateTerminalDisplay();
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
        btnStopGCode.setOnClickListener(v -> stopGCodeCommands());

        // Home-Button Listener
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                if (bluetoothHelper != null && bluetoothHelper.isConnected()) {
                    if (!isGCodeRunning) {
                        goHome();
                    } else {
                        Toast.makeText(this, "Cannot home while G-code is running", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Not connected!", Toast.LENGTH_SHORT).show();
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
        if (!bluetoothHelper.isConnected()) {
            return;
        }

        // Add timestamp to track command timing
        lastCommandTime = System.currentTimeMillis();
        waitingForOk = true;
        commandsSent++;

        // Send command with proper termination
        bluetoothHelper.sendData(command + "\n");

        // Add to terminal display
        terminalOutput.append("> ").append(command).append("\n");
        updateTerminalDisplay();
    }

    private void initializePenPlotter() {
        // Send initialization commands for pen plotter
        sendGrblCommand("G21"); // Set units to millimeters
        sendGrblCommand("G90"); // Absolute positioning
        sendGrblCommand("G92 X0 Y0 Z0"); // Set current position as origin
        sendGrblCommand("G0 Z" + PEN_UP_Z); // Start with pen up
    }




    private void goHome() {
        if (!bluetoothHelper.isConnected()) {
            Toast.makeText(this, "Not connected!", Toast.LENGTH_SHORT).show();
            return;
        }

        terminalOutput.append("[MOVING TO HOME POSITION]\n");
        updateTerminalDisplay();


       // sendGrblCommand("M17"); // Enable motors
        gCodeHandler.postDelayed(() -> sendGrblCommand("G28"), 500); // Use G28 home command

        Toast.makeText(this, "Homing to (0,0)...", Toast.LENGTH_SHORT).show();
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

        // Parse G-code commands with pen plotter conversion
        gCodeQueue.clear();
        String[] lines = gCodeText.split("\n");
        for (String line : lines) {
            line = line.trim();
            // Skip empty lines, comments, and unit commands that can cause issues
            if (!line.isEmpty() &&
                    !line.startsWith(";") &&
                    !line.startsWith("(") &&
                    !line.startsWith("G21") &&  // Skip unit commands
                    !line.startsWith("G90")) {   // Skip absolute mode (already default)

                // Convert the G-code line for pen plotter BEFORE adding to queue
                String convertedLine = convertGCodeForPenPlotter(line);
                gCodeQueue.add(convertedLine);
            }
        }

        if (gCodeQueue.isEmpty()) {
            Toast.makeText(this, "No valid G-code commands found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Reset counters
        currentGCodeIndex = 0;
        commandsSent = 0;
        commandsProcessed = 0;
        waitingForOk = false;

        // Start execution
        isGCodeRunning = true;
        updateGCodeProgress();
        updateGCodeButtons();

        // Send initial connection commands for pen plotter
        terminalOutput.append("[STARTING PEN PLOTTER EXECUTION - ").append(gCodeQueue.size()).append(" commands]\n");
        updateTerminalDisplay();

        // Initialize pen plotter
        initializePenPlotter();

        gCodeHandler.postDelayed(() -> {
            sendNextGCodeCommandWithDelay();
        }, 2000); // Extra time for pen plotter initialization

    }

    private String convertGCodeForPenPlotter(String gcodeLine) {
        String line = gcodeLine.trim().toUpperCase();

        // Skip empty lines, comments, and non-movement commands
        if (line.isEmpty() || line.startsWith(";") || line.startsWith("(") ||
                line.startsWith("M") || line.startsWith("G28") || line.startsWith("G92")) {
            return gcodeLine;
        }

        // Handle G0 commands (pen up movement)
        if (line.startsWith("G0") || line.startsWith("G00")) {
            return convertToMovementCommand(line, PEN_UP_Z, "G0");
        }

        // Handle G1 commands (pen down movement)
        if (line.startsWith("G1") || line.startsWith("G01")) {
            return convertToMovementCommand(line, PEN_DOWN_Z, "G1");
        }

        // Return original line if no conversion needed
        return gcodeLine;
    }

    private String convertToMovementCommand(String line, float zValue, String gCommand) {
        StringBuilder convertedCommand = new StringBuilder();

        // Start with the G command
        convertedCommand.append(gCommand);

        // Extract X and Y coordinates if present
        String xCoord = extractCoordinate(line, "X");
        String yCoord = extractCoordinate(line, "Y");
        String feedRate = extractCoordinate(line, "F");

        // Add coordinates to command
        if (xCoord != null) {
            convertedCommand.append(" X").append(xCoord);
        }
        if (yCoord != null) {
            convertedCommand.append(" Y").append(yCoord);
        }

        // Always add Z coordinate for pen up/down
        convertedCommand.append(" Z").append(zValue);

        // Add feed rate if present
        if (feedRate != null) {
            convertedCommand.append(" F").append(feedRate);
        }

        return convertedCommand.toString();
    }

    private String extractCoordinate(String line, String axis) {
        int index = line.indexOf(axis);
        if (index == -1) return null;

        // Start after the axis letter
        int start = index + 1;
        int end = start;

        // Find the end of the number (including decimal points and negative signs)
        while (end < line.length()) {
            char c = line.charAt(end);
            if (Character.isDigit(c) || c == '.' || c == '-') {
                end++;
            } else {
                break;
            }
        }

        if (end > start) {
            return line.substring(start, end);
        }
        return null;
    }

    private void sendNextGCodeCommandWithDelay() {
        if (!isGCodeRunning) {
            return;
        }

        // Check if we've sent all commands
        if (currentGCodeIndex >= gCodeQueue.size()) {
            // Send M400 to wait for completion, then M18 to disable motors
            if (currentGCodeIndex == gCodeQueue.size()) {
                sendGrblCommand("M400"); // Wait for all moves to complete
                currentGCodeIndex++; // Increment to prevent re-sending M400
                gCodeHandler.postDelayed(this::sendNextGCodeCommandWithDelay, 100);
                return;
            } else if (currentGCodeIndex == gCodeQueue.size() + 1) {
                sendGrblCommand("M18"); // Disable motors
                currentGCodeIndex++; // Increment to prevent re-sending M18
                gCodeHandler.postDelayed(this::sendNextGCodeCommandWithDelay, 100);
                return;
            } else {
                // All commands including M400 and M18 have been sent
                finishGCodeExecution();
                return;
            }
        }

        // Wait for previous command to be acknowledged
        if (waitingForOk) {
            // Check for timeout
            if (System.currentTimeMillis() - lastCommandTime > COMMAND_TIMEOUT_MS) {
                Log.w(TAG, "Command timeout, continuing anyway");
                terminalOutput.append("[TIMEOUT - CONTINUING]\n");
                updateTerminalDisplay();
                waitingForOk = false;
                commandsProcessed++; // Count timeout as processed
            } else {
                // Still waiting, check again in 20ms
                gCodeHandler.postDelayed(this::sendNextGCodeCommandWithDelay, 20);
                return;
            }
        }

        // Send next command
        String command = gCodeQueue.get(currentGCodeIndex);
        sendGrblCommand(command);
        currentGCodeIndex++;

        gCodeHandler.postDelayed(this::sendNextGCodeCommandWithDelay, Math.max(DEFAULT_GCODE_DELAY_MS, INTER_COMMAND_DELAY_MS));
    }

    private void finishGCodeExecution() {
        isGCodeRunning = false;
        waitingForOk = false;
        updateGCodeButtons();

        runOnUiThread(() -> {
            terminalOutput.append("[DRAWING COMPLETED - ALL MOVES FINISHED]\n");
            updateTerminalDisplay();
            Toast.makeText(this, "G-code execution completed successfully!", Toast.LENGTH_LONG).show();
        });
    }

    private void stopGCodeCommands() {
        isGCodeRunning = false;
        waitingForOk = false;
        gCodeHandler.removeCallbacksAndMessages(null); // Remove all pending callbacks

        // Send emergency stop
        if (bluetoothHelper.isConnected()) {
            sendGrblCommand("M18"); // Disable motors
        }

        updateGCodeButtons();
        terminalOutput.append("[G-CODE EXECUTION STOPPED]\n");
        updateTerminalDisplay();
        Toast.makeText(this, "G-code execution stopped", Toast.LENGTH_SHORT).show();
    }

    private void updateGCodeProgress() {
        int totalCommands = gCodeQueue.size() + 2; // +2 for M400 and M18
        String progress = commandsProcessed + "/" + totalCommands + " commands processed";
        if (tvGCodeProgress != null) {
            tvGCodeProgress.setText(progress);
        }

        // Calculate percentage based on processed commands
        int percentage = totalCommands > 0 ? (commandsProcessed * 100) / totalCommands : 0;

        // Log progress for debugging
        Log.d(TAG, "Progress: " + percentage + "% (" + progress + ")");
    }

    private void updateGCodeButtons() {
        boolean connected = bluetoothHelper.isConnected();
        if (btnSendGCode != null) btnSendGCode.setEnabled(connected && !isGCodeRunning);
        if (btnStopGCode != null) btnStopGCode.setEnabled(connected && isGCodeRunning);
        if (btnHome != null) btnHome.setEnabled(connected && !isGCodeRunning);
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
        terminalOutput.append("[CONNECTING TO: ").append(device.getName()).append("]\n");
        updateTerminalDisplay();

        bluetoothHelper.connectToDevice(device, new BluetoothHelper.ConnectionCallback() {
            @Override
            public void onSuccess() {
                terminalOutput.append("[CONNECTION SUCCESSFUL]\n");
                updateTerminalDisplay();
            }

            @Override
            public void onFailure(String error) {
                terminalOutput.append("[CONNECTION FAILED: ").append(error).append("]\n");
                updateTerminalDisplay();
            }
        });
    }

    private void loadPairedDevices() {
        deviceList.clear();
        List<String> deviceNames = new ArrayList<>();

        // Add debug output to terminal
        terminalOutput.append("[SCANNING DEVICES...]\n");
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
            terminalOutput.append("[PLEASE PAIR YOUR DEVICE IN SETTINGS]\n");
        }

        updateTerminalDisplay();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDevices.setAdapter(adapter);

    }

    @Override
    protected void onDestroy() {
        if (bluetoothHelper != null) {
            bluetoothHelper.disconnect();
        }
        // Clean up handlers
        if (gCodeHandler != null) {
            gCodeHandler.removeCallbacksAndMessages(null);
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
        commandsSent = 0;
        commandsProcessed = 0;
        isGCodeRunning = false;
        waitingForOk = false;
        if (gCodeHandler != null) {
            gCodeHandler.removeCallbacksAndMessages(null);
        }
        updateGCodeButtons();
        updateGCodeProgress();
    }
}