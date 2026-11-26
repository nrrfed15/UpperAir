package com.example

import com.example.data.remote.NwsSoundingClient
import com.example.domain.model.NwsStationsCatalog
import com.example.domain.model.SoundingLevel
import com.example.domain.physics.AtmosphericCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpperAirPhysicsAndParserTest {

    @Test
    fun thermodynamicCalculator_computesInstabilityAndShear() {
        val sampleLevels = listOf(
            SoundingLevel(975.0, 350.0, 29.0, 21.0, 160.0, 12.0),
            SoundingLevel(925.0, 810.0, 24.0, 18.0, 175.0, 22.0),
            SoundingLevel(850.0, 1520.0, 18.5, 13.0, 195.0, 32.0),
            SoundingLevel(700.0, 3140.0, 6.0, -4.0, 225.0, 42.0),
            SoundingLevel(500.0, 5860.0, -14.0, -25.0, 250.0, 55.0),
            SoundingLevel(300.0, 9550.0, -40.0, -52.0, 265.0, 75.0),
            SoundingLevel(200.0, 12200.0, -56.0, -68.0, 270.0, 82.0)
        )
        val idx = AtmosphericCalculator.computeIndices(sampleLevels)
        assertTrue("Expected positive SBCAPE > 500 J/kg, was ${idx.sbcapeJkg}", idx.sbcapeJkg > 500)
        assertTrue("Expected 0-6km shear > 35 kt, was ${idx.shear06Kt}", idx.shear06Kt > 35)
        assertTrue("Expected positive PWAT > 20 mm, was ${idx.pwatMm}", idx.pwatMm > 20.0)
        assertTrue("Expected freezing level between 3000m and 5000m, was ${idx.freezingLevelM}", idx.freezingLevelM in 3000..5000)
    }

    @Test
    fun iemRaobParser_parsesNestedProfile() {
        val json = """
            {
              "station": "KOUN",
              "valid": "2026-11-25T12:00:00Z",
              "profiles": [
                {
                  "station": "KOUN",
                  "valid": "2026-11-25T12:00:00Z",
                  "profile": [
                    {"pres": 970.0, "hght": 362.0, "tmpc": 14.2, "dwpc": 9.1, "drct": 170.0, "sknt": 10.0},
                    {"pres": 925.0, "hght": 780.0, "tmpc": 11.4, "dwpc": 6.0, "drct": 185.0, "sknt": 18.0},
                    {"pres": 850.0, "hght": 1480.0, "tmpc": 7.0, "dwpc": 1.2, "drct": 205.0, "sknt": 25.0},
                    {"pres": 700.0, "hght": 3080.0, "tmpc": -3.1, "dwpc": -11.0, "drct": 235.0, "sknt": 35.0},
                    {"pres": 500.0, "hght": 5720.0, "tmpc": -19.5, "dwpc": -30.0, "drct": 255.0, "sknt": 52.0}
                  ]
                }
              ]
            }
        """.trimIndent()
        val client = NwsSoundingClient()
        val station = NwsStationsCatalog.findById("OUN")
        val profile = client.parseIemRaobResponse(json, station, "2026-11-25T12:00:00Z", 1000L, "NWS OUN")
        assertNotNull(profile)
        assertEquals(5, profile!!.levels.size)
        assertEquals("OUN", profile.stationId)
        assertEquals(970.0, profile.levels.first().pressureHpa, 0.01)
    }

    @Test
    fun openMeteoGfsParser_parsesFullSevenDaysUpTo168Hours() {
        val hoursCount = 175
        val times = (0 until hoursCount).joinToString(",") { i ->
            val day = 25 + (i / 24)
            val hr = i % 24
            val dayStr = if (day <= 30) "2026-11-${day.toString().padStart(2, '0')}" else "2026-12-${(day - 30).toString().padStart(2, '0')}"
            "\"${dayStr}T${hr.toString().padStart(2, '0')}:00\""
        }
        fun repeatNum(v: String) = (0 until hoursCount).joinToString(",") { v }

        val levels = listOf(1000, 975, 950, 925, 900, 850, 800, 700, 600, 500, 400, 300, 250, 200, 150, 100)
        val levelFields = levels.joinToString(",") { p ->
            """
            "temperature_${p}hPa": [${repeatNum((20.0 - (1000 - p) * 0.08).toString())}],
            "relative_humidity_${p}hPa": [${repeatNum("65.0")}],
            "geopotential_height_${p}hPa": [${repeatNum(((1000 - p) * 14.0 + 100.0).toString())}],
            "wind_speed_${p}hPa": [${repeatNum((10.0 + (1000 - p) * 0.07).toString())}],
            "wind_direction_${p}hPa": [${repeatNum("230.0")}]
            """.trimIndent()
        }

        val json = """
            {
              "hourly": {
                "time": [$times],
                "temperature_2m": [${repeatNum("21.0")}],
                "dew_point_2m": [${repeatNum("15.0")}],
                "surface_pressure": [${repeatNum("970.0")}],
                "wind_speed_10m": [${repeatNum("12.0")}],
                "wind_direction_10m": [${repeatNum("175.0")}],
                "cape": [${repeatNum("850.0")}],
                "convective_inhibition": [${repeatNum("-25.0")}],
                "lifted_index": [${repeatNum("-3.2")}],
                "freezing_level_height": [${repeatNum("3400.0")}],
                $levelFields
              }
            }
        """.trimIndent()

        val client = NwsSoundingClient()
        val station = NwsStationsCatalog.findById("OUN")
        val profiles = client.parseOpenMeteoGfsResponse(json, station, 0L)
        assertTrue("Expected >= 50 3-hour profiles across 7 days, got ${profiles.size}", profiles.size >= 50)
        assertEquals(168, profiles.last().forecastHourOffset)
        assertTrue(profiles.first().levels.size >= 10)
    }

    @Test
    fun stationSearch_andNearestRaobLookup_workCorrectly() {
        val vefMatches = NwsStationsCatalog.searchMatches("Las Vegas")
        assertTrue(vefMatches.any { it.icao == "KVEF" })

        val oaxMatches = NwsStationsCatalog.searchMatches("KOAX")
        assertEquals("OAX", oaxMatches.first().id)

        // Austin, TX (30.267, -97.743) nearest NWS RAOB site should be FWD or CRP/DRT
        val nearestToAustin = NwsStationsCatalog.findNearestStation(30.267, -97.743)
        assertTrue(nearestToAustin.icao in setOf("KFWD", "KCRP", "KDRT"))
    }

    @Test
    fun openMeteoRrfsParser_parsesHighResFirstDayAndThreeHourTail() {
        val hoursCount = 86
        val times = (0 until hoursCount).joinToString(",") { i ->
            val day = 25 + (i / 24)
            val hr = i % 24
            "\"2026-11-${day.toString().padStart(2, '0')}T${hr.toString().padStart(2, '0')}:00\""
        }
        fun repeatNum(v: String) = (0 until hoursCount).joinToString(",") { v }

        val levels = listOf(1000, 975, 950, 925, 900, 850, 800, 700, 600, 500, 400, 300, 250, 200, 150, 100)
        val levelFields = levels.joinToString(",") { p ->
            """
            "temperature_${p}hPa": [${repeatNum((20.0 - (1000 - p) * 0.08).toString())}],
            "relative_humidity_${p}hPa": [${repeatNum("65.0")}],
            "geopotential_height_${p}hPa": [${repeatNum(((1000 - p) * 14.0 + 100.0).toString())}],
            "wind_speed_${p}hPa": [${repeatNum((10.0 + (1000 - p) * 0.07).toString())}],
            "wind_direction_${p}hPa": [${repeatNum("230.0")}]
            """.trimIndent()
        }

        val json = """
            {
              "hourly": {
                "time": [$times],
                "temperature_2m": [${repeatNum("22.0")}],
                "dew_point_2m": [${repeatNum("16.0")}],
                "surface_pressure": [${repeatNum("972.0")}],
                "wind_speed_10m": [${repeatNum("14.0")}],
                "wind_direction_10m": [${repeatNum("180.0")}],
                "cape": [${repeatNum("1250.0")}],
                "convective_inhibition": [${repeatNum("-15.0")}],
                "lifted_index": [${repeatNum("-4.0")}],
                "freezing_level_height": [${repeatNum("3550.0")}],
                $levelFields
              }
            }
        """.trimIndent()

        val client = NwsSoundingClient()
        val station = NwsStationsCatalog.findById("OUN")
        val profiles = client.parseOpenMeteoModelResponse(
            jsonStr = json,
            station = station,
            fetchedMs = 0L,
            modelId = "RRFS",
            sourceLabel = "NOAA RRFS 3km",
            maxOffset = 84,
            highResFirstDay = true
        )
        assertTrue("Expected hourly first 24h + 3h tail >= 40 profiles, got ${profiles.size}", profiles.size >= 40)
        assertEquals("RRFS", profiles.first().modelId)
        assertEquals(0, profiles[0].forecastHourOffset)
        assertEquals(1, profiles[1].forecastHourOffset)
        assertEquals(84, profiles.last().forecastHourOffset)
    }

    @Test
    fun iemRaobArchiveParser_parsesMultiplePastSoundingsChronologically() {
        val sampleLevelsJson = """
            [
              {"pres": 970.0, "hght": 362.0, "tmpc": 14.2, "dwpc": 9.1, "drct": 170.0, "sknt": 10.0},
              {"pres": 925.0, "hght": 780.0, "tmpc": 11.4, "dwpc": 6.0, "drct": 185.0, "sknt": 18.0},
              {"pres": 850.0, "hght": 1480.0, "tmpc": 7.0, "dwpc": 1.2, "drct": 205.0, "sknt": 25.0},
              {"pres": 700.0, "hght": 3080.0, "tmpc": -3.1, "dwpc": -11.0, "drct": 235.0, "sknt": 35.0},
              {"pres": 500.0, "hght": 5720.0, "tmpc": -19.5, "dwpc": -30.0, "drct": 255.0, "sknt": 52.0}
            ]
        """.trimIndent()
        val archiveJson = """
            {
              "station": "KOUN",
              "profiles": [
                {"station": "KOUN", "valid": "2026-11-23T00:00:00Z", "profile": $sampleLevelsJson},
                {"station": "KOUN", "valid": "2026-11-24T12:00:00Z", "profile": $sampleLevelsJson},
                {"station": "KOUN", "valid": "2026-11-25T12:00:00Z", "profile": $sampleLevelsJson}
              ]
            }
        """.trimIndent()

        val client = NwsSoundingClient()
        val station = NwsStationsCatalog.findById("OUN")
        val profiles = client.parseIemRaobArchiveResponse(
            jsonStr = archiveJson,
            station = station,
            fallbackIso = "2026-11-25T12:00:00Z",
            fetchedMs = 5000L,
            nwsOffice = "NWS OUN"
        )
        assertEquals(3, profiles.size)
        // Sorted newest-first
        assertEquals("2026-11-25T12:00:00Z", profiles[0].validTimeUtcIso)
        assertEquals("2026-11-24T12:00:00Z", profiles[1].validTimeUtcIso)
        assertEquals("2026-11-23T00:00:00Z", profiles[2].validTimeUtcIso)
    }

    @Test
    fun parcelTypes_mostUnstableFindsElevatedInstabilityAndComputesScpStp() {
        // Classic Plains supercell/tornado profile with strong veering 0-3km winds and low LCL
        val supercellLevels = listOf(
            SoundingLevel(975.0, 350.0, 27.0, 22.0, 140.0, 14.0),
            SoundingLevel(950.0, 580.0, 25.0, 20.5, 165.0, 24.0),
            SoundingLevel(900.0, 1060.0, 21.0, 17.0, 195.0, 34.0),
            SoundingLevel(850.0, 1560.0, 17.5, 13.5, 215.0, 42.0),
            SoundingLevel(700.0, 3160.0, 5.0, -5.0, 240.0, 52.0),
            SoundingLevel(500.0, 5860.0, -15.0, -28.0, 255.0, 68.0),
            SoundingLevel(300.0, 9550.0, -42.0, -54.0, 265.0, 88.0),
            SoundingLevel(200.0, 12200.0, -58.0, -70.0, 270.0, 95.0)
        )
        val sb = AtmosphericCalculator.computeParcelAnalysis(supercellLevels, com.example.domain.physics.ParcelType.SURFACE_BASED)
        val ml = AtmosphericCalculator.computeParcelAnalysis(supercellLevels, com.example.domain.physics.ParcelType.MIXED_LAYER)
        val mu = AtmosphericCalculator.computeParcelAnalysis(supercellLevels, com.example.domain.physics.ParcelType.MOST_UNSTABLE)
        val comp = AtmosphericCalculator.computeSevereComposites(supercellLevels)

        assertTrue("Expected positive SBCAPE, was ${sb.capeJkg}", sb.capeJkg > 1000)
        assertTrue("Expected positive MLCAPE, was ${ml.capeJkg}", ml.capeJkg > 500)
        assertTrue("Expected MUCAPE >= SBCAPE, was ${mu.capeJkg} vs ${sb.capeJkg}", mu.capeJkg >= sb.capeJkg)
        assertTrue("Expected positive 0-1km SRH > 100, was ${comp.srh01M2S2}", comp.srh01M2S2 > 100)
        assertTrue("Expected elevated SCP > 1.0, was ${comp.scp}", comp.scp > 1.0)
        assertTrue("Expected elevated STP > 0.5, was ${comp.stp}", comp.stp > 0.5)
        assertTrue("Expected positive EHI 0-1km > 0.5, was ${comp.ehi01}", comp.ehi01 > 0.5)

        // Elevated inversion sounding: cool stable surface, warm moist 850 hPa layer
        val elevatedLevels = listOf(
            SoundingLevel(980.0, 300.0, 10.0, 8.0, 90.0, 8.0),
            SoundingLevel(925.0, 800.0, 14.0, 11.0, 150.0, 18.0),
            SoundingLevel(850.0, 1500.0, 21.0, 18.0, 200.0, 35.0),
            SoundingLevel(700.0, 3100.0, 6.0, -2.0, 235.0, 45.0),
            SoundingLevel(500.0, 5800.0, -16.0, -26.0, 255.0, 60.0),
            SoundingLevel(300.0, 9450.0, -42.0, -54.0, 265.0, 78.0)
        )
        val sbElev = AtmosphericCalculator.computeParcelAnalysis(elevatedLevels, com.example.domain.physics.ParcelType.SURFACE_BASED)
        val muElev = AtmosphericCalculator.computeParcelAnalysis(elevatedLevels, com.example.domain.physics.ParcelType.MOST_UNSTABLE)
        assertTrue("Expected MUCAPE (${muElev.capeJkg}) > SBCAPE (${sbElev.capeJkg}) for elevated inversion", muElev.capeJkg > sbElev.capeJkg + 400)
    }

    @Test
    fun webMercator_roundTripProjectionIsAccurateAcrossUsa() {
        val testPoints = listOf(
            35.181 to -97.438,  // Norman, OK
            61.167 to -150.017, // Anchorage, AK
            21.307 to -157.858, // Honolulu, HI
            40.713 to -74.006   // New York, NY
        )
        for ((lat, lon) in testPoints) {
            val screen = com.example.ui.components.WebMercator.latLonToScreen(
                lat = lat,
                lon = lon,
                centerLat = 38.6,
                centerLon = -96.8,
                zoom = 4.5f,
                viewportWidthPx = 1080f,
                viewportHeightPx = 1920f
            )
            val (outLat, outLon) = com.example.ui.components.WebMercator.screenToLatLon(
                screenX = screen.x,
                screenY = screen.y,
                centerLat = 38.6,
                centerLon = -96.8,
                zoom = 4.5f,
                viewportWidthPx = 1080f,
                viewportHeightPx = 1920f
            )
            assertEquals("Lat mismatch for ($lat, $lon)", lat, outLat, 0.002)
            assertEquals("Lon mismatch for ($lat, $lon)", lon, outLon, 0.002)
        }
    }
}
