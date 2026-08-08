package com.example.roothidremote;

import java.util.ArrayList;
import java.util.List;

public final class HidPythonBridge {
    private final RootHidBackend rootBackend;
    private final BluetoothHidBackend bluetoothBackend;

    // AĞ (Network) kısmını buradan kaldırdık çünkü Ağ artık sadece bir Alıcı (Dinleyici).
    public HidPythonBridge(RootHidBackend rootBackend, BluetoothHidBackend bluetoothBackend) {
        this.rootBackend = rootBackend;
        this.bluetoothBackend = bluetoothBackend;
    }

    public void typeText(String text) throws Exception {
        String value = text == null ? "" : text;
        runOnAvailableBackends(
                () -> rootBackend.sendText(value),
                () -> bluetoothBackend.sendText(value));
    }

    public void moveMouse(int dx, int dy) throws Exception {
        runOnAvailableBackends(
                () -> rootBackend.moveMouse(dx, dy),
                () -> bluetoothBackend.moveMouse(dx, dy));
    }

    public void click() throws Exception {
        clickButton(1);
    }

    public void rightClick() throws Exception {
        clickButton(2);
    }

    public void clickButton(int buttonMask) throws Exception {
        runOnAvailableBackends(
                () -> rootBackend.click(buttonMask),
                () -> bluetoothBackend.click(buttonMask));
    }

    public void key(String name) throws Exception {
        int keyCode = keyCodeForName(name);
        runOnAvailableBackends(
                () -> rootBackend.sendKey(keyCode, 0),
                () -> bluetoothBackend.sendKey(keyCode, 0));
    }

    public void sleepMs(long milliseconds) throws InterruptedException {
        Thread.sleep(Math.max(0L, milliseconds));
    }

    private void runOnAvailableBackends(ThrowingAction rootAction, ThrowingBooleanAction bluetoothAction) throws Exception {
        List<String> errors = new ArrayList<>();
        boolean sent = false;
        try {
            rootAction.run();
            sent = true;
        } catch (Exception e) {
            errors.add("root: " + e.getMessage());
        }
        try {
            sent = bluetoothAction.run() || sent;
        } catch (Exception e) {
            errors.add("bluetooth: " + e.getMessage());
        }
        if (!sent) {
            throw new IllegalStateException("HID gönderimi başarısız: " + String.join("; ", errors));
        }
    }

    private int keyCodeForName(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase();
        switch (normalized) {
            case "enter": return HidReport.KeyCode.ENTER;
            case "esc":
            case "escape": return HidReport.KeyCode.ESCAPE;
            case "backspace": return HidReport.KeyCode.BACKSPACE;
            case "tab": return HidReport.KeyCode.TAB;
            case "space": return HidReport.KeyCode.SPACE;
            case "delete":
            case "del": return HidReport.KeyCode.DELETE;
            case "right":
            case "arrowright": return HidReport.KeyCode.RIGHT;
            case "left":
            case "arrowleft": return HidReport.KeyCode.LEFT;
            case "down":
            case "arrowdown": return HidReport.KeyCode.DOWN;
            case "up":
            case "arrowup": return HidReport.KeyCode.UP;
            default: throw new IllegalArgumentException("Bilinmeyen tuş: " + name);
        }
    }

    private interface ThrowingAction { void run() throws Exception; }
    private interface ThrowingBooleanAction { boolean run() throws Exception; }
}
