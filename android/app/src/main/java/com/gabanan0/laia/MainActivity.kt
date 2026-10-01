package com.gabanan0.laia

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var nativeRecorder: MediaRecorder? = null
    private var nativeRecordingFile: File? = null
    private var nativeRecordingMode: String = "voice"

    private var wakeRecognizer: SpeechRecognizer? = null
    private var wakeEnabled = true
    private var wakeListening = false
    private var isResumed = false
    private val wakeHandler = Handler(Looper.getMainLooper())

    private var pendingTorchState: Boolean? = null

    private val laiaUrl = "https://gabanan0.github.io/Laia/"
    private val appVersion = "0.4.0"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.settings.textZoom = 100
        webView.settings.setSupportZoom(false)

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
                if (url.startsWith(laiaUrl)) {
                    injectLaiaFace()
                    setupWakeWord()
                    resumeWakeWord(1000)
                }
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
            if (it.contains("?")) "$it&app=$appVersion" else "$it?app=$appVersion"
        }
    }

    private fun injectLaiaFace() {
        val faceData = LaiaFace.dataUrl()
        val js = """
            (() => {
              document.body.classList.add('native-app');
              const face = document.querySelector('#laia');
              if (!face) return;

              let style = document.querySelector('#laiaNativeFaceStyle');
              if (!style) {
                style = document.createElement('style');
                style.id = 'laiaNativeFaceStyle';
                style.textContent = `
                  .native-app .face{
                    width:min(74vw,330px)!important;
                    height:min(74vw,330px)!important;
                    flex:0 0 min(74vw,330px)!important;
                    margin-top:clamp(78px,10vh,118px)!important;
                    border-radius:50%!important;
                    position:relative!important;
                    overflow:hidden!important;
                    background:#09113b!important;
                  }
                  .native-app .face .brow,.native-app .face .eye,.native-app .face .mouth{display:none!important}
                  #laiaNativePortrait{
                    position:absolute;inset:0;width:100%;height:100%;
                    object-fit:cover;border-radius:50%;image-rendering:auto;
                    transform:none!important;filter:none;
                  }
                  #laiaNativeMouth{
                    position:absolute;left:43%;top:69.2%;width:14%;height:3px;
                    background:#ffe11a;z-index:4;
                    box-shadow:0 4px 0 #ff5311;
                    transform-origin:center;
                    opacity:.96;
                  }
                  #laiaNativeBlink{position:absolute;inset:0;z-index:5;pointer-events:none}
                  #laiaNativeBlink:before,#laiaNativeBlink:after{
                    content:'';position:absolute;top:49.2%;width:18%;height:0;
                    background:#09113b;opacity:0;transition:height .07s ease,opacity .04s;
                  }
                  #laiaNativeBlink:before{left:24.5%}
                  #laiaNativeBlink:after{right:24.5%}
                  .face.blink #laiaNativeBlink:before,.face.blink #laiaNativeBlink:after{height:10%;opacity:.98}
                  .face.talking #laiaNativeMouth{animation:laiaMouth .13s steps(2,end) infinite alternate}
                  .face.happy #laiaNativeMouth{width:18%;left:41%;transform:translateY(1px)}
                  .face.skeptical #laiaNativeMouth{width:10%;left:45%;transform:rotate(-5deg)}
                  .face.surprised #laiaNativeMouth{width:7%;height:7%;left:46.5%;top:67%;border-radius:50%;box-shadow:none;background:#ff5a13}
                  @keyframes laiaMouth{
                    from{height:3px;transform:scaleX(.88) scaleY(1)}
                    to{height:8px;transform:scaleX(1.06) scaleY(1.35)}
                  }
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
              const blink = document.createElement('div');
              blink.id = 'laiaNativeBlink';
              face.appendChild(img);
              face.appendChild(mouth);
              face.appendChild(blink);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    fun startNativeRecording(mode: String): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return false
        }
        if (nativeRecorder != null) return false

        pauseWakeWord()

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
            resumeWakeWord(900)
            runOnUiThread {
                Toast.makeText(this, "Mikrofon konnte nicht gestartet werden.", Toast.LENGTH_SHORT).show()
            }
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
            resumeWakeWord(1400)
            true
        } catch (e: Exception) {
            try { recorder.release() } catch (_: Exception) {}
            nativeRecordingFile?.delete()
            nativeRecordingFile = null
            resumeWakeWord(900)
            false
        }
    }

    private fun setupWakeWord() {
        if (wakeRecognizer != null || !SpeechRecognizer.isRecognitionAvailable(this)) return

        wakeRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    wakeListening = false
                    if (wakeEnabled && isResumed && nativeRecorder == null) resumeWakeWord(1100)
                }

                override fun onResults(results: Bundle?) {
                    wakeListening = false
                    val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val heard = phrases.firstOrNull { containsWakePhrase(it) }
                    if (heard != null) {
                        val rest = extractAfterWakePhrase(heard)
                        val quoted = JSONObject.quote(rest)
                        webView.evaluateJavascript(
                            "window.receiveWakePhrase && window.receiveWakePhrase($quoted)",
                            null
                        )
                        resumeWakeWord(1800)
                    } else {
                        resumeWakeWord(650)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }

    private fun containsWakePhrase(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        return listOf("hey laia", "hey leia", "hey laya", "hey lya", "hei laia").any { t.contains(it) }
    }

    private fun extractAfterWakePhrase(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val candidates = listOf("hey laia", "hey leia", "hey laya", "hey lya", "hei laia")
        val match = candidates.map { it to lower.indexOf(it) }.filter { it.second >= 0 }.minByOrNull { it.second }
            ?: return ""
        return text.substring((match.second + match.first.length).coerceAtMost(text.length))
            .trim(' ', ',', '.', ':', '-', '!')
    }

    fun pauseWakeWord() {
        wakeHandler.removeCallbacksAndMessages(null)
        if (wakeListening) {
            try { wakeRecognizer?.cancel() } catch (_: Exception) {}
        }
        wakeListening = false
    }

    fun resumeWakeWord(delayMs: Long = 600L) {
        if (!wakeEnabled) return
        wakeHandler.postDelayed({ startWakeListening() }, delayMs)
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        wakeEnabled = enabled
        if (enabled) resumeWakeWord(300) else pauseWakeWord()
    }

    private fun startWakeListening() {
        if (!wakeEnabled || !isResumed || nativeRecorder != null || wakeListening) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        setupWakeWord()
        try {
            wakeRecognizer?.startListening(recognitionIntent())
            wakeListening = true
        } catch (_: Exception) {
            wakeListening = false
        }
    }

    fun setTorch(enabled: Boolean): String {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingTorchState = enabled
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
            return "permission_requested"
        }
        return try {
            val manager = getSystemService(CameraManager::class.java)
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                val c = manager.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "unavailable"
            manager.setTorchMode(cameraId, enabled)
            "ok"
        } catch (_: Exception) {
            "failed"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            resumeWakeWord(500)
        }
        if (requestCode == REQ_CAMERA) {
            val desired = pendingTorchState
            pendingTorchState = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && desired != null) {
                val result = setTorch(desired)
                Toast.makeText(
                    this,
                    if (result == "ok") "Taschenlampe ${if (desired) "an" else "aus"}." else "Taschenlampe nicht verfügbar.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        resumeWakeWord(800)
    }

    override fun onPause() {
        isResumed = false
        pauseWakeWord()
        super.onPause()
    }

    override fun onDestroy() {
        pauseWakeWord()
        try { wakeRecognizer?.destroy() } catch (_: Exception) {}
        wakeRecognizer = null
        try { nativeRecorder?.release() } catch (_: Exception) {}
        nativeRecorder = null
        super.onDestroy()
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

    companion object {
        private const val REQ_MIC = 100
        private const val REQ_CAMERA = 101
    }
}

class LaiaAndroidBridge(private val activity: MainActivity) {
    @JavascriptInterface
    fun startRecording(mode: String): Boolean = activity.startNativeRecording(mode)

    @JavascriptInterface
    fun stopRecording(): Boolean = activity.stopNativeRecording()

    @JavascriptInterface
    fun pauseWakeWord() = activity.pauseWakeWord()

    @JavascriptInterface
    fun resumeWakeWord() = activity.resumeWakeWord(900)

    @JavascriptInterface
    fun setWakeWordEnabled(enabled: Boolean) = activity.setWakeWordEnabled(enabled)

    @JavascriptInterface
    fun setAlarm(hour: Int, minute: Int, label: String): Boolean {
        if (hour !in 0..23 || minute !in 0..59) return false
        var launched = false
        activity.runOnUiThread {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.take(120))
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            launched = launch(intent, "Keine Wecker-App gefunden.")
        }
        return launched
    }

    @JavascriptInterface
    fun setTimer(seconds: Int, label: String): Boolean {
        if (seconds !in 1..86400) return false
        var launched = false
        activity.runOnUiThread {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.take(120))
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            launched = launch(intent, "Keine Timer-App gefunden.")
        }
        return launched
    }

    @JavascriptInterface
    fun openApp(app: String): Boolean {
        var launched = false
        activity.runOnUiThread {
            launched = when (app.lowercase()) {
                "whatsapp" -> launchPackage("com.whatsapp", "WhatsApp ist nicht installiert.")
                "spotify" -> launchPackage("com.spotify.music", "Spotify ist nicht installiert.")
                "maps" -> launchPackage("com.google.android.apps.maps", "Google Maps ist nicht installiert.")
                "camera" -> launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE), "Keine Kamera-App gefunden.")
                "settings" -> launch(Intent(Settings.ACTION_SETTINGS), "Einstellungen konnten nicht geöffnet werden.")
                "clock" -> launch(Intent(AlarmClock.ACTION_SHOW_ALARMS), "Keine Uhr-App gefunden.")
                else -> false
            }
        }
        return launched
    }

    @JavascriptInterface
    fun setFlashlight(enabled: Boolean): String = activity.setTorch(enabled)

    @JavascriptInterface
    fun createCalendarEvent(
        title: String,
        startMillis: Long,
        endMillis: Long,
        location: String,
        description: String
    ): Boolean {
        if (startMillis <= 0L) return false
        var launched = false
        activity.runOnUiThread {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title.take(160))
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                if (endMillis > startMillis) putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                if (location.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, location.take(240))
                if (description.isNotBlank()) putExtra(CalendarContract.Events.DESCRIPTION, description.take(1000))
            }
            launched = launch(intent, "Keine Kalender-App gefunden.")
        }
        return launched
    }

    private fun launchPackage(packageName: String, error: String): Boolean {
        val intent = activity.packageManager.getLaunchIntentForPackage(packageName) ?: run {
            toast(error)
            return false
        }
        activity.startActivity(intent)
        return true
    }

    private fun launch(intent: Intent, error: String): Boolean {
        return if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
            true
        } else {
            toast(error)
            false
        }
    }

    private fun toast(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }
}
