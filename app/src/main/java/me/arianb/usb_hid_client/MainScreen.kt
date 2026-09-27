package me.arianb.usb_hid_client

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.arianb.usb_hid_client.input_views.DirectInput
import me.arianb.usb_hid_client.input_views.DirectInputIconButton
import me.arianb.usb_hid_client.input_views.Touchpad
import me.arianb.usb_hid_client.input_views.scripting.ScriptsDisplayView
import me.arianb.usb_hid_client.settings.SettingsScreen
import me.arianb.usb_hid_client.settings.SettingsViewModel
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import me.arianb.usb_hid_client.troubleshooting.TroubleshootingScreen
import me.arianb.usb_hid_client.ui.standalone_screens.HelpScreen
import me.arianb.usb_hid_client.ui.standalone_screens.InfoScreen
import me.arianb.usb_hid_client.ui.theme.CornerExtraLarge
import me.arianb.usb_hid_client.ui.theme.CornerLargeIncreased
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.PaddingSmall
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.BasicTopBar
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import timber.log.Timber

class MainScreen : Screen {
    @Composable
    override fun Content() {
        MainPage()
    }
}

@Composable
fun MainPage(
    mainViewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val rootStateHolder = RootStateHolder.getInstance()
    val rootState by rootStateHolder.uiState.collectAsState()

    val showMissingCharDeviceOnStartupAlert = remember { mutableStateOf(mainViewModel.anyCharacterDeviceMissing()) }

    val uiState by mainViewModel.uiState.collectAsState()
    Timber.d("in MainScreen, uiState is: %s", uiState.toString())

    LaunchedEffect(uiState.missingCharacterDevice) {
        if (!uiState.missingCharacterDevice) {
            showMissingCharDeviceOnStartupAlert.value = false
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val preferences by settingsViewModel.userPreferencesFlow.collectAsState()
    val isDeviceInLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullScreenTouchPadEnabled = preferences.isTouchpadFullscreenInLandscape && isDeviceInLandscape

    BasicPage(
        snackbarHostState = snackbarHostState,
        topBar = if (fullScreenTouchPadEnabled) { {} } else {
            { MainTopBar(showTitle = !preferences.hideAppTitle) }
        },
        padding = if (fullScreenTouchPadEnabled) PaddingValues(PaddingNormal) else PaddingValues(start = PaddingNormal, end = PaddingNormal, bottom = PaddingNormal),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (fullScreenTouchPadEnabled) Arrangement.Top else Arrangement.spacedBy(10.dp, Alignment.Top)
    ) {
        if (showMissingCharDeviceOnStartupAlert.value) {
            Timber.d("MISSING CHAR DEV ON START")
            CreateCharDevicesAlertDialog(showMissingCharDeviceOnStartupAlert)
        }

        if (!fullScreenTouchPadEnabled && preferences.enableScriptingSupport) {
            ScriptsDisplayView()
        }

        DirectInput()

        Touchpad()

        LaunchedEffect(uiState) {
            Timber.d("LAUNCHED EFFECT RUNNING WITH UI STATE = %s", uiState.toString())
            if (rootState.missingRootPrivileges) {
                snackbarHostState.showSnackbar(
                    message = "Missing root permissions",
                    duration = SnackbarDuration.Long
                )
            } else if (uiState.usbHidKernelUnsupported) {
                snackbarHostState.showSnackbar(
                    message = "This kernel has no USB HID gadget function. Pointer output requires a compatible kernel.",
                    duration = SnackbarDuration.Long
                )
            } else if (uiState.isDeviceUnplugged) {
                snackbarHostState.showSnackbar(
                    message = "ERROR: Your device seems to be disconnected. If not, try reseating the USB cable",
                    duration = SnackbarDuration.Long
                )
            } else if (!showMissingCharDeviceOnStartupAlert.value && uiState.missingCharacterDevice) {
                val result = snackbarHostState.showSnackbar(
                    message = "ERROR: Character device has disappeared since the app was started.",
                    actionLabel = "RECREATE",
                )
                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        mainViewModel.createCharacterDevices()
                    }
                    SnackbarResult.Dismissed -> {}
                }
            } else if (uiState.isCharacterDevicePermissionsBroken != null) {
                val characterDevicePath = uiState.isCharacterDevicePermissionsBroken!!
                val result = snackbarHostState.showSnackbar(
                    message = "ERROR: Character device permissions seem incorrect.",
                    actionLabel = "FIX",
                )
                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        mainViewModel.fixCharacterDevicePermissions(characterDevicePath)
                    }
                    SnackbarResult.Dismissed -> {}
                }
            }
        }
    }
}

private data class NavMenuItem(val screen: Screen, val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(showTitle: Boolean) {
    val navigator = LocalNavigator.currentOrThrow
    var showDropdownMenu by remember { mutableStateOf(false) }

    BasicTopBar(
        title = if (showTitle) stringResource(R.string.app_name) else "",
        titleContent = if (showTitle) {
            {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                            append("USB ")
                        }
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)) {
                            append("HID Client")
                        }
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        letterSpacing = (-0.3).sp
                    )
                )
            }
        } else null,
        actions = {
            DirectInputIconButton()
            IconButton(onClick = { showDropdownMenu = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Overflow Menu",
                )
                DropdownMenu(
                    expanded = showDropdownMenu,
                    shape = RoundedCornerShape(CornerLargeIncreased),
                    onDismissRequest = { showDropdownMenu = false }
                ) {
                    val menuItems = arrayOf(
                        NavMenuItem(SettingsScreen(), stringResource(R.string.settings), Icons.Outlined.Settings),
                        NavMenuItem(TroubleshootingScreen(), stringResource(R.string.troubleshooting_title), Icons.Outlined.Build),
                        NavMenuItem(HelpScreen(), stringResource(R.string.help), Icons.Outlined.HelpOutline),
                        NavMenuItem(InfoScreen(), stringResource(R.string.info), Icons.Outlined.Info)
                    )
                    for (item in menuItems) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                val thisScreen = item.screen
                                for (screen in navigator.items.reversed()) {
                                    if (screen::class == thisScreen::class) {
                                        return@DropdownMenuItem
                                    }
                                }
                                navigator.push(thisScreen)
                                showDropdownMenu = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun CreateCharDevicesAlertDialog(showAlert: MutableState<Boolean>, mainViewModel: MainViewModel = viewModel()) {
    AlertDialog(
        shape = RoundedCornerShape(CornerExtraLarge),
        icon = {
            Icon(
                imageVector = Icons.Outlined.Usb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Character device(s) do not exist",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Add HID functions to the default USB gadget? This must be re-done after every reboot.\n\nNote: The app requires these devices to send input to your PC.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    mainViewModel.createCharacterDevices()
                    showAlert.value = false
                }
            ) {
                Text("Enable Gadget")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showAlert.value = false }
            ) {
                Text("Not Now")
            }
        },
        onDismissRequest = {}
    )
}

@DarkLightModePreviews
@Composable
private fun MainScreenPreview() {
    Navigator(MainScreen())
}
