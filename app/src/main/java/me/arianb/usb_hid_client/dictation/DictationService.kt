package me.arianb.usb_hid_client.dictation

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import android.provider.Settings
import android.system.ErrnoException
import android.system.OsConstants
import android.system.Os
import android.view.*
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.arianb.usb_hid_client.MainActivity
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation
import me.arianb.usb_hid_client.settings.UserPreferencesRepository
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class DictationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private class Recording(val request: Long) {
        val held = AtomicBoolean(true)
        @Volatile var recorder: AudioRecord? = null
        val finished = CompletableDeferred<Unit>()
        var job: Job? = null
        var phase = DictationPhase.RECORDING
        var message = "● Recording · release to transcribe"
    }
    private val sessions = linkedSetOf<Recording>()
    private var pipelineTail: Deferred<Unit>? = null
    private var activeDecodes = 0
    private var buttonDown = false
    private var recording: Recording? = null
    private val microphone = Mutex()
    private val typing = Mutex()
    private var operation: Job? = null
    private var listening = false
    private var overlay: TextView? = null
    private var indicatorTimeout: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "USBHID:dictation").apply { setReferenceCounted(false) }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL, "Voice dictation", NotificationManager.IMPORTANCE_LOW)
        )
        promote()
        scope.launch {
            DictationPreferences.options.collectLatest { options ->
                if (options.enabled && !listening) {
                    listening = true
                    RawInputMonitor.acquire(this@DictationService, this@DictationService)
                } else if (!options.enabled && listening) {
                    listening = false
                    buttonDown = false
                    cancelRecording()
                    RawInputMonitor.release(this@DictationService)
                    operation?.cancel()
                    if (DictationState.status.value.phase != DictationPhase.DOWNLOADING) stopSelf()
                }
            }
        }
        scope.launch {
            RawInputMonitor.events.collect { button ->
                val options = DictationPreferences.options.value
                if (!options.enabled || RawInputMonitor.captureActive || button.device != options.device || button.type != options.type || button.code != options.code) return@collect
                if (button.value == 1 && !buttonDown) {
                    buttonDown = true
                    beginRecording()
                }
                if (button.value == 0 && buttonDown) {
                    buttonDown = false
                    recording?.let { session ->
                        session.held.set(false)
                        session.recorder?.let { runCatching { it.stop() } }
                    }
                }
            }
        }
        scope.launch {
            RawInputMonitor.error.collect { error ->
                if (error != null && error != "Connecting to root input service…" && listening) {
                    buttonDown = false
                    cancelRecording()
                    operation?.cancel()
                    update(DictationPhase.ERROR, error)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote()
        if (intent?.action == DOWNLOAD && operation?.isActive != true) downloadModel()
        else if (operation?.isActive != true) update(DictationPhase.IDLE, "Hold the mapped button to dictate")
        return START_NOT_STICKY
    }

    private fun promote() {
        val useMicrophone = DictationPreferences.options.value.enabled &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(42, notification("Dictation ready"),
                if (useMicrophone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(42, notification("Dictation ready"))
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.keyboard)
            .setContentTitle("${getString(me.arianb.usb_hid_client.R.string.app_name)} dictation").setContentText(message)
            .setContentIntent(open).setOngoing(true).setSilent(true).build()
    }

    private fun update(phase: DictationPhase, message: String, progress: Float = 0f) {
        indicatorTimeout?.cancel()
        val visible = phase in setOf(DictationPhase.RECORDING, DictationPhase.PROCESSING, DictationPhase.SENDING, DictationPhase.ERROR)
        DictationState.status.value = DictationStatus(phase, message, progress,
            DictationPreferences.modelFile(this).isFile, indicatorVisible = visible)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(42, notification(message))
        showOverlay(if (visible) message else null)
        if (phase == DictationPhase.ERROR) {
            indicatorTimeout = scope.launch {
                delay(2000)
                // Hide the transient error indicator without covering the touchpad.
                DictationState.status.value = DictationState.status.value.copy(indicatorVisible = false)
                showOverlay(null)
            }
        }
    }

    private fun showOverlay(message: String?) {
        val windows = getSystemService(WINDOW_SERVICE) as WindowManager
        if (message == null || !Settings.canDrawOverlays(this)) {
            overlay?.let { runCatching { windows.removeView(it) } }; overlay = null
            DictationState.status.value = DictationState.status.value.copy(overlayVisible = false)
            return
        }
        if (overlay == null) {
            val density = resources.displayMetrics.density
            val text = TextView(this).apply {
                setTextColor(Color.WHITE); textSize = 16f
                setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
                background = GradientDrawable().apply { setColor(0xee243a42.toInt()); cornerRadius = 28 * density }
            }
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = (56 * density).toInt()
            }
            runCatching { windows.addView(text, params); overlay = text }
        }
        overlay?.text = message
        DictationState.status.value = DictationState.status.value.copy(overlayVisible = overlay != null)
    }

    private fun beginRecording() {
        if (DictationState.status.value.phase == DictationPhase.DOWNLOADING) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            update(DictationPhase.ERROR, "Microphone permission is required"); return
        }
        if (!DictationPreferences.modelFile(this).isFile) {
            update(DictationPhase.ERROR, "Download Whisper base in Settings first"); return
        }
        if (DictationPreferences.options.value.replacePending) cancelRecording()
        val session = Recording(WhisperEngine.newRequest())
        val predecessor = pipelineTail
        pipelineTail = session.finished
        recording = session
        sessions.add(session)
        timber.log.Timber.i("Dictation recording %d started", session.request)
        update(DictationPhase.RECORDING, "● Recording · release to transcribe")
        val job = scope.launch(start = CoroutineStart.LAZY) {
            wakeLock.acquire(10 * 60 * 1000L)
            var terminalPhase = DictationPhase.IDLE
            var terminalMessage = "Dictation cancelled"
            try {
                // Only wait for the previous microphone to close, not its native decode.
                val audio = withContext(Dispatchers.IO) { microphone.withLock { recordAudio(session) } }
                if (!DictationPreferences.options.value.enabled) return@launch
                // Delivery follows the chain established on button-down, even
                // when a later short clip finishes inference before an older one.
                session.phase = DictationPhase.PROCESSING
                session.message = "Waiting for earlier dictation…"
                refreshSessions()
                val parallel = !DictationPreferences.options.value.replacePending && DictationPreferences.options.value.parallel
                if (!parallel) predecessor?.await()
                if (audio.size < 3200 || sqrt(audio.sumOf { (it * it).toDouble() } / audio.size) < 0.003) {
                    terminalMessage = "No speech recorded"; return@launch
                }
                session.message = "Transcribing on phone…"
                refreshSessions()
                val concurrency = acquireDecodeSlot()
                val started = SystemClock.elapsedRealtime()
                val text = try {
                    withContext(Dispatchers.Default) {
                        WhisperEngine.transcribe(DictationPreferences.modelFile(this@DictationService).absolutePath, audio, 4, session.request, concurrency).trim()
                    }
                } finally {
                    activeDecodes--
                }
                ensureActive()
                timber.log.Timber.i("Dictation %d decoded %d samples in %d ms; %d characters", session.request, audio.size, SystemClock.elapsedRealtime() - started, text.length)
                if (text.isBlank()) { terminalMessage = "No speech detected"; return@launch }
                session.message = "Waiting to type in recording order…"
                refreshSessions()
                predecessor?.await()
                session.phase = DictationPhase.SENDING
                session.message = if (UserPreferencesRepository.getInstance(application).userPreferencesFlow.value.isLoopbackModeEnabled)
                    "Typing on phone…" else "Typing to connected device…"
                refreshSessions()
                val sent = withContext(Dispatchers.IO) { typing.withLock { sendText(text) } }
                terminalMessage = if (sent) "Sent transcription" else "Dictation discarded: no USB host connected"
                timber.log.Timber.i("Dictation %d delivery completed: %s", session.request, sent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ensureActive()
                terminalPhase = DictationPhase.ERROR
                terminalMessage = error.message ?: "Dictation failed"
            } finally {
                session.held.set(false)
                // Even empty, failed, or cancelled clips must preserve the chain:
                // a later clip must never bypass an unfinished earlier recording.
                withContext(NonCancellable) { predecessor?.await() }
                if (recording === session) recording = null
                sessions.remove(session)
                if (scope.isActive) refreshSessions(terminalPhase, terminalMessage)
                session.finished.complete(Unit)
                if (sessions.isEmpty() && wakeLock.isHeld) wakeLock.release()
            }
        }
        session.job = job
        operation = job
        job.start()
    }

    private fun refreshSessions(terminalPhase: DictationPhase = DictationPhase.IDLE, terminalMessage: String = "Dictation ready") {
        val active = sessions.filter { it.job?.isActive == true }
        val visible = active.firstOrNull { it.phase == DictationPhase.RECORDING } ?: active.firstOrNull()
        if (visible == null) {
            update(terminalPhase, terminalMessage)
        } else {
            val waiting = active.size - 1
            update(visible.phase, visible.message + if (waiting > 0) " · $waiting pending" else "")
        }
    }

    // Called only on the main dispatcher. Lowering a limit lets existing jobs
    // finish; waiting jobs observe the current setting before taking a slot.
    private suspend fun acquireDecodeSlot(): Int {
        while (true) {
            currentCoroutineContext().ensureActive()
            val options = DictationPreferences.options.value
            val limit = if (!options.replacePending && options.parallel) options.parallelLimit.coerceIn(1, 5) else 1
            if (activeDecodes < limit) { activeDecodes++; return limit }
            delay(25)
        }
    }

    private fun cancelRecording() {
        sessions.toList().forEach { session ->
            session.held.set(false)
            timber.log.Timber.i("Dictation %d cancelled", session.request)
            WhisperEngine.cancel(session.request)
            session.recorder?.let { runCatching { it.stop() } }
            session.job?.cancel()
        }
        operation?.cancel()
    }

    @Suppress("MissingPermission")
    private fun recordAudio(session: Recording): FloatArray {
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0) { "16 kHz microphone input is unavailable" }
        val input = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 8192))
        val limit = 16000 * 300 // Five minute safety limit.
        var samples = FloatArray(16000 * 5)
        val buffer = ShortArray(1024)
        var count = 0
        try {
            check(input.state == AudioRecord.STATE_INITIALIZED) { "Could not initialize microphone" }
            session.recorder = input
            if (!session.held.get()) return FloatArray(0)
            input.startRecording()
            while (session.held.get() && count < limit) {
                val n = input.read(buffer, 0, minOf(buffer.size, limit - count), AudioRecord.READ_BLOCKING)
                if (n < 0) { if (!session.held.get()) break; error("Microphone read failed: $n") }
                if (count + n > samples.size) samples = samples.copyOf(minOf(limit, maxOf(samples.size * 2, count + n)))
                for (i in 0 until n) samples[count++] = buffer[i] / 32768f
            }
            return samples.copyOf(count)
        } finally {
            session.recorder = null
            runCatching { input.stop() }; input.release()
        }
    }

    private fun usbConnected(): Boolean {
        val files = File("/sys/class/udc").listFiles().orEmpty()
        if (files.any { runCatching { File(it, "state").readText().trim() == "configured" }.getOrDefault(false) }) return true
        // This ROM denies the app UID access to UDC state even though HID writes
        // are allowed. Ask the existing root shell instead of assuming unplugged.
        return Shell.cmd("cat /sys/class/udc/*/state").exec().out.any { it.trim() == "configured" }
    }

    private suspend fun sendText(text: String): Boolean {
        val preferences = UserPreferencesRepository.getInstance(application).userPreferencesFlow.value
        val loopback = preferences.isLoopbackModeEnabled
        if (!loopback && !usbConnected()) return false
        // HID characters assume a US keyboard layout. Convert common punctuation
        // and decomposable accents rather than silently dropping characters.
        val normalized = Normalizer.normalize(text.replace('’', '\'').replace('‘', '\'')
            .replace('“', '"').replace('”', '"').replace('—', '-').replace('–', '-').replace("…", "..."), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        val reports = normalized.map { char ->
            KeyCodeTranslation.keyCharToScanCodes(char) ?: error("Cannot type '$char' through US HID.")
        }
        val local = if (loopback) LoopbackKeyboard.open(application) else null
        val output = if (loopback) null else openKeyboard(preferences.keyboardCharacterDevicePath.path)
        suspend fun report(bytes: ByteArray) {
            if (local != null) local.send(bytes) else writeReport(requireNotNull(output), bytes)
        }
        try {
                for ((index, codes) in reports.withIndex()) {
                    val (modifier, key) = codes
                    currentCoroutineContext().ensureActive()
                    if (!loopback && index % 32 == 0) check(usbConnected()) { "USB disconnected during typing." }
                    report(byteArrayOf(1, modifier, 0, key, 0)); delay(8)
                    report(byteArrayOf(1, 0, 0, 0, 0)); delay(8)
                }
        } finally {
            // Release even on cancellation. The nonblocking endpoint bounds this
            // cleanup, preventing a held key from repeating while USB is stalled.
            withContext(NonCancellable) { runCatching { report(byteArrayOf(1, 0, 0, 0, 0)) } }
            withContext(NonCancellable) {
                local?.close()
                output?.let { runCatching { Os.close(it) } }
            }
        }
        return true
    }

    private suspend fun writeReport(output: FileDescriptor, report: ByteArray) {
        val deadline = SystemClock.elapsedRealtime() + 1000
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                check(Os.write(output, report, 0, report.size) == report.size) { "Incomplete keyboard report" }
                return
            } catch (error: ErrnoException) {
                if (error.errno != OsConstants.EAGAIN) throw IOException("Keyboard input interrupted.", error)
                if (SystemClock.elapsedRealtime() >= deadline) throw IOException("Keyboard stopped accepting input.", error)
                delay(4)
            }
        }
    }

    private suspend fun openKeyboard(path: String): FileDescriptor {
        // A USB reconfiguration can briefly leave the node present while its
        // endpoint is unavailable. Retry only before sending the first character.
        val deadline = SystemClock.elapsedRealtime() + 2000
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return Os.open(path, OsConstants.O_WRONLY or OsConstants.O_NONBLOCK, 0)
            } catch (error: ErrnoException) {
                val errno = error.errno
                if (errno != OsConstants.ENXIO && errno != OsConstants.ENODEV && errno != OsConstants.ENOENT) throw error
                if (SystemClock.elapsedRealtime() >= deadline || !usbConnected()) {
                    throw IOException("USB keyboard unavailable. Reconnect USB or recreate USB HID.", error)
                }
                delay(100)
            }
        }
    }

    private fun downloadModel() {
        operation = scope.launch {
            update(DictationPhase.DOWNLOADING, "Downloading Whisper base Q5…")
            val model = DictationPreferences.modelFile(this@DictationService)
            val partial = File(model.parentFile, "download.part")
            try {
                withContext(Dispatchers.IO) {
                    model.parentFile!!.mkdirs()
                    val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
                    connection.connectTimeout = 30000; connection.readTimeout = 30000
                    try {
                        check(connection.responseCode == 200) { "Model download failed: HTTP ${connection.responseCode}" }
                        val digest = MessageDigest.getInstance("SHA-256")
                        var downloaded = 0L
                        var lastUpdate = 0L
                        connection.inputStream.use { input ->
                            partial.outputStream().use { output ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    ensureActive()
                                    val size = input.read(buffer); if (size < 0) break
                                    output.write(buffer, 0, size); digest.update(buffer, 0, size)
                                    downloaded += size
                                    if (SystemClock.elapsedRealtime() - lastUpdate > 250) {
                                        lastUpdate = SystemClock.elapsedRealtime()
                                        withContext(Dispatchers.Main) { update(DictationPhase.DOWNLOADING, "Downloading model · ${downloaded * 100 / MODEL_SIZE}%", downloaded.toFloat() / MODEL_SIZE) }
                                    }
                                }
                            }
                        }
                        check(downloaded == MODEL_SIZE && digest.digest().joinToString("") { "%02x".format(it) } == MODEL_SHA256) { "Model verification failed. Please retry." }
                        check(partial.renameTo(model)) { "Could not save model" }
                    } finally { connection.disconnect() }
                }
                update(DictationPhase.IDLE, "Whisper base downloaded and verified")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { update(DictationPhase.ERROR, error.message ?: "Download failed")
            } finally {
                partial.delete()
                if (!DictationPreferences.options.value.enabled) stopSelf()
            }
        }
    }

    override fun onDestroy() {
        buttonDown = false; cancelRecording()
        RawInputMonitor.release(this)
        scope.cancel()
        DictationState.status.value = DictationState.status.value.copy(indicatorVisible = false)
        if (DictationState.status.value.phase in setOf(DictationPhase.RECORDING, DictationPhase.PROCESSING, DictationPhase.SENDING, DictationPhase.DOWNLOADING)) {
            DictationState.status.value = DictationState.status.value.copy(phase = DictationPhase.IDLE, message = "Dictation stopped")
        }
        showOverlay(null)
        if (wakeLock.isHeld) wakeLock.release()
        // JNI release serializes behind an in-flight inference without blocking UI.
        Thread { runCatching { WhisperEngine.release() } }.start()
        super.onDestroy()
    }
    companion object {
        const val DOWNLOAD = "download-model"
        private const val CHANNEL = "dictation"
        private const val MODEL_SIZE = 59707625L
        private const val MODEL_SHA256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
        private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"
        fun start(context: Context, download: Boolean = false) {
            ContextCompat.startForegroundService(context, Intent(context, DictationService::class.java).apply {
                if (download) action = DOWNLOAD
            })
        }
    }
}
