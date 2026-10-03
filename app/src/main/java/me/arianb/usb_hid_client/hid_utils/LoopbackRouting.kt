package me.arianb.usb_hid_client.hid_utils

import android.app.Application
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.InputDevice
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Root entry point for Android's runtime input/display association API. */
@androidx.annotation.Keep
object LoopbackRouting {
    @JvmStatic fun main(args: Array<String>) {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "input")
        val service = Class.forName("android.hardware.input.IInputManager\$Stub")
            .getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)
        val api = Class.forName("android.hardware.input.IInputManager")
        if (args[0] == "add") {
            val displayBinder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "display")
            val displays = Class.forName("android.hardware.display.IDisplayManager\$Stub")
                .getMethod("asInterface", android.os.IBinder::class.java).invoke(null, displayBinder)
            val info = Class.forName("android.hardware.display.IDisplayManager")
                .getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(displays, args[2].toInt())
            val uniqueId = info.javaClass.getField("uniqueId").get(info) as String
            api.getMethod("addUniqueIdAssociationByDescriptor", String::class.java, String::class.java)
                .invoke(service, args[1], uniqueId)
        }
        else api.getMethod("removeUniqueIdAssociationByDescriptor", String::class.java).invoke(service, args[1])
    }

    fun targetDisplay(application: Application): Display = application.getSystemService(DisplayManager::class.java)
        .displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON }
        ?: application.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    suspend fun associate(application: Application, descriptor: String, display: Display?) = withContext(Dispatchers.IO) {
        // Older Android versions do not have the descriptor association API.
        if (android.os.Build.VERSION.SDK_INT < 35) return@withContext
        val args = if (display == null) "remove ${quote(descriptor)}"
            else "add ${quote(descriptor)} ${display.displayId}"
        val result = Shell.cmd("CLASSPATH=${quote(application.applicationInfo.sourceDir)} /system/bin/app_process /system/bin ${LoopbackRouting::class.java.name} $args").exec()
        if (!result.isSuccess) timber.log.Timber.w("Loopback display association failed: %s", result.err)
    }

    suspend fun routeBuiltInPen(application: Application, enabled: Boolean) {
        val display = if (enabled) application.getSystemService(DisplayManager::class.java).getDisplay(0) else null
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            if (device.name == "sec_e-pen" || device.name == "sec_virtual-e-pen")
                associate(application, device.descriptor, display)
        }
    }
}
