package com.morningdigest.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.morningdigest.app.MorningDigestApp
import com.morningdigest.app.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs once a day (or on-demand): fetches weather/bitcoin/currency/news in
 * parallel, then delivers the result as a rich local notification (the
 * morning brief itself — no email/SMTP involved) and stores it in history.
 */
class MorningDigestWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as MorningDigestApp
        val container = app.container

        return@withContext try {
            val settings = container.settingsRepository.currentSettings()
            val rawReport = container.digestRepository.buildFreshReport(settings)

            var finalReport = rawReport

            if (inputData.getBoolean(KEY_SEND_NOTIFICATION, true) && settings.notificationsEnabled) {
                val posted = NotificationHelper.postDigest(
                    applicationContext, rawReport, settings.city, settings.userName
                )
                finalReport = if (posted) {
                    rawReport.copy(notificationSent = true, notificationError = null)
                } else {
                    rawReport.copy(
                        notificationSent = false,
                        notificationError = "Notification permission isn't granted. Enable notifications for The Brief in system settings."
                    )
                }
            }

            container.digestRepository.saveReport(finalReport)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_SEND_NOTIFICATION = "send_notification"
        const val UNIQUE_PERIODIC_NAME = "morning_digest_daily"
        const val UNIQUE_ONE_TIME_NAME = "morning_digest_manual"
    }
}
