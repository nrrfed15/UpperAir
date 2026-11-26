package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.SoundingProfile
import com.example.ui.theme.InstabilityCoral
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.ThermalAmber
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun OutlookTrendChart(
    forecasts: List<SoundingProfile>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "7-Day Instability & Shear Trend",
    emptyMessage: String = "Sync station to view model trend"
) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrendDot("CAPE", InstabilityCoral)
                TrendDot("0-6km Shear", SkyCyan)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            if (forecasts.size >= 2) {
                val maxHour = max(24, forecasts.maxOf { it.forecastHourOffset }).toFloat()
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("outlook_trend_canvas")
                        .pointerInput(forecasts) {
                            detectTapGestures { offset ->
                                val leftPad = 36.dp.toPx()
                                val rightPad = 12.dp.toPx()
                                val plotW = (size.width - leftPad - rightPad).coerceAtLeast(10f)
                                val norm = ((offset.x - leftPad) / plotW).coerceIn(0f, 1f)
                                val targetHr = norm * maxHour
                                val nearestIdx = forecasts.indices.minByOrNull { idx ->
                                    kotlin.math.abs(forecasts[idx].forecastHourOffset - targetHr)
                                } ?: 0
                                onSelectIndex(nearestIdx)
                            }
                        }
                ) {
                    val leftPad = 36.dp.toPx()
                    val rightPad = 12.dp.toPx()
                    val topPad = 12.dp.toPx()
                    val bottomPad = 24.dp.toPx()
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(10f)
                    val plotH = (size.height - topPad - bottomPad).coerceAtLeast(10f)

                    val maxCape = max(1000, forecasts.maxOf { it.indices.sbcapeJkg }).toFloat()
                    val maxShear = max(60, forecasts.maxOf { it.indices.shear06Kt }).toFloat()
                    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)

                    val maxDay = ((maxHour.toInt() + 23) / 24).coerceIn(1, 7)
                    for (day in 0..maxDay) {
                        val hourTarget = day * 24f
                        val frac = (hourTarget / maxHour).coerceIn(0f, 1f)
                        val x = leftPad + frac * plotW
                        drawLine(
                            color = gridColor,
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + plotH),
                            strokeWidth = 1f
                        )
                        if (day < maxDay) {
                            val lbl = if (day == 0) "Now" else "D+$day"
                            val measured = textMeasurer.measure(lbl, labelStyle)
                            drawText(measured, topLeft = Offset(x + 2.dp.toPx(), topPad + plotH + 4.dp.toPx()))
                        }
                    }

                    // Horizontal reference lines
                    for (frac in listOf(0f, 0.5f, 1f)) {
                        val y = topPad + plotH * (1f - frac)
                        drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + plotW, y), strokeWidth = 0.8f)
                    }
                    val topLbl = textMeasurer.measure("${maxCape.toInt()}", labelStyle)
                    drawText(topLbl, topLeft = Offset(2.dp.toPx(), topPad - 4.dp.toPx()))
                    val zeroLbl = textMeasurer.measure("0", labelStyle)
                    drawText(zeroLbl, topLeft = Offset(12.dp.toPx(), topPad + plotH - 8.dp.toPx()))

                    // CAPE filled area + line
                    val capeLine = Path()
                    val capeFill = Path()
                    val shearLine = Path()

                    forecasts.forEachIndexed { idx, p ->
                        val x = leftPad + (p.forecastHourOffset / maxHour).coerceIn(0f, 1f) * plotW
                        val yCape = topPad + plotH * (1f - (p.indices.sbcapeJkg / maxCape).coerceIn(0f, 1f))
                        val yShear = topPad + plotH * (1f - (p.indices.shear06Kt / maxShear).coerceIn(0f, 1f))
                        if (idx == 0) {
                            capeLine.moveTo(x, yCape)
                            capeFill.moveTo(x, topPad + plotH)
                            capeFill.lineTo(x, yCape)
                            shearLine.moveTo(x, yShear)
                        } else {
                            capeLine.lineTo(x, yCape)
                            capeFill.lineTo(x, yCape)
                            shearLine.lineTo(x, yShear)
                        }
                        if (idx == forecasts.lastIndex) {
                            capeFill.lineTo(x, topPad + plotH)
                            capeFill.close()
                        }
                    }

                    drawPath(
                        path = capeFill,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                InstabilityCoral.copy(alpha = 0.42f),
                                InstabilityCoral.copy(alpha = 0.04f)
                            ),
                            startY = topPad,
                            endY = topPad + plotH
                        )
                    )
                    drawPath(
                        path = capeLine,
                        color = InstabilityCoral,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = shearLine,
                        color = SkyCyan,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Selected cursor
                    val safeIdx = selectedIndex.coerceIn(0, forecasts.lastIndex)
                    val selHour = forecasts[safeIdx].forecastHourOffset
                    val selX = leftPad + (selHour / maxHour).coerceIn(0f, 1f) * plotW
                    drawLine(
                        color = ThermalAmber,
                        start = Offset(selX, topPad),
                        end = Offset(selX, topPad + plotH),
                        strokeWidth = 1.6.dp.toPx()
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
