package com.example.roothidremote;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

final class RootHidBackend {
    private final String keyboardPath;
    private final String mousePath;

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

    void sendText(String text) throws IOException, InterruptedException {
        for (char c : text.toCharArray()) {
            writeHex(keyboardPath, HidReport.keyboard(c, true));
            writeHex(keyboardPath, HidReport.keyboard(c, false));
        }
    }

    void moveMouse(int dx, int dy) throws IOException, InterruptedException {
        writeHex(mousePath, HidReport.mouse(0, dx, dy, 0));
    }

    void click(int buttonMask) throws IOException, InterruptedException {
        writeHex(mousePath, HidReport.mouse(buttonMask, 0, 0, 0));
        writeHex(mousePath, HidReport.mouse(0, 0, 0, 0));
    }

    private void writeHex(String device, byte[] bytes) throws IOException, InterruptedException {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format(Locale.US, "\\x%02x", b & 0xff));
        String command = "printf '" + hex + "' > " + shellQuote(device);
        Process process = Runtime.getRuntime().exec("su");
        try (DataOutputStream os = new DataOutputStream(process.getOutputStream())) {
            os.writeBytes(command + "\nexit\n");
        }
        int code = process.waitFor();
        if (code != 0) throw new IOException("su write failed with exit " + code);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
