package com.phonevps.app

object RustBridge {
    val available: Boolean = try {
        System.loadLibrary("phonevps_core")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    external fun nativeVersion(): String
    external fun nativeStartServer(workspacePath: String, port: Int): String
    external fun nativeStopServer(): String
    external fun nativeServerStatus(): String
    external fun nativeExecuteCommand(command: String, workspacePath: String): String
}
