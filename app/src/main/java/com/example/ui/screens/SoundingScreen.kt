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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.SoundingLevel
import com.example.domain.model.SoundingProfile
import com.example.domain.physics.AtmosphericCalculator
import com.example.domain.model.UpperAirStation
import com.example.ui.ChartViewType
import com.example.ui.SoundingDataMode
import com.example.ui.UpperAirUiState
import com.example.ui.components.HodographChart
import com.example.ui.components.SiteSearchBar
import com.example.ui.components.SkewTChart
import com.example.ui.theme.DewpointEmerald
import com.example.ui.theme.InstabilityCoral
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.StormViolet
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
    modifier: Modifier = Modifier
) {
    val activeProfile = uiState.activeProfile
    var showHistoricalDatePicker by remember { mutableStateOf(false) }
    var selectedSynopticHour by remember { mutableIntStateOf(12) }

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
            // 0. Site & Location Search Bar
            item {
                SiteSearchBar(
                    query = uiState.siteQueryInput,
                    suggestions = uiState.siteSuggestions,
                    isResolving = uiState.isResolvingSite,
                    onQueryChange = onSiteQueryChange,
                    onSelectStation = onSelectResolvedSite,
                    onSubmitQuery = onSubmitSiteQuery
                )
            }

            // 1. Station Header + Live Sync Action
            item {
                StationHeaderCard(
                    uiState = uiState,
                    onOpenStationPicker = onOpenStationPicker,
                    onSyncNow = onSyncNow
                )
            }

            // Optional Sync Status Banner
            if (uiState.statusBanner != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismissBanner() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.statusBanner,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Dismiss",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 2. Mode Switcher (Latest Obs, Past Obs Archive, GFS 7-Day, RRFS 3km)
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = uiState.dataMode == SoundingDataMode.OBSERVED,
                        onClick = { onSelectMode(SoundingDataMode.OBSERVED) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                        modifier = Modifier.testTag("mode_observed_btn")
                    ) {
                        Text("Latest", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    SegmentedButton(
                        selected = uiState.dataMode == SoundingDataMode.PAST_OBSERVED,
                        onClick = { onSelectMode(SoundingDataMode.PAST_OBSERVED) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                        modifier = Modifier.testTag("mode_past_btn")
                    ) {
                        Text("Past Obs", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    SegmentedButton(
                        selected = uiState.dataMode == SoundingDataMode.FORECAST_GFS,
                        onClick = { onSelectMode(SoundingDataMode.FORECAST_GFS) },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                        modifier = Modifier.testTag("mode_forecast_btn")
                    ) {
                        Text("GFS 7d", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    SegmentedButton(
                        selected = uiState.dataMode == SoundingDataMode.FORECAST_RRFS,
                        onClick = { onSelectMode(SoundingDataMode.FORECAST_RRFS) },
                        shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                        modifier = Modifier.testTag("mode_rrfs_btn")
                    ) {
                        Text("RRFS 3km", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // 3A. Past Observed RAOB Archive Card (when in PAST_OBSERVED mode)
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

            // 3B. Forecast Timeline Scrubber (when in GFS or RRFS forecast mode)
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

            // 4. Main Sounding Visualization or Empty State
            if (activeProfile == null) {
                item {
                    EmptySoundingCard(
                        isSyncing = uiState.isSyncing,
                        stationIcao = uiState.activeStation.icao,
                        onSyncNow = onSyncNow
                    )
                }
            } else {
                // Valid time & source pill + Chart Type Switcher
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileMetaBar(profile = activeProfile)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ChartViewType.entries.forEachIndexed { idx, type ->
                                val label = when (type) {
                                    ChartViewType.SKEW_T -> "Skew-T"
                                    ChartViewType.HODOGRAPH -> "Hodograph"
                                    ChartViewType.LEVELS_TABLE -> "Levels"
                                }
                                SegmentedButton(
                                    selected = uiState.chartViewType == type,
                                    onClick = { onSelectChartType(type) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = idx,
                                        count = ChartViewType.entries.size
                                    ),
                                    modifier = Modifier.testTag("chart_tab_${type.name.lowercase()}")
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }

                // Interactive Chart
                item {
                    when (uiState.chartViewType) {
                        ChartViewType.SKEW_T -> SkewTChart(
                            profile = activeProfile,
                            useFahrenheit = uiState.settings.useFahrenheit,
                            useMph = uiState.settings.useMph
                        )
                        ChartViewType.HODOGRAPH -> HodographChart(
                            profile = activeProfile,
                            useMph = uiState.settings.useMph
                        )
                        ChartViewType.LEVELS_TABLE -> LevelsTableCard(
                            levels = activeProfile.levels,
                            useFahrenheit = uiState.settings.useFahrenheit,
                            useMph = uiState.settings.useMph
                        )
                    }
                }

                // Thermodynamic & Kinematic Telemetry Grid
                item {
                    ThermodynamicTelemetryGrid(
                        profile = activeProfile,
                        useMph = uiState.settings.useMph
                    )
                }
            }
        }

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
private fun StationHeaderCard(
    uiState: UpperAirUiState,
    onOpenStationPicker: () -> Unit,
    onSyncNow: () -> Unit
) {
    val station = uiState.activeStation
    val lastSynced = uiState.stationLastSyncMap[station.id] ?: 0L

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            SkyCyan.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenStationPicker)
                        .padding(4.dp)
                        .testTag("station_selector_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SkyCyan.copy(alpha = 0.18f))
                            .border(1.dp, SkyCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (station.raobIcaoOverride != null) station.effectiveRaobIcao.take(4) else station.id,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SkyCyan,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${station.name}, ${station.state}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Switch station",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        val nwsNote = uiState.observedSounding?.nwsOfficeHeadline?.takeIf { it.isNotBlank() }
                        val siteMeta = if (station.raobIcaoOverride != null) {
                            "RAOB ${station.effectiveRaobIcao} • Elev ${station.elevationM}m • ${nwsNote ?: "Model Point"}"
                        } else {
                            "${station.icao} • Elev ${station.elevationM}m • ${nwsNote ?: "WMO ${station.wmo}"}"
                        }
                        Text(
                            text = siteMeta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val cacheStatus = when {
                            lastSynced > 0 && uiState.settings.dailyOfflineCacheEnabled ->
                                "Offline cached • ${TimeFormatters.formatRelativeAge(lastSynced)} • Daily sync ON"
                            lastSynced > 0 ->
                                "Offline cached • ${TimeFormatters.formatRelativeAge(lastSynced)}"
                            else -> "Tap sync to download offline cache"
                        }
                        Text(
                            text = cacheStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = DewpointEmerald,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = SkyCyan
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Download latest sounding, GFS, and RRFS forecasts",
                            tint = SkyCyan
                        )
                    }
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (forecasts.isEmpty()) {
                Text(
                    text = if (isRrfs) {
                        "No NOAA RRFS 3km profiles cached yet. Tap Sync to download high-resolution RRFS soundings."
                    } else {
                        "No 7-day forecast profiles cached yet. Tap Sync to download NOAA GFS up to +168h."
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
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$modelPrefix +${current.forecastHourOffset}h (Day ${current.forecastHourOffset / 24 + 1})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = TimeFormatters.formatUtcShort(current.validEpochMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyCyan
                    )
                }

                Slider(
                    value = safeIdx.toFloat(),
                    onValueChange = { onSelectIndex(it.roundToInt()) },
                    valueRange = 0f..(forecasts.lastIndex.coerceAtLeast(1)).toFloat(),
                    steps = (forecasts.size - 2).coerceAtLeast(0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forecast_hour_slider")
                )

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
                                        text = if (day == 0) "Today" else "Day +$day",
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pastSoundings.isEmpty()) {
                Text(
                    text = "No past RAOB launches cached yet for $stationRaobCode. Load the past 7 days or pick any historical date below.",
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
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Archive (${pastSoundings.size} cached)",
                            style = MaterialTheme.typography.titleMedium,
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
                    Slider(
                        value = safeIdx.toFloat(),
                        onValueChange = { onSelectIndex(it.roundToInt()) },
                        valueRange = 0f..(pastSoundings.lastIndex.coerceAtLeast(1)).toFloat(),
                        steps = (pastSoundings.size - 2).coerceAtLeast(0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("past_sounding_slider")
                    )
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

            // Historical Date + 00Z/12Z Cycle Lookup Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    Spacer(modifier = Modifier.width(6.dp))
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
private fun ProfileMetaBar(profile: SoundingProfile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (profile.isForecast) ThermalAmber else DewpointEmerald)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = profile.sourceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "${profile.levels.size} lvls • ${TimeFormatters.formatUtcShort(profile.validEpochMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThermodynamicTelemetryGrid(
    profile: SoundingProfile,
    useMph: Boolean
) {
    val idx = profile.indices
    val maxW = if (useMph) "${(idx.maxWindKt * 1.15078).roundToInt()} mph" else "${idx.maxWindKt} kt"
    val shear06 = if (useMph) "${(idx.shear06Kt * 1.15078).roundToInt()} mph" else "${idx.shear06Kt} kt"
    val shear01 = if (useMph) "${(idx.shear01Kt * 1.15078).roundToInt()} mph" else "${idx.shear01Kt} kt"
    val pwatIn = "%.2f in".format(idx.pwatMm / 25.4)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Atmospheric Parameters",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryKpiCard(
                title = "SBCAPE / CIN",
                value = "${idx.sbcapeJkg} J/kg",
                subtitle = "CIN ${idx.sbcinJkg} • ML ${idx.mlcapeJkg}",
                icon = Icons.Outlined.Bolt,
                accent = InstabilityCoral,
                modifier = Modifier.weight(1f)
            )
            TelemetryKpiCard(
                title = "Bulk Shear",
                value = "0-6km $shear06",
                subtitle = "0-1km $shear01 • SRH ${idx.srh03M2S2}",
                icon = Icons.Outlined.Air,
                accent = SkyCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryKpiCard(
                title = "Moisture / PWAT",
                value = "${idx.pwatMm} mm ($pwatIn)",
                subtitle = "LCL ${idx.lclMeters}m (${idx.lclHpa} hPa)",
                icon = Icons.Outlined.WaterDrop,
                accent = DewpointEmerald,
                modifier = Modifier.weight(1f)
            )
            TelemetryKpiCard(
                title = "Freezing & Lapse",
                value = "${idx.freezingLevelM} m",
                subtitle = "700-500 LR ${idx.lapseRate700500}°C/km",
                icon = Icons.Outlined.Thermostat,
                accent = ThermalAmber,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryKpiCard(
                title = "Stability Indices",
                value = "LI ${idx.liftedIndex}",
                subtitle = "K ${idx.kIndex} • TT ${idx.totalTotals}",
                icon = Icons.Outlined.Bolt,
                accent = StormViolet,
                modifier = Modifier.weight(1f)
            )
            TelemetryKpiCard(
                title = "Jet Max Wind",
                value = maxW,
                subtitle = "Peak @ ${idx.maxWindPressureHpa} hPa",
                icon = Icons.Outlined.Air,
                accent = SkyCyan,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TelemetryKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LevelsTableCard(
    levels: List<SoundingLevel>,
    useFahrenheit: Boolean,
    useMph: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("PRES", style = MaterialTheme.typography.labelSmall, color = SkyCyan, modifier = Modifier.weight(1f))
                Text("HGHT", style = MaterialTheme.typography.labelSmall, color = SkyCyan, modifier = Modifier.weight(1f))
                Text("TEMP", style = MaterialTheme.typography.labelSmall, color = ThermalAmber, modifier = Modifier.weight(1f))
                Text("DEWP", style = MaterialTheme.typography.labelSmall, color = DewpointEmerald, modifier = Modifier.weight(1f))
                Text("WIND", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.2f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            for (lvl in levels.take(40)) {
                val tStr = TimeFormatters.formatTemp(lvl.tempC, useFahrenheit)
                val tdStr = lvl.dewpointC?.let { TimeFormatters.formatTemp(it, useFahrenheit) } ?: "--"
                val wStr = if (lvl.windDirDeg != null && lvl.windSpeedKt != null) {
                    val spd = if (useMph) "${(lvl.windSpeedKt * 1.15078).roundToInt()}" else "${lvl.windSpeedKt.roundToInt()}"
                    "${lvl.windDirDeg.roundToInt().toString().padStart(3, '0')}°/$spd"
                } else "--"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${lvl.pressureHpa.roundToInt()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text("${lvl.heightM.roundToInt()}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(tStr, style = MaterialTheme.typography.labelSmall, color = ThermalAmber, modifier = Modifier.weight(1f))
                    Text(tdStr, style = MaterialTheme.typography.labelSmall, color = DewpointEmerald, modifier = Modifier.weight(1f))
                    Text(wStr, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                tint = SkyCyan,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = if (isSyncing) "Downloading $stationIcao Soundings..." else "Ready to Download $stationIcao",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Fetches the latest NWS radiosonde launch (00Z/12Z) and 7 days of NOAA GFS upper-atmosphere model profiles.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onSyncNow,
                enabled = !isSyncing,
                modifier = Modifier.testTag("empty_sync_button")
            ) {
                Text(if (isSyncing) "Downloading..." else "Download Sounding & 7-Day Forecast")
            }
        }
    }
}
