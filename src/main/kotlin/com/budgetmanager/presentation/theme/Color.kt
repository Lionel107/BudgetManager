package com.budgetmanager.presentation.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// ===== Theme Colors Data Class =====

data class NeumorphicColors(
    val background: Color,
    val elevated: Color,
    val depressed: Color,
    val primary: Color,
    val primaryVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val lightShadow: Color,
    val darkShadow: Color,
    val income: Color,
    val expense: Color,
    val budgetSafe: Color,
    val budgetWarning: Color,
    val budgetAlert: Color,
    val transfer: Color,
    val cardBackground: Color,
    val dialogBackground: Color,
    val textFieldFocused: Color,
    val textFieldUnfocused: Color,
)

// ===== Light Theme =====

val LightNeumorphicColors = NeumorphicColors(
    // MATIÈRE UNIQUE : fond ET éléments partagent la même teinte ; le volume vient
    // du dégradé de surface (convexe/concave) + des ombres du modificateur.
    // Charte "Émeraude Tech" (clair futuriste) : base plate, cartes blanches
    // bordées, accent émeraude->teal. Le relief neumorphique est abandonné.
    background = Color(0xFFFFFFFF),        // fond blanc pur
    elevated = Color(0xFFFFFFFF),          // cartes blanches
    depressed = Color(0xFFEDF2F0),         // fonds de champ / creux légers
    primary = Color(0xFF0FB985),           // émeraude
    primaryVariant = Color(0xFF06D6C4),    // teal (2e arrêt du dégradé)
    textPrimary = Color(0xFF0C1512),
    textSecondary = Color(0xFF5B6B65),
    textTertiary = Color(0xFF94A39C),
    lightShadow = Color(0xFFFFFFFF),
    darkShadow = Color(0xFFE1E7E4),        // sert de couleur de bordure fine
    income = Color(0xFF0FB985),
    expense = Color(0xFFF43F5E),
    budgetSafe = Color(0xFF0FB985),
    budgetWarning = Color(0xFFF5A623),
    budgetAlert = Color(0xFFF43F5E),
    transfer = Color(0xFF06D6C4),
    cardBackground = Color(0xFFFFFFFF),
    dialogBackground = Color(0xFFFFFFFF),
    textFieldFocused = Color(0xFFFFFFFF),
    textFieldUnfocused = Color(0xFFEDF2F0),
)

// ===== Dark Theme =====

val DarkNeumorphicColors = NeumorphicColors(
    background = Color(0xFF2D2D2D),
    elevated = Color(0xFF353535),
    depressed = Color(0xFF252525),
    primary = Color(0xFF7C73FF),
    primaryVariant = Color(0xFF6A61E0),
    textPrimary = Color(0xFFE8E8E8),
    textSecondary = Color(0xFFB0B0B0),
    textTertiary = Color(0xFF707070),
    lightShadow = Color(0xFF3D3D3D),
    darkShadow = Color(0xFF1A1A1A),
    income = Color(0xFF2ED8A3),
    expense = Color(0xFFFF8A6C),
    budgetSafe = Color(0xFF2ED8A3),
    budgetWarning = Color(0xFFFFAB2E),
    budgetAlert = Color(0xFFFF5252),
    transfer = Color(0xFF82C4FF),
    cardBackground = Color(0xFF353535),
    dialogBackground = Color(0xFF3A3A3A),
    textFieldFocused = Color(0xFF404040),
    textFieldUnfocused = Color(0xFF252525).copy(alpha = 0.8f),
)

// ===== Blue Theme =====

val BlueNeumorphicColors = NeumorphicColors(
    background = Color(0xFF1E2D42),
    elevated = Color(0xFF253750),
    depressed = Color(0xFF162236),
    primary = Color(0xFF5BA8F5),
    primaryVariant = Color(0xFF4A95E0),
    textPrimary = Color(0xFFE4EBF5),
    textSecondary = Color(0xFF9DB4CC),
    textTertiary = Color(0xFF5D7A96),
    lightShadow = Color(0xFF2A3F5C),
    darkShadow = Color(0xFF111C2B),
    income = Color(0xFF4AE0B0),
    expense = Color(0xFFFF8A70),
    budgetSafe = Color(0xFF4AE0B0),
    budgetWarning = Color(0xFFFFB84D),
    budgetAlert = Color(0xFFFF5C5C),
    transfer = Color(0xFF82C8FF),
    cardBackground = Color(0xFF253750),
    dialogBackground = Color(0xFF2A3D55),
    textFieldFocused = Color(0xFF2E4562),
    textFieldUnfocused = Color(0xFF162236).copy(alpha = 0.8f),
)

// ===== Rose / Kawaii Theme =====

val RoseNeumorphicColors = NeumorphicColors(
    background = Color(0xFFFFE4EE),
    elevated = Color(0xFFFFECF3),
    depressed = Color(0xFFF5D0DE),
    primary = Color(0xFFFF6B9D),
    primaryVariant = Color(0xFFE85A8A),
    textPrimary = Color(0xFF5C2040),
    textSecondary = Color(0xFFA86080),
    textTertiary = Color(0xFFD4A0B8),
    lightShadow = Color(0xFFFFF5F8),
    darkShadow = Color(0xFFDEB8CA),
    income = Color(0xFF5AD8A8),
    expense = Color(0xFFFF7878),
    budgetSafe = Color(0xFF5AD8A8),
    budgetWarning = Color(0xFFFFB347),
    budgetAlert = Color(0xFFFF5C7A),
    transfer = Color(0xFF8AC4FF),
    cardBackground = Color(0xFFFFECF3),
    dialogBackground = Color(0xFFFFECF3),
    textFieldFocused = Color.White.copy(alpha = 0.65f),
    textFieldUnfocused = Color(0xFFF5D0DE).copy(alpha = 0.7f),
)

// ===== CompositionLocal =====

val LocalNeumorphicColors = staticCompositionLocalOf { LightNeumorphicColors }

// ===== Accessor object for easy use =====

object AppColors {
    val current: NeumorphicColors
        @Composable @ReadOnlyComposable
        get() = LocalNeumorphicColors.current
}

// ===== Legacy aliases (read from current theme) =====
// These allow existing code to keep compiling while we migrate

val NeumorphicBackground: Color @Composable @ReadOnlyComposable get() = AppColors.current.background
val NeumorphicElevated: Color @Composable @ReadOnlyComposable get() = AppColors.current.elevated
val NeumorphicDepressed: Color @Composable @ReadOnlyComposable get() = AppColors.current.depressed
val NeumorphicPrimary: Color @Composable @ReadOnlyComposable get() = AppColors.current.primary
val NeumorphicPrimaryVariant: Color @Composable @ReadOnlyComposable get() = AppColors.current.primaryVariant
val NeumorphicTextPrimary: Color @Composable @ReadOnlyComposable get() = AppColors.current.textPrimary
val NeumorphicTextSecondary: Color @Composable @ReadOnlyComposable get() = AppColors.current.textSecondary
val NeumorphicTextTertiary: Color @Composable @ReadOnlyComposable get() = AppColors.current.textTertiary
val NeumorphicLightShadow: Color @Composable @ReadOnlyComposable get() = AppColors.current.lightShadow
val NeumorphicDarkShadow: Color @Composable @ReadOnlyComposable get() = AppColors.current.darkShadow
val NeumorphicIncome: Color @Composable @ReadOnlyComposable get() = AppColors.current.income
val NeumorphicExpense: Color @Composable @ReadOnlyComposable get() = AppColors.current.expense
val NeumorphicBudgetSafe: Color @Composable @ReadOnlyComposable get() = AppColors.current.budgetSafe
val NeumorphicBudgetWarning: Color @Composable @ReadOnlyComposable get() = AppColors.current.budgetWarning
val NeumorphicBudgetAlert: Color @Composable @ReadOnlyComposable get() = AppColors.current.budgetAlert
val ExpenseColor: Color @Composable @ReadOnlyComposable get() = AppColors.current.expense
val IncomeColor: Color @Composable @ReadOnlyComposable get() = AppColors.current.income
val TransferColor: Color @Composable @ReadOnlyComposable get() = AppColors.current.transfer

// Category default colors (same in both themes)
val CategoryColors = listOf(
    Color(0xFFE17055), Color(0xFF00B894), Color(0xFF74B9FF),
    Color(0xFFFDCB6E), Color(0xFF6C63FF), Color(0xFFE84393),
    Color(0xFF00CEC9), Color(0xFFFF7675), Color(0xFF55A3F0),
    Color(0xFFA29BFE), Color(0xFFFF9F43), Color(0xFF2ED573),
    Color(0xFFFF6B81), Color(0xFF5F27CD), Color(0xFF01A3A4),
    Color(0xFFf368e0)
)
