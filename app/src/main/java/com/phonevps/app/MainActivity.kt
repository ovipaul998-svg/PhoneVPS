package com.phonevps.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

class MainActivity : Activity() {
    private lateinit var webView: WebView
    @Volatile private var pendingUploadFolder: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
        }
        webView = WebView(this)
        webView.fitsSystemWindows = true
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.e("PhoneVPS-Web", message.message() + " -- line " + message.lineNumber() + " of " + message.sourceId())
                return true
            }
        }
        webView.addJavascriptInterface(AppBridge(), "PhoneVPS")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_REQUEST_CODE) {
            val uri = if (resultCode == RESULT_OK) data?.data else null
            if (uri == null) {
                notifyUploadResult("Upload cancelled")
                return
            }
            val targetFolder = pendingUploadFolder
            Thread {
                try {
                    val rawName = queryDisplayName(uri) ?: "uploaded_file"
                    val cleanName = File(rawName).name.ifBlank { "uploaded_file" }
                    val relPath = if (targetFolder.isBlank()) cleanName else "$targetFolder/$cleanName"
                    val dest = safeWorkspaceFile(relPath)
                    dest.parentFile?.mkdirs()
                    contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Could not open selected file")
                    notifyUploadResult("Uploaded: $cleanName")
                } catch (e: Exception) {
                    notifyUploadResult("Upload error: ${e.message}")
                }
            }.start()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    private fun notifyUploadResult(message: String) {
        if (!::webView.isInitialized) return
        val quoted = JSONObject.quote(message)
        webView.post {
            webView.evaluateJavascript("window.onNativeFileUploaded && window.onNativeFileUploaded($quoted);", null)
        }
    }

    private fun safeWorkspaceFile(path: String): File {
        require(!path.startsWith("/") && !path.split("/").contains("..")) { "Invalid path" }
        val root = File(filesDir, "projects").canonicalFile
        val file = File(root, path).canonicalFile
        require(file == root || file.path.startsWith(root.path + File.separator)) { "Path outside workspace" }
        return file
    }

    inner class AppBridge {
        @JavascriptInterface
        fun rustReady(): Boolean = RustBridge.available

        @JavascriptInterface
        fun rustVersion(): String = if (RustBridge.available) {
            try { RustBridge.nativeVersion() } catch (e: Throwable) { "Rust error: ${e.message}" }
        } else {
            "Unavailable"
        }

        @JavascriptInterface
        fun getEngine(): String = PhoneVPSService.getConfiguredEngine(this@MainActivity)

        @JavascriptInterface
        fun getActiveEngine(): String = PhoneVPSService.activeEngine

        @JavascriptInterface
        fun setEngine(engine: String): String = try {
            PhoneVPSService.setConfiguredEngine(this@MainActivity, engine)
            val label = if (engine.trim().equals(PhoneVPSService.ENGINE_RUST, true)) "Rust Native" else "Python 3.11"
            if (PhoneVPSService.running) "Engine set to $label. Restart server to apply." else "Engine set to $label."
        } catch (e: Exception) {
            "Engine error: ${e.message}"
        }

        @JavascriptInterface
        fun pythonReady(): Boolean = try {
            ensurePython()
            true
        } catch (_: Exception) {
            false
        }

        @JavascriptInterface
        fun runPython(code: String): String = try {
            ensurePython()
            Python.getInstance().getModule("phonevps").callAttr("execute", code, File(filesDir, "projects").absolutePath).toString()
        } catch (e: Exception) {
            "Python error: ${e.message}"
        }

        @JavascriptInterface
        fun runCommand(command: String): String {
            return try {
                val projectsPath = File(filesDir, "projects").apply { mkdirs() }.absolutePath
                val engine = PhoneVPSService.getConfiguredEngine(this@MainActivity)
                if (engine == PhoneVPSService.ENGINE_RUST && RustBridge.available) {
                    val rustOut = RustBridge.nativeExecuteCommand(command, projectsPath)
                    if (rustOut != "__USE_PYTHON__") return rustOut
                }
                ensurePython()
                Python.getInstance().getModule("phonevps")
                    .callAttr("execute_command", command, projectsPath).toString()
            } catch (e: Throwable) {
                Log.e("PhoneVPS", "Terminal command failed", e)
                "Command error: " + e.javaClass.simpleName + ": " + e.message
            }
        }

        @JavascriptInterface
        fun saveBotToken(token: String): String = try {
            val tokenFile = File(filesDir, "bot_token.txt")
            val trimmed = token.trim()
            if (trimmed.isEmpty()) {
                if (tokenFile.exists()) tokenFile.delete()
                "Bot token cleared."
            } else {
                tokenFile.writeText(trimmed)
                "Bot token saved securely (outside /projects)."
            }
        } catch (e: Exception) {
            "Token save error: ${e.message}"
        }

        @JavascriptInterface
        fun hasBotToken(): Boolean = try {
            val tokenFile = File(filesDir, "bot_token.txt")
            tokenFile.isFile && tokenFile.readText().isNotBlank()
        } catch (_: Exception) {
            false
        }

        @JavascriptInterface
        fun startBot(filename: String, autoRestart: Boolean): String = try {
            val serviceIntent = Intent(this@MainActivity, PhoneVPSService::class.java).setAction(PhoneVPSService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
            ensurePython()
            Python.getInstance().getModule("phonevps")
                .callAttr("start_bot", File(filesDir, "projects").absolutePath, filename, autoRestart).toString()
        } catch (e: Throwable) {
            "Bot start error: ${e.javaClass.simpleName}: ${e.message}"
        }

        @JavascriptInterface
        fun stopBot(): String = try {
            ensurePython()
            Python.getInstance().getModule("phonevps").callAttr("stop_bot").toString()
        } catch (e: Throwable) {
            "Bot stop error: ${e.javaClass.simpleName}: ${e.message}"
        }

        @JavascriptInterface
        fun botStatus(): String = try {
            ensurePython()
            Python.getInstance().getModule("phonevps").callAttr("bot_status").toString()
        } catch (e: Throwable) { "error: ${e.message}" }

        @JavascriptInterface
        fun botLogs(): String = try {
            ensurePython()
            Python.getInstance().getModule("phonevps").callAttr("bot_logs").toString()
        } catch (e: Throwable) { "Bot log error: ${e.message}" }

        @JavascriptInterface
        fun getServerPort(): Int = PhoneVPSService.getConfiguredPort(this@MainActivity)

        @JavascriptInterface
        fun getActivePort(): Int = PhoneVPSService.activePort

        @JavascriptInterface
        fun setServerPort(port: Int): String = try {
            PhoneVPSService.setConfiguredPort(this@MainActivity, port)
            if (PhoneVPSService.running) "Port $port saved. Restart server to apply." else "Port $port saved."
        } catch (e: Exception) {
            "Invalid port: ${e.message}"
        }

        @JavascriptInterface
        fun startHosting(): String = try {
            val intent = Intent(this@MainActivity, PhoneVPSService::class.java).setAction(PhoneVPSService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            "Starting server…"
        } catch (e: Exception) {
            "Could not start hosting: ${e.message}"
        }

        @JavascriptInterface
        fun stopHosting(): String = try {
            startService(Intent(this@MainActivity, PhoneVPSService::class.java).setAction(PhoneVPSService.ACTION_STOP))
            "Stop requested."
        } catch (e: Exception) {
            "Could not stop hosting: ${e.message}"
        }

        @JavascriptInterface
        fun lanAddress(): String = try {
            val addresses = NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
            addresses.firstOrNull()?.hostAddress ?: "Connect to Wi-Fi to get a LAN address"
        } catch (_: Exception) { "LAN address unavailable" }

        @JavascriptInterface
        fun hostingStatus(): String = when {
            PhoneVPSService.lastError.isNotEmpty() -> "Error: " + PhoneVPSService.lastError
            PhoneVPSService.running && isHttpServerResponding() -> "Online"
            PhoneVPSService.running -> "Starting / Not responding"
            else -> "Offline"
        }

        private fun isHttpServerResponding(): Boolean {
            var connection: HttpURLConnection? = null
            return try {
                connection = URL("http://127.0.0.1:${PhoneVPSService.activePort}/").openConnection() as HttpURLConnection
                connection.connectTimeout = 700
                connection.readTimeout = 700
                connection.requestMethod = "GET"
                connection.responseCode in 200..299
            } catch (_: Exception) {
                false
            } finally {
                connection?.disconnect()
            }
        }

        @JavascriptInterface
        fun pickFileToUpload(folder: String) {
            try {
                safeWorkspaceFile(folder)
                pendingUploadFolder = folder
                runOnUiThread {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    startActivityForResult(intent, FILE_PICKER_REQUEST_CODE)
                }
            } catch (e: Exception) {
                notifyUploadResult("Upload error: ${e.message}")
            }
        }

        @JavascriptInterface
        fun listFiles(folder: String): String = try {
            val dir = safeWorkspaceFile(folder)
            dir.mkdirs()
            val arr = JSONArray()
            dir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { item ->
                val obj = JSONObject()
                obj.put("name", item.name)
                obj.put("directory", item.isDirectory)
                obj.put("size", item.length())
                arr.put(obj)
            }
            arr.toString()
        } catch (e: Exception) { "[]" }

        @JavascriptInterface
        fun readFile(path: String): String = try {
            val file = safeWorkspaceFile(path)
            if (file.isFile) file.readText() else ""
        } catch (e: Exception) { "File error: ${e.message}" }

        @JavascriptInterface
        fun saveFile(path: String, content: String): String = try {
            val file = safeWorkspaceFile(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            "Saved: ${file.name}"
        } catch (e: Exception) { "File error: ${e.message}" }

        @JavascriptInterface
        fun createFolder(path: String): String = try {
            val dir = safeWorkspaceFile(path)
            if (dir.exists()) "Already exists" else if (dir.mkdirs()) "Folder created" else "Could not create folder"
        } catch (e: Exception) { "Folder error: ${e.message}" }

        @JavascriptInterface
        fun deletePath(path: String): String = try {
            val file = safeWorkspaceFile(path)
            if (!file.exists()) "Not found" else if (file.deleteRecursively()) "Deleted" else "Could not delete"
        } catch (e: Exception) { "Delete error: ${e.message}" }

        private fun ensurePython() {
            if (!Python.isStarted()) Python.start(AndroidPlatform(this@MainActivity))
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    companion object {
        private const val FILE_PICKER_REQUEST_CODE = 1001
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002
    }
}
