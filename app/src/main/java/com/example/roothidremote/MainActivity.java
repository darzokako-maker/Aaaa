package com.example.roothidremote;

import android.Manifest;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = label("Hazır"); addFullWidth(root, status, 0, 0, 0, 16);
        Button check = button("Bluetooth / root durumunu kontrol et"); addFullWidth(root, check, 0, 0, 0, 24);

        EditText text = new EditText(this);
        text.setHint("Klavye ile gönderilecek yazı");
        addFullWidth(root, text, 0, 0, 0, 12);
        Button send = button("Yazıyı gönder"); addFullWidth(root, send, 0, 0, 0, 24);

        TextView pad = label("Mouse pad: burada sürükle\nSol tık için dokun");
        pad.setMinHeight(420);
        pad.setTextSize(20);
        pad.setGravity(Gravity.CENTER);
        pad.setBackground(padBackground());
        addFullWidth(root, pad, 0, 0, 0, 24);

        Button right = button("Sağ tık"); addFullWidth(root, right, 0, 0, 0, 32);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        check.setOnClickListener(v -> refreshStatus());
        send.setOnClickListener(v -> runRoot(() -> rootBackend.sendText(text.getText().toString())));
        right.setOnClickListener(v -> runRoot(() -> rootBackend.click(2)));
        pad.setOnTouchListener((v, event) -> handleTouch(event));
        refreshStatus();
    }

    private void addFullWidth(LinearLayout parent, android.view.View view, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(left, top, right, bottom);
        parent.addView(view, params);
    }

    private GradientDrawable padBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#F1F5F9"));
        drawable.setCornerRadius(24f);
        drawable.setStroke(2, Color.parseColor("#CBD5E1"));
        return drawable;
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
