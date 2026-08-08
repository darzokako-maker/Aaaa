import contextlib
import io
import traceback


def run_user_script(script, bridge):
    stdout = io.StringIO()
    stderr = io.StringIO()
    def type_text(text):
        bridge.typeText(str(text))

    def move_mouse(dx=0, dy=0):
        bridge.moveMouse(int(dx), int(dy))

    def click(button="left"):
        if str(button).lower() in ("right", "2"):
            bridge.rightClick()
        else:
            bridge.click()

    def right_click():
        bridge.rightClick()

    def key(name):
        bridge.key(str(name))

    def sleep(ms):
        bridge.sleepMs(int(ms))

    namespace = {
        "__name__": "__main__",
        "hid": bridge,
        "type_text": type_text,
        "move_mouse": move_mouse,
        "click": click,
        "right_click": right_click,
        "key": key,
        "sleep": sleep,
    }
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
