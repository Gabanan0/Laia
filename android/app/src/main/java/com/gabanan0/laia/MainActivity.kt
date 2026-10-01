package com.gabanan0.laia

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val laiaUrl = "https://gabanan0.github.io/Laia/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        webView.addJavascriptInterface(LaiaAndroidBridge(this), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val trusted = request.origin?.scheme == "https" &&
                    request.origin?.host == "gabanan0.github.io"
                val wantsMic = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                if (trusted && wantsMic &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                ) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    request.deny()
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return if (uri.scheme == "https" && uri.host == "gabanan0.github.io") {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                }
            }
        }

        val startUrl = intent?.data?.toString()?.takeIf {
            it.startsWith("https://gabanan0.github.io/Laia/")
        } ?: laiaUrl
        webView.loadUrl(startUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.takeIf {
            it.startsWith("https://gabanan0.github.io/Laia/")
        }?.let(webView::loadUrl)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

class LaiaAndroidBridge(private val activity: Activity) {
    @JavascriptInterface
    fun setAlarm(hour: Int, minute: Int, label: String) {
        if (hour !in 0..23 || minute !in 0..59) return
        activity.runOnUiThread {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.take(120))
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            launch(intent, "Keine Wecker-App gefunden.")
        }
    }

    @JavascriptInterface
    fun setTimer(seconds: Int, label: String) {
        if (seconds !in 1..86400) return
        activity.runOnUiThread {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.take(120))
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            launch(intent, "Keine Timer-App gefunden.")
        }
    }

    @JavascriptInterface
    fun openApp(app: String) {
        activity.runOnUiThread {
            when (app.lowercase()) {
                "whatsapp" -> launchPackage("com.whatsapp", "WhatsApp ist nicht installiert.")
                "spotify" -> launchPackage("com.spotify.music", "Spotify ist nicht installiert.")
                "maps" -> launchPackage("com.google.android.apps.maps", "Google Maps ist nicht installiert.")
                "camera" -> launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE), "Keine Kamera-App gefunden.")
                "settings" -> launch(Intent(Settings.ACTION_SETTINGS), "Einstellungen konnten nicht geöffnet werden.")
                "clock" -> launch(Intent(AlarmClock.ACTION_SHOW_ALARMS), "Keine Uhr-App gefunden.")
            }
        }
    }

    private fun launchPackage(packageName: String, error: String) {
        val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) activity.startActivity(intent) else toast(error)
    }

    private fun launch(intent: Intent, error: String) {
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
        } else {
            toast(error)
        }
    }

    private fun toast(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }
}
