package me.arianb.usb_hid_client.input_views

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.input_views.touch_input_handlers.MouseInputHandler
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.MouseSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.PointerDeviceSender
import me.arianb.usb_hid_client.settings.AppPreference
import me.arianb.usb_hid_client.settings.SettingsViewModel
import me.arianb.usb_hid_client.ui.theme.CornerLargeIncreased
import me.arianb.usb_hid_client.ui.theme.ElevationLevel0
import me.arianb.usb_hid_client.ui.theme.ElevationLevel2

@Composable
fun Touchpad(
    mainViewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val pointerDeviceSender by mainViewModel.touchpadSender.collectAsState()
    val preferences by settingsViewModel.userPreferencesFlow.collectAsState()
    val deviceOrientation = LocalConfiguration.current.orientation
    val isLandscape = deviceOrientation == Configuration.ORIENTATION_LANDSCAPE
    val fullScreenTouchPadEnabled = preferences.isTouchpadFullscreenInLandscape && isLandscape

    val currentSpenMode = remember(preferences.spenMode) {
        runCatching { SpenMode.valueOf(preferences.spenMode) }.getOrDefault(SpenMode.HYBRID)
    }

    val spenSensitivity = when (currentSpenMode) {
        SpenMode.HOVER -> 1f
        SpenMode.MOUSE -> preferences.spenMouseSensitivity
        SpenMode.HYBRID -> preferences.spenHybridSensitivity
    }

    val spenHoverRange = when (currentSpenMode) {
        SpenMode.HOVER -> preferences.spenHoverRange
        SpenMode.HYBRID -> preferences.spenHybridHoverRange
        SpenMode.MOUSE -> 1f
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = if (fullScreenTouchPadEnabled) Arrangement.Top else Arrangement.spacedBy(10.dp)
    ) {
        // Unified Tablet & Touchpad Surface takes all available space
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            UnifiedTabletSurface(
                modifier = Modifier.fillMaxSize(),
                pointerDeviceSender = pointerDeviceSender,
                spenMode = currentSpenMode,
                spenSensitivity = spenSensitivity,
                hoverRange = spenHoverRange,
                mouseSensitivity = preferences.touchpadMouseSensitivity,
                precisionSensitivity = preferences.precisionTouchpadSensitivity,
                deviceOrientation = deviceOrientation,
                isFullScreen = fullScreenTouchPadEnabled,
                onSpenModeChange = { newMode ->
                    settingsViewModel.setPreference(AppPreference.SpenModePref, newMode.name)
                }
            )

            if (fullScreenTouchPadEnabled) {
                LandscapeFloatingKeyboardButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            }
        }

        // If in relative mouse mode (legacy mouse without precision touchpad),
        // provide physical click buttons at the bottom for finger touch
        if (!fullScreenTouchPadEnabled && pointerDeviceSender is MouseSender) {
            val mouseInputHandler = remember(pointerDeviceSender, preferences.touchpadMouseSensitivity) {
                MouseInputHandler(pointerDeviceSender as MouseSender, preferences.touchpadMouseSensitivity)
            }
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val leftButtonInteractionSource = remember { MutableInteractionSource() }
                val rightButtonInteractionSource = remember { MutableInteractionSource() }

                val isLeftButtonPressed: Boolean by leftButtonInteractionSource.collectIsPressedAsState()
                val isRightButtonPressed: Boolean by rightButtonInteractionSource.collectIsPressedAsState()

                val sendButtonStateUpdate: () -> Unit = {
                    mouseInputHandler.sendButtonStateUpdate(
                        PointerDeviceSender.TouchpadButtonState(
                            isLeftButtonPressed,
                            isRightButtonPressed,
                        )
                    )
                }

                TouchPadButton(
                    modifier = Modifier.weight(1f),
                    text = "Left Button",
                    interactionSource = leftButtonInteractionSource,
                    onPressed = sendButtonStateUpdate,
                    onReleased = sendButtonStateUpdate
                )
                TouchPadButton(
                    modifier = Modifier.weight(1f),
                    text = "Right Button",
                    interactionSource = rightButtonInteractionSource,
                    onPressed = sendButtonStateUpdate,
                    onReleased = sendButtonStateUpdate
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LandscapeFloatingKeyboardButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localView = LocalView.current
    val isImeVisible = WindowInsets.isImeVisible

    Surface(
        shape = CircleShape,
        color = if (isImeVisible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
        contentColor = if (isImeVisible) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (isImeVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shadowElevation = 4.dp,
        modifier = modifier
            .size(46.dp)
            .pointerInput(isImeVisible) {
                detectTapGestures(
                    onTap = {
                        val currentlyActive = isImeVisible || isKeyboardActive(context, localView)
                        if (currentlyActive) {
                            hideDirectInputSoftKeyboard(context, localView)
                        } else {
                            Toast.makeText(context, "Double tap to open keyboard", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDoubleTap = {
                        val currentlyActive = isImeVisible || isKeyboardActive(context, localView)
                        if (currentlyActive) {
                            hideDirectInputSoftKeyboard(context, localView)
                        } else {
                            showDirectInputSoftKeyboard(context, localView)
                        }
                    }
                )
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                painter = painterResource(R.drawable.keyboard),
                contentDescription = stringResource(R.string.direct_input),
                tint = if (isImeVisible) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun TouchPadButton(
    modifier: Modifier = Modifier,
    text: String,
    interactionSource: MutableInteractionSource,
    onPressed: () -> Unit = {},
    onReleased: () -> Unit = {},
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isPressed: Boolean by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            onPressed()
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
            onReleased()
        }
    }

    val buttonColor by animateColorAsState(
        targetValue = if (isPressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "btnColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isPressed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        label = "btnContentColor"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) ElevationLevel0 else ElevationLevel2,
        label = "btnElevation"
    )

    Surface(
        onClick = {},
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
        shape = RoundedCornerShape(CornerLargeIncreased),
        color = buttonColor,
        border = BorderStroke(
            1.dp,
            if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        shadowElevation = elevation,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Mouse,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}
