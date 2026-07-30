package com.budgetmanager.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import androidx.compose.ui.geometry.RoundRect

/**
 * Neumorphic raised shadow effect - element appears elevated above the surface.
 * Light shadow top-left, dark shadow bottom-right.
 * Uses Skia MaskFilter for blur (compatible with Compose Desktop).
 */
fun Modifier.neumorphicShadow(
    elevation: Dp = 8.dp,
    borderRadius: Dp = 12.dp,
    lightShadowColor: Color,
    darkShadowColor: Color,
    backgroundColor: Color
): Modifier = this.then(
    Modifier.drawBehind {
        val elevationPx = elevation.toPx()
        val radiusPx = borderRadius.toPx()
        val blurRadius = elevationPx * 0.8f
        val offsetAmount = elevationPx * 0.5f

        // Dark shadow (bottom-right) using Skia MaskFilter
        val darkPaint = Paint().apply {
            color = darkShadowColor.copy(alpha = 0.55f)
            style = PaintingStyle.Fill
            asFrameworkPaint().apply {
                isAntiAlias = true
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blurRadius / 2f)
            }
        }

        // Light shadow (top-left) using Skia MaskFilter
        val lightPaint = Paint().apply {
            color = lightShadowColor.copy(alpha = 0.9f)
            style = PaintingStyle.Fill
            asFrameworkPaint().apply {
                isAntiAlias = true
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blurRadius / 2f)
            }
        }

        // Fond PLEIN (couleur unie) — pas de dégradé gris ; le relief vient des ombres.
        val bgPaint = Paint().apply {
            color = backgroundColor
            style = PaintingStyle.Fill
            asFrameworkPaint().isAntiAlias = true
        }

        // Dark shadow path (offset bottom-right)
        val darkPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = offsetAmount,
                    top = offsetAmount,
                    right = size.width + offsetAmount,
                    bottom = size.height + offsetAmount,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            )
        }

        // Light shadow path (offset top-left)
        val lightPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = -offsetAmount,
                    top = -offsetAmount,
                    right = size.width - offsetAmount,
                    bottom = size.height - offsetAmount,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            )
        }

        // Background path
        val bgPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            )
        }

        drawIntoCanvas { canvas ->
            canvas.drawPath(darkPath, darkPaint)
            canvas.drawPath(lightPath, lightPaint)
            canvas.drawPath(bgPath, bgPaint)
        }
    }
)

/**
 * Neumorphic pressed/depressed effect - element appears pressed into the surface.
 * Inverted shadows: dark inset top-left, light inset bottom-right.
 */
fun Modifier.neumorphicPressed(
    depth: Dp = 4.dp,
    borderRadius: Dp = 12.dp,
    darkShadowColor: Color,
    lightShadowColor: Color,
    backgroundColor: Color
): Modifier = this.then(
    Modifier.drawBehind {
        val depthPx = depth.toPx()
        val radiusPx = borderRadius.toPx()
        val blurRadius = depthPx * 0.6f
        val offsetAmount = depthPx * 0.4f

        // Inner dark shadow (top-left direction)
        val darkPaint = Paint().apply {
            color = darkShadowColor.copy(alpha = 0.25f)
            style = PaintingStyle.Fill
            asFrameworkPaint().apply {
                isAntiAlias = true
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blurRadius)
            }
        }

        // Inner light shadow (bottom-right direction)
        val lightPaint = Paint().apply {
            color = lightShadowColor.copy(alpha = 0.6f)
            style = PaintingStyle.Fill
            asFrameworkPaint().apply {
                isAntiAlias = true
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blurRadius)
            }
        }

        // Fond PLEIN (couleur unie) — pas de dégradé gris ; le creux vient des ombres.
        val bgPaint = Paint().apply {
            color = backgroundColor
            style = PaintingStyle.Fill
            asFrameworkPaint().isAntiAlias = true
        }

        val bgPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            )
        }

        // Inset dark path (offset inward from top-left)
        val darkPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = -offsetAmount,
                    top = -offsetAmount,
                    right = size.width - offsetAmount,
                    bottom = size.height - offsetAmount,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            )
        }

        // Inset light path (offset inward from bottom-right)
        val lightPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = offsetAmount,
                    top = offsetAmount,
                    right = size.width + offsetAmount,
                    bottom = size.height + offsetAmount,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            )
        }

        drawIntoCanvas { canvas ->
            canvas.drawPath(bgPath, bgPaint)
            canvas.drawPath(darkPath, darkPaint)
            canvas.drawPath(lightPath, lightPaint)
        }
    }
)

// ===== Composable convenience wrappers that auto-resolve theme colors =====

@Composable
fun Modifier.neumorphicShadow(
    elevation: Dp = 8.dp,
    borderRadius: Dp = 12.dp,
    backgroundColor: Color = AppColors.current.elevated
): Modifier {
    val colors = AppColors.current
    return this.neumorphicShadow(
        elevation = elevation,
        borderRadius = borderRadius,
        lightShadowColor = colors.lightShadow,
        darkShadowColor = colors.darkShadow,
        backgroundColor = backgroundColor
    )
}

@Composable
fun Modifier.neumorphicPressed(
    depth: Dp = 4.dp,
    borderRadius: Dp = 12.dp,
    backgroundColor: Color = AppColors.current.depressed
): Modifier {
    val colors = AppColors.current
    return this.neumorphicPressed(
        depth = depth,
        borderRadius = borderRadius,
        darkShadowColor = colors.darkShadow,
        lightShadowColor = colors.lightShadow,
        backgroundColor = backgroundColor
    )
}
