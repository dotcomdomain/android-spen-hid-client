package me.arianb.usb_hid_client.input_views

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.coroutines.launch
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.settings.AppPreference
import me.arianb.usb_hid_client.settings.SettingsViewModel
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar

class FullKeyboardScreen : Screen {
    @Composable
    override fun Content() {
        FullKeyboardPage()
    }
}

private data class VirtualKey(
    val label: String,
    val usage: Int,
    val width: Float = 1f,
    val modifier: Int = 0,
    val media: Boolean = false,
)

private data class KeyboardRow(
    val main: List<VirtualKey>,
    val navigation: List<VirtualKey?>,
    val numberPad: List<VirtualKey?>,
)

private val keyboardRows = listOf(
    KeyboardRow(
        main = listOf(
            VirtualKey("`\n~", 0x35), VirtualKey("1\n!", 0x1e), VirtualKey("2\n@", 0x1f),
            VirtualKey("3\n#", 0x20), VirtualKey("4\n$", 0x21), VirtualKey("5\n%", 0x22),
            VirtualKey("6\n^", 0x23), VirtualKey("7\n&", 0x24), VirtualKey("8\n*", 0x25),
            VirtualKey("9\n(", 0x26), VirtualKey("0\n)", 0x27), VirtualKey("-\n_", 0x2d),
            VirtualKey("=\n+", 0x2e), VirtualKey("Backspace", 0x2a, 2f),
        ),
        navigation = listOf(VirtualKey("Insert", 0x49), VirtualKey("Home", 0x4a), VirtualKey("PgUp", 0x4b)),
        numberPad = listOf(VirtualKey("Num", 0x53), VirtualKey("/", 0x54), VirtualKey("*", 0x55), VirtualKey("-", 0x56)),
    ),
    KeyboardRow(
        main = listOf(
            VirtualKey("Tab", 0x2b, 1.5f), VirtualKey("Q", 0x14), VirtualKey("W", 0x1a),
            VirtualKey("E", 0x08), VirtualKey("R", 0x15), VirtualKey("T", 0x17),
            VirtualKey("Y", 0x1c), VirtualKey("U", 0x18), VirtualKey("I", 0x0c),
            VirtualKey("O", 0x12), VirtualKey("P", 0x13), VirtualKey("[\n{", 0x2f),
            VirtualKey("]\n}", 0x30), VirtualKey("\\\n|", 0x31, 1.5f),
        ),
        navigation = listOf(VirtualKey("Delete", 0x4c), VirtualKey("End", 0x4d), VirtualKey("PgDn", 0x4e)),
        numberPad = listOf(VirtualKey("7", 0x5f), VirtualKey("8", 0x60), VirtualKey("9", 0x61), VirtualKey("+", 0x57)),
    ),
    KeyboardRow(
        main = listOf(
            VirtualKey("Caps", 0x39, 1.75f), VirtualKey("A", 0x04), VirtualKey("S", 0x16),
            VirtualKey("D", 0x07), VirtualKey("F", 0x09), VirtualKey("G", 0x0a),
            VirtualKey("H", 0x0b), VirtualKey("J", 0x0d), VirtualKey("K", 0x0e),
            VirtualKey("L", 0x0f), VirtualKey(";\n:", 0x33), VirtualKey("'\n\"", 0x34),
            VirtualKey("Enter", 0x28, 2.25f),
        ),
        navigation = listOf(null, null, null),
        numberPad = listOf(VirtualKey("4", 0x5c), VirtualKey("5", 0x5d), VirtualKey("6", 0x5e), VirtualKey("+", 0x57)),
    ),
    KeyboardRow(
        main = listOf(
            VirtualKey("Shift", 0, 2.25f, modifier = 0x02), VirtualKey("Z", 0x1d),
            VirtualKey("X", 0x1b), VirtualKey("C", 0x06), VirtualKey("V", 0x19),
            VirtualKey("B", 0x05), VirtualKey("N", 0x11), VirtualKey("M", 0x10),
            VirtualKey(",\n<", 0x36), VirtualKey(".\n>", 0x37), VirtualKey("/\n?", 0x38),
            VirtualKey("Shift", 0, 2.75f, modifier = 0x20),
        ),
        navigation = listOf(null, VirtualKey("↑", 0x52), null),
        numberPad = listOf(VirtualKey("1", 0x59), VirtualKey("2", 0x5a), VirtualKey("3", 0x5b), VirtualKey("Enter", 0x58)),
    ),
    KeyboardRow(
        main = listOf(
            VirtualKey("Ctrl", 0, 1.25f, modifier = 0x01),
            VirtualKey("Win", 0, 1.25f, modifier = 0x08),
            VirtualKey("Alt", 0, 1.25f, modifier = 0x04),
            VirtualKey("Space", 0x2c, 6.25f),
            VirtualKey("Alt", 0, 1.25f, modifier = 0x40),
            VirtualKey("Win", 0, 1.25f, modifier = 0x80),
            VirtualKey("Menu", 0x65, 1.25f),
            VirtualKey("Ctrl", 0, 1.25f, modifier = 0x10),
        ),
        navigation = listOf(VirtualKey("←", 0x50), VirtualKey("↓", 0x51), VirtualKey("→", 0x4f)),
        numberPad = listOf(VirtualKey("0", 0x62, 2f), VirtualKey(".", 0x63), VirtualKey("Enter", 0x58)),
    ),
)

private val mediaKeys = listOf(
    VirtualKey("Previous", 0xb6, media = true),
    VirtualKey("Play / pause", 0xcd, 1.5f, media = true),
    VirtualKey("Next", 0xb5, media = true),
    VirtualKey("Mute", 0xe2, media = true),
    VirtualKey("Volume -", 0xea, 1.25f, media = true),
    VirtualKey("Volume +", 0xe9, 1.25f, media = true),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FullKeyboardPage(
    mainViewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val preferences by settingsViewModel.userPreferencesFlow.collectAsState()
    val latchModifiers = preferences.latchFullKeyboardModifiers
    var modifierMask by remember { mutableIntStateOf(0) }
    val pressedKeyUsages = remember { mutableStateListOf<Int>() }
    var pressedMediaUsage by remember { mutableIntStateOf(0) }
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val isCtrlActive = (modifierMask and 0x11) != 0
    val isShiftActive = (modifierMask and 0x22) != 0
    val isAltActive = (modifierMask and 0x44) != 0
    val isWinActive = (modifierMask and 0x88) != 0

    fun sendStandardKeyState() {
        mainViewModel.setStandardKeyState(
            modifierMask.toByte(),
            pressedKeyUsages.getOrElse(0) { 0 }.toByte(),
            pressedKeyUsages.getOrElse(1) { 0 }.toByte(),
        )
    }

    fun tapKey(key: VirtualKey) {
        when {
            key.modifier != 0 -> modifierMask = modifierMask xor key.modifier
            key.media -> {
                mainViewModel.addMediaKey(key.usage.toByte())
                modifierMask = 0
            }
            key.usage != 0 -> {
                mainViewModel.addStandardKey(modifierMask.toByte(), key.usage.toByte())
                modifierMask = 0
            }
        }
    }

    fun pressKey(key: VirtualKey) {
        when {
            key.modifier != 0 -> {
                modifierMask = modifierMask or key.modifier
                sendStandardKeyState()
            }
            key.media -> {
                pressedMediaUsage = key.usage
                mainViewModel.setMediaKeyState(key.usage.toByte())
            }
            key.usage != 0 -> {
                if (key.usage !in pressedKeyUsages && pressedKeyUsages.size < 2) {
                    pressedKeyUsages += key.usage
                    sendStandardKeyState()
                }
            }
        }
    }

    fun releaseKey(key: VirtualKey) {
        when {
            key.modifier != 0 -> {
                modifierMask = modifierMask and key.modifier.inv()
                sendStandardKeyState()
            }
            key.media && pressedMediaUsage == key.usage -> {
                pressedMediaUsage = 0
                mainViewModel.setMediaKeyState(0)
            }
            key.usage != 0 && pressedKeyUsages.remove(key.usage) -> {
                sendStandardKeyState()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mainViewModel.setStandardKeyState(0, 0, 0)
            mainViewModel.setMediaKeyState(0)
        }
    }

    BasicPage(
        topBar = {
            SimpleNavTopBar(
                title = stringResource(R.string.full_keyboard_title),
                actions = {
                    // Quick HUD status pills in landscape mode top bar
                    if (isLandscape) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            ModifierBadge("CTRL", isCtrlActive) {
                                modifierMask = modifierMask xor 0x01
                                sendStandardKeyState()
                            }
                            ModifierBadge("SHIFT", isShiftActive) {
                                modifierMask = modifierMask xor 0x02
                                sendStandardKeyState()
                            }
                            ModifierBadge("ALT", isAltActive) {
                                modifierMask = modifierMask xor 0x04
                                sendStandardKeyState()
                            }
                            ModifierBadge("WIN", isWinActive) {
                                modifierMask = modifierMask xor 0x08
                                sendStandardKeyState()
                            }
                        }
                    }

                    // Latch toggle chip
                    FilterChip(
                        selected = latchModifiers,
                        onClick = {
                            settingsViewModel.setPreference(
                                AppPreference.LatchFullKeyboardModifiers,
                                !latchModifiers
                            )
                        },
                        label = {
                            Text(
                                text = if (latchModifiers) "Latch On" else "Latch Off",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (latchModifiers) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )

                    // Clear button when modifiers are engaged
                    if (modifierMask != 0) {
                        IconButton(
                            onClick = {
                                modifierMask = 0
                                sendStandardKeyState()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear modifiers",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
        },
        padding = PaddingValues(if (isLandscape) 4.dp else 8.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val rowSpacing = if (isLandscape) 3.dp else 5.dp
            val keyHeight = if (isLandscape) {
                ((maxHeight - 20.dp - (rowSpacing * 6)) / 7.2f).coerceIn(30.dp, 44.dp)
            } else {
                44.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScroll)
                    .verticalScroll(verticalScroll),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                // Portrait Modifier Status Card & Helper Info
                if (!isLandscape) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            // Active Modifier Badges
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ModifierBadge("CTRL", isCtrlActive) {
                                    modifierMask = modifierMask xor 0x01
                                    sendStandardKeyState()
                                }
                                ModifierBadge("SHIFT", isShiftActive) {
                                    modifierMask = modifierMask xor 0x02
                                    sendStandardKeyState()
                                }
                                ModifierBadge("ALT", isAltActive) {
                                    modifierMask = modifierMask xor 0x04
                                    sendStandardKeyState()
                                }
                                ModifierBadge("WIN", isWinActive) {
                                    modifierMask = modifierMask xor 0x08
                                    sendStandardKeyState()
                                }
                            }

                            // Dynamic Mode Hint Pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = if (modifierMask != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = when {
                                        modifierMask != 0 && latchModifiers -> "Modifier locked for next key"
                                        latchModifiers -> "Tap modifier to latch"
                                        else -> "Hold modifier + key together"
                                    },
                                    color = if (modifierMask != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (modifierMask != 0) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                // Keyboard Chassis / Deck
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(rowSpacing)
                    ) {
                        // Function Keys Row
                        FunctionKeyRow(
                            modifierMask = modifierMask,
                            latchModifiers = latchModifiers,
                            keyHeight = keyHeight,
                            onKeyTapped = ::tapKey,
                            onKeyPressed = ::pressKey,
                            onKeyReleased = ::releaseKey,
                        )

                        // Main typing, navigation, and numpad rows
                        keyboardRows.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                KeyboardKeyRow(
                                    keys = row.main,
                                    modifierMask = modifierMask,
                                    latchModifiers = latchModifiers,
                                    keyHeight = keyHeight,
                                    onKeyTapped = ::tapKey,
                                    onKeyPressed = ::pressKey,
                                    onKeyReleased = ::releaseKey,
                                )
                                KeyboardKeyRow(
                                    keys = row.navigation,
                                    modifierMask = modifierMask,
                                    latchModifiers = latchModifiers,
                                    keyHeight = keyHeight,
                                    onKeyTapped = ::tapKey,
                                    onKeyPressed = ::pressKey,
                                    onKeyReleased = ::releaseKey,
                                )
                                KeyboardKeyRow(
                                    keys = row.numberPad,
                                    modifierMask = modifierMask,
                                    latchModifiers = latchModifiers,
                                    keyHeight = keyHeight,
                                    onKeyTapped = ::tapKey,
                                    onKeyPressed = ::pressKey,
                                    onKeyReleased = ::releaseKey,
                                )
                            }
                        }

                        // Media keys & Section Jump Navigation strip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            // Media badge & keys
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.height(keyHeight - 2.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = "MEDIA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            KeyboardKeyRow(
                                keys = mediaKeys,
                                modifierMask = modifierMask,
                                latchModifiers = latchModifiers,
                                keyHeight = keyHeight,
                                onKeyTapped = ::tapKey,
                                onKeyPressed = ::pressKey,
                                onKeyReleased = ::releaseKey,
                            )

                            // Quick Section Jump Chips
                            Spacer(Modifier.width(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.height(keyHeight - 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    val maxScroll = horizontalScroll.maxValue.coerceAtLeast(1)
                                    SectionJumpChip("Main Typing", horizontalScroll.value < maxScroll * 0.25f) {
                                        coroutineScope.launch { horizontalScroll.animateScrollTo(0) }
                                    }
                                    SectionJumpChip("Arrows & Nav", horizontalScroll.value in (maxScroll * 0.25f).toInt()..(maxScroll * 0.70f).toInt()) {
                                        coroutineScope.launch {
                                            horizontalScroll.animateScrollTo((maxScroll * 0.55f).toInt())
                                        }
                                    }
                                    SectionJumpChip("Numpad", horizontalScroll.value > maxScroll * 0.70f) {
                                        coroutineScope.launch { horizontalScroll.animateScrollTo(maxScroll) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionJumpChip(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ModifierBadge(
    name: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = name,
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FunctionKeyRow(
    modifierMask: Int,
    latchModifiers: Boolean,
    keyHeight: Dp,
    onKeyTapped: (VirtualKey) -> Unit,
    onKeyPressed: (VirtualKey) -> Unit,
    onKeyReleased: (VirtualKey) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // Esc
        Keycap(VirtualKey("Esc", 0x29), modifierMask, latchModifiers, keyHeight, onKeyTapped, onKeyPressed, onKeyReleased)

        // Gap after Esc (calibrated to align with ANSI standard typing cluster)
        Spacer(Modifier.width(28.dp))

        // F1 - F4
        listOf(
            VirtualKey("F1", 0x3a), VirtualKey("F2", 0x3b),
            VirtualKey("F3", 0x3c), VirtualKey("F4", 0x3d)
        ).forEach { key ->
            Keycap(key, modifierMask, latchModifiers, keyHeight, onKeyTapped, onKeyPressed, onKeyReleased)
        }

        // Gap between F4 and F5
        Spacer(Modifier.width(38.dp))

        // F5 - F8
        listOf(
            VirtualKey("F5", 0x3e), VirtualKey("F6", 0x3f),
            VirtualKey("F7", 0x40), VirtualKey("F8", 0x41)
        ).forEach { key ->
            Keycap(key, modifierMask, latchModifiers, keyHeight, onKeyTapped, onKeyPressed, onKeyReleased)
        }

        // Gap between F8 and F9
        Spacer(Modifier.width(38.dp))

        // F9 - F12
        listOf(
            VirtualKey("F9", 0x42), VirtualKey("F10", 0x43),
            VirtualKey("F11", 0x44), VirtualKey("F12", 0x45)
        ).forEach { key ->
            Keycap(key, modifierMask, latchModifiers, keyHeight, onKeyTapped, onKeyPressed, onKeyReleased)
        }

        // Gap to align with Navigation Cluster
        Spacer(Modifier.width(18.dp))

        // PrtSc, ScrLk, Pause
        listOf(
            VirtualKey("PrtSc", 0x46),
            VirtualKey("ScrLk", 0x47),
            VirtualKey("Pause", 0x48)
        ).forEach { key ->
            Keycap(key, modifierMask, latchModifiers, keyHeight, onKeyTapped, onKeyPressed, onKeyReleased)
        }
    }
}

@Composable
private fun KeyboardKeyRow(
    keys: List<VirtualKey?>,
    modifierMask: Int,
    latchModifiers: Boolean,
    keyHeight: Dp,
    onKeyTapped: (VirtualKey) -> Unit,
    onKeyPressed: (VirtualKey) -> Unit,
    onKeyReleased: (VirtualKey) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { key ->
            if (key == null) {
                Spacer(Modifier.width(46.dp).height(keyHeight))
            } else {
                Keycap(
                    key = key,
                    modifierMask = modifierMask,
                    latchModifiers = latchModifiers,
                    keyHeight = keyHeight,
                    onKeyTapped = onKeyTapped,
                    onKeyPressed = onKeyPressed,
                    onKeyReleased = onKeyReleased,
                )
            }
        }
    }
}

@Composable
private fun Keycap(
    key: VirtualKey,
    modifierMask: Int,
    latchModifiers: Boolean,
    keyHeight: Dp,
    onKeyTapped: (VirtualKey) -> Unit,
    onKeyPressed: (VirtualKey) -> Unit,
    onKeyReleased: (VirtualKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    var physicallyPressed by remember(key) { mutableStateOf(false) }
    val isLatched = key.modifier != 0 && (modifierMask and key.modifier != 0)
    val isSelected = physicallyPressed || isLatched
    val haptic = LocalHapticFeedback.current

    // Tactile press depression: surface animates down on touch
    val pressOffsetY by animateDpAsState(
        targetValue = if (physicallyPressed) 2.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "keyOffset"
    )

    val isEsc = key.usage == 0x29
    val isEnter = key.usage == 0x28 || key.usage == 0x58
    val isModifier = key.modifier != 0
    val isMedia = key.media
    val isFn = key.usage in 0x3a..0x48

    // Elegant color hierarchy
    val baseSurfaceColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isEsc -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        isEnter -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        isModifier -> MaterialTheme.colorScheme.surfaceContainerHighest
        isMedia -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        isFn -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
            isEsc -> MaterialTheme.colorScheme.onErrorContainer
            isEnter -> MaterialTheme.colorScheme.onPrimaryContainer
            isMedia -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "keyContentColor"
    )

    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isEnter -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        isEsc -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    val bevelColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceDim.copy(alpha = 0.85f)
    }

    val keyShape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .width(46.dp * key.width)
            .height(keyHeight)
            .pointerInput(key, latchModifiers) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val activePointers = mutableSetOf(down.id)
                    val isLatchedModifier = latchModifiers && key.modifier != 0
                    physicallyPressed = true
                    var keyPressSent = false
                    try {
                        if (!isLatchedModifier) {
                            onKeyPressed(key)
                            keyPressSent = true
                        }

                        while (activePointers.isNotEmpty()) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { change ->
                                if (change.id in activePointers) {
                                    if (!change.pressed) {
                                        activePointers -= change.id
                                    }
                                    change.consume()
                                } else if (
                                    change.changedToDownIgnoreConsumed() &&
                                    change.position.x >= 0f && change.position.x < size.width &&
                                    change.position.y >= 0f && change.position.y < size.height
                                ) {
                                    activePointers += change.id
                                    change.consume()
                                }
                            }
                        }

                        if (isLatchedModifier) {
                            onKeyTapped(key)
                        }
                    } finally {
                        if (keyPressSent) {
                            onKeyReleased(key)
                        }
                        physicallyPressed = false
                    }
                }
            }
            .semantics {
                role = Role.Button
                onClick {
                    if (latchModifiers && key.modifier != 0) {
                        onKeyTapped(key)
                    } else {
                        onKeyPressed(key)
                        onKeyReleased(key)
                    }
                    true
                }
            }
    ) {
        // 1. Bottom Bevel/Shadow Lip (physical chiclet depth)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeight)
                .padding(top = 2.dp)
                .background(bevelColor, keyShape)
        )

        // 2. Keycap Surface (depresses smoothly on touch)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeight - 2.dp)
                .offset(y = pressOffsetY)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            baseSurfaceColor,
                            baseSurfaceColor.copy(alpha = 0.9f)
                        )
                    ),
                    shape = keyShape
                )
                .border(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = keyShape
                ),
            contentAlignment = Alignment.Center
        ) {
            KeycapContent(
                key = key,
                isSelected = isSelected,
                contentColor = contentColor,
            )
        }
    }
}

@Composable
private fun KeycapContent(
    key: VirtualKey,
    isSelected: Boolean,
    contentColor: Color,
) {
    when {
        // Media Keys with Material vector icons
        key.media -> {
            val icon = when (key.usage) {
                0xb6 -> Icons.Filled.SkipPrevious
                0xcd -> Icons.Filled.PlayArrow
                0xb5 -> Icons.Filled.SkipNext
                0xe2 -> Icons.Filled.VolumeOff
                0xea -> Icons.Filled.VolumeDown
                0xe9 -> Icons.Filled.VolumeUp
                else -> null
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = key.label,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = key.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }

        // Navigation Arrow Keys with vector icons
        key.label in listOf("↑", "↓", "←", "→") -> {
            val arrowIcon = when (key.label) {
                "↑" -> Icons.Filled.KeyboardArrowUp
                "↓" -> Icons.Filled.KeyboardArrowDown
                "←" -> Icons.Filled.KeyboardArrowLeft
                "→" -> Icons.Filled.KeyboardArrowRight
                else -> null
            }
            if (arrowIcon != null) {
                Icon(
                    imageVector = arrowIcon,
                    contentDescription = key.label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Backspace
        key.usage == 0x2a -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Bksp",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }

        // Main Enter
        key.usage == 0x28 -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = "Enter",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Enter",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }

        // Numpad Enter (compact width)
        key.usage == 0x58 -> {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                contentDescription = "Enter",
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        // Tab
        key.usage == 0x2b -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardTab,
                    contentDescription = "Tab",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Tab",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }

        // Shift
        key.modifier == 0x02 || key.modifier == 0x20 -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.North,
                    contentDescription = "Shift",
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Shift",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }

        // Caps Lock with LED indicator
        key.usage == 0x39 -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(
                            color = if (isSelected) Color(0xFF00E5FF) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                )
                Text(
                    text = "Caps",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }

        // Windows Key
        key.label == "Win" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    val gap = 1.5.dp.toPx()
                    val sz = (size.width - gap) / 2f
                    val r = 1.dp.toPx()
                    drawRoundRect(contentColor, Offset(0f, 0f), Size(sz, sz), CornerRadius(r))
                    drawRoundRect(contentColor, Offset(sz + gap, 0f), Size(sz, sz), CornerRadius(r))
                    drawRoundRect(contentColor, Offset(0f, sz + gap), Size(sz, sz), CornerRadius(r))
                    drawRoundRect(contentColor, Offset(sz + gap, sz + gap), Size(sz, sz), CornerRadius(r))
                }
                Text(
                    text = "Win",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }

        // Menu Key
        key.usage == 0x65 -> {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Menu",
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
        }

        // Space Bar
        key.usage == 0x2c -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(3.dp)
                        .background(
                            contentColor.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(1.5.dp)
                        )
                )
            }
        }

        // Dual-legend keys (like 1 / !, [ / {, ; / :, etc.)
        '\n' in key.label -> {
            val baseChar = key.label.substringBefore('\n')
            val shiftChar = key.label.substringAfter('\n')
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = shiftChar,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = baseChar,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Standard Alpha, Numbers, or Function keys
        else -> {
            Text(
                text = if (key.usage == 0x29) "ESC" else key.label,
                fontSize = when {
                    key.label.length > 4 -> 10.sp
                    key.label.startsWith("F") && key.label.length > 2 -> 10.sp
                    else -> 12.sp
                },
                fontWeight = if (isSelected || key.modifier != 0 || key.usage == 0x29) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}
