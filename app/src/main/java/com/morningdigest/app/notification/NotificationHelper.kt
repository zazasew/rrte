package com.morningdigest.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.morningdigest.app.MainActivity
import com.morningdigest.app.data.model.CustomAlertMatch
import com.morningdigest.app.data.model.DigestReport
import com.morningdigest.app.data.model.PriceAlertHit
import com.morningdigest.app.data.model.YoutubeVideoUpdate
import com.morningdigest.app.data.remote.PoliceReportFetcher

object NotificationHelper {
    const val CHANNEL_ID = "morning_digest_brief"
    private const val CHANNEL_NAME = "The Brief"
    // Separate, higher-importance channel for custom weather alert heads-ups
    // ("temperature about to cross your limit", etc.) - these are time-sensitive
    // and shouldn't be silently bundled with (or muted alongside) the daily brief.
    const val CHANNEL_ID_WEATHER_ALERTS = "morning_digest_weather_alerts"
    private const val CHANNEL_NAME_WEATHER_ALERTS = "Custom Weather Alerts"
    // Separate channel for Bitcoin/currency price threshold crossings - its
    // own toggle in system notification settings, same as weather alerts.
    const val CHANNEL_ID_PRICE_ALERTS = "morning_digest_price_alerts"
    private const val CHANNEL_NAME_PRICE_ALERTS = "Price Alerts"
    // Separate channel for "new video from a channel you follow" pushes -
    // its own toggle in system notification settings, same treatment as
    // weather/price alerts.
    const val CHANNEL_ID_YOUTUBE = "morning_digest_youtube"
    const val CHANNEL_ID_POLICE = "morning_digest_police"
    private const val CHANNEL_NAME_YOUTUBE = "New YouTube Videos"
    private const val CHANNEL_NAME_POLICE = "Nearby Police Incidents"
    private const val NOTIFICATION_ID_DIGEST = 2001
    private const val NOTIFICATION_ID_FAILURE = 2002
    private const val NOTIFICATION_ID_CUSTOM_ALERT = 2003
    private const val NOTIFICATION_ID_PRICE_ALERT = 2004
    private const val NOTIFICATION_ID_YOUTUBE = 2005
    private const val NOTIFICATION_ID_POLICE = 2006

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Delivers your morning brief — weather, markets, and top news"
            }
            val alertChannel = NotificationChannel(
                CHANNEL_ID_WEATHER_ALERTS,
                CHANNEL_NAME_WEATHER_ALERTS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads-up warnings when your custom weather alert rules are about to be crossed"
            }
            val priceChannel = NotificationChannel(
                CHANNEL_ID_PRICE_ALERTS,
                CHANNEL_NAME_PRICE_ALERTS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads-up alerts when Bitcoin or your currency pair crosses a price you set"
            }
            val youtubeChannel = NotificationChannel(
                CHANNEL_ID_YOUTUBE,
                CHANNEL_NAME_YOUTUBE,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies you when a YouTube channel you added in Settings posts a new video"
            }
            val policeChannel = NotificationChannel(
                CHANNEL_ID_POLICE,
                CHANNEL_NAME_POLICE,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Public incidents from the Norwegian Police operational log for your selected municipality"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
            manager?.createNotificationChannel(alertChannel)
            manager?.createNotificationChannel(priceChannel)
            manager?.createNotificationChannel(youtubeChannel)
            manager?.createNotificationChannel(policeChannel)
        }
    }

    /**
     * Posts the digest itself as the notification — this IS the delivery
     * mechanism now (no email involved). Returns true if it was actually
     * posted (false if notification permission isn't granted).
     */
    fun postDigest(context: Context, report: DigestReport, cityLabel: String, userName: String): Boolean {
        if (!hasPermission(context)) return false

        val intent = android.content.Intent(context, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = DigestNotificationBuilder.title(report, userName)
        val summary = DigestNotificationBuilder.shortSummary(report)
        val fullBrief = DigestNotificationBuilder.fullBrief(report, cityLabel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullBrief))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DIGEST, notification)
        return true
    }

    /**
     * Posts a heads-up notification for one or more custom weather alert
     * rules whose matched forecast hour has just entered the configured lead
     * time (e.g. "1h before your temperature limit is reached"). Returns true
     * if it was actually posted.
     */
    fun postCustomWeatherAlert(context: Context, matches: List<CustomAlertMatch>): Boolean {
        if (matches.isEmpty() || !hasPermission(context)) return false

        val intent = android.content.Intent(context, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (matches.size == 1) "⚠️ ${matches.first().label}" else "⚠️ ${matches.size} weather alerts coming up"
        val summary = matches.joinToString(" · ") { it.label }
        val fullBody = matches.joinToString("\n") { "• ${it.label} — ${it.detail}" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_WEATHER_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CUSTOM_ALERT, notification)
        return true
    }

    /**
     * Posts a heads-up notification for one or more Bitcoin/currency price
     * thresholds that just got crossed (e.g. "Bitcoin just went above
     * €70,000"). Returns true if it was actually posted.
     */
    fun postPriceAlert(context: Context, hits: List<PriceAlertHit>): Boolean {
        if (hits.isEmpty() || !hasPermission(context)) return false

        val intent = android.content.Intent(context, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (hits.size == 1) "💰 ${hits.first().label}" else "💰 ${hits.size} price alerts triggered"
        val summary = hits.joinToString(" · ") { it.label }
        val fullBody = hits.joinToString("\n") { "• ${it.label} — ${it.detail}" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PRICE_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PRICE_ALERT, notification)
        return true
    }

    /**
     * Posts a notification for one or more new videos from YouTube channels
     * added in Settings. Tapping it opens the video directly when there's
     * just one, or the app (where the New Videos section lists all of them)
     * when there are several at once. Returns true if it was actually posted.
     */
    fun postYoutubeUpdates(context: Context, updates: List<YoutubeVideoUpdate>): Boolean {
        if (updates.isEmpty() || !hasPermission(context)) return false

        val intent = if (updates.size == 1 && updates.first().videoLink.isNotBlank()) {
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(updates.first().videoLink))
        } else {
            android.content.Intent(context, MainActivity::class.java)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (updates.size == 1) {
            "📺 New video from ${updates.first().channelName}"
        } else {
            "📺 ${updates.size} new videos"
        }
        val summary = if (updates.size == 1) {
            updates.first().videoTitle
        } else {
            updates.joinToString(" · ") { it.channelName }
        }
        val fullBody = updates.joinToString("\n") { "• ${it.channelName}: ${it.videoTitle}" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_YOUTUBE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullBody))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_YOUTUBE, notification)
        return true
    }

    fun postPoliceIncidents(context: Context, incidents: List<PoliceReportFetcher.Incident>): Boolean {
        if (incidents.isEmpty() || !hasPermission(context)) return false
        val intent = android.content.Intent(context, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (incidents.size == 1) "🚨 ${incidents.first().categoryEn} nearby" else "🚨 ${incidents.size} nearby police incidents"
        val summary = incidents.first().englishText
        val body = incidents.joinToString("\n") { "• ${it.categoryEn}: ${it.englishText}" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_POLICE)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_POLICE, notification)
        return true
    }

    fun notifyFailure(context: Context, error: String) {
        if (!hasPermission(context)) return
        val intent = android.content.Intent(context, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("❌ Morning brief failed")
            .setContentText(error)
            .setStyle(NotificationCompat.BigTextStyle().bigText(error))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FAILURE, notification)
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
