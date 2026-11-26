package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Pure Kotlin Web Mercator (EPSG:3857) projection utilities for slippy-tile maps and vector overlays.
 */
object WebMercator {
    const val TILE_SIZE = 256.0
    const val MIN_LAT = -85.05112878
    const val MAX_LAT = 85.05112878

    fun lonToNormalizedX(lon: Double): Double = (lon + 180.0) / 360.0

    fun latToNormalizedY(lat: Double): Double {
        val clamped = lat.coerceIn(MIN_LAT, MAX_LAT)
        val rad = Math.toRadians(clamped)
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
    }

    fun normalizedXToLon(nx: Double): Double = (nx * 360.0 - 180.0).coerceIn(-180.0, 180.0)

    fun normalizedYToLat(ny: Double): Double {
        val n = PI * (1.0 - 2.0 * ny.coerceIn(0.0, 1.0))
        return Math.toDegrees(atan(sinh(n)))
    }

    fun worldSizePx(zoom: Float): Double = TILE_SIZE * 2.0.pow(zoom.toDouble())

    fun latLonToScreen(
        lat: Double,
        lon: Double,
        centerLat: Double,
        centerLon: Double,
        zoom: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Offset {
        val world = worldSizePx(zoom)
        val cx = lonToNormalizedX(centerLon) * world
        val cy = latToNormalizedY(centerLat) * world
        val px = lonToNormalizedX(lon) * world
        val py = latToNormalizedY(lat) * world
        return Offset(
            x = (viewportWidthPx * 0.5f) + (px - cx).toFloat(),
            y = (viewportHeightPx * 0.5f) + (py - cy).toFloat()
        )
    }

    fun screenToLatLon(
        screenX: Float,
        screenY: Float,
        centerLat: Double,
        centerLon: Double,
        zoom: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Pair<Double, Double> {
        val world = worldSizePx(zoom)
        val cx = lonToNormalizedX(centerLon) * world
        val cy = latToNormalizedY(centerLat) * world
        val worldX = cx + (screenX - viewportWidthPx * 0.5f)
        val worldY = cy + (screenY - viewportHeightPx * 0.5f)
        val lon = normalizedXToLon(worldX / world)
        val lat = normalizedYToLat(worldY / world)
        return lat to lon
    }
}

/**
 * Bundled lightweight offline vector outlines for CONUS, Alaska, Hawaii, Puerto Rico,
 * and key regional US corridors so the interactive sounding map renders crisply even without network.
 */
object UsaVectorOverlay {

    // Simplified CONUS coastline & international border (lat, lon)
    val conusOutline: List<Pair<Double, Double>> = listOf(
        48.99 to -124.73, 48.38 to -124.67, 46.25 to -124.06, 44.63 to -124.07,
        42.83 to -124.56, 41.75 to -124.20, 40.44 to -124.41, 38.95 to -123.73,
        37.78 to -122.51, 36.60 to -121.95, 34.45 to -120.47, 33.90 to -118.42,
        32.53 to -117.12, 32.72 to -114.72, 31.33 to -111.07, 31.33 to -108.21,
        31.78 to -106.52, 30.65 to -105.00, 29.01 to -103.30, 29.76 to -101.40,
        28.70 to -100.50, 27.50 to -99.51, 26.07 to -98.23, 25.96 to -97.14,
        27.80 to -97.08, 28.95 to -95.30, 29.70 to -93.84, 29.55 to -91.20,
        29.00 to -89.25, 30.25 to -88.80, 30.38 to -86.50, 29.68 to -84.90,
        29.95 to -83.85, 28.00 to -82.80, 26.15 to -81.80, 25.15 to -80.90,
        25.75 to -80.13, 27.95 to -80.50, 30.40 to -81.40, 32.08 to -80.85,
        33.85 to -78.55, 35.22 to -75.53, 36.92 to -76.00, 38.78 to -75.08,
        40.48 to -74.00, 41.05 to -71.95, 41.68 to -69.95, 42.88 to -70.82,
        44.35 to -68.20, 44.81 to -66.95, 47.35 to -67.79, 47.46 to -69.22,
        45.01 to -71.50, 45.01 to -74.76, 44.10 to -76.35, 43.25 to -79.05,
        42.88 to -78.90, 41.90 to -82.50, 42.33 to -83.05, 45.80 to -82.50,
        46.50 to -84.35, 48.00 to -89.50, 48.99 to -95.15, 48.99 to -104.05,
        48.99 to -114.05, 48.99 to -122.76
    )

    // Key internal regional state-grid reference corridors for visual orientation offline
    val internalCorridors: List<List<Pair<Double, Double>>> = listOf(
        // Western Mountain / Plains divide (MT-WY-CO-NM eastern boundary ~104W / 102W)
        listOf(49.0 to -104.05, 45.0 to -104.05, 41.0 to -104.05, 41.0 to -102.05, 37.0 to -102.05, 36.5 to -103.0, 32.0 to -103.05),
        // Central Plains / Tornado Alley latitude lines (41N, 37N, 34N)
        listOf(45.0 to -111.0, 45.0 to -96.5),
        listOf(41.0 to -114.0, 41.0 to -95.8),
        listOf(37.0 to -114.05, 37.0 to -89.2),
        // Texas / Oklahoma Panhandle & Red River
        listOf(36.5 to -103.0, 36.5 to -100.0, 34.55 to -100.0, 33.85 to -98.0, 33.65 to -94.04, 30.0 to -93.8),
        // Mississippi River corridor (MN to LA)
        listOf(47.2 to -95.2, 44.9 to -93.0, 41.5 to -90.6, 38.6 to -90.2, 37.0 to -89.2, 35.1 to -90.1, 32.3 to -90.9, 29.9 to -90.1),
        // Great Lakes & Ohio Valley to Mid-Atlantic
        listOf(42.0 to -124.2, 42.0 to -111.05),
        listOf(42.0 to -120.0, 39.0 to -120.0, 35.0 to -114.6),
        listOf(37.0 to -89.2, 38.0 to -84.5, 39.7 to -80.5, 39.7 to -75.5),
        listOf(35.0 to -90.1, 35.0 to -84.3, 36.55 to -81.7, 36.55 to -75.85),
        // Southeast Gulf states north borders
        listOf(35.0 to -88.2, 31.0 to -88.4),
        listOf(35.0 to -85.6, 31.0 to -85.0, 30.7 to -81.5)
    )

    // Alaska main landmass outline
    val alaskaOutline: List<Pair<Double, Double>> = listOf(
        71.35 to -156.78, 70.20 to -148.40, 69.65 to -141.00, 60.30 to -141.00,
        59.50 to -139.70, 56.50 to -132.50, 55.30 to -131.60, 58.30 to -136.50,
        60.50 to -146.00, 60.00 to -151.70, 58.80 to -153.50, 55.20 to -162.70,
        58.70 to -158.60, 59.90 to -164.00, 63.00 to -164.50, 64.50 to -165.40,
        65.60 to -168.00, 66.80 to -162.60, 68.30 to -166.70, 70.60 to -160.00
    )

    // Hawaii main islands (simplified)
    val hawaiiIslands: List<List<Pair<Double, Double>>> = listOf(
        listOf(20.26 to -155.85, 19.72 to -155.05, 18.92 to -155.68, 19.45 to -155.98),
        listOf(21.03 to -156.60, 20.78 to -156.00, 20.60 to -156.45),
        listOf(21.70 to -157.95, 21.30 to -157.68, 21.30 to -158.12, 21.58 to -158.28),
        listOf(22.22 to -159.45, 21.95 to -159.32, 21.88 to -159.65, 22.12 to -159.76)
    )

    // Puerto Rico outline
    val puertoRicoOutline: List<Pair<Double, Double>> = listOf(
        18.50 to -67.15, 18.46 to -65.62, 17.96 to -65.85, 17.94 to -67.18
    )

    fun DrawScope.drawOfflineUsaBase(
        centerLat: Double,
        centerLon: Double,
        zoom: Float,
        darkTheme: Boolean,
        textMeasurer: TextMeasurer
    ) {
        val landFill = if (darkTheme) Color(0xFF101D31) else Color(0xFFE3ECF6)
        val coastStroke = if (darkTheme) Color(0xFF33527A) else Color(0xFF8AA5C7)
        val corridorStroke = if (darkTheme) Color(0xFF213754) else Color(0xFFB5C7DD)
        val gridColor = if (darkTheme) Color(0xFF1A2D46) else Color(0xFFC9D8EA)
        val labelColor = if (darkTheme) Color(0xFF637E9F) else Color(0xFF5D738E)

        // 1. Lat/Lon Graticule
        val stepDeg = when {
            zoom >= 7.5f -> 2
            zoom >= 5.5f -> 5
            else -> 10
        }
        val labelStyle = TextStyle(color = labelColor, fontSize = 9.sp)

        var lon = -170
        while (lon <= -60) {
            val ptTop = WebMercator.latLonToScreen(74.0, lon.toDouble(), centerLat, centerLon, zoom, size.width, size.height)
            val ptBot = WebMercator.latLonToScreen(15.0, lon.toDouble(), centerLat, centerLon, zoom, size.width, size.height)
            if (ptTop.x in -40f..(size.width + 40f)) {
                drawLine(
                    color = gridColor,
                    start = Offset(ptTop.x, 0f),
                    end = Offset(ptBot.x, size.height),
                    strokeWidth = 0.8f
                )
                if (ptTop.x in 24f..(size.width - 36f)) {
                    val txt = textMeasurer.measure("${kotlin.math.abs(lon)}°W", labelStyle)
                    drawText(txt, topLeft = Offset(ptTop.x + 3.dp.toPx(), 6.dp.toPx()))
                }
            }
            lon += stepDeg
        }

        var lat = 20
        while (lat <= 72) {
            val ptLeft = WebMercator.latLonToScreen(lat.toDouble(), -170.0, centerLat, centerLon, zoom, size.width, size.height)
            if (ptLeft.y in -40f..(size.height + 40f)) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, ptLeft.y),
                    end = Offset(size.width, ptLeft.y),
                    strokeWidth = 0.8f
                )
                if (ptLeft.y in 20f..(size.height - 20f)) {
                    val txt = textMeasurer.measure("${lat}°N", labelStyle)
                    drawText(txt, topLeft = Offset(6.dp.toPx(), ptLeft.y - txt.size.height - 2.dp.toPx()))
                }
            }
            lat += stepDeg
        }

        // 2. Filled Polygons (CONUS, Alaska, Hawaii, Puerto Rico)
        val polygons = listOf(conusOutline, alaskaOutline, puertoRicoOutline) + hawaiiIslands
        for (poly in polygons) {
            val path = Path()
            poly.forEachIndexed { idx, (pLat, pLon) ->
                val pt = WebMercator.latLonToScreen(pLat, pLon, centerLat, centerLon, zoom, size.width, size.height)
                if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            path.close()
            drawPath(path, color = landFill)
            drawPath(
                path,
                color = coastStroke,
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // 3. Internal Regional Corridors
        for (corridor in internalCorridors) {
            val path = Path()
            corridor.forEachIndexed { idx, (pLat, pLon) ->
                val pt = WebMercator.latLonToScreen(pLat, pLon, centerLat, centerLon, zoom, size.width, size.height)
                if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            drawPath(
                path,
                color = corridorStroke,
                style = Stroke(width = 0.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
