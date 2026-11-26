package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.MapTileRepository
import com.example.domain.model.NwsStationsCatalog
import com.example.domain.model.UpperAirStation
import com.example.ui.MapPinSelection
import com.example.ui.UpperAirUiState
import com.example.ui.components.UsaVectorOverlay.drawOfflineUsaBase
import com.example.ui.components.WebMercator
import com.example.ui.theme.DewpointEmerald
import com.example.ui.theme.MidnightVoid
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.ThermalAmber
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

private data class MapRegionPreset(
    val id: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val zoom: Float
)

private val US_REGION_PRESETS = listOf(
    MapRegionPreset("conus", "CONUS", 38.6, -96.8, 3.8f),
    MapRegionPreset("plains", "Plains", 36.8, -98.5, 5.4f),
    MapRegionPreset("midwest", "Midwest", 41.6, -89.4, 5.4f),
    MapRegionPreset("south", "South", 32.8, -88.2, 5.4f),
    MapRegionPreset("east", "East", 40.4, -75.4, 5.5f),
    MapRegionPreset("west", "West", 38.8, -118.4, 5.1f),
    MapRegionPreset("ak", "AK", 63.5, -150.0, 4.1f),
    MapRegionPreset("hi", "HI", 20.6, -157.0, 6.4f)
)

@Composable
fun UsaMapScreen(
    uiState: UpperAirUiState,
    onDropMapPin: (Double, Double) -> Unit,
    onSelectMapStation: (UpperAirStation) -> Unit,
    onClearMapPin: () -> Unit,
    onLoadSelectedMapPointRrfs: () -> Unit,
    onTogglePinSelectedMapPoint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val tileRepository = remember(context.applicationContext) {
        (context.applicationContext as? com.example.UpperAirApplication)?.container?.mapTileRepository
            ?: MapTileRepository(context.applicationContext)
    }

    val darkTheme = uiState.settings.darkTheme
    var centerLat by remember { mutableDoubleStateOf(38.6) }
    var centerLon by remember { mutableDoubleStateOf(-96.8) }
    var zoom by remember { mutableFloatStateOf(3.8f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var tileRevision by remember { mutableIntStateOf(0) }
    var activeRegionId by remember { mutableStateOf("conus") }

    val pulseTransition = rememberInfiniteTransition(label = "pin_pulse")
    val pulseProgress by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pin_pulse_progress"
    )

    val minZoom = 2.8f
    val maxZoom = 11.0f
    val stations = NwsStationsCatalog.stations
    val pinnedIds = uiState.settings.pinnedStationIds
    val activeStationId = uiState.activeStation.id
    val mapPin = uiState.mapPin

    // Lazily fetch visible Web Mercator tiles whenever camera or theme changes
    LaunchedEffect(centerLat, centerLon, zoom, viewportSize, darkTheme) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return@LaunchedEffect
        val tileZ = zoom.roundToInt().coerceIn(3, 11)
        val nTiles = 1 shl tileZ
        val world = WebMercator.worldSizePx(zoom)
        val tileScreenSize = (world / nTiles).toFloat().coerceAtLeast(16f)
        val cxWorld = WebMercator.lonToNormalizedX(centerLon) * world
        val cyWorld = WebMercator.latToNormalizedY(centerLat) * world

        val leftWorld = cxWorld - viewportSize.width * 0.5f
        val rightWorld = cxWorld + viewportSize.width * 0.5f
        val topWorld = cyWorld - viewportSize.height * 0.5f
        val bottomWorld = cyWorld + viewportSize.height * 0.5f

        val minTx = floor(leftWorld / tileScreenSize).toInt().coerceIn(0, nTiles - 1)
        val maxTx = ceil(rightWorld / tileScreenSize).toInt().coerceIn(0, nTiles - 1)
        val minTy = floor(topWorld / tileScreenSize).toInt().coerceIn(0, nTiles - 1)
        val maxTy = ceil(bottomWorld / tileScreenSize).toInt().coerceIn(0, nTiles - 1)

        for (tx in minTx..maxTx) {
            for (ty in minTy..maxTy) {
                if (tileRepository.getMemoryTile(darkTheme, tileZ, tx, ty) == null) {
                    launch {
                        val loaded = tileRepository.loadTile(darkTheme, tileZ, tx, ty)
                        if (loaded != null) {
                            tileRevision++
                        }
                    }
                }
            }
        }
    }

    fun clampCamera(lat: Double, lon: Double): Pair<Double, Double> {
        return lat.coerceIn(15.0, 73.0) to lon.coerceIn(-172.0, -60.0)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (darkTheme) MidnightVoid else Color(0xFFD2E1F0))
    ) {
        // Full-bleed Interactive Map Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .testTag("usa_map_canvas")
                .onSizeChanged { viewportSize = it }
                .pointerInput(stations) {
                    val tapSnapRadiusPx = with(density) { 24.dp.toPx() }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalMovementPx = 0f
                        var hadMultiTouch = false

                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }

                            if (pressedCount >= 2) {
                                hadMultiTouch = true
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)

                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val anchorScreen = if (centroid != Offset.Unspecified) centroid else Offset(w / 2f, h / 2f)
                                    val (anchorLat, anchorLon) = WebMercator.screenToLatLon(
                                        anchorScreen.x,
                                        anchorScreen.y,
                                        centerLat,
                                        centerLon,
                                        zoom,
                                        w,
                                        h
                                    )
                                    val newZoom = (zoom + kotlin.math.ln(zoomChange.toDouble()) / kotlin.math.ln(2.0))
                                        .toFloat()
                                        .coerceIn(minZoom, maxZoom)
                                    val newWorld = WebMercator.worldSizePx(newZoom)
                                    val targetScreen = anchorScreen + panChange
                                    val anchorWx = WebMercator.lonToNormalizedX(anchorLon) * newWorld
                                    val anchorWy = WebMercator.latToNormalizedY(anchorLat) * newWorld
                                    val newCenterWx = anchorWx - (targetScreen.x - w * 0.5f)
                                    val newCenterWy = anchorWy - (targetScreen.y - h * 0.5f)
                                    val (clampedLat, clampedLon) = clampCamera(
                                        WebMercator.normalizedYToLat(newCenterWy / newWorld),
                                        WebMercator.normalizedXToLon(newCenterWx / newWorld)
                                    )
                                    zoom = newZoom
                                    centerLat = clampedLat
                                    centerLon = clampedLon
                                    activeRegionId = ""
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (pressedCount == 1) {
                                val change = event.changes.first { it.pressed }
                                val delta = change.position - change.previousPosition
                                totalMovementPx += delta.getDistance()
                                if (delta != Offset.Zero && totalMovementPx > 4f) {
                                    val world = WebMercator.worldSizePx(zoom)
                                    val cx = WebMercator.lonToNormalizedX(centerLon) * world - delta.x
                                    val cy = WebMercator.latToNormalizedY(centerLat) * world - delta.y
                                    val (clampedLat, clampedLon) = clampCamera(
                                        WebMercator.normalizedYToLat(cy / world),
                                        WebMercator.normalizedXToLon(cx / world)
                                    )
                                    centerLat = clampedLat
                                    centerLon = clampedLon
                                    activeRegionId = ""
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Single Tap -> snap to nearby NWS RAOB dot or drop custom coordinate pin
                        if (!hadMultiTouch && totalMovementPx < 14f) {
                            val tapX = down.position.x
                            val tapY = down.position.y
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)

                            var bestStation: UpperAirStation? = null
                            var bestDistPx = Float.MAX_VALUE
                            for (st in stations) {
                                val pt = WebMercator.latLonToScreen(st.lat, st.lon, centerLat, centerLon, zoom, w, h)
                                val d = hypot(pt.x - tapX, pt.y - tapY)
                                if (d < tapSnapRadiusPx && d < bestDistPx) {
                                    bestDistPx = d
                                    bestStation = st
                                }
                            }

                            if (bestStation != null) {
                                onSelectMapStation(bestStation)
                            } else {
                                val (tapLat, tapLon) = WebMercator.screenToLatLon(tapX, tapY, centerLat, centerLon, zoom, w, h)
                                onDropMapPin(tapLat, tapLon)
                            }
                        }
                    }
                }
        ) {
            // Read tileRevision so Compose redraws as async tiles arrive
            val rev = tileRevision
            if (rev >= 0) {
                // Layer 1: Instant Offline Vector US Map + Graticule
                drawOfflineUsaBase(
                    centerLat = centerLat,
                    centerLon = centerLon,
                    zoom = zoom,
                    darkTheme = darkTheme,
                    textMeasurer = textMeasurer
                )

                // Layer 2: Cached Dark/Light Slippy Map Tiles
                val tileZ = zoom.roundToInt().coerceIn(3, 11)
                val nTiles = 1 shl tileZ
                val world = WebMercator.worldSizePx(zoom)
                val tilePx = world / nTiles
                val cxWorld = WebMercator.lonToNormalizedX(centerLon) * world
                val cyWorld = WebMercator.latToNormalizedY(centerLat) * world

                val leftWorld = cxWorld - size.width * 0.5f
                val rightWorld = cxWorld + size.width * 0.5f
                val topWorld = cyWorld - size.height * 0.5f
                val bottomWorld = cyWorld + size.height * 0.5f

                val minTx = floor(leftWorld / tilePx).toInt().coerceIn(0, nTiles - 1)
                val maxTx = ceil(rightWorld / tilePx).toInt().coerceIn(0, nTiles - 1)
                val minTy = floor(topWorld / tilePx).toInt().coerceIn(0, nTiles - 1)
                val maxTy = ceil(bottomWorld / tilePx).toInt().coerceIn(0, nTiles - 1)

                for (tx in minTx..maxTx) {
                    for (ty in minTy..maxTy) {
                        val bmp = tileRepository.getMemoryTile(darkTheme, tileZ, tx, ty) ?: continue
                        val sx = (tx * tilePx - cxWorld + size.width * 0.5).roundToInt()
                        val sy = (ty * tilePx - cyWorld + size.height * 0.5).roundToInt()
                        val ex = ((tx + 1) * tilePx - cxWorld + size.width * 0.5).roundToInt()
                        val ey = ((ty + 1) * tilePx - cyWorld + size.height * 0.5).roundToInt()
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(sx, sy),
                            dstSize = IntSize((ex - sx).coerceAtLeast(1), (ey - sy).coerceAtLeast(1)),
                            alpha = 0.92f
                        )
                    }
                }
            }

            // Layer 3: 72 NWS RAOB Station Dots
            val labelStyle = TextStyle(
                color = if (darkTheme) Color(0xFFD6E4F0) else Color(0xFF1E293B),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
            val showStationLabels = zoom >= 4.6f
            val dotBg = if (darkTheme) MidnightVoid else Color.White

            for (st in stations) {
                val pt = WebMercator.latLonToScreen(st.lat, st.lon, centerLat, centerLon, zoom, size.width, size.height)
                if (pt.x !in -24f..(size.width + 24f) || pt.y !in -24f..(size.height + 24f)) continue

                val isActive = st.id == activeStationId
                val isPinned = pinnedIds.contains(st.id)
                val dotColor = when {
                    isActive -> SkyCyan
                    isPinned -> ThermalAmber
                    else -> DewpointEmerald
                }
                val r = when {
                    isActive -> 5.dp.toPx()
                    isPinned -> 4.2.dp.toPx()
                    else -> 3.4.dp.toPx()
                }
                drawCircle(color = dotBg, radius = r + 1.6.dp.toPx(), center = pt)
                drawCircle(color = dotColor, radius = r, center = pt)

                if (showStationLabels || isActive) {
                    val txt = textMeasurer.measure(st.id, labelStyle)
                    drawText(txt, topLeft = Offset(pt.x + r + 3.dp.toPx(), pt.y - txt.size.height * 0.5f))
                }
            }

            // Layer 4: Dropped Coordinate Target Reticle + Nearest RAOB Link Line
            if (mapPin != null) {
                val pinPt = WebMercator.latLonToScreen(
                    mapPin.lat,
                    mapPin.lon,
                    centerLat,
                    centerLon,
                    zoom,
                    size.width,
                    size.height
                )
                val raobPt = WebMercator.latLonToScreen(
                    mapPin.nearestRaob.lat,
                    mapPin.nearestRaob.lon,
                    centerLat,
                    centerLon,
                    zoom,
                    size.width,
                    size.height
                )

                // Dashed link to nearest NWS RAOB site when pin is a custom coordinate
                if (mapPin.distanceToRaobKm >= 2.0) {
                    drawLine(
                        color = SkyCyan.copy(alpha = 0.55f),
                        start = pinPt,
                        end = raobPt,
                        strokeWidth = 1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                }

                // Pulsing outer ring
                val pulseRadius = 10.dp.toPx() + pulseProgress * 18.dp.toPx()
                val pulseAlpha = (1f - pulseProgress) * 0.65f
                drawCircle(
                    color = SkyCyan.copy(alpha = pulseAlpha),
                    radius = pulseRadius,
                    center = pinPt,
                    style = Stroke(width = 1.8.dp.toPx())
                )

                // SHARPpy Target Crosshair Reticle
                val armOuter = 13.dp.toPx()
                val armInner = 5.dp.toPx()
                drawLine(SkyCyan, Offset(pinPt.x - armOuter, pinPt.y), Offset(pinPt.x - armInner, pinPt.y), 2.dp.toPx(), StrokeCap.Round)
                drawLine(SkyCyan, Offset(pinPt.x + armInner, pinPt.y), Offset(pinPt.x + armOuter, pinPt.y), 2.dp.toPx(), StrokeCap.Round)
                drawLine(SkyCyan, Offset(pinPt.x, pinPt.y - armOuter), Offset(pinPt.x, pinPt.y - armInner), 2.dp.toPx(), StrokeCap.Round)
                drawLine(SkyCyan, Offset(pinPt.x, pinPt.y + armInner), Offset(pinPt.x, pinPt.y + armOuter), 2.dp.toPx(), StrokeCap.Round)

                drawCircle(color = dotBg, radius = 6.dp.toPx(), center = pinPt)
                drawCircle(color = SkyCyan, radius = 4.5.dp.toPx(), center = pinPt)
                drawCircle(color = Color.White, radius = 1.8.dp.toPx(), center = pinPt)
            }
        }

        // Top Floating HUD: Header + Region Jump Chips
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "USA RRFS 3KM POINT SOUNDING MAP",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Tap any US location • Green = NWS RAOB • Amber = Pinned",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        color = SkyCyan.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Z${"%.1f".format(zoom)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SkyCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                US_REGION_PRESETS.forEach { preset ->
                    val selected = activeRegionId == preset.id
                    Surface(
                        color = if (selected) SkyCyan.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                if (selected) SkyCyan else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                centerLat = preset.lat
                                centerLon = preset.lon
                                zoom = preset.zoom
                                activeRegionId = preset.id
                            }
                            .testTag("map_region_${preset.id}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) SkyCyan else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Floating Zoom & Reset Controls (Right Side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MapControlIconButton(
                icon = Icons.Default.Add,
                contentDescription = "Zoom in map",
                testTag = "map_zoom_in",
                onClick = { zoom = (zoom + 0.85f).coerceIn(minZoom, maxZoom) }
            )
            MapControlIconButton(
                icon = Icons.Default.Remove,
                contentDescription = "Zoom out map",
                testTag = "map_zoom_out",
                onClick = { zoom = (zoom - 0.85f).coerceIn(minZoom, maxZoom) }
            )
            MapControlIconButton(
                icon = Icons.Default.MyLocation,
                contentDescription = "Reset map to CONUS",
                testTag = "map_reset_conus",
                tint = SkyCyan,
                onClick = {
                    centerLat = 38.6
                    centerLon = -96.8
                    zoom = 3.8f
                    activeRegionId = "conus"
                }
            )
        }

        // Bottom Docked Attribution + Pin Preview Card
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "© OpenStreetMap • © CARTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (mapPin == null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Drop a Pin Anywhere in the USA",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap a storm target or NWS site to preview & pull NOAA RRFS 3km soundings.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                val st = uiState.activeStation
                                centerLat = st.lat
                                centerLon = st.lon
                                zoom = zoom.coerceAtLeast(5.5f)
                                onSelectMapStation(st)
                            },
                            modifier = Modifier.testTag("map_focus_active_station")
                        ) {
                            Text(uiState.activeStation.id.take(6), maxLines = 1)
                        }
                    }
                }
            } else {
                AnimatedVisibility(visible = true) {
                    MapPinPreviewCard(
                        pin = mapPin,
                        isPinnedForCache = pinnedIds.contains(mapPin.station.id),
                        onLoadRrfs = onLoadSelectedMapPointRrfs,
                        onTogglePin = onTogglePinSelectedMapPoint,
                        onClear = onClearMapPin
                    )
                }
            }
        }
    }
}

@Composable
private fun MapControlIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .size(48.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MapPinPreviewCard(
    pin: MapPinSelection,
    isPinnedForCache: Boolean,
    onLoadRrfs: () -> Unit,
    onTogglePin: () -> Unit,
    onClear: () -> Unit
) {
    val ns = if (pin.lat >= 0.0) "N" else "S"
    val ew = if (pin.lon >= 0.0) "E" else "W"
    val coordText = String.format(Locale.US, "%.2f°%s, %.2f°%s", abs(pin.lat), ns, abs(pin.lon), ew)
    val distMi = (pin.distanceToRaobKm * 0.621371).roundToInt()
    val distKm = pin.distanceToRaobKm.roundToInt()

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SkyCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .testTag("map_pin_preview_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SkyCyan.copy(alpha = 0.16f))
                            .border(1.dp, SkyCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (pin.isNwsRaobSite) pin.station.id else "RRFS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SkyCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${pin.station.name}, ${pin.station.state}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (pin.isResolvingName) {
                                Spacer(modifier = Modifier.width(6.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = SkyCyan
                                )
                            }
                        }
                        Text(
                            text = if (pin.isNwsRaobSite) {
                                "$coordText • NWS RAOB (${pin.station.icao}) • ${pin.station.elevationM}m"
                            } else {
                                "$coordText • Nearest RAOB: ${pin.nearestRaob.icao} (${distKm}km / ${distMi}mi)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("map_clear_pin_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear map pin",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onLoadRrfs,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("map_load_rrfs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Load RRFS 3km Sounding",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("map_pin_cache_button")
                ) {
                    Icon(
                        imageVector = if (isPinnedForCache) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinnedForCache) "Unpin from daily cache" else "Pin to daily cache",
                        tint = if (isPinnedForCache) ThermalAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPinnedForCache) "Pinned" else "Pin",
                        color = if (isPinnedForCache) ThermalAmber else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
