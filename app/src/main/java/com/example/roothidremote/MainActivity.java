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
    
    private static final float MOUSE_SENSITIVITY = 0.65f;
    private static final float TAP_SLOP = 18f;

    private TextView status;
    private boolean bluetoothReceiverRegistered;
    private float lastX, lastY, downX, downY, mouseRemainderX, mouseRemainderY;
    private boolean touchMoved;

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
        EditText macAddress = new EditText(this); macAddress.setHint("MAC adresi ile bağlan (00:11:22:AA:BB:CC)"); root.addView(macAddress);
        Button connectMac = button("MAC ile Bluetooth HID bağlan"); root.addView(connectMac);

        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView deviceListView = new ListView(this);
        deviceListView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400));
        deviceListView.setAdapter(deviceListAdapter);
        root.addView(deviceListView);

        EditText text = new EditText(this); text.setHint("Klavye ile gönderilecek yazı"); root.addView(text);
        Button send = button("USB/root ile yazıyı gönder"); root.addView(send);
        Button sendBt = button("Bluetooth ile yazıyı gönder"); root.addView(sendBt);

        root.addView(label("Klavye kısayolları"));
        LinearLayout keyboardRow1 = horizontalRow(); root.addView(keyboardRow1);
        addKeyboardButton(keyboardRow1, "Enter", HidReport.KeyCode.ENTER, 0);
        addKeyboardButton(keyboardRow1, "Backspace", HidReport.KeyCode.BACKSPACE, 0);
        addKeyboardButton(keyboardRow1, "Tab", HidReport.KeyCode.TAB, 0);
        addKeyboardButton(keyboardRow1, "Esc", HidReport.KeyCode.ESCAPE, 0);
        LinearLayout keyboardRow2 = horizontalRow(); root.addView(keyboardRow2);
        addKeyboardButton(keyboardRow2, "←", HidReport.KeyCode.LEFT, 0);
        addKeyboardButton(keyboardRow2, "↑", HidReport.KeyCode.UP, 0);
        addKeyboardButton(keyboardRow2, "↓", HidReport.KeyCode.DOWN, 0);
        addKeyboardButton(keyboardRow2, "→", HidReport.KeyCode.RIGHT, 0);
        addKeyboardButton(keyboardRow2, "Delete", HidReport.KeyCode.DELETE, 0);

        TextView pad = label("Mouse pad: akıcı hareket için sürükle\nKısa dokun: sol tık"); pad.setMinHeight(420); pad.setTextSize(20); root.addView(pad);
        Button right = button("USB/root sağ tık"); root.addView(right);
        Button rightBt = button("Bluetooth sağ tık"); root.addView(rightBt);

        root.addView(label("Python script çalıştır"));
        EditText pythonScript = new EditText(this);
        pythonScript.setMinLines(5);
        pythonScript.setHint("print('Merhaba HID')");
        root.addView(pythonScript);
        Button runPython = button("Python scripti çalıştır"); root.addView(runPython);
        TextView pythonOutput = label("Python çıktısı burada görünecek"); root.addView(pythonOutput);
        
        scrollView.addView(root);
        setContentView(scrollView);

        check.setOnClickListener(v -> refreshStatus());
        scanBt.setOnClickListener(v -> startBluetoothScan());
        connectMac.setOnClickListener(v -> connectBluetoothByMac(macAddress.getText().toString()));
        
        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < discoveredDevices.size()) {
                BluetoothDevice selectedDevice = discoveredDevices.get(position);
                new Thread(() -> {
                    boolean connected = bluetoothBackend.connectDevice(selectedDevice);
                    runOnUiThread(() -> showBluetoothConnectResult(connected));
                }).start();
            }
        });

        send.setOnClickListener(v -> runRoot(() -> rootBackend.sendText(text.getText().toString())));
        sendBt.setOnClickListener(v -> runBluetoothSignal(() -> bluetoothBackend.sendText(text.getText().toString())));
        right.setOnClickListener(v -> runRoot(() -> rootBackend.click(2)));
        rightBt.setOnClickListener(v -> runBluetoothSignal(() -> bluetoothBackend.click(2)));
        runPython.setOnClickListener(v -> runPythonScript(pythonScript.getText().toString(), pythonOutput));
        pad.setOnTouchListener((v, event) -> handleTouch(v, event));

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

    private void connectBluetoothByMac(String macAddress) {
        if (!hasBluetoothRuntimePermissions()) {
            Toast.makeText(this, "MAC ile bağlantı için Bluetooth izni verin", Toast.LENGTH_LONG).show();
            requestBluetoothRuntimePermissions();
            return;
        }
        new Thread(() -> {
            boolean connected = bluetoothBackend.connectByMac(macAddress);
            runOnUiThread(() -> showBluetoothConnectResult(connected));
        }).start();
    }

    private void showBluetoothConnectResult(boolean commandSent) {
        refreshStatus();
        Toast.makeText(this, commandSent ? "Bluetooth bağlantı isteği gönderildi" : bluetoothBackend.status(), Toast.LENGTH_LONG).show();
    }

    private void refreshStatus() {
        boolean rootOk = rootBackend.canUseRoot();
        boolean btOk = bluetoothBackend.isSupported(this);
        status.setText("Root: " + (rootOk ? "var" : "yok") + "\nBluetooth HID: " + (btOk ? "deneniyor" : "kapalı/izin yok") + "\n" + bluetoothBackend.status() + "\nUSB gadget yolları: /dev/hidg0 klavye, /dev/hidg1 mouse");
    }

    private boolean handleTouch(android.view.View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            view.getParent().requestDisallowInterceptTouchEvent(true);
            downX = lastX = event.getX();
            downY = lastY = event.getY();
            mouseRemainderX = mouseRemainderY = 0f;
            touchMoved = false;
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            float rawDx = event.getX() - lastX;
            float rawDy = event.getY() - lastY;
            lastX = event.getX();
            lastY = event.getY();
            if (distance(event.getX() - downX, event.getY() - downY) > TAP_SLOP) touchMoved = true;

            mouseRemainderX += rawDx * MOUSE_SENSITIVITY;
            mouseRemainderY += rawDy * MOUSE_SENSITIVITY;
            int dx = (int) mouseRemainderX;
            int dy = (int) mouseRemainderY;
            mouseRemainderX -= dx;
            mouseRemainderY -= dy;
            if (dx != 0 || dy != 0) {
                runRootQuiet(() -> rootBackend.moveMouse(dx, dy));
                runBluetoothSignalQuiet(() -> bluetoothBackend.moveMouse(dx, dy));
            }
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            view.getParent().requestDisallowInterceptTouchEvent(false);
            if (!touchMoved && distance(event.getX() - downX, event.getY() - downY) <= TAP_SLOP) {
                runRoot(() -> rootBackend.click(1));
                runBluetoothSignal(() -> bluetoothBackend.click(1));
            }
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            view.getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        return true;
    }

    private void runPythonScript(String script, TextView outputView) {
        outputView.setText("Python script çalışıyor...");
        new Thread(() -> {
            PythonScriptRunner.Result result = PythonScriptRunner.run(this, script);
            runOnUiThread(() -> {
                outputView.setText(result.message);
                Toast.makeText(this, result.success ? "Python script çalıştı" : "Python script çalışmadı", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void addKeyboardButton(LinearLayout row, String label, int keyCode, int modifier) {
        Button key = button(label);
        key.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        key.setOnClickListener(v -> {
            runRoot(() -> rootBackend.sendKey(keyCode, modifier));
            runBluetoothSignal(() -> bluetoothBackend.sendKey(keyCode, modifier));
        });
        row.addView(key);
    }

    private LinearLayout horizontalRow() { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); return row; }
    private float distance(float dx, float dy) { return (float) Math.hypot(dx, dy); }
    private TextView label(String value) { TextView view = new TextView(this); view.setText(value); view.setTextSize(16); view.setPadding(0,12,0,12); return view; }
    private Button button(String value) { Button b = new Button(this); b.setText(value); return b; }

    private void runRoot(HidAction action) {
        new Thread(() -> {
            try { action.run(); runOnUiThread(() -> Toast.makeText(this, "Gönderildi", Toast.LENGTH_SHORT).show()); }
            catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show()); }
        }).start();
    }

    private void runRootQuiet(HidAction action) {
        new Thread(() -> {
            try { action.run(); }
            catch (Exception ignored) {}
        }).start();
    }

    private void runBluetoothSignal(BluetoothSignalAction action) {
        runBluetoothSignal(action, true);
    }

    private void runBluetoothSignalQuiet(BluetoothSignalAction action) {
        runBluetoothSignal(action, false);
    }

    private void runBluetoothSignal(BluetoothSignalAction action, boolean showError) {
        new Thread(() -> {
            boolean sent = action.run();
            if (!sent && showError) {
                runOnUiThread(() -> Toast.makeText(this, bluetoothBackend.status(), Toast.LENGTH_LONG).show());
            }
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
    private interface BluetoothSignalAction { boolean run(); }
}
