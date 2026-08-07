package com.example.roothidremote;

import android.bluetooth.BluetoothAdapter;
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
                @Override public void onServiceConnected(int profile, BluetoothProfile proxy) { if (proxy instanceof BluetoothHidDevice) hidDevice = (BluetoothHidDevice) proxy; }
                @Override public void onServiceDisconnected(int profile) { hidDevice = null; }
            }, BluetoothProfile.HID_DEVICE);
        } catch (SecurityException privilegedMissing) {
            return false;
        }
    }

    String status() {
        return hidDevice == null
                ? "Bluetooth HID Device API requires a privileged/system app signature on most Android builds. Root alone is not enough unless you install as a privileged app or use a custom ROM."
                : "Bluetooth HID profile proxy opened. Pair from the target device, then send keyboard/mouse reports.";
    }
}
