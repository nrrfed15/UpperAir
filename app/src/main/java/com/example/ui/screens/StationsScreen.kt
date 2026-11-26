package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.domain.model.NwsStationsCatalog
import com.example.domain.model.UpperAirStation
import com.example.ui.UpperAirUiState
import com.example.ui.theme.DewpointEmerald
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.ThermalAmber
import com.example.ui.util.TimeFormatters

@Composable
fun StationsScreen(
    uiState: UpperAirUiState,
    onSelectStation: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRegionFilterChange: (String) -> Unit,
    onSyncAllPinned: () -> Unit,
    onSubmitCustomSiteQuery: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val allKnownStations = remember(uiState.settings.customStations) {
        (uiState.settings.customStations.values + NwsStationsCatalog.stations).distinctBy { it.id }
    }
    val filteredStations = remember(
        uiState.stationSearchQuery,
        uiState.selectedRegionFilter,
        uiState.settings.pinnedStationIds,
        allKnownStations
    ) {
        val q = uiState.stationSearchQuery.trim()
        allKnownStations
            .filter { st ->
                val matchesRegion = uiState.selectedRegionFilter == "All" || st.region == uiState.selectedRegionFilter
                val matchesQuery = q.isEmpty() ||
                    st.id.contains(q, ignoreCase = true) ||
                    st.icao.contains(q, ignoreCase = true) ||
                    st.name.contains(q, ignoreCase = true) ||
                    st.state.contains(q, ignoreCase = true)
                matchesRegion && matchesQuery
            }
            .sortedByDescending { uiState.settings.pinnedStationIds.contains(it.id) }
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "NWS Upper-Air Network",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${uiState.settings.pinnedStationIds.size} pinned for auto-download",
                            style = MaterialTheme.typography.labelSmall,
                            color = DewpointEmerald
                        )
                    }
                    Button(
                        onClick = onSyncAllPinned,
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.testTag("sync_pinned_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Pinned")
                    }
                }
            }

            item {
                val trimmed = uiState.stationSearchQuery.trim()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.stationSearchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Station ID (OUN, KVEF) or city (Austin, TX)") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (trimmed.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onSearchQueryChange("") },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                    IconButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            onSubmitCustomSiteQuery(trimmed)
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("station_search_submit")
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Query site",
                                            tint = SkyCyan
                                        )
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (trimmed.isNotEmpty()) {
                                    focusManager.clearFocus()
                                    onSubmitCustomSiteQuery(trimmed)
                                }
                            }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("station_search_input")
                    )

                    if (trimmed.isNotEmpty()) {
                        Surface(
                            color = DewpointEmerald.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    focusManager.clearFocus()
                                    onSubmitCustomSiteQuery(trimmed)
                                }
                                .testTag("station_query_location_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = DewpointEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Query sounding data for \"$trimmed\"",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = DewpointEmerald
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (region in NwsStationsCatalog.regions) {
                        FilterChip(
                            selected = uiState.selectedRegionFilter == region,
                            onClick = { onRegionFilterChange(region) },
                            label = { Text(region) }
                        )
                    }
                }
            }

            items(filteredStations, key = { it.id }) { station ->
                StationDirectoryRow(
                    station = station,
                    isActive = station.id == uiState.activeStation.id,
                    isPinned = uiState.settings.pinnedStationIds.contains(station.id),
                    lastSyncMs = uiState.stationLastSyncMap[station.id] ?: 0L,
                    onSelect = { onSelectStation(station.id) },
                    onTogglePin = { onTogglePin(station.id) }
                )
            }
        }
    }
}

@Composable
private fun StationDirectoryRow(
    station: UpperAirStation,
    isActive: Boolean,
    isPinned: Boolean,
    lastSyncMs: Long,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit
) {
    val borderColor = if (isActive) SkyCyan else MaterialTheme.colorScheme.outlineVariant

    Surface(
        color = if (isActive) SkyCyan.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .testTag("station_row_${station.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = station.id,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isActive) SkyCyan else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${station.name}, ${station.state}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active station",
                                tint = SkyCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "${station.icao} • WMO ${station.wmo} • ${station.region} • ${station.elevationM}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lastSyncMs > 0L) {
                        Text(
                            text = "Cached ${TimeFormatters.formatRelativeAge(lastSyncMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DewpointEmerald
                        )
                    }
                }
            }

            IconButton(
                onClick = onTogglePin,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("pin_station_${station.id}")
            ) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isPinned) "Unpin station from auto-sync" else "Pin station for auto-sync",
                    tint = if (isPinned) ThermalAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
