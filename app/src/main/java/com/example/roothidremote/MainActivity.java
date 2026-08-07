package com.example.roothidremote;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
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
    private float lastX, lastY;

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && !discoveredDevices.contains(device)) {
                    discoveredDevices.add(device);
                    String name = device.getName() != null ? device.getName() : "Bilinmeyen Cihaz";
                    deviceListAdapter.add(name + "\n" + device.getAddress());
                    deviceListAdapter.notifyDataSetChanged();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Toast.makeText(context, "Arama tamamlandı.", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermissionsIfNecessary();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); 
        root.setOrientation(LinearLayout.VERTICAL); 
        root.setPadding(32, 32, 32, 32);

        status = label("Hazır"); 
        root.addView(status);

        Button check = button("Bluetooth / Root Durumunu Kontrol Et"); 
        root.addView(check);

        Button scanBt = button("Cihazları Tara / Eşleşenleri Getir");
        root.addView(scanBt);

        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView deviceListView = new ListView(this);
        deviceListView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 400));
        deviceListView.setAdapter(deviceListAdapter);
        root.addView(deviceListView);

        EditText text = new EditText(this); 
        text.setHint("Klavye ile gönderilecek yazı"); 
        root.addView(text);

        Button send = button("Yazıyı gönder"); 
        root.addView(send);

        TextView pad = label("Mouse Pad: Sürükle / Sol tık için dokun"); 
        pad.setMinHeight(350); 
        pad.setTextSize(18); 
        root.addView(pad);

        Button right = button("Sağ tık"); 
        root.addView(right);

        scrollView.addView(root);
        setContentView(scrollView);

        check.setOnClickListener(v -> refreshStatus());
        scanBt.setOnClickListener(v -> startBluetoothScan());
        
        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < discoveredDevices.size()) {
                BluetoothDevice selectedDevice = discoveredDevices.get(position);
                boolean connected = bluetoothBackend.connectDevice(selectedDevice);
                Toast.makeText(this, (connected ? "Bağlantı isteği gönderildi: " : "Bağlantı başarısız (Eşleştirmeyi Ayarlar'dan deneyin): ") + selectedDevice.getAddress(), Toast.LENGTH_SHORT).show();
            }
        });

        send.setOnClickListener(v -> runRoot(() -> rootBackend.sendText(text.getText().toString())));
        right.setOnClickListener(v -> runRoot(() -> rootBackend.click(2)));
        pad.setOnTouchListener((v, event) -> handleTouch(event));

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(bluetoothReceiver, filter);

        refreshStatus();
    }

    private void requestPermissionsIfNecessary() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{
                Manifest.permission.BLUETOOTH_CONNECT, 
                Manifest.permission.BLUETOOTH_SCAN, 
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }, 10);
        } else {
            requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }, 10);
        }
    }

    @SuppressLint("MissingPermission")
    private void startBluetoothScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Lütfen Bluetooth'u açın", Toast.LENGTH_SHORT).show();
            return;
        }

        discoveredDevices.clear();
        deviceListAdapter.clear();

        // 1. Önce önceden eşleşmiş (Paired) cihazları ekle
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                discoveredDevices.add(device);
                String name = device.getName() != null ? device.getName() : "Bilinmeyen Cihaz";
                deviceListAdapter.add("[Eşleşmiş] " + name + "\n" + device.getAddress());
            }
        }

        // 2. Etraftaki yeni cihazları taramaya başla
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();
        Toast.makeText(this, "Tarama başlatıldı...", Toast.LENGTH_SHORT).show();
    }

    private void refreshStatus() {
        boolean rootOk = rootBackend.canUseRoot();
        boolean btOk = bluetoothBackend.isSupported(this);
        status.setText("Root: " + (rootOk ? "Var" : "Yok") + 
                "\nBluetooth HID: " + (btOk ? "Aktif" : "Erişim Yok") + 
                "\n" + bluetoothBackend.status());
    }

    private boolean handleTouch(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) { 
            lastX = event.getX(); 
            lastY = event.getY(); 
            return true; 
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            int dx = Math.round(event.getX() - lastX); 
            int dy = Math.round(event.getY() - lastY);
            if (dx != 0 || dy != 0) {
                lastX = event.getX(); 
                lastY = event.getY(); 
                runRoot(() -> rootBackend.moveMouse(dx, dy)); 
            }
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) { 
            runRoot(() -> rootBackend.click(1)); 
            return true; 
        }
        return true;
    }

    private TextView label(String value) { 
        TextView view = new TextView(this); 
        view.setText(value); 
        view.setTextSize(16); 
        view.setPadding(0, 12, 0, 12); 
        return view; 
    }

    private Button button(String value) { 
        Button b = new Button(this); 
        b.setText(value); 
        return b; 
    }

    private void runRoot(HidAction action) {
        new Thread(() -> {
            try { action.run(); }
            catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()); }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(bluetoothReceiver);
        } catch (Exception ignored) {}
    }

    private interface HidAction { void run() throws Exception; }
}
