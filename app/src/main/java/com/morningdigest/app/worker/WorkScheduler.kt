package com.morningdigest.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.morningdigest.app.data.prefs.AppSettings
import com.morningdigest.app.data.prefs.ScheduleMode
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkScheduler {

    /**
     * Applies whichever schedule the user picked in Settings: a fixed daily
     * time, or a repeating "every N hours" cadence. Call this any time the
     * schedule-related settings change, on boot, and on app start.
     */
    fun applySchedule(context: Context, settings: AppSettings) {
        if (!settings.autoSendEnabled) {
            cancelDaily(context)
        } else {
            when (settings.scheduleMode) {
                ScheduleMode.DAILY -> scheduleDaily(context, settings.wakeHour, settings.wakeMinute)
                ScheduleMode.INTERVAL -> scheduleInterval(context, settings.intervalHours)
            }
        }
        applyWeatherAlertCheckSchedule(context, settings.customAlertRules.enabled)
        applyPriceAlertCheckSchedule(context, settings.priceAlertRules.enabled)
        applyPoliceIncidentSchedule(context, settings.policeAlertsEnabled)
        applyYoutubeCheckSchedule(context, settings.youtubeChannels.isNotEmpty())
    }


    fun applyPoliceIncidentSchedule(context: Context, enabled: Boolean) {
        if (enabled) schedulePoliceIncidentChecks(context) else cancelPoliceIncidentChecks(context)
    }

    /**
     * Checks public police incidents every hour when enabled, refreshing
     * Max's Police Report card and the dashboard's shield indicator in the
     * background. Network + battery-not-low constraints keep it
     * battery-friendly; Doze mode can still delay an individual run by a
     * bit, and a failed run backs off and retries rather than waiting a
     * full extra hour.
     */
    fun schedulePoliceIncidentChecks(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<PoliceIncidentCheckWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PoliceIncidentCheckWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelPoliceIncidentChecks(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PoliceIncidentCheckWorker.UNIQUE_PERIODIC_NAME)
    }

    /**
     * Turns the periodic Bitcoin/currency price-threshold check on or off -
     * independent of everything else, since it's purely about catching a
     * price crossing as soon as reasonably possible.
     */
    fun applyPriceAlertCheckSchedule(context: Context, enabled: Boolean) {
        if (enabled) schedulePriceAlertChecks(context) else cancelPriceAlertChecks(context)
    }

    /**
     * Checks the user's Bitcoin/currency price alert rules about every 3
     * hours in the background - this only covers the case where the app
     * hasn't been opened in a while; any time the user actually opens the
     * app and refreshes (pull-to-refresh, or the Bitcoin/Currency card),
     * that already delivers live prices right then AND restarts this
     * countdown fresh from that moment via [restartPriceAlertCountdown], so
     * the background check never fires on stale leftover time from before
     * the user last looked.
     */
    fun schedulePriceAlertChecks(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PriceAlertCheckWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildPriceAlertRequest()
        )
    }

    /**
     * Cancels and re-enqueues the price-alert check so its next background
     * run is a fresh 3 hours from right now, instead of whatever was left
     * of the previous window. Call this after a manual in-app refresh
     * already delivered live price data - there's no point in the
     * background job re-checking again a few minutes later.
     */
    fun restartPriceAlertCountdown(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PriceAlertCheckWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            buildPriceAlertRequest()
        )
    }

    private fun buildPriceAlertRequest() = PeriodicWorkRequestBuilder<PriceAlertCheckWorker>(3, TimeUnit.HOURS)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
        .build()

    fun cancelPriceAlertChecks(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PriceAlertCheckWorker.UNIQUE_PERIODIC_NAME)
    }

    /**
     * Turns the custom-weather-alert-rule check on or off - independent of
     * the main digest schedule/auto-send switch above, since the whole
     * point is a fresh heads-up notification ahead of the daily brief.
     */
    fun applyWeatherAlertCheckSchedule(context: Context, enabled: Boolean) {
        if (enabled) scheduleWeatherAlertChecks(context) else cancelWeatherAlertChecks(context)
    }

    /**
     * Checks the user's custom weather alert rules against the forecast
     * about every 3 hours - forecasts don't shift enough minute-to-minute
     * to need tighter routine polling than that, so this is the
     * battery-friendly fallback that guarantees the rules eventually get
     * (re-)checked no matter what. The actual "instant" part of a heads-up
     * notification doesn't come from polling more often - it comes from
     * [WeatherAlertCheckWorker] scheduling a single precise one-time
     * follow-up (see [scheduleWeatherAlertPreciseCheck]) timed for the exact
     * moment the soonest upcoming match enters its configured lead window,
     * since the forecast already tells us exactly when that will be.
     */
    fun scheduleWeatherAlertChecks(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<WeatherAlertCheckWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WeatherAlertCheckWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelWeatherAlertChecks(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WeatherAlertCheckWorker.UNIQUE_PERIODIC_NAME)
        cancelWeatherAlertPreciseCheck(context)
    }

    /**
     * A single one-time check, timed by [WeatherAlertCheckWorker] itself to
     * fire right when the soonest not-yet-imminent match enters its lead
     * window - close to instant delivery for that one specific alert
     * without any extra routine polling. Re-enqueuing with this same unique
     * name (REPLACE) each time keeps at most one of these scheduled, so it
     * always reflects the freshest forecast rather than piling up.
     */
    fun scheduleWeatherAlertPreciseCheck(context: Context, delayMillis: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<WeatherAlertCheckWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WEATHER_ALERT_PRECISE_CHECK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelWeatherAlertPreciseCheck(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WEATHER_ALERT_PRECISE_CHECK_NAME)
    }

    private const val WEATHER_ALERT_PRECISE_CHECK_NAME = "weather_alert_precise_checkin"

    /**
     * Turns the periodic "check followed YouTube channels for new videos"
     * job on or off - on whenever at least one channel is configured in
     * Settings, off when the list is emptied.
     */
    fun applyYoutubeCheckSchedule(context: Context, enabled: Boolean) {
        if (enabled) scheduleYoutubeChecks(context) else cancelYoutubeChecks(context)
    }

    /**
     * Checks every followed YouTube channel for new videos every 45 minutes
     * when at least one channel is followed. This was previously a no-op
     * (see git history / earlier comment here) on the assumption that a
     * server-side push architecture would handle this instead - but that
     * requires deploying the optional push server (see
     * PUSH_SETUP_REQUIRED.md) and isn't set up by default, so leaving this
     * disabled meant new videos were only ever noticed the next time someone
     * happened to open the dashboard, with no background notification at
     * all. [YoutubeCheckWorker] was already fully implemented (battery/
     * network guards, its own notified-tracking so a video is never
     * notified twice) and simply wasn't being scheduled - this restores that.
     */
    fun scheduleYoutubeChecks(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<YoutubeCheckWorker>(45, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            YoutubeCheckWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelYoutubeChecks(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(YoutubeCheckWorker.UNIQUE_PERIODIC_NAME)
    }

    /**
     * Schedules (or reschedules) a daily job targeting [hour]:[minute]. WorkManager
     * periodic work doesn't support an exact time-of-day directly, so we compute
     * the initial delay until the next occurrence of that time and use a 24h
     * period from there - this survives app restarts, reboots, and Doze mode.
     */
    fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        val initialDelay = millisUntilNext(hour, minute)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<MorningDigestWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putBoolean(MorningDigestWorker.KEY_SEND_NOTIFICATION, true).build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MorningDigestWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Schedules (or reschedules) a repeating job that fires every [hours] hours,
     * starting [hours] from now. Used for the "every N hours" wake-up option
     * (e.g. every 3h, 4h, 6h, 8h or 12h) instead of one fixed daily time.
     */
    fun scheduleInterval(context: Context, hours: Int) {
        val safeHours = hours.coerceIn(1, 24)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<MorningDigestWorker>(safeHours.toLong(), TimeUnit.HOURS)
            .setInitialDelay(safeHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putBoolean(MorningDigestWorker.KEY_SEND_NOTIFICATION, true).build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MorningDigestWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelDaily(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MorningDigestWorker.UNIQUE_PERIODIC_NAME)
    }

    /** "Refresh Now" / "Notify Now" - runs immediately, once. */
    fun runNow(context: Context, sendNotification: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<MorningDigestWorker>()
            .setConstraints(constraints)
            .setInputData(Data.Builder().putBoolean(MorningDigestWorker.KEY_SEND_NOTIFICATION, sendNotification).build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MorningDigestWorker.UNIQUE_ONE_TIME_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun nextScheduledMillis(hour: Int, minute: Int): Long =
        System.currentTimeMillis() + millisUntilNext(hour, minute)

    /** Best-effort "next send" estimate for either schedule mode, for display in the UI. */
    fun nextScheduledMillis(settings: AppSettings, lastSentMillis: Long?): Long = when (settings.scheduleMode) {
        ScheduleMode.DAILY -> nextScheduledMillis(settings.wakeHour, settings.wakeMinute)
        ScheduleMode.INTERVAL -> {
            val intervalMillis = settings.intervalHours.coerceIn(1, 24) * 3_600_000L
            val base = lastSentMillis ?: System.currentTimeMillis()
            var next = base + intervalMillis
            while (next < System.currentTimeMillis()) next += intervalMillis
            next
        }
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - now.timeInMillis
    }
}
