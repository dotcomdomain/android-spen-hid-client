package me.arianb.usb_hid_client.hid_utils

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import timber.log.Timber

class UsbGadgetServiceConnection : ServiceConnection {
    private var mService: Messenger? = null
    private var pendingCompletion: CompletableDeferred<Unit>? = null
    private val replyMessenger = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what == UsbGadgetService.MSG_COMPLETE) {
                pendingCompletion?.complete(Unit)
                true
            } else {
                false
            }
        }
    )

    val isBound: Boolean
        get() = mService != null

    override fun onServiceConnected(className: ComponentName, service: IBinder) {
        mService = Messenger(service)
    }

    override fun onServiceDisconnected(className: ComponentName) {
        // This is called when the connection with the service has been
        // unexpectedly disconnected; that is, its process crashed.
        mService = null
    }

    private suspend fun send(messageType: Int, preferences: GadgetUserPreferences) {
        if (!isBound) {
            Timber.w("Attempted to communicate with service using unbound connection")
            return
        }

        val msg = Message.obtain(null, messageType).apply {
            data.putParcelable(UsbGadgetService.GADGET_PREF_BUNDLE_KEY, preferences)
            replyTo = replyMessenger
        }
        val completion = CompletableDeferred<Unit>()
        pendingCompletion = completion
        try {
            mService!!.send(msg)
            withTimeout(30_000) { completion.await() }
        } catch (e: RemoteException) {
            Timber.e(e)
        } finally {
            pendingCompletion = null
        }
    }

    suspend fun createGadget(preferences: GadgetUserPreferences) {
        send(UsbGadgetService.MSG_CREATE, preferences)
    }

    suspend fun deleteGadget(preferences: GadgetUserPreferences) {
        send(UsbGadgetService.MSG_DELETE, preferences)
    }

}
