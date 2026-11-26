package com.example.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

object TimeFormatters {
    fun formatUtcShort(epochMs: Long): String {
        if (epochMs <= 0L) return "Never"
        val sdf = SimpleDateFormat("MMM d HH:mm 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(epochMs))
    }

    fun formatUtcCycleChip(epochMs: Long): String {
        if (epochMs <= 0L) return "--"
        val sdf = SimpleDateFormat("MMM d HH'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(epochMs))
    }

    fun formatLocalShort(epochMs: Long): String {
        if (epochMs <= 0L) return "Never"
        val sdf = SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatRelativeAge(epochMs: Long): String {
        if (epochMs <= 0L) return "Not synced"
        val diffMin = ((System.currentTimeMillis() - epochMs) / 60_000L).coerceAtLeast(0L)
        return when {
            diffMin < 1 -> "Just now"
            diffMin < 60 -> "${diffMin}m ago"
            diffMin < 1440 -> "${diffMin / 60}h ago"
            else -> "${diffMin / 1440}d ago"
        }
    }

    fun formatTemp(tempC: Double, useFahrenheit: Boolean): String {
        val v = if (useFahrenheit) tempC * 9.0 / 5.0 + 32.0 else tempC
        val u = if (useFahrenheit) "°F" else "°C"
        return "${"%.1f".format(v)}$u"
    }

    fun formatWind(speedKt: Double?, useMph: Boolean): String {
        if (speedKt == null) return "--"
        val v = if (useMph) (speedKt * 1.15078).roundToInt() else speedKt.roundToInt()
        val u = if (useMph) "mph" else "kt"
        return "$v $u"
    }
}
