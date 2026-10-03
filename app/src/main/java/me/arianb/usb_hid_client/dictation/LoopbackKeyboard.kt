package me.arianb.usb_hid_client.dictation

import android.app.Application
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.arianb.usb_hid_client.hid_utils.LocalHidDevice

internal object LoopbackKeyboard {
    private val descriptor = intArrayOf(
        0x05,0x01,0x09,0x06,0xA1,0x01,0x85,0x01,0x75,0x01,0x95,0x08,
        0x05,0x07,0x19,0xE0,0x29,0xE7,0x15,0x00,0x25,0x01,0x81,0x02,
        0x75,0x01,0x95,0x08,0x81,0x03,0x95,0x02,0x75,0x08,0x15,0x00,
        0x25,0xFF,0x05,0x07,0x19,0x00,0x29,0xFF,0x81,0x00,0xC0,
        0x05,0x0C,0x09,0x01,0xA1,0x01,0x85,0x02,0x75,0x10,0x95,0x01,
        0x26,0xFF,0x07,0x19,0x00,0x2A,0xFF,0x07,0x81,0x00,0xC0,
    ).map(Int::toByte).toByteArray()
    private val mutex = Mutex()
    private var keyboard: LocalHidDevice? = null
    suspend fun prepare(application: Application) = mutex.withLock { getKeyboard(application); Unit }
    private suspend fun getKeyboard(application: Application): LocalHidDevice = keyboard ?:
        LocalHidDevice.open(application, "keyboard", descriptor, 0x0a39).also { keyboard = it }
    suspend fun open(application: Application) = LocalHidDevice.open(application, "dictation", descriptor, 0x0a38)
    suspend fun send(application: Application, report: ByteArray) = mutex.withLock {
        val device = getKeyboard(application)
        device.send(report)
    }
    suspend fun close() = mutex.withLock { keyboard?.close(); keyboard = null }
}
