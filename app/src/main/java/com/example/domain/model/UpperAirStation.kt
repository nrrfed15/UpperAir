package com.example.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class UpperAirStation(
    val id: String,          // 3/4-letter ID e.g. "OAX" or custom site key
    val icao: String,        // 4-letter ICAO e.g. "KOAX"
    val wmo: String,         // WMO block+station e.g. "72558" or "LOC" for custom city
    val name: String,        // City / Site name
    val state: String,       // US State / Region code
    val region: String,      // Plains, Midwest, Northeast, Southeast, West, Mountain, Non-CONUS, Custom
    val lat: Double,
    val lon: Double,
    val elevationM: Int,
    val raobIcaoOverride: String? = null // Nearest NWS RAOB site when querying an arbitrary city
) {
    val effectiveRaobIcao: String get() = raobIcaoOverride ?: icao
}

object NwsStationsCatalog {
    val stations: List<UpperAirStation> = listOf(
        // Plains
        UpperAirStation("OAX", "KOAX", "72558", "Omaha / Valley", "NE", "Plains", 41.320, -96.366, 350),
        UpperAirStation("OUN", "KOUN", "72357", "Norman", "OK", "Plains", 35.181, -97.438, 362),
        UpperAirStation("TOP", "KTOP", "72456", "Topeka", "KS", "Plains", 39.073, -95.626, 268),
        UpperAirStation("DDC", "KDDC", "72451", "Dodge City", "KS", "Plains", 37.761, -99.969, 790),
        UpperAirStation("LBF", "KLBF", "72562", "North Platte", "NE", "Plains", 41.133, -100.683, 849),
        UpperAirStation("AMA", "KAMA", "72363", "Amarillo", "TX", "Plains", 35.233, -101.709, 1099),
        UpperAirStation("MAF", "KMAF", "72265", "Midland", "TX", "Plains", 31.943, -102.189, 873),
        UpperAirStation("FWD", "KFWD", "72249", "Fort Worth", "TX", "Plains", 32.835, -97.298, 196),
        UpperAirStation("CRP", "KCRP", "72251", "Corpus Christi", "TX", "Plains", 27.770, -97.501, 14),
        UpperAirStation("BRO", "KBRO", "72250", "Brownsville", "TX", "Plains", 25.916, -97.419, 7),
        UpperAirStation("DRT", "KDRT", "72261", "Del Rio", "TX", "Plains", 29.373, -100.917, 314),
        UpperAirStation("ABR", "KABR", "72659", "Aberdeen", "SD", "Plains", 45.456, -98.413, 397),
        UpperAirStation("UNR", "KUNR", "72662", "Rapid City", "SD", "Plains", 44.073, -103.211, 1037),
        UpperAirStation("BIS", "KBIS", "72764", "Bismarck", "ND", "Plains", 46.771, -100.757, 506),

        // Midwest
        UpperAirStation("SGF", "KSGF", "72440", "Springfield", "MO", "Midwest", 37.237, -93.390, 394),
        UpperAirStation("ILX", "KILX", "74560", "Lincoln", "IL", "Midwest", 40.151, -89.337, 178),
        UpperAirStation("DVN", "KDVN", "74455", "Davenport", "IA", "Midwest", 41.612, -90.581, 229),
        UpperAirStation("MPX", "KMPX", "72649", "Chanhassen / Twin Cities", "MN", "Midwest", 44.849, -93.565, 287),
        UpperAirStation("INL", "KINL", "72747", "International Falls", "MN", "Midwest", 48.561, -93.398, 360),
        UpperAirStation("GRB", "KGRB", "72645", "Green Bay", "WI", "Midwest", 44.498, -88.111, 214),
        UpperAirStation("APX", "KAPX", "72634", "Gaylord", "MI", "Midwest", 44.907, -84.720, 446),
        UpperAirStation("DTX", "KDTX", "72632", "White Lake / Detroit", "MI", "Midwest", 42.700, -83.472, 329),
        UpperAirStation("ILN", "KILN", "72426", "Wilmington", "OH", "Midwest", 39.420, -83.822, 322),

        // Southeast
        UpperAirStation("BNA", "KBNA", "72327", "Nashville", "TN", "Southeast", 36.247, -86.563, 180),
        UpperAirStation("LZK", "KLZK", "72340", "Little Rock", "AR", "Southeast", 34.836, -92.262, 172),
        UpperAirStation("SHV", "KSHV", "72248", "Shreveport", "LA", "Southeast", 32.451, -93.841, 84),
        UpperAirStation("LCH", "KLCH", "72240", "Lake Charles", "LA", "Southeast", 30.125, -93.216, 10),
        UpperAirStation("LIX", "KLIX", "72233", "Slidell / New Orleans", "LA", "Southeast", 30.337, -89.825, 11),
        UpperAirStation("JAN", "KJAN", "72235", "Jackson", "MS", "Southeast", 32.318, -90.080, 91),
        UpperAirStation("BMX", "KBMX", "72230", "Birmingham / Shelby", "AL", "Southeast", 33.172, -86.770, 178),
        UpperAirStation("FFC", "KFFC", "72215", "Peachtree City / Atlanta", "GA", "Southeast", 33.363, -84.566, 246),
        UpperAirStation("TLH", "KTLH", "72214", "Tallahassee", "FL", "Southeast", 30.396, -84.350, 21),
        UpperAirStation("JAX", "KJAX", "72206", "Jacksonville", "FL", "Southeast", 30.484, -81.702, 10),
        UpperAirStation("TBW", "KTBW", "72210", "Tampa / Ruskin", "FL", "Southeast", 27.705, -82.401, 13),
        UpperAirStation("MFL", "KMFL", "72202", "Miami", "FL", "Southeast", 25.754, -80.384, 4),
        UpperAirStation("EYW", "KEYW", "72201", "Key West", "FL", "Southeast", 24.556, -81.752, 2),
        UpperAirStation("CHS", "KCHS", "72208", "Charleston", "SC", "Southeast", 32.899, -80.041, 15),
        UpperAirStation("GSO", "KGSO", "72317", "Greensboro", "NC", "Southeast", 36.098, -79.944, 277),
        UpperAirStation("MHX", "KMHX", "72305", "Morehead City", "NC", "Southeast", 34.776, -76.876, 11),
        UpperAirStation("RNK", "KRNK", "72318", "Blacksburg", "VA", "Southeast", 37.206, -80.415, 648),

        // Northeast
        UpperAirStation("IAD", "KIAD", "72403", "Sterling / Washington", "VA", "Northeast", 38.977, -77.487, 85),
        UpperAirStation("WAL", "KWAL", "72402", "Wallops Island", "VA", "Northeast", 37.937, -75.476, 13),
        UpperAirStation("PIT", "KPIT", "72520", "Pittsburgh", "PA", "Northeast", 40.531, -80.217, 360),
        UpperAirStation("OKX", "KOKX", "72501", "Upton / New York", "NY", "Northeast", 40.865, -72.865, 20),
        UpperAirStation("ALB", "KALB", "72518", "Albany", "NY", "Northeast", 42.747, -73.805, 89),
        UpperAirStation("BUF", "KBUF", "72528", "Buffalo", "NY", "Northeast", 42.941, -78.736, 218),
        UpperAirStation("CHH", "KCHH", "74494", "Chatham", "MA", "Northeast", 41.669, -69.959, 16),
        UpperAirStation("GYX", "KGYX", "74389", "Gray / Portland", "ME", "Northeast", 43.890, -70.256, 125),
        UpperAirStation("CAR", "KCAR", "72712", "Caribou", "ME", "Northeast", 46.868, -68.013, 191),

        // Mountain
        UpperAirStation("DNR", "KDNR", "72469", "Denver", "CO", "Mountain", 39.768, -104.869, 1611),
        UpperAirStation("GJT", "KGJT", "72476", "Grand Junction", "CO", "Mountain", 39.122, -108.526, 1475),
        UpperAirStation("ABQ", "KABQ", "72365", "Albuquerque", "NM", "Mountain", 35.038, -106.622, 1619),
        UpperAirStation("EPZ", "KEPZ", "72364", "Santa Teresa / El Paso", "NM", "Mountain", 31.873, -106.698, 1252),
        UpperAirStation("TUS", "KTUS", "72274", "Tucson", "AZ", "Mountain", 32.120, -110.935, 788),
        UpperAirStation("FGZ", "KFGZ", "72376", "Flagstaff", "AZ", "Mountain", 35.231, -111.821, 2179),
        UpperAirStation("SLC", "KSLC", "72572", "Salt Lake City", "UT", "Mountain", 40.771, -111.955, 1288),
        UpperAirStation("RIW", "KRIW", "72672", "Riverton", "WY", "Mountain", 43.065, -108.477, 1695),
        UpperAirStation("BOI", "KBOI", "72681", "Boise", "ID", "Mountain", 43.567, -116.211, 871),
        UpperAirStation("TFX", "KTFX", "72776", "Great Falls", "MT", "Mountain", 47.460, -111.384, 1131),
        UpperAirStation("GGW", "KGGW", "72768", "Glasgow", "MT", "Mountain", 48.206, -106.625, 694),
        UpperAirStation("LKN", "KLKN", "72582", "Elko", "NV", "Mountain", 40.865, -115.736, 1599),
        UpperAirStation("VEF", "KVEF", "72388", "Las Vegas", "NV", "Mountain", 36.048, -115.183, 697),
        UpperAirStation("REV", "KREV", "72489", "Reno", "NV", "Mountain", 39.568, -119.796, 1515),

        // West
        UpperAirStation("OAK", "KOAK", "72493", "Oakland / San Francisco", "CA", "West", 37.744, -122.223, 6),
        UpperAirStation("NKX", "KNKX", "72293", "San Diego / Miramar", "CA", "West", 32.855, -117.120, 134),
        UpperAirStation("MFR", "KMFR", "72597", "Medford", "OR", "West", 42.369, -122.872, 397),
        UpperAirStation("SLE", "KSLE", "72694", "Salem", "OR", "West", 44.909, -123.005, 61),
        UpperAirStation("OTX", "KOTX", "72786", "Spokane", "WA", "West", 47.680, -117.627, 728),
        UpperAirStation("UIL", "KUIL", "72797", "Quillayute", "WA", "West", 47.937, -124.555, 62),

        // Non-CONUS
        UpperAirStation("PANC", "PANC", "70273", "Anchorage", "AK", "Non-CONUS", 61.167, -150.017, 45),
        UpperAirStation("PAFA", "PAFA", "70261", "Fairbanks", "AK", "Non-CONUS", 64.815, -147.876, 138),
        UpperAirStation("PAJN", "PAJN", "70381", "Yakutat / Juneau", "AK", "Non-CONUS", 58.355, -134.576, 9),
        UpperAirStation("PABR", "PABR", "70026", "Utqiagvik / Barrow", "AK", "Non-CONUS", 71.283, -156.783, 12),
        UpperAirStation("PHLI", "PHLI", "91165", "Lihue", "HI", "Non-CONUS", 21.976, -159.339, 36),
        UpperAirStation("PHTO", "PHTO", "91285", "Hilo", "HI", "Non-CONUS", 19.717, -155.067, 11),
        UpperAirStation("TJSJ", "TJSJ", "78526", "San Juan", "PR", "Non-CONUS", 18.431, -66.002, 3)
    )

    val regions: List<String> = listOf("All", "Plains", "Midwest", "Southeast", "Northeast", "Mountain", "West", "Non-CONUS")

    fun findByIdOrNull(id: String): UpperAirStation? =
        stations.firstOrNull {
            it.id.equals(id, ignoreCase = true) ||
                it.icao.equals(id, ignoreCase = true) ||
                it.wmo.equals(id, ignoreCase = true)
        }

    fun findById(id: String): UpperAirStation = findByIdOrNull(id) ?: stations.first()

    fun findNearestStation(lat: Double, lon: Double): UpperAirStation {
        return stations.minByOrNull { haversineKm(lat, lon, it.lat, it.lon) } ?: stations.first()
    }

    fun searchMatches(query: String, limit: Int = 8): List<UpperAirStation> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return stations.filter { st ->
            st.id.contains(q, ignoreCase = true) ||
                st.icao.contains(q, ignoreCase = true) ||
                st.wmo.contains(q, ignoreCase = true) ||
                st.name.contains(q, ignoreCase = true) ||
                st.state.contains(q, ignoreCase = true)
        }.sortedWith(
            compareByDescending<UpperAirStation> {
                it.id.equals(q, ignoreCase = true) || it.icao.equals(q, ignoreCase = true)
            }.thenByDescending {
                it.id.startsWith(q, ignoreCase = true) || it.icao.startsWith(q, ignoreCase = true) || it.name.startsWith(q, ignoreCase = true)
            }
        ).take(limit)
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
