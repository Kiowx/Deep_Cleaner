package com.kiowx.deepcleaner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiowx.deepcleaner.core.ThemeMode

// Blue-white brand palette. Existing names are kept to avoid scattering
// presentation-only renames through the feature code.
val DeepTeal = Color(0xFF2563EB)
val BrightMint = Color(0xFFBFD7FF)
val DeepNavy = Color(0xFF0B2A5B)
val AquaBlue = Color(0xFF0284C7)
val WarmOrange = Color(0xFF60A5FA)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF071B3D),
    secondary = Color(0xFF53698F),
    secondaryContainer = Color(0xFFDDE6F8),
    tertiary = Color(0xFF0061A4),
    tertiaryContainer = Color(0xFFD1E4FF),
    background = Color(0xFFF8FAFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3EAF5),
    surfaceContainer = Color(0xFFEFF3FA),
    surfaceContainerHigh = Color(0xFFE7EDF7),
    onSurface = Color(0xFF172033),
    onSurfaceVariant = Color(0xFF46546A),
    outline = Color(0xFF77849B),
    outlineVariant = Color(0xFFC6CEDC),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF002D6B),
    primaryContainer = Color(0xFF084899),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFBBC6DB),
    secondaryContainer = Color(0xFF354153),
    tertiary = Color(0xFF9ECAFF),
    tertiaryContainer = Color(0xFF004A78),
    background = Color(0xFF0B1220),
    surface = Color(0xFF101827),
    surfaceVariant = Color(0xFF40495B),
    surfaceContainer = Color(0xFF172033),
    surfaceContainerHigh = Color(0xFF202A3A),
    onSurface = Color(0xFFE5EAF3),
    onSurfaceVariant = Color(0xFFC1C9D8),
    outline = Color(0xFF8B95A8),
    outlineVariant = Color(0xFF40495B),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun DeepCleanerTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
        ),
        content = content,
    )
}
