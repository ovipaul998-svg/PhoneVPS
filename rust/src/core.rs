use std::fs;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::path::{Component, Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::Duration;

struct ServerHandle {
    stop: Arc<AtomicBool>,
    port: u16,
    thread: Option<JoinHandle<()>>,
}

static SERVER_STATE: Mutex<Option<ServerHandle>> = Mutex::new(None);
static WORKSPACE_ROOT: Mutex<Option<PathBuf>> = Mutex::new(None);
static CURRENT_DIR: Mutex<Option<PathBuf>> = Mutex::new(None);

fn set_workspace(path: &str) -> Result<PathBuf, String> {
    let raw = PathBuf::from(path);
    fs::create_dir_all(&raw).map_err(|e| format!("Could not create workspace: {e}"))?;
    let canonical = fs::canonicalize(&raw).unwrap_or(raw);
    if let Ok(mut guard) = WORKSPACE_ROOT.lock() {
        *guard = Some(canonical.clone());
    }
    if let Ok(mut cur) = CURRENT_DIR.lock() {
        let valid = cur.as_ref().map(|p| p.is_dir()).unwrap_or(false);
        if !valid {
            *cur = Some(canonical.clone());
        }
    }
    Ok(canonical)
}

fn safe_path(raw: &str, root: &Path) -> Result<PathBuf, String> {
    let base = CURRENT_DIR
        .lock()
        .ok()
        .and_then(|g| g.clone())
        .filter(|p| p.starts_with(root) && p.is_dir())
        .unwrap_or_else(|| root.to_path_buf());

    let candidate_input = if raw.is_empty() {
        base
    } else {
        let p = Path::new(raw);
        if p.is_absolute() {
            return Err("Path outside PhoneVPS workspace".to_string());
        }
        base.join(p)
    };

    let mut normalized = PathBuf::new();
    for comp in candidate_input.components() {
        match comp {
            Component::Prefix(pre) => normalized.push(pre.as_os_str()),
            Component::RootDir => normalized.push(Component::RootDir.as_os_str()),
            Component::CurDir => {}
            Component::ParentDir => {
                if !normalized.pop() {
                    return Err("Path outside PhoneVPS workspace".to_string());
                }
            }
            Component::Normal(seg) => normalized.push(seg),
        }
    }

    if normalized != root && !normalized.starts_with(root) {
        return Err("Path outside PhoneVPS workspace".to_string());
    }
    Ok(normalized)
}

fn stop_server_locked(guard: &mut Option<ServerHandle>) -> String {
    if let Some(mut handle) = guard.take() {
        handle.stop.store(true, Ordering::SeqCst);
        let _ = TcpStream::connect_timeout(
            &std::net::SocketAddr::from(([127, 0, 0, 1], handle.port)),
            Duration::from_millis(250),
        );
        if let Some(join) = handle.thread.take() {
            let _ = join.join();
        }
    }
    "stopped".to_string()
}

pub fn start_server(workspace_path: &str, port: u16) -> Result<String, String> {
    let mut guard = SERVER_STATE
        .lock()
        .map_err(|_| "Server lock poisoned".to_string())?;
    if let Some(existing) = guard.as_ref() {
        if existing.port == port && !existing.stop.load(Ordering::SeqCst) {
            return Ok("already running".to_string());
        }
        stop_server_locked(&mut guard);
    }

    let root = set_workspace(workspace_path)?;
    let index_path = root.join("index.html");
    if !index_path.exists() {
        let _ = fs::write(
            &index_path,
            "<!doctype html><meta name='viewport' content='width=device-width'><h1>PhoneVPS Rust Server is online</h1><p>Edit projects/index.html in PhoneVPS.</p>",
        );
    }

    let listener = TcpListener::bind(("0.0.0.0", port))
        .map_err(|e| format!("Could not bind port {port}: {e}"))?;
    let stop = Arc::new(AtomicBool::new(false));
    let stop_clone = Arc::clone(&stop);
    let root_clone = root.clone();

    let thread = thread::Builder::new()
        .name("PhoneVPS-Rust-HTTP".to_string())
        .spawn(move || {
            for stream in listener.incoming() {
                if stop_clone.load(Ordering::SeqCst) {
                    break;
                }
                if let Ok(stream) = stream {
                    let req_root = root_clone.clone();
                    thread::spawn(move || handle_client(stream, &req_root));
                }
            }
        })
        .map_err(|e| format!("Failed to spawn HTTP thread: {e}"))?;

    *guard = Some(ServerHandle {
        stop,
        port,
        thread: Some(thread),
    });
    Ok("started".to_string())
}

pub fn stop_server() -> String {
    match SERVER_STATE.lock() {
        Ok(mut guard) => stop_server_locked(&mut guard),
        Err(_) => "stopped".to_string(),
    }
}

pub fn server_status() -> &'static str {
    match SERVER_STATE.lock() {
        Ok(guard) => match guard.as_ref() {
            Some(h) if !h.stop.load(Ordering::SeqCst) => "running",
            _ => "stopped",
        },
        Err(_) => "stopped",
    }
}

fn handle_client(mut stream: TcpStream, root: &Path) {
    let _ = stream.set_read_timeout(Some(Duration::from_secs(5)));
    let _ = stream.set_write_timeout(Some(Duration::from_secs(10)));

    let mut buf = [0u8; 4096];
    let n = match stream.read(&mut buf) {
        Ok(n) if n > 0 => n,
        _ => return,
    };
    let req = String::from_utf8_lossy(&buf[..n]);
    let first_line = req.lines().next().unwrap_or("");
    let mut parts = first_line.split_whitespace();
    let method = parts.next().unwrap_or("");
    let raw_target = parts.next().unwrap_or("/");

    if method != "GET" && method != "HEAD" {
        write_response(&mut stream, "405 Method Not Allowed", "text/plain; charset=utf-8", b"Method Not Allowed", method == "HEAD");
        return;
    }

    let path_only = raw_target.split(['?', '#']).next().unwrap_or("/");
    let decoded = percent_decode(path_only);
    let rel = decoded.trim_start_matches('/');

    let mut candidate = root.to_path_buf();
    for seg in rel.split('/').filter(|s| !s.is_empty() && *s != ".") {
        if seg == ".." {
            write_response(&mut stream, "403 Forbidden", "text/plain; charset=utf-8", b"Forbidden", method == "HEAD");
            return;
        }
        candidate.push(seg);
    }

    if candidate.is_dir() {
        let index = candidate.join("index.html");
        if index.is_file() {
            candidate = index;
        } else {
            let listing = render_dir_listing(&candidate, &decoded);
            write_response(&mut stream, "200 OK", "text/html; charset=utf-8", listing.as_bytes(), method == "HEAD");
            return;
        }
    }

    let canonical = match fs::canonicalize(&candidate) {
        Ok(c) if c.starts_with(root) && c.is_file() => c,
        _ => {
            write_response(&mut stream, "404 Not Found", "text/plain; charset=utf-8", b"404 Not Found", method == "HEAD");
            return;
        }
    };

    match fs::read(&canonical) {
        Ok(bytes) => {
            let mime = content_type_for(&canonical);
            write_response(&mut stream, "200 OK", mime, &bytes, method == "HEAD");
        }
        Err(_) => {
            write_response(&mut stream, "500 Internal Server Error", "text/plain; charset=utf-8", b"Read Error", method == "HEAD");
        }
    }
}

fn write_response(stream: &mut TcpStream, status: &str, content_type: &str, body: &[u8], head_only: bool) {
    let header = format!(
        "HTTP/1.1 {status}\r\nContent-Type: {content_type}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
        body.len()
    );
    let _ = stream.write_all(header.as_bytes());
    if !head_only {
        let _ = stream.write_all(body);
    }
    let _ = stream.flush();
}

fn render_dir_listing(dir: &Path, req_path: &str) -> String {
    let mut items = Vec::new();
    if let Ok(entries) = fs::read_dir(dir) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            let is_dir = entry.path().is_dir();
            items.push((name, is_dir));
        }
    }
    items.sort_by(|a, b| a.0.to_lowercase().cmp(&b.0.to_lowercase()));
    let mut html = format!("<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'><title>Index of {req_path}</title><h2>Index of {req_path}</h2><ul>");
    for (name, is_dir) in items {
        let suffix = if is_dir { "/" } else { "" };
        html.push_str(&format!("<li><a href='{name}{suffix}'>{name}{suffix}</a></li>"));
    }
    html.push_str("</ul>");
    html
}

fn content_type_for(path: &Path) -> &'static str {
    match path.extension().and_then(|e| e.to_str()).unwrap_or("").to_ascii_lowercase().as_str() {
        "html" | "htm" => "text/html; charset=utf-8",
        "css" => "text/css; charset=utf-8",
        "js" | "mjs" => "application/javascript; charset=utf-8",
        "json" => "application/json; charset=utf-8",
        "svg" => "image/svg+xml",
        "png" => "image/png",
        "jpg" | "jpeg" => "image/jpeg",
        "gif" => "image/gif",
        "ico" => "image/x-icon",
        "txt" | "md" | "py" | "rs" | "toml" | "yml" | "yaml" => "text/plain; charset=utf-8",
        _ => "application/octet-stream",
    }
}

fn percent_decode(input: &str) -> String {
    let bytes = input.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let (Some(h), Some(l)) = (hex_val(bytes[i + 1]), hex_val(bytes[i + 2])) {
                out.push((h << 4) | l);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn hex_val(b: u8) -> Option<u8> {
    match b {
        b'0'..=b'9' => Some(b - b'0'),
        b'a'..=b'f' => Some(b - b'a' + 10),
        b'A'..=b'F' => Some(b - b'A' + 10),
        _ => None,
    }
}

fn split_shell_args(raw: &str) -> Result<Vec<String>, String> {
    let mut args = Vec::new();
    let mut current = String::new();
    let mut quote: Option<char> = None;
    let mut escape = false;
    let mut token_started = false;

    for ch in raw.chars() {
        if escape {
            current.push(ch);
            escape = false;
            token_started = true;
            continue;
        }
        match quote {
            Some(q) if ch == q => {
                quote = None;
            }
            Some(_) => {
                if ch == '\\' && quote == Some('"') {
                    escape = true;
                } else {
                    current.push(ch);
                }
            }
            None => match ch {
                '\\' => {
                    escape = true;
                    token_started = true;
                }
                '\'' | '"' => {
                    quote = Some(ch);
                    token_started = true;
                }
                c if c.is_whitespace() => {
                    if token_started {
                        args.push(std::mem::take(&mut current));
                        token_started = false;
                    }
                }
                _ => {
                    current.push(ch);
                    token_started = true;
                }
            },
        }
    }
    if escape {
        return Err("Parse error: trailing backslash".to_string());
    }
    if quote.is_some() {
        return Err("Parse error: unclosed quote".to_string());
    }
    if token_started {
        args.push(current);
    }
    Ok(args)
}

pub fn execute_command(command: &str, workspace_path: &str) -> String {
    let root = match set_workspace(workspace_path) {
        Ok(r) => r,
        Err(e) => return e,
    };
    let raw = command.trim();
    if raw.is_empty() {
        return String::new();
    }
    if raw == "clear" || raw == "cls" {
        return "__CLEAR__".to_string();
    }
    let parts = match split_shell_args(raw) {
        Ok(p) if !p.is_empty() => p,
        Ok(_) => return String::new(),
        Err(e) => return e,
    };
    let cmd = parts[0].as_str();
    let args = &parts[1..];

    match cmd {
        "help" | "?" => concat!(
            "PhoneVPS Rust Native Shell:\n",
            "pwd, ls [path], cd [path], mkdir [-p] name, touch name, ",
            "cat file, rm [-r] path, echo text > file, python file.py, ",
            "whoami, uname, clear, help\n",
            "Running in native Rust mode. Switch to Python engine or run `python` for Python 3.11."
        )
        .to_string(),
        "pwd" => CURRENT_DIR
            .lock()
            .ok()
            .and_then(|g| g.clone())
            .unwrap_or(root)
            .display()
            .to_string(),
        "whoami" => "phonevps (rust)".to_string(),
        "uname" => format!("PhoneVPS Rust Native Core v{}", env!("CARGO_PKG_VERSION")),
        "ls" => {
            let target = match safe_path(args.first().map(String::as_str).unwrap_or("."), &root) {
                Ok(p) => p,
                Err(e) => return e,
            };
            if !target.is_dir() {
                return if target.exists() {
                    target.file_name().map(|n| n.to_string_lossy().into_owned()).unwrap_or_default()
                } else {
                    "No such file or directory".to_string()
                };
            }
            match fs::read_dir(&target) {
                Ok(rd) => {
                    let mut names: Vec<String> = rd
                        .flatten()
                        .map(|e| e.file_name().to_string_lossy().into_owned())
                        .collect();
                    names.sort();
                    if names.is_empty() {
                        "(empty directory)".to_string()
                    } else {
                        names.join("\n")
                    }
                }
                Err(e) => format!("ls error: {e}"),
            }
        }
        "cd" => {
            let arg = args.first().map(String::as_str).unwrap_or("");
            let target = if arg.is_empty() {
                root.clone()
            } else {
                match safe_path(arg, &root) {
                    Ok(p) => p,
                    Err(e) => return e,
                }
            };
            if !target.is_dir() {
                return format!("cd: no such directory: {arg}");
            }
            if let Ok(mut cur) = CURRENT_DIR.lock() {
                *cur = Some(target.clone());
            }
            target.display().to_string()
        }
        "mkdir" => {
            let recursive = args.iter().any(|a| a == "-p");
            let vals: Vec<&String> = args.iter().filter(|a| a.as_str() != "-p").collect();
            if vals.is_empty() {
                return "mkdir: missing folder name".to_string();
            }
            for val in vals {
                let target = match safe_path(val, &root) {
                    Ok(p) => p,
                    Err(e) => return e,
                };
                let res = if recursive {
                    fs::create_dir_all(&target)
                } else {
                    fs::create_dir(&target)
                };
                if let Err(e) = res {
                    return format!("mkdir error: {e}");
                }
            }
            "Folder created".to_string()
        }
        "touch" => {
            if args.is_empty() {
                return "touch: missing file name".to_string();
            }
            for val in args {
                let target = match safe_path(val, &root) {
                    Ok(p) => p,
                    Err(e) => return e,
                };
                if let Some(parent) = target.parent() {
                    let _ = fs::create_dir_all(parent);
                }
                if let Err(e) = fs::OpenOptions::new().create(true).append(true).open(&target) {
                    return format!("touch error: {e}");
                }
            }
            "File created".to_string()
        }
        "cat" => {
            let Some(file) = args.first() else {
                return "cat: missing file name".to_string();
            };
            let target = match safe_path(file, &root) {
                Ok(p) => p,
                Err(e) => return e,
            };
            match fs::read_to_string(&target) {
                Ok(text) => text,
                Err(e) => format!("cat error: {e}"),
            }
        }
        "rm" => {
            let recursive = args.iter().any(|a| a == "-r" || a == "-rf");
            let vals: Vec<&String> = args
                .iter()
                .filter(|a| !matches!(a.as_str(), "-r" | "-rf" | "-f"))
                .collect();
            if vals.is_empty() {
                return "rm: missing path".to_string();
            }
            for val in vals {
                let target = match safe_path(val, &root) {
                    Ok(p) => p,
                    Err(e) => return e,
                };
                if target == root {
                    return "rm: cannot remove workspace root".to_string();
                }
                if target.is_dir() {
                    if !recursive {
                        return "rm: is a directory (use rm -r)".to_string();
                    }
                    if let Err(e) = fs::remove_dir_all(&target) {
                        return format!("rm error: {e}");
                    }
                } else if target.exists() {
                    if let Err(e) = fs::remove_file(&target) {
                        return format!("rm error: {e}");
                    }
                }
            }
            "Removed".to_string()
        }
        "echo" => {
            if let Some(pos) = args.iter().position(|a| a == ">") {
                if pos + 1 >= args.len() {
                    return "echo: missing output file".to_string();
                }
                let text = args[..pos].join(" ");
                let target = match safe_path(&args[pos + 1], &root) {
                    Ok(p) => p,
                    Err(e) => return e,
                };
                if let Some(parent) = target.parent() {
                    let _ = fs::create_dir_all(parent);
                }
                match fs::write(&target, format!("{text}\n")) {
                    Ok(_) => format!("Wrote {}", args[pos + 1]),
                    Err(e) => format!("echo error: {e}"),
                }
            } else {
                args.join(" ")
            }
        }
        "python" | "python3" | "py" => "__USE_PYTHON__".to_string(),
        _ => format!("{cmd}: command not found. Type 'help' for supported commands."),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn temp_workspace(name: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("phonevps_test_{}_{}", name, std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn shell_rejects_traversal() {
        let ws = temp_workspace("traversal");
        let root = ws.to_string_lossy().into_owned();
        let res = execute_command("cat ../../etc/passwd", &root);
        assert!(res.contains("outside PhoneVPS workspace"));
        let _ = fs::remove_dir_all(&ws);
    }

    #[test]
    fn shell_file_ops_work() {
        let ws = temp_workspace("ops");
        let root = ws.to_string_lossy().into_owned();
        assert_eq!(execute_command("mkdir -p app", &root), "Folder created");
        assert_eq!(execute_command("echo hello > app/hello.txt", &root), "Wrote app/hello.txt");
        assert_eq!(execute_command("cat app/hello.txt", &root), "hello\n");
        assert_eq!(execute_command("rm -r app", &root), "Removed");
        let _ = fs::remove_dir_all(&ws);
    }

    #[test]
    fn http_server_serves_and_stops() {
        let ws = temp_workspace("http");
        let root = ws.to_string_lossy().into_owned();
        let probe = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let port = probe.local_addr().unwrap().port();
        drop(probe);

        assert_eq!(start_server(&root, port).unwrap(), "started");
        assert_eq!(server_status(), "running");

        let mut client = TcpStream::connect(("127.0.0.1", port)).unwrap();
        client.write_all(b"GET / HTTP/1.1\r\nHost: localhost\r\n\r\n").unwrap();
        let mut resp = String::new();
        client.read_to_string(&mut resp).unwrap();
        assert!(resp.contains("200 OK"));
        assert!(resp.contains("PhoneVPS Rust Server is online"));

        assert_eq!(stop_server(), "stopped");
        assert_eq!(server_status(), "stopped");
        let _ = fs::remove_dir_all(&ws);
    }
}
