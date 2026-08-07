package com.example.roothidremote;

import android.content.Context;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    String runPythonScript(Context context, String script) {
        if (script == null || script.trim().isEmpty()) {
            return "Çalıştırılacak Python script boş.";
        }
        File scriptFile = new File(context.getCacheDir(), "user_script.py");
        try (FileOutputStream outputStream = new FileOutputStream(scriptFile)) {
            outputStream.write(script.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return "Script dosyası yazılamadı: " + e.getMessage();
        }

        String command = "PY=$(command -v python3 || command -v python); "
                + "if [ -z \"$PY\" ]; then echo 'python3 veya python bulunamadı'; exit 127; fi; "
                + "\"$PY\" " + shellQuote(scriptFile.getAbsolutePath());
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            String output = readProcessOutput(process);
            int code = process.waitFor();
            if (output.trim().isEmpty()) output = "(çıktı yok)";
            return "Çıkış kodu: " + code + "\n" + output;
        } catch (Exception e) {
            return "Python script çalıştırılamadı: " + e.getMessage();
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
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
