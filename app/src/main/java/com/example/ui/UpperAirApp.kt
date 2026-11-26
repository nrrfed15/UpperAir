package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.NwsStationsCatalog
import com.example.ui.screens.OutlookScreen
import com.example.ui.screens.SoundingScreen
import com.example.ui.screens.StationsScreen
import com.example.ui.screens.SyncSettingsScreen
import com.example.ui.screens.UsaMapScreen
import com.example.ui.theme.SkyCyan

private enum class TopTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Sounding("sounding", "Sounding", Icons.Filled.Timeline, Icons.Outlined.Timeline),
    Map("map", "Map", Icons.Filled.Map, Icons.Outlined.Map),
    Outlook("outlook", "7-Day", Icons.Filled.CloudSync, Icons.Outlined.CloudSync),
    Stations("stations", "Stations", Icons.Filled.Public, Icons.Outlined.Public),
    Sync("sync", "Auto-Sync", Icons.Filled.Tune, Icons.Outlined.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpperAirApp(viewModel: UpperAirViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: TopTab.Sounding.route
    var showStationSheet by remember { mutableStateOf(false) }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        TopTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TopTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = TopTab.Sounding.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(TopTab.Sounding.route) {
                        SoundingScreen(
                            uiState = uiState,
                            onOpenStationPicker = { showStationSheet = true },
                            onSyncNow = viewModel::syncCurrentStationNow,
                            onSelectMode = viewModel::setDataMode,
                            onSelectChartType = viewModel::setChartViewType,
                            onSelectForecastIndex = viewModel::selectForecastIndex,
                            onSelectPastIndex = viewModel::selectPastIndex,
                            onFetchPastSevenDays = viewModel::fetchPastSevenDays,
                            onFetchHistoricalSounding = viewModel::fetchHistoricalSounding,
                            onDismissBanner = viewModel::dismissBanner,
                            onSiteQueryChange = viewModel::updateSiteQueryInput,
                            onSelectResolvedSite = viewModel::selectResolvedSite,
                            onSubmitSiteQuery = viewModel::submitSiteQuery,
                            onSelectParcelType = viewModel::setParcelType,
                            onToggleMiniHodograph = viewModel::toggleMiniHodograph
                        )
                    }
                    composable(TopTab.Map.route) {
                        UsaMapScreen(
                            uiState = uiState,
                            onDropMapPin = viewModel::dropMapPin,
                            onSelectMapStation = viewModel::selectMapStation,
                            onClearMapPin = viewModel::clearMapPin,
                            onLoadSelectedMapPointRrfs = {
                                viewModel.loadSelectedMapPointRrfs()
                                navigateToTab(TopTab.Sounding.route)
                            },
                            onTogglePinSelectedMapPoint = viewModel::togglePinSelectedMapPoint
                        )
                    }
                    composable(TopTab.Outlook.route) {
                        OutlookScreen(
                            uiState = uiState,
                            onSelectForecastIndex = viewModel::selectForecastIndex,
                            onJumpToSoundingHour = { mode, hourIdx ->
                                if (mode == SoundingDataMode.FORECAST_RRFS) {
                                    viewModel.jumpToRrfsHour(hourIdx)
                                } else {
                                    viewModel.jumpToForecastHour(hourIdx)
                                }
                                navigateToTab(TopTab.Sounding.route)
                            },
                            onSyncNow = viewModel::syncCurrentStationNow
                        )
                    }
                    composable(TopTab.Stations.route) {
                        StationsScreen(
                            uiState = uiState,
                            onSelectStation = { stationId ->
                                viewModel.selectStation(stationId)
                                navigateToTab(TopTab.Sounding.route)
                            },
                            onTogglePin = viewModel::togglePinnedStation,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onRegionFilterChange = viewModel::updateRegionFilter,
                            onSyncAllPinned = viewModel::syncAllPinnedNow,
                            onSubmitCustomSiteQuery = { query ->
                                viewModel.submitSiteQuery(query)
                                navigateToTab(TopTab.Sounding.route)
                            }
                        )
                    }
                    composable(TopTab.Sync.route) {
                        SyncSettingsScreen(
                            uiState = uiState,
                            onSetDailyOfflineCache = viewModel::setDailyOfflineCacheEnabled,
                            onSetAutoSync = viewModel::setAutoSyncEnabled,
                            onSetIntervalHours = viewModel::setSyncIntervalHours,
                            onSetWifiOnly = viewModel::setWifiOnly,
                            onRunBackgroundWorkerNow = viewModel::triggerBackgroundWorkerSync,
                            onSetUseFahrenheit = viewModel::setUseFahrenheit,
                            onSetUseMph = viewModel::setUseMph,
                            onSetDarkTheme = viewModel::setDarkTheme
                        )
                    }
                }
            }
        }
    }

    if (showStationSheet) {
        var sheetQuery by remember { mutableStateOf("") }
        val quickStations = remember(uiState.settings.pinnedStationIds, uiState.settings.customStations, sheetQuery) {
            val allSites = (uiState.settings.customStations.values + NwsStationsCatalog.stations)
                .distinctBy { it.id }
            val q = sheetQuery.trim()
            val filtered = if (q.isEmpty()) {
                allSites
            } else {
                allSites.filter { st ->
                    st.id.contains(q, ignoreCase = true) ||
                        st.icao.contains(q, ignoreCase = true) ||
                        st.name.contains(q, ignoreCase = true) ||
                        st.state.contains(q, ignoreCase = true)
                }
            }
            filtered.sortedByDescending { uiState.settings.pinnedStationIds.contains(it.id) }
        }
        ModalBottomSheet(onDismissRequest = { showStationSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Select or Query Site",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = sheetQuery,
                    onValueChange = { sheetQuery = it },
                    placeholder = { Text("Station ID (KOAX) or city name...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_station_search_input")
                )
                if (sheetQuery.trim().isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = SkyCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.submitSiteQuery(sheetQuery.trim())
                                showStationSheet = false
                            }
                            .testTag("sheet_query_custom_button")
                    ) {
                        Text(
                            text = "Query sounding for \"${sheetQuery.trim()}\"",
                            style = MaterialTheme.typography.labelLarge,
                            color = SkyCyan,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(quickStations, key = { it.id }) { station ->
                        val isSelected = station.id == uiState.activeStation.id
                        val isPinned = uiState.settings.pinnedStationIds.contains(station.id)
                        Surface(
                            color = if (isSelected) SkyCyan.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.selectStation(station.id)
                                    showStationSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (station.raobIcaoOverride != null) station.effectiveRaobIcao else station.icao,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) SkyCyan else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "${station.name}, ${station.state}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                if (isPinned) {
                                    Text(
                                        text = "Pinned",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SkyCyan
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}
