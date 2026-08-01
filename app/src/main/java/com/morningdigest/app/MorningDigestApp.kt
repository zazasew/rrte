package com.morningdigest.app

import android.app.Application
import com.morningdigest.app.di.AppContainer
import com.morningdigest.app.notification.NotificationHelper
import com.morningdigest.app.worker.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MorningDigestApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.getInstance(this)
        NotificationHelper.createChannels(this)

        // Ensure the daily WorkManager job is (re)scheduled according to the
        // user's saved wake-up time every time the app process starts.
        CoroutineScope(Dispatchers.Default).launch {
            val settings = container.settingsRepository.currentSettings()
            WorkScheduler.applySchedule(this@MorningDigestApp, settings)
        }
    }
}
