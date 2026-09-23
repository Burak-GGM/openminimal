package org.openminimal.launcher.ui

import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.openminimal.launcher.model.*
import java.util.Locale

@Composable
fun OpenMinimalTheme(config: LauncherConfig, content: @Composable () -> Unit) {
    val dark = when (config.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = launcherColorScheme(config, dark)
    val density = LocalDensity.current
    val base = LocalContext.current
    // A configuration context is no longer the Activity. Preserve its result registry for
    // document pickers when the user selects an explicit application language.
    val activityResults = LocalActivityResultRegistryOwner.current
    val current = LocalConfiguration.current
    val localized = remember(base, config.language, current) {
        if (config.language == "system") base else base.createConfigurationContext(
            Configuration(current).apply { setLocale(Locale.forLanguageTag(config.language)) }
        )
    }
    CompositionLocalProvider(
        *(activityResults?.let { arrayOf(LocalActivityResultRegistryOwner provides it) } ?: emptyArray()),
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        // Popup/DropdownMenu create another Android view with the activity's base context.
        // Carry the selected resources explicitly into those child compositions.
        LocalResources provides localized.resources,
        LocalDensity provides Density(density.density, density.fontScale * config.fontScale),
    ) {
        MaterialTheme(
            colorScheme = colors,
            shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
            content = content,
        )
    }
}

/** Palette and background are independent of the selected layout preset. */
fun launcherColorScheme(config: LauncherConfig, dark: Boolean): ColorScheme {
    val palette = config.palette
    val primary = when (palette) {
        ColorPalette.SAGE -> if (dark) Color(0xFFB7CBB3) else Color(0xFF526A55)
        ColorPalette.MONOCHROME -> if (dark) Color(0xFFE3E3E3) else Color(0xFF494949)
        ColorPalette.OCEAN -> if (dark) Color(0xFFB0CEEB) else Color(0xFF365D79)
        ColorPalette.SAND -> if (dark) Color(0xFFEBC58D) else Color(0xFF78572D)
        ColorPalette.PLUM -> if (dark) Color(0xFFD7B9DD) else Color(0xFF75507E)
    }
    val black = dark && config.darkBackground == DarkBackground.PITCH_BLACK
    val background = if (black) Color.Black else when (palette) {
        ColorPalette.SAGE -> if (dark) Color(0xFF181B18) else Color(0xFFF5F3EE)
        ColorPalette.MONOCHROME -> if (dark) Color(0xFF191919) else Color(0xFFF4F4F4)
        ColorPalette.OCEAN -> if (dark) Color(0xFF161C22) else Color(0xFFF0F4F7)
        ColorPalette.SAND -> if (dark) Color(0xFF201B15) else Color(0xFFF8F2E8)
        ColorPalette.PLUM -> if (dark) Color(0xFF201921) else Color(0xFFF6F0F6)
    }
    val foreground = if (dark) Color(0xFFF0F0F0) else Color(0xFF282828)
    val muted = if (dark) Color(0xFFB9B9B9) else Color(0xFF626262)
    val container = if (black) Color(0xFF111111) else androidx.compose.ui.graphics.lerp(background, primary, if (dark) 0.08f else 0.07f)
    val variant = if (black) Color(0xFF222222) else androidx.compose.ui.graphics.lerp(background, primary, 0.14f)
    val accentContainer = androidx.compose.ui.graphics.lerp(background, primary, if (dark) 0.20f else 0.16f)
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    return colors.copy(
        primary = primary, onPrimary = if (dark) Color.Black else Color.White,
        primaryContainer = accentContainer, onPrimaryContainer = foreground,
        secondary = primary, onSecondary = if (dark) Color.Black else Color.White,
        secondaryContainer = accentContainer, onSecondaryContainer = foreground,
        tertiary = primary, onTertiary = if (dark) Color.Black else Color.White,
        background = background, surface = background, onBackground = foreground,
        onSurface = foreground, onSurfaceVariant = muted,
        surfaceContainer = container, surfaceContainerLow = container,
        surfaceContainerHigh = variant, surfaceContainerHighest = variant,
        surfaceContainerLowest = background, surfaceVariant = variant,
        surfaceTint = primary, outline = muted,
        outlineVariant = if (dark) Color(0xFF414141) else Color(0xFFD0D0D0),
    )
}
