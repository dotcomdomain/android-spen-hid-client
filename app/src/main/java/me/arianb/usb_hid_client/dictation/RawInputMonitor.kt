package me.arianb.usb_hid_client.dictation

import android.content.*
import android.os.*
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class RawButton(val device: String, val type: Int, val code: Int, val value: Int)
object RawInputMonitor {
    val events = MutableSharedFlow<RawButton>(extraBufferCapacity = 64)
    val error = MutableStateFlow<String?>(null)
    var captureActive = false
    private val owners = mutableSetOf<Any>()
    private var connected = false
    private val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            2 -> events.tryEmit(RawButton(message.data.getString("device") ?: "", message.data.getInt("type"), message.data.getInt("code"), message.data.getInt("value")))
            3 -> error.value = "Raw input reader stopped. Disable and re-enable dictation to reconnect."
        }
        true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            connected = true
            if (owners.isEmpty()) { RootService.unbind(this); return }
            error.value = null
            Messenger(binder).send(Message.obtain(null, 1).apply { replyTo = reply })
        }
        override fun onServiceDisconnected(name: ComponentName) {
            connected = false
            error.value = "Root input service disconnected."
        }
    }
    fun acquire(context: Context, owner: Any) {
        if (owners.add(owner) && owners.size == 1) {
            error.value = "Connecting to root input service…"
            RootService.bind(Intent(context, RawInputService::class.java), connection)
        }
    }
    fun release(owner: Any) {
        owners.remove(owner)
        if (owners.isEmpty() && connected) { RootService.unbind(connection); connected = false }
    }
}
