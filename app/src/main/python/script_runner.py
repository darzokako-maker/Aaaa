import contextlib
import io
import traceback


def run_user_script(script):
    stdout = io.StringIO()
    stderr = io.StringIO()
    namespace = {"__name__": "__main__"}
    try:
        code = compile(script, "<root_hid_remote_script>", "exec")
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            exec(code, namespace, namespace)
        output = stdout.getvalue()
        error_output = stderr.getvalue()
        message = "Python script çalıştı."
        if output or error_output:
            message += "\n\n" + output + error_output
        return True, message
    except Exception:
        output = stdout.getvalue()
        error = stderr.getvalue() + traceback.format_exc()
        message = "Python script hata verdi."
        if output:
            message += "\n\nÇıktı:\n" + output
        message += "\n\nHata:\n" + error
        return False, message
