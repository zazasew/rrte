package com.morningdigest.app.data.alert

import com.morningdigest.app.data.model.AlertRuleType
import com.morningdigest.app.data.model.CustomAlertMatch
import com.morningdigest.app.data.model.OneCallHourly
import com.morningdigest.app.data.model.WeatherAlert
import com.morningdigest.app.data.prefs.CustomAlertRules
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Evaluates the user's custom weather alert rules (Settings > Custom Weather
 * Alert Rules) against an hourly forecast, instead of relying purely on the
 * provider's own severe weather alerts.
 *
 * For each enabled rule, this scans forward from "now" out to
 * [CustomAlertRules.horizonHours] and reports the *first* hour the threshold
 * is met - e.g. "temperature above 30°C" reports the first forecast hour
 * where temp >= 30, not every subsequent hour that's also hot. That single
 * match is then flagged [CustomAlertMatch.leadWarning] once it falls inside
 * the configured lead time (e.g. "1h before"), which is what the periodic
 * background check uses to decide whether to actually push a notification.
 *
 * Every match also gets a calendar-day-relative [CustomAlertMatch.dayLabel]
 * ("Today" / "Tomorrow" / "In 2 days" / ...) so it's obvious at a glance
 * whether a match is imminent or still a couple of days out, since the
 * 12/24/48h horizon can span into day-after-tomorrow.
 */
object CustomWeatherAlertEngine {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val weekdayDateFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())

    fun evaluate(
        hourly: List<OneCallHourly>,
        officialAlerts: List<WeatherAlert>,
        rules: CustomAlertRules,
        nowMillis: Long = System.currentTimeMillis()
    ): List<CustomAlertMatch> {
        if (!rules.enabled) return emptyList()

        val horizonMillis = TimeUnit.HOURS.toMillis(rules.horizonHours.coerceIn(1, 48).toLong())
        val leadMillis = TimeUnit.HOURS.toMillis(rules.leadTimeHours.coerceIn(1, 24).toLong())
        val windowEnd = nowMillis + horizonMillis

        val points = hourly
            .map { it.dt to it }
            .filter { (dt, _) -> TimeUnit.SECONDS.toMillis(dt) in nowMillis..windowEnd }
            .sortedBy { it.first }
            .map { it.second }

        val results = mutableListOf<CustomAlertMatch>()

        fun matchOf(point: OneCallHourly): Long = TimeUnit.SECONDS.toMillis(point.dt)
        fun isImminent(atMillis: Long): Boolean = atMillis - nowMillis <= leadMillis
        // "Today, 14:00" / "Tomorrow, 09:00" / "In 2 days, 21:00" - prefixed onto
        // every match's detail text so the day is obvious without doing mental
        // date math on a bare time-of-day.
        fun whenText(atMillis: Long): String = "${dayLabel(atMillis, nowMillis)}, ${timeFormat.format(Date(atMillis))}"

        if (rules.tempAboveEnabled) {
            points.firstOrNull { (it.temp ?: Double.MIN_VALUE) >= rules.tempAboveValue }?.let { p ->
                val at = matchOf(p)
                results += CustomAlertMatch(
                    type = AlertRuleType.TEMP_ABOVE,
                    label = "Temperature above ${formatNumber(rules.tempAboveValue)}°C",
                    detail = "${whenText(at)} — Forecast ${p.temp?.let { formatNumber(it) } ?: "—"}°C",
                    dayLabel = dayLabel(at, nowMillis),
                    triggerAtMillis = at,
                    leadWarning = isImminent(at)
                )
            }
        }

        if (rules.tempBelowEnabled) {
            points.firstOrNull { (it.temp ?: Double.MAX_VALUE) <= rules.tempBelowValue }?.let { p ->
                val at = matchOf(p)
                results += CustomAlertMatch(
                    type = AlertRuleType.TEMP_BELOW,
                    label = "Temperature below ${formatNumber(rules.tempBelowValue)}°C",
                    detail = "${whenText(at)} — Forecast ${p.temp?.let { formatNumber(it) } ?: "—"}°C",
                    dayLabel = dayLabel(at, nowMillis),
                    triggerAtMillis = at,
                    leadWarning = isImminent(at)
                )
            }
        }

        if (rules.uvIndexEnabled) {
            points.firstOrNull { (it.uvi ?: -1.0) >= rules.uvIndexValue }?.let { p ->
                val at = matchOf(p)
                results += CustomAlertMatch(
                    type = AlertRuleType.UV_INDEX,
                    label = "UV index above ${formatNumber(rules.uvIndexValue)}",
                    detail = "${whenText(at)} — Forecast UV ${p.uvi?.let { formatNumber(it) } ?: "—"}",
                    dayLabel = dayLabel(at, nowMillis),
                    triggerAtMillis = at,
                    leadWarning = isImminent(at)
                )
            }
        }

        if (rules.windSpeedEnabled) {
            points.firstOrNull { (it.windSpeed ?: -1.0) >= rules.windSpeedValue }?.let { p ->
                val at = matchOf(p)
                results += CustomAlertMatch(
                    type = AlertRuleType.WIND_SPEED,
                    label = "Wind speed above ${formatNumber(rules.windSpeedValue)} m/s",
                    detail = "${whenText(at)} — Forecast ${p.windSpeed?.let { formatNumber(it) } ?: "—"} m/s",
                    dayLabel = dayLabel(at, nowMillis),
                    triggerAtMillis = at,
                    leadWarning = isImminent(at)
                )
            }
        }

        if (rules.rainProbEnabled) {
            val thresholdFraction = rules.rainProbValue / 100.0
            points.firstOrNull { (it.pop ?: -1.0) >= thresholdFraction }?.let { p ->
                val at = matchOf(p)
                val percent = ((p.pop ?: 0.0) * 100).roundToInt()
                results += CustomAlertMatch(
                    type = AlertRuleType.RAIN_PROBABILITY,
                    label = "Rain probability above ${rules.rainProbValue}%",
                    detail = "${whenText(at)} — Forecast $percent%",
                    dayLabel = dayLabel(at, nowMillis),
                    triggerAtMillis = at,
                    leadWarning = isImminent(at)
                )
            }
        }

        if (rules.thunderstormEnabled) {
            points.firstOrNull { p -> p.weather.orEmpty().any { it.main.equals("Thunderstorm", ignoreCase = true) } }
                ?.let { p ->
                    val at = matchOf(p)
                    results += CustomAlertMatch(
                        type = AlertRuleType.THUNDERSTORM,
                        label = "Thunderstorm expected",
                        detail = "Expected ${whenText(at)}",
                        dayLabel = dayLabel(at, nowMillis),
                        triggerAtMillis = at,
                        leadWarning = isImminent(at)
                    )
                }
        }

        if (rules.snowEnabled) {
            points.firstOrNull { p -> p.weather.orEmpty().any { it.main.equals("Snow", ignoreCase = true) } }
                ?.let { p ->
                    val at = matchOf(p)
                    results += CustomAlertMatch(
                        type = AlertRuleType.SNOW,
                        label = "Snow expected",
                        detail = "Expected ${whenText(at)}",
                        dayLabel = dayLabel(at, nowMillis),
                        triggerAtMillis = at,
                        leadWarning = isImminent(at)
                    )
                }
        }

        if (rules.officialAlertEnabled) {
            officialAlerts.forEach { alert ->
                val start = alert.startMillis ?: return@forEach
                if (start in nowMillis..windowEnd) {
                    results += CustomAlertMatch(
                        type = AlertRuleType.OFFICIAL_SEVERE,
                        label = alert.event.ifBlank { "Severe weather warning" },
                        detail = "Starts ${whenText(start)}" +
                            if (alert.senderName.isNotBlank()) " — ${alert.senderName}" else "",
                        dayLabel = dayLabel(start, nowMillis),
                        triggerAtMillis = start,
                        leadWarning = isImminent(start)
                    )
                }
            }
        }

        return results.sortedBy { it.triggerAtMillis }
    }

    /** "Today" / "Tomorrow" / "In N days" / a short date further out, based on calendar-day difference (not raw hour count). */
    private fun dayLabel(triggerMillis: Long, nowMillis: Long): String {
        val startOfToday = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val diffDays = ((triggerMillis - startOfToday) / TimeUnit.DAYS.toMillis(1)).toInt()
        return when {
            diffDays <= 0 -> "Today"
            diffDays == 1 -> "Tomorrow"
            diffDays in 2..6 -> "In $diffDays days"
            else -> weekdayDateFormat.format(Date(triggerMillis))
        }
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
}
