package me.arianb.usb_hid_client.input_views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
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

private val functionKeys = buildList {
    add(VirtualKey("Esc", 0x29))
    for (number in 1..12) {
        add(VirtualKey("F$number", 0x39 + number))
    }
    add(VirtualKey("PrtSc", 0x46))
    add(VirtualKey("ScrLk", 0x47))
    add(VirtualKey("Pause", 0x48))
}

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
        numberPad = listOf(VirtualKey("4", 0x5c), VirtualKey("5", 0x5d), VirtualKey("6", 0x5e), null),
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
        numberPad = listOf(VirtualKey("0", 0x62, 2f), VirtualKey(".", 0x63), null),
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
private fun FullKeyboardPage(mainViewModel: MainViewModel = viewModel()) {
    var modifierMask by rememberSaveable { mutableIntStateOf(0) }
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()

    fun pressKey(key: VirtualKey) {
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

    BasicPage(
        topBar = { SimpleNavTopBar(title = androidx.compose.ui.res.stringResource(R.string.full_keyboard_title)) },
        padding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScroll)
                .verticalScroll(verticalScroll),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (modifierMask == 0) {
                    androidx.compose.ui.res.stringResource(R.string.full_keyboard_modifier_hint)
                } else {
                    androidx.compose.ui.res.stringResource(R.string.full_keyboard_modifier_active)
                },
                color = if (modifierMask == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )

            KeyboardKeyRow(functionKeys, modifierMask, ::pressKey)

            Spacer(Modifier.height(2.dp))
            keyboardRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KeyboardKeyRow(row.main, modifierMask, ::pressKey)
                    KeyboardKeyRow(row.navigation, modifierMask, ::pressKey)
                    KeyboardKeyRow(row.numberPad, modifierMask, ::pressKey)
                }
            }

            Spacer(Modifier.height(2.dp))
            KeyboardKeyRow(mediaKeys, modifierMask, ::pressKey)
        }
    }
}

@Composable
private fun KeyboardKeyRow(
    keys: List<VirtualKey?>,
    modifierMask: Int,
    onKeyPressed: (VirtualKey) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { key ->
            if (key == null) {
                Spacer(Modifier.size(46.dp))
            } else {
                val selected = key.modifier != 0 && modifierMask and key.modifier != 0
                Surface(
                    onClick = { onKeyPressed(key) },
                    modifier = Modifier
                        .width(46.dp * key.width)
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = key.label,
                            textAlign = TextAlign.Center,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = if ('\n' in key.label) 10.sp else 11.sp,
                            lineHeight = 12.sp,
                            modifier = Modifier.padding(horizontal = 3.dp),
                        )
                    }
                }
            }
        }
    }
}
