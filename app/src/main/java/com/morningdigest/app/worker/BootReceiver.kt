package com.morningdigest.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.morningdigest.app.MorningDigestApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as MorningDigestApp
                val settings = app.container.settingsRepository.currentSettings()
                WorkScheduler.applySchedule(context, settings)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
