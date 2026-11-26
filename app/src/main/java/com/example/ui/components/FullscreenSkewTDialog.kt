package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.SoundingLevel
import com.example.domain.model.SoundingProfile
import com.example.domain.physics.AtmosphericCalculator
import com.example.ui.theme.DewpointEmerald
import com.example.ui.theme.InstabilityCoral
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.ThermalAmber
import com.example.ui.util.TimeFormatters
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun FullscreenSkewTDialog(
    stationLabel: String,
    profile: SoundingProfile,
    useFahrenheit: Boolean,
    useMph: Boolean,
    inspectedLevel: SoundingLevel?,
    onInspectLevel: (SoundingLevel?) -> Unit,
    onDismiss: () -> Unit
) {
    val levels = remember(profile) { profile.levels.sortedByDescending { it.pressureHpa } }
    val parcelAnalysis = remember(levels) { AtmosphericCalculator.computeParcelAnalysis(levels) }
    val textMeasurer = rememberTextMeasurer()

    var zoomScale by remember(profile) { mutableFloatStateOf(1f) }
    var panOffset by remember(profile) { mutableStateOf(Offset.Zero) }
    var scrubModeWhenZoomed by remember(profile) { mutableStateOf(false) }

    val chartColors = SkewTColors(
        gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.48f),
        subtleLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        windColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .testTag("fullscreen_skew_t_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Fullscreen SHARPpy Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$stationLabel • ${profile.sourceLabel}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${TimeFormatters.formatUtcShort(profile.validEpochMs)} • Drag to scrub • Pinch to zoom",
                            style = MaterialTheme.typography.labelSmall,
                            color = SkyCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (zoomScale > 1.01f) {
                            Surface(
                                color = if (scrubModeWhenZoomed) SkyCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, if (scrubModeWhenZoomed) SkyCyan else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                    .clickable { scrubModeWhenZoomed = !scrubModeWhenZoomed }
                                    .testTag("fullscreen_mode_toggle")
                            ) {
                                Text(
                                    text = if (scrubModeWhenZoomed) "SCRUB" else "PAN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (scrubModeWhenZoomed) SkyCyan else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(
                            onClick = {
                                val next = (zoomScale - 0.35f).coerceIn(1f, 2.5f)
                                zoomScale = next
                                if (next <= 1.01f) panOffset = Offset.Zero
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("fullscreen_zoom_out")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom out", modifier = Modifier.size(18.dp))
                        }

                        Text(
                            text = "${"%.1f".format(zoomScale)}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        IconButton(
                            onClick = {
                                zoomScale = (zoomScale + 0.35f).coerceIn(1f, 2.5f)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("fullscreen_zoom_in")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom in", modifier = Modifier.size(18.dp))
                        }

                        if (zoomScale > 1.01f) {
                            IconButton(
                                onClick = {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                    scrubModeWhenZoomed = false
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("fullscreen_zoom_reset")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset zoom", tint = SkyCyan, modifier = Modifier.size(18.dp))
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("fullscreen_close_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close fullscreen Skew-T")
                        }
                    }
                }

                // Unrestricted Fullscreen Interactive Skew-T Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clipToBounds()
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("fullscreen_skew_t_canvas")
                            .pointerInput(levels, scrubModeWhenZoomed) {
                                awaitEachGesture {
                                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                                    val topPad = 14.dp.toPx()
                                    val bottomPad = 22.dp.toPx()
                                    val plotH = (size.height - topPad - bottomPad).coerceAtLeast(10f)

                                    fun inspectAtScreenY(screenY: Float) {
                                        val chartY = (screenY - panOffset.y) / zoomScale
                                        val targetP = SkewTGeometry.yToPressure(chartY, topPad, plotH)
                                        onInspectLevel(levels.minByOrNull { abs(it.pressureHpa - targetP) })
                                    }

                                    // Immediate inspection on touch when at 1x or in Scrub mode
                                    if (zoomScale <= 1.01f || scrubModeWhenZoomed) {
                                        inspectAtScreenY(firstDown.position.y)
                                    }

                                    var movedSignificantly = false
                                    do {
                                        val event = awaitPointerEvent()
                                        val activePointers = event.changes.count { it.pressed }

                                        if (activePointers >= 2) {
                                            movedSignificantly = true
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            val centroid = event.calculateCentroid(useCurrent = false)
                                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                                val prevScale = zoomScale
                                                val newScale = (prevScale * zoomChange).coerceIn(1f, 2.5f)
                                                val scaleRatio = newScale / prevScale
                                                val rawPan = if (centroid != Offset.Unspecified) {
                                                    centroid - (centroid - panOffset) * scaleRatio + panChange
                                                } else {
                                                    panOffset + panChange
                                                }
                                                val minX = size.width * (1f - newScale)
                                                val minY = size.height * (1f - newScale)
                                                zoomScale = newScale
                                                panOffset = if (newScale <= 1.01f) {
                                                    Offset.Zero
                                                } else {
                                                    Offset(
                                                        x = rawPan.x.coerceIn(minX, 0f),
                                                        y = rawPan.y.coerceIn(minY, 0f)
                                                    )
                                                }
                                            }
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        } else if (activePointers == 1) {
                                            val change = event.changes.first { it.pressed }
                                            val delta = change.position - change.previousPosition
                                            if (delta.getDistance() > 2f) movedSignificantly = true

                                            if (zoomScale <= 1.01f || scrubModeWhenZoomed) {
                                                inspectAtScreenY(change.position.y)
                                                change.consume()
                                            } else {
                                                val minX = size.width * (1f - zoomScale)
                                                val minY = size.height * (1f - zoomScale)
                                                panOffset = Offset(
                                                    x = (panOffset.x + delta.x).coerceIn(minX, 0f),
                                                    y = (panOffset.y + delta.y).coerceIn(minY, 0f)
                                                )
                                                change.consume()
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })

                                    // Tap while zoomed in Pan mode still inspects the tapped level
                                    if (!movedSignificantly && zoomScale > 1.01f && !scrubModeWhenZoomed) {
                                        inspectAtScreenY(firstDown.position.y)
                                    }
                                }
                            }
                    ) {
                        withTransform({
                            translate(left = panOffset.x, top = panOffset.y)
                            scale(scaleX = zoomScale, scaleY = zoomScale, pivot = Offset.Zero)
                        }) {
                            drawSkewTDiagram(
                                levels = levels,
                                parcelAnalysis = parcelAnalysis,
                                inspectedLevel = inspectedLevel,
                                useFahrenheit = useFahrenheit,
                                colors = chartColors,
                                textMeasurer = textMeasurer,
                                highlightCursor = true
                            )
                        }
                    }
                }

                // Docked SHARPpy Live Telemetry Footer
                val shown = inspectedLevel ?: levels.firstOrNull()
                if (shown != null) {
                    SkewTLevelReadoutBar(
                        level = shown,
                        useFahrenheit = useFahrenheit,
                        useMph = useMph,
                        isActiveScrub = true
                    )
                }

                val idx = profile.indices
                val shearStr = if (useMph) "${(idx.shear06Kt * 1.15078).roundToInt()}mph" else "${idx.shear06Kt}kt"
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CompactStat("SBCAPE", "${idx.sbcapeJkg}", InstabilityCoral)
                        CompactStat("MLCAPE", "${idx.mlcapeJkg}", ThermalAmber)
                        CompactStat("CIN", "${idx.sbcinJkg}", MaterialTheme.colorScheme.onSurfaceVariant)
                        CompactStat("0-6 SHR", shearStr, SkyCyan)
                        CompactStat("SRH 0-3", "${idx.srh03M2S2}", DewpointEmerald)
                        CompactStat("LCL", "${idx.lclMeters}m", MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}
