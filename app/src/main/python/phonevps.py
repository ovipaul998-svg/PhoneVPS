import contextlib
import io
import os
import shlex
import shutil
import threading
import traceback
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

_server = None
_server_thread = None
_server_lock = threading.RLock()
_workspace = None
_current_dir = None

def _set_workspace(path):
    global _workspace, _current_dir
    _workspace = os.path.abspath(path)
    os.makedirs(_workspace, exist_ok=True)
    if _current_dir is None or not os.path.isdir(_current_dir):
        _current_dir = _workspace
    return _workspace

def _safe_path(path):
    if not path:
        path = _current_dir or _workspace
    candidate = os.path.abspath(os.path.join(_current_dir or _workspace, path))
    if candidate != _workspace and not candidate.startswith(_workspace + os.sep):
        raise ValueError("Path outside PhoneVPS workspace")
    return candidate

def execute(code, workspace_path=None):
    output = io.StringIO()
    root = _set_workspace(workspace_path or os.path.join(os.environ.get("HOME", "/"), "projects"))
    scope = {"__name__": "__main__", "__builtins__": __builtins__, "workspace": root}
    try:
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            exec(compile(code, "<PhoneVPS Python>", "exec"), scope, scope)
    except Exception:
        traceback.print_exc(file=output)
    result = output.getvalue()
    return result if result else "Executed successfully (no output)."

def execute_command(command, workspace_path):
    global _current_dir
    root = _set_workspace(workspace_path)
    raw = command.strip()
    if not raw:
        return ""
    if raw in ("clear", "cls"):
        return "__CLEAR__"
    try:
        parts = shlex.split(raw)
    except ValueError as exc:
        return "Parse error: " + str(exc)
    if not parts:
        return ""
    cmd, args = parts[0], parts[1:]
    try:
        if cmd in ("help", "?"):
            return ("PhoneVPS built-in shell commands:\n"
                    "pwd, ls [path], cd [path], mkdir [-p] name, touch name, "
                    "cat file, rm [-r] path, echo text > file, python file.py, "
                    "python -c \"code\", whoami, uname, clear, help\n"
                    "Python packages and arbitrary Linux binaries are not yet included.")
        if cmd == "pwd":
            return _current_dir
        if cmd == "whoami":
            return "phonevps"
        if cmd == "uname":
            return "PhoneVPS Android userspace (not Ubuntu)"
        if cmd == "ls":
            target = _safe_path(args[0] if args else ".")
            if not os.path.isdir(target):
                return os.path.basename(target) if os.path.exists(target) else "No such file or directory"
            entries = sorted(os.listdir(target))
            return "\n".join(entries) if entries else "(empty directory)"
        if cmd == "cd":
            target = _safe_path(args[0] if args else root)
            if not os.path.isdir(target):
                return "cd: no such directory: " + (args[0] if args else "")
            _current_dir = target
            return _current_dir
        if cmd == "mkdir":
            vals = [x for x in args if x != "-p"]
            if not vals: return "mkdir: missing folder name"
            for val in vals:
                target = _safe_path(val)
                os.makedirs(target, exist_ok="-p" in args)
            return "Folder created"
        if cmd == "touch":
            if not args: return "touch: missing file name"
            for val in args:
                target = _safe_path(val)
                os.makedirs(os.path.dirname(target), exist_ok=True)
                open(target, "a").close()
            return "File created"
        if cmd == "cat":
            if not args: return "cat: missing file name"
            with open(_safe_path(args[0]), "r", encoding="utf-8") as f:
                return f.read()
        if cmd == "rm":
            vals = [x for x in args if x not in ("-r", "-rf", "-f")]
            if not vals: return "rm: missing path"
            for val in vals:
                target = _safe_path(val)
                if os.path.isdir(target):
                    if "-r" not in args and "-rf" not in args: return "rm: is a directory (use rm -r)"
                    shutil.rmtree(target)
                elif os.path.exists(target):
                    os.remove(target)
            return "Removed"
        if cmd == "echo":
            if ">" in args:
                i = args.index(">")
                text = " ".join(args[:i])
                if i + 1 >= len(args): return "echo: missing output file"
                target = _safe_path(args[i+1])
                os.makedirs(os.path.dirname(target), exist_ok=True)
                with open(target, "w", encoding="utf-8") as f: f.write(text + "\n")
                return "Wrote " + args[i+1]
            return " ".join(args)
        if cmd in ("python", "python3", "py"):
            if not args: return "Python 3.11 embedded runtime"
            if args[0] == "-c":
                return execute(" ".join(args[1:]), root)
            script = _safe_path(args[0])
            if not os.path.isfile(script): return "python: file not found: " + args[0]
            with open(script, "r", encoding="utf-8") as f: source = f.read()
            return execute(source, root)
        if cmd == "df":
            total, used, free = shutil.disk_usage(root)
            return "Workspace storage: total %d MB | used %d MB | free %d MB" % (total//1048576, used//1048576, free//1048576)
        return cmd + ": command not found. Type 'help' for supported commands."
    except Exception as exc:
        return type(exc).__name__ + ": " + str(exc)

def start_server(app_files_dir, port=8080):
    global _server, _server_thread
    with _server_lock:
        if _server is not None:
            return "already running"
        root = _set_workspace(os.path.join(app_files_dir, "projects"))
        index = os.path.join(root, "index.html")
        if not os.path.exists(index):
            with open(index, "w", encoding="utf-8") as f:
                f.write("<!doctype html><meta name='viewport' content='width=device-width'><h1>PhoneVPS is online</h1><p>Edit projects/index.html in PhoneVPS.</p>")
        handler = lambda *args, **kwargs: SimpleHTTPRequestHandler(*args, directory=root, **kwargs)
        server = ThreadingHTTPServer(("127.0.0.1", int(port)), handler)
        server.daemon_threads = True
        thread = threading.Thread(target=server.serve_forever, name="PhoneVPS-HTTP", daemon=True)
        thread.start()
        _server, _server_thread = server, thread
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
    return "running" if _server is not None and _server_thread is not None and _server_thread.is_alive() else "stopped"
