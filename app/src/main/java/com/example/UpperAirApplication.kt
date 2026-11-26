package com.example

import android.app.Application
import com.example.data.local.SettingsStore
import com.example.data.local.UpperAirDatabase
import com.example.data.remote.MapTileRepository
import com.example.data.remote.NwsSoundingClient
import com.example.data.repository.UpperAirRepository
import com.example.worker.SoundingSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    private val database by lazy { UpperAirDatabase.getInstance(application) }
    private val settingsStore by lazy { SettingsStore(application) }
    private val client by lazy { NwsSoundingClient() }
    val mapTileRepository by lazy { MapTileRepository(application) }
    val repository by lazy {
        UpperAirRepository(
            dao = database.upperAirDao(),
            settingsStore = settingsStore,
            client = client
        )
    }
    val syncScheduler by lazy { SoundingSyncScheduler(application) }
}

class UpperAirApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            val settings = SettingsStore(this@UpperAirApplication).currentSettings()
            container.syncScheduler.configurePeriodicSync(
                dailyOfflineEnabled = settings.dailyOfflineCacheEnabled,
                autoSyncEnabled = settings.autoSyncEnabled,
                intervalHours = settings.syncIntervalHours,
                wifiOnly = settings.wifiOnly
            )
        }
    }
}
