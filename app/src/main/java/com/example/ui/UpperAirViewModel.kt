package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.UpperAirRepository
import com.example.domain.model.NwsStationsCatalog
import com.example.domain.model.SoundingProfile
import com.example.domain.model.SyncLogEntry
import com.example.domain.model.UpperAirStation
import com.example.domain.model.UserSettings
import com.example.worker.SoundingSyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SoundingDataMode {
    OBSERVED,
    PAST_OBSERVED,
    FORECAST_GFS,
    FORECAST_RRFS
}

enum class ChartViewType {
    SKEW_T,
    HODOGRAPH,
    LEVELS_TABLE
}

data class UpperAirUiState(
    val settings: UserSettings = UserSettings(),
    val activeStation: UpperAirStation = NwsStationsCatalog.findById("OUN"),
    val dataMode: SoundingDataMode = SoundingDataMode.OBSERVED,
    val chartViewType: ChartViewType = ChartViewType.SKEW_T,
    val observedSounding: SoundingProfile? = null,
    val pastObservedSoundings: List<SoundingProfile> = emptyList(),
    val selectedPastIndex: Int = 0,
    val isFetchingPast: Boolean = false,
    val forecastSoundings: List<SoundingProfile> = emptyList(),
    val rrfsSoundings: List<SoundingProfile> = emptyList(),
    val selectedForecastIndex: Int = 0,
    val isSyncing: Boolean = false,
    val statusBanner: String? = null,
    val syncLogs: List<SyncLogEntry> = emptyList(),
    val stationLastSyncMap: Map<String, Long> = emptyMap(),
    val totalCachedProfiles: Int = 0,
    val cachedStationsCount: Int = 0,
    val stationSearchQuery: String = "",
    val selectedRegionFilter: String = "All",
    val siteQueryInput: String = "",
    val siteSuggestions: List<UpperAirStation> = emptyList(),
    val isResolvingSite: Boolean = false
) {
    val activeModelForecasts: List<SoundingProfile>
        get() = when (dataMode) {
            SoundingDataMode.FORECAST_RRFS -> rrfsSoundings
            else -> forecastSoundings
        }

    val activeProfile: SoundingProfile?
        get() = when (dataMode) {
            SoundingDataMode.OBSERVED ->
                observedSounding ?: forecastSoundings.firstOrNull() ?: rrfsSoundings.firstOrNull()
            SoundingDataMode.PAST_OBSERVED -> {
                if (pastObservedSoundings.isEmpty()) observedSounding
                else pastObservedSoundings[selectedPastIndex.coerceIn(0, pastObservedSoundings.lastIndex)]
            }
            SoundingDataMode.FORECAST_GFS -> {
                if (forecastSoundings.isEmpty()) observedSounding
                else forecastSoundings[selectedForecastIndex.coerceIn(0, forecastSoundings.lastIndex)]
            }
            SoundingDataMode.FORECAST_RRFS -> {
                if (rrfsSoundings.isEmpty()) observedSounding ?: forecastSoundings.firstOrNull()
                else rrfsSoundings[selectedForecastIndex.coerceIn(0, rrfsSoundings.lastIndex)]
            }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UpperAirViewModel(
    private val repository: UpperAirRepository,
    private val syncScheduler: SoundingSyncScheduler
) : ViewModel() {

    private val dataModeFlow = MutableStateFlow(SoundingDataMode.OBSERVED)
    private val chartTypeFlow = MutableStateFlow(ChartViewType.SKEW_T)
    private val selectedForecastIndexFlow = MutableStateFlow(0)
    private val selectedPastIndexFlow = MutableStateFlow(0)
    private val isFetchingPastFlow = MutableStateFlow(false)
    private val isSyncingFlow = MutableStateFlow(false)
    private val statusBannerFlow = MutableStateFlow<String?>(null)
    private val searchQueryFlow = MutableStateFlow("")
    private val regionFilterFlow = MutableStateFlow("All")

    private val selectedStationIdFlow = repository.settingsFlow
        .map { it.selectedStationId }
        .distinctUntilChanged()

    private val observedFlow = selectedStationIdFlow.flatMapLatest { id ->
        repository.observeLatestObserved(id)
    }

    private val pastObservedFlow = selectedStationIdFlow.flatMapLatest { id ->
        repository.observePastObserved(id)
    }

    private val gfsForecastsFlow = selectedStationIdFlow.flatMapLatest { id ->
        repository.observeGfsForecasts(id)
    }

    private val rrfsForecastsFlow = selectedStationIdFlow.flatMapLatest { id ->
        repository.observeRrfsForecasts(id)
    }

    private data class UiMeta(
        val dataMode: SoundingDataMode,
        val chartType: ChartViewType,
        val forecastIdx: Int,
        val pastIdx: Int,
        val isFetchingPast: Boolean,
        val isSyncing: Boolean,
        val banner: String?,
        val query: String,
        val region: String
    )

    private val selectionFlow = combine(
        dataModeFlow,
        chartTypeFlow,
        selectedForecastIndexFlow,
        selectedPastIndexFlow,
        isFetchingPastFlow
    ) { mode, chart, forecastIdx, pastIdx, fetchingPast ->
        UiMeta(
            dataMode = mode,
            chartType = chart,
            forecastIdx = forecastIdx,
            pastIdx = pastIdx,
            isFetchingPast = fetchingPast,
            isSyncing = false,
            banner = null,
            query = "",
            region = "All"
        )
    }

    private val syncAndFilterFlow = combine(isSyncingFlow, statusBannerFlow, searchQueryFlow, regionFilterFlow) { syncing, banner, query, region ->
        UiMeta(
            dataMode = SoundingDataMode.OBSERVED,
            chartType = ChartViewType.SKEW_T,
            forecastIdx = 0,
            pastIdx = 0,
            isFetchingPast = false,
            isSyncing = syncing,
            banner = banner,
            query = query,
            region = region
        )
    }

    private val metaFlow = combine(selectionFlow, syncAndFilterFlow) { sel, rest ->
        rest.copy(
            dataMode = sel.dataMode,
            chartType = sel.chartType,
            forecastIdx = sel.forecastIdx,
            pastIdx = sel.pastIdx,
            isFetchingPast = sel.isFetchingPast
        )
    }

    private data class LogsAndCounts(
        val logs: List<SyncLogEntry>,
        val lastMap: Map<String, Long>,
        val totalProfiles: Int,
        val cachedStations: Int
    )

    private val cacheCountsFlow = combine(
        repository.totalCachedProfilesFlow,
        repository.cachedStationsCountFlow
    ) { totalProfiles, cachedStations ->
        totalProfiles to cachedStations
    }

    private val logsAndCountsFlow = combine(
        repository.syncLogsFlow,
        repository.stationLastSyncFlow,
        cacheCountsFlow
    ) { logs, lastMap, counts ->
        LogsAndCounts(logs, lastMap, counts.first, counts.second)
    }

    private data class CacheBundle(
        val observed: SoundingProfile?,
        val pastObserved: List<SoundingProfile>,
        val gfsForecasts: List<SoundingProfile>,
        val rrfsForecasts: List<SoundingProfile>,
        val logs: List<SyncLogEntry>,
        val lastMap: Map<String, Long>,
        val totalProfiles: Int,
        val cachedStations: Int
    )

    private val cacheAndLogsFlow = combine(
        observedFlow,
        pastObservedFlow,
        gfsForecastsFlow,
        rrfsForecastsFlow,
        logsAndCountsFlow
    ) { observed, pastObs, gfs, rrfs, extra ->
        CacheBundle(
            observed = observed,
            pastObserved = pastObs,
            gfsForecasts = gfs,
            rrfsForecasts = rrfs,
            logs = extra.logs,
            lastMap = extra.lastMap,
            totalProfiles = extra.totalProfiles,
            cachedStations = extra.cachedStations
        )
    }

    private val siteQueryInputFlow = MutableStateFlow("")
    private val siteSuggestionsFlow = MutableStateFlow<List<UpperAirStation>>(emptyList())
    private val isResolvingSiteFlow = MutableStateFlow(false)
    private var suggestJob: Job? = null

    private data class SiteSearchBundle(
        val queryInput: String,
        val suggestions: List<UpperAirStation>,
        val isResolving: Boolean
    )

    private val siteSearchFlow = combine(
        siteQueryInputFlow,
        siteSuggestionsFlow,
        isResolvingSiteFlow
    ) { q, list, resolving ->
        SiteSearchBundle(q, list, resolving)
    }

    val uiState: StateFlow<UpperAirUiState> = combine(
        repository.settingsFlow,
        cacheAndLogsFlow,
        metaFlow,
        siteSearchFlow
    ) { settings, bundle, meta, siteSearch ->
        val activeList = if (meta.dataMode == SoundingDataMode.FORECAST_RRFS) {
            bundle.rrfsForecasts
        } else {
            bundle.gfsForecasts
        }
        val safeForecastIdx = if (activeList.isEmpty()) 0 else meta.forecastIdx.coerceIn(0, activeList.lastIndex)
        val safePastIdx = if (bundle.pastObserved.isEmpty()) 0 else meta.pastIdx.coerceIn(0, bundle.pastObserved.lastIndex)
        UpperAirUiState(
            settings = settings,
            activeStation = repository.resolveStation(settings.selectedStationId, settings.customStations),
            dataMode = meta.dataMode,
            chartViewType = meta.chartType,
            observedSounding = bundle.observed,
            pastObservedSoundings = bundle.pastObserved,
            selectedPastIndex = safePastIdx,
            isFetchingPast = meta.isFetchingPast,
            forecastSoundings = bundle.gfsForecasts,
            rrfsSoundings = bundle.rrfsForecasts,
            selectedForecastIndex = safeForecastIdx,
            isSyncing = meta.isSyncing,
            statusBanner = meta.banner,
            syncLogs = bundle.logs,
            stationLastSyncMap = bundle.lastMap,
            totalCachedProfiles = bundle.totalProfiles,
            cachedStationsCount = bundle.cachedStations,
            stationSearchQuery = meta.query,
            selectedRegionFilter = meta.region,
            siteQueryInput = siteSearch.queryInput,
            siteSuggestions = siteSearch.suggestions,
            isResolvingSite = siteSearch.isResolving
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UpperAirUiState()
    )

    init {
        viewModelScope.launch {
            val activeId = repository.settingsFlow.map { it.selectedStationId }.distinctUntilChanged()
            activeId.collect { stationId ->
                if (repository.needsInitialSync(stationId)) {
                    syncActiveStation(stationId, sourceTag = "AUTO_OPEN")
                }
            }
        }
    }

    fun selectStation(stationId: String) {
        viewModelScope.launch {
            repository.selectStation(stationId)
            selectedForecastIndexFlow.value = 0
            selectedPastIndexFlow.value = 0
        }
    }

    fun setDataMode(mode: SoundingDataMode) {
        dataModeFlow.value = mode
        if (mode == SoundingDataMode.FORECAST_GFS || mode == SoundingDataMode.FORECAST_RRFS) {
            selectedForecastIndexFlow.value = 0
        } else if (mode == SoundingDataMode.PAST_OBSERVED && uiState.value.pastObservedSoundings.size <= 1) {
            fetchPastSevenDays()
        }
    }

    fun openPastArchive() {
        dataModeFlow.value = SoundingDataMode.PAST_OBSERVED
        val pastList = uiState.value.pastObservedSoundings
        selectedPastIndexFlow.value = if (pastList.size > 1) 1 else 0
        if (pastList.size <= 1) {
            fetchPastSevenDays()
        }
    }

    fun selectPastIndex(index: Int) {
        selectedPastIndexFlow.value = index.coerceAtLeast(0)
    }

    fun fetchPastSevenDays() {
        if (isFetchingPastFlow.value) return
        val station = uiState.value.activeStation
        viewModelScope.launch {
            isFetchingPastFlow.value = true
            statusBannerFlow.value = "Fetching past 7 days of RAOB launches for ${station.effectiveRaobIcao}..."
            val res = repository.fetchPastObservedArchive(station, daysBack = 7)
            isFetchingPastFlow.value = false
            res.onSuccess { list ->
                statusBannerFlow.value = "Cached ${list.size} past RAOB launches for ${station.effectiveRaobIcao}"
            }.onFailure { err ->
                statusBannerFlow.value = err.message ?: "Past archive unavailable for ${station.effectiveRaobIcao}"
            }
        }
    }

    fun fetchHistoricalSounding(utcDateMidnightMs: Long, synopticHourUtc: Int) {
        if (isFetchingPastFlow.value) return
        val station = uiState.value.activeStation
        viewModelScope.launch {
            isFetchingPastFlow.value = true
            val hrLabel = if (synopticHourUtc >= 12) "12Z" else "00Z"
            statusBannerFlow.value = "Querying historical $hrLabel RAOB archive for ${station.effectiveRaobIcao}..."
            val res = repository.fetchHistoricalSounding(station, utcDateMidnightMs, synopticHourUtc)
            isFetchingPastFlow.value = false
            res.onSuccess { profile ->
                dataModeFlow.value = SoundingDataMode.PAST_OBSERVED
                delay(80)
                val matchIdx = uiState.value.pastObservedSoundings.indexOfFirst {
                    it.validEpochMs == profile.validEpochMs
                }
                if (matchIdx >= 0) {
                    selectedPastIndexFlow.value = matchIdx
                }
                statusBannerFlow.value = "Loaded historical ${profile.validTimeUtcIso} (${profile.levels.size} levels)"
            }.onFailure { err ->
                statusBannerFlow.value = err.message ?: "No RAOB launch recorded for that date/cycle"
            }
        }
    }

    fun setChartViewType(type: ChartViewType) {
        chartTypeFlow.value = type
    }

    fun selectForecastIndex(index: Int) {
        selectedForecastIndexFlow.value = index.coerceAtLeast(0)
    }

    fun jumpToForecastHour(index: Int) {
        dataModeFlow.value = SoundingDataMode.FORECAST_GFS
        selectedForecastIndexFlow.value = index.coerceAtLeast(0)
    }

    fun jumpToRrfsHour(index: Int) {
        dataModeFlow.value = SoundingDataMode.FORECAST_RRFS
        selectedForecastIndexFlow.value = index.coerceAtLeast(0)
    }

    fun syncCurrentStationNow() {
        val stationId = uiState.value.activeStation.id
        viewModelScope.launch {
            syncActiveStation(stationId, sourceTag = "MANUAL")
        }
    }

    fun syncAllPinnedNow() {
        if (isSyncingFlow.value) return
        viewModelScope.launch {
            isSyncingFlow.value = true
            statusBannerFlow.value = "Caching RAOB, past 7d archive, GFS & RRFS offline for pinned sites..."
            val outcomes = repository.syncAllPinnedStations(sourceTag = "BATCH")
            val okCount = outcomes.count { it.observedSuccess || it.forecastSuccess || it.rrfsSuccess }
            isSyncingFlow.value = false
            statusBannerFlow.value = "Cached offline data for $okCount/${outcomes.size} pinned stations"
        }
    }

    private suspend fun syncActiveStation(stationId: String, sourceTag: String) {
        if (isSyncingFlow.value) return
        isSyncingFlow.value = true
        val station = repository.resolveCurrentStation(stationId)
        statusBannerFlow.value = "Fetching ${station.name} (${station.effectiveRaobIcao}) RAOB, Archive, GFS & RRFS..."
        val outcome = repository.syncStation(station, sourceTag = sourceTag)
        isSyncingFlow.value = false
        statusBannerFlow.value = outcome.message
    }

    fun updateSiteQueryInput(input: String) {
        siteQueryInputFlow.value = input
        suggestJob?.cancel()
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            siteSuggestionsFlow.value = emptyList()
            return
        }
        val localMatches = NwsStationsCatalog.searchMatches(trimmed, limit = 6)
        siteSuggestionsFlow.value = localMatches

        if (trimmed.length >= 3) {
            suggestJob = viewModelScope.launch {
                delay(320)
                val online = repository.searchLocationsOnline(trimmed)
                val combined = (localMatches + online)
                    .distinctBy { "${it.name.lowercase()}_${it.state.lowercase()}_${it.icao}" }
                    .take(8)
                siteSuggestionsFlow.value = combined
            }
        }
    }

    fun selectResolvedSite(station: UpperAirStation) {
        siteQueryInputFlow.value = ""
        siteSuggestionsFlow.value = emptyList()
        viewModelScope.launch {
            repository.selectResolvedStation(station)
            selectedForecastIndexFlow.value = 0
            selectedPastIndexFlow.value = 0
        }
    }

    fun submitSiteQuery(rawQuery: String = siteQueryInputFlow.value) {
        val clean = rawQuery.trim()
        if (clean.isEmpty() || isResolvingSiteFlow.value) return
        suggestJob?.cancel()
        viewModelScope.launch {
            isResolvingSiteFlow.value = true
            statusBannerFlow.value = "Looking up \"$clean\"..."
            val res = repository.resolveSiteQuery(clean)
            isResolvingSiteFlow.value = false
            res.onSuccess { station ->
                siteQueryInputFlow.value = ""
                siteSuggestionsFlow.value = emptyList()
                repository.selectResolvedStation(station)
                selectedForecastIndexFlow.value = 0
                selectedPastIndexFlow.value = 0
                if (!repository.needsInitialSync(station.id)) {
                    syncActiveStation(station.id, sourceTag = "QUERY")
                }
            }.onFailure { err ->
                statusBannerFlow.value = err.message ?: "Could not find site \"$clean\""
            }
        }
    }

    fun dismissBanner() {
        statusBannerFlow.value = null
    }

    fun togglePinnedStation(stationId: String) {
        viewModelScope.launch {
            repository.togglePinnedStation(stationId)
        }
    }

    fun updateSearchQuery(q: String) {
        searchQueryFlow.value = q
    }

    fun updateRegionFilter(region: String) {
        regionFilterFlow.value = region
    }

    fun setDailyOfflineCacheEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDailyOfflineCacheEnabled(enabled)
            val s = uiState.value.settings
            syncScheduler.configurePeriodicSync(
                dailyOfflineEnabled = enabled,
                autoSyncEnabled = s.autoSyncEnabled,
                intervalHours = s.syncIntervalHours,
                wifiOnly = s.wifiOnly
            )
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoSyncEnabled(enabled)
            val s = uiState.value.settings
            syncScheduler.configurePeriodicSync(
                dailyOfflineEnabled = s.dailyOfflineCacheEnabled,
                autoSyncEnabled = enabled,
                intervalHours = s.syncIntervalHours,
                wifiOnly = s.wifiOnly
            )
        }
    }

    fun setSyncIntervalHours(hours: Int) {
        viewModelScope.launch {
            repository.setSyncIntervalHours(hours)
            val s = uiState.value.settings
            syncScheduler.configurePeriodicSync(
                dailyOfflineEnabled = s.dailyOfflineCacheEnabled,
                autoSyncEnabled = s.autoSyncEnabled,
                intervalHours = hours,
                wifiOnly = s.wifiOnly
            )
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            repository.setWifiOnly(wifiOnly)
            val s = uiState.value.settings
            syncScheduler.configurePeriodicSync(
                dailyOfflineEnabled = s.dailyOfflineCacheEnabled,
                autoSyncEnabled = s.autoSyncEnabled,
                intervalHours = s.syncIntervalHours,
                wifiOnly = wifiOnly
            )
        }
    }

    fun setUseFahrenheit(useF: Boolean) {
        viewModelScope.launch { repository.setUseFahrenheit(useF) }
    }

    fun setUseMph(useMph: Boolean) {
        viewModelScope.launch { repository.setUseMph(useMph) }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { repository.setDarkTheme(dark) }
    }

    fun triggerBackgroundWorkerSync() {
        val s = uiState.value.settings
        syncScheduler.enqueueImmediateBackgroundSync(s.wifiOnly)
        statusBannerFlow.value = "Queued WorkManager offline cache download for pinned stations"
    }

    class Factory(
        private val repository: UpperAirRepository,
        private val syncScheduler: SoundingSyncScheduler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UpperAirViewModel(repository, syncScheduler) as T
        }
    }
}
