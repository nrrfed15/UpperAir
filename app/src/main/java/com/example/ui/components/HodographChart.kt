package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.SoundingProfile
import com.example.domain.physics.AtmosphericCalculator
import com.example.ui.theme.DewpointEmerald
import com.example.ui.theme.InstabilityCoral
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.StormViolet
import com.example.ui.theme.ThermalAmber
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun HodographChart(
    profile: SoundingProfile,
    useMph: Boolean,
    modifier: Modifier = Modifier
) {
    val sorted = remember(profile) { profile.levels.sortedByDescending { it.pressureHpa } }
    val sfcH = sorted.firstOrNull()?.heightM ?: 0.0
    val bunkers = remember(sorted) { AtmosphericCalculator.computeBunkersRightMotionKt(sorted) }
    val textMeasurer = rememberTextMeasurer()

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
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
                text = "HODOGRAPH (BUNKERS RM)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "0–6km ${profile.indices.shear06Kt}kt • SRH ${profile.indices.srh03M2S2}",
                style = MaterialTheme.typography.labelSmall,
                color = SkyCyan
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("hodograph_canvas")
            ) {
                val maxSpeedKt = max(
                    80.0,
                    ((sorted.mapNotNull { it.windSpeedKt }.maxOrNull() ?: 60.0) / 20.0).roundToInt() * 20.0 + 20.0
                ).coerceAtMost(140.0)

                val cx = size.width / 2f
                val cy = size.height / 2f
                val radiusMax = (min(size.width, size.height) / 2f) - 22.dp.toPx()
                val scale = (radiusMax / maxSpeedKt).toFloat()

                val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)

                // Polar speed rings
                val ringStep = 20
                var ring = ringStep
                while (ring <= maxSpeedKt.toInt()) {
                    val r = ring * scale
                    drawCircle(
                        color = gridColor,
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = if (ring % 40 == 0) 1.3f else 0.8f)
                    )
                    val disp = if (useMph) (ring * 1.15078).roundToInt() else ring
                    val txt = textMeasurer.measure("$disp", labelStyle)
                    drawText(txt, topLeft = Offset(cx + 3.dp.toPx(), cy - r + 2.dp.toPx()))
                    ring += ringStep
                }

                // Crosshair axes
                drawLine(gridColor, Offset(cx - radiusMax, cy), Offset(cx + radiusMax, cy), strokeWidth = 1f)
                drawLine(gridColor, Offset(cx, cy - radiusMax), Offset(cx, cy + radiusMax), strokeWidth = 1f)

                val windPts = sorted.filter { it.windDirDeg != null && it.windSpeedKt != null }
                for (i in 0 until windPts.size - 1) {
                    val a = windPts[i]
                    val b = windPts[i + 1]
                    val (ua, va) = AtmosphericCalculator.windVectorFromMet(a.windDirDeg!!, a.windSpeedKt!!)
                    val (ub, vb) = AtmosphericCalculator.windVectorFromMet(b.windDirDeg!!, b.windSpeedKt!!)

                    val aglKm = ((a.heightM - sfcH) / 1000.0).coerceAtLeast(0.0)
                    val segColor = when {
                        aglKm < 1.0 -> InstabilityCoral
                        aglKm < 3.0 -> ThermalAmber
                        aglKm < 6.0 -> DewpointEmerald
                        aglKm < 9.0 -> SkyCyan
                        else -> StormViolet
                    }

                    val p1 = Offset(cx + (ua * scale).toFloat(), cy - (va * scale).toFloat())
                    val p2 = Offset(cx + (ub * scale).toFloat(), cy - (vb * scale).toFloat())
                    drawLine(
                        color = segColor,
                        start = p1,
                        end = p2,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Bunkers Right-Moving Storm Motion marker
                val bx = cx + (bunkers.first * scale).toFloat()
                val by = cy - (bunkers.second * scale).toFloat()
                drawCircle(
                    color = InstabilityCoral,
                    radius = 6.dp.toPx(),
                    center = Offset(bx, by),
                    style = Stroke(width = 2.dp.toPx())
                )
                val rmTxt = textMeasurer.measure(
                    "RM",
                    TextStyle(color = InstabilityCoral, fontSize = 10.sp)
                )
                drawText(rmTxt, topLeft = Offset(bx + 8.dp.toPx(), by - 6.dp.toPx()))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            HodoLegend("0-1km", InstabilityCoral)
            HodoLegend("1-3km", ThermalAmber)
            HodoLegend("3-6km", DewpointEmerald)
            HodoLegend("6-9km", SkyCyan)
            HodoLegend("9km+", StormViolet)
        }
    }
}

@Composable
private fun HodoLegend(label: String, color: Color) {
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
