package com.ludocontroller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQ_PERM = 100;

    private BluetoothAdapter btAdapter;
    private BluetoothSocket socket;
    private OutputStream out;
    private boolean connected = false;

    private String selectedColor = null; // null=any, "R","G","B","Y"

    private TextView statusText;
    private Button btnRed, btnGreen, btnBlue, btnYellow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        btAdapter = BluetoothAdapter.getDefaultAdapter();

        statusText = findViewById(R.id.statusText);
        btnRed = findViewById(R.id.btnRed);
        btnGreen = findViewById(R.id.btnGreen);
        btnBlue = findViewById(R.id.btnBlue);
        btnYellow = findViewById(R.id.btnYellow);

        findViewById(R.id.connectBtn).setOnClickListener(v -> requestPermissionsAndConnect());

        btnRed.setOnClickListener(v -> selectColor("R"));
        btnGreen.setOnClickListener(v -> selectColor("G"));
        btnBlue.setOnClickListener(v -> selectColor("B"));
        btnYellow.setOnClickListener(v -> selectColor("Y"));

        findViewById(R.id.clearColorBtn).setOnClickListener(v -> {
            selectedColor = null;
            updateColorButtons();
            toast("Color cleared (any player)");
        });

        findViewById(R.id.d1).setOnClickListener(v -> sendDice(1));
        findViewById(R.id.d2).setOnClickListener(v -> sendDice(2));
        findViewById(R.id.d3).setOnClickListener(v -> sendDice(3));
        findViewById(R.id.d4).setOnClickListener(v -> sendDice(4));
        findViewById(R.id.d5).setOnClickListener(v -> sendDice(5));
        findViewById(R.id.d6).setOnClickListener(v -> sendDice(6));

        findViewById(R.id.resetBtn).setOnClickListener(v -> sendCmd("RESET"));
        findViewById(R.id.skipBtn).setOnClickListener(v -> sendCmd("SKIP"));
        findViewById(R.id.winBtn).setOnClickListener(v -> sendCmd("WIN"));
        findViewById(R.id.killRedBtn).setOnClickListener(v -> sendCmd("KILL_RED"));
        findViewById(R.id.killGreenBtn).setOnClickListener(v -> sendCmd("KILL_GREEN"));
        findViewById(R.id.killBlueBtn).setOnClickListener(v -> sendCmd("KILL_BLUE"));
        findViewById(R.id.killYellowBtn).setOnClickListener(v -> sendCmd("KILL_YELLOW"));

        updateColorButtons();
    }

    private void selectColor(String c) {
        if (c.equals(selectedColor)) {
            selectedColor = null;
            toast("Color cleared (any player)");
        } else {
            selectedColor = c;
            toast("Selected: " + colorName(c));
        }
        updateColorButtons();
    }

    private String colorName(String c) {
        switch (c) {
            case "R": return "Red";
            case "G": return "Green";
            case "B": return "Blue";
            case "Y": return "Yellow";
        }
        return "Any";
    }

    private void updateColorButtons() {
        btnRed.setAlpha(selectedColor == null || selectedColor.equals("R") ? 1f : 0.4f);
        btnGreen.setAlpha(selectedColor == null || selectedColor.equals("G") ? 1f : 0.4f);
        btnBlue.setAlpha(selectedColor == null || selectedColor.equals("B") ? 1f : 0.4f);
        btnYellow.setAlpha(selectedColor == null || selectedColor.equals("Y") ? 1f : 0.4f);
    }

    private void sendDice(int n) {
        String cmd = (selectedColor != null) ? (selectedColor + n) : String.valueOf(n);
        sendCmd(cmd);
    }

    private void sendCmd(String cmd) {
        if (!connected || out == null) {
            toast("Not connected!");
            return;
        }
        try {
            out.write((cmd + "\n").getBytes());
            out.flush();
            toast("Sent: " + cmd);
        } catch (Exception e) {
            toast("Send failed");
            connected = false;
            statusText.setText("Disconnected");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void requestPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                        REQ_PERM);
                return;
            }
        }
        showDevicePicker();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_PERM) {
            boolean ok = true;
            for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) showDevicePicker();
            else toast("Bluetooth permission denied");
        }
    }

    @SuppressLint("MissingPermission")
    private void showDevicePicker() {
        if (btAdapter == null) { toast("Bluetooth not supported"); return; }
        if (!btAdapter.isEnabled()) { toast("Please turn on Bluetooth"); return; }

        Set<BluetoothDevice> bonded = btAdapter.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) {
            toast("No paired devices. Pair game phone first.");
            return;
        }

        List<BluetoothDevice> list = new ArrayList<>(bonded);
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            BluetoothDevice d = list.get(i);
            names[i] = d.getName() + "\n" + d.getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Game Device")
                .setItems(names, (dialog, which) -> connectTo(list.get(which)))
                .show();
    }

    @SuppressLint("MissingPermission")
    private void connectTo(BluetoothDevice device) {
        statusText.setText("Connecting to " + device.getName() + "...");
        new Thread(() -> {
            try {
                if (socket != null) { try { socket.close(); } catch (Exception ignored) {} }
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                btAdapter.cancelDiscovery();
                socket.connect();
                out = socket.getOutputStream();
                connected = true;
                runOnUiThread(() -> {
                    statusText.setText("Connected to " + device.getName());
                    toast("Connected!");
                });
            } catch (Exception e) {
                connected = false;
                runOnUiThread(() -> {
                    statusText.setText("Not connected");
                    toast("Connection failed. Is game running?");
                });
            }
        }).start();
    }
                                    }
