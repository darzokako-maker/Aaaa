package com.example.roothidremote;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

final class RootHidBackend {
    private final String keyboardPath;
    private final String mousePath;
    
    private Process suProcess;
    private DataOutputStream suOut;

    RootHidBackend(String keyboardPath, String mousePath) {
        this.keyboardPath = keyboardPath;
        this.mousePath = mousePath;
    }

    boolean canUseRoot() {
        try {
            Process p = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private synchronized void initSu() throws IOException {
        if (suProcess == null || suOut == null) {
            suProcess = Runtime.getRuntime().exec("su");
            suOut = new DataOutputStream(suProcess.getOutputStream());
        }
    }

    void sendText(String text) throws IOException, InterruptedException {
        for (char c : text.toCharArray()) {
            writeHex(keyboardPath, HidReport.keyboard(c, true));
            writeHex(keyboardPath, HidReport.keyboard(c, false));
        }
    }

    void sendKey(int keyCode, int modifier) throws IOException, InterruptedException {
        writeHex(keyboardPath, HidReport.keyReport(keyCode, modifier, true));
        writeHex(keyboardPath, HidReport.keyReport(keyCode, modifier, false));
    }

    void moveMouse(int dx, int dy) throws IOException, InterruptedException {
        if (dx == 0 && dy == 0) return;
        writeHex(mousePath, HidReport.mouse(0, dx, dy, 0));
    }

    void click(int buttonMask) throws IOException, InterruptedException {
        writeHex(mousePath, HidReport.mouse(buttonMask, 0, 0, 0));
        writeHex(mousePath, HidReport.mouse(0, 0, 0, 0));
    }
    
    void close() {
        if (suOut != null) {
            try { suOut.close(); } catch (Exception ignored) {}
        }
        if (suProcess != null) {
            suProcess.destroy();
        }
        suProcess = null;
        suOut = null;
    }

    private synchronized void writeHex(String device, byte[] bytes) throws IOException, InterruptedException {
        initSu();
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format(Locale.US, "\\x%02x", b & 0xff));
        String command = "printf '" + hex + "' > " + shellQuote(device) + "\n";
        suOut.writeBytes(command);
        suOut.flush();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
