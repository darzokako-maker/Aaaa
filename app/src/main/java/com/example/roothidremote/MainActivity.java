package com.example.roothidremote;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Build;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private final RootHidBackend rootBackend = new RootHidBackend("/dev/hidg0", "/dev/hidg1");
    private final BluetoothHidBackend bluetoothBackend = new BluetoothHidBackend();
    
    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> deviceListAdapter;
    private final List<BluetoothDevice> discoveredDevices = new ArrayList<>();
    
    private TextView status;
    private boolean bluetoothReceiverRegistered;
    private float lastX, lastY;

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && !discoveredDevices.contains(device) && hasBluetoothRuntimePermissions()) {
                    discoveredDevices.add(device);
                    String name = bluetoothDeviceName(device);
                    deviceListAdapter.add(name + "\n" + bluetoothDeviceAddress(device));
                    deviceListAdapter.notifyDataSetChanged();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Toast.makeText(context, "Tarama tamamlandı", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestBluetoothRuntimePermissions();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,32,32,32);
        
        status = label("Hazır"); root.addView(status);
        Button check = button("Bluetooth / root durumunu kontrol et"); root.addView(check);
        
        Button scanBt = button("Cihazları Tara / Eşleşenleri Getir"); root.addView(scanBt);

        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView deviceListView = new ListView(this);
        deviceListView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400));
        deviceListView.setAdapter(deviceListAdapter);
        root.addView(deviceListView);

        EditText text = new EditText(this); text.setHint("Klavye ile gönderilecek yazı"); root.addView(text);
        Button send = button("Yazıyı gönder"); root.addView(send);
        TextView pad = label("Mouse pad: burada sürükle\nSol tık için dokun"); pad.setMinHeight(420); pad.setTextSize(20); root.addView(pad);
        Button right = button("Sağ tık"); root.addView(right);
        
        scrollView.addView(root);
        setContentView(scrollView);

        check.setOnClickListener(v -> refreshStatus());
        scanBt.setOnClickListener(v -> startBluetoothScan());
        
        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < discoveredDevices.size()) {
                BluetoothDevice selectedDevice = discoveredDevices.get(position);
                new Thread(() -> {
                    boolean connected = bluetoothBackend.connectDevice(selectedDevice);
                    runOnUiThread(() -> Toast.makeText(this, (connected ? "Bağlantı kuruluyor: " : "Bağlantı başarısız: ") + bluetoothDeviceAddress(selectedDevice), Toast.LENGTH_SHORT).show());
                }).start();
            }
        });

        send.setOnClickListener(v -> runRoot(() -> rootBackend.sendText(text.getText().toString())));
        right.setOnClickListener(v -> runRoot(() -> rootBackend.click(2)));
        pad.setOnTouchListener((v, event) -> handleTouch(event));

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(bluetoothReceiver, filter);
        bluetoothReceiverRegistered = true;

        refreshStatus();
    }

    @SuppressLint("MissingPermission")
    private void startBluetoothScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Lütfen Bluetooth'u açın", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasBluetoothRuntimePermissions()) {
            Toast.makeText(this, "Bluetooth taraması için izin verin", Toast.LENGTH_LONG).show();
            requestBluetoothRuntimePermissions();
            return;
        }

        discoveredDevices.clear();
        deviceListAdapter.clear();

        Set<BluetoothDevice> pairedDevices;
        try {
            pairedDevices = bluetoothAdapter.getBondedDevices();
        } catch (SecurityException missingPermission) {
            Toast.makeText(this, "Bluetooth cihaz listesi için izin eksik", Toast.LENGTH_LONG).show();
            return;
        }
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                discoveredDevices.add(device);
                String name = bluetoothDeviceName(device);
                deviceListAdapter.add("[Eşleşmiş] " + name + "\n" + bluetoothDeviceAddress(device));
            }
        }

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        try {
            if (bluetoothAdapter.startDiscovery()) {
                Toast.makeText(this, "Tarama başlatıldı...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Tarama başlatılamadı", Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException missingPermission) {
            Toast.makeText(this, "Bluetooth taraması için izin eksik", Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasBluetoothRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE
            }, 10);
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 10);
        }
    }

    private String bluetoothDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name != null ? name : "Bilinmeyen Cihaz";
        } catch (SecurityException missingPermission) {
            return "Bilinmeyen Cihaz";
        }
    }

    private String bluetoothDeviceAddress(BluetoothDevice device) {
        try {
            return device.getAddress();
        } catch (SecurityException missingPermission) {
            return "Adres izni yok";
        }
    }

    private void refreshStatus() {
        boolean rootOk = rootBackend.canUseRoot();
        boolean btOk = bluetoothBackend.isSupported(this);
        status.setText("Root: " + (rootOk ? "var" : "yok") + "\nBluetooth HID: " + (btOk ? "deneniyor" : "kapalı/izin yok") + "\n" + bluetoothBackend.status() + "\nUSB gadget yolları: /dev/hidg0 klavye, /dev/hidg1 mouse");
    }

    private boolean handleTouch(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) { lastX = event.getX(); lastY = event.getY(); return true; }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            int dx = Math.round((event.getX() - lastX) / 2f); int dy = Math.round((event.getY() - lastY) / 2f);
            lastX = event.getX(); lastY = event.getY(); runRoot(() -> rootBackend.moveMouse(dx, dy)); return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) { runRoot(() -> rootBackend.click(1)); return true; }
        return true;
    }

    private TextView label(String value) { TextView view = new TextView(this); view.setText(value); view.setTextSize(16); view.setPadding(0,12,0,12); return view; }
    private Button button(String value) { Button b = new Button(this); b.setText(value); return b; }

    private void runRoot(HidAction action) {
        new Thread(() -> {
            try { action.run(); runOnUiThread(() -> Toast.makeText(this, "Gönderildi", Toast.LENGTH_SHORT).show()); }
            catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()); }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothReceiverRegistered) {
            try { unregisterReceiver(bluetoothReceiver); } catch (Exception ignored) {}
            bluetoothReceiverRegistered = false;
        }
    }

    private interface HidAction { void run() throws Exception; }
}
