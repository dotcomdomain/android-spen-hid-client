package me.arianb.usb_hid_client.hid_utils

import android.app.Application
import android.graphics.Point
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private external fun getTouchpadDescriptor(): ByteArray
const val NATIVE_PROJECT_LIB_NAME = "usb_hid_client"

object UHID {
    const val PATH = "/dev/uhid"
    private val mutex = Mutex()
    private var touchpad: LocalHidDevice? = null
    private var mouse: LocalHidDevice? = null
    private var absolute: LocalHidDevice? = null
    private var absoluteSize = Point()
    init { System.loadLibrary(NATIVE_PROJECT_LIB_NAME) }

    private val relativeDescriptor = intArrayOf(
        0x05,0x01,0x09,0x02,0xA1,0x01,0x85,0x01,0x09,0x01,0xA1,0x00,
        0x05,0x09,0x19,0x01,0x29,0x02,0x15,0x00,0x25,0x01,0x75,0x01,
        0x95,0x02,0x81,0x02,0x95,0x06,0x81,0x03,0x05,0x01,0x09,0x30,
        0x09,0x31,0x15,0x81,0x25,0x7F,0x75,0x08,0x95,0x02,0x81,0x06,
        0x75,0x08,0x95,0x05,0x81,0x03,0xC0,0xC0,
    ).map(Int::toByte).toByteArray()

    private fun absoluteDescriptor(width: Int, height: Int) = intArrayOf(
        0x05,0x0D,0x09,0x02,0xA1,0x01,0x85,0x08,0x09,0x20,0xA1,0x00,
        0x09,0x42,0x09,0x44,0x09,0x32,0x15,0x00,0x25,0x01,0x75,0x01,
        0x95,0x03,0x81,0x02,0x95,0x05,0x81,0x03,0x05,0x01,0x15,0x00,
        0x75,0x10,0x95,0x01,0x09,0x30,0x26,(width-1) and 255,(width-1) ushr 8,0x81,0x02,
        0x09,0x31,0x26,(height-1) and 255,(height-1) ushr 8,0x81,0x02,0xC0,0xC0,
    ).map(Int::toByte).toByteArray()

    suspend fun prepare(application: Application) { mutex.withLock { getTouchpad(application) } }
    private suspend fun getTouchpad(application: Application): LocalHidDevice = touchpad ?:
        LocalHidDevice.open(application, "touchpad", getTouchpadDescriptor(), 0x0a37) { id ->
            when (id) {
                2 -> byteArrayOf(2, 0x1f) // 15 contacts, one button
                7 -> byteArrayOf(7, 0, 0)
                6 -> byteArrayOf(6) + ByteArray(256)
                else -> byteArrayOf(id.toByte(), 0)
            }
        }.also { touchpad = it }

    suspend fun sendHidEvent(application: Application, report: ByteArray) = mutex.withLock {
        when (report.firstOrNull()?.toInt()) {
            1 -> {
                val device = mouse ?: LocalHidDevice.open(application, "mouse", relativeDescriptor, 0x0a3a).also { mouse = it }
                device.send(report)
            }
            8 -> {
                val size = Point()
                LoopbackRouting.targetDisplay(application).getRealSize(size)
                if (size != absoluteSize) { absolute?.close(); absolute = null; absoluteSize = size }
                val device = absolute ?: LocalHidDevice.open(application, "absolute pointer",
                    absoluteDescriptor(size.x, size.y), 0x0a3b).also { absolute = it }
                val scaled = report.copyOf()
                scaled[1] = (scaled[1].toInt() or 4).toByte() // In range, including hover with no button held.
                for ((offset, extent) in listOf(2 to size.x, 4 to size.y)) {
                    val value = ((report[offset].toInt() and 255) or ((report[offset+1].toInt() and 255) shl 8))
                    val pixel = (value.toLong() * (extent-1) / 32767).toInt()
                    scaled[offset] = pixel.toByte(); scaled[offset+1] = (pixel ushr 8).toByte()
                }
                device.send(scaled)
            }
            else -> getTouchpad(application).send(report)
        }
    }

    suspend fun destroy() = mutex.withLock {
        touchpad?.close(); mouse?.close(); absolute?.close()
        touchpad = null; mouse = null; absolute = null
    }
}

@Suppress("unused", "EnumEntryName")
internal enum class UHIDEventType {
    __UHID_LEGACY_CREATE, UHID_DESTROY, UHID_START, UHID_STOP, UHID_OPEN, UHID_CLOSE,
    UHID_OUTPUT, __UHID_LEGACY_OUTPUT_EV, __UHID_LEGACY_INPUT, UHID_GET_REPORT,
    UHID_GET_REPORT_REPLY, UHID_CREATE2, UHID_INPUT2, UHID_SET_REPORT, UHID_SET_REPORT_REPLY,
}
