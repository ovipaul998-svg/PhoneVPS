pub mod core;

use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;

fn get_string(env: &mut JNIEnv, input: &JString) -> String {
    env.get_string(input)
        .map(|s| s.to_string_lossy().into_owned())
        .unwrap_or_default()
}

fn to_jstring(env: JNIEnv, value: &str) -> jstring {
    env.new_string(value)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_phonevps_app_RustBridge_nativeVersion<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    to_jstring(env, concat!("PhoneVPS Rust Core v", env!("CARGO_PKG_VERSION")))
}

#[no_mangle]
pub extern "system" fn Java_com_phonevps_app_RustBridge_nativeStartServer<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    workspace_path: JString<'local>,
    port: jint,
) -> jstring {
    let path = get_string(&mut env, &workspace_path);
    let result = match core::start_server(&path, port as u16) {
        Ok(msg) => msg,
        Err(err) => format!("Error: {err}"),
    };
    to_jstring(env, &result)
}

#[no_mangle]
pub extern "system" fn Java_com_phonevps_app_RustBridge_nativeStopServer<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let result = core::stop_server();
    to_jstring(env, &result)
}

#[no_mangle]
pub extern "system" fn Java_com_phonevps_app_RustBridge_nativeServerStatus<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    to_jstring(env, core::server_status())
}

#[no_mangle]
pub extern "system" fn Java_com_phonevps_app_RustBridge_nativeExecuteCommand<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    command: JString<'local>,
    workspace_path: JString<'local>,
) -> jstring {
    let cmd = get_string(&mut env, &command);
    let path = get_string(&mut env, &workspace_path);
    let result = core::execute_command(&cmd, &path);
    to_jstring(env, &result)
}
