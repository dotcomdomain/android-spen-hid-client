package me.arianb.usb_hid_client.troubleshooting

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.shell_utils.RootMethod
import me.arianb.usb_hid_client.ui.theme.CornerExtraLarge
import me.arianb.usb_hid_client.ui.theme.CornerLargeIncreased
import me.arianb.usb_hid_client.ui.theme.CornerMedium
import me.arianb.usb_hid_client.ui.theme.M3SuccessGreen
import me.arianb.usb_hid_client.ui.theme.M3WarningAmber
import me.arianb.usb_hid_client.ui.theme.PaddingSmall
import me.arianb.usb_hid_client.ui.theme.codeLineHeightScaleFactor
import me.arianb.usb_hid_client.ui.theme.codeStyle
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.utils.Experimental
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar
import timber.log.Timber

class TroubleshootingScreen : Screen {
    @Composable
    override fun Content() {
        TroubleshootingPage()
    }
}

@Composable
fun TroubleshootingPage() {
    BasicPage(
        topBar = { TroubleshootingTopBar() },
        scrollable = true,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top)
    ) {
        Experimental {
            TroubleshootingCategoryCard(title = "Gadget Actions") {
                GadgetActionButtons()
            }
        }

        TroubleshootingCategoryCard(title = "Diagnostics & System Health") {
            DebuggingInfoList()
        }

        TroubleshootingCategoryCard(title = "Logs") {
            ExportLogsPreferenceButton()
        }
    }
}

@Composable
private fun TroubleshootingCategoryCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                content()
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TroubleshootingTopBar() {
    SimpleNavTopBar(
        title = stringResource(R.string.troubleshooting_title)
    )
}

@Composable
private fun GadgetActionButtons(mainViewModel: MainViewModel = viewModel()) {
    var isShowingConfirmationAlert by remember { mutableStateOf(false) }
    val state by mainViewModel.uiState.collectAsState()

    val runOnClick: () -> Unit
    val actionLabel: String
    if (state.missingCharacterDevice) {
        runOnClick = { mainViewModel.createCharacterDevices() }
        actionLabel = "Create Character Devices"
    } else {
        runOnClick = { mainViewModel.deleteCharacterDevices() }
        actionLabel = "Delete Character Devices"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        FilledTonalButton(
            onClick = { isShowingConfirmationAlert = true },
            shape = RoundedCornerShape(CornerLargeIncreased),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.Build,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(actionLabel, fontWeight = FontWeight.SemiBold)
        }
    }

    if (isShowingConfirmationAlert) {
        AlertDialog(
            shape = RoundedCornerShape(CornerExtraLarge),
            title = {
                Text(
                    text = "Confirm Action",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(text = "Are you sure you want to: $actionLabel?") },
            onDismissRequest = { isShowingConfirmationAlert = false },
            confirmButton = {
                Button(
                    onClick = {
                        runOnClick()
                        isShowingConfirmationAlert = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { isShowingConfirmationAlert = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DebuggingInfoList() {
    val troubleshootingInfo = detectIssues()
    Timber.d("debug info: %s", troubleshootingInfo.toString())

    with(troubleshootingInfo.rootPermissionInfo) {
        GadgetStatusItem(
            title = "Root Permissions",
            summary = "Method: ${rootMethod.name}",
            isGood = hasRootPermissions && rootMethod != RootMethod.UNKNOWN
        )
    }

    troubleshootingInfo.characterDevicesInfoList?.let {
        for (characterDevice in it) {
            GadgetStatusItem(
                title = "Character Device",
                summary = characterDevice.path,
                extraInfo = AnnotatedString(characterDevice.permissions ?: "Failed to read permissions"),
                isGood = characterDevice.isPresent && characterDevice.isVisibleWithoutRoot && characterDevice.permissions != null
            )
        }
    }

    troubleshootingInfo.kernelInfo?.let {
        GadgetStatusItem(
            title = "Kernel Support",
            summary = "Version: ${it.version}\nConfigFS: ${it.hasConfigFsSupport ?: "Unknown"} · HID: ${it.hasConfigFsHidFunctionSupport ?: "Unknown"}",
            extraInfo = it.kernelConfigAnnotated,
            isGood = if (it.hasConfigFsSupport == null || it.hasConfigFsHidFunctionSupport == null) {
                null
            } else {
                it.hasConfigFsSupport && it.hasConfigFsHidFunctionSupport
            }
        )
    }
}

@Composable
private fun GadgetStatusItem(
    title: String,
    summary: String? = null,
    isGood: Boolean?,
    extraInfo: AnnotatedString,
) {
    var isShowingInfoAlert by remember { mutableStateOf(false) }

    GadgetStatusItem(
        title = title,
        summary = summary,
        isGood = isGood,
        additionalTrailingContent = {
            IconButton(onClick = { isShowingInfoAlert = !isShowingInfoAlert }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = "Details"
                )
            }
        }
    )

    if (isShowingInfoAlert) {
        val clipboardManager = LocalClipboard.current.nativeClipboard

        AlertDialog(
            shape = RoundedCornerShape(CornerExtraLarge),
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Surface(
                    shape = RoundedCornerShape(CornerMedium),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        CodeText(extraInfo)
                    }
                }
            },
            onDismissRequest = { isShowingInfoAlert = false },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        clipboardManager.text = extraInfo
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { isShowingInfoAlert = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun GadgetStatusItem(
    title: String,
    summary: String?,
    isGood: Boolean?,
    additionalTrailingContent: @Composable (() -> Unit)? = null
) {
    val statusColor = when (isGood) {
        true -> M3SuccessGreen
        false -> MaterialTheme.colorScheme.error
        null -> M3WarningAmber
    }

    val statusBgColor = when (isGood) {
        true -> M3SuccessGreen.copy(alpha = 0.15f)
        false -> MaterialTheme.colorScheme.errorContainer
        null -> M3WarningAmber.copy(alpha = 0.15f)
    }

    val statusIcon = when (isGood) {
        true -> Icons.Default.Check
        false -> Icons.Outlined.PriorityHigh
        null -> Icons.Outlined.HelpOutline
    }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                additionalTrailingContent?.invoke()

                Surface(
                    shape = CircleShape,
                    color = statusBgColor,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = statusIcon,
                            tint = statusColor,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun CodeText(text: AnnotatedString) {
    AutoResizeText(text, style = codeStyle)
}

@Composable
fun AutoResizeText(text: AnnotatedString, style: TextStyle, minFontSize: TextUnit = 8.sp) {
    var readyToDraw by remember { mutableStateOf(false) }
    var textStyle by remember { mutableStateOf(style) }
    Text(
        text,
        style = textStyle,
        softWrap = false,
        modifier = Modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        },
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowWidth && textStyle.fontSize > minFontSize) {
                Timber.d("fixing font size, current value: %s", textStyle.fontSize)
                textStyle = textStyle.copy(
                    fontSize = textStyle.fontSize * 0.95,
                    lineHeight = textStyle.fontSize * codeLineHeightScaleFactor
                )
            } else {
                Timber.d("FONT SIZE THAT FITS: %s", textStyle.fontSize)
                readyToDraw = true
            }
        },
    )
}

@DarkLightModePreviews
@Composable
private fun GadgetConfigurationScreenPreview() {
    Navigator(TroubleshootingScreen())
}
