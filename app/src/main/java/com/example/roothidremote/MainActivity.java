package com.example.roothidremote;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private final RootHidBackend rootBackend = new RootHidBackend("/dev/hidg0", "/dev/hidg1");
    private final BluetoothHidBackend bluetoothBackend = new BluetoothHidBackend();
    private TextView status;
    private float lastX, lastY;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE}, 10);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,32,32,32);
        status = label("Hazır"); root.addView(status);
        Button check = button("Bluetooth / root durumunu kontrol et"); root.addView(check);
        EditText text = new EditText(this); text.setHint("Klavye ile gönderilecek yazı"); root.addView(text);
        Button send = button("Yazıyı gönder"); root.addView(send);
        TextView pad = label("Mouse pad: burada sürükle\nSol tık için dokun"); pad.setMinHeight(420); pad.setTextSize(20); root.addView(pad);
        Button right = button("Sağ tık"); root.addView(right);
        setContentView(root);
        check.setOnClickListener(v -> refreshStatus());
        send.setOnClickListener(v -> runRoot(() -> rootBackend.sendText(text.getText().toString())));
        right.setOnClickListener(v -> runRoot(() -> rootBackend.click(2)));
        pad.setOnTouchListener((v, event) -> handleTouch(event));
        refreshStatus();
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
    private interface HidAction { void run() throws Exception; }
}
