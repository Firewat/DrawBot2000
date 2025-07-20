package com.example.drawbot;

import android.bluetooth.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BluetoothHelper {
    private static final String TAG = "BluetoothHelper";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private Context context;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isConnected = false;
    private boolean stopReading = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private MessageCallback messageCallback;
    private ConnectionStatusCallback statusCallback;

    public BluetoothHelper(Context context) {
        this.context = context;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public Set<BluetoothDevice> getPairedDevices() {
        // Berechtigungsprüfung je nach Android-Version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ - neue Bluetooth-Berechtigungen
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "BLUETOOTH_CONNECT Berechtigung fehlt");
                return null;
            }
        } else {
            // Android 11 und früher - klassische Bluetooth-Berechtigungen
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "BLUETOOTH Berechtigung fehlt");
                return null;
            }
        }

        if (bluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter ist null");
            return null;
        }

        return bluetoothAdapter.getBondedDevices();
    }

    public void connectToDevice(BluetoothDevice device, ConnectionCallback callback) {
        new Thread(() -> {
            try {
                // Berechtigungsprüfung je nach Android-Version
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ - neue Bluetooth-Berechtigungen
                    if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        handler.post(() -> callback.onFailure("BLUETOOTH_CONNECT Berechtigung fehlt"));
                        return;
                    }
                } else {
                    // Android 11 und früher - klassische Bluetooth-Berechtigungen
                    if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                        handler.post(() -> callback.onFailure("BLUETOOTH Berechtigung fehlt"));
                        return;
                    }
                }

                // Falls bereits eine Verbindung besteht, zuerst trennen
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }

                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                bluetoothSocket.connect();

                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                isConnected = true;

                if (statusCallback != null) {
                    handler.post(() -> statusCallback.onStatusChanged(true));
                }

                handler.post(() -> callback.onSuccess());

                // Starte den Lese-Thread
                startReadingData();

            } catch (IOException e) {
                isConnected = false;
                handler.post(() -> callback.onFailure(e.getMessage()));

                if (statusCallback != null) {
                    handler.post(() -> statusCallback.onStatusChanged(false));
                }
            }
        }).start();
    }

    private void startReadingData() {
        stopReading = false;
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            while (!stopReading) {
                try {
                    if (inputStream == null) break;

                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String receivedData = new String(buffer, 0, bytes);

                        if (messageCallback != null) {
                            handler.post(() -> messageCallback.onMessageReceived(receivedData));
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Fehler beim Lesen der Daten: " + e.getMessage());
                    break;
                }
            }
        }).start();
    }

    public boolean sendData(String message) {
        if (!isConnected || outputStream == null) {
            Log.e(TAG, "Nicht verbunden oder OutputStream ist null");
            return false;
        }

        new Thread(() -> {
            try {
                outputStream.write(message.getBytes());
                outputStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Fehler beim Senden: " + e.getMessage());
                isConnected = false;

                if (statusCallback != null) {
                    handler.post(() -> statusCallback.onStatusChanged(false));
                }
            }
        }).start();
        return true;
    }

    public void disconnect() {
        stopReading = true;
        isConnected = false;

        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }

            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }

            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }

            if (statusCallback != null) {
                handler.post(() -> statusCallback.onStatusChanged(false));
            }
        } catch (IOException e) {
            Log.e(TAG, "Fehler beim Trennen: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
    }

    public void setStatusCallback(ConnectionStatusCallback callback) {
        this.statusCallback = callback;
    }

    public interface ConnectionCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public interface MessageCallback {
        void onMessageReceived(String message);
    }

    public interface ConnectionStatusCallback {
        void onStatusChanged(boolean isConnected);
    }
}