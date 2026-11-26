package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.SoundingLevel
import com.example.domain.model.SoundingProfile
import com.example.domain.physics.AtmosphericCalculator
import com.example.ui.theme.DewpointEmerald
import com.example.ui.theme.InstabilityCoral
import com.example.ui.theme.ParcelGold
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.ThermalAmber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SkewTChart(
    profile: SoundingProfile,
    useFahrenheit: Boolean,
    useMph: Boolean,
    modifier: Modifier = Modifier
) {
    val levels = remember(profile) { profile.levels.sortedByDescending { it.pressureHpa } }
    val parcelAnalysis = remember(levels) { AtmosphericCalculator.computeParcelAnalysis(levels) }
    var inspectedLevel by remember(profile) { mutableStateOf<SoundingLevel?>(null) }

    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val subtleLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bgChartColor = MaterialTheme.colorScheme.surface
    val windColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)

    val pMax = 1050.0
    val pMin = 100.0
    val tMin = -42.0
    val tMax = 45.0
    val skewSlope = 1.05f // Classic ~45 degree isotherm skew

    fun pressureToY(p: Double, topPad: Float, plotH: Float): Float {
        val ratio = (ln(pMax) - ln(p.coerceIn(pMin, pMax))) / (ln(pMax) - ln(pMin))
        return topPad + plotH * (1f - ratio.toFloat())
    }

    fun yToPressure(y: Float, topPad: Float, plotH: Float): Double {
        val ratio = 1.0 - ((y - topPad) / plotH).coerceIn(0f, 1f)
        val lnP = ln(pMax) - ratio * (ln(pMax) - ln(pMin))
        return kotlin.math.exp(lnP)
    }

    fun tempToX(tempC: Double, y: Float, leftPad: Float, plotW: Float, topPad: Float, plotH: Float): Float {
        val baseFraction = ((tempC - tMin) / (tMax - tMin)).toFloat()
        val yNormFromBottom = 1f - ((y - topPad) / plotH)
        return leftPad + (baseFraction + skewSlope * yNormFromBottom * 0.55f) * plotW
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgChartColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        // Legend + interactive readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendChip(color = ThermalAmber, label = "Temp")
                LegendChip(color = DewpointEmerald, label = "Dewpt")
                LegendChip(color = ParcelGold, label = "Parcel")
            }
            Text(
                text = "Drag chart to inspect",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(370.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("skew_t_canvas")
                    .pointerInput(levels) {
                        detectTapGestures { offset ->
                            val topPad = 16.dp.toPx()
                            val bottomPad = 24.dp.toPx()
                            val plotH = (size.height - topPad - bottomPad).coerceAtLeast(10f)
                            val targetP = yToPressure(offset.y, topPad, plotH)
                            inspectedLevel = levels.minByOrNull { abs(it.pressureHpa - targetP) }
                        }
                    }
                    .pointerInput(levels) {
                        detectDragGestures(
                            onDragEnd = {},
                            onDrag = { change, _ ->
                                change.consume()
                                val topPad = 16.dp.toPx()
                                val bottomPad = 24.dp.toPx()
                                val plotH = (size.height - topPad - bottomPad).coerceAtLeast(10f)
                                val targetP = yToPressure(change.position.y, topPad, plotH)
                                inspectedLevel = levels.minByOrNull { abs(it.pressureHpa - targetP) }
                            }
                        )
                    }
            ) {
                val leftPad = 38.dp.toPx()
                val rightWindCol = 44.dp.toPx()
                val topPad = 16.dp.toPx()
                val bottomPad = 24.dp.toPx()
                val plotW = (size.width - leftPad - rightWindCol).coerceAtLeast(50f)
                val plotH = (size.height - topPad - bottomPad).coerceAtLeast(50f)

                val axisStyle = TextStyle(
                    color = labelTextColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )

                // 1. Horizontal Isobars (hPa)
                val isobars = listOf(1000.0, 925.0, 850.0, 700.0, 500.0, 400.0, 300.0, 200.0, 100.0)
                for (p in isobars) {
                    val y = pressureToY(p, topPad, plotH)
                    drawLine(
                        color = gridColor,
                        start = Offset(leftPad, y),
                        end = Offset(leftPad + plotW, y),
                        strokeWidth = if (p == 500.0 || p == 850.0) 1.4f else 0.9f
                    )
                    val label = p.toInt().toString()
                    val layout = textMeasurer.measure(label, axisStyle)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(leftPad - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f)
                    )
                }

                // 2. Clipped Thermodynamic Lines & Sounding Curves
                clipRect(
                    left = leftPad,
                    top = topPad,
                    right = leftPad + plotW,
                    bottom = topPad + plotH
                ) {
                    // Skewed Isotherms (-80 C to +50 C every 10 C)
                    for (t in -80..50 step 10) {
                        val yBot = topPad + plotH
                        val yTop = topPad
                        val xBot = tempToX(t.toDouble(), yBot, leftPad, plotW, topPad, plotH)
                        val xTop = tempToX(t.toDouble(), yTop, leftPad, plotW, topPad, plotH)
                        val isZero = t == 0
                        val isMinus20 = t == -20
                        drawLine(
                            color = when {
                                isZero -> SkyCyan.copy(alpha = 0.65f)
                                isMinus20 -> SkyCyan.copy(alpha = 0.35f)
                                else -> gridColor
                            },
                            start = Offset(xBot, yBot),
                            end = Offset(xTop, yTop),
                            strokeWidth = if (isZero) 1.8f else 0.85f,
                            pathEffect = if (isZero) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                        )
                    }

                    // Dry Adiabats (theta = 260..420 K step 20 K)
                    for (theta in 250..410 step 20) {
                        val path = Path()
                        var started = false
                        var p = 1050.0
                        while (p >= 100.0) {
                            val y = pressureToY(p, topPad, plotH)
                            val t = AtmosphericCalculator.dryAdiabatTempC(theta.toDouble(), p)
                            val x = tempToX(t, y, leftPad, plotW, topPad, plotH)
                            if (!started) {
                                path.moveTo(x, y)
                                started = true
                            } else {
                                path.lineTo(x, y)
                            }
                            p -= 40.0
                        }
                        drawPath(path, color = ThermalAmber.copy(alpha = 0.14f), style = Stroke(width = 1f))
                    }

                    // Saturation Mixing Ratio lines (2, 5, 10, 15, 20 g/kg)
                    for (w in listOf(2.0, 5.0, 10.0, 15.0, 20.0)) {
                        val path = Path()
                        var started = false
                        for (p in listOf(1050.0, 850.0, 700.0, 500.0, 350.0)) {
                            val y = pressureToY(p, topPad, plotH)
                            val t = AtmosphericCalculator.tempFromMixingRatio(p, w)
                            val x = tempToX(t, y, leftPad, plotW, topPad, plotH)
                            if (!started) {
                                path.moveTo(x, y)
                                started = true
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                        drawPath(
                            path,
                            color = DewpointEmerald.copy(alpha = 0.22f),
                            style = Stroke(
                                width = 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                            )
                        )
                    }

                    // Positive CAPE area shading between Parcel & Env Temp
                    if (parcelAnalysis.trace.size >= 2 && parcelAnalysis.capeJkg > 5) {
                        val capePath = Path()
                        var inCape = false
                        val positivePts = mutableListOf<Pair<Offset, Offset>>()
                        for (pt in parcelAnalysis.trace) {
                            if (pt.parcelTempC > pt.envTempC && pt.pressureHpa >= 100.0) {
                                val y = pressureToY(pt.pressureHpa, topPad, plotH)
                                val xParcel = tempToX(pt.parcelTempC, y, leftPad, plotW, topPad, plotH)
                                val xEnv = tempToX(pt.envTempC, y, leftPad, plotW, topPad, plotH)
                                positivePts.add(Offset(xEnv, y) to Offset(xParcel, y))
                                inCape = true
                            } else if (inCape && positivePts.isNotEmpty()) {
                                break
                            }
                        }
                        if (positivePts.size >= 2) {
                            capePath.moveTo(positivePts.first().first.x, positivePts.first().first.y)
                            for (pair in positivePts) capePath.lineTo(pair.second.x, pair.second.y)
                            for (pair in positivePts.reversed()) capePath.lineTo(pair.first.x, pair.first.y)
                            capePath.close()
                            drawPath(capePath, color = InstabilityCoral.copy(alpha = 0.20f))
                        }
                    }

                    // Surface Parcel Trace
                    if (parcelAnalysis.trace.size >= 2) {
                        val pPath = Path()
                        parcelAnalysis.trace.forEachIndexed { idx, pt ->
                            val y = pressureToY(pt.pressureHpa, topPad, plotH)
                            val x = tempToX(pt.parcelTempC, y, leftPad, plotW, topPad, plotH)
                            if (idx == 0) pPath.moveTo(x, y) else pPath.lineTo(x, y)
                        }
                        drawPath(
                            path = pPath,
                            color = ParcelGold.copy(alpha = 0.85f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
                            )
                        )
                    }

                    // Dewpoint Trace (Td)
                    val tdLevels = levels.filter { it.dewpointC != null }
                    if (tdLevels.size >= 2) {
                        val tdPath = Path()
                        tdLevels.forEachIndexed { idx, lvl ->
                            val y = pressureToY(lvl.pressureHpa, topPad, plotH)
                            val x = tempToX(lvl.dewpointC!!, y, leftPad, plotW, topPad, plotH)
                            if (idx == 0) tdPath.moveTo(x, y) else tdPath.lineTo(x, y)
                        }
                        drawPath(
                            path = tdPath,
                            color = DewpointEmerald,
                            style = Stroke(
                                width = 2.8.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Environmental Temperature Trace (T)
                    if (levels.size >= 2) {
                        val tPath = Path()
                        levels.forEachIndexed { idx, lvl ->
                            val y = pressureToY(lvl.pressureHpa, topPad, plotH)
                            val x = tempToX(lvl.tempC, y, leftPad, plotW, topPad, plotH)
                            if (idx == 0) tPath.moveTo(x, y) else tPath.lineTo(x, y)
                        }
                        drawPath(
                            path = tPath,
                            color = ThermalAmber,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Inspected level cursor line
                    inspectedLevel?.let { lvl ->
                        val y = pressureToY(lvl.pressureHpa, topPad, plotH)
                        drawLine(
                            color = SkyCyan,
                            start = Offset(leftPad, y),
                            end = Offset(leftPad + plotW, y),
                            strokeWidth = 1.6.dp.toPx()
                        )
                        val xT = tempToX(lvl.tempC, y, leftPad, plotW, topPad, plotH)
                        drawCircle(color = ThermalAmber, radius = 4.5.dp.toPx(), center = Offset(xT, y))
                        lvl.dewpointC?.let { td ->
                            val xTd = tempToX(td, y, leftPad, plotW, topPad, plotH)
                            drawCircle(color = DewpointEmerald, radius = 4.5.dp.toPx(), center = Offset(xTd, y))
                        }
                    }
                }

                // 3. Bottom temperature labels along 1050 hPa
                for (t in listOf(-40, -30, -20, -10, 0, 10, 20, 30, 40)) {
                    val x = tempToX(t.toDouble(), topPad + plotH, leftPad, plotW, topPad, plotH)
                    if (x in leftPad..(leftPad + plotW)) {
                        val dispT = if (useFahrenheit) ((t * 9.0 / 5.0) + 32.0).roundToInt() else t
                        val unit = if (useFahrenheit) "°" else "°"
                        val layout = textMeasurer.measure("$dispT$unit", axisStyle)
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(x - layout.size.width / 2f, topPad + plotH + 4.dp.toPx())
                        )
                    }
                }

                // 4. Right Wind Barb Column
                val staffX = leftPad + plotW + rightWindCol * 0.52f
                drawLine(
                    color = subtleLineColor,
                    start = Offset(leftPad + plotW, topPad),
                    end = Offset(leftPad + plotW, topPad + plotH),
                    strokeWidth = 1f
                )
                val windLevels = levels.filter { it.windDirDeg != null && it.windSpeedKt != null && it.pressureHpa >= 100.0 }
                var lastDrawnY = Float.MAX_VALUE
                for (lvl in windLevels) {
                    val y = pressureToY(lvl.pressureHpa, topPad, plotH)
                    if (abs(lastDrawnY - y) >= 22.dp.toPx()) {
                        drawWindBarb(
                            center = Offset(staffX, y),
                            dirDeg = lvl.windDirDeg!!,
                            speedKt = lvl.windSpeedKt!!,
                            color = windColor,
                            staffLen = 17.dp.toPx()
                        )
                        lastDrawnY = y
                    }
                }

                // Plot frame border
                drawRect(
                    color = gridColor,
                    topLeft = Offset(leftPad, topPad),
                    size = androidx.compose.ui.geometry.Size(plotW, plotH),
                    style = Stroke(width = 1.2f)
                )
            }
        }

        // Interactive Level Readout Bar
        val shown = inspectedLevel ?: levels.firstOrNull()
        if (shown != null) {
            Spacer(modifier = Modifier.height(6.dp))
            val tVal = if (useFahrenheit) (shown.tempC * 9.0 / 5.0 + 32.0) else shown.tempC
            val tdVal = shown.dewpointC?.let { if (useFahrenheit) (it * 9.0 / 5.0 + 32.0) else it }
            val tUnit = if (useFahrenheit) "°F" else "°C"
            val wSpd = shown.windSpeedKt?.let { if (useMph) (it * 1.15078).roundToInt() else it.roundToInt() }
            val wUnit = if (useMph) "mph" else "kt"
            val rh = shown.dewpointC?.let { AtmosphericCalculator.rhFromTempAndDewpoint(shown.tempC, it) }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${shown.pressureHpa.roundToInt()} hPa • ${shown.heightM.roundToInt()} m",
                        style = MaterialTheme.typography.labelMedium,
                        color = SkyCyan
                    )
                    Text(
                        text = "T ${"%.1f".format(tVal)}$tUnit  Td ${tdVal?.let { "%.1f".format(it) } ?: "--"}$tUnit${rh?.let { " ($it%)" } ?: ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (shown.windDirDeg != null && wSpd != null) {
                            "${shown.windDirDeg.roundToInt().toString().padStart(3, '0')}°/${wSpd}$wUnit"
                        } else "--",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawWindBarb(
    center: Offset,
    dirDeg: Double,
    speedKt: Double,
    color: Color,
    staffLen: Float
) {
    drawCircle(color = color, radius = 2.dp.toPx(), center = center)
    if (speedKt < 2.5) {
        drawCircle(color = color, radius = 4.dp.toPx(), center = center, style = Stroke(1.2f))
        return
    }
    // Meteorological convention: barb points INTO the wind (direction wind comes FROM)
    val rad = (dirDeg - 90.0) * PI / 180.0
    val ux = cos(rad).toFloat()
    val uy = sin(rad).toFloat()
    val end = Offset(center.x + ux * staffLen, center.y + uy * staffLen)
    drawLine(color = color, start = center, end = end, strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)

    // Tick vector perpendicular (clockwise in NH)
    val px = -uy
    val py = ux
    val tickLen = staffLen * 0.42f
    var rem = ((speedKt + 2.5) / 5.0).toInt() * 5
    var posAlong = staffLen

    while (rem >= 50) {
        val base1 = Offset(center.x + ux * posAlong, center.y + uy * posAlong)
        val base2 = Offset(center.x + ux * (posAlong - staffLen * 0.22f), center.y + uy * (posAlong - staffLen * 0.22f))
        val tip = Offset(base1.x + px * tickLen, base1.y + py * tickLen)
        val flag = Path().apply {
            moveTo(base1.x, base1.y)
            lineTo(tip.x, tip.y)
            lineTo(base2.x, base2.y)
            close()
        }
        drawPath(flag, color = color)
        posAlong -= staffLen * 0.28f
        rem -= 50
    }
    while (rem >= 10) {
        val base = Offset(center.x + ux * posAlong, center.y + uy * posAlong)
        val tip = Offset(base.x + px * tickLen + ux * 1.5f, base.y + py * tickLen + uy * 1.5f)
        drawLine(color = color, start = base, end = tip, strokeWidth = 1.3.dp.toPx(), cap = StrokeCap.Round)
        posAlong -= staffLen * 0.17f
        rem -= 10
    }
    if (rem >= 5) {
        val base = Offset(center.x + ux * posAlong, center.y + uy * posAlong)
        val tip = Offset(base.x + px * (tickLen * 0.55f), base.y + py * (tickLen * 0.55f))
        drawLine(color = color, start = base, end = tip, strokeWidth = 1.3.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
