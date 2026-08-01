package com.morningdigest.app.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.morningdigest.app.MorningDigestApp
import com.morningdigest.app.data.model.PriceAlertHit
import com.morningdigest.app.data.prefs.PairAlertRule
import com.morningdigest.app.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs periodically (independent of the main daily/interval digest and of
 * the hourly weather alert check) to compare the live Bitcoin price, the
 * main currency pair, and every extra currency pair the user added, against
 * the thresholds set from the bell icon on each card, firing a heads-up
 * notification the moment one is crossed.
 *
 * Uses a simple "arm/trigger" state per rule (persisted via
 * [com.morningdigest.app.data.prefs.SettingsRepository.getPriceAlertArmedKeys])
 * so it notifies once per crossing rather than every run while the price
 * stays past the threshold - it re-arms automatically once the price moves
 * back to the other side.
 */
class PriceAlertCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as MorningDigestApp
        val container = app.container

        return@withContext try {
            val settings = container.settingsRepository.currentSettings()
            val rules = settings.priceAlertRules
            if (!rules.enabled) return@withContext Result.success()

            if (isBatteryCriticallyLow(applicationContext)) return@withContext Result.success()
            if (isNetworkRestricted(applicationContext)) return@withContext Result.success()

            // Reuses the existing Markets refresh (Bitcoin + currency + watchlist),
            // which also keeps the dashboard's own cards fresh as a side effect.
            val updatedReport = container.digestRepository.refreshMarketsSection(settings)

            val armed = container.settingsRepository.getPriceAlertArmedKeys().toMutableSet()
            val hits = mutableListOf<PriceAlertHit>()

            fun check(ruleId: String, isPast: Boolean, label: String, detail: String) {
                if (isPast) {
                    if (ruleId !in armed) {
                        hits += PriceAlertHit(ruleId, label, detail)
                        armed += ruleId
                    }
                } else {
                    armed -= ruleId
                }
            }

            fun checkPair(idPrefix: String, assetLabel: String, rule: PairAlertRule, value: Double, unitPrefix: String, decimals: Int) {
                fun fmt(v: Double) = if (decimals == 0) "$unitPrefix${"%,.0f".format(v)}" else "$unitPrefix${"%.4f".format(v)}"
                if (rule.aboveEnabled) {
                    check("${idPrefix}_above", value > rule.aboveValue, "$assetLabel above ${fmt(rule.aboveValue)}", "Now at ${fmt(value)}")
                }
                if (rule.belowEnabled) {
                    check("${idPrefix}_below", value < rule.belowValue, "$assetLabel below ${fmt(rule.belowValue)}", "Now at ${fmt(value)}")
                }
            }

            val btc = updatedReport.bitcoin
            if (btc.available && btc.eur != null) {
                checkPair("btc", "Bitcoin", rules.bitcoin, btc.eur, "€", decimals = 0)
            }

            val fx = updatedReport.currency
            if (fx.available && fx.rate != null) {
                checkPair("fx_main", "${fx.baseCurrency}/${fx.targetCurrency}", rules.mainCurrency, fx.rate, "", decimals = 4)
            }

            // Extra currency pairs the user added in Settings - same treatment
            // as Bitcoin/the main pair now, each with its own independent rule
            // keyed by "BASE/TARGET".
            updatedReport.watchlist.filter { !it.isCrypto && it.available && it.value != null }.forEach { entry ->
                val pairKey = entry.label.replace(" → ", "/")
                val rule = rules.extraPairs[pairKey] ?: return@forEach
                if (rule.aboveEnabled || rule.belowEnabled) {
                    checkPair("fx_extra_$pairKey", pairKey, rule, entry.value!!, "", decimals = 4)
                }
            }

            if (hits.isNotEmpty()) {
                NotificationHelper.postPriceAlert(applicationContext, hits)
            }
            container.settingsRepository.setPriceAlertArmedKeys(armed)

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
        const val UNIQUE_PERIODIC_NAME = "price_alert_check"
        private const val CRITICAL_BATTERY_PERCENT = 15
    }
}
