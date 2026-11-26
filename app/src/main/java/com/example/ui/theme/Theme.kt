package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ObservatoryDarkColorScheme = darkColorScheme(
    primary = SkyCyan,
    onPrimary = MidnightVoid,
    primaryContainer = Color(0xFF0C3554),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = ThermalAmber,
    onSecondary = MidnightVoid,
    secondaryContainer = Color(0xFF452408),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = DewpointEmerald,
    onTertiary = MidnightVoid,
    tertiaryContainer = Color(0xFF0B3B2E),
    onTertiaryContainer = Color(0xFFA7F3D0),
    error = InstabilityCoral,
    onError = MidnightVoid,
    background = MidnightVoid,
    onBackground = CrispIceText,
    surface = StratosphereSlate,
    onSurface = CrispIceText,
    surfaceVariant = TroposphereCard,
    onSurfaceVariant = MutedRadarText,
    surfaceContainerLow = StratosphereSlate,
    surfaceContainer = TroposphereCard,
    surfaceContainerHigh = MesosphereSurface,
    outline = StratosphereOutline,
    outlineVariant = Color(0xFF1E304A)
)

private val ObservatoryLightColorScheme = lightColorScheme(
    primary = DeepAzure,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F0FD),
    onPrimaryContainer = Color(0xFF082F49),
    secondary = WarmAmberDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    error = Color(0xFFE11D48),
    onError = Color.White,
    background = DaylightCanvas,
    onBackground = DaylightInk,
    surface = DaylightSurface,
    onSurface = DaylightInk,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightMutedInk,
    surfaceContainerLow = DaylightCanvas,
    surfaceContainer = DaylightSurface,
    surfaceContainerHigh = DaylightSurfaceVariant,
    outline = DaylightOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun UpperAirTheme(
    darkTheme: Boolean = true, // Default to crisp Stratospheric Dark Cockpit; user/system toggleable
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ObservatoryDarkColorScheme else ObservatoryLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = UpperAirTypography,
        content = content
    )
}
