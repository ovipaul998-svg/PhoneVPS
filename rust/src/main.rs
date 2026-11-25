use std::env;
use std::io::{self, BufRead, Write};

use phonevps_core::core;

fn main() {
    let mut args = env::args().skip(1);
    let mut port: u16 = 8080;
    let mut workspace = String::from("./projects");

    while let Some(arg) = args.next() {
        match arg.as_str() {
            "--port" => {
                if let Some(val) = args.next() {
                    if let Ok(parsed) = val.parse::<u16>() {
                        port = parsed;
                    }
                }
            }
            "--dir" => {
                if let Some(val) = args.next() {
                    workspace = val;
                }
            }
            _ => {}
        }
    }

    match core::start_server(&workspace, port) {
        Ok(state) => println!("PhoneVPS Rust HTTP server {state} on http://0.0.0.0:{port} (root: {workspace})"),
        Err(err) => {
            eprintln!("Failed to start server: {err}");
            std::process::exit(1);
        }
    }

    println!("Type 'help' for shell commands or 'exit' to stop.");
    let stdin = io::stdin();
    let mut stdout = io::stdout();
    for line in stdin.lock().lines() {
        let Ok(cmd) = line else { break };
        let trimmed = cmd.trim();
        if trimmed == "exit" || trimmed == "quit" {
            break;
        }
        let output = core::execute_command(trimmed, &workspace);
        if !output.is_empty() {
            println!("{output}");
        }
        print!("> ");
        let _ = stdout.flush();
    }

    core::stop_server();
    println!("Stopped.");
}
