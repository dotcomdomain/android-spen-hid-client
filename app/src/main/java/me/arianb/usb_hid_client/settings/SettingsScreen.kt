package me.arianb.usb_hid_client.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.settings.AppSettings.AppThemePreference
import me.arianb.usb_hid_client.settings.AppSettings.DynamicColors
import me.arianb.usb_hid_client.settings.AppSettings.EnablePrecisionTouchpad
import me.arianb.usb_hid_client.settings.AppSettings.ExperimentalMode
import me.arianb.usb_hid_client.settings.AppSettings.FullyDisableGadgetDuringConfiguration
import me.arianb.usb_hid_client.settings.AppSettings.HideStatusBar
import me.arianb.usb_hid_client.settings.AppSettings.HideAppTitle
import me.arianb.usb_hid_client.settings.AppSettings.KeyboardCharacterDevicePath
import me.arianb.usb_hid_client.settings.AppSettings.MediaKeyPassthrough
import me.arianb.usb_hid_client.settings.AppSettings.LatchFullKeyboardModifiers
import me.arianb.usb_hid_client.settings.AppSettings.PreferenceCategory
import me.arianb.usb_hid_client.settings.AppSettings.PreferenceDivider
import me.arianb.usb_hid_client.settings.AppSettings.PrecisionTouchpadSensitivity
import me.arianb.usb_hid_client.settings.AppSettings.SpenHoverRange
import me.arianb.usb_hid_client.settings.AppSettings.SpenHybridHoverRange
import me.arianb.usb_hid_client.settings.AppSettings.SpenHybridSensitivity
import me.arianb.usb_hid_client.settings.AppSettings.SpenMouseSensitivity
import me.arianb.usb_hid_client.settings.AppSettings.TouchpadCharacterDevicePath
import me.arianb.usb_hid_client.settings.AppSettings.TouchpadFullscreenInLandscape
import me.arianb.usb_hid_client.settings.AppSettings.TouchpadLoopbackMode
import me.arianb.usb_hid_client.settings.AppSettings.TouchpadMouseSensitivity
import me.arianb.usb_hid_client.settings.AppSettings.UsbGadgetPath
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.PaddingSmall
import me.arianb.usb_hid_client.ui.theme.isDynamicColorAvailable
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.utils.Experimental
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar
import me.arianb.usb_hid_client.ui.utils.isExperimentalModeEnabled

class SettingsScreen : Screen {
    @Composable
    override fun Content() {
        SettingsPage()
    }
}

@Composable
fun SettingsPage() {
    BasicPage(
        topBar = { SettingsTopBar() },
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(PaddingSmall, Alignment.Top),
        scrollable = true
    ) {
        PreferenceCategory(
            title = stringResource(R.string.theme_header),
        ) {
            AppThemePreference()

            if (isDynamicColorAvailable()) {
                PreferenceDivider()
                DynamicColors()
            }

            PreferenceDivider()
            HideStatusBar()
            PreferenceDivider()
            HideAppTitle()
        }

        PreferenceCategory(
            title = stringResource(R.string.direct_input),
        ) {
            MediaKeyPassthrough()
            PreferenceDivider()
            LatchFullKeyboardModifiers()
        }

        PreferenceCategory(title = "Voice dictation") {
            me.arianb.usb_hid_client.dictation.DictationSettings()
        }


        PreferenceCategory(
            title = stringResource(R.string.touchpad_label),
        ) {
            TouchpadFullscreenInLandscape()
            PreferenceDivider()
            TouchpadLoopbackMode()
            PreferenceDivider()
            EnablePrecisionTouchpad()
        }

        PreferenceCategory(
            title = stringResource(R.string.touchpad_sensitivity_header),
        ) {
            TouchpadMouseSensitivity()
            PreferenceDivider()
            PrecisionTouchpadSensitivity()
        }

        PreferenceCategory(
            title = stringResource(R.string.spen_settings_header),
        ) {
            SpenHoverRange()
            PreferenceDivider()
            SpenHybridHoverRange()
            PreferenceDivider()
            SpenMouseSensitivity()
            PreferenceDivider()
            SpenHybridSensitivity()
        }

        PreferenceCategory(
            title = stringResource(R.string.misc_header),
            showDivider = isExperimentalModeEnabled()
        ) {
            ExperimentalMode()
        }

        Experimental {
            PreferenceCategory(
                title = stringResource(R.string.device_specific_quirks_header),
                showDivider = false
            ) {
                FullyDisableGadgetDuringConfiguration()
                PreferenceDivider()
                UsbGadgetPath()
                PreferenceDivider()
                KeyboardCharacterDevicePath()
                PreferenceDivider()
                TouchpadCharacterDevicePath()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar() {
    SimpleNavTopBar(
        title = stringResource(R.string.settings)
    )
}

private object AppSettings {
    @Composable
    fun PreferenceCategory(
        title: String,
        showDivider: Boolean = true,
        preferences: @Composable (() -> Unit)
    ) {
        me.arianb.usb_hid_client.settings.PreferenceCategory(
            title = title,
            modifier = Modifier,
            showDivider = showDivider,
            preferences = preferences,
        )
    }

    @Composable
    fun PreferenceDivider() {
        me.arianb.usb_hid_client.settings.PreferenceDivider()
    }

    @Composable
    fun AppThemePreference(
        enabled: Boolean = true,
        settingsViewModel: SettingsViewModel = viewModel()
    ) {
        val preferencesState by settingsViewModel.userPreferencesFlow.collectAsState()

        val selectedTheme = preferencesState.appTheme
        val options = AppTheme.values
        BasicListPreference(
            title = stringResource(R.string.app_theme_title),
            options = options,
            enabled = enabled,
            selected = selectedTheme,
            onPreferenceClicked = { thisAppTheme ->
                settingsViewModel.setPreference(AppPreference.AppThemeKey, thisAppTheme)
            }
        )
    }

    @Composable
    fun TouchpadLoopbackMode() {
        SwitchPreference(
            title = stringResource(R.string.touchpad_loopback_mode_title),
            summary = stringResource(R.string.touchpad_loopback_mode_summary),
            preference = AppPreference.LoopbackMode
        )
    }

    @Composable
    fun DynamicColors() {
        SwitchPreference(
            title = stringResource(R.string.dynamic_colors_title),
            preference = AppPreference.DynamicColorKey
        )
    }

    @Composable
    fun HideStatusBar() {
        SwitchPreference(
            title = stringResource(R.string.hide_status_bar_title),
            summary = stringResource(R.string.hide_status_bar_summary),
            preference = AppPreference.HideStatusBar
        )
    }

    @Composable
    fun HideAppTitle() {
        SwitchPreference(
            title = stringResource(R.string.hide_app_title_title),
            summary = stringResource(R.string.hide_app_title_summary),
            preference = AppPreference.HideAppTitle
        )
    }

    @Composable
    fun MediaKeyPassthrough() {
        SwitchPreference(
            title = stringResource(R.string.volume_button_passthrough_title),
            summary = stringResource(R.string.volume_button_passthrough_summary),
            preference = AppPreference.VolumeButtonPassthroughKey
        )
    }

    @Composable
    fun LatchFullKeyboardModifiers() {
        SwitchPreference(
            title = stringResource(R.string.latch_full_keyboard_modifiers_title),
            summary = stringResource(R.string.latch_full_keyboard_modifiers_summary),
            preference = AppPreference.LatchFullKeyboardModifiers
        )
    }


    @Composable
    fun TouchpadFullscreenInLandscape() {
        SwitchPreference(
            title = stringResource(R.string.touchpad_fullscreen_in_landscape_title),
            summary = stringResource(R.string.touchpad_fullscreen_in_landscape_summary),
            preference = AppPreference.TouchpadFullscreenInLandscape
        )
    }

    @Composable
    fun EnablePrecisionTouchpad() {
        SwitchPreference(
            title = stringResource(R.string.enable_precision_touchpad_title),
            summary = stringResource(R.string.enable_precision_touchpad_summary),
            preference = AppPreference.EnablePrecisionTouchpad
        )
    }

    @Composable
    fun TouchpadMouseSensitivity() {
        SensitivityPreference(
            title = stringResource(R.string.touchpad_mouse_sensitivity_title),
            preference = AppPreference.TouchpadMouseSensitivity
        )
    }

    @Composable
    fun PrecisionTouchpadSensitivity() {
        SensitivityPreference(
            title = stringResource(R.string.precision_touchpad_sensitivity_title),
            preference = AppPreference.PrecisionTouchpadSensitivity
        )
    }

    @Composable
    fun SpenHoverRange() {
        HoverRangePreference(
            title = stringResource(R.string.spen_hover_range_title),
            summary = stringResource(R.string.spen_hover_range_summary),
            preference = AppPreference.SpenHoverRange
        )
    }

    @Composable
    fun SpenHybridHoverRange() {
        HoverRangePreference(
            title = stringResource(R.string.spen_hybrid_hover_range_title),
            summary = stringResource(R.string.spen_hybrid_hover_range_summary),
            preference = AppPreference.SpenHybridHoverRange
        )
    }

    @Composable
    fun SpenMouseSensitivity() {
        SensitivityPreference(
            title = stringResource(R.string.spen_mouse_sensitivity_title),
            preference = AppPreference.SpenMouseSensitivity
        )
    }

    @Composable
    fun SpenHybridSensitivity() {
        SensitivityPreference(
            title = stringResource(R.string.spen_hybrid_sensitivity_title),
            preference = AppPreference.SpenHybridSensitivity
        )
    }

    @Composable
    fun ExperimentalMode() {
        SwitchPreference(
            title = stringResource(R.string.experimental_mode_title),
            summary = stringResource(R.string.experimental_mode_summary),
            preference = AppPreference.ExperimentalMode
        )
    }

    @Composable
    fun UsbGadgetPath() {
        TextDialogPreference(
            title = stringResource(R.string.usb_gadget_path_title),
            preference = AppPreference.UsbGadgetPathPref,
            property = UserPreferences::usbGadgetPath
        )
    }

    @Composable
    fun KeyboardCharacterDevicePath() {
        TextDialogPreference(
            title = stringResource(R.string.keyboard_character_device_path),
            preference = AppPreference.KeyboardCharacterDevicePath,
            property = UserPreferences::keyboardCharacterDevicePath
        )
    }

    @Composable
    fun TouchpadCharacterDevicePath() {
        TextDialogPreference(
            title = stringResource(R.string.touchpad_character_device_path),
            preference = AppPreference.TouchpadCharacterDevicePath,
            property = UserPreferences::touchpadCharacterDevicePath
        )
    }

    @Composable
    fun FullyDisableGadgetDuringConfiguration() {
        SwitchPreference(
            title = stringResource(R.string.disable_gadget_functions_during_config),
            summary = stringResource(R.string.disable_gadget_functions_during_config_summary),
            preference = AppPreference.DisableGadgetFunctionsDuringConfiguration
        )
    }

}

@DarkLightModePreviews
@Composable
private fun SettingsScreenPreview() {
    Navigator(SettingsScreen())
}
