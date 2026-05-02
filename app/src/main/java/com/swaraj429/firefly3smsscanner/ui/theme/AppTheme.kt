package com.swaraj429.firefly3smsscanner.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryVariant,
    onPrimaryContainer = Color(0xFFE0DEFF),
    secondary = CreditGreen,
    onSecondary = Color(0xFF003731),
    secondaryContainer = CreditGreenContainer,
    onSecondaryContainer = CreditGreen,
    tertiary = WarningAmber,
    onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF2D2200),
    onTertiaryContainer = WarningAmber,
    error = ErrorCrimson,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3D1616),
    onErrorContainer = DebitRed,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkSurfaceBorder,
    outlineVariant = DarkSurfaceBorder,
    inverseSurface = LightSurface,
    inverseOnSurface = LightOnSurface,
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Color(0xFFE8E6FF),
    onPrimaryContainer = PrimaryVariant,
    secondary = CreditGreenDark,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = CreditGreenContainerLight,
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = WarningAmber,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF3E0),
    onTertiaryContainer = Color(0xFF3D2E00),
    error = ErrorCrimson,
    onError = Color(0xFFFFFFFF),
    errorContainer = DebitRedContainerLight,
    onErrorContainer = Color(0xFF410002),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightSurfaceBorder,
    outlineVariant = LightSurfaceBorder,
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkOnSurface,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun FireflyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Set status bar color
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
