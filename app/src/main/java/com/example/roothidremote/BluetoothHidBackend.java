package com.example.roothidremote;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

final class BluetoothHidBackend {
    private BluetoothHidDevice hidDevice;

    boolean isSupported(Context context) {
        if (Build.VERSION.SDK_INT < 28) return false;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return false;
        try {
            return adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int profile, BluetoothProfile proxy) { 
                    if (proxy instanceof BluetoothHidDevice) hidDevice = (BluetoothHidDevice) proxy; 
                }
                @Override public void onServiceDisconnected(int profile) { hidDevice = null; }
            }, BluetoothProfile.HID_DEVICE);
        } catch (SecurityException privilegedMissing) {
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    boolean connectDevice(BluetoothDevice device) {
        if (hidDevice == null || device == null) return false;
        try {
            return hidDevice.connect(device);
        } catch (Exception e) {
            return false;
        }
    }

    String status() {
        return hidDevice == null
                ? "Bluetooth HID Device API kısıtlı. (Sistem/Privileged izni gerekir)"
                : "Bluetooth HID profili açık. Hedef cihazla eşleşip bağlantı kurabilirsiniz.";
    }
}
