package com.example.data.repository

import com.example.data.local.ProfileJsonCodec
import com.example.data.local.SettingsStore
import com.example.data.local.StationSyncTimestamp
import com.example.data.local.SyncLogEntity
import com.example.data.local.UpperAirDao
import com.example.data.local.toDomain
import com.example.data.remote.NwsSoundingClient
import com.example.domain.model.NwsStationsCatalog
import com.example.domain.model.SoundingProfile
import com.example.domain.model.SyncLogEntry
import com.example.domain.model.UpperAirStation
import com.example.domain.model.UserSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class StationSyncOutcome(
    val stationId: String,
    val observedSuccess: Boolean,
    val forecastSuccess: Boolean,
    val rrfsSuccess: Boolean,
    val message: String
)

class UpperAirRepository(
    private val dao: UpperAirDao,
    private val settingsStore: SettingsStore,
    private val client: NwsSoundingClient
) {
    val settingsFlow: Flow<UserSettings> = settingsStore.settingsFlow

    val syncLogsFlow: Flow<List<SyncLogEntry>> = dao.observeSyncLogs().map { list ->
        list.map { it.toDomain() }
    }

    val stationLastSyncFlow: Flow<Map<String, Long>> = dao.observeStationLastFetched().map { list ->
        list.associate { it.stationId to it.lastFetched }
    }

    val totalCachedProfilesFlow: Flow<Int> = dao.observeTotalCachedProfilesCount()
    val cachedStationsCountFlow: Flow<Int> = dao.observeCachedStationsCount()

    fun observeLatestObserved(stationId: String): Flow<SoundingProfile?> =
        dao.observeLatestObserved(stationId).map { entity ->
            entity?.let { ProfileJsonCodec.toDomain(it) }
        }

    fun observePastObserved(stationId: String): Flow<List<SoundingProfile>> =
        dao.observePastObserved(stationId).map { list ->
            list.map { ProfileJsonCodec.toDomain(it) }
        }

    fun observeGfsForecasts(stationId: String): Flow<List<SoundingProfile>> =
        dao.observeModelForecasts(stationId, "GFS").map { list ->
            list.map { ProfileJsonCodec.toDomain(it) }
        }

    fun observeRrfsForecasts(stationId: String): Flow<List<SoundingProfile>> =
        dao.observeModelForecasts(stationId, "RRFS").map { list ->
            list.map { ProfileJsonCodec.toDomain(it) }
        }

    suspend fun syncStation(
        station: UpperAirStation,
        sourceTag: String = "MANUAL"
    ): StationSyncOutcome = coroutineScope {
        val obsDeferred = async { client.fetchLatestObservedSounding(station) }
        val pastArchiveDeferred = async { client.fetchPastObservedSoundings(station, daysBack = 7) }
        val gfsDeferred = async { client.fetchSevenDayForecastSoundings(station) }
        val rrfsDeferred = async { client.fetchRrfsForecastSoundings(station) }

        val obsResult = obsDeferred.await()
        val pastArchiveResult = pastArchiveDeferred.await()
        val gfsResult = gfsDeferred.await()
        val rrfsResult = rrfsDeferred.await()

        val now = System.currentTimeMillis()
        var obsOk = false
        var gfsOk = false
        var rrfsOk = false
        val notes = mutableListOf<String>()

        obsResult.onSuccess { profile ->
            dao.upsertSnapshot(ProfileJsonCodec.toEntity(profile))
            dao.insertSyncLog(
                SyncLogEntity(
                    timestampMs = now,
                    stationId = station.id,
                    sourceType = "${sourceTag}_RAOB",
                    success = true,
                    message = "Cached ${profile.levels.size} RAOB levels (${profile.validTimeUtcIso})",
                    levelsOrStepsCount = profile.levels.size
                )
            )
            obsOk = true
            notes.add("RAOB ${profile.levels.size} lvls")
        }.onFailure { err ->
            dao.insertSyncLog(
                SyncLogEntity(
                    timestampMs = now,
                    stationId = station.id,
                    sourceType = "${sourceTag}_RAOB",
                    success = false,
                    message = err.message ?: "RAOB fetch failed",
                    levelsOrStepsCount = 0
                )
            )
            notes.add("RAOB unavail")
        }

        pastArchiveResult.onSuccess { archiveList ->
            if (archiveList.isNotEmpty()) {
                dao.upsertSnapshots(archiveList.map { ProfileJsonCodec.toEntity(it) })
                obsOk = true
                notes.add("${archiveList.size} past obs")
            }
        }

        gfsResult.onSuccess { profiles ->
            val entities = profiles.map { ProfileJsonCodec.toEntity(it) }
            dao.replaceModelForStation(station.id, "GFS", entities)
            dao.insertSyncLog(
                SyncLogEntity(
                    timestampMs = now,
                    stationId = station.id,
                    sourceType = "${sourceTag}_GFS7D",
                    success = true,
                    message = "Cached ${profiles.size} GFS profiles (0h to +${profiles.last().forecastHourOffset}h)",
                    levelsOrStepsCount = profiles.size
                )
            )
            gfsOk = true
            notes.add("GFS ${profiles.size}")
        }.onFailure { err ->
            dao.insertSyncLog(
                SyncLogEntity(
                    timestampMs = now,
                    stationId = station.id,
                    sourceType = "${sourceTag}_GFS7D",
                    success = false,
                    message = err.message ?: "GFS 7-day fetch failed",
                    levelsOrStepsCount = 0
                )
            )
            notes.add("GFS err")
        }

        rrfsResult.onSuccess { profiles ->
            val entities = profiles.map { ProfileJsonCodec.toEntity(it) }
            dao.replaceModelForStation(station.id, "RRFS", entities)
            dao.insertSyncLog(
                SyncLogEntity(
                    timestampMs = now,
                    stationId = station.id,
                    sourceType = "${sourceTag}_RRFS",
                    success = true,
                    message = "Cached ${profiles.size} RRFS 3km profiles (0h to +${profiles.last().forecastHourOffset}h)",
                    levelsOrStepsCount = profiles.size
                )
            )
            rrfsOk = true
            notes.add("RRFS ${profiles.size}")
        }.onFailure { err ->
            dao.insertSyncLog(
                SyncLogEntity(
                    timestampMs = now,
                    stationId = station.id,
                    sourceType = "${sourceTag}_RRFS",
                    success = false,
                    message = err.message ?: "RRFS fetch failed",
                    levelsOrStepsCount = 0
                )
            )
        }

        if (obsOk || gfsOk || rrfsOk) {
            settingsStore.updateLastSyncEpoch(now)
        }
        dao.trimOldLogs()

        StationSyncOutcome(
            stationId = station.id,
            observedSuccess = obsOk,
            forecastSuccess = gfsOk,
            rrfsSuccess = rrfsOk,
            message = "${station.id}: ${notes.joinToString(" • ")}"
        )
    }

    fun resolveStation(stationId: String, customStations: Map<String, UpperAirStation>): UpperAirStation {
        return NwsStationsCatalog.findByIdOrNull(stationId)
            ?: customStations[stationId]
            ?: NwsStationsCatalog.findById(stationId)
    }

    suspend fun resolveCurrentStation(stationId: String): UpperAirStation {
        val settings = settingsStore.currentSettings()
        return resolveStation(stationId, settings.customStations)
    }

    suspend fun resolveSiteQuery(query: String): Result<UpperAirStation> {
        val result = client.resolveSiteQuery(query)
        result.onSuccess { station ->
            if (NwsStationsCatalog.findByIdOrNull(station.id) == null) {
                settingsStore.saveCustomStation(station)
            }
        }
        return result
    }

    suspend fun searchLocationsOnline(query: String): List<UpperAirStation> =
        client.searchLocationsOnline(query)

    fun createImmediateMapStation(lat: Double, lon: Double): UpperAirStation =
        client.createImmediateMapStation(lat, lon)

    suspend fun resolveMapPoint(lat: Double, lon: Double): UpperAirStation =
        client.resolveMapPoint(lat, lon)

    suspend fun saveCustomStation(station: UpperAirStation) {
        if (NwsStationsCatalog.findByIdOrNull(station.id) == null) {
            settingsStore.saveCustomStation(station)
        }
    }

    suspend fun selectResolvedStation(station: UpperAirStation) {
        if (NwsStationsCatalog.findByIdOrNull(station.id) == null) {
            settingsStore.saveCustomStation(station)
        }
        settingsStore.setSelectedStation(station.id)
    }

    suspend fun syncAllPinnedStations(sourceTag: String = "AUTO"): List<StationSyncOutcome> {
        val settings = settingsStore.currentSettings()
        val ids = (settings.pinnedStationIds + settings.selectedStationId).distinct()
        val outcomes = mutableListOf<StationSyncOutcome>()
        for (id in ids) {
            val station = resolveStation(id, settings.customStations)
            outcomes.add(syncStation(station, sourceTag))
        }
        return outcomes
    }

    suspend fun fetchPastObservedArchive(
        station: UpperAirStation,
        daysBack: Int = 7
    ): Result<List<SoundingProfile>> {
        val result = client.fetchPastObservedSoundings(station, daysBack)
        val now = System.currentTimeMillis()
        result.onSuccess { profiles ->
            dao.upsertSnapshots(profiles.map { ProfileJsonCodec.toEntity(it) })
            dao.insertSyncLog(
                SyncLogEntity(
                    timestampMs = now,
                    stationId = station.id,
                    sourceType = "ARCHIVE_${daysBack}D",
                    success = true,
                    message = "Cached ${profiles.size} past RAOB launches (past ${daysBack}d)",
                    levelsOrStepsCount = profiles.size
                )
            )
            settingsStore.updateLastSyncEpoch(now)
        }.onFailure { err ->
            dao.insertSyncLog(
                SyncLogEntity(
                    timestampMs = now,
                    stationId = station.id,
                    sourceType = "ARCHIVE_${daysBack}D",
                    success = false,
                    message = err.message ?: "Past archive fetch failed",
                    levelsOrStepsCount = 0
                )
            )
        }
        dao.trimOldLogs()
        return result
    }

    suspend fun fetchHistoricalSounding(
        station: UpperAirStation,
        utcDateMidnightMs: Long,
        synopticHourUtc: Int
    ): Result<SoundingProfile> {
        val result = client.fetchObservedSoundingAt(station, utcDateMidnightMs, synopticHourUtc)
        val now = System.currentTimeMillis()
        result.onSuccess { profile ->
            dao.upsertSnapshot(ProfileJsonCodec.toEntity(profile))
            dao.insertSyncLog(
                SyncLogEntity(
                    timestampMs = now,
                    stationId = station.id,
                    sourceType = "HIST_RAOB",
                    success = true,
                    message = "Cached historical RAOB ${profile.validTimeUtcIso} (${profile.levels.size} lvls)",
                    levelsOrStepsCount = profile.levels.size
                )
            )
        }.onFailure { err ->
            dao.insertSyncLog(
                SyncLogEntity(
                    timestampMs = now,
                    stationId = station.id,
                    sourceType = "HIST_RAOB",
                    success = false,
                    message = err.message ?: "Historical RAOB fetch failed",
                    levelsOrStepsCount = 0
                )
            )
        }
        dao.trimOldLogs()
        return result
    }

    suspend fun needsInitialSync(stationId: String, maxAgeMs: Long = 12 * 3600 * 1000L): Boolean {
        val last = dao.getLastFetchedForStation(stationId) ?: return true
        return System.currentTimeMillis() - last > maxAgeMs
    }

    suspend fun selectStation(stationId: String) = settingsStore.setSelectedStation(stationId)
    suspend fun togglePinnedStation(stationId: String) = settingsStore.togglePinnedStation(stationId)
    suspend fun setDailyOfflineCacheEnabled(enabled: Boolean) = settingsStore.setDailyOfflineCacheEnabled(enabled)
    suspend fun setAutoSyncEnabled(enabled: Boolean) = settingsStore.setAutoSyncEnabled(enabled)
    suspend fun setSyncIntervalHours(hours: Int) = settingsStore.setSyncIntervalHours(hours)
    suspend fun setWifiOnly(wifiOnly: Boolean) = settingsStore.setWifiOnly(wifiOnly)
    suspend fun setUseFahrenheit(useF: Boolean) = settingsStore.setUseFahrenheit(useF)
    suspend fun setUseMph(useMph: Boolean) = settingsStore.setUseMph(useMph)
    suspend fun setDarkTheme(dark: Boolean) = settingsStore.setDarkTheme(dark)
}
