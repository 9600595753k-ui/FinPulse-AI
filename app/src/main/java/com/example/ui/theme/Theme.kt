package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldBright,
    onPrimary = Color(0xFF00382B),
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = EmeraldMist,
    secondary = TealAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF113834),
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = GoldAccent,
    onTertiary = Color(0xFF281D00),
    background = ObsidianBg,
    onBackground = Color(0xFFF1F5F9),
    surface = CharcoalSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = CharcoalElevated,
    surfaceContainerHigh = Color(0xFF212E36),
    outline = CharcoalOutline,
    outlineVariant = Color(0xFF1F2B33),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = CrimsonDarkContainer,
    onErrorContainer = CrimsonSoft
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = EmeraldMist,
    onPrimaryContainer = Color(0xFF022C22),
    secondary = TealAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = GoldAccent,
    onTertiary = Color.White,
    background = AlabasterBg,
    onBackground = SlateInk,
    surface = PureWhiteSurface,
    onSurface = SlateInk,
    surfaceVariant = SoftSageSurface,
    onSurfaceVariant = SlateMuted,
    surfaceContainerLow = PureWhiteSurface,
    surfaceContainer = SoftSageSurface,
    surfaceContainerHigh = Color(0xFFE2E8E5),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonSoft,
    onErrorContainer = Color(0xFF7F1D1D)
)

val FinPulseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FinPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FinPulseTypography,
        shapes = FinPulseShapes,
        content = content
    )
}
