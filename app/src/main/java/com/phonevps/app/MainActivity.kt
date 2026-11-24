package com.phonevps.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(TermuxBridge(), "PhoneVPS")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    inner class TermuxBridge {
        @JavascriptInterface
        fun isTermuxInstalled(): Boolean {
            return try {
                packageManager.getPackageInfo("com.termux", 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

        @JavascriptInterface
        fun runCommand(command: String, workingDirectory: String, background: Boolean): String {
            if (command.isBlank() || command.length > 4000) return "Invalid command"
            if (!isTermuxInstalled()) return "Termux is not installed"
            return try {
                val intent = Intent("com.termux.RUN_COMMAND").apply {
                    setClassName("com.termux", "com.termux.app.RunCommandService")
                    putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
                    putExtra("com.termux.RUN_COMMAND_WORKDIR", workingDirectory.ifBlank { "/data/data/com.termux/files/home" })
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
                    putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "PhoneVPS")
                }
                startService(intent)
                "Command sent to Termux"
            } catch (e: SecurityException) {
                "Permission denied. Grant PhoneVPS the 'Run commands in Termux environment' permission in Android App settings, and enable allow-external-apps in Termux."
            } catch (e: Exception) {
                "Could not start Termux command: ${e.message}"
            }
        }

        @JavascriptInterface
        fun openTermux(): String {
            return try {
                val launch = packageManager.getLaunchIntentForPackage("com.termux")
                    ?: return "Could not open Termux"
                startActivity(launch)
                "Opening Termux"
            } catch (e: Exception) {
                "Could not open Termux: ${e.message}"
            }
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
