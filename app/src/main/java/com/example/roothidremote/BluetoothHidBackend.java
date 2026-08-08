package com.example.roothidremote;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

final class BluetoothHidBackend {
    private static final int KEYBOARD_REPORT_ID = 1;
    private static final int MOUSE_REPORT_ID = 2;
    private final Executor callbackExecutor = Executors.newSingleThreadExecutor();
    private BluetoothAdapter adapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice connectedDevice;
    private BluetoothDevice pendingConnectDevice;
    private boolean appRegistered;
    private boolean profileProxyRequested;
    private String lastStatus = "Bluetooth HID profili henüz açılmadı.";

    boolean isSupported(Context context) {
        if (Build.VERSION.SDK_INT < 28) {
            lastStatus = "Bluetooth HID Device API için Android 9+ gerekir.";
            return false;
        }
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            lastStatus = "Bluetooth adaptörü bulunamadı.";
            return false;
        }
        if (hidDevice != null) return true;
        if (profileProxyRequested) return true;
        try {
            profileProxyRequested = adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile == BluetoothProfile.HID_DEVICE && proxy instanceof BluetoothHidDevice) {
                        hidDevice = (BluetoothHidDevice) proxy;
                        registerAppIfNeeded();
                    }
                }

                @Override public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice = null;
                        appRegistered = false;
                        connectedDevice = null;
                        pendingConnectDevice = null;
                        profileProxyRequested = false;
                        lastStatus = "Bluetooth HID profili kapandı.";
                    }
                }
            }, BluetoothProfile.HID_DEVICE);
            if (!profileProxyRequested) {
                lastStatus = "Bluetooth HID profil proxy isteği başlatılamadı.";
            }
            return profileProxyRequested;
        } catch (SecurityException privilegedMissing) {
            lastStatus = "Bluetooth HID Device API kısıtlı. (Sistem/Privileged izni gerekir)";
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    boolean connectDevice(BluetoothDevice device) {
        if (device == null) {
            lastStatus = "Bağlanılacak Bluetooth cihazı seçilmedi.";
            return false;
        }
        if (!appRegistered) {
            pendingConnectDevice = device;
            return registerAppIfNeeded();
        }
        try {
            boolean commandSent = hidDevice.connect(device);
            lastStatus = commandSent
                    ? "Bluetooth HID bağlantı isteği gönderildi: " + safeAddress(device)
                    : "Bluetooth HID bağlantı isteği gönderilemedi. Cihaz eşleşmiş olmalı.";
            return commandSent;
        } catch (SecurityException missingPermission) {
            lastStatus = "Bluetooth bağlantısı için BLUETOOTH_CONNECT izni eksik.";
            return false;
        } catch (Exception e) {
            lastStatus = "Bluetooth bağlantı hatası: " + e.getMessage();
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    boolean connectByMac(String macAddress) {
        if (adapter == null) adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            lastStatus = "Bluetooth adaptörü bulunamadı.";
            return false;
        }
        String normalizedAddress = macAddress == null ? "" : macAddress.trim().toUpperCase();
        if (!BluetoothAdapter.checkBluetoothAddress(normalizedAddress)) {
            lastStatus = "Geçersiz MAC adresi. Örnek biçim: 00:11:22:AA:BB:CC";
            return false;
        }
        try {
            return connectDevice(adapter.getRemoteDevice(normalizedAddress));
        } catch (IllegalArgumentException e) {
            lastStatus = "Geçersiz MAC adresi: " + normalizedAddress;
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    boolean sendText(String text) {
        if (text == null) text = "";
        for (char c : text.toCharArray()) {
            if (!sendKeyboardReport(HidReport.keyboard(c, true))) return false;
            sleepBetweenReports();
            if (!sendKeyboardReport(HidReport.keyboard(c, false))) return false;
            sleepBetweenReports();
        }
        lastStatus = "Bluetooth klavye raporları gönderildi.";
        return true;
    }

    boolean sendKey(int keyCode, int modifier) {
        if (!sendKeyboardReport(HidReport.keyReport(keyCode, modifier, true))) return false;
        sleepBetweenReports();
        if (!sendKeyboardReport(HidReport.keyReport(keyCode, modifier, false))) return false;
        lastStatus = "Bluetooth klavye tuşu gönderildi.";
        return true;
    }

    boolean moveMouse(int dx, int dy) {
        if (dx == 0 && dy == 0) return true;
        return sendMouseReport(HidReport.mouse(0, dx, dy, 0), "Bluetooth mouse hareketi gönderildi.");
    }

    boolean click(int buttonMask) {
        if (!sendMouseReport(HidReport.mouse(buttonMask, 0, 0, 0), "Bluetooth mouse tık basıldı.")) return false;
        sleepBetweenReports();
        return sendMouseReport(HidReport.mouse(0, 0, 0, 0), "Bluetooth mouse tık bırakıldı.");
    }

    String status() {
        return lastStatus;
    }

    @SuppressLint("MissingPermission")
    private boolean registerAppIfNeeded() {
        if (hidDevice == null) {
            lastStatus = "Bluetooth HID profili hazır değil. Önce durum kontrolü yapın.";
            return false;
        }
        if (appRegistered) return true;
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "Root HID Remote",
                "Keyboard and mouse HID remote",
                "RootHidRemote",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                combinedDescriptor());
        try {
            boolean commandSent = hidDevice.registerApp(sdp, null, null, callbackExecutor, new BluetoothHidDevice.Callback() {
                @Override public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
                    appRegistered = registered;
                    if (registered) {
                        lastStatus = "Bluetooth HID uygulaması kaydedildi. Eşleşmiş cihaza bağlanabilirsiniz.";
                        BluetoothDevice pendingDevice = pendingConnectDevice;
                        pendingConnectDevice = null;
                        if (pendingDevice != null) connectDevice(pendingDevice);
                    } else {
                        pendingConnectDevice = null;
                        lastStatus = "Bluetooth HID uygulaması kaydı kapandı.";
                    }
                }

                @Override public void onConnectionStateChanged(BluetoothDevice device, int state) {
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        connectedDevice = device;
                        lastStatus = "Bluetooth HID bağlı: " + safeAddress(device);
                    } else if (state == BluetoothProfile.STATE_CONNECTING) {
                        lastStatus = "Bluetooth HID bağlanıyor: " + safeAddress(device);
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        if (device != null && device.equals(connectedDevice)) connectedDevice = null;
                        lastStatus = "Bluetooth HID bağlantısı kapalı.";
                    } else if (state == BluetoothProfile.STATE_DISCONNECTING) {
                        lastStatus = "Bluetooth HID bağlantısı kapatılıyor: " + safeAddress(device);
                    }
                }
            });
            if (!commandSent) {
                lastStatus = "Bluetooth HID uygulama kaydı başlatılamadı.";
            } else {
                lastStatus = pendingConnectDevice == null
                        ? "Bluetooth HID uygulama kaydı başlatıldı."
                        : "Bluetooth HID uygulama kaydı başlatıldı; kayıt tamamlanınca bağlantı denenecek.";
            }
            return commandSent;
        } catch (SecurityException privilegedMissing) {
            lastStatus = "Bluetooth HID kaydı için privileged/system izin gerekir.";
            return false;
        } catch (Exception e) {
            lastStatus = "Bluetooth HID kayıt hatası: " + e.getMessage();
            return false;
        }
    }

    private boolean sendKeyboardReport(byte[] report) {
        return sendReport(KEYBOARD_REPORT_ID, report, "Bluetooth klavye raporu gönderildi.");
    }

    private boolean sendMouseReport(byte[] report, String successStatus) {
        return sendReport(MOUSE_REPORT_ID, report, successStatus);
    }

    @SuppressLint("MissingPermission")
    private boolean sendReport(int reportId, byte[] report, String successStatus) {
        if (!appRegistered || connectedDevice == null) {
            lastStatus = "Bluetooth HID bağlı değil. Önce hedef cihaza bağlanın.";
            return false;
        }
        try {
            boolean commandSent = hidDevice.sendReport(connectedDevice, reportId, report);
            lastStatus = commandSent ? successStatus : "Bluetooth HID raporu gönderilemedi.";
            return commandSent;
        } catch (SecurityException missingPermission) {
            lastStatus = "Bluetooth raporu göndermek için BLUETOOTH_CONNECT izni eksik.";
            return false;
        } catch (Exception e) {
            lastStatus = "Bluetooth rapor gönderme hatası: " + e.getMessage();
            return false;
        }
    }

    private void sleepBetweenReports() {
        try {
            Thread.sleep(8);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private byte[] combinedDescriptor() {
        byte[] keyboard = withReportId(HidReport.KEYBOARD_DESCRIPTOR, KEYBOARD_REPORT_ID);
        byte[] mouse = withReportId(HidReport.MOUSE_DESCRIPTOR, MOUSE_REPORT_ID);
        byte[] combined = new byte[keyboard.length + mouse.length];
        System.arraycopy(keyboard, 0, combined, 0, keyboard.length);
        System.arraycopy(mouse, 0, combined, keyboard.length, mouse.length);
        return combined;
    }

    private byte[] withReportId(byte[] descriptor, int reportId) {
        byte[] withId = new byte[descriptor.length + 2];
        System.arraycopy(descriptor, 0, withId, 0, 6);
        withId[6] = (byte) 0x85;
        withId[7] = (byte) reportId;
        System.arraycopy(descriptor, 6, withId, 8, descriptor.length - 6);
        return withId;
    }

    private String safeAddress(BluetoothDevice device) {
        if (device == null) return "bilinmeyen cihaz";
        try {
            return device.getAddress();
        } catch (SecurityException missingPermission) {
            return "adres izni yok";
        }
    }
}
