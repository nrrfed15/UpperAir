package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.SoundingLevel
import com.example.domain.model.SoundingProfile
import com.example.domain.model.UpperAirStation
import com.example.domain.physics.ParcelType
import com.example.ui.ChartViewType
import com.example.ui.SoundingDataMode
import com.example.ui.UpperAirUiState
import com.example.ui.components.FullscreenSkewTDialog
import com.example.ui.components.HodographChart
import com.example.ui.components.SharppyLevelsTable
import com.example.ui.components.SharppyTelemetryPanel
import com.example.ui.components.SiteSearchBar
import com.example.ui.components.SkewTChart
import com.example.ui.theme.DewpointEmerald
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.ThermalAmber
import com.example.ui.util.TimeFormatters
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundingScreen(
    uiState: UpperAirUiState,
    onOpenStationPicker: () -> Unit,
    onSyncNow: () -> Unit,
    onSelectMode: (SoundingDataMode) -> Unit,
    onSelectChartType: (ChartViewType) -> Unit,
    onSelectForecastIndex: (Int) -> Unit,
    onSelectPastIndex: (Int) -> Unit,
    onFetchPastSevenDays: () -> Unit,
    onFetchHistoricalSounding: (Long, Int) -> Unit,
    onDismissBanner: () -> Unit,
    onSiteQueryChange: (String) -> Unit,
    onSelectResolvedSite: (UpperAirStation) -> Unit,
    onSubmitSiteQuery: (String) -> Unit,
    onSelectParcelType: (ParcelType) -> Unit = {},
    onToggleMiniHodograph: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeProfile = uiState.activeProfile
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showFullscreenSkewT by remember { mutableStateOf(false) }
    var showHistoricalDatePicker by remember { mutableStateOf(false) }
    var selectedSynopticHour by remember { mutableIntStateOf(12) }
    var inspectedLevel by remember(activeProfile) { mutableStateOf<SoundingLevel?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Compact SPC/SHARPpy Top Command Bar (Station + Collapsible Search + Sync)
            item {
                CompactStationCommandBar(
                    uiState = uiState,
                    isSearchExpanded = isSearchExpanded,
                    onOpenStationPicker = onOpenStationPicker,
                    onToggleSearch = {
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded && uiState.siteQueryInput.isNotEmpty()) {
                            onSiteQueryChange("")
                        }
                    },
                    onSyncNow = onSyncNow
                )
            }

            // 1B. Collapsible Site & Location Search Bar (expands only when requested)
            item {
                AnimatedVisibility(visible = isSearchExpanded) {
                    SiteSearchBar(
                        query = uiState.siteQueryInput,
                        suggestions = uiState.siteSuggestions,
                        isResolving = uiState.isResolvingSite,
                        onQueryChange = onSiteQueryChange,
                        onSelectStation = { station ->
                            isSearchExpanded = false
                            onSelectResolvedSite(station)
                        },
                        onSubmitQuery = { query ->
                            isSearchExpanded = false
                            onSubmitSiteQuery(query)
                        }
                    )
                }
            }

            // Optional Compact Status Chip
            if (uiState.statusBanner != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismissBanner() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.statusBanner,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DISMISS",
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyCyan
                            )
                        }
                    }
                }
            }

            // 2. Slim High-Density Data Mode Strip
            item {
                CompactModeStrip(
                    selectedMode = uiState.dataMode,
                    onSelectMode = onSelectMode
                )
            }

            // 3A. Past Observed Archive Bar
            item {
                AnimatedVisibility(visible = uiState.dataMode == SoundingDataMode.PAST_OBSERVED) {
                    PastSoundingsCard(
                        stationRaobCode = uiState.activeStation.effectiveRaobIcao,
                        pastSoundings = uiState.pastObservedSoundings,
                        selectedIndex = uiState.selectedPastIndex,
                        selectedCycleHour = selectedSynopticHour,
                        isFetchingPast = uiState.isFetchingPast,
                        onSelectIndex = onSelectPastIndex,
                        onSelectCycleHour = { selectedSynopticHour = it },
                        onOpenDatePicker = { showHistoricalDatePicker = true },
                        onFetchPastSevenDays = onFetchPastSevenDays
                    )
                }
            }

            // 3B. Forecast Timeline Scrubber (GFS / RRFS)
            item {
                val showTimeline = uiState.dataMode == SoundingDataMode.FORECAST_GFS ||
                    uiState.dataMode == SoundingDataMode.FORECAST_RRFS
                AnimatedVisibility(visible = showTimeline) {
                    ForecastTimelineCard(
                        forecasts = uiState.activeModelForecasts,
                        isRrfs = uiState.dataMode == SoundingDataMode.FORECAST_RRFS,
                        selectedIndex = uiState.selectedForecastIndex,
                        onSelectIndex = onSelectForecastIndex
                    )
                }
            }

            // 4. Sounding Workspace (Integrated Chart Header + Chart + SHARPpy Matrices)
            if (activeProfile == null) {
                item {
                    EmptySoundingCard(
                        isSyncing = uiState.isSyncing,
                        stationIcao = uiState.activeStation.icao,
                        onSyncNow = onSyncNow
                    )
                }
            } else {
                item {
                    IntegratedChartHeader(
                        profile = activeProfile,
                        selectedChart = uiState.chartViewType,
                        onSelectChartType = onSelectChartType
                    )
                }

                item {
                    when (uiState.chartViewType) {
                        ChartViewType.SKEW_T -> SkewTChart(
                            profile = activeProfile,
                            useFahrenheit = uiState.settings.useFahrenheit,
                            useMph = uiState.settings.useMph,
                            inspectedLevel = inspectedLevel,
                            onInspectLevel = { inspectedLevel = it },
                            onOpenFullscreen = { showFullscreenSkewT = true },
                            parcelType = uiState.selectedParcelType,
                            showMiniHodograph = uiState.showMiniHodograph,
                            onToggleMiniHodograph = onToggleMiniHodograph
                        )
                        ChartViewType.HODOGRAPH -> HodographChart(
                            profile = activeProfile,
                            useMph = uiState.settings.useMph
                        )
                        ChartViewType.LEVELS_TABLE -> SharppyLevelsTable(
                            levels = activeProfile.levels,
                            useFahrenheit = uiState.settings.useFahrenheit,
                            useMph = uiState.settings.useMph
                        )
                    }
                }

                item {
                    SharppyTelemetryPanel(
                        profile = activeProfile,
                        useFahrenheit = uiState.settings.useFahrenheit,
                        useMph = uiState.settings.useMph,
                        selectedParcelType = uiState.selectedParcelType,
                        onSelectParcelType = onSelectParcelType
                    )
                }
            }
        }

        // Fullscreen Skew-T Inspector Modal
        if (showFullscreenSkewT && activeProfile != null) {
            val st = uiState.activeStation
            FullscreenSkewTDialog(
                stationLabel = "${st.effectiveRaobIcao} • ${st.name}",
                profile = activeProfile,
                useFahrenheit = uiState.settings.useFahrenheit,
                useMph = uiState.settings.useMph,
                inspectedLevel = inspectedLevel,
                onInspectLevel = { inspectedLevel = it },
                onDismiss = { showFullscreenSkewT = false },
                parcelType = uiState.selectedParcelType,
                onSelectParcelType = onSelectParcelType,
                showMiniHodograph = uiState.showMiniHodograph,
                onToggleMiniHodograph = onToggleMiniHodograph
            )
        }

        // Historical RAOB Date Picker Dialog
        if (showHistoricalDatePicker) {
            val todayEndMs = System.currentTimeMillis()
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = todayEndMs - 24L * 3600L * 1000L,
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        return utcTimeMillis <= todayEndMs
                    }
                }
            )
            DatePickerDialog(
                onDismissRequest = { showHistoricalDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val chosenMs = datePickerState.selectedDateMillis
                            showHistoricalDatePicker = false
                            if (chosenMs != null) {
                                onFetchHistoricalSounding(chosenMs, selectedSynopticHour)
                            }
                        }
                    ) {
                        Text("Fetch ${if (selectedSynopticHour >= 12) "12Z" else "00Z"}")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHistoricalDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
private fun CompactStationCommandBar(
    uiState: UpperAirUiState,
    isSearchExpanded: Boolean,
    onOpenStationPicker: () -> Unit,
    onToggleSearch: () -> Unit,
    onSyncNow: () -> Unit
) {
    val station = uiState.activeStation
    val lastSynced = uiState.stationLastSyncMap[station.id] ?: 0L
    val badgeCode = if (station.raobIcaoOverride != null) station.effectiveRaobIcao.take(4) else station.id

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenStationPicker)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .testTag("station_selector_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SkyCyan.copy(alpha = 0.16f))
                        .border(1.dp, SkyCyan.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCode,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SkyCyan,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${station.name}, ${station.state}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch station",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    val statusLine = buildString {
                        append("${station.elevationM}m")
                        if (lastSynced > 0L) {
                            append(" • Cached ${TimeFormatters.formatRelativeAge(lastSynced)}")
                        } else {
                            append(" • Not cached")
                        }
                        if (uiState.settings.dailyOfflineCacheEnabled) {
                            append(" • Daily")
                        }
                    }
                    Text(
                        text = statusLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lastSynced > 0L) DewpointEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("toggle_site_search_button")
            ) {
                Icon(
                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (isSearchExpanded) "Close site search" else "Search station or city",
                    tint = if (isSearchExpanded) ThermalAmber else SkyCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onSyncNow,
                enabled = !uiState.isSyncing,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("sync_now_button")
            ) {
                if (uiState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = SkyCyan
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Download latest sounding, archive, GFS, and RRFS",
                        tint = SkyCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactModeStrip(
    selectedMode: SoundingDataMode,
    onSelectMode: (SoundingDataMode) -> Unit
) {
    val options = listOf(
        Triple(SoundingDataMode.OBSERVED, "Latest", "mode_observed_btn"),
        Triple(SoundingDataMode.PAST_OBSERVED, "Past Obs", "mode_past_btn"),
        Triple(SoundingDataMode.FORECAST_GFS, "GFS 7d", "mode_forecast_btn"),
        Triple(SoundingDataMode.FORECAST_RRFS, "RRFS 3km", "mode_rrfs_btn")
    )
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
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            options.forEach { (mode, label, tag) ->
                val active = selectedMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) SkyCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0f))
                        .then(
                            if (active) Modifier.border(1.dp, SkyCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            else Modifier
                        )
                        .clickable { onSelectMode(mode) }
                        .testTag(tag),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) SkyCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun IntegratedChartHeader(
    profile: SoundingProfile,
    selectedChart: ChartViewType,
    onSelectChartType: (ChartViewType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (profile.isForecast) ThermalAmber else DewpointEmerald)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = profile.sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${TimeFormatters.formatUtcShort(profile.validEpochMs)} • ${profile.levels.size} lvls",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ChartViewType.entries.forEach { type ->
                val active = selectedChart == type
                val label = when (type) {
                    ChartViewType.SKEW_T -> "Skew-T"
                    ChartViewType.HODOGRAPH -> "Hodo"
                    ChartViewType.LEVELS_TABLE -> "Table"
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) SkyCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            if (active) SkyCyan else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectChartType(type) }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .testTag("chart_tab_${type.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) SkyCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastTimelineCard(
    forecasts: List<SoundingProfile>,
    isRrfs: Boolean,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (forecasts.isEmpty()) {
                Text(
                    text = if (isRrfs) {
                        "No NOAA RRFS 3km profiles cached yet. Tap Sync to download."
                    } else {
                        "No 7-day GFS forecast profiles cached yet. Tap Sync to download."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val safeIdx = selectedIndex.coerceIn(0, forecasts.lastIndex)
                val current = forecasts[safeIdx]
                val modelPrefix = if (isRrfs) "RRFS 3km" else "GFS"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = ThermalAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "$modelPrefix +${current.forecastHourOffset}h (D${current.forecastHourOffset / 24 + 1})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = TimeFormatters.formatUtcShort(current.validEpochMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyCyan
                    )
                }

                // Precision Step Arrows + Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSelectIndex((safeIdx - 1).coerceAtLeast(0)) },
                        enabled = safeIdx > 0,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("forecast_step_prev")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous forecast hour"
                        )
                    }
                    Slider(
                        value = safeIdx.toFloat(),
                        onValueChange = { onSelectIndex(it.roundToInt()) },
                        valueRange = 0f..(forecasts.lastIndex.coerceAtLeast(1)).toFloat(),
                        steps = (forecasts.size - 2).coerceAtLeast(0),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("forecast_hour_slider")
                    )
                    IconButton(
                        onClick = { onSelectIndex((safeIdx + 1).coerceAtMost(forecasts.lastIndex)) },
                        enabled = safeIdx < forecasts.lastIndex,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("forecast_step_next")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next forecast hour"
                        )
                    }
                }

                val maxDay = (forecasts.maxOf { it.forecastHourOffset } / 24).coerceIn(0, 7)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (day in 0..maxDay) {
                        val targetHour = day * 24
                        val matchIdx = forecasts.indexOfFirst { it.forecastHourOffset >= targetHour }
                        if (matchIdx >= 0) {
                            val selectedDay = current.forecastHourOffset / 24 == day
                            FilterChip(
                                selected = selectedDay,
                                onClick = { onSelectIndex(matchIdx) },
                                label = {
                                    Text(
                                        text = if (day == 0) "Today" else "D+$day",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PastSoundingsCard(
    stationRaobCode: String,
    pastSoundings: List<SoundingProfile>,
    selectedIndex: Int,
    selectedCycleHour: Int,
    isFetchingPast: Boolean,
    onSelectIndex: (Int) -> Unit,
    onSelectCycleHour: (Int) -> Unit,
    onOpenDatePicker: () -> Unit,
    onFetchPastSevenDays: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (pastSoundings.isEmpty()) {
                Text(
                    text = "No past RAOB launches cached yet for $stationRaobCode. Load the past 7 days or pick a date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val safeIdx = selectedIndex.coerceIn(0, pastSoundings.lastIndex)
                val current = pastSoundings[safeIdx]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = DewpointEmerald,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Archive (${pastSoundings.size} launches)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${TimeFormatters.formatUtcShort(current.validEpochMs)} • ${TimeFormatters.formatRelativeAge(current.validEpochMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyCyan
                    )
                }

                if (pastSoundings.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onSelectIndex((safeIdx - 1).coerceAtLeast(0)) },
                            enabled = safeIdx > 0,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("past_step_prev")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Newer launch"
                            )
                        }
                        Slider(
                            value = safeIdx.toFloat(),
                            onValueChange = { onSelectIndex(it.roundToInt()) },
                            valueRange = 0f..(pastSoundings.lastIndex.coerceAtLeast(1)).toFloat(),
                            steps = (pastSoundings.size - 2).coerceAtLeast(0),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("past_sounding_slider")
                        )
                        IconButton(
                            onClick = { onSelectIndex((safeIdx + 1).coerceAtMost(pastSoundings.lastIndex)) },
                            enabled = safeIdx < pastSoundings.lastIndex,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("past_step_next")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Older launch"
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
                    pastSoundings.forEachIndexed { idx, prof ->
                        FilterChip(
                            selected = idx == safeIdx,
                            onClick = { onSelectIndex(idx) },
                            label = {
                                Text(
                                    text = "${TimeFormatters.formatUtcCycleChip(prof.validEpochMs)} (${prof.indices.sbcapeJkg}J)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("past_launch_chip_$idx")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedCycleHour == 0,
                    onClick = { onSelectCycleHour(0) },
                    label = { Text("00Z", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("past_cycle_00z")
                )
                FilterChip(
                    selected = selectedCycleHour == 12,
                    onClick = { onSelectCycleHour(12) },
                    label = { Text("12Z", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("past_cycle_12z")
                )
                FilledTonalButton(
                    onClick = onOpenDatePicker,
                    enabled = !isFetchingPast,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_past_date_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Pick Date", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = onFetchPastSevenDays,
                    enabled = !isFetchingPast,
                    modifier = Modifier.testTag("load_past_7d_button")
                ) {
                    if (isFetchingPast) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Past 7d", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySoundingCard(
    isSyncing: Boolean,
    stationIcao: String,
    onSyncNow: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                tint = SkyCyan,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (isSyncing) "Downloading $stationIcao Soundings..." else "Ready to Download $stationIcao",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Fetches latest & past 7d NWS RAOB launches plus NOAA GFS (7-day) and RRFS 3km profiles.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onSyncNow,
                enabled = !isSyncing,
                modifier = Modifier.testTag("empty_sync_button")
            ) {
                Text(if (isSyncing) "Downloading..." else "Download Sounding & Forecasts")
            }
        }
    }
}
