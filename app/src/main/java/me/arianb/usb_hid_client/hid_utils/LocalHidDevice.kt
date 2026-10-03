package me.arianb.usb_hid_client.hid_utils

import android.app.Application
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.system.ErrnoException
import android.view.InputDevice
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*

/** Owns a UHID endpoint and answers kernel requests while the device registers. */
internal class LocalHidDevice private constructor(
    private val application: Application,
    private val fd: FileDescriptor,
    private val feature: (Int) -> ByteArray,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var descriptor: String? = null
    private var displayId = -1
    private val reader = scope.launch {
        val buffer = ByteArray(4380)
        while (isActive) {
            try {
                val n = Os.read(fd, buffer, 0, buffer.size)
                if (n < 4) { delay(5); continue }
                val request = ByteBuffer.wrap(buffer, 0, n).order(ByteOrder.LITTLE_ENDIAN)
                when (request.int) {
                    UHIDEventType.UHID_GET_REPORT.ordinal -> {
                        val id = request.int
                        val number = request.get().toInt() and 255
                        val data = feature(number)
                        val reply = ByteBuffer.allocate(12 + data.size).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(UHIDEventType.UHID_GET_REPORT_REPLY.ordinal).putInt(id)
                            .putShort(0).putShort(data.size.toShort()).put(data).array()
                        Os.write(fd, reply, 0, reply.size)
                    }
                    UHIDEventType.UHID_SET_REPORT.ordinal -> {
                        val reply = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(UHIDEventType.UHID_SET_REPORT_REPLY.ordinal).putInt(request.int).putShort(0).array()
                        Os.write(fd, reply, 0, reply.size)
                    }
                }
            } catch (e: ErrnoException) {
                if (e.errno != OsConstants.EAGAIN) break
                delay(5)
            }
        }
    }

    private suspend fun route() {
        val target = LoopbackRouting.targetDisplay(application)
        if (target.displayId != displayId) {
            descriptor?.let { LoopbackRouting.associate(application, it, target) }
            displayId = target.displayId
            // InputReader applies display associations asynchronously.
            delay(50)
        }
    }

    suspend fun send(report: ByteArray) {
        route()
        val event = ByteBuffer.allocate(6 + report.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(UHIDEventType.UHID_INPUT2.ordinal).putShort(report.size.toShort()).put(report).array()
        val deadline = SystemClock.elapsedRealtime() + 1000
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                if (Os.write(fd, event, 0, event.size) != event.size) throw IOException("Incomplete local HID report")
                return
            } catch (e: ErrnoException) {
                if (e.errno != OsConstants.EAGAIN || SystemClock.elapsedRealtime() >= deadline)
                    throw IOException("Local HID report failed", e)
                delay(4)
            }
        }
    }

    suspend fun close() {
        reader.cancel()
        scope.cancel()
        runCatching { Os.close(fd) }
        descriptor?.let { LoopbackRouting.associate(application, it, null) }
    }

    companion object {
        suspend fun open(application: Application, label: String, descriptor: ByteArray,
                         product: Int, feature: (Int) -> ByteArray = { byteArrayOf(it.toByte(), 0) }): LocalHidDevice {
            CharacterDeviceManager.getInstance(application).fixCharacterDevicePermissions(UHID.PATH)
            val fd = try {
                Os.open(UHID.PATH, OsConstants.O_RDWR or OsConstants.O_NONBLOCK or OsConstants.O_CLOEXEC, 0)
            } catch (e: ErrnoException) { throw IOException("Cannot open local HID endpoint", e) }
            val device = LocalHidDevice(application, fd, feature)
            try {
                val name = "USB HID $label ${SystemClock.elapsedRealtimeNanos()}"
                val event = ByteBuffer.allocate(280 + descriptor.size).order(ByteOrder.LITTLE_ENDIAN)
                event.putInt(UHIDEventType.UHID_CREATE2.ordinal).put(name.toByteArray())
                event.position(260)
                event.putShort(descriptor.size.toShort()).putShort(3)
                    .putInt(0x15d9).putInt(product).putInt(1).putInt(0).put(descriptor)
                val bytes = event.array()
                check(Os.write(fd, bytes, 0, bytes.size) == bytes.size)
                val deadline = SystemClock.elapsedRealtime() + 3000
                while (true) {
                    val input = InputDevice.getDeviceIds().asSequence().mapNotNull(InputDevice::getDevice)
                        .firstOrNull { it.name.startsWith(name) }
                    if (input != null) { device.descriptor = input.descriptor; break }
                    if (SystemClock.elapsedRealtime() >= deadline) throw IOException("Local $label unavailable")
                    delay(10)
                }
                device.route()
                return device
            } catch (e: Throwable) {
                withContext(NonCancellable) { device.close() }
                if (e is ErrnoException) throw IOException("Cannot create local HID endpoint", e)
                throw e
            }
        }
    }
}
