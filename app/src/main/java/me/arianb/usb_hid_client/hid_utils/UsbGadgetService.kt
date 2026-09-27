package me.arianb.usb_hid_client.hid_utils

import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Parcelable
import android.os.Process
import android.os.RemoteException
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.parcelize.Parcelize
import me.arianb.usb_hid_client.BuildConfig
import me.arianb.usb_hid_client.getParcelableCompat
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import timber.log.Timber
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.io.path.writer

class UsbGadgetService : RootService() {
    init {
        // This is called in a different process, so we gotta replant Timber stuff
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Shell.enableVerboseLogging = true
        }
//        Timber.plant(ProductionTree())
    }

    private val mMessenger: Messenger by lazy {
        Messenger(
            Handler(Looper.getMainLooper(), MessageHandler())
        )
    }

    internal class MessageHandler : Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            Timber.d("Message received in service, running with UID = ${Process.myUid()}")

            val gadgetUserPreferences: GadgetUserPreferences? = run {
                val bundle = msg.data.apply {
                    classLoader = GadgetUserPreferences::class.java.classLoader
                }
                bundle.getParcelableCompat(GADGET_PREF_BUNDLE_KEY)
            }
            if (gadgetUserPreferences == null) {
                Timber.e("Failed to unmarshal GadgetUserPreferences")
                return false
            }
            Timber.d("GadgetUserPreferences = $gadgetUserPreferences")
            try {
                val usbGadgetManager = UsbGadgetManager(gadgetUserPreferences)
                when (msg.what) {
                    MSG_CREATE -> usbGadgetManager.createCharacterDevices()
                    MSG_DELETE -> usbGadgetManager.deleteCharacterDevices()
                    else -> {
                        Timber.w("Unhandled message: $msg")
                        return false
                    }
                }
            } catch (e: Exception) {
                // Catching unhandled exceptions because the logs were complaining about unclosed file descriptors
                // when I didn't
                Timber.w("Oh no, an unhandled exception occurred in UsbGadgetService")
                Timber.w(e)
            } finally {
                try {
                    msg.replyTo?.send(Message.obtain(null, MSG_COMPLETE))
                } catch (e: RemoteException) {
                    Timber.w(e, "Failed to acknowledge USB gadget operation")
                }
            }

            return true
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Timber.d("UsbGadgetService onBind() called")
        return mMessenger.binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Timber.d("UsbGadgetService onUnbind() called")
        return super.onUnbind(intent)
    }

    companion object {
        const val GADGET_PREF_BUNDLE_KEY = "data"

        const val MSG_CREATE = 0
        const val MSG_DELETE = 1
        const val MSG_COMPLETE = 2
    }
}

// FIXME: implement CreateNewGadgetForFunctions preference
@OptIn(ExperimentalUnsignedTypes::class)
internal class UsbGadgetManager(val gadgetUserPreferences: GadgetUserPreferences) {
    private val CONFIG_FS_PATH: Path = Path("/config/usb_gadget")
    private val USB_GADGET_PATH: Path = determineGadgetPath()
    private val UDC_PATH: Path = USB_GADGET_PATH / "UDC"

    private val CONFIGS_PATH: Path = USB_GADGET_PATH / "configs/b.1/"
    private val FUNCTIONS_PATH: Path = USB_GADGET_PATH / "functions/"
    private val ANDROID_USB_PATH: Path = Path("/sys/class/android_usb/android0")
    private val ANDROID_USB_ENABLE_PATH: Path = ANDROID_USB_PATH / "enable"
    private val ANDROID_USB_FUNCTIONS_PATH: Path = ANDROID_USB_PATH / "functions"
    private val SAVED_USB_CONFIG_PATH: Path = Path("/data/local/tmp/android-hid-client-usb-config")
    private val SAVED_USB_FUNCTIONS_PATH: Path = Path("/data/local/tmp/android-hid-client-usb-functions")

    private val usesSamsungUsbSelector: Boolean
        get() = ANDROID_USB_ENABLE_PATH.isRegularFile() && ANDROID_USB_FUNCTIONS_PATH.isRegularFile()

    private inner class HidFunction(
        val name: String,
        val protocol: UByte,
        val subclass: UByte,
        val reportLength: UInt,
        val reportDescriptor: UByteArray
    ) {
        val functionPath: Path
            get() = FUNCTIONS_PATH / name

        val configPath: Path
            get() = CONFIGS_PATH / name
    }

    private val allHidFunctions = arrayOf(
        HidFunction(
            "hid.keyboard",
            protocol = 1u,
            subclass = 1u,
            reportLength = 5u,
            // @formatter:off
            reportDescriptor = ubyteArrayOf(0x05u,0x01u,0x09u,0x06u,0xA1u,0x01u,0x85u,0x01u,0x75u,0x01u,0x95u,0x08u,0x05u,0x07u,0x19u,0xE0u,0x29u,0xE7u,0x15u,0x00u,0x25u,0x01u,0x81u,0x02u,0x75u,0x01u,0x95u,0x08u,0x81u,0x03u,0x95u,0x02u,0x75u,0x08u,0x15u,0x00u,0x25u,0xFFu,0x05u,0x07u,0x19u,0x00u,0x29u,0xFFu,0x81u,0x00u,0xC0u,0x05u,0x0Cu,0x09u,0x01u,0xA1u,0x01u,0x85u,0x02u,0x75u,0x10u,0x95u,0x01u,0x26u,0xFFu,0x07u,0x19u,0x00u,0x2Au,0xFFu,0x07u,0x81u,0x00u,0xC0u)
            // @formatter:on
        ), HidFunction(
            "hid.touchpad",
            // Precision touchpads use the HID report protocol, not the boot-mouse protocol.
            // Keeping this at 2 makes Windows identify the entire multi-collection interface
            // as a boot mouse and can leave the precision touchpad collection non-functional.
            protocol = 0u,
            subclass = 0u,
            reportLength = 12u,
            // @formatter:off
            reportDescriptor = ubyteArrayOf(0x05u, 0x01u, 0x09u, 0x02u, 0xa1u, 0x01u, 0x85u, 0x01u, 0x09u, 0x01u, 0xa1u, 0x00u, 0x05u, 0x09u, 0x19u, 0x01u, 0x29u, 0x02u, 0x15u, 0x00u, 0x25u, 0x01u, 0x75u, 0x01u, 0x95u, 0x02u, 0x81u, 0x02u, 0x95u, 0x06u, 0x81u, 0x03u, 0x05u, 0x01u, 0x09u, 0x30u, 0x09u, 0x31u, 0x15u, 0x81u, 0x25u, 0x7fu, 0x75u, 0x08u, 0x95u, 0x02u, 0x81u, 0x06u, 0x75u, 0x08u, 0x95u, 0x05u, 0x81u, 0x03u, 0xc0u, 0x06u, 0x00u, 0xffu, 0x09u, 0x01u, 0x85u, 0x0eu, 0x09u, 0xc5u, 0x15u, 0x00u, 0x26u, 0xffu, 0x00u, 0x75u, 0x08u, 0x95u, 0x04u, 0xb1u, 0x02u, 0xc0u, 0x05u, 0x0du, 0x09u, 0x05u, 0xa1u, 0x01u, 0x85u, 0x04u, 0x09u, 0x22u, 0xa1u, 0x02u, 0x15u, 0x00u, 0x25u, 0x01u, 0x09u, 0x47u, 0x09u, 0x42u, 0x95u, 0x02u, 0x75u, 0x01u, 0x81u, 0x02u, 0x75u, 0x01u, 0x95u, 0x02u, 0x81u, 0x03u, 0x95u, 0x01u, 0x75u, 0x04u, 0x25u, 0x0fu, 0x09u, 0x51u, 0x81u, 0x02u, 0x05u, 0x01u, 0x15u, 0x00u, 0x26u, 0xe0u, 0x2eu, 0x75u, 0x10u, 0x55u, 0x0du, 0x65u, 0x11u, 0x09u, 0x30u, 0x35u, 0x00u, 0x46u, 0xe0u, 0x2eu, 0x95u, 0x01u, 0x81u, 0x02u, 0x46u, 0xe0u, 0x2eu, 0x26u, 0xe0u, 0x2eu, 0x26u, 0xe0u, 0x2eu, 0x09u, 0x31u, 0x81u, 0x02u, 0x05u, 0x0du, 0x15u, 0x00u, 0x25u, 0x64u, 0x95u, 0x03u, 0xc0u, 0x55u, 0x0cu, 0x66u, 0x01u, 0x10u, 0x47u, 0xffu, 0xffu, 0x00u, 0x00u, 0x27u, 0xffu, 0xffu, 0x00u, 0x00u, 0x75u, 0x10u, 0x95u, 0x01u, 0x09u, 0x56u, 0x81u, 0x02u, 0x09u, 0x54u, 0x25u, 0x7fu, 0x95u, 0x01u, 0x75u, 0x08u, 0x81u, 0x02u, 0x05u, 0x09u, 0x09u, 0x01u, 0x25u, 0x01u, 0x75u, 0x01u, 0x95u, 0x01u, 0x81u, 0x02u, 0x95u, 0x07u, 0x81u, 0x03u, 0x09u, 0xc5u, 0x75u, 0x08u, 0x95u, 0x02u, 0x81u, 0x03u, 0x05u, 0x0du, 0x85u, 0x02u, 0x09u, 0x55u, 0x09u, 0x59u, 0x75u, 0x04u, 0x95u, 0x02u, 0x25u, 0x0fu, 0xb1u, 0x02u, 0x85u, 0x07u, 0x09u, 0x60u, 0x75u, 0x01u, 0x95u, 0x01u, 0x15u, 0x00u, 0x25u, 0x01u, 0xb1u, 0x02u, 0x95u, 0x0fu, 0xb1u, 0x03u, 0x06u, 0x00u, 0xffu, 0x06u, 0x00u, 0xffu, 0x85u, 0x06u, 0x09u, 0xc5u, 0x15u, 0x00u, 0x26u, 0xffu, 0x00u, 0x75u, 0x08u, 0x96u, 0x00u, 0x01u, 0xb1u, 0x02u, 0xc0u, 0x05u, 0x0du, 0x09u, 0x0eu, 0xa1u, 0x01u, 0x85u, 0x03u, 0x09u, 0x22u, 0xa1u, 0x00u, 0x09u, 0x52u, 0x15u, 0x00u, 0x25u, 0x0au, 0x75u, 0x08u, 0x95u, 0x01u, 0xb1u, 0x02u, 0xc0u, 0x09u, 0x22u, 0xa1u, 0x00u, 0x85u, 0x05u, 0x09u, 0x57u, 0x09u, 0x58u, 0x75u, 0x01u, 0x95u, 0x02u, 0x25u, 0x01u, 0xb1u, 0x02u, 0x95u, 0x0eu, 0xb1u, 0x03u, 0xc0u, 0xc0u, 0x06u, 0x00u, 0xffu, 0x09u, 0x01u, 0xa1u, 0x01u, 0x85u, 0x5cu, 0x09u, 0x01u, 0x95u, 0x0bu, 0x75u, 0x08u, 0x81u, 0x06u, 0x85u, 0x0du, 0x09u, 0xc5u, 0x15u, 0x00u, 0x26u, 0xffu, 0x00u, 0x75u, 0x08u, 0x95u, 0x04u, 0xb1u, 0x02u, 0x85u, 0x0cu, 0x09u, 0xc6u, 0x96u, 0xf0u, 0x03u, 0x75u, 0x08u, 0xb1u, 0x02u, 0x85u, 0x0bu, 0x09u, 0xc7u, 0x96u, 0x82u, 0x00u, 0x75u, 0x08u, 0xb1u, 0x02u, 0xc0u)
           // @formatter:on
        )
    )

    private fun determineGadgetPath(): Path {
        val pathsToTry: List<Path> = buildList {
            val prefPath = gadgetUserPreferences.usbGadgetPath
            if (prefPath.path.isNotBlank()) {
                this.add(Path(prefPath.path))
            }

            this.add(CONFIG_FS_PATH / "g1")
            this.add(CONFIG_FS_PATH / "g2")
        }

        for (path in pathsToTry) {
            if (path.isDirectory()) {
                return path
            }
        }

        val gadgetPaths = (runCatching { CONFIG_FS_PATH.listDirectoryEntries() }.getOrNull() ?: run {
            Timber.e("Failed to list entries at path: $CONFIG_FS_PATH")
            emptyList()
        }).filter { it.isDirectory() }

        if (gadgetPaths.isEmpty()) {
            // TODO: This is WRONG, but it's better than a RuntimeException and I don't have better handling yet
            return pathsToTry.first()
        } else {
            for (path in gadgetPaths) {
                if ((path / "UDC").isRegularFile()) {
                    return path
                }
            }

            return gadgetPaths.first()
        }
    }

    fun createCharacterDevices() {
        // TODO:
        //  check if symlinks already exist in configs dir bc if they do, writes will fail due to "device or resource busy",
        //  which is reasonable, since the function would be active.

        val gadgetFunctionLinksToRestore: List<Pair<Path, Path>> =
            if (gadgetUserPreferences.disableGadgetFunctionsDuringConfiguration && !usesSamsungUsbSelector) {
                getGadgetFunctionLinksToRestore().apply {
                    // Delete links
                    forEach { (linkPath, _) ->
                        runCatching {
                            linkPath.deleteIfExists()
                        }
                    }
                }
            } else {
                emptyList()
            }

        // Disable gadget before configuring it to work around possible device-specific issue
        //
        // Credit to @szescxz on GitHub for finding this workaround
        //   - https://github.com/Arian04/android-hid-client/issues/50#issuecomment-2915345677
        // Credit to @alryaz on GitHub for submitting a PR that I unfortunately couldn't accept, since it was based
        // on outdated code from the main branch
        //  - https://github.com/Arian04/android-hid-client/pull/64
        try {
            disableGadget()
        } catch (e: IOException) {
            Timber.e("Failed to disable usb gadget")
            Timber.e(e)
        }

        for (hidFunction in allHidFunctions) {
            try {
                addHidFunction(hidFunction)
            } catch (e: IOException) {
                Timber.e("Failed to add '${hidFunction.name}' function to usb gadget")
                Timber.e(e)
            }
        }

        linkFunctionsToConfig(allHidFunctions)

        gadgetFunctionLinksToRestore.forEach { (linkPath, targetPath) ->
            runCatching {
                linkPath.createSymbolicLinkPointingTo(targetPath)
            }.onFailure {
                Timber.e("ugh it didn't work, here's some info: link=$linkPath target=$targetPath")
                Timber.e(it)
            }
        }

        try {
            enableGadget()
        } catch (e: IOException) {
            Timber.e("Failed to reset usb gadget")
            Timber.e(e)
        }
    }

    private fun getGadgetFunctionLinksToRestore(): List<Pair<Path, Path>> {
        val entries: List<Path> = runCatching { CONFIGS_PATH.listDirectoryEntries() }.getOrNull() ?: run {
            Timber.e("Failed to list directory entries at path: $CONFIGS_PATH")
            emptyList()
        }

        Timber.i(entries.toString())

        val links = entries.filter { it.isSymbolicLink() }
        Timber.i(links.toString())

        val linkPairs: List<Pair<Path, Path>> = links.mapNotNull {
            runCatching {
                // It is extremely important to get the real path, and not just use the link's target, because using
                // and restoring that relative path target will fail later. I think it's due to some file system
                // weirdness in the way ConfigFS handles symlinks (play around with `ln -s` and you'll see what I mean)
                Pair(it, it.toRealPath())
            }.getOrNull()
        }

        Timber.i(linkPairs.toString())

        return linkPairs
    }

    private fun Path.writeAsString(uByte: UByte) = writeAsString(uByte.toUInt())

    private fun Path.writeAsString(uInt: UInt) = writeText(uInt.toString())

    @Throws(IOException::class)
    private fun addHidFunction(function: HidFunction) {
        function.functionPath.let {
            // Ensure this directory (and all its parents) exist
            it.createDirectories()

            (it / "protocol").writeAsString(function.protocol)
            (it / "subclass").writeAsString(function.subclass)
            try {
                // Not critical, so don't let this one fail the whole operation
                (it / "no_out_endpoint").writeAsString(1u)
            } catch (e: IOException) {
                Timber.w(e)
            }
            (it / "report_length").writeAsString(function.reportLength)
            (it / "report_desc").writeBytes(function.reportDescriptor.asByteArray())
        }
    }

    private fun linkFunctionsToConfig(functions: Array<HidFunction>) {
        if (functions.isEmpty()) {
            // FIXME: handle this?
            Timber.w("bruh it's empty")
            return
        }

        // Ensure this directory (and all its parents) exist
        try {
            CONFIGS_PATH.createDirectories()
        } catch (e: IOException) {
            Timber.e("IOException occurred while trying to create all directories in path: $CONFIGS_PATH")
            Timber.e(e)
        }

        functions.forEach {
            try {
                it.configPath.createSymbolicLinkPointingTo(it.functionPath)
            } catch (e: java.nio.file.FileAlreadyExistsException) {
                // NOTE: it's extremely important to make sure you catch Java's FileAlreadyExistsException, not Kotlin's
                Timber.w("Attempted to create a symlink in a location that already had a file")
                Timber.d(e)
            }
        }
    }

    private fun resetGadget() {
        try {
            disableGadget()
        } catch (e: IOException) {
            Timber.w(e, "Failed to disable USB gadget during reset procedure")
        }

        try {
            enableGadget()
        } catch (e: IOException) {
            Timber.w(e, "Failed to enable USB gadget during reset procedure")
        }
    }

    @Throws(IOException::class)
    private fun disableGadget() {
        if (usesSamsungUsbSelector) {
            val usbConfig = getSystemProperty("sys.usb.config")
            if (usbConfig.isNotBlank() && usbConfig != "none") {
                SAVED_USB_CONFIG_PATH.writeText(usbConfig)
                SAVED_USB_FUNCTIONS_PATH.writeText(ANDROID_USB_FUNCTIONS_PATH.toFile().readText().trim())
                setSystemProperty("sys.usb.config", "none")
                var attempts = 0
                while (getSystemProperty("sys.usb.state") != "none" && attempts < 50) {
                    Thread.sleep(100)
                    attempts++
                }
            }
            ANDROID_USB_ENABLE_PATH.writeText("0")
            return
        }

        UDC_PATH.writer(options = arrayOf(StandardOpenOption.SYNC)).use {
            // For some reason, it was refusing to clear without writing a newline, other whitespace didn't seem to work.
            it.write("\n")
        }
    }

    @Throws(IOException::class)
    private fun enableGadget() {
        val udc = getUDC()

        if (usesSamsungUsbSelector) {
            // Samsung's Exynos ConfigFS fork keeps every function symlinked and selects active functions here.
            // One "hid" token selects every linked HID instance.
            val originalFunctions = runCatching { SAVED_USB_FUNCTIONS_PATH.toFile().readText().trim() }
                .getOrDefault("")
            if (originalFunctions.split(',').contains("adb")) {
                runCommand("/system/bin/start", "adbd")
                var attempts = 0
                while (getSystemProperty("sys.usb.ffs.ready") != "1" && attempts < 50) {
                    Thread.sleep(100)
                    attempts++
                }
            }
            val functions = (
                originalFunctions.split(',').filter { it.isNotBlank() && it != "hid" } + "hid"
            ).distinct().joinToString(",")
            ANDROID_USB_FUNCTIONS_PATH.writeText(functions)
            UDC_PATH.writeText(udc)
            ANDROID_USB_ENABLE_PATH.writeText("1")
            return
        }

        UDC_PATH.writer(options = arrayOf(StandardOpenOption.SYNC)).use {
            // This part seems to happen implicitly
            it.write(udc)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    fun deleteCharacterDevices() {
        if (usesSamsungUsbSelector) {
            disableGadget()
            // Move the HID functions into the active list so Samsung's ConfigFS unlink callback can find them.
            ANDROID_USB_FUNCTIONS_PATH.writeText("hid")
        }

        for (hidFunction in allHidFunctions) {
            try {
                // Clear out function configuration directory (should just point to function path)
                hidFunction.configPath.deleteRecursively()

                // Delete function directories
                hidFunction.functionPath.deleteIfExists()
            } catch (e: IOException) {
                Timber.e("Failed to remove '${hidFunction.name}' function from usb gadget")
                Timber.e(e)
            }

            // Delete character devices
            CharacterDeviceManager.Companion.DevicePaths.all.map { Path(it.path) }.forEach {
                it.deleteIfExists()
            }
        }

        if (usesSamsungUsbSelector) {
            val usbConfig = runCatching { SAVED_USB_CONFIG_PATH.toFile().readText().trim() }
                .getOrDefault("adb")
                .ifBlank { "adb" }
            SAVED_USB_CONFIG_PATH.deleteIfExists()
            SAVED_USB_FUNCTIONS_PATH.deleteIfExists()
            setSystemProperty("sys.usb.config", usbConfig)
        } else {
            resetGadget()
        }
    }

    @Throws(IOException::class)
    private fun getSystemProperty(name: String): String {
        val process = ProcessBuilder("/system/bin/getprop", name).redirectErrorStream(true).start()
        val value = process.inputStream.bufferedReader().use { it.readText().trim() }
        if (process.waitFor() != 0) throw IOException("Failed to read Android property: $name")
        return value
    }

    @Throws(IOException::class)
    private fun setSystemProperty(name: String, value: String) {
        runCommand("/system/bin/setprop", name, value)
    }

    @Throws(IOException::class)
    private fun runCommand(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        if (process.waitFor() != 0) {
            throw IOException("Command failed (${command.joinToString(" ")}): $output")
        }
    }

    @Throws(IOException::class)
    fun getUDC(): String {
        // NOTE:
        //  Reading the "sys.usb.controller" property will return null when (I think) the gadget is disabled.
        //  My guess is it returns the *active* UDC, so I can't read the UDC when it's inactive. So we're doing
        //  it this way instead.

        val udcList: List<Path> = run {
            val udcDirectoryPath = Path("/sys/class/udc")

            udcDirectoryPath.listDirectoryEntries()
        }
        Timber.d("UDC value from file listing is: $udcList")

        val udcPath: Path = if (udcList.isEmpty()) {
            // TODO: What do we even do at this point
            Path("")
        } else if (udcList.size == 1) {
            udcList.first()
        } else {
            // There's more than one, attempt to filter it down I guess
            val filteredList = udcList.filter { it.isSymbolicLink() }

            if (filteredList.isEmpty()) {
                // Just use the unfiltered list I guess
                udcList.first()
            } else if (filteredList.size == 1) {
                filteredList.first()
            } else {
                Timber.w("filtered list of UDCs has more than one, using first one: $udcList")

                filteredList.first()
            }
        }

        val udc = udcPath.name

        return udc
    }
}

@JvmInline
@Parcelize
value class UsbGadgetPath(val path: String) : Parcelable
