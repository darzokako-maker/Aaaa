package com.example.roothidremote;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

final class PythonScriptRunner {
    private PythonScriptRunner() {}

    static synchronized Result run(Context context, String script) {
        if (script == null || script.trim().isEmpty()) {
            return new Result(false, "Çalıştırılacak Python kodu boş.");
        }
        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(context.getApplicationContext()));
            }
            PyObject result = Python.getInstance()
                    .getModule("script_runner")
                    .callAttr("run_user_script", script);
            boolean success = result.asList().get(0).toBoolean();
            String message = result.asList().get(1).toString();
            return new Result(success, message);
        } catch (Exception e) {
            return new Result(false, "Gömülü Python çalıştırma hatası: " + e.getMessage());
        }
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
