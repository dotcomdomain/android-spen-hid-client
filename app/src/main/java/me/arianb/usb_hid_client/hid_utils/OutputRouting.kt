package me.arianb.usb_hid_client.hid_utils

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.arianb.usb_hid_client.settings.LoopbackSetting
import java.io.File

/**
 * Decides whether keyboard and pointer reports go to the USB host or to this phone.
 *
 * Automatic mode treats a host as able to receive HID input when the USB controller is configured by a host and
 * the HID gadget functions are active.
 */
object OutputRouting {
    private const val SAMSUNG_FUNCTIONS_PATH = "/sys/class/android_usb/android0/functions"

    // Gadget rebuilds briefly unconfigure USB, so require a few matching samples before switching.
    private const val CONNECT_SAMPLES = 2
    private const val DISCONNECT_SAMPLES = 3

    private val _usbHostConnected = MutableStateFlow<Boolean?>(null)

    /** Debounced host state, or null before the first probe. */
    val usbHostConnected: StateFlow<Boolean?> = _usbHostConnected.asStateFlow()
    private var streak = 0

    fun usesLoopback(setting: LoopbackSetting, usbHostConnected: Boolean?): Boolean = when (setting) {
        LoopbackSetting.Usb -> false
        LoopbackSetting.Loopback -> true
        // Until the first probe completes, keep USB so loopback devices are not created and destroyed at startup.
        LoopbackSetting.Auto -> usbHostConnected == false
    }

    /** Blocking. Reads USB state through the root shell because this ROM denies the app UID access to it. */
    fun probeUsbHost(): Boolean {
        val output = Shell.cmd(
            "cat /sys/class/udc/*/state 2>/dev/null",
            "echo '--'",
            "cat $SAMSUNG_FUNCTIONS_PATH 2>/dev/null"
        ).exec().out
        val separator = output.indexOf("--")
        if (separator < 0) return false
        val configured = output.subList(0, separator).any { it.trim() == "configured" }
        if (!configured) return false

        // Samsung's selector lists active functions. Other ConfigFS gadgets activate every linked function.
        val samsungFunctions = output.subList(separator + 1, output.size).joinToString(",")
        if (File(SAMSUNG_FUNCTIONS_PATH).exists() || samsungFunctions.isNotBlank()) {
            if (samsungFunctions.split(',').none { it.trim() == "hid" }) return false
        }
        return CharacterDeviceManager.Companion.DevicePaths.all.all { it.exists() }
    }

    /** Forgets the host state when nothing monitors it, so a later Automatic selection does not start stale. */
    @Synchronized
    fun reset() {
        _usbHostConnected.value = null
        streak = 0
    }

    /** Records a probe sample, switching the published state only after it is stable. */
    @Synchronized
    fun observe(connected: Boolean) {
        val current = _usbHostConnected.value
        if (current == null || current == connected) {
            _usbHostConnected.value = connected
            streak = 0
            return
        }
        streak++
        if (streak >= if (connected) CONNECT_SAMPLES else DISCONNECT_SAMPLES) {
            _usbHostConnected.value = connected
            streak = 0
        }
    }
}
