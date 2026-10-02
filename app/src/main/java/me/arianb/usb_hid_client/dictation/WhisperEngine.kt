package me.arianb.usb_hid_client.dictation

/** Model weights are downloaded separately; this library contains inference code only. */
@androidx.annotation.Keep
object WhisperEngine {
    private val requests = java.util.concurrent.atomic.AtomicLong()
    init { System.loadLibrary("dictation") }
    fun newRequest(): Long = requests.incrementAndGet()
    fun transcribe(modelPath: String, samples: FloatArray, threads: Int, request: Long = newRequest(), concurrency: Int = 1): String =
        transcribeNative(modelPath, samples, threads, request, concurrency)
    private external fun transcribeNative(modelPath: String, samples: FloatArray, threads: Int, request: Long, concurrency: Int): String
    external fun cancel(request: Long)
    external fun release()
}
