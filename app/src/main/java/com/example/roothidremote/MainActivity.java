package com.example.roothidremote;

import android.Manifest;
import android.app.Activity;
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
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private final RootHidBackend rootBackend = new RootHidBackend("/dev/hidg0", "/dev/hidg1");
    private final BluetoothHidBackend bluetoothBackend = new BluetoothHidBackend();
    private final Map<String, BluetoothDevice> bluetoothDevices = new LinkedHashMap<>();
    private ArrayAdapter<String> deviceAdapter;
    private TextView status;
    private BluetoothDevice selectedDevice;
    private boolean receiverRegistered;
    private float lastX, lastY;

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                addBluetoothDevice(device);
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestBluetoothPermissions();
        IntentFilter discoveryFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(discoveryReceiver, discoveryFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(discoveryReceiver, discoveryFilter);
        }
        receiverRegistered = true;

        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,32,32,32);
        status = label("Hazır"); root.addView(status);
        Button check = button("Bluetooth / root durumunu kontrol et"); root.addView(check);
        Button scan = button("Bluetooth cihazlarını tara / yenile"); root.addView(scan);
        ListView devices = new ListView(this); deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, new ArrayList<>()); devices.setAdapter(deviceAdapter); devices.setChoiceMode(ListView.CHOICE_MODE_SINGLE); devices.setMinimumHeight(260); root.addView(devices);
        Button connect = button("Seçili Bluetooth cihaza bağlan"); root.addView(connect);
        EditText text = new EditText(this); text.setHint("Klavye ile gönderilecek yazı"); root.addView(text);
        Button send = button("Yazıyı gönder"); root.addView(send);
        TextView pad = label("Mouse pad: burada sürükle\nSol tık için dokun"); pad.setMinHeight(420); pad.setTextSize(20); root.addView(pad);
        Button right = button("Sağ tık"); root.addView(right);
        setContentView(root);

        check.setOnClickListener(v -> refreshStatus());
        scan.setOnClickListener(v -> scanBluetoothDevices());
        devices.setOnItemClickListener((parent, view, position, id) -> selectBluetoothDevice(position));
        connect.setOnClickListener(v -> connectSelectedBluetoothDevice());
        send.setOnClickListener(v -> runHid(() -> rootBackend.sendText(text.getText().toString()), () -> bluetoothBackend.sendText(text.getText().toString()), true));
        right.setOnClickListener(v -> runHid(() -> rootBackend.click(2), () -> bluetoothBackend.click(2), true));
        pad.setOnTouchListener((v, event) -> handleTouch(event));
        refreshStatus();
        scanBluetoothDevices();
    }

    @Override protected void onDestroy() {
        bluetoothBackend.stopDiscovery();
        if (receiverRegistered) unregisterReceiver(discoveryReceiver);
        super.onDestroy();
    }

    private void refreshStatus() {
        boolean rootOk = rootBackend.canUseRoot();
        boolean btOk = bluetoothBackend.initialize(this);
        status.setText("Root: " + (rootOk ? "var" : "yok")
                + "\nBluetooth HID: " + (btOk ? "hazırlanıyor" : "kapalı/izin yok")
                + "\nSeçili cihaz: " + (selectedDevice == null ? "yok" : BluetoothHidBackend.safeName(selectedDevice))
                + "\nAktif çıkış: " + (bluetoothBackend.isConnected() ? "Bluetooth HID" : "root /dev/hidg")
                + "\n" + bluetoothBackend.status()
                + "\nUSB gadget yolları: /dev/hidg0 klavye, /dev/hidg1 mouse");
    }

    private void scanBluetoothDevices() {
        bluetoothBackend.initialize(this);
        for (BluetoothDevice device : bluetoothBackend.bondedDevices()) addBluetoothDevice(device);
        boolean started = bluetoothBackend.startDiscovery();
        toast(started ? "Bluetooth taraması başladı" : bluetoothBackend.status());
        refreshStatus();
    }

    private void addBluetoothDevice(BluetoothDevice device) {
        if (device == null) return;
        String key;
        try { key = device.getAddress(); } catch (SecurityException e) { toast("Bluetooth izinleri gerekli"); return; }
        if (!bluetoothDevices.containsKey(key)) {
            bluetoothDevices.put(key, device);
            deviceAdapter.add(BluetoothHidBackend.safeName(device));
            deviceAdapter.notifyDataSetChanged();
        }
    }

    private void selectBluetoothDevice(int position) {
        List<BluetoothDevice> devices = new ArrayList<>(bluetoothDevices.values());
        if (position >= 0 && position < devices.size()) {
            selectedDevice = devices.get(position);
            refreshStatus();
        }
    }

    private void connectSelectedBluetoothDevice() {
        bluetoothBackend.stopDiscovery();
        boolean ok = bluetoothBackend.connect(selectedDevice);
        toast(bluetoothBackend.status());
        if (!ok) refreshStatus();
    }

    private boolean handleTouch(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) { lastX = event.getX(); lastY = event.getY(); return true; }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            int dx = Math.round((event.getX() - lastX) / 2f); int dy = Math.round((event.getY() - lastY) / 2f);
            lastX = event.getX(); lastY = event.getY(); runHid(() -> rootBackend.moveMouse(dx, dy), () -> bluetoothBackend.moveMouse(dx, dy), false); return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) { runHid(() -> rootBackend.click(1), () -> bluetoothBackend.click(1), false); return true; }
        return true;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE}, 10);
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 11);
        }
    }

    private TextView label(String value) { TextView view = new TextView(this); view.setText(value); view.setTextSize(16); view.setPadding(0,12,0,12); return view; }
    private Button button(String value) { Button b = new Button(this); b.setText(value); return b; }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    private void runHid(HidAction rootAction, HidAction bluetoothAction, boolean showSuccess) {
        new Thread(() -> {
            try {
                if (bluetoothBackend.isConnected()) {
                    bluetoothAction.run();
                } else {
                    rootAction.run();
                }
                if (showSuccess) runOnUiThread(() -> toast("Gönderildi"));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    private interface HidAction { void run() throws Exception; }
}
