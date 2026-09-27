package me.arianb.usb_hid_client.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import kotlin.math.abs
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.ui.theme.CornerExtraLarge
import me.arianb.usb_hid_client.ui.theme.CornerLargeIncreased
import me.arianb.usb_hid_client.ui.theme.CornerMedium
import me.arianb.usb_hid_client.ui.theme.PaddingSmall
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.reflect.KProperty1

/**
 * Modern Grouped Preference Category Card matching the reference screenshot.
 * Displays category title with cyan secondary accent and a rounded container card.
 */
@Composable
fun PreferenceCategory(
    title: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    preferences: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp)
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
                preferences()
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Hairline divider between preferences inside a category container.
 */
@Composable
fun PreferenceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

@Composable
fun SwitchPreference(
    title: String,
    summary: String? = null,
    preference: BooleanPreferenceKey,
    viewModel: SettingsViewModel = viewModel()
) {
    var isChecked by remember { mutableStateOf(viewModel.getPreference(preference)) }

    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal
            )
        },
        supportingContent = {
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        trailingContent = {
            Switch(
                checked = isChecked,
                thumbContent = {
                    if (isChecked) {
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
                    isChecked = it
                    viewModel.setPreference(preference, isChecked)
                },
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensitivityPreference(
    title: String,
    preference: FloatPreferenceKey,
    summary: String? = null,
    viewModel: SettingsViewModel = viewModel()
) {
    var value by remember(preference.key) {
        mutableFloatStateOf(viewModel.getPreference(preference).coerceIn(0.25f, 4f))
    }
    val isDefault = abs(value - preference.defaultValue) < 0.01f

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
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isDefault) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable {
                                value = preference.defaultValue
                                viewModel.resetPreferenceToDefault(preference)
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
                    color = if (!isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = if (!isDefault) {
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                value = preference.defaultValue
                                viewModel.resetPreferenceToDefault(preference)
                            }
                    } else Modifier
                ) {
                    Text(
                        text = String.format(Locale.US, "%.2f×", value),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (!isDefault) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        Slider(
            value = value,
            onValueChange = { raw ->
                value = (raw * 20f).roundToInt() / 20f
                viewModel.setPreference(preference, value)
            },
            valueRange = 0.25f..4f,
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
                text = "0.25× (Slow)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = "1.00× (Default)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
            Text(
                text = "4.00× (Fast)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoverRangePreference(
    title: String,
    preference: FloatPreferenceKey,
    summary: String? = null,
    viewModel: SettingsViewModel = viewModel()
) {
    var value by remember(preference.key) {
        mutableFloatStateOf(viewModel.getPreference(preference).coerceIn(0.1f, 1f))
    }
    val isDefault = abs(value - preference.defaultValue) < 0.01f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isDefault) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable {
                                value = preference.defaultValue
                                viewModel.resetPreferenceToDefault(preference)
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
                    color = if (!isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = if (!isDefault) {
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                value = preference.defaultValue
                                viewModel.resetPreferenceToDefault(preference)
                            }
                    } else Modifier
                ) {
                    Text(
                        text = "${(value * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (!isDefault) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Slider(
            value = value,
            onValueChange = { raw ->
                value = (raw * 10f).roundToInt() / 10f
                viewModel.setPreference(preference, value)
            },
            valueRange = 0.1f..1f,
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

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "10% (Near)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = "100% (Default)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun <T : SealedString> BasicListPreference(
    title: String,
    options: List<T>,
    selected: T,
    enabled: Boolean = true,
    onPreferenceClicked: ((thisSealedString: T) -> Unit),
) {
    var isShowingAlert by remember { mutableStateOf(false) }
    val selectedThemeLabel = stringResource(selected.id)

    OnClickPreference(
        title = title,
        summary = selectedThemeLabel,
        enabled = enabled,
        onClick = { isShowingAlert = true },
        trailingContent = {
            if (isShowingAlert) {
                AlertDialog(
                    shape = RoundedCornerShape(CornerExtraLarge),
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onDismissRequest = { isShowingAlert = false },
                    confirmButton = {
                        TextButton(onClick = { isShowingAlert = false }) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (thisSealedString in options) {
                                val isSelected = selected == thisSealedString
                                val label = stringResource(thisSealedString.id)
                                RadioButtonHelper(
                                    label = label,
                                    selected = isSelected,
                                    onClick = {
                                        onPreferenceClicked(thisSealedString)
                                        isShowingAlert = false
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    )
}

@Composable
private fun RadioButtonHelper(
    label: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
) {
    Surface(
        onClick = { onClick?.invoke() },
        shape = RoundedCornerShape(CornerMedium),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                colors = colors,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun OnClickPreference(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val defaults = ListItemDefaults.colors(containerColor = Color.Transparent)
    val listItemColors: ListItemColors = if (enabled) {
        defaults
    } else {
        ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = defaults.disabledHeadlineColor,
            leadingIconColor = defaults.disabledLeadingIconColor,
            trailingIconColor = defaults.disabledTrailingIconColor,
            supportingColor = defaults.disabledHeadlineColor
        )
    }
    val modifier = if (enabled) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    ListItem(
        modifier = modifier,
        colors = listItemColors,
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal
            )
        },
        supportingContent = {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        },
        trailingContent = trailingContent
    )
}

@Composable
fun <T> TextDialogPreference(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    preference: ObjectPreferenceKey<T>,
    property: KProperty1<UserPreferences, T>,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val currentUserPreferences by settingsViewModel.userPreferencesFlow.collectAsState()

    val initialValue = property.get(currentUserPreferences)
    val initialValueString = if (initialValue == preference.defaultValue) {
        ""
    } else {
        preference.toStringPreference(initialValue)
    }

    var isShowingDialog by remember { mutableStateOf(false) }
    var editableValue by remember { mutableStateOf(initialValueString) }

    OnClickPreference(
        title = title,
        summary = summary ?: editableValue.ifBlank { "Default" },
        enabled = enabled,
        onClick = { isShowingDialog = true },
        trailingContent = {
            if (isShowingDialog) {
                AlertDialog(
                    shape = RoundedCornerShape(CornerExtraLarge),
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onDismissRequest = { isShowingDialog = false },
                    confirmButton = {
                        TextButton(onClick = { isShowingDialog = false }) {
                            Text("OK", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        OutlinedTextField(
                            value = editableValue,
                            placeholder = {
                                Text(text = preference.toStringPreference(preference.defaultValue))
                            },
                            shape = RoundedCornerShape(CornerMedium),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            onValueChange = {
                                editableValue = it
                                if (it.isBlank()) {
                                    settingsViewModel.resetPreferenceToDefault(preference)
                                } else {
                                    settingsViewModel.setPreference(preference, preference.fromStringPreference(it))
                                }
                            },
                        )
                    }
                )
            }
        }
    )
}
