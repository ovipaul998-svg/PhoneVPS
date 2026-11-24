package com.phonevps.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.chaquo.python.AndroidPlatform
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
        fun filesList(): String = try {
            val root = File(filesDir, "projects")
            root.mkdirs()
            JSONArray(root.listFiles()?.map { it.name } ?: emptyList<String>()).toString()
        } catch (e: Exception) { "[]" }

        @JavascriptInterface
        fun readProjectFile(name: String): String = try {
            val f = safeProjectFile(name)
            if (f.isFile) f.readText() else ""
        } catch (e: Exception) { "File error: ${e.message}" }

        @JavascriptInterface
        fun writeProjectFile(name: String, content: String): String = try {
            val f = safeProjectFile(name)
            f.parentFile?.mkdirs()
            f.writeText(content)
            "Saved ${f.name}"
        } catch (e: Exception) { "File error: ${e.message}" }

        private fun safeProjectFile(name: String): File {
            require(name.isNotBlank() && !name.contains("..") && !name.startsWith("/")) { "Invalid file name" }
            val root = File(filesDir, "projects").canonicalFile
            val file = File(root, name).canonicalFile
            require(file.path.startsWith(root.path + File.separator)) { "Path outside project folder" }
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
