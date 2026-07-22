package com.budgetmanager.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== Global theme state (accessible from settings) =====

val ThemeModeState: MutableState<String> = mutableStateOf("light")

/** Global font scale (0.85, 1.0, 1.15, 1.30). */
val FontScaleState: MutableState<Float> = mutableStateOf(1.0f)

/** Global density: "compact", "normal", "large". */
val DensityState: MutableState<String> = mutableStateOf("normal")

// ===== Material Color Schemes =====

@Composable
private fun neumorphicLightColorScheme() = lightColorScheme(
    primary = AppColors.current.primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.current.primary.copy(alpha = 0.12f),
    onPrimaryContainer = AppColors.current.primary,
    secondary = AppColors.current.income,
    onSecondary = Color.White,
    tertiary = AppColors.current.transfer,
    onTertiary = Color.White,
    background = AppColors.current.background,
    onBackground = AppColors.current.textPrimary,
    surface = AppColors.current.elevated,
    onSurface = AppColors.current.textPrimary,
    surfaceVariant = AppColors.current.depressed,
    onSurfaceVariant = AppColors.current.textSecondary,
    outline = AppColors.current.textTertiary,
    error = AppColors.current.expense,
    onError = Color.White,
)

@Composable
private fun neumorphicDarkColorScheme() = darkColorScheme(
    primary = AppColors.current.primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.current.primary.copy(alpha = 0.15f),
    onPrimaryContainer = AppColors.current.primary,
    secondary = AppColors.current.income,
    onSecondary = Color.White,
    tertiary = AppColors.current.transfer,
    onTertiary = Color.White,
    background = AppColors.current.background,
    onBackground = AppColors.current.textPrimary,
    surface = AppColors.current.elevated,
    onSurface = AppColors.current.textPrimary,
    surfaceVariant = AppColors.current.depressed,
    onSurfaceVariant = AppColors.current.textSecondary,
    outline = AppColors.current.textTertiary,
    error = AppColors.current.expense,
    onError = Color.White,
)

// Desktop-optimized typography — colors set dynamically + font-scale aware
@Composable
private fun appTypography(): Typography {
    val textPrimary = AppColors.current.textPrimary
    val textSecondary = AppColors.current.textSecondary
    val textTertiary = AppColors.current.textTertiary
    val s = FontScaleState.value
    return Typography(
        displayLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = (36 * s).sp,
            lineHeight = (44 * s).sp,
            letterSpacing = (-0.5).sp,
            color = textPrimary
        ),
        displayMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = (30 * s).sp,
            lineHeight = (38 * s).sp,
            color = textPrimary
        ),
        headlineLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = (26 * s).sp,
            lineHeight = (34 * s).sp,
            color = textPrimary
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = (22 * s).sp,
            lineHeight = (28 * s).sp,
            color = textPrimary
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = (20 * s).sp,
            lineHeight = (26 * s).sp,
            color = textPrimary
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = (17 * s).sp,
            lineHeight = (24 * s).sp,
            color = textPrimary
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (16 * s).sp,
            lineHeight = (24 * s).sp,
            color = textPrimary
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (15 * s).sp,
            lineHeight = (22 * s).sp,
            color = textSecondary
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (13 * s).sp,
            lineHeight = (18 * s).sp,
            color = textTertiary
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = (15 * s).sp,
            lineHeight = (22 * s).sp,
            color = textPrimary
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = (13 * s).sp,
            lineHeight = (18 * s).sp,
            color = textSecondary
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * s).sp,
            lineHeight = (16 * s).sp,
            color = textTertiary
        )
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun BudgetManagerTheme(
    themeMode: String = ThemeModeState.value,
    content: @Composable () -> Unit
) {
    val neumorphicColors = when (themeMode) {
        "dark" -> DarkNeumorphicColors
        "blue" -> BlueNeumorphicColors
        "rose" -> RoseNeumorphicColors
        else -> LightNeumorphicColors
    }
    val isDark = themeMode == "dark" || themeMode == "blue"

    CompositionLocalProvider(LocalNeumorphicColors provides neumorphicColors) {
        val colorScheme = if (isDark) neumorphicDarkColorScheme() else neumorphicLightColorScheme()
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(),
            shapes = AppShapes,
            content = content
        )
    }
}
