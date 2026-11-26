package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.SoundingLevel
import com.example.domain.model.SoundingProfile
import com.example.domain.physics.AtmosphericCalculator
import com.example.domain.physics.ParcelAnalysis
import com.example.ui.theme.DewpointEmerald
import com.example.ui.theme.InstabilityCoral
import com.example.ui.theme.ParcelGold
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.ThermalAmber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

internal object SkewTGeometry {
    const val P_MAX = 1050.0
    const val P_MIN = 100.0
    const val T_MIN = -42.0
    const val T_MAX = 45.0
    const val SKEW_SLOPE = 1.05f

    fun pressureToY(p: Double, topPad: Float, plotH: Float): Float {
        val ratio = (ln(P_MAX) - ln(p.coerceIn(P_MIN, P_MAX))) / (ln(P_MAX) - ln(P_MIN))
        return topPad + plotH * (1f - ratio.toFloat())
    }

    fun yToPressure(y: Float, topPad: Float, plotH: Float): Double {
        val ratio = 1.0 - ((y - topPad) / plotH).coerceIn(0f, 1f)
        val lnP = ln(P_MAX) - ratio * (ln(P_MAX) - ln(P_MIN))
        return exp(lnP)
    }

    fun tempToX(tempC: Double, y: Float, leftPad: Float, plotW: Float, topPad: Float, plotH: Float): Float {
        val baseFraction = ((tempC - T_MIN) / (T_MAX - T_MIN)).toFloat()
        val yNormFromBottom = 1f - ((y - topPad) / plotH)
        return leftPad + (baseFraction + SKEW_SLOPE * yNormFromBottom * 0.55f) * plotW
    }
}

internal data class SkewTColors(
    val gridColor: Color,
    val subtleLineColor: Color,
    val labelTextColor: Color,
    val windColor: Color
)

@Composable
fun SkewTChart(
    profile: SoundingProfile,
    useFahrenheit: Boolean,
    useMph: Boolean,
    inspectedLevel: SoundingLevel?,
    onInspectLevel: (SoundingLevel?) -> Unit,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val levels = remember(profile) { profile.levels.sortedByDescending { it.pressureHpa } }
    val parcelAnalysis = remember(levels) { AtmosphericCalculator.computeParcelAnalysis(levels) }
    var isScrubbing by remember(profile) { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val chartColors = SkewTColors(
        gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        subtleLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        windColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
    )

    val borderColor = if (isScrubbing) SkyCyan else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isScrubbing) 1.5.dp else 1.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // Compact SHARPpy legend + gesture status + Fullscreen trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendChip(color = ThermalAmber, label = "T")
                LegendChip(color = DewpointEmerald, label = "Td")
                LegendChip(color = ParcelGold, label = "Parcel")
                if (isScrubbing) {
                    Surface(
                        color = SkyCyan.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "SCRUBBING",
                            style = MaterialTheme.typography.labelSmall,
                            color = SkyCyan,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Tap • Hold to scrub",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onOpenFullscreen,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("skew_t_fullscreen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Open fullscreen Skew-T inspector",
                    tint = SkyCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(356.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("skew_t_canvas")
                    // Single tap selects nearest pressure level without blocking vertical scroll
                    .pointerInput(levels) {
                        detectTapGestures { offset ->
                            val topPad = 14.dp.toPx()
                            val bottomPad = 22.dp.toPx()
                            val plotH = (size.height - topPad - bottomPad).coerceAtLeast(10f)
                            val targetP = SkewTGeometry.yToPressure(offset.y, topPad, plotH)
                            onInspectLevel(levels.minByOrNull { abs(it.pressureHpa - targetP) })
                        }
                    }
                    // Long-press initiates vertical scrubbing; regular swipes pass straight to LazyColumn
                    .pointerInput(levels) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { startOffset ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                isScrubbing = true
                                val topPad = 14.dp.toPx()
                                val bottomPad = 22.dp.toPx()
                                val plotH = (size.height - topPad - bottomPad).coerceAtLeast(10f)
                                val targetP = SkewTGeometry.yToPressure(startOffset.y, topPad, plotH)
                                onInspectLevel(levels.minByOrNull { abs(it.pressureHpa - targetP) })
                            },
                            onDragEnd = { isScrubbing = false },
                            onDragCancel = { isScrubbing = false },
                            onDrag = { change, _ ->
                                change.consume()
                                val topPad = 14.dp.toPx()
                                val bottomPad = 22.dp.toPx()
                                val plotH = (size.height - topPad - bottomPad).coerceAtLeast(10f)
                                val targetP = SkewTGeometry.yToPressure(change.position.y, topPad, plotH)
                                onInspectLevel(levels.minByOrNull { abs(it.pressureHpa - targetP) })
                            }
                        )
                    }
            ) {
                drawSkewTDiagram(
                    levels = levels,
                    parcelAnalysis = parcelAnalysis,
                    inspectedLevel = inspectedLevel,
                    useFahrenheit = useFahrenheit,
                    colors = chartColors,
                    textMeasurer = textMeasurer,
                    highlightCursor = isScrubbing
                )
            }
        }

        // Compact Monospace Level Readout Bar
        val shown = inspectedLevel ?: levels.firstOrNull()
        if (shown != null) {
            Spacer(modifier = Modifier.height(5.dp))
            SkewTLevelReadoutBar(
                level = shown,
                useFahrenheit = useFahrenheit,
                useMph = useMph,
                isActiveScrub = isScrubbing
            )
        }
    }
}

@Composable
internal fun SkewTLevelReadoutBar(
    level: SoundingLevel,
    useFahrenheit: Boolean,
    useMph: Boolean,
    isActiveScrub: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tVal = if (useFahrenheit) (level.tempC * 9.0 / 5.0 + 32.0) else level.tempC
    val tdVal = level.dewpointC?.let { if (useFahrenheit) (it * 9.0 / 5.0 + 32.0) else it }
    val tUnit = if (useFahrenheit) "°F" else "°C"
    val wSpd = level.windSpeedKt?.let { if (useMph) (it * 1.15078).roundToInt() else it.roundToInt() }
    val wUnit = if (useMph) "mph" else "kt"
    val rh = level.dewpointC?.let { AtmosphericCalculator.rhFromTempAndDewpoint(level.tempC, it) }

    Surface(
        color = if (isActiveScrub) SkyCyan.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isActiveScrub) SkyCyan.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(6.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${level.pressureHpa.roundToInt()} hPa • ${level.heightM.roundToInt()}m",
                style = MaterialTheme.typography.labelMedium,
                color = SkyCyan,
                maxLines = 1
            )
            Text(
                text = "T ${"%.1f".format(tVal)}$tUnit  Td ${tdVal?.let { "%.1f".format(it) } ?: "--"}$tUnit${rh?.let { " ($it%)" } ?: ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (level.windDirDeg != null && wSpd != null) {
                    "${level.windDirDeg.roundToInt().toString().padStart(3, '0')}°/$wSpd$wUnit"
                } else "--",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

internal fun DrawScope.drawSkewTDiagram(
    levels: List<SoundingLevel>,
    parcelAnalysis: ParcelAnalysis,
    inspectedLevel: SoundingLevel?,
    useFahrenheit: Boolean,
    colors: SkewTColors,
    textMeasurer: TextMeasurer,
    highlightCursor: Boolean = false
) {
    val leftPad = 36.dp.toPx()
    val rightWindCol = 42.dp.toPx()
    val topPad = 14.dp.toPx()
    val bottomPad = 22.dp.toPx()
    val plotW = (size.width - leftPad - rightWindCol).coerceAtLeast(50f)
    val plotH = (size.height - topPad - bottomPad).coerceAtLeast(50f)

    val axisStyle = TextStyle(
        color = colors.labelTextColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium
    )

    // 1. Horizontal Isobars (hPa)
    val isobars = listOf(1000.0, 925.0, 850.0, 700.0, 500.0, 400.0, 300.0, 200.0, 100.0)
    for (p in isobars) {
        val y = SkewTGeometry.pressureToY(p, topPad, plotH)
        drawLine(
            color = colors.gridColor,
            start = Offset(leftPad, y),
            end = Offset(leftPad + plotW, y),
            strokeWidth = if (p == 500.0 || p == 850.0) 1.4f else 0.85f
        )
        val label = p.toInt().toString()
        val layout = textMeasurer.measure(label, axisStyle)
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(leftPad - layout.size.width - 5.dp.toPx(), y - layout.size.height / 2f)
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
            val xBot = SkewTGeometry.tempToX(t.toDouble(), yBot, leftPad, plotW, topPad, plotH)
            val xTop = SkewTGeometry.tempToX(t.toDouble(), yTop, leftPad, plotW, topPad, plotH)
            val isZero = t == 0
            val isMinus20 = t == -20
            drawLine(
                color = when {
                    isZero -> SkyCyan.copy(alpha = 0.7f)
                    isMinus20 -> SkyCyan.copy(alpha = 0.38f)
                    else -> colors.gridColor
                },
                start = Offset(xBot, yBot),
                end = Offset(xTop, yTop),
                strokeWidth = if (isZero) 1.8f else 0.85f,
                pathEffect = if (isZero) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
            )
        }

        // Dry Adiabats
        for (theta in 250..410 step 20) {
            val path = Path()
            var started = false
            var p = 1050.0
            while (p >= 100.0) {
                val y = SkewTGeometry.pressureToY(p, topPad, plotH)
                val t = AtmosphericCalculator.dryAdiabatTempC(theta.toDouble(), p)
                val x = SkewTGeometry.tempToX(t, y, leftPad, plotW, topPad, plotH)
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
                val y = SkewTGeometry.pressureToY(p, topPad, plotH)
                val t = AtmosphericCalculator.tempFromMixingRatio(p, w)
                val x = SkewTGeometry.tempToX(t, y, leftPad, plotW, topPad, plotH)
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

        // Positive CAPE area shading
        if (parcelAnalysis.trace.size >= 2 && parcelAnalysis.capeJkg > 5) {
            val capePath = Path()
            var inCape = false
            val positivePts = mutableListOf<Pair<Offset, Offset>>()
            for (pt in parcelAnalysis.trace) {
                if (pt.parcelTempC > pt.envTempC && pt.pressureHpa >= 100.0) {
                    val y = SkewTGeometry.pressureToY(pt.pressureHpa, topPad, plotH)
                    val xParcel = SkewTGeometry.tempToX(pt.parcelTempC, y, leftPad, plotW, topPad, plotH)
                    val xEnv = SkewTGeometry.tempToX(pt.envTempC, y, leftPad, plotW, topPad, plotH)
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
                drawPath(capePath, color = InstabilityCoral.copy(alpha = 0.22f))
            }
        }

        // Surface Parcel Trace
        if (parcelAnalysis.trace.size >= 2) {
            val pPath = Path()
            parcelAnalysis.trace.forEachIndexed { idx, pt ->
                val y = SkewTGeometry.pressureToY(pt.pressureHpa, topPad, plotH)
                val x = SkewTGeometry.tempToX(pt.parcelTempC, y, leftPad, plotW, topPad, plotH)
                if (idx == 0) pPath.moveTo(x, y) else pPath.lineTo(x, y)
            }
            drawPath(
                path = pPath,
                color = ParcelGold.copy(alpha = 0.88f),
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                )
            )
        }

        // Dewpoint Trace (Td)
        val tdLevels = levels.filter { it.dewpointC != null }
        if (tdLevels.size >= 2) {
            val tdPath = Path()
            tdLevels.forEachIndexed { idx, lvl ->
                val y = SkewTGeometry.pressureToY(lvl.pressureHpa, topPad, plotH)
                val x = SkewTGeometry.tempToX(lvl.dewpointC!!, y, leftPad, plotW, topPad, plotH)
                if (idx == 0) tdPath.moveTo(x, y) else tdPath.lineTo(x, y)
            }
            drawPath(
                path = tdPath,
                color = DewpointEmerald,
                style = Stroke(
                    width = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Environmental Temperature Trace (T)
        if (levels.size >= 2) {
            val tPath = Path()
            levels.forEachIndexed { idx, lvl ->
                val y = SkewTGeometry.pressureToY(lvl.pressureHpa, topPad, plotH)
                val x = SkewTGeometry.tempToX(lvl.tempC, y, leftPad, plotW, topPad, plotH)
                if (idx == 0) tPath.moveTo(x, y) else tPath.lineTo(x, y)
            }
            drawPath(
                path = tPath,
                color = ThermalAmber,
                style = Stroke(
                    width = 2.8.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Inspected level cursor line
        inspectedLevel?.let { lvl ->
            val y = SkewTGeometry.pressureToY(lvl.pressureHpa, topPad, plotH)
            drawLine(
                color = if (highlightCursor) SkyCyan else SkyCyan.copy(alpha = 0.85f),
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = if (highlightCursor) 2.dp.toPx() else 1.4.dp.toPx()
            )
            val xT = SkewTGeometry.tempToX(lvl.tempC, y, leftPad, plotW, topPad, plotH)
            drawCircle(color = ThermalAmber, radius = 4.5.dp.toPx(), center = Offset(xT, y))
            lvl.dewpointC?.let { td ->
                val xTd = SkewTGeometry.tempToX(td, y, leftPad, plotW, topPad, plotH)
                drawCircle(color = DewpointEmerald, radius = 4.5.dp.toPx(), center = Offset(xTd, y))
            }
        }
    }

    // 3. Bottom temperature labels along 1050 hPa
    for (t in listOf(-40, -30, -20, -10, 0, 10, 20, 30, 40)) {
        val x = SkewTGeometry.tempToX(t.toDouble(), topPad + plotH, leftPad, plotW, topPad, plotH)
        if (x in leftPad..(leftPad + plotW)) {
            val dispT = if (useFahrenheit) ((t * 9.0 / 5.0) + 32.0).roundToInt() else t
            val layout = textMeasurer.measure("$dispT°", axisStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x - layout.size.width / 2f, topPad + plotH + 4.dp.toPx())
            )
        }
    }

    // 4. Right Wind Barb Column
    val staffX = leftPad + plotW + rightWindCol * 0.52f
    drawLine(
        color = colors.subtleLineColor,
        start = Offset(leftPad + plotW, topPad),
        end = Offset(leftPad + plotW, topPad + plotH),
        strokeWidth = 1f
    )
    val windLevels = levels.filter { it.windDirDeg != null && it.windSpeedKt != null && it.pressureHpa >= 100.0 }
    var lastDrawnY = Float.MAX_VALUE
    for (lvl in windLevels) {
        val y = SkewTGeometry.pressureToY(lvl.pressureHpa, topPad, plotH)
        if (abs(lastDrawnY - y) >= 21.dp.toPx()) {
            drawWindBarb(
                center = Offset(staffX, y),
                dirDeg = lvl.windDirDeg!!,
                speedKt = lvl.windSpeedKt!!,
                color = colors.windColor,
                staffLen = 16.dp.toPx()
            )
            lastDrawnY = y
        }
    }

    // Plot frame border
    drawRect(
        color = colors.gridColor,
        topLeft = Offset(leftPad, topPad),
        size = Size(plotW, plotH),
        style = Stroke(width = 1.2f)
    )
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
    val rad = (dirDeg - 90.0) * PI / 180.0
    val ux = cos(rad).toFloat()
    val uy = sin(rad).toFloat()
    val end = Offset(center.x + ux * staffLen, center.y + uy * staffLen)
    drawLine(color = color, start = center, end = end, strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)

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
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
