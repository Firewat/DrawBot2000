package com.example.drawbot;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothConnectActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 101;
    private Spinner spinnerDevices;
    private Button btnRefreshDevices;
    private Button btnConnect;
    private ArrayList<BluetoothDevice> deviceList = new ArrayList<>();
    private BluetoothHelper bluetoothHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       // setContentView(R.layout.activity_bluetooth_connect);

        spinnerDevices = findViewById(R.id.spinnerDevices);
        btnRefreshDevices = findViewById(R.id.btnRefreshDevices);
        btnConnect = findViewById(R.id.btnConnect);
        bluetoothHelper = new BluetoothHelper(this);

        btnRefreshDevices.setOnClickListener(v -> loadPairedDevices());
        btnConnect.setOnClickListener(v -> connectToSelectedDevice());

        if (!bluetoothHelper.isBluetoothSupported()) {
            Toast.makeText(this, "Bluetooth wird nicht unterstützt", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!bluetoothHelper.isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            loadPairedDevices();
        }
    }

    private void loadPairedDevices() {
        deviceList.clear();
        List<String> deviceNames = new ArrayList<>();
        Set<BluetoothDevice> pairedDevices = bluetoothHelper.getPairedDevices();
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device);
                deviceNames.add(device.getName() != null ? device.getName() : device.getAddress());
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDevices.setAdapter(adapter);
    }

    private void connectToSelectedDevice() {
        int pos = spinnerDevices.getSelectedItemPosition();
        if (pos >= 0 && pos < deviceList.size()) {
            BluetoothDevice device = deviceList.get(pos);
            bluetoothHelper.connectToDevice(device, new BluetoothHelper.ConnectionCallback() {
                @Override
                public void onSuccess() {
                    Toast.makeText(BluetoothConnectActivity.this, "Verbunden!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(BluetoothConnectActivity.this, BluetoothTerminalActivity.class);
                    startActivity(intent);
                    finish();
                }
                @Override
                public void onFailure(String error) {
                    Toast.makeText(BluetoothConnectActivity.this, "Verbindung fehlgeschlagen: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(this, "Bitte Gerät auswählen", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            loadPairedDevices();
        }
    }
}

