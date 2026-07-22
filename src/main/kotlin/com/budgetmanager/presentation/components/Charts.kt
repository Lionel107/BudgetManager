package com.budgetmanager.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetmanager.presentation.theme.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val CHART_PALETTE = listOf(
    Color(0xFF6C63FF), Color(0xFFFF6584), Color(0xFF00BFA6), Color(0xFFFFA726),
    Color(0xFF42A5F5), Color(0xFFAB47BC), Color(0xFF26A69A), Color(0xFFEF5350),
    Color(0xFFFFCA28), Color(0xFF66BB6A), Color(0xFF7E57C2), Color(0xFFEC407A)
)

// ===================== PIE / DONUT CHART =====================

data class PieSlice(val label: String, val value: Float, val color: Color)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PieChart(
    slices: List<PieSlice>,
    modifier: Modifier = Modifier,
    donut: Boolean = true
) {
    val total = slices.fold(0f) { a, s -> a + s.value }.coerceAtLeast(0.0001f)

    // Vertical layout: chart on top (centered), legend below — avoids any overlap
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(240.dp).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val side = minOf(size.width, size.height)
                val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
                val arcSize = Size(side, side)
                var startAngle = -90f
                for (slice in slices) {
                    val sweep = (slice.value / total) * 360f
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = !donut,
                        topLeft = topLeft,
                        size = arcSize,
                        // Thinner stroke so center text fits comfortably
                        style = if (donut) Stroke(width = side * 0.14f) else androidx.compose.ui.graphics.drawscope.Fill
                    )
                    startAngle += sweep
                }
            }
            if (donut) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${total.toInt()} EUR",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeumorphicTextPrimary
                    )
                    Text(
                        "${slices.size} categorie(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeumorphicTextTertiary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Legend below, in a flow layout (wraps on multiple rows if needed)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slices.take(12).forEach { slice ->
                val pct = (slice.value / total) * 100f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(slice.color))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        slice.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeumorphicTextPrimary,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${String.format("%.0f", slice.value)} EUR · ${String.format("%.1f", pct)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeumorphicTextSecondary
                    )
                }
            }
        }
    }
}

fun assignColors(items: List<Pair<String, Float>>): List<PieSlice> =
    items.mapIndexed { i, (label, value) ->
        PieSlice(label, value, CHART_PALETTE[i % CHART_PALETTE.size])
    }

// ===================== LINE CHART =====================

data class LinePoint(val label: String, val value: Float)

@Composable
fun LineChart(
    points: List<LinePoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = NeumorphicPrimary,
    fillColor: Color = NeumorphicPrimary.copy(alpha = 0.15f),
    height: Int = 200
) {
    if (points.isEmpty()) {
        Box(modifier = modifier.height(height.dp), contentAlignment = Alignment.Center) {
            Text("Aucune donnee", color = NeumorphicTextTertiary)
        }
        return
    }

    val minValue = points.minOf { it.value }
    val maxValue = points.maxOf { it.value }
    val range = (maxValue - minValue).coerceAtLeast(1f)
    val gridColor = NeumorphicTextTertiary.copy(alpha = 0.3f)

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height.dp)) {
            val w = size.width
            val h = size.height
            val padding = 8f
            val effectiveH = h - padding * 2

            // Horizontal grid lines (4)
            for (i in 0..3) {
                val y = padding + (effectiveH * i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
            }

            val stepX = if (points.size > 1) w / (points.size - 1) else w
            // Build path
            val linePath = Path()
            val fillPath = Path()
            points.forEachIndexed { idx, pt ->
                val x = stepX * idx
                val normalized = (pt.value - minValue) / range
                val y = padding + effectiveH * (1f - normalized)
                if (idx == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            // Close fill path
            fillPath.lineTo(w, h)
            fillPath.close()

            // Draw fill area
            drawPath(fillPath, color = fillColor)
            // Draw line
            drawPath(linePath, color = lineColor, style = Stroke(width = 3f))

            // Draw points
            points.forEachIndexed { idx, pt ->
                val x = stepX * idx
                val normalized = (pt.value - minValue) / range
                val y = padding + effectiveH * (1f - normalized)
                drawCircle(color = lineColor, radius = 5f, center = Offset(x, y))
                drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 2.5f, center = Offset(x, y))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            points.forEach { pt ->
                Text(
                    pt.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeumorphicTextTertiary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

// ===================== STACKED BAR CHART =====================

data class StackedBarColumn(
    val label: String,
    val segments: List<Pair<Float, Color>> // (value, color)
)

@Composable
fun StackedBarChart(
    columns: List<StackedBarColumn>,
    legend: List<Pair<String, Color>>,
    modifier: Modifier = Modifier,
    height: Int = 220
) {
    val maxTotal = columns.maxOfOrNull { col -> col.segments.fold(0f) { a, (v, _) -> a + v } }
        ?: 1f

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            columns.forEach { col ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    val total = col.segments.fold(0f) { a, (v, _) -> a + v }
                    val heightRatio = (total / maxTotal).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .fillMaxHeight(heightRatio.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    ) {
                        // Top-down rendering: take last (top) segment first
                        col.segments.forEach { (value, color) ->
                            val pct = if (total > 0) value / total else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(pct)
                                    .background(color)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        col.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeumorphicTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            legend.forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = NeumorphicTextSecondary)
                }
            }
        }
    }
}

// ===================== HEATMAP =====================

@Composable
fun DailyHeatmap(
    year: Int,
    month: Int,
    valuePerDay: Map<Int, Float>, // dayOfMonth -> total spent
    modifier: Modifier = Modifier
) {
    val yearMonth = java.time.YearMonth.of(year, month)
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value // 1 = Monday
    val maxValue = valuePerDay.values.maxOrNull() ?: 0f

    val weekDayLabels = listOf("Lu", "Ma", "Me", "Je", "Ve", "Sa", "Di")

    Column(modifier = modifier.fillMaxWidth()) {
        // Header: weekday labels
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDayLabels.forEach { d ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(d, style = MaterialTheme.typography.labelSmall, color = NeumorphicTextTertiary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        val totalCells = firstDayOfWeek - 1 + daysInMonth
        val weeks = (totalCells + 6) / 7

        for (week in 0 until weeks) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                for (dayOfWeek in 0..6) {
                    val cellIndex = week * 7 + dayOfWeek
                    val dayNumber = cellIndex - (firstDayOfWeek - 1) + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNumber in 1..daysInMonth) {
                            val value = valuePerDay[dayNumber] ?: 0f
                            val intensity = if (maxValue > 0f) value / maxValue else 0f
                            val color = heatColor(intensity)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    dayNumber.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (intensity > 0.5f) Color.White else NeumorphicTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        // Legend gradient
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Moins", style = MaterialTheme.typography.labelSmall, color = NeumorphicTextTertiary)
            Spacer(Modifier.width(8.dp))
            (0..4).forEach { i ->
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 14.dp)
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(heatColor(i / 4f))
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("Plus", style = MaterialTheme.typography.labelSmall, color = NeumorphicTextTertiary)
            Spacer(Modifier.weight(1f))
            Text("Total: ${valuePerDay.values.sum().toInt()} EUR", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun heatColor(intensity: Float): Color {
    val t = intensity.coerceIn(0f, 1f)
    val baseR = 0xEB; val baseG = 0xED; val baseB = 0xF3
    val peakR = 0xE5; val peakG = 0x39; val peakB = 0x35
    val r = (baseR + (peakR - baseR) * t).toInt()
    val g = (baseG + (peakG - baseG) * t).toInt()
    val b = (baseB + (peakB - baseB) * t).toInt()
    return Color(r, g, b)
}
