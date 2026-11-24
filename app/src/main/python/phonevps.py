import contextlib
import io
import os
import threading
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

_server = None
_server_thread = None
_server_lock = threading.RLock()

def execute(code):
    """Run Python code in the app's private workspace and return stdout/stderr."""
    output = io.StringIO()
    workspace = os.path.join(os.environ.get("HOME", "/"), "projects")
    os.makedirs(workspace, exist_ok=True)
    scope = {"__name__": "__main__", "__builtins__": __builtins__}
    scope["workspace"] = workspace
    try:
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            exec(compile(code, "<PhoneVPS console>", "exec"), scope, scope)
    except Exception as exc:
        print(type(exc).__name__ + ": " + str(exc), file=output)
    result = output.getvalue()
    return result if result else "Executed successfully (no output)."

def start_server(app_files_dir, port=8080):
    global _server, _server_thread
    with _server_lock:
        if _server is not None:
            return "already running"
        web_root = os.path.join(app_files_dir, "www")
        os.makedirs(web_root, exist_ok=True)
        index = os.path.join(web_root, "index.html")
        if not os.path.exists(index):
            with open(index, "w", encoding="utf-8") as f:
                f.write("<!doctype html><meta name='viewport' content='width=device-width'><h1>PhoneVPS is online</h1><p>Your phone is hosting this page.</p>")
        handler = lambda *args, **kwargs: SimpleHTTPRequestHandler(*args, directory=web_root, **kwargs)
        _server = ThreadingHTTPServer(("127.0.0.1", int(port)), handler)
        _server.daemon_threads = True
        _server_thread = threading.Thread(target=_server.serve_forever, name="PhoneVPS-HTTP", daemon=True)
        _server_thread.start()
        return "started"

def stop_server():
    global _server, _server_thread
    with _server_lock:
        if _server is not None:
            _server.shutdown()
            _server.server_close()
            _server = None
            _server_thread = None
        return "stopped"

def server_status():
    return "running" if _server is not None else "stopped"
