package com.example.domain.model

data class SoundingLevel(
    val pressureHpa: Double,
    val heightM: Double,
    val tempC: Double,
    val dewpointC: Double?,
    val windDirDeg: Double?,
    val windSpeedKt: Double?
)

data class ThermodynamicIndices(
    val sbcapeJkg: Int,
    val sbcinJkg: Int,
    val mlcapeJkg: Int,
    val liftedIndex: Double,
    val lclMeters: Int,
    val lclHpa: Int,
    val lfcMeters: Int?,
    val elMeters: Int?,
    val kIndex: Double,
    val totalTotals: Double,
    val pwatMm: Double,
    val shear01Kt: Int,
    val shear06Kt: Int,
    val srh03M2S2: Int,
    val lapseRate700500: Double,
    val freezingLevelM: Int,
    val maxWindKt: Int,
    val maxWindPressureHpa: Int
)

data class SoundingProfile(
    val stationId: String,
    val modelId: String = "RAOB",    // "RAOB", "GFS", "RRFS"
    val sourceLabel: String,         // e.g. "NWS RAOB (IEM)", "NOAA GFS 0.25°", "NOAA RRFS 3km"
    val validTimeUtcIso: String,
    val validEpochMs: Long,
    val fetchedEpochMs: Long,
    val isForecast: Boolean,
    val forecastHourOffset: Int,     // 0 for observed, 0..168 for forecast
    val levels: List<SoundingLevel>,
    val indices: ThermodynamicIndices,
    val nwsOfficeHeadline: String = ""
)

data class SyncLogEntry(
    val id: Long = 0,
    val timestampMs: Long,
    val stationId: String,
    val sourceType: String,          // "RAOB_OBS", "GFS_7DAY", "RRFS_3KM", "AUTO_WORK"
    val success: Boolean,
    val message: String,
    val levelsOrStepsCount: Int
)

data class UserSettings(
    val selectedStationId: String = "OUN",
    val pinnedStationIds: Set<String> = setOf("OUN", "OAX", "DNR", "OKX"),
    val customStations: Map<String, UpperAirStation> = emptyMap(),
    val dailyOfflineCacheEnabled: Boolean = true,
    val autoSyncEnabled: Boolean = true,
    val syncIntervalHours: Int = 12,
    val wifiOnly: Boolean = false,
    val useFahrenheit: Boolean = false,
    val useMph: Boolean = false,
    val darkTheme: Boolean = true,
    val lastSyncEpochMs: Long = 0L
)
