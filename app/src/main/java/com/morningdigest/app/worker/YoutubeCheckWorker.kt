package com.morningdigest.app.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.morningdigest.app.MorningDigestApp
import com.morningdigest.app.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs periodically (independent of the main daily/interval digest) to check
 * every YouTube channel added in Settings for videos newer than that
 * channel's baseline, firing a push notification the moment a genuinely new
 * one shows up - instead of only surfacing new videos the next time the
 * dashboard's "New videos" section happens to be viewed.
 *
 * Uses its own `notifiedVideoIds` per channel (separate from the dashboard's
 * `dismissedVideoIds`) so a video is notified about exactly once, regardless
 * of whether/when the person dismisses it from the dashboard.
 */
class YoutubeCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as MorningDigestApp
        val container = app.container

        return@withContext try {
            val settings = container.settingsRepository.currentSettings()
            val channels = settings.youtubeChannels
            if (channels.isEmpty() || !settings.notificationsEnabled) return@withContext Result.success()

            if (isBatteryCriticallyLow(applicationContext)) return@withContext Result.success()
            if (isNetworkRestricted(applicationContext)) return@withContext Result.success()

            val updates = container.digestRepository.checkYoutubeChannelsForNewVideos(channels)
            val toNotify = updates.filter { update ->
                val channel = channels.find { it.channelId == update.channelId }
                channel != null && update.videoId !in channel.notifiedVideoIds
            }

            if (toNotify.isNotEmpty()) {
                NotificationHelper.postYoutubeUpdates(applicationContext, toNotify)

                val newlyNotifiedByChannel = toNotify.groupBy({ it.channelId }, { it.videoId })
                val updatedChannels = channels.map { channel ->
                    val newlyNotified = newlyNotifiedByChannel[channel.channelId].orEmpty()
                    if (newlyNotified.isEmpty()) channel
                    else channel.copy(notifiedVideoIds = (channel.notifiedVideoIds + newlyNotified).toList().takeLast(200).toSet())
                }
                container.settingsRepository.updateYoutubeChannels(updatedChannels)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** Skip this optional check when the battery is critically low and not charging. */
    private fun isBatteryCriticallyLow(context: Context): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return false
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        return !isCharging && level in 0..CRITICAL_BATTERY_PERCENT
    }

    /** Skip when the user has Data Saver on, or the active connection is roaming. */
    private fun isNetworkRestricted(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        if (cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "youtube_check"
        private const val CRITICAL_BATTERY_PERCENT = 15
    }
}
