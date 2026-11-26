package com.example.data.remote

import com.example.domain.model.NwsStationsCatalog
import com.example.domain.model.SoundingLevel
import com.example.domain.model.SoundingProfile
import com.example.domain.model.UpperAirStation
import com.example.domain.physics.AtmosphericCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class NwsSoundingClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val USER_AGENT = "UpperAirViewer/1.0 (android-sounding-client)"
        private val GFS_PRESSURE_LEVELS = listOf(
            1000, 975, 950, 925, 900, 850, 800, 700, 600, 500, 400, 300, 250, 200, 150, 100
        )
    }

    suspend fun fetchLatestObservedSounding(station: UpperAirStation): Result<SoundingProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val nowMs = System.currentTimeMillis()
                val candidates = recentSynopticTimestamps(nowMs)
                val raobCode = station.effectiveRaobIcao
                val raobShort = if (raobCode.length == 4 && raobCode.startsWith("K")) raobCode.drop(1) else raobCode
                val stationCodes = listOf(raobCode, raobShort).distinct()
                val nwsOffice = fetchNwsPointOffice(station)

                var lastError: String? = null
                for (ts in candidates) {
                    for (code in stationCodes) {
                        val url = "https://mesonet.agron.iastate.edu/json/raob.py?station=$code&ts=$ts"
                        val body = executeGet(url) ?: continue
                        val parsed = parseIemRaobResponse(
                            jsonStr = body,
                            station = station,
                            fallbackIso = formatSynopticIso(ts),
                            fetchedMs = nowMs,
                            nwsOffice = nwsOffice
                        )
                        if (parsed != null && parsed.levels.size >= 5) {
                            return@runCatching parsed
                        } else {
                            lastError = "Empty RAOB profile for $code at $ts"
                        }
                    }
                }
                throw IllegalStateException(
                    lastError ?: "No recent NWS RAOB observation available for $raobCode"
                )
            }
        }

    suspend fun fetchPastObservedSoundings(
        station: UpperAirStation,
        daysBack: Int = 7
    ): Result<List<SoundingProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            val nowMs = System.currentTimeMillis()
            val startMs = nowMs - daysBack.coerceIn(1, 30) * 24L * 3600L * 1000L
            val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val sts = isoFmt.format(java.util.Date(startMs))
            val ets = isoFmt.format(java.util.Date(nowMs))

            val raobCode = station.effectiveRaobIcao
            val raobShort = if (raobCode.length == 4 && raobCode.startsWith("K")) raobCode.drop(1) else raobCode
            val stationCodes = listOf(raobCode, raobShort).distinct()
            val nwsOffice = fetchNwsPointOffice(station)

            // Primary: single range query via IEM sts/ets archive parameters
            for (code in stationCodes) {
                val url = "https://mesonet.agron.iastate.edu/json/raob.py?station=$code&sts=$sts&ets=$ets"
                val body = executeGet(url) ?: continue
                val parsed = parseIemRaobArchiveResponse(
                    jsonStr = body,
                    station = station,
                    fallbackIso = ets,
                    fetchedMs = nowMs,
                    nwsOffice = nwsOffice
                )
                if (parsed.isNotEmpty()) {
                    return@runCatching parsed
                }
            }

            // Fallback: step through recent synoptic 00Z/12Z timestamps
            val collected = mutableListOf<SoundingProfile>()
            val candidates = recentSynopticTimestamps(nowMs, count = (daysBack * 2).coerceIn(4, 14))
            for (ts in candidates) {
                for (code in stationCodes) {
                    val url = "https://mesonet.agron.iastate.edu/json/raob.py?station=$code&ts=$ts"
                    val body = executeGet(url) ?: continue
                    val parsed = parseIemRaobResponse(
                        jsonStr = body,
                        station = station,
                        fallbackIso = formatSynopticIso(ts),
                        fetchedMs = nowMs,
                        nwsOffice = nwsOffice
                    )
                    if (parsed != null && parsed.levels.size >= 5) {
                        collected.add(parsed)
                        break
                    }
                }
            }
            if (collected.isEmpty()) {
                throw IllegalStateException("No past RAOB archive data found for $raobCode")
            }
            collected.distinctBy { it.validEpochMs }.sortedByDescending { it.validEpochMs }
        }
    }

    suspend fun fetchObservedSoundingAt(
        station: UpperAirStation,
        utcDateMidnightMs: Long,
        synopticHourUtc: Int
    ): Result<SoundingProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val nowMs = System.currentTimeMillis()
            val safeHour = if (synopticHourUtc >= 12) 12 else 0
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = utcDateMidnightMs
                set(Calendar.HOUR_OF_DAY, safeHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val fmt = SimpleDateFormat("yyyyMMddHHmm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val ts = fmt.format(cal.time)
            val raobCode = station.effectiveRaobIcao
            val raobShort = if (raobCode.length == 4 && raobCode.startsWith("K")) raobCode.drop(1) else raobCode
            val stationCodes = listOf(raobCode, raobShort).distinct()
            val nwsOffice = fetchNwsPointOffice(station)

            for (code in stationCodes) {
                val url = "https://mesonet.agron.iastate.edu/json/raob.py?station=$code&ts=$ts"
                val body = executeGet(url) ?: continue
                val parsed = parseIemRaobResponse(
                    jsonStr = body,
                    station = station,
                    fallbackIso = formatSynopticIso(ts),
                    fetchedMs = nowMs,
                    nwsOffice = nwsOffice
                )
                if (parsed != null && parsed.levels.size >= 5) {
                    return@runCatching parsed
                }
            }
            throw IllegalStateException(
                "No RAOB launch found for $raobCode at ${formatSynopticIso(ts)}"
            )
        }
    }

    suspend fun resolveSiteQuery(query: String): Result<UpperAirStation> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clean = query.trim()
                if (clean.isEmpty()) throw IllegalArgumentException("Enter a station ID or city name")

                // 1. Direct NWS catalog match by ID / ICAO / WMO / exact city
                NwsStationsCatalog.findByIdOrNull(clean)?.let { return@runCatching it }
                val catalogMatches = NwsStationsCatalog.searchMatches(clean, limit = 5)
                if (catalogMatches.size == 1 && clean.length >= 3) {
                    return@runCatching catalogMatches.first()
                }

                // 2. If query looks like a station code (e.g. KVEF, BOS, KDEN), query NWS stations API
                if (clean.matches(Regex("^[A-Za-z0-9]{3,4}$"))) {
                    val code = clean.uppercase(Locale.US)
                    val codeCandidates = if (code.length == 3) listOf("K$code", code) else listOf(code)
                    for (c in codeCandidates) {
                        val st = fetchNwsStationByCode(c)
                        if (st != null) return@runCatching st
                    }
                }

                // 3. Otherwise geocode city / location name via Open-Meteo Geocoding API
                val geoMatches = searchLocationsOnlineInternal(clean)
                if (geoMatches.isNotEmpty()) {
                    return@runCatching geoMatches.first()
                }

                // 4. Fallback to first partial catalog match if geocoding returned nothing
                if (catalogMatches.isNotEmpty()) {
                    return@runCatching catalogMatches.first()
                }

                throw IllegalStateException("No station or location found for \"$clean\"")
            }
        }

    suspend fun searchLocationsOnline(query: String): List<UpperAirStation> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.length < 2) return@withContext emptyList()
            searchLocationsOnlineInternal(clean)
        }

    fun createImmediateMapStation(lat: Double, lon: Double): UpperAirStation {
        val nearestRaob = NwsStationsCatalog.findNearestStation(lat, lon)
        val id = buildMapPointId(lat, lon)
        val coordLabel = formatCoordinateBrief(lat, lon)
        return UpperAirStation(
            id = id,
            icao = "RRFS",
            wmo = "MAP",
            name = coordLabel,
            state = nearestRaob.state,
            region = "Custom",
            lat = lat,
            lon = lon,
            elevationM = nearestRaob.elevationM,
            raobIcaoOverride = nearestRaob.icao
        )
    }

    suspend fun resolveMapPoint(lat: Double, lon: Double): UpperAirStation =
        withContext(Dispatchers.IO) {
            val base = createImmediateMapStation(lat, lon)
            try {
                val url = String.format(Locale.US, "https://api.weather.gov/points/%.4f,%.4f", lat, lon)
                val body = executeGet(url) ?: return@withContext base
                val root = JSONObject(body)
                val props = root.optJSONObject("properties") ?: return@withContext base
                val cwa = props.optString("cwa", "").trim()
                val relProps = props.optJSONObject("relativeLocation")?.optJSONObject("properties")
                val city = relProps?.optString("city", "")?.trim().orEmpty()
                val state = relProps?.optString("state", "")?.trim().orEmpty()
                val resolvedName = when {
                    city.isNotBlank() -> city
                    cwa.isNotBlank() -> "NWS $cwa Sector"
                    else -> base.name
                }
                val resolvedState = state.ifBlank { base.state }
                val resolvedIcao = if (cwa.isNotBlank()) "WFO-$cwa" else "RRFS"
                base.copy(
                    icao = resolvedIcao,
                    name = resolvedName,
                    state = resolvedState
                )
            } catch (_: Exception) {
                base
            }
        }

    private fun buildMapPointId(lat: Double, lon: Double): String {
        val latInt = (kotlin.math.abs(lat) * 100.0).roundToInt()
        val lonInt = (kotlin.math.abs(lon) * 100.0).roundToInt()
        val ns = if (lat >= 0.0) "N" else "S"
        val ew = if (lon >= 0.0) "E" else "W"
        return "MAP_${latInt}${ns}_${lonInt}${ew}"
    }

    private fun formatCoordinateBrief(lat: Double, lon: Double): String {
        val ns = if (lat >= 0.0) "N" else "S"
        val ew = if (lon >= 0.0) "E" else "W"
        return String.format(Locale.US, "%.2f°%s, %.2f°%s", kotlin.math.abs(lat), ns, kotlin.math.abs(lon), ew)
    }

    private fun fetchNwsStationByCode(icaoCode: String): UpperAirStation? {
        return try {
            val body = executeGet("https://api.weather.gov/stations/$icaoCode") ?: return null
            val root = JSONObject(body)
            val geom = root.optJSONObject("geometry") ?: return null
            val coords = geom.optJSONArray("coordinates") ?: return null
            if (coords.length() < 2) return null
            val lon = coords.getDouble(0)
            val lat = coords.getDouble(1)
            val props = root.optJSONObject("properties")
            val rawName = props?.optString("name", icaoCode)?.ifBlank { icaoCode } ?: icaoCode
            val elevMeters = props?.optJSONObject("elevation")?.optDouble("value", 0.0)?.roundToInt() ?: 0
            val nearestRaob = NwsStationsCatalog.findNearestStation(lat, lon)
            UpperAirStation(
                id = icaoCode,
                icao = icaoCode,
                wmo = "NWS",
                name = rawName.substringBefore(",").trim(),
                state = "Site",
                region = "Custom",
                lat = lat,
                lon = lon,
                elevationM = elevMeters,
                raobIcaoOverride = nearestRaob.icao
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun searchLocationsOnlineInternal(query: String): List<UpperAirStation> {
        return try {
            val parts = query.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val cityPart = parts.firstOrNull() ?: query
            val qualifier = parts.drop(1).joinToString(" ").trim()

            val encoded = URLEncoder.encode(cityPart, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=6&language=en&format=json"
            val body = executeGet(url) ?: return emptyList()
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: return emptyList()

            val parsed = mutableListOf<UpperAirStation>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val name = item.optString("name", "").ifBlank { continue }
                val lat = item.optDouble("latitude", Double.NaN)
                val lon = item.optDouble("longitude", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite()) continue
                val elev = item.optDouble("elevation", 0.0).roundToInt()
                val admin1 = item.optString("admin1", "")
                val countryCode = item.optString("country_code", "")
                val stateLabel = when {
                    countryCode == "US" && admin1.isNotBlank() -> abbreviateUsState(admin1)
                    admin1.isNotBlank() -> admin1.take(12)
                    else -> countryCode
                }
                val nearestRaob = NwsStationsCatalog.findNearestStation(lat, lon)
                val safeId = "LOC_${name.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "").take(6)}_${(lat * 10).roundToInt()}"
                parsed.add(
                    UpperAirStation(
                        id = safeId,
                        icao = safeId,
                        wmo = "GEO",
                        name = name,
                        state = stateLabel,
                        region = "Custom",
                        lat = lat,
                        lon = lon,
                        elevationM = elev,
                        raobIcaoOverride = nearestRaob.icao
                    )
                )
            }

            // Rank US matches and state/qualifier matches first
            parsed.sortedWith(
                compareByDescending<UpperAirStation> { st ->
                    qualifier.isNotBlank() && (st.state.equals(qualifier, ignoreCase = true) || st.state.contains(qualifier, ignoreCase = true))
                }.thenByDescending { st ->
                    st.state.length == 2 // US state abbreviation
                }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun abbreviateUsState(stateName: String): String {
        val map = mapOf(
            "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR",
            "California" to "CA", "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE",
            "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI", "Idaho" to "ID",
            "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA", "Kansas" to "KS",
            "Kentucky" to "KY", "Louisiana" to "LA", "Maine" to "ME", "Maryland" to "MD",
            "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN", "Mississippi" to "MS",
            "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE", "Nevada" to "NV",
            "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM", "New York" to "NY",
            "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK",
            "Oregon" to "OR", "Pennsylvania" to "PA", "Rhode Island" to "RI", "South Carolina" to "SC",
            "South Dakota" to "SD", "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT",
            "Vermont" to "VT", "Virginia" to "VA", "Washington" to "WA", "West Virginia" to "WV",
            "Wisconsin" to "WI", "Wyoming" to "WY", "District of Columbia" to "DC"
        )
        return map[stateName] ?: stateName.take(10)
    }

    private fun buildHourlyPressureVars(): String = buildList {
        add("temperature_2m")
        add("dew_point_2m")
        add("surface_pressure")
        add("wind_speed_10m")
        add("wind_direction_10m")
        add("cape")
        add("convective_inhibition")
        add("lifted_index")
        add("freezing_level_height")
        for (p in GFS_PRESSURE_LEVELS) {
            add("temperature_${p}hPa")
            add("relative_humidity_${p}hPa")
            add("geopotential_height_${p}hPa")
            add("wind_speed_${p}hPa")
            add("wind_direction_${p}hPa")
        }
    }.joinToString(",")

    suspend fun fetchSevenDayForecastSoundings(station: UpperAirStation): Result<List<SoundingProfile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val hourlyVars = buildHourlyPressureVars()
                val url = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/gfs?latitude=%.4f&longitude=%.4f&hourly=%s&forecast_days=8&wind_speed_unit=kn&timezone=UTC",
                    station.lat,
                    station.lon,
                    hourlyVars
                )

                val body = executeGet(url)
                    ?: throw IllegalStateException("Failed to fetch NOAA GFS 7-day forecast for ${station.id}")
                val profiles = parseOpenMeteoGfsResponse(body, station, System.currentTimeMillis())
                if (profiles.isEmpty()) {
                    throw IllegalStateException("NOAA GFS response contained no valid hourly profiles")
                }
                profiles
            }
        }

    suspend fun fetchRrfsForecastSoundings(station: UpperAirStation): Result<List<SoundingProfile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val hourlyVars = buildHourlyPressureVars()
                val candidateEndpoints = listOf(
                    Triple(
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&hourly=%s&models=ncep_rrfs_conus&forecast_days=4&wind_speed_unit=kn&timezone=UTC",
                        "NOAA RRFS 3km",
                        84
                    ),
                    Triple(
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&hourly=%s&models=rrfs_conus&forecast_days=4&wind_speed_unit=kn&timezone=UTC",
                        "NOAA RRFS 3km",
                        84
                    ),
                    Triple(
                        "https://api.open-meteo.com/v1/gfs?latitude=%.4f&longitude=%.4f&hourly=%s&models=ncep_hrrr_conus&forecast_days=3&wind_speed_unit=kn&timezone=UTC",
                        "NOAA RRFS/HRRR 3km",
                        48
                    )
                )

                for ((template, label, maxHr) in candidateEndpoints) {
                    val url = String.format(Locale.US, template, station.lat, station.lon, hourlyVars)
                    val body = executeGet(url) ?: continue
                    val profiles = parseOpenMeteoModelResponse(
                        jsonStr = body,
                        station = station,
                        fetchedMs = System.currentTimeMillis(),
                        modelId = "RRFS",
                        sourceLabel = label,
                        maxOffset = maxHr,
                        highResFirstDay = true
                    )
                    if (profiles.isNotEmpty()) {
                        return@runCatching profiles
                    }
                }
                throw IllegalStateException("NOAA RRFS 3km profile unavailable for ${station.name}")
            }
        }

    private fun fetchNwsPointOffice(station: UpperAirStation): String {
        return try {
            val url = String.format(Locale.US, "https://api.weather.gov/points/%.4f,%.4f", station.lat, station.lon)
            val body = executeGet(url) ?: return ""
            val root = JSONObject(body)
            val props = root.optJSONObject("properties") ?: return ""
            val cwa = props.optString("cwa", "")
            val relLoc = props.optJSONObject("relativeLocation")?.optJSONObject("properties")
            val city = relLoc?.optString("city", "") ?: ""
            val st = relLoc?.optString("state", "") ?: ""
            if (cwa.isNotBlank() && city.isNotBlank()) "NWS $cwa ($city, $st)"
            else if (cwa.isNotBlank()) "NWS WFO $cwa"
            else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun executeGet(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun recentSynopticTimestamps(nowMs: Long, count: Int = 4): List<String> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = nowMs
            val hour = get(Calendar.HOUR_OF_DAY)
            val synopticHour = if (hour >= 12) 12 else 0
            set(Calendar.HOUR_OF_DAY, synopticHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fmt = SimpleDateFormat("yyyyMMddHHmm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val list = mutableListOf<String>()
        repeat(count) {
            list.add(fmt.format(cal.time))
            cal.add(Calendar.HOUR_OF_DAY, -12)
        }
        return list
    }

    private fun formatSynopticIso(ts: String): String {
        return if (ts.length >= 10) {
            "${ts.substring(0, 4)}-${ts.substring(4, 6)}-${ts.substring(6, 8)}T${ts.substring(8, 10)}:00:00Z"
        } else ts
    }

    private fun parseIsoToEpochMs(iso: String): Long {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(iso)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }

    internal fun parseIemRaobResponse(
        jsonStr: String,
        station: UpperAirStation,
        fallbackIso: String,
        fetchedMs: Long,
        nwsOffice: String
    ): SoundingProfile? = parseIemRaobArchiveResponse(
        jsonStr = jsonStr,
        station = station,
        fallbackIso = fallbackIso,
        fetchedMs = fetchedMs,
        nwsOffice = nwsOffice
    ).firstOrNull()

    internal fun parseIemRaobArchiveResponse(
        jsonStr: String,
        station: UpperAirStation,
        fallbackIso: String,
        fetchedMs: Long,
        nwsOffice: String
    ): List<SoundingProfile> {
        val root = JSONObject(jsonStr)
        val profilesArr = root.optJSONArray("profiles") ?: return emptyList()
        if (profilesArr.length() == 0) return emptyList()

        val rootValidIso = root.optString("valid", fallbackIso).ifBlank { fallbackIso }
        val firstObj = profilesArr.optJSONObject(0) ?: return emptyList()
        val raobLabel = if (station.raobIcaoOverride != null) {
            "Nearest NWS RAOB (${station.effectiveRaobIcao})"
        } else {
            "NWS RAOB (${station.icao})"
        }

        val out = mutableListOf<SoundingProfile>()
        if (firstObj.has("profile")) {
            for (pIdx in 0 until profilesArr.length()) {
                val entryObj = profilesArr.optJSONObject(pIdx) ?: continue
                val validIso = entryObj.optString("valid", rootValidIso).ifBlank { rootValidIso }
                val levelsArr = entryObj.optJSONArray("profile") ?: continue
                parseRaobLevelsArray(levelsArr)?.let { sorted ->
                    out.add(
                        SoundingProfile(
                            stationId = station.id,
                            modelId = "RAOB",
                            sourceLabel = raobLabel,
                            validTimeUtcIso = validIso,
                            validEpochMs = parseIsoToEpochMs(validIso),
                            fetchedEpochMs = fetchedMs,
                            isForecast = false,
                            forecastHourOffset = 0,
                            levels = sorted,
                            indices = AtmosphericCalculator.computeIndices(sorted),
                            nwsOfficeHeadline = nwsOffice
                        )
                    )
                }
            }
        } else {
            parseRaobLevelsArray(profilesArr)?.let { sorted ->
                out.add(
                    SoundingProfile(
                        stationId = station.id,
                        modelId = "RAOB",
                        sourceLabel = raobLabel,
                        validTimeUtcIso = rootValidIso,
                        validEpochMs = parseIsoToEpochMs(rootValidIso),
                        fetchedEpochMs = fetchedMs,
                        isForecast = false,
                        forecastHourOffset = 0,
                        levels = sorted,
                        indices = AtmosphericCalculator.computeIndices(sorted),
                        nwsOfficeHeadline = nwsOffice
                    )
                )
            }
        }

        return out.distinctBy { it.validEpochMs }.sortedByDescending { it.validEpochMs }
    }

    private fun parseRaobLevelsArray(levelsArr: JSONArray): List<SoundingLevel>? {
        val levels = mutableListOf<SoundingLevel>()
        for (i in 0 until levelsArr.length()) {
            val row = levelsArr.optJSONObject(i) ?: continue
            val pres = optFiniteDouble(row, "pres", "pressure", "p") ?: continue
            val tempC = optFiniteDouble(row, "tmpc", "temp", "t") ?: continue
            val hght = optFiniteDouble(row, "hght", "height", "z") ?: continue
            if (pres !in 10.0..1080.0 || tempC < -110.0 || tempC > 65.0) continue

            val dwpc = optFiniteDouble(row, "dwpc", "dwpt", "td")
                ?.takeIf { it in -120.0..50.0 }
            val drct = optFiniteDouble(row, "drct", "wdir", "wd")
                ?.takeIf { it in 0.0..360.0 }
            val sknt = optFiniteDouble(row, "sknt", "wspd", "ws")
                ?.takeIf { it in 0.0..350.0 }

            levels.add(
                SoundingLevel(
                    pressureHpa = pres,
                    heightM = hght,
                    tempC = tempC,
                    dewpointC = dwpc,
                    windDirDeg = drct,
                    windSpeedKt = sknt
                )
            )
        }
        if (levels.size < 5) return null
        return levels.distinctBy { it.pressureHpa }.sortedByDescending { it.pressureHpa }
    }

    internal fun parseOpenMeteoGfsResponse(
        jsonStr: String,
        station: UpperAirStation,
        fetchedMs: Long
    ): List<SoundingProfile> = parseOpenMeteoModelResponse(
        jsonStr = jsonStr,
        station = station,
        fetchedMs = fetchedMs,
        modelId = "GFS",
        sourceLabel = "NOAA GFS 0.25°",
        maxOffset = 168,
        highResFirstDay = false
    )

    internal fun parseOpenMeteoModelResponse(
        jsonStr: String,
        station: UpperAirStation,
        fetchedMs: Long,
        modelId: String,
        sourceLabel: String,
        maxOffset: Int,
        highResFirstDay: Boolean
    ): List<SoundingProfile> {
        val root = JSONObject(jsonStr)
        val hourly = root.optJSONObject("hourly") ?: return emptyList()
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val totalHours = times.length()
        if (totalHours == 0) return emptyList()

        // Find current-hour anchor index so hourOffset=0 starts around now
        val nowMs = fetchedMs
        var startIdx = 0
        var bestDiff = Long.MAX_VALUE
        for (i in 0 until minOf(totalHours, 48)) {
            val tMs = parseIsoToEpochMs(times.getString(i))
            val diff = kotlin.math.abs(tMs - nowMs)
            if (diff < bestDiff) {
                bestDiff = diff
                startIdx = i
            }
        }

        val t2m = hourly.optJSONArray("temperature_2m")
        val td2m = hourly.optJSONArray("dew_point_2m")
        val spArr = hourly.optJSONArray("surface_pressure")
        val ws10 = hourly.optJSONArray("wind_speed_10m")
        val wd10 = hourly.optJSONArray("wind_direction_10m")
        val capeArr = hourly.optJSONArray("cape")
        val cinArr = hourly.optJSONArray("convective_inhibition")
        val liArr = hourly.optJSONArray("lifted_index")
        val frzArr = hourly.optJSONArray("freezing_level_height")

        val tempArrays = GFS_PRESSURE_LEVELS.associateWith { hourly.optJSONArray("temperature_${it}hPa") }
        val rhArrays = GFS_PRESSURE_LEVELS.associateWith { hourly.optJSONArray("relative_humidity_${it}hPa") }
        val hgtArrays = GFS_PRESSURE_LEVELS.associateWith { hourly.optJSONArray("geopotential_height_${it}hPa") }
        val wspdArrays = GFS_PRESSURE_LEVELS.associateWith { hourly.optJSONArray("wind_speed_${it}hPa") }
        val wdirArrays = GFS_PRESSURE_LEVELS.associateWith { hourly.optJSONArray("wind_direction_${it}hPa") }

        val profiles = mutableListOf<SoundingProfile>()

        var offset = 0
        while (offset <= maxOffset) {
            val idx = startIdx + offset
            if (idx >= totalHours) break

            val isoTime = times.getString(idx).let { if (it.endsWith("Z")) it else "${it}:00Z" }
            val validMs = parseIsoToEpochMs(isoTime)

            val sfcP = arrayDouble(spArr, idx) ?: 1013.25
            val sfcT = arrayDouble(t2m, idx)
            val sfcTd = arrayDouble(td2m, idx)
            val sfcWs = arrayDouble(ws10, idx)
            val sfcWd = arrayDouble(wd10, idx)

            val levels = mutableListOf<SoundingLevel>()
            if (sfcT != null) {
                levels.add(
                    SoundingLevel(
                        pressureHpa = sfcP,
                        heightM = station.elevationM.toDouble(),
                        tempC = sfcT,
                        dewpointC = sfcTd,
                        windDirDeg = sfcWd,
                        windSpeedKt = sfcWs
                    )
                )
            }

            for (p in GFS_PRESSURE_LEVELS) {
                if (p.toDouble() >= sfcP - 5.0) continue
                val t = arrayDouble(tempArrays[p], idx) ?: continue
                val h = arrayDouble(hgtArrays[p], idx) ?: continue
                val rh = arrayDouble(rhArrays[p], idx)
                val td = rh?.let { AtmosphericCalculator.dewpointFromTempAndRh(t, it) }
                val ws = arrayDouble(wspdArrays[p], idx)
                val wd = arrayDouble(wdirArrays[p], idx)

                levels.add(
                    SoundingLevel(
                        pressureHpa = p.toDouble(),
                        heightM = h,
                        tempC = t,
                        dewpointC = td,
                        windDirDeg = wd,
                        windSpeedKt = ws
                    )
                )
            }

            if (levels.size >= 5) {
                val sorted = levels.sortedByDescending { it.pressureHpa }
                val modelCape = arrayDouble(capeArr, idx)?.toInt()
                val modelCin = arrayDouble(cinArr, idx)?.toInt()?.let { if (it > 0) -it else it }
                val modelLi = arrayDouble(liArr, idx)
                val modelFrz = arrayDouble(frzArr, idx)?.toInt()

                val indices = AtmosphericCalculator.computeIndices(
                    levels = sorted,
                    modelCapeOverride = modelCape,
                    modelCinOverride = modelCin,
                    modelLiOverride = modelLi,
                    modelFreezingOverrideM = modelFrz
                )

                profiles.add(
                    SoundingProfile(
                        stationId = station.id,
                        modelId = modelId,
                        sourceLabel = sourceLabel,
                        validTimeUtcIso = isoTime,
                        validEpochMs = validMs,
                        fetchedEpochMs = fetchedMs,
                        isForecast = true,
                        forecastHourOffset = offset,
                        levels = sorted,
                        indices = indices
                    )
                )
            }

            offset += if (highResFirstDay && offset < 24) 1 else 3
        }

        return profiles
    }

    private fun optFiniteDouble(obj: JSONObject, vararg keys: String): Double? {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val v = obj.optDouble(k, Double.NaN)
                if (v.isFinite() && v > -9000.0) return v
            }
        }
        return null
    }

    private fun arrayDouble(arr: JSONArray?, index: Int): Double? {
        if (arr == null || index < 0 || index >= arr.length() || arr.isNull(index)) return null
        val v = arr.optDouble(index, Double.NaN)
        return if (v.isFinite()) v else null
    }
}
