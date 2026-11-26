package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.SoundingLevel
import com.example.domain.model.SoundingProfile
import com.example.domain.model.SyncLogEntry
import com.example.domain.model.ThermodynamicIndices
import org.json.JSONArray
import org.json.JSONObject

@Entity(
    tableName = "sounding_snapshots",
    indices = [Index(value = ["stationId", "modelId", "forecastHourOffset"])]
)
data class SoundingSnapshotEntity(
    @PrimaryKey val id: String,
    val stationId: String,
    val modelId: String,
    val isForecast: Boolean,
    val forecastHourOffset: Int,
    val sourceLabel: String,
    val validTimeUtcIso: String,
    val validEpochMs: Long,
    val fetchedEpochMs: Long,
    val levelsJson: String,
    val indicesJson: String,
    val nwsOfficeHeadline: String
)

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val stationId: String,
    val sourceType: String,
    val success: Boolean,
    val message: String,
    val levelsOrStepsCount: Int
)

fun SyncLogEntity.toDomain(): SyncLogEntry = SyncLogEntry(
    id = id,
    timestampMs = timestampMs,
    stationId = stationId,
    sourceType = sourceType,
    success = success,
    message = message,
    levelsOrStepsCount = levelsOrStepsCount
)

object ProfileJsonCodec {
    fun encodeLevels(levels: List<SoundingLevel>): String {
        val arr = JSONArray()
        for (lvl in levels) {
            val obj = JSONObject()
            obj.put("p", lvl.pressureHpa)
            obj.put("h", lvl.heightM)
            obj.put("t", lvl.tempC)
            if (lvl.dewpointC != null) obj.put("td", lvl.dewpointC)
            if (lvl.windDirDeg != null) obj.put("wd", lvl.windDirDeg)
            if (lvl.windSpeedKt != null) obj.put("ws", lvl.windSpeedKt)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeLevels(json: String): List<SoundingLevel> {
        val arr = JSONArray(json)
        val out = ArrayList<SoundingLevel>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            out.add(
                SoundingLevel(
                    pressureHpa = obj.getDouble("p"),
                    heightM = obj.getDouble("h"),
                    tempC = obj.getDouble("t"),
                    dewpointC = if (obj.has("td") && !obj.isNull("td")) obj.getDouble("td") else null,
                    windDirDeg = if (obj.has("wd") && !obj.isNull("wd")) obj.getDouble("wd") else null,
                    windSpeedKt = if (obj.has("ws") && !obj.isNull("ws")) obj.getDouble("ws") else null
                )
            )
        }
        return out
    }

    fun encodeIndices(idx: ThermodynamicIndices): String {
        val obj = JSONObject()
        obj.put("sbcape", idx.sbcapeJkg)
        obj.put("sbcin", idx.sbcinJkg)
        obj.put("mlcape", idx.mlcapeJkg)
        obj.put("li", idx.liftedIndex)
        obj.put("lclM", idx.lclMeters)
        obj.put("lclP", idx.lclHpa)
        if (idx.lfcMeters != null) obj.put("lfcM", idx.lfcMeters)
        if (idx.elMeters != null) obj.put("elM", idx.elMeters)
        obj.put("k", idx.kIndex)
        obj.put("tt", idx.totalTotals)
        obj.put("pwat", idx.pwatMm)
        obj.put("s01", idx.shear01Kt)
        obj.put("s06", idx.shear06Kt)
        obj.put("srh03", idx.srh03M2S2)
        obj.put("lr", idx.lapseRate700500)
        obj.put("frz", idx.freezingLevelM)
        obj.put("maxW", idx.maxWindKt)
        obj.put("maxWP", idx.maxWindPressureHpa)
        return obj.toString()
    }

    fun decodeIndices(json: String): ThermodynamicIndices {
        val obj = JSONObject(json)
        return ThermodynamicIndices(
            sbcapeJkg = obj.optInt("sbcape", 0),
            sbcinJkg = obj.optInt("sbcin", 0),
            mlcapeJkg = obj.optInt("mlcape", 0),
            liftedIndex = obj.optDouble("li", 0.0),
            lclMeters = obj.optInt("lclM", 0),
            lclHpa = obj.optInt("lclP", 1000),
            lfcMeters = if (obj.has("lfcM") && !obj.isNull("lfcM")) obj.getInt("lfcM") else null,
            elMeters = if (obj.has("elM") && !obj.isNull("elM")) obj.getInt("elM") else null,
            kIndex = obj.optDouble("k", 0.0),
            totalTotals = obj.optDouble("tt", 0.0),
            pwatMm = obj.optDouble("pwat", 0.0),
            shear01Kt = obj.optInt("s01", 0),
            shear06Kt = obj.optInt("s06", 0),
            srh03M2S2 = obj.optInt("srh03", 0),
            lapseRate700500 = obj.optDouble("lr", 0.0),
            freezingLevelM = obj.optInt("frz", 0),
            maxWindKt = obj.optInt("maxW", 0),
            maxWindPressureHpa = obj.optInt("maxWP", 250)
        )
    }

    fun toEntity(profile: SoundingProfile): SoundingSnapshotEntity {
        val id = if (profile.isForecast) {
            "${profile.stationId}_${profile.modelId}_${profile.forecastHourOffset}"
        } else {
            "${profile.stationId}_RAOB_${profile.validEpochMs}"
        }
        return SoundingSnapshotEntity(
            id = id,
            stationId = profile.stationId,
            modelId = profile.modelId,
            isForecast = profile.isForecast,
            forecastHourOffset = profile.forecastHourOffset,
            sourceLabel = profile.sourceLabel,
            validTimeUtcIso = profile.validTimeUtcIso,
            validEpochMs = profile.validEpochMs,
            fetchedEpochMs = profile.fetchedEpochMs,
            levelsJson = encodeLevels(profile.levels),
            indicesJson = encodeIndices(profile.indices),
            nwsOfficeHeadline = profile.nwsOfficeHeadline
        )
    }

    fun toDomain(entity: SoundingSnapshotEntity): SoundingProfile {
        return SoundingProfile(
            stationId = entity.stationId,
            modelId = entity.modelId,
            sourceLabel = entity.sourceLabel,
            validTimeUtcIso = entity.validTimeUtcIso,
            validEpochMs = entity.validEpochMs,
            fetchedEpochMs = entity.fetchedEpochMs,
            isForecast = entity.isForecast,
            forecastHourOffset = entity.forecastHourOffset,
            levels = decodeLevels(entity.levelsJson),
            indices = decodeIndices(entity.indicesJson),
            nwsOfficeHeadline = entity.nwsOfficeHeadline
        )
    }
}
