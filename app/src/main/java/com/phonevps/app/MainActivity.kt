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
            Python.getInstance().getModule("phonevps").callAttr("execute", code).toString()
        } catch (e: Exception) {
            "Python error: ${e.message}"
        }

        @JavascriptInterface
        fun startHosting(): String = try {
            val intent = Intent(this@MainActivity, PhoneVPSService::class.java)
                .setAction(PhoneVPSService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            "Hosting service start requested. Check the persistent PhoneVPS notification."
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
        fun hostingStatus(): String = if (PhoneVPSService.running) "Running" else "Stopped"

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
