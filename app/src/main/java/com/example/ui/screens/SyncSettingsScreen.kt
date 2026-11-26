package com.example.ui.screens

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.SyncLogEntry
import com.example.ui.UpperAirUiState
import com.example.ui.theme.DewpointEmerald
import com.example.ui.theme.InstabilityCoral
import com.example.ui.theme.SkyCyan
import com.example.ui.util.TimeFormatters

@Composable
fun SyncSettingsScreen(
    uiState: UpperAirUiState,
    onSetDailyOfflineCache: (Boolean) -> Unit,
    onSetAutoSync: (Boolean) -> Unit,
    onSetIntervalHours: (Int) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onRunBackgroundWorkerNow: () -> Unit,
    onSetUseFahrenheit: (Boolean) -> Unit,
    onSetUseMph: (Boolean) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Offline Cache, Sync & Units",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // WorkManager Daily Offline Cache & Auto-Download Settings Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Offline storage summary pill
                        Surface(
                            color = DewpointEmerald.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = DewpointEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${uiState.totalCachedProfiles} sounding profiles stored offline",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Across ${uiState.cachedStationsCount} stations (RAOB, 7-Day GFS & RRFS 3km)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Daily Offline Cache (At Least Once/Day)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Guarantees pinned & active stations refresh local Room storage at least every 24h for offline viewing.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.dailyOfflineCacheEnabled,
                                onCheckedChange = onSetDailyOfflineCache,
                                modifier = Modifier.testTag("daily_offline_cache_switch")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Synoptic Auto-Refresh",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Downloads latest NWS RAOB soundings, 7-day GFS & RRFS 3km for ${settings.pinnedStationIds.size} pinned stations.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autoSyncEnabled,
                                onCheckedChange = onSetAutoSync,
                                modifier = Modifier.testTag("auto_sync_switch")
                            )
                        }

                        Text(
                            text = "Synoptic Refresh Cadence",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(6, 12, 24).forEach { hrs ->
                                FilterChip(
                                    selected = settings.syncIntervalHours == hrs,
                                    onClick = { onSetIntervalHours(hrs) },
                                    label = {
                                        Text(if (hrs == 24) "24h (Daily)" else "Every ${hrs}h")
                                    },
                                    enabled = settings.autoSyncEnabled,
                                    modifier = Modifier.testTag("interval_${hrs}h_chip")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Wi-Fi / Unmetered Only (Auto-Refresh)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Defer frequent synoptic updates until Wi-Fi is connected.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.wifiOnly,
                                onCheckedChange = onSetWifiOnly,
                                enabled = settings.autoSyncEnabled,
                                modifier = Modifier.testTag("wifi_only_switch")
                            )
                        }

                        Button(
                            onClick = onRunBackgroundWorkerNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trigger_worker_button")
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cache Pinned Sites Offline Now")
                        }
                    }
                }
            }

            // Units & Theme Preferences Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Display & Units",
                            style = MaterialTheme.typography.titleMedium
                        )
                        PreferenceToggleRow(
                            title = "Temperature in Fahrenheit (°F)",
                            subtitle = "Standard Skew-T plots use °C; toggle for °F readouts",
                            checked = settings.useFahrenheit,
                            onCheckedChange = onSetUseFahrenheit,
                            tag = "unit_fahrenheit_switch"
                        )
                        PreferenceToggleRow(
                            title = "Wind Speed in mph",
                            subtitle = "Toggle between meteorological knots (kt) and mph",
                            checked = settings.useMph,
                            onCheckedChange = onSetUseMph,
                            tag = "unit_mph_switch"
                        )
                        PreferenceToggleRow(
                            title = "Observatory Dark Theme",
                            subtitle = "High-contrast dark cockpit telemetry palette",
                            checked = settings.darkTheme,
                            onCheckedChange = onSetDarkTheme,
                            tag = "dark_theme_switch"
                        )
                    }
                }
            }

            // Download & Sync History Log
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Download History Log",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (settings.lastSyncEpochMs > 0L) {
                            "Last: ${TimeFormatters.formatLocalShort(settings.lastSyncEpochMs)}"
                        } else "No sync yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyCyan
                    )
                }
            }

            if (uiState.syncLogs.isEmpty()) {
                item {
                    Text(
                        text = "No download events recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.syncLogs, key = { it.id }) { log ->
                    SyncLogRow(log)
                }
            }
        }
    }
}

@Composable
private fun PreferenceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun SyncLogRow(log: SyncLogEntry) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (log.success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = if (log.success) "Success" else "Failed",
                tint = if (log.success) DewpointEmerald else InstabilityCoral,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${log.stationId} • ${log.sourceType}",
                        style = MaterialTheme.typography.labelMedium,
                        color = SkyCyan
                    )
                    Text(
                        text = TimeFormatters.formatRelativeAge(log.timestampMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
