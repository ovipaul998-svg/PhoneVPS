package com.phonevps.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.chaquo.python.android.AndroidPlatform
import com.chaquo.python.Python
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.NetworkInterface
import java.net.Inet4Address

class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(AppBridge(), "PhoneVPS")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    inner class AppBridge {
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
        fun runCommand(command: String): String = try {
            ensurePython()
            Python.getInstance().getModule("phonevps").callAttr("execute_command", command, File(filesDir, "projects").absolutePath).toString()
        } catch (e: Exception) {
            "Command error: ${e.message}"
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
                connection = URL("http://127.0.0.1:${PhoneVPSService.SERVER_PORT}/").openConnection() as HttpURLConnection
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
        fun listFiles(folder: String): String = try {
            val dir = safeWorkspacePath(folder)
            dir.mkdirs()
            val arr = JSONArray()
            dir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { item ->
                val obj = org.json.JSONObject()
                obj.put("name", item.name)
                obj.put("directory", item.isDirectory)
                obj.put("size", item.length())
                arr.put(obj)
            }
            arr.toString()
        } catch (e: Exception) { "[]" }

        @JavascriptInterface
        fun readFile(path: String): String = try {
            val file = safeWorkspacePath(path)
            if (file.isFile) file.readText() else ""
        } catch (e: Exception) { "File error: ${e.message}" }

        @JavascriptInterface
        fun saveFile(path: String, content: String): String = try {
            val file = safeWorkspacePath(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            "Saved: ${file.name}"
        } catch (e: Exception) { "File error: ${e.message}" }

        @JavascriptInterface
        fun createFolder(path: String): String = try {
            val dir = safeWorkspacePath(path)
            if (dir.exists()) "Already exists" else if (dir.mkdirs()) "Folder created" else "Could not create folder"
        } catch (e: Exception) { "Folder error: ${e.message}" }

        @JavascriptInterface
        fun deletePath(path: String): String = try {
            val file = safeWorkspacePath(path)
            if (!file.exists()) "Not found" else if (file.deleteRecursively()) "Deleted" else "Could not delete"
        } catch (e: Exception) { "Delete error: ${e.message}" }

        private fun safeWorkspacePath(path: String): File {
            require(!path.startsWith("/") && !path.split("/").contains("..")) { "Invalid path" }
            val root = File(filesDir, "projects").canonicalFile
            val file = File(root, path).canonicalFile
            require(file == root || file.path.startsWith(root.path + File.separator)) { "Path outside workspace" }
            return file
        }

        private fun ensurePython() {
            if (!Python.isStarted()) Python.start(AndroidPlatform(this@MainActivity))
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
