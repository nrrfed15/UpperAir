package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.SoundingProfile
import com.example.ui.SoundingDataMode
import com.example.ui.UpperAirUiState
import com.example.ui.components.OutlookTrendChart
import com.example.ui.theme.DewpointEmerald
import com.example.ui.theme.InstabilityCoral
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.ThermalAmber
import com.example.ui.util.TimeFormatters
import kotlin.math.roundToInt

private data class DailyOutlookBucket(
    val dayNumber: Int,
    val label: String,
    val peakCapeProfile: SoundingProfile,
    val peakCapeIndex: Int,
    val maxShearKt: Int,
    val maxPwatMm: Double,
    val minFreezingM: Int,
    val maxFreezingM: Int
)

@Composable
fun OutlookScreen(
    uiState: UpperAirUiState,
    onSelectForecastIndex: (Int) -> Unit,
    onJumpToSoundingHour: (SoundingDataMode, Int) -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedModel by remember {
        mutableStateOf(
            if (uiState.dataMode == SoundingDataMode.FORECAST_RRFS) {
                SoundingDataMode.FORECAST_RRFS
            } else {
                SoundingDataMode.FORECAST_GFS
            }
        )
    }
    val isRrfs = selectedModel == SoundingDataMode.FORECAST_RRFS
    val forecasts = if (isRrfs) uiState.rrfsSoundings else uiState.forecastSoundings
    val safeSelectedIndex = if (forecasts.isEmpty()) 0 else uiState.selectedForecastIndex.coerceIn(0, forecasts.lastIndex)

    val dailyBuckets = remember(forecasts) {
        forecasts.withIndex()
            .groupBy { it.value.forecastHourOffset / 24 }
            .entries
            .sortedBy { it.key }
            .take(8)
            .map { (day, entries) ->
                val peakEntry = entries.maxByOrNull { it.value.indices.sbcapeJkg } ?: entries.first()
                DailyOutlookBucket(
                    dayNumber = day,
                    label = if (day == 0) "Today (+0–24h)" else "Day +$day (+${day * 24}–${(day + 1) * 24}h)",
                    peakCapeProfile = peakEntry.value,
                    peakCapeIndex = peakEntry.index,
                    maxShearKt = entries.maxOf { it.value.indices.shear06Kt },
                    maxPwatMm = entries.maxOf { it.value.indices.pwatMm },
                    minFreezingM = entries.minOf { it.value.indices.freezingLevelM },
                    maxFreezingM = entries.maxOf { it.value.indices.freezingLevelM }
                )
            }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRrfs) "RRFS 3km High-Res Outlook" else "7-Day Upper-Air Outlook",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val maxHr = forecasts.lastOrNull()?.forecastHourOffset ?: if (isRrfs) 84 else 168
                        Text(
                            text = if (isRrfs) {
                                "${uiState.activeStation.icao} • NOAA RRFS 3km (+0h to +${maxHr}h)"
                            } else {
                                "${uiState.activeStation.icao} • NOAA GFS 0.25° (+0h to +${maxHr}h)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SkyCyan
                        )
                    }
                    IconButton(onClick = onSyncNow, enabled = !uiState.isSyncing) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh model forecast",
                            tint = SkyCyan
                        )
                    }
                }
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isRrfs,
                        onClick = {
                            selectedModel = SoundingDataMode.FORECAST_GFS
                            onSelectForecastIndex(0)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("outlook_model_gfs")
                    ) {
                        Text("GFS 7-Day (+168h)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SegmentedButton(
                        selected = isRrfs,
                        onClick = {
                            selectedModel = SoundingDataMode.FORECAST_RRFS
                            onSelectForecastIndex(0)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("outlook_model_rrfs")
                    ) {
                        Text("RRFS 3km (High-Res)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            item {
                OutlookTrendChart(
                    forecasts = forecasts,
                    selectedIndex = safeSelectedIndex,
                    onSelectIndex = onSelectForecastIndex,
                    title = if (isRrfs) "RRFS 3km Instability & Shear Trend" else "7-Day Instability & Shear Trend",
                    emptyMessage = if (isRrfs) "Sync station to download NOAA RRFS 3km trend" else "Sync station to view 7-day model trend"
                )
            }

            if (forecasts.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (isRrfs) {
                                    "No NOAA RRFS 3km forecast cached for ${uiState.activeStation.icao}"
                                } else {
                                    "No 7-day GFS forecast cached for ${uiState.activeStation.icao}"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Button(onClick = onSyncNow, enabled = !uiState.isSyncing) {
                                Text("Download Soundings & Forecasts")
                            }
                        }
                    }
                }
            } else {
                val selected = forecasts[safeSelectedIndex]
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onJumpToSoundingHour(selectedModel, safeSelectedIndex) }
                            .testTag("jump_selected_hour_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${if (isRrfs) "RRFS" else "GFS"} +${selected.forecastHourOffset}h (${TimeFormatters.formatUtcShort(selected.validEpochMs)})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "CAPE ${selected.indices.sbcapeJkg} J/kg • Shear ${selected.indices.shear06Kt} kt • Frz ${selected.indices.freezingLevelM}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open Skew-T for this hour",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                items(dailyBuckets, key = { "${selectedModel.name}_${it.dayNumber}" }) { bucket ->
                    DailyOutlookCard(
                        bucket = bucket,
                        useMph = uiState.settings.useMph,
                        onOpenPeakSounding = { onJumpToSoundingHour(selectedModel, bucket.peakCapeIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyOutlookCard(
    bucket: DailyOutlookBucket,
    useMph: Boolean,
    onOpenPeakSounding: () -> Unit
) {
    val peak = bucket.peakCapeProfile
    val peakShearStr = if (useMph) "${(bucket.maxShearKt * 1.15078).roundToInt()} mph" else "${bucket.maxShearKt} kt"

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenPeakSounding)
            .testTag("outlook_day_${bucket.dayNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bucket.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Inspect +${peak.forecastHourOffset}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyCyan
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = SkyCyan,
                        modifier = Modifier.size(16.dp).padding(start = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricPill(
                    label = "Peak CAPE",
                    value = "${peak.indices.sbcapeJkg} J/kg",
                    color = InstabilityCoral
                )
                MetricPill(
                    label = "Max 0-6 Shear",
                    value = peakShearStr,
                    color = SkyCyan
                )
                MetricPill(
                    label = "Peak PWAT",
                    value = "${bucket.maxPwatMm} mm",
                    color = DewpointEmerald
                )
                MetricPill(
                    label = "0°C Height",
                    value = "${bucket.minFreezingM}–${bucket.maxFreezingM}m",
                    color = ThermalAmber
                )
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
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
