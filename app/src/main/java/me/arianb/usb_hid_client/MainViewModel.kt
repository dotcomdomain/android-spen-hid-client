package me.arianb.usb_hid_client

import android.app.Application
import android.util.Log
import com.topjohnwu.superuser.Shell
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import me.arianb.usb_hid_client.hid_utils.DevicePath
import me.arianb.usb_hid_client.hid_utils.KeyboardDevicePath
import me.arianb.usb_hid_client.hid_utils.ModifiesStateDirectly
import me.arianb.usb_hid_client.hid_utils.OutputRouting
import me.arianb.usb_hid_client.hid_utils.TouchpadDevicePath
import me.arianb.usb_hid_client.hid_utils.UHID
import me.arianb.usb_hid_client.report_senders.KeySender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.LoopbackTouchpadSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.MouseSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.PointerDeviceSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.TouchpadSender
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import me.arianb.usb_hid_client.settings.LoopbackSetting
import me.arianb.usb_hid_client.settings.UserPreferences
import me.arianb.usb_hid_client.settings.UserPreferencesRepository
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Data class that represents the UI state
 */
data class MyUiState(
    // Character Device Stuff
    val missingCharacterDevice: Boolean = false,
    val isCharacterDevicePermissionsBroken: String? = null,
    val usbHidKernelUnsupported: Boolean = false,

    // Other Stuff
    val isDeviceUnplugged: Boolean = false
)

/** Effective destination of each device after resolving Automatic against the USB host state. */
data class OutputRoute(val keyboardLoopback: Boolean, val pointerLoopback: Boolean)

/** The USB gadget is needed unless both devices are forced to loopback. */
private val UserPreferences.needsUsbGadget: Boolean
    get() = keyboardLoopback != LoopbackSetting.Loopback || pointerLoopback != LoopbackSetting.Loopback

private val UserPreferences.usesAutomaticOutput: Boolean
    get() = keyboardLoopback == LoopbackSetting.Auto || pointerLoopback == LoopbackSetting.Auto

private fun resolveOutputRoute(preferences: UserPreferences, usbHostConnected: Boolean?) = OutputRoute(
    keyboardLoopback = OutputRouting.usesLoopback(preferences.keyboardLoopback, usbHostConnected),
    pointerLoopback = OutputRouting.usesLoopback(preferences.pointerLoopback, usbHostConnected),
)

private data class KeySenderConfig(val path: KeyboardDevicePath, val loopback: Boolean)

private data class PointerSenderConfig(
    val path: TouchpadDevicePath,
    val loopback: Boolean,
    val precisionTouchpad: Boolean,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState

    private val characterDeviceManager = CharacterDeviceManager.getInstance(application)
    private val rootStateHolder = RootStateHolder.getInstance()
    private val userPreferencesStateFlow = UserPreferencesRepository.getInstance(application).userPreferencesFlow
    private val isRecoveringGadget = AtomicBoolean(false)
    private val appContext: Application = application

    val outputRoute: StateFlow<OutputRoute> = combine(
        userPreferencesStateFlow, OutputRouting.usbHostConnected, ::resolveOutputRoute
    ).distinctUntilChanged().stateIn(
        viewModelScope, SharingStarted.Eagerly,
        resolveOutputRoute(userPreferencesStateFlow.value, OutputRouting.usbHostConnected.value)
    )

    private fun keySenderConfig(preferences: UserPreferences, route: OutputRoute) =
        KeySenderConfig(preferences.keyboardCharacterDevicePath, route.keyboardLoopback)

    private fun pointerSenderConfig(preferences: UserPreferences, route: OutputRoute) = PointerSenderConfig(
        preferences.touchpadCharacterDevicePath, route.pointerLoopback, preferences.enablePrecisionTouchpad
    )

    private fun createKeySender(config: KeySenderConfig) =
        KeySender(config.path, if (config.loopback) appContext else null)

    // Loopback always uses the precision touchpad, so Android handles its gestures natively.
    private fun createPointerSender(config: PointerSenderConfig): PointerDeviceSender =
        if (config.loopback) {
            fixCharacterDevicePermissions(UHID.PATH)
            LoopbackTouchpadSender(TouchpadDevicePath(UHID.PATH), appContext)
        } else if (config.precisionTouchpad) {
            TouchpadSender(config.path)
        } else {
            MouseSender(config.path)
        }

    // Senders are rebuilt only when their own configuration changes, not on unrelated preference edits.
    val keySender: StateFlow<KeySender> = combine(userPreferencesStateFlow, outputRoute, ::keySenderConfig)
        .distinctUntilChanged()
        .map(::createKeySender)
        .stateIn(
            viewModelScope, SharingStarted.Eagerly,
            createKeySender(keySenderConfig(userPreferencesStateFlow.value, outputRoute.value))
        )

    val touchpadSender: StateFlow<PointerDeviceSender> =
        combine(userPreferencesStateFlow, outputRoute, ::pointerSenderConfig)
            .distinctUntilChanged()
            .map(::createPointerSender)
            .stateIn(
                viewModelScope, SharingStarted.Eagerly,
                createPointerSender(pointerSenderConfig(userPreferencesStateFlow.value, outputRoute.value))
            )

    private val senderFlowList = listOf(keySender, touchpadSender)

    init {
        viewModelScope.launch {
            outputRoute.map { it.pointerLoopback }.distinctUntilChanged().collectLatest { enabled ->
                if (enabled) {
                    clearUsbErrorsIfUnused()
                    try {
                        coroutineScope {
                            launch { me.arianb.usb_hid_client.hid_utils.LoopbackRouting.routeBuiltInPen(application, true) }
                            launch { UHID.prepare(application) }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Pointer loopback initialization failed")
                    }
                } else {
                    me.arianb.usb_hid_client.hid_utils.LoopbackRouting.routeBuiltInPen(application, false)
                    UHID.destroy()
                }
            }
        }
        viewModelScope.launch {
            outputRoute.map { it.keyboardLoopback }.distinctUntilChanged().collectLatest { enabled ->
                if (enabled) {
                    clearUsbErrorsIfUnused()
                    try {
                        me.arianb.usb_hid_client.dictation.LoopbackKeyboard.prepare(application)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Keyboard loopback initialization failed")
                    }
                } else {
                    me.arianb.usb_hid_client.dictation.LoopbackKeyboard.close()
                }
            }
        }
        // Automatic output follows the USB host while the app is open.
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesStateFlow.map { it.usesAutomaticOutput }.distinctUntilChanged().collectLatest { automatic ->
                if (!automatic) {
                    OutputRouting.reset()
                    return@collectLatest
                }
                while (true) {
                    OutputRouting.observe(OutputRouting.probeUsbHost())
                    delay(1000)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(2000)
                if (anyCharacterDeviceMissing()) {
                    recoverDisabledGadget()
                }
            }
        }

        senderFlowList.forEach { senderFlow ->
            viewModelScope.launch {
                senderFlow.collectLatest { sender ->
                    sender.start(
                        onSuccess = {
                            // This is called when no exception was thrown, meaning everything is good :)
                            // so let's set the UI state back to default (no errors)
                            _uiState.update { MyUiState() }
                        },
                        onException = { e ->
                            val characterDevicePath = sender.characterDevicePath
                            if (e is FileNotFoundException && characterDeviceMissing(characterDevicePath)) {
                                Timber.i("Character device '$characterDevicePath' doesn't exist. The user probably skipped the character device creation prompt.")
                                recoverDisabledGadget()
                            } else {
                                handleException(e, sender.characterDevicePath)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun handleException(e: IOException, devicePath: DevicePath) {
        val exceptionString = e.message ?: Log.getStackTraceString(e)
        val lowercaseExceptionString = exceptionString.lowercase()

        if (lowercaseExceptionString.contains("errno 108")) {
            Timber.i("device might be unplugged")
            _uiState.update { it.copy(isDeviceUnplugged = true) }
            recoverDisabledGadget()
        } else if (lowercaseExceptionString.contains("permission denied")) {
            Timber.i("char dev perms are wrong")
            _uiState.update { it.copy(isCharacterDevicePermissionsBroken = devicePath.path) }
        } else if (lowercaseExceptionString.contains("enxio")) {
            Timber.i("somehow the HID gadget is disabled but the character devices are still present")
            _uiState.update { it.copy(missingCharacterDevice = true) }
            recoverDisabledGadget()
        } else {
            Timber.e(e)
            Timber.e("unknown error has occurred while trying to write to character device")
//            showSnackbar("ERROR: Failed to send mouse report.", Snackbar.LENGTH_SHORT)
        }

        Timber.d("in MainViewModel, new state is: %s", uiState.value.toString())
    }

    private fun clearUsbErrorsIfUnused() {
        val route = outputRoute.value
        if (route.keyboardLoopback && route.pointerLoopback) {
            _uiState.update {
                it.copy(missingCharacterDevice = false, isDeviceUnplugged = false, isCharacterDevicePermissionsBroken = null)
            }
        }
    }

    private fun recoverDisabledGadget() {
        if (!userPreferencesStateFlow.value.needsUsbGadget) return
        if (!rootStateHolder.hasRootPermissions()) return
        if (!isRecoveringGadget.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                if (!userPreferencesStateFlow.value.needsUsbGadget) return@launch
                val preferences = GadgetUserPreferences.fromUserPreferences(userPreferencesStateFlow.value)
                characterDeviceManager.createCharacterDevices(preferences)
                anyCharacterDeviceMissing()
            } finally {
                isRecoveringGadget.set(false)
            }
        }
    }

    // Character Device Manager
    fun createCharacterDevices() {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.w("Can't create character devices, missing root permissions")
            return
        }
        if (kernelLacksUsbHid()) {
            _uiState.update { it.copy(usbHidKernelUnsupported = true) }
            return
        }

        viewModelScope.launch {
            val gadgetUserPreferences = GadgetUserPreferences.fromUserPreferences(userPreferencesStateFlow.value)
            characterDeviceManager.createCharacterDevices(gadgetUserPreferences)

            // Re-evaluate state
            anyCharacterDeviceMissing()
        }
    }

    fun deleteCharacterDevices() {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.w("Can't delete character devices, missing root permissions")
            return
        }

        viewModelScope.launch {
            val gadgetUserPreferences = GadgetUserPreferences.fromUserPreferences(userPreferencesStateFlow.value)
            characterDeviceManager.deleteCharacterDevices(gadgetUserPreferences)

            // Re-evaluate state
            anyCharacterDeviceMissing()
        }
    }

    fun recreateCharacterDevices() {
        if (!rootStateHolder.hasRootPermissions()) return
        if (kernelLacksUsbHid()) {
            createCharacterDevices()
            return
        }
        viewModelScope.launch {
            val preferences = GadgetUserPreferences.fromUserPreferences(userPreferencesStateFlow.value)
            characterDeviceManager.deleteCharacterDevices(preferences)
            characterDeviceManager.createCharacterDevices(preferences)
            anyCharacterDeviceMissing()
        }
    }

    private fun kernelLacksUsbHid(): Boolean = Shell.cmd(
        "/system/bin/zcat /proc/config.gz | /system/bin/grep -q '^# CONFIG_USB_CONFIGFS_F_HID is not set$'"
    ).exec().code == 0

    fun fixCharacterDevicePermissions(device: String) {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.w("Can't fix character device permissions, missing root permissions")
            return
        }

        characterDeviceManager.fixCharacterDevicePermissions(device)
    }

    @OptIn(ModifiesStateDirectly::class)
    fun characterDeviceMissing(charDevicePath: DevicePath): Boolean {
        val result = if (charDevicePath.path == UHID.PATH)
            java.io.File(UHID.PATH).exists().not()
        else characterDeviceManager.characterDeviceMissing(charDevicePath)

        _uiState.update { it.copy(missingCharacterDevice = result) }

        return result
    }

    @OptIn(ModifiesStateDirectly::class)
    fun anyCharacterDeviceMissing(): Boolean {
        val result = if (userPreferencesStateFlow.value.needsUsbGadget)
            characterDeviceManager.anyCharacterDeviceMissing()
        else !java.io.File(UHID.PATH).exists()

        _uiState.update { it.copy(missingCharacterDevice = result) }

        return result
    }

    // Keyboard
    fun addStandardKey(modifier: Byte, key: Byte) =
        keySender.value.addStandardKey(modifier, key)

    fun setStandardKeyState(modifier: Byte, firstKey: Byte, secondKey: Byte = 0) =
        keySender.value.setStandardKeyState(modifier, firstKey, secondKey)

    fun addMediaKey(key: Byte) =
        keySender.value.addMediaKey(key)

    fun setMediaKeyState(key: Byte) =
        keySender.value.setMediaKeyState(key)
}
