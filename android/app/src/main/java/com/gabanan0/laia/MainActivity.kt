package com.gabanan0.laia

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.media.MediaRecorder
import android.util.Base64
import android.webkit.WebSettings
import java.io.File
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
    private var nativeRecorder: MediaRecorder? = null
    private var nativeRecordingFile: File? = null
    private var nativeRecordingMode: String = "voice"
    private val laiaUrl = "https://gabanan0.github.io/Laia/"
    private val appVersion = "0.3.0"

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
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE

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
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url.startsWith(laiaUrl)) injectLaiaFace()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "laia" && uri.host == "auth") {
                    view.loadUrl(resolveIntentUrl(Intent(Intent.ACTION_VIEW, uri)) ?: laiaUrl)
                    return true
                }
                return if (uri.scheme == "https" && uri.host == "gabanan0.github.io") {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                }
            }
        }

        webView.clearCache(true)
        webView.loadUrl(resolveIntentUrl(intent) ?: (laiaUrl + "?app=" + appVersion))
    }

    private fun resolveIntentUrl(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme == "laia" && uri.host == "auth") {
            val existingQuery = uri.encodedQuery
            val query = buildString {
                append("?app=").append(appVersion)
                if (!existingQuery.isNullOrBlank()) append("&").append(existingQuery)
                append("&native_auth=").append(System.currentTimeMillis())
            }
            val fragment = uri.encodedFragment?.let { "#$it" } ?: ""
            return laiaUrl + query + fragment
        }
        val raw = uri.toString()
        return raw.takeIf { it.startsWith(laiaUrl) }?.let {
            if (it.contains("?")) it + "&app=" + appVersion else it + "?app=" + appVersion
        }
    }

    private fun injectLaiaFace() {
        val faceData = LaiaFace.dataUrl()
        val js = """
            (() => {
              const face = document.querySelector('#laia');
              if (!face) return;
              let style = document.querySelector('#laiaNativeFaceStyle');
              if (!style) {
                style = document.createElement('style');
                style.id = 'laiaNativeFaceStyle';
                style.textContent = `
                  .face{width:min(72vw,310px)!important;height:min(72vw,310px)!important;flex:0 0 min(72vw,310px)!important;margin-top:clamp(70px,9vh,105px)!important;border-radius:50%!important;position:relative!important;overflow:hidden!important;background:#070d2c!important}
                  .face .brow,.face .eye,.face .mouth{display:none!important}
                  #laiaNativePortrait{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;border-radius:50%;image-rendering:auto;transition:filter .22s ease!important}
                  #laiaNativeMouth{position:absolute;left:37%;top:67.3%;width:26%;height:8.2%;background:#07113b;border-radius:2px;overflow:hidden;z-index:3;opacity:.98}
                  #laiaNativeMouth:before{content:'';position:absolute;left:15%;top:28%;width:70%;height:18%;background:#ffe21d;box-shadow:0 4px 0 #ff5a13}
                  #laiaNativeMouth:after{content:'';position:absolute;left:24%;top:58%;width:52%;height:12%;background:#f2f2f2}
                  .face.talking #laiaNativeMouth{animation:laiaMouth .16s steps(2,end) infinite alternate}
                  .face.happy #laiaNativeMouth{height:7%;top:68%;transform:scaleX(1.08)}
                  .face.skeptical #laiaNativeMouth{transform:rotate(-3deg) scaleX(.78)}
                  .face.surprised #laiaNativeMouth{left:43%;width:14%;height:11%;top:66%;border-radius:50%}
                  .face.blink #laiaNativePortrait{filter:brightness(.78)}
                  @keyframes laiaMouth{from{height:5.8%;top:68.1%}to{height:11.5%;top:65.8%}}
                `;
                document.head.appendChild(style);
              }
              face.innerHTML = '';
              const img = document.createElement('img');
              img.id = 'laiaNativePortrait';
              img.alt = 'LAIA';
              img.src = '""" + faceData + """';
              const mouth = document.createElement('div');
              mouth.id = 'laiaNativeMouth';
              face.appendChild(img);
              face.appendChild(mouth);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    fun startNativeRecording(mode: String): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            return false
        }
        if (nativeRecorder != null) return false
        return try {
            nativeRecordingMode = if (mode == "music") "music" else "voice"
            val file = File(cacheDir, "laia_${nativeRecordingMode}_${System.currentTimeMillis()}.m4a")
            nativeRecordingFile = file
            nativeRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            nativeRecorder?.release()
            nativeRecorder = null
            nativeRecordingFile?.delete()
            nativeRecordingFile = null
            runOnUiThread { Toast.makeText(this, "Mikrofon konnte nicht gestartet werden.", Toast.LENGTH_SHORT).show() }
            false
        }
    }

    fun stopNativeRecording(): Boolean {
        val recorder = nativeRecorder ?: return false
        nativeRecorder = null
        return try {
            recorder.stop()
            recorder.release()
            val file = nativeRecordingFile ?: return false
            nativeRecordingFile = null
            val bytes = file.readBytes()
            file.delete()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mode = nativeRecordingMode
            runOnUiThread {
                webView.evaluateJavascript(
                    "window.receiveNativeRecording && window.receiveNativeRecording('$base64','audio/mp4','$mode')",
                    null
                )
            }
            true
        } catch (e: Exception) {
            try { recorder.release() } catch (_: Exception) {}
            nativeRecordingFile?.delete()
            nativeRecordingFile = null
            false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveIntentUrl(intent)?.let(webView::loadUrl)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

class LaiaAndroidBridge(private val activity: MainActivity) {
    @JavascriptInterface
    fun startRecording(mode: String): Boolean = activity.startNativeRecording(mode)

    @JavascriptInterface
    fun stopRecording(): Boolean = activity.stopNativeRecording()

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
