package me.arianb.usb_hid_client.dictation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Runs on the rooted test phone after using the in-app model downloader. */
@RunWith(AndroidJUnit4::class)
class DictationSmokeTest {
    @Test fun rootInputReaderConnectsWithoutStartingRecording() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val owner = Any()
        try {
            instrumentation.runOnMainSync { RawInputMonitor.acquire(instrumentation.targetContext, owner) }
            val deadline = System.currentTimeMillis() + 20000
            while (RawInputMonitor.error.value == "Connecting to root input service…" && System.currentTimeMillis() < deadline) Thread.sleep(100)
            Thread.sleep(1000)
            assertNull(RawInputMonitor.error.value, RawInputMonitor.error.value)
            assertFalse(DictationPreferences.options.value.enabled)
        } finally {
            instrumentation.runOnMainSync { RawInputMonitor.release(owner) }
        }
    }

    @Test fun downloadedModelAndNativeTranscription() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        assertFalse("Dictation must remain disabled until enabled by the user", DictationPreferences.options.value.enabled)
        val model = DictationPreferences.modelFile(target)
        assertEquals(59707625L, model.length())
        val hash = MessageDigest.getInstance("SHA-256")
        model.inputStream().use { input ->
            val bytes = ByteArray(65536)
            while (true) { val n = input.read(bytes); if (n < 0) break; hash.update(bytes, 0, n) }
        }
        assertEquals("422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898", hash.digest().joinToString("") { "%02x".format(it) })
        val wav = instrumentation.context.assets.open("jfk.wav").use { it.readBytes() }
        val data = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        data.position(12)
        var samples = FloatArray(0)
        var fullAudio = FloatArray(0)
        while (data.remaining() >= 8) {
            val name = ByteArray(4); data.get(name)
            val size = data.int
            if (String(name, Charsets.US_ASCII) == "data") {
                fullAudio = FloatArray(size / 2) { data.short / 32768f }
                samples = fullAudio.copyOf(minOf(fullAudio.size, 16000 * 4))
                break
            }
            data.position(data.position() + size + size % 2)
        }
        assertEquals(64000, samples.size)
        val started = System.nanoTime()
        val text = WhisperEngine.transcribe(model.absolutePath, samples, 4)
        android.util.Log.i("DictationSmoke", "4 second audio took ${(System.nanoTime() - started) / 1e9}s: $text")
        assertTrue(text, text.contains("fellow", ignoreCase = true))
        assertTrue(text, text.contains("Americans", ignoreCase = true))
        val warmStart = System.nanoTime()
        val longerText = WhisperEngine.transcribe(model.absolutePath, fullAudio, 4)
        android.util.Log.i("DictationSmoke", "11 second cached-model audio took ${(System.nanoTime() - warmStart) / 1e9}s: $longerText")
        assertTrue(longerText, longerText.contains("country", ignoreCase = true))
        assertTrue(longerText, longerText.contains("do for you", ignoreCase = true))
        WhisperEngine.release()
    }
}
