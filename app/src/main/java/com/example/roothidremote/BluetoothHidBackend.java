package com.example.roothidremote;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

final class BluetoothHidBackend {
    private final Executor executor = Executors.newSingleThreadExecutor();
    private BluetoothAdapter adapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice connectedDevice;
    private boolean appRegistered;
    private String lastStatus = "Bluetooth hazır değil.";

    boolean initialize(Context context) {
        if (Build.VERSION.SDK_INT < 28) {
            lastStatus = "Bluetooth HID Device API Android 9+ ister.";
            return false;
        }
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            lastStatus = "Bu cihazda Bluetooth adapter bulunamadı.";
            return false;
        }
        try {
            return adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile == BluetoothProfile.HID_DEVICE && proxy instanceof BluetoothHidDevice) {
                        hidDevice = (BluetoothHidDevice) proxy;
                        registerApp();
                    }
                }
                @Override public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice = null;
                        connectedDevice = null;
                        appRegistered = false;
                        lastStatus = "Bluetooth HID profili kapandı.";
                    }
                }
            }, BluetoothProfile.HID_DEVICE);
        } catch (SecurityException privilegedMissing) {
            lastStatus = privilegedMessage();
            return false;
        }
    }

    List<BluetoothDevice> bondedDevices() {
        List<BluetoothDevice> devices = new ArrayList<>();
        if (adapter == null) return devices;
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) devices.addAll(bonded);
        } catch (SecurityException e) {
            lastStatus = "Bluetooth cihaz listesi için BLUETOOTH_CONNECT izni gerekli.";
        }
        return devices;
    }

    boolean startDiscovery() {
        if (adapter == null) return false;
        try {
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            return adapter.startDiscovery();
        } catch (SecurityException e) {
            lastStatus = "Bluetooth taraması için BLUETOOTH_SCAN izni gerekli.";
            return false;
        }
    }

    void stopDiscovery() {
        if (adapter == null) return;
        try {
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
        } catch (SecurityException ignored) {
            // Status is updated by start/connect paths where the user can act on it.
        }
    }

    boolean connect(BluetoothDevice device) {
        if (device == null) {
            lastStatus = "Önce bir Bluetooth cihazı seç.";
            return false;
        }
        if (hidDevice == null || !appRegistered) {
            lastStatus = privilegedMessage();
            return false;
        }
        try {
            boolean started = hidDevice.connect(device);
            lastStatus = started ? "Bağlanma isteği gönderildi: " + safeName(device) : "Bağlanma başlatılamadı.";
            return started;
        } catch (SecurityException e) {
            lastStatus = privilegedMessage();
            return false;
        }
    }

    boolean isConnected() {
        return hidDevice != null && connectedDevice != null;
    }

    void sendText(String text) {
        for (char c : text.toCharArray()) {
            sendKeyboardReport(HidReport.keyboard(c, true));
            sendKeyboardReport(HidReport.keyboard(c, false));
        }
    }

    void moveMouse(int dx, int dy) {
        sendMouseReport(HidReport.mouse(0, dx, dy, 0));
    }

    void click(int buttonMask) {
        sendMouseReport(HidReport.mouse(buttonMask, 0, 0, 0));
        sendMouseReport(HidReport.mouse(0, 0, 0, 0));
    }

    String status() {
        if (hidDevice == null) return lastStatus;
        return appRegistered ? lastStatus : privilegedMessage();
    }

    static String safeName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? device.getAddress() : name + " (" + device.getAddress() + ")";
        } catch (SecurityException e) {
            return "izin gerekli";
        }
    }

    private void registerApp() {
        if (hidDevice == null) return;
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "Root HID Remote", "Android keyboard and mouse", "RootHidRemote", (byte) 0xC0,
                combinedDescriptor());
        try {
            hidDevice.registerApp(sdp, null, null, executor, new BluetoothHidDevice.Callback() {
                @Override public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
                    appRegistered = registered;
                    lastStatus = registered ? "Bluetooth HID uygulaması kayıtlı. Cihaz seçip bağlanabilirsin." : privilegedMessage();
                }
                @Override public void onConnectionStateChanged(BluetoothDevice device, int state) {
                    if (state == BluetoothProfile.STATE_CONNECTED) connectedDevice = device;
                    if (state == BluetoothProfile.STATE_DISCONNECTED && connectedDevice != null && connectedDevice.equals(device)) connectedDevice = null;
                    lastStatus = "Bluetooth HID durumu: " + state + " / " + safeName(device);
                }
            });
        } catch (SecurityException e) {
            appRegistered = false;
            lastStatus = privilegedMessage();
        }
    }

    private static byte[] combinedDescriptor() {
        return HidReport.BLUETOOTH_DESCRIPTOR;
    }

    private void sendKeyboardReport(byte[] report) {
        sendReport(1, report);
    }

    private void sendMouseReport(byte[] report) {
        sendReport(2, report);
    }

    private void sendReport(int reportId, byte[] report) {
        if (!isConnected()) {
            lastStatus = "Bluetooth HID bağlı değil; önce cihaz seçip bağlan.";
            return;
        }
        try {
            hidDevice.sendReport(connectedDevice, reportId, report);
        } catch (SecurityException e) {
            lastStatus = privilegedMessage();
        }
    }

    private static String privilegedMessage() {
        return "Bluetooth HID bağlanma çoğu ROM'da BLUETOOTH_PRIVILEGED/system app ister. Root varsa APK'yı privileged kur veya izni ROM tarafında ver.";
    }
}
