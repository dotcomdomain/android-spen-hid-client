package me.arianb.usb_hid_client.ui.theme

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.settings.AppTheme
import me.arianb.usb_hid_client.settings.SettingsViewModel

private val DarkColorScheme = darkColorScheme(
    primary = M3DarkPrimary,
    onPrimary = M3DarkOnPrimary,
    primaryContainer = M3DarkPrimaryContainer,
    onPrimaryContainer = M3DarkOnPrimaryContainer,
    inversePrimary = M3DarkInversePrimary,
    secondary = M3DarkSecondary,
    onSecondary = M3DarkOnSecondary,
    secondaryContainer = M3DarkSecondaryContainer,
    onSecondaryContainer = M3DarkOnSecondaryContainer,
    tertiary = M3DarkTertiary,
    onTertiary = M3DarkOnTertiary,
    tertiaryContainer = M3DarkTertiaryContainer,
    onTertiaryContainer = M3DarkOnTertiaryContainer,
    background = M3DarkBackground,
    onBackground = M3DarkOnBackground,
    surface = M3DarkSurface,
    onSurface = M3DarkOnSurface,
    surfaceVariant = M3DarkSurfaceVariant,
    onSurfaceVariant = M3DarkOnSurfaceVariant,
    surfaceContainerLowest = M3DarkSurfaceContainerLowest,
    surfaceContainerLow = M3DarkSurfaceContainerLow,
    surfaceContainer = M3DarkSurfaceContainer,
    surfaceContainerHigh = M3DarkSurfaceContainerHigh,
    surfaceContainerHighest = M3DarkSurfaceContainerHighest,
    inverseSurface = M3DarkInverseSurface,
    inverseOnSurface = M3DarkInverseOnSurface,
    outline = M3DarkOutline,
    outlineVariant = M3DarkOutlineVariant,
    scrim = M3DarkScrim,
    error = M3DarkError,
    onError = M3DarkOnError,
    errorContainer = M3DarkErrorContainer,
    onErrorContainer = M3DarkOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = M3LightPrimary,
    onPrimary = M3LightOnPrimary,
    primaryContainer = M3LightPrimaryContainer,
    onPrimaryContainer = M3LightOnPrimaryContainer,
    inversePrimary = M3LightInversePrimary,
    secondary = M3LightSecondary,
    onSecondary = M3LightOnSecondary,
    secondaryContainer = M3LightSecondaryContainer,
    onSecondaryContainer = M3LightOnSecondaryContainer,
    tertiary = M3LightTertiary,
    onTertiary = M3LightOnTertiary,
    tertiaryContainer = M3LightTertiaryContainer,
    onTertiaryContainer = M3LightOnTertiaryContainer,
    background = M3LightBackground,
    onBackground = M3LightOnBackground,
    surface = M3LightSurface,
    onSurface = M3LightOnSurface,
    surfaceVariant = M3LightSurfaceVariant,
    onSurfaceVariant = M3LightOnSurfaceVariant,
    surfaceContainerLowest = M3LightSurfaceContainerLowest,
    surfaceContainerLow = M3LightSurfaceContainerLow,
    surfaceContainer = M3LightSurfaceContainer,
    surfaceContainerHigh = M3LightSurfaceContainerHigh,
    surfaceContainerHighest = M3LightSurfaceContainerHighest,
    inverseSurface = M3LightInverseSurface,
    inverseOnSurface = M3LightInverseOnSurface,
    outline = M3LightOutline,
    outlineVariant = M3LightOutlineVariant,
    scrim = M3LightScrim,
    error = M3LightError,
    onError = M3LightOnError,
    errorContainer = M3LightErrorContainer,
    onErrorContainer = M3LightOnErrorContainer
)

val M3ExpressiveShapes = Shapes(
    extraSmall = ShapeExtraSmall,
    small = ShapeSmall,
    medium = ShapeMedium,
    large = ShapeLargeIncreased,
    extraLarge = ShapeExtraLarge
)

fun isDynamicColorAvailable(): Boolean {
    // Dynamic color is available on Android 12+ (API 31+)
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

@Composable
fun USBHIDClientTheme(
    settingsViewModel: SettingsViewModel = viewModel(),
    content: @Composable () -> Unit
) {
    val preferencesState by settingsViewModel.userPreferencesFlow.collectAsState()

    val darkTheme = when (preferencesState.appTheme) {
        AppTheme.DarkMode -> true
        AppTheme.LightMode -> false
        else -> isSystemInDarkTheme()
    }

    val dynamicColor = preferencesState.isDynamicColorEnabled

    val colorScheme = when {
        dynamicColor && isDynamicColorAvailable() -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val hideStatusBar = preferencesState.hideStatusBar
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val shouldHideStatusBar = hideStatusBar || (preferencesState.isTouchpadFullscreenInLandscape && isLandscape)

        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            // Set status bar & nav bar background to match surface
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()

            // Light status bar means dark icons (for light theme)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme

            if (shouldHideStatusBar) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = M3ExpressiveShapes,
        typography = Typography,
        content = content
    )
}
