package me.arianb.usb_hid_client.dictation

import android.content.Intent
import android.os.*
import com.topjohnwu.superuser.ipc.RootService
import java.io.File

/** Reads the same evdev events used by Key Mapper expert mode, without requiring accessibility. */
class RawInputService : RootService() {
    @Volatile private var reader: java.lang.Process? = null
    @Volatile private var generation = 0
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == 1) {
            stopReader()
            val token = generation
            val reply = message.replyTo
            Thread {
                try {
                    val process = ProcessBuilder("/system/bin/getevent", "-t").redirectErrorStream(true).start()
                    if (generation != token) { process.destroy(); return@Thread }
                    reader = process
                    val pattern = Regex("(/dev/input/event\\d+):\\s+([0-9a-fA-F]{4})\\s+([0-9a-fA-F]{4})\\s+([0-9a-fA-F]{8})")
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (generation != token) return@forEach
                            val match = pattern.find(line) ?: return@forEach
                            val type = match.groupValues[2].toInt(16)
                            if (type != 1 && type != 5) return@forEach
                            val path = match.groupValues[1]
                            val name = runCatching { File("/sys/class/input/${File(path).name}/device/name").readText().trim() }.getOrDefault(path)
                            if (generation != token) return@forEach
                            reply.send(Message.obtain(null, 2).apply {
                                data = Bundle().apply {
                                    putString("device", name)
                                    putInt("type", type)
                                    putInt("code", match.groupValues[3].toInt(16))
                                    putInt("value", match.groupValues[4].toLong(16).toInt())
                                }
                            })
                        }
                    }
                    if (generation == token && reader === process) reply.send(Message.obtain(null, 3))
                } catch (_: Exception) {
                    if (generation == token) runCatching { reply.send(Message.obtain(null, 3)) }
                }
            }.start()
        }
        true
    })
    private fun stopReader() { generation++; reader?.destroy(); reader = null }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onUnbind(intent: Intent): Boolean { stopReader(); return false }
    override fun onDestroy() { stopReader(); super.onDestroy() }
}
