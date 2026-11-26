package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.UpperAirStation
import com.example.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.upperAirDataStore: DataStore<Preferences> by preferencesDataStore(name = "upperair_prefs")

class SettingsStore(private val context: Context) {
    private object Keys {
        val SELECTED_STATION = stringPreferencesKey("selected_station")
        val PINNED_STATIONS = stringSetPreferencesKey("pinned_stations")
        val CUSTOM_STATIONS_JSON = stringPreferencesKey("custom_stations_json")
        val DAILY_OFFLINE_CACHE = booleanPreferencesKey("daily_offline_cache")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val USE_FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
        val USE_MPH = booleanPreferencesKey("use_mph")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val LAST_SYNC_EPOCH = longPreferencesKey("last_sync_epoch")
    }

    val settingsFlow: Flow<UserSettings> = context.upperAirDataStore.data.map { prefs ->
        UserSettings(
            selectedStationId = prefs[Keys.SELECTED_STATION] ?: "OUN",
            pinnedStationIds = prefs[Keys.PINNED_STATIONS] ?: setOf("OUN", "OAX", "DNR", "OKX"),
            customStations = decodeCustomStations(prefs[Keys.CUSTOM_STATIONS_JSON]),
            dailyOfflineCacheEnabled = prefs[Keys.DAILY_OFFLINE_CACHE] ?: true,
            autoSyncEnabled = prefs[Keys.AUTO_SYNC_ENABLED] ?: true,
            syncIntervalHours = prefs[Keys.SYNC_INTERVAL_HOURS] ?: 12,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
            useFahrenheit = prefs[Keys.USE_FAHRENHEIT] ?: false,
            useMph = prefs[Keys.USE_MPH] ?: false,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            lastSyncEpochMs = prefs[Keys.LAST_SYNC_EPOCH] ?: 0L
        )
    }

    suspend fun currentSettings(): UserSettings = settingsFlow.first()

    suspend fun setSelectedStation(stationId: String) {
        context.upperAirDataStore.edit { prefs ->
            prefs[Keys.SELECTED_STATION] = stationId
        }
    }

    suspend fun saveCustomStation(station: UpperAirStation) {
        context.upperAirDataStore.edit { prefs ->
            val current = decodeCustomStations(prefs[Keys.CUSTOM_STATIONS_JSON]).toMutableMap()
            current[station.id] = station
            // Keep at most 20 most recent custom sites
            val trimmed = if (current.size > 20) {
                current.entries.toList().takeLast(20).associate { it.key to it.value }
            } else current
            prefs[Keys.CUSTOM_STATIONS_JSON] = encodeCustomStations(trimmed.values.toList())
        }
    }

    suspend fun togglePinnedStation(stationId: String) {
        context.upperAirDataStore.edit { prefs ->
            val current = prefs[Keys.PINNED_STATIONS]?.toMutableSet() ?: mutableSetOf("OUN", "OAX", "DNR", "OKX")
            if (current.contains(stationId)) {
                if (current.size > 1) current.remove(stationId)
            } else {
                current.add(stationId)
            }
            prefs[Keys.PINNED_STATIONS] = current
        }
    }

    suspend fun setDailyOfflineCacheEnabled(enabled: Boolean) {
        context.upperAirDataStore.edit { it[Keys.DAILY_OFFLINE_CACHE] = enabled }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.upperAirDataStore.edit { it[Keys.AUTO_SYNC_ENABLED] = enabled }
    }

    suspend fun setSyncIntervalHours(hours: Int) {
        context.upperAirDataStore.edit { it[Keys.SYNC_INTERVAL_HOURS] = hours }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        context.upperAirDataStore.edit { it[Keys.WIFI_ONLY] = wifiOnly }
    }

    suspend fun setUseFahrenheit(useF: Boolean) {
        context.upperAirDataStore.edit { it[Keys.USE_FAHRENHEIT] = useF }
    }

    suspend fun setUseMph(useMph: Boolean) {
        context.upperAirDataStore.edit { it[Keys.USE_MPH] = useMph }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.upperAirDataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun updateLastSyncEpoch(epochMs: Long) {
        context.upperAirDataStore.edit { it[Keys.LAST_SYNC_EPOCH] = epochMs }
    }

    private fun encodeCustomStations(stations: List<UpperAirStation>): String {
        val arr = JSONArray()
        for (st in stations) {
            val obj = JSONObject()
            obj.put("id", st.id)
            obj.put("icao", st.icao)
            obj.put("wmo", st.wmo)
            obj.put("name", st.name)
            obj.put("state", st.state)
            obj.put("region", st.region)
            obj.put("lat", st.lat)
            obj.put("lon", st.lon)
            obj.put("elev", st.elevationM)
            if (st.raobIcaoOverride != null) obj.put("raob", st.raobIcaoOverride)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeCustomStations(raw: String?): Map<String, UpperAirStation> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val arr = JSONArray(raw)
            val map = LinkedHashMap<String, UpperAirStation>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val st = UpperAirStation(
                    id = o.getString("id"),
                    icao = o.getString("icao"),
                    wmo = o.getString("wmo"),
                    name = o.getString("name"),
                    state = o.getString("state"),
                    region = o.getString("region"),
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    elevationM = o.getInt("elev"),
                    raobIcaoOverride = if (o.has("raob") && !o.isNull("raob")) o.getString("raob") else null
                )
                map[st.id] = st
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
