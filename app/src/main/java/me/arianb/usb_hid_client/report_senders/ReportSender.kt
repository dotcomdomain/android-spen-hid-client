package me.arianb.usb_hid_client.report_senders

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.system.ErrnoException
import me.arianb.usb_hid_client.hid_utils.DevicePath
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException

abstract class ReportSender(
    val characterDevicePath: DevicePath
) {
    // Keep the newest reports, especially button releases, if USB temporarily stops accepting writes.
    private val reportsChannel = Channel<ByteArray>(256, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun start(onSuccess: () -> Unit, onException: (e: IOException) -> Unit) = withContext(Dispatchers.IO) {
        for (report in reportsChannel) {
            try {
                Timber.d("REPORT HEX (len = %d): %s", report.size, report.toHexString())
                sendReport(report)
                onSuccess()
            } catch (e: IOException) {
                Timber.d(e)

                // TODO: map exception to a sealed error type and pass that to lambda?
                onException(e)
                // Reports from a disconnected host must not replay as delayed cursor movement.
                while (reportsChannel.tryReceive().isSuccess) { /* discard stale reports */ }
                delay(250)
            }
        }
    }

    // IMPORTANT: Implement this when extending this class. Parameter list can be any number of bytes.
    // public void addReport(byte foo, byte bar, byte baz) {
    //     super.addReportToChannel(new byte[]{foo, bar, baz});
    // }
    //
    // Of course, make sure the argument list matches what the character device is expecting.
    protected fun addReportToChannel(report: ByteArray) {
        // DROP_OLDEST keeps the latest state when the host falls behind.
        reportsChannel.trySend(report)
    }

    open suspend fun sendReport(report: ByteArray) {
        writeBytes(report)
    }

    // Writes HID report to character device
    @Throws(IOException::class, FileNotFoundException::class)
    suspend fun writeBytes(report: ByteArray) {
        currentCoroutineContext().ensureActive()
        val fd = try {
            Os.open(characterDevicePath.path, OsConstants.O_WRONLY or OsConstants.O_NONBLOCK or OsConstants.O_CLOEXEC, 0)
        } catch (error: ErrnoException) {
            throw FileNotFoundException("${characterDevicePath.path}: ${error.message}").apply { initCause(error) }
        }
        try {
            val deadline = SystemClock.elapsedRealtime() + 1000
            while (true) {
                currentCoroutineContext().ensureActive()
                try {
                    if (Os.write(fd, report, 0, report.size) != report.size)
                        throw IOException("Incomplete HID report")
                    return
                } catch (error: ErrnoException) {
                    if (error.errno != OsConstants.EAGAIN)
                        throw IOException("${characterDevicePath.path}: ${error.message}", error)
                    if (SystemClock.elapsedRealtime() >= deadline)
                        throw IOException("HID endpoint stopped accepting reports: ${characterDevicePath.path}")
                    delay(4)
                }
            }
        } finally {
            runCatching { Os.close(fd) }
        }
    }
}
