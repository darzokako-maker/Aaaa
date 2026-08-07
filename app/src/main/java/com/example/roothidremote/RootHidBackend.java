package com.example.roothidremote;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

final class RootHidBackend {
    private final String keyboardPath;
    private final String mousePath;
    private Process suProcess;
    private DataOutputStream os;

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

    void sendText(String text) throws IOException {
        for (char c : text.toCharArray()) {
            writeHex(keyboardPath, HidReport.keyboard(c, true));
            writeHex(keyboardPath, HidReport.keyboard(c, false));
        }
    }

    void moveMouse(int dx, int dy) throws IOException {
        writeHex(mousePath, HidReport.mouse(0, dx, dy, 0));
    }

    void click(int buttonMask) throws IOException {
        writeHex(mousePath, HidReport.mouse(buttonMask, 0, 0, 0));
        writeHex(mousePath, HidReport.mouse(0, 0, 0, 0));
    }

    private synchronized void writeHex(String device, byte[] bytes) throws IOException {
        if (suProcess == null || os == null) {
            suProcess = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(suProcess.getOutputStream());
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format(Locale.US, "\\x%02x", b & 0xff));
        String command = "printf '" + hex + "' > " + shellQuote(device) + "\n";
        os.writeBytes(command);
        os.flush();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
