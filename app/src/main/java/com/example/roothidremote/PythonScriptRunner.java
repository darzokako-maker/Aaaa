package com.example.roothidremote;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

final class PythonScriptRunner {
    private static final String[] PYTHON_CANDIDATES = new String[]{
            "python3",
            "python",
            "/data/data/com.termux/files/usr/bin/python3",
            "/data/data/com.termux/files/usr/bin/python",
            "/system/bin/python3",
            "/system/bin/python"
    };

    private PythonScriptRunner() {}

    static Result run(Context context, String script) {
        if (script == null || script.trim().isEmpty()) {
            return new Result(false, "Çalıştırılacak Python kodu boş.");
        }
        try {
            String python = findPythonCommand();
            if (python == null) {
                return new Result(false,
                        "Bu cihazda çalıştırılabilir Python yorumlayıcısı yok. "
                                + "Termux/Pydroid veya sistem python3 kurulduktan sonra script buradan çalıştırılır.");
            }

            File scriptFile = File.createTempFile("root_hid_remote_", ".py", context.getCacheDir());
            try (FileWriter writer = new FileWriter(scriptFile)) {
                writer.write(script);
                writer.write('\n');
            }

            Process process = new ProcessBuilder(python, scriptFile.getAbsolutePath())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Result(false, "Python script zaman aşımına uğradı (30 sn).");
            }
            String output = readOutput(process);
            int exitCode = process.exitValue();
            return new Result(exitCode == 0,
                    (exitCode == 0 ? "Python script çalıştı." : "Python script hata kodu: " + exitCode)
                            + (output.isEmpty() ? "" : "\n\n" + output));
        } catch (Exception e) {
            return new Result(false, "Python script çalıştırma hatası: " + e.getMessage());
        }
    }

    private static String findPythonCommand() {
        for (String candidate : PYTHON_CANDIDATES) {
            if (candidate.contains("/")) {
                if (new File(candidate).canExecute()) return candidate;
            } else if (commandExists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean commandExists(String command) {
        try {
            Process process = new ProcessBuilder("sh", "-c", "command -v " + command)
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) output.append('\n');
                output.append(line);
            }
        }
        return output.toString();
    }

    static final class Result {
        final boolean success;
        final String message;

        Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
