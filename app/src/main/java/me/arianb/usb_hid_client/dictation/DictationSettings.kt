package me.arianb.usb_hid_client.dictation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import me.arianb.usb_hid_client.settings.PreferenceDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictationSettings() {
    val context = LocalContext.current
    val options by DictationPreferences.options.collectAsState()
    val status by DictationState.status.collectAsState()
    val inputError by RawInputMonitor.error.collectAsState()
    var remap by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    val modelExists = status.modelReady || DictationPreferences.modelFile(context).isFile

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            DictationPreferences.save(DictationPreferences.options.value.copy(enabled = true))
            DictationService.start(context)
        }
    }

    fun toggleVoiceDictation(enabled: Boolean) {
        if (!enabled) {
            DictationPreferences.save(options.copy(enabled = false))
            context.stopService(Intent(context, DictationService::class.java))
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            DictationPreferences.save(options.copy(enabled = true))
            DictationService.start(context)
        } else {
            permissionLauncher.launch(
                buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray()
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Master Toggle Row
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = MaterialTheme.colorScheme.onSurface,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            headlineContent = {
                Text(
                    text = "Voice dictation",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal
                )
            },
            supportingContent = {
                Text(
                    text = "Hold hardware button to speak and type",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            },
            trailingContent = {
                Switch(
                    checked = options.enabled,
                    thumbContent = {
                        if (options.enabled) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    },
                    onCheckedChange = { toggleVoiceDictation(it) }
                )
            },
            modifier = Modifier.clickable { toggleVoiceDictation(!options.enabled) }
        )

        PreferenceDivider()
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = MaterialTheme.colorScheme.onSurface,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            headlineContent = {
                Text(
                    text = "Replace pending dictation",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal
                )
            },
            supportingContent = {
                Text(
                    text = if (options.replacePending) {
                        "Cancel pending dictation on new hold"
                    } else {
                        "Queue earlier recordings in order"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = {
                Switch(
                    checked = options.replacePending,
                    thumbContent = {
                        if (options.replacePending) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    },
                    onCheckedChange = {
                        DictationPreferences.save(options.copy(replacePending = it))
                    }
                )
            },
            modifier = Modifier.clickable {
                DictationPreferences.save(options.copy(replacePending = !options.replacePending))
            }
        )

        if (!options.replacePending) {
            PreferenceDivider()
            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    headlineColor = MaterialTheme.colorScheme.onSurface,
                    supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                headlineContent = {
                    Text(
                        text = "Parallel transcription",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal
                    )
                },
                supportingContent = {
                    Text(
                        text = if (options.parallel) {
                            "Decode clips together concurrently"
                        } else {
                            "Queue and process clips one by one"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingContent = {
                    Switch(
                        checked = options.parallel,
                        thumbContent = {
                            if (options.parallel) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        },
                        onCheckedChange = {
                            DictationPreferences.save(options.copy(parallel = it))
                        }
                    )
                },
                modifier = Modifier.clickable { DictationPreferences.save(options.copy(parallel = !options.parallel)) }
            )
            if (options.parallel) {
                val isDefaultLimit = options.parallelLimit == 2
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Parallel limit",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Higher limits use more memory and CPU",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!isDefaultLimit) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            DictationPreferences.save(options.copy(parallelLimit = 2))
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Reset to default",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (!isDefaultLimit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = if (!isDefaultLimit) {
                                    Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            DictationPreferences.save(options.copy(parallelLimit = 2))
                                        }
                                } else Modifier
                            ) {
                                Text(
                                    text = "${options.parallelLimit} clips",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isDefaultLimit) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    Slider(
                        value = options.parallelLimit.toFloat(),
                        onValueChange = { raw ->
                            DictationPreferences.save(options.copy(parallelLimit = kotlin.math.round(raw).toInt()))
                        },
                        valueRange = 1f..5f,
                        steps = 3,
                        thumb = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(20.dp),
                                border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.surface)
                            ) {}
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1 (Min)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "2 (Default)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                        Text(
                            text = "5 (Max)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // 2. Whisper Speech Model Row
        PreferenceDivider()
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = MaterialTheme.colorScheme.onSurface,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            headlineContent = {
                Text(
                    text = "Whisper speech model",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal
                )
            },
            supportingContent = {
                Text(
                    text = when {
                        status.phase == DictationPhase.DOWNLOADING -> "Downloading model… ${(status.progress * 100).toInt()}%"
                        modelExists -> "Whisper base (57 MB)"
                        else -> "Offline speech recognition model"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            },
            trailingContent = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.height(32.dp)
                ) {
                    when {
                        status.phase == DictationPhase.DOWNLOADING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        modelExists -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Installed",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = { DictationService.start(context, download = true) },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp),
                                enabled = status.phase !in setOf(
                                    DictationPhase.DOWNLOADING,
                                    DictationPhase.RECORDING,
                                    DictationPhase.PROCESSING,
                                    DictationPhase.SENDING
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Download",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.clickable {
                if (status.phase == DictationPhase.DOWNLOADING) return@clickable
                if (modelExists) {
                    showModelDialog = true
                } else {
                    DictationService.start(context, download = true)
                }
            }
        )

        if (status.phase == DictationPhase.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { status.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }

        // 3. Hardware Activation Button Row
        PreferenceDivider()
        val buttonDescription = when {
            options.device == "gpio_keys" && options.code == 703 -> "Bixby button (Key 703)"
            options.code == 114 -> "Volume Down (Key 114)"
            options.code == 115 -> "Volume Up (Key 115)"
            else -> "${options.device} (Key ${options.code})"
        }

        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = MaterialTheme.colorScheme.onSurface,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            headlineContent = {
                Text(
                    text = "Activation button",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal
                )
            },
            supportingContent = {
                Text(
                    text = buttonDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            },
            trailingContent = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.height(32.dp)
                ) {
                    FilledTonalButton(
                        onClick = { remap = true },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                        enabled = status.phase !in setOf(
                            DictationPhase.RECORDING,
                            DictationPhase.PROCESSING,
                            DictationPhase.SENDING
                        )
                    ) {
                        Text(
                            text = "Remap",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            modifier = Modifier.clickable {
                if (status.phase !in setOf(DictationPhase.RECORDING, DictationPhase.PROCESSING, DictationPhase.SENDING)) {
                    remap = true
                }
            }
        )

        // 4. Floating Overlay Indicator Row
        PreferenceDivider()
        val canDrawOverlays = remember(context) { Settings.canDrawOverlays(context) }

        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = MaterialTheme.colorScheme.onSurface,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            headlineContent = {
                Text(
                    text = "Floating indicator",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal
                )
            },
            supportingContent = {
                Text(
                    text = "Show status pill over other apps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            },
            trailingContent = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.height(32.dp)
                ) {
                    if (canDrawOverlays) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Allowed",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Grant",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            modifier = Modifier.clickable {
                if (!canDrawOverlays) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }
        )

        // Error Alert Banner (only if root/hardware failure occurred)
        if (inputError != null) {
            PreferenceDivider()
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = inputError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Whisper speech model",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "The Whisper base speech model (57 MB) is already installed. Do you want to download it again?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showModelDialog = false
                        DictationService.start(context, download = true)
                    }
                ) {
                    Text("Re-download", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (remap) {
        InputMappingDialog(onDismiss = { remap = false })
    }
}

@Composable
private fun InputMappingDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val options = DictationPreferences.options.value
    var device by remember { mutableStateOf(options.device) }
    var type by remember { mutableStateOf(options.type.toString()) }
    var code by remember { mutableStateOf(options.code.toString()) }
    var learning by remember { mutableStateOf(false) }
    var capturedInfo by remember { mutableStateOf<String?>(null) }
    val owner = remember { Any() }

    DisposableEffect(Unit) {
        RawInputMonitor.captureActive = true
        onDispose {
            RawInputMonitor.captureActive = false
            RawInputMonitor.release(owner)
        }
    }

    LaunchedEffect(learning) {
        if (learning) {
            RawInputMonitor.acquire(context, owner)
            RawInputMonitor.events.collect { button ->
                if (button.value == 1 && learning) {
                    device = button.device
                    type = button.type.toString()
                    code = button.code.toString()
                    capturedInfo = "Detected: ${button.device} (Code ${button.code})"
                    learning = false
                    RawInputMonitor.release(owner)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (learning) RawInputMonitor.release(owner)
            onDismiss()
        },
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = "Activation button",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Interactive Detection Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (learning) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (learning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        if (learning) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (learning) Icons.Filled.Sensors else Icons.Outlined.Sensors,
                                    contentDescription = null,
                                    tint = if (learning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (learning) "Listening for button…" else "Automatic detection",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (learning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (learning) {
                                        "Press any hardware button now"
                                    } else {
                                        capturedInfo ?: "Press a hardware button to detect key"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (learning) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (learning) {
                            OutlinedButton(
                                onClick = {
                                    learning = false
                                    RawInputMonitor.release(owner)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel listening")
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    learning = true
                                    capturedInfo = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Detect button")
                            }
                        }
                    }
                }

                // Manual Input Fields
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Manual configuration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = device,
                        onValueChange = { device = it },
                        label = { Text("Device name") },
                        placeholder = { Text("gpio_keys") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("Key code") },
                            placeholder = { Text("703") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.3f)
                        )
                        OutlinedTextField(
                            value = type,
                            onValueChange = { type = it },
                            label = { Text("Event type") },
                            placeholder = { Text("1") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isValid = device.isNotBlank() &&
                    type.toIntOrNull() in listOf(1, 5) &&
                    code.toIntOrNull()?.let { it in 0..767 } == true

            Button(
                enabled = isValid,
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    DictationPreferences.save(
                        DictationPreferences.options.value.copy(
                            device = device.trim(),
                            type = type.toInt(),
                            code = code.toInt()
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (learning) RawInputMonitor.release(owner)
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DictationIndicator() {
    val status by DictationState.status.collectAsState()
    AnimatedVisibility(
        visible = status.indicatorVisible && !status.overlayVisible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 }
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.padding(top = 48.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = when (status.phase) {
                        DictationPhase.RECORDING -> Icons.Filled.Mic
                        DictationPhase.PROCESSING, DictationPhase.SENDING -> Icons.Filled.GraphicEq
                        DictationPhase.ERROR -> Icons.Filled.Warning
                        else -> Icons.Filled.Info
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
