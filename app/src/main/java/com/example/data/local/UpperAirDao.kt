package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UpperAirDao {
    @Query("SELECT * FROM sounding_snapshots WHERE stationId = :stationId AND modelId = 'RAOB' ORDER BY validEpochMs DESC LIMIT 1")
    fun observeLatestObserved(stationId: String): Flow<SoundingSnapshotEntity?>

    @Query("SELECT * FROM sounding_snapshots WHERE stationId = :stationId AND modelId = 'RAOB' ORDER BY validEpochMs DESC")
    fun observePastObserved(stationId: String): Flow<List<SoundingSnapshotEntity>>

    @Query("SELECT * FROM sounding_snapshots WHERE stationId = :stationId AND modelId = :modelId ORDER BY forecastHourOffset ASC")
    fun observeModelForecasts(stationId: String, modelId: String): Flow<List<SoundingSnapshotEntity>>

    @Query("SELECT stationId, MAX(fetchedEpochMs) as lastFetched FROM sounding_snapshots GROUP BY stationId")
    fun observeStationLastFetched(): Flow<List<StationSyncTimestamp>>

    @Query("SELECT COUNT(*) FROM sounding_snapshots")
    fun observeTotalCachedProfilesCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT stationId) FROM sounding_snapshots")
    fun observeCachedStationsCount(): Flow<Int>

    @Query("SELECT MAX(fetchedEpochMs) FROM sounding_snapshots WHERE stationId = :stationId")
    suspend fun getLastFetchedForStation(stationId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: SoundingSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshots(snapshots: List<SoundingSnapshotEntity>)

    @Query("DELETE FROM sounding_snapshots WHERE stationId = :stationId AND modelId = :modelId")
    suspend fun clearModelForStation(stationId: String, modelId: String)

    @Transaction
    suspend fun replaceModelForStation(stationId: String, modelId: String, forecasts: List<SoundingSnapshotEntity>) {
        clearModelForStation(stationId, modelId)
        upsertSnapshots(forecasts)
    }

    @Query("SELECT * FROM sync_logs ORDER BY timestampMs DESC LIMIT 40")
    fun observeSyncLogs(): Flow<List<SyncLogEntity>>

    @Insert
    suspend fun insertSyncLog(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs WHERE id NOT IN (SELECT id FROM sync_logs ORDER BY timestampMs DESC LIMIT 80)")
    suspend fun trimOldLogs()
}

data class StationSyncTimestamp(
    val stationId: String,
    val lastFetched: Long
)
