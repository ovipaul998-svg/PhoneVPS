import contextlib
import asyncio
import builtins
import io
import os
import shlex
import shutil
import sys
import threading
import traceback
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

_server = None
_server_thread = None
_server_port = 8080
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
    if root not in sys.path:
        sys.path.insert(0, root)
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
                    "This is an Android shell, not Ubuntu. pip packages and arbitrary Linux binaries are not included.")
        if cmd == "pwd":
            return _current_dir
        if cmd == "whoami":
            return "phonevps"
        if cmd == "uname":
            return "PhoneVPS Android userspace (not Ubuntu; LAN-hosted)"
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
    global _server, _server_thread, _server_port
    with _server_lock:
        target_port = int(port)
        if _server is not None:
            if _server_port == target_port:
                return "already running"
            _server.shutdown()
            _server.server_close()
            _server = None
            _server_thread = None
        root = _set_workspace(os.path.join(app_files_dir, "projects"))
        index = os.path.join(root, "index.html")
        if not os.path.exists(index):
            with open(index, "w", encoding="utf-8") as f:
                f.write("<!doctype html><meta name='viewport' content='width=device-width'><h1>PhoneVPS is online</h1><p>Edit projects/index.html in PhoneVPS.</p>")
        handler = lambda *args, **kwargs: SimpleHTTPRequestHandler(*args, directory=root, **kwargs)
        server = ThreadingHTTPServer(("0.0.0.0", target_port), handler)
        server.daemon_threads = True
        thread = threading.Thread(target=server.serve_forever, name="PhoneVPS-HTTP", daemon=True)
        thread.start()
        _server, _server_thread, _server_port = server, thread, target_port
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


# Managed user-script runner; always reads the file saved in /projects.
_bot_lock = threading.RLock()
_bot_thread = None
_bot_state = "stopped"
_bot_logs = []
_bot_client = None
_bot_loop = None
_bot_stop_requested = threading.Event()

class _BotLogStream:
    def write(self, value):
        if value:
            with _bot_lock:
                _bot_logs.extend(str(value).splitlines())
                del _bot_logs[:-400]
    def flush(self):
        pass

def _read_saved_token(root):
    token_path = os.path.join(os.path.dirname(root), "bot_token.txt")
    if os.path.isfile(token_path):
        try:
            with open(token_path, "r", encoding="utf-8") as handle:
                return handle.read().strip()
        except OSError:
            return ""
    return ""

def bot_status():
    with _bot_lock:
        if _bot_thread is None or not _bot_thread.is_alive():
            return "stopped" if _bot_state in ("running", "restarting") else _bot_state
        return _bot_state

def bot_logs():
    with _bot_lock:
        return "\n".join(_bot_logs[-200:]) or "No bot output yet."

def start_bot(workspace_path, filename="main.py", auto_restart=False):
    global _bot_thread, _bot_state, _bot_client, _bot_loop
    root = _set_workspace(workspace_path)
    target = os.path.abspath(os.path.join(root, filename))
    if target == root or not target.startswith(root + os.sep):
        return "ERROR: Bot file must be inside /projects."
    if not os.path.isfile(target):
        return "ERROR: " + filename + " was not found in PhoneVPS Files."
    with _bot_lock:
        if _bot_thread is not None and _bot_thread.is_alive():
            return "Bot runner is already active."
        _bot_stop_requested.clear()
        _bot_logs.clear()
        _bot_logs.append("Loading saved file: /projects/" + filename)
        _bot_state = "starting"
        _bot_client = None
        _bot_loop = None

    def worker():
        global _bot_state, _bot_client, _bot_loop
        stream = _BotLogStream()
        max_restarts = 3
        restarts = 0
        try:
            while True:
                crashed = False
                try:
                    if root not in sys.path:
                        sys.path.insert(0, root)
                    saved_token = _read_saved_token(root)
                    if saved_token:
                        os.environ["DISCORD_TOKEN"] = saved_token
                        os.environ["BOT_TOKEN"] = saved_token
                    with open(target, "r", encoding="utf-8") as handle:
                        source = handle.read()
                    safe_builtins = dict(vars(builtins))
                    def bot_print(*values, sep=" ", end="\n", file=None, flush=False):
                        stream.write(sep.join(str(value) for value in values) + end)
                    safe_builtins["print"] = bot_print
                    scope = {"__name__": "__main__", "__file__": target,
                             "__builtins__": safe_builtins}
                    try:
                        import discord
                        def managed_run(client, token=None, **kwargs):
                            global _bot_client, _bot_loop, _bot_state
                            resolved_token = token or saved_token or os.environ.get("DISCORD_TOKEN", "")
                            loop = asyncio.new_event_loop()
                            asyncio.set_event_loop(loop)
                            with _bot_lock:
                                _bot_client, _bot_loop = client, loop
                                _bot_state = "running"
                                _bot_logs.append("Discord client connecting…")
                            try:
                                loop.run_until_complete(client.start(resolved_token, reconnect=kwargs.get("reconnect", True)))
                            finally:
                                if not loop.is_closed():
                                    pending = asyncio.all_tasks(loop)
                                    for task in pending:
                                        task.cancel()
                                    if pending:
                                        loop.run_until_complete(asyncio.gather(*pending, return_exceptions=True))
                                    loop.close()
                                asyncio.set_event_loop(None)
                        discord.Client.run = managed_run
                    except ImportError:
                        with _bot_lock:
                            _bot_logs.append("ERROR: discord.py is not installed in this APK.")
                    exec(compile(source, target, "exec"), scope, scope)
                    with _bot_lock:
                        if _bot_state not in ("error", "stopping"):
                            _bot_state = "stopped"
                        _bot_logs.append("Bot script exited.")
                except BaseException:
                    if _bot_stop_requested.is_set():
                        break
                    crashed = True
                    with _bot_lock:
                        _bot_state = "error"
                        _bot_logs.extend(traceback.format_exc().splitlines())
                        del _bot_logs[:-400]

                if crashed and auto_restart and not _bot_stop_requested.is_set() and restarts < max_restarts:
                    restarts += 1
                    with _bot_lock:
                        _bot_state = "restarting"
                        _bot_logs.append(f"Bot crashed. Auto-restarting in 5s (attempt {restarts}/{max_restarts})…")
                    if _bot_stop_requested.wait(5.0):
                        break
                    with _bot_lock:
                        _bot_state = "starting"
                        _bot_client = None
                        _bot_loop = None
                    continue
                break
        finally:
            with _bot_lock:
                if _bot_stop_requested.is_set() or _bot_state == "stopping":
                    _bot_state = "stopped"
                _bot_client = None
                _bot_loop = None

    _bot_thread = threading.Thread(target=worker, name="PhoneVPS-Bot", daemon=True)
    _bot_thread.start()
    return "Started saved file /projects/" + filename

def stop_bot():
    global _bot_state
    with _bot_lock:
        _bot_stop_requested.set()
        if _bot_thread is None or not _bot_thread.is_alive():
            _bot_state = "stopped"
            return "Bot is already stopped."
        _bot_state = "stopping"
        if _bot_client is not None and _bot_loop is not None and not _bot_loop.is_closed():
            try:
                asyncio.run_coroutine_threadsafe(_bot_client.close(), _bot_loop)
                return "Stop requested for Discord client."
            except Exception as exc:
                _bot_logs.append("Stop error: " + str(exc))
        return "Stop requested. A script that blocks without a stoppable client may require app restart."
