package com.morningdigest.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.morningdigest.app.data.remote.BusinessFeedCatalog
import com.morningdigest.app.data.remote.NewsFeedCatalog
import com.morningdigest.app.data.remote.PoliticsFeedCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** How the digest is scheduled: once a day at a fixed time, or repeating every N hours. */
enum class ScheduleMode { DAILY, INTERVAL }

/**
 * A user-added RSS feed, on top of the built-in catalog. There is no cap on
 * how many of these a user can add - they can add as many outlets as they
 * want (e.g. Yahoo News), and each one is included in the digest and grouped
 * into the picker by [topic] (defaults to "World") just like a catalog feed.
 */
data class CustomFeed(
    val id: String,
    val label: String,
    val url: String,
    val topic: String = "World"
)

/** Whether an [AssetRef] refers to a fiat currency or a crypto coin. */
enum class AssetType { CURRENCY, CRYPTO }

/**
 * One side of a currency/crypto pair. [code] is a 3-letter ISO currency code
 * (e.g. "USD") when [type] is CURRENCY, or a CoinGecko coin id (e.g.
 * "ethereum") when [type] is CRYPTO.
 */
data class AssetRef(val type: AssetType, val code: String)

/**
 * One extra "From -> To" pair the user added on top of the primary
 * Currency Pair above (Settings > Currency Pair > additional pairs). Either
 * side can be a fiat currency or a crypto coin, in any combination - e.g.
 * USD -> BTC, ETH -> EUR, or BTC -> ETH all work the same way.
 */
data class CurrencyPairConfig(val from: AssetRef, val to: AssetRef)

/**
 * User-defined weather alert thresholds, on top of the provider's own severe
 * weather alerts. Each numeric rule is independently on/off with its own
 * value (e.g. "temperature above 30°C"), plus two yes/no rules (thunderstorm
 * expected / snow expected) and a rule for the provider's official severe
 * warnings. [horizonHours] controls how far ahead the forecast is scanned
 * (12/24/48h) and [leadTimeHours] controls how soon before the matched hour
 * a push notification actually fires ("fresh notification 1h before the
 * limit is reached").
 */
data class CustomAlertRules(
    val enabled: Boolean = false,
    val horizonHours: Int = 24,
    val leadTimeHours: Int = 1,
    val tempAboveEnabled: Boolean = false,
    val tempAboveValue: Double = 30.0,
    val tempBelowEnabled: Boolean = false,
    val tempBelowValue: Double = -10.0,
    val uvIndexEnabled: Boolean = false,
    val uvIndexValue: Double = 8.0,
    val windSpeedEnabled: Boolean = false,
    val windSpeedValue: Double = 15.0,
    val rainProbEnabled: Boolean = false,
    val rainProbValue: Int = 80,
    val thunderstormEnabled: Boolean = false,
    val snowEnabled: Boolean = false,
    // The provider's official severe weather warning, folded into the same
    // "notify me before it starts" lead-time behaviour as the other rules,
    // separate from the always-on Severe Weather Alerts card above it.
    val officialAlertEnabled: Boolean = true
)

/**
 * User-defined Bitcoin/currency price thresholds, checked periodically in
 * the background (independent of the daily digest schedule) and pushed as a
 * heads-up notification the moment the live price crosses a threshold - e.g.
 * "Bitcoin just went above €70,000". Unlike the weather alert rules above,
 * these are instantaneous (current price vs. threshold), not forecast-based,
 * so there's no lead time/horizon - just "notify me when it crosses".
 */
/** One asset's above/below alert thresholds - the same shape for Bitcoin, the main currency pair, or any extra pair the user adds. */
data class PairAlertRule(
    val aboveEnabled: Boolean = false,
    val aboveValue: Double = 0.0,
    val belowEnabled: Boolean = false,
    val belowValue: Double = 0.0
)

/**
 * One subscribed YouTube channel (Settings > YouTube Channels).
 * [baselineVideoId] is the newest video that already existed when the
 * channel was added, so the very first check doesn't flood in years of old
 * uploads as "new". [dismissedVideoIds] is every video (posted after that
 * baseline) the user has already dismissed or opened - each video is
 * dismissed independently, so if a channel posts two videos before you next
 * open the app, dismissing one doesn't hide the other.
 */
data class YoutubeChannelConfig(
    val channelId: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val baselineVideoId: String = "",
    val dismissedVideoIds: Set<String> = emptySet(),
    // Tracks which new videos have already triggered a push notification -
    // deliberately separate from dismissedVideoIds (which only controls the
    // dashboard's "New videos" section) so dismissing a video on the
    // dashboard doesn't cause it to be re-notified, and a notification
    // doesn't cause it to disappear from the dashboard.
    val notifiedVideoIds: Set<String> = emptySet()
)

data class PriceAlertRules(
    val enabled: Boolean = false,
    val bitcoin: PairAlertRule = PairAlertRule(aboveValue = 70000.0, belowValue = 50000.0),
    val mainCurrency: PairAlertRule = PairAlertRule(aboveValue = 11.5, belowValue = 10.5),
    // Keyed by "BASE/TARGET" (e.g. "USD/JPY") so every extra currency pair
    // added in Settings gets its own independent alert, same as Bitcoin.
    val extraPairs: Map<String, PairAlertRule> = emptyMap()
)

data class AppSettings(
    val userName: String = "Sasa",
    val city: String = "Tyristrand",
    val country: String = "NO",
    // First/legacy municipality plus the full set of municipalities monitored by Max.
    val policeMunicipality: String = "Ringerike",
    val policeMunicipalities: List<String> = listOf("Ringerike"),
    val policeAlertsEnabled: Boolean = true,
    // All public Politiloggen categories enabled by default.
    val policeCategories: Set<String> = com.morningdigest.app.data.remote.PoliceReportFetcher.CATEGORY_TRANSLATIONS.keys,
    val weatherApiKey: String = "8cf97e2ca0b40ba470fc324bac475ccb",
    val wakeHour: Int = 7,
    val wakeMinute: Int = 0,
    val scheduleMode: ScheduleMode = ScheduleMode.DAILY,
    val intervalHours: Int = 4,
    val darkMode: Boolean = false,
    val useSystemTheme: Boolean = true,
    // Whether the digest is posted automatically as a notification on the
    // schedule above (vs only ever being triggered manually via Refresh/Notify Now).
    val autoSendEnabled: Boolean = true,
    // Master switch for whether a notification is actually posted at all.
    // When off, the digest still refreshes and saves to history/widget, it
    // just won't interrupt with a notification banner.
    val notificationsEnabled: Boolean = true,
    // Currency pair shown on the dashboard/notification - configurable instead
    // of being hardcoded to EUR->NOK.
    val currencyBase: String = "EUR",
    val currencyTarget: String = "NOK",
    // Which RSS sources/topics feed the news section - lets the digest reflect
    // what the user actually cares about instead of one fixed outlet list.
    val selectedNewsFeedIds: Set<String> = NewsFeedCatalog.DEFAULT_SELECTED_IDS,
    // Extra RSS feeds/outlets the user has added themselves (e.g. Yahoo News),
    // on top of the catalog picks above. Unlimited - the user can add as many
    // of their own outlets as they want, and each becomes part of its topic
    // section (World by default) just like a built-in feed.
    val customFeeds: List<CustomFeed> = emptyList(),
    // Whether to check for severe weather alerts covering the configured location.
    val weatherAlertsEnabled: Boolean = true,
    // Optional dedicated cards, off by default, so the main World News feed
    // isn't a mix of everything - turn these on to get a focused Politics
    // and/or Business card on the dashboard, each with its own curated
    // sources (toggle-able the same way as the main News Sources list).
    val politicsNewsEnabled: Boolean = false,
    val businessNewsEnabled: Boolean = false,
    val selectedPoliticsFeedIds: Set<String> = PoliticsFeedCatalog.DEFAULT_SELECTED_IDS,
    val selectedBusinessFeedIds: Set<String> = BusinessFeedCatalog.DEFAULT_SELECTED_IDS,
    // User-defined weather alert thresholds (temp/UV/wind/rain/thunderstorm/
    // snow/official-alert), checked against the next 12-48h of forecast.
    val customAlertRules: CustomAlertRules = CustomAlertRules(),
    // User-defined Bitcoin/currency price thresholds, checked periodically
    // in the background - "notify me when Bitcoin/the currency pair crosses
    // this number", independent of the daily digest.
    val priceAlertRules: PriceAlertRules = PriceAlertRules(),
    // Channels subscribed to for "new video" bubbles on the dashboard.
    val youtubeChannels: List<YoutubeChannelConfig> = emptyList(),
    // Extra "From -> To" currency/crypto pairs shown as additional small
    // cards on the dashboard, beyond the primary Currency Pair above.
    val extraCurrencyPairs: List<CurrencyPairConfig> = emptyList(),
    // Order of the reorderable content cards on the dashboard (Settings > Dashboard Layout).
    val dashboardCardOrder: List<String> = DashboardCards.DEFAULT_ORDER,
    // Which analyst report cards are turned on - all six by default.
    val enabledAssistantIds: Set<String> = MascotCharacter.ALL_IDS
)

/**
 * Central place for reading/writing user-configurable settings. Everything
 * here is non-secret (no email credentials to protect since delivery is a
 * local notification, not SMTP), so it all lives in Jepack DataStore
 * (async, reactive via Flow).
 */
class SettingsRepository(private val context: Context) {

    private val gson = Gson()
    private val customFeedsListType = object : TypeToken<List<CustomFeed>>() {}.type

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val CITY = stringPreferencesKey("city")
        val COUNTRY = stringPreferencesKey("country")
        val POLICE_MUNICIPALITY = stringPreferencesKey("police_municipality")
        val POLICE_MUNICIPALITIES = stringSetPreferencesKey("police_municipalities")
        val POLICE_ALERTS_ENABLED = booleanPreferencesKey("police_alerts_enabled")
        val POLICE_CATEGORIES = stringSetPreferencesKey("police_categories")
        val WEATHER_KEY = stringPreferencesKey("weather_api_key")
        val WAKE_HOUR = intPreferencesKey("wake_hour")
        val WAKE_MINUTE = intPreferencesKey("wake_minute")
        val SCHEDULE_MODE = stringPreferencesKey("schedule_mode")
        val INTERVAL_HOURS = intPreferencesKey("interval_hours")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val USE_SYSTEM_THEME = booleanPreferencesKey("use_system_theme")
        val AUTO_SEND = booleanPreferencesKey("auto_send_enabled")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val CURRENCY_BASE = stringPreferencesKey("currency_base")
        val CURRENCY_TARGET = stringPreferencesKey("currency_target")
        val SELECTED_NEWS_FEEDS = stringSetPreferencesKey("selected_news_feed_ids")
        // JSON-encoded list of CustomFeed, replacing the old single custom_rss_url/
        // custom_rss_label pair so users aren't capped at one custom outlet.
        val CUSTOM_FEEDS_JSON = stringPreferencesKey("custom_feeds_json")
        val WEATHER_ALERTS_ENABLED = booleanPreferencesKey("weather_alerts_enabled")
        val POLITICS_NEWS_ENABLED = booleanPreferencesKey("politics_news_enabled")
        val BUSINESS_NEWS_ENABLED = booleanPreferencesKey("business_news_enabled")
        val SELECTED_POLITICS_FEEDS = stringSetPreferencesKey("selected_politics_feed_ids")
        val SELECTED_BUSINESS_FEEDS = stringSetPreferencesKey("selected_business_feed_ids")
        // JSON-encoded CustomAlertRules - one blob rather than a dozen separate
        // keys, since every field is always saved together from one Save button.
        val CUSTOM_ALERT_RULES_JSON = stringPreferencesKey("custom_alert_rules_json")
        // JSON-encoded PriceAlertRules - same one-blob pattern.
        val PRICE_ALERT_RULES_JSON = stringPreferencesKey("price_alert_rules_json")
        val YOUTUBE_CHANNELS_JSON = stringPreferencesKey("youtube_channels_json")
        // "Armed" state per price alert rule (ruleId -> currently past threshold,
        // already notified), so the periodic check only fires once per crossing
        // instead of every run while the price stays past the threshold - it
        // re-arms once the price moves back the other way.
        val PRICE_ALERT_ARMED_KEYS = stringSetPreferencesKey("price_alert_armed_keys")
        // Dedup set for the hourly alert-check worker, so the same predicted
        // threshold-crossing doesn't re-notify on every hourly check - each
        // entry is "type|triggerAtMillis", pruned of anything older than a few
        // hours by the worker before it writes back.
        val NOTIFIED_ALERT_KEYS = stringSetPreferencesKey("notified_alert_keys")
        val POLICE_SEEN_IDS = stringSetPreferencesKey("police_seen_ids")
        val POLICE_DISMISSED_IDS = stringSetPreferencesKey("police_dismissed_ids")
        // JSON-encoded List<CurrencyPairConfig> - one blob since the whole
        // list is always saved together from one Save button.
        val EXTRA_CURRENCY_PAIRS_JSON = stringPreferencesKey("extra_currency_pairs_json")
        val DASHBOARD_CARD_ORDER_JSON = stringPreferencesKey("dashboard_card_order_json")
        val ENABLED_ASSISTANT_IDS = stringSetPreferencesKey("enabled_assistant_ids")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            userName = prefs[Keys.USER_NAME] ?: "Sasa",
            city = prefs[Keys.CITY] ?: "Tyristrand",
            country = prefs[Keys.COUNTRY] ?: "NO",
            policeMunicipality = (prefs[Keys.POLICE_MUNICIPALITIES]?.toList()?.firstOrNull()
                ?: prefs[Keys.POLICE_MUNICIPALITY]
                ?: "Ringerike"),
            policeMunicipalities = (prefs[Keys.POLICE_MUNICIPALITIES]?.toList()?.sortedWith(String.CASE_INSENSITIVE_ORDER)
                ?: listOf(prefs[Keys.POLICE_MUNICIPALITY] ?: "Ringerike")),
            policeAlertsEnabled = prefs[Keys.POLICE_ALERTS_ENABLED] ?: true,
            policeCategories = prefs[Keys.POLICE_CATEGORIES] ?: com.morningdigest.app.data.remote.PoliceReportFetcher.CATEGORY_TRANSLATIONS.keys,
            weatherApiKey = prefs[Keys.WEATHER_KEY] ?: "8cf97e2ca0b40ba470fc324bac475ccb",
            wakeHour = prefs[Keys.WAKE_HOUR] ?: 7,
            wakeMinute = prefs[Keys.WAKE_MINUTE] ?: 0,
            scheduleMode = when (prefs[Keys.SCHEDULE_MODE]) {
                "INTERVAL" -> ScheduleMode.INTERVAL
                else -> ScheduleMode.DAILY
            },
            intervalHours = prefs[Keys.INTERVAL_HOURS] ?: 4,
            darkMode = prefs[Keys.DARK_MODE] ?: false,
            useSystemTheme = prefs[Keys.USE_SYSTEM_THEME] ?: true,
            autoSendEnabled = prefs[Keys.AUTO_SEND] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            currencyBase = prefs[Keys.CURRENCY_BASE] ?: "EUR",
            currencyTarget = prefs[Keys.CURRENCY_TARGET] ?: "NOK",
            selectedNewsFeedIds = prefs[Keys.SELECTED_NEWS_FEEDS] ?: NewsFeedCatalog.DEFAULT_SELECTED_IDS,
            customFeeds = prefs[Keys.CUSTOM_FEEDS_JSON]?.let { json ->
                runCatching { gson.fromJson<List<CustomFeed>>(json, customFeedsListType) }.getOrNull()
            } ?: emptyList(),
            weatherAlertsEnabled = prefs[Keys.WEATHER_ALERTS_ENABLED] ?: true,
            politicsNewsEnabled = prefs[Keys.POLITICS_NEWS_ENABLED] ?: false,
            businessNewsEnabled = prefs[Keys.BUSINESS_NEWS_ENABLED] ?: false,
            selectedPoliticsFeedIds = prefs[Keys.SELECTED_POLITICS_FEEDS] ?: PoliticsFeedCatalog.DEFAULT_SELECTED_IDS,
            selectedBusinessFeedIds = prefs[Keys.SELECTED_BUSINESS_FEEDS] ?: BusinessFeedCatalog.DEFAULT_SELECTED_IDS,
            customAlertRules = prefs[Keys.CUSTOM_ALERT_RULES_JSON]?.let { json ->
                runCatching { gson.fromJson(json, CustomAlertRules::class.java) }.getOrNull()
            } ?: CustomAlertRules(),
            priceAlertRules = prefs[Keys.PRICE_ALERT_RULES_JSON]?.let { json ->
                runCatching { gson.fromJson(json, PriceAlertRules::class.java) }.getOrNull()
            } ?: PriceAlertRules(),
            youtubeChannels = prefs[Keys.YOUTUBE_CHANNELS_JSON]?.let { json ->
                runCatching {
                    gson.fromJson<List<YoutubeChannelConfig>>(json, object : TypeToken<List<YoutubeChannelConfig>>() {}.type)
                }.getOrNull()
            } ?: emptyList(),
            extraCurrencyPairs = prefs[Keys.EXTRA_CURRENCY_PAIRS_JSON]?.let { json ->
                runCatching {
                    gson.fromJson<List<CurrencyPairConfig>>(json, object : TypeToken<List<CurrencyPairConfig>>() {}.type)
                }.getOrNull()
            } ?: emptyList(),
            dashboardCardOrder = DashboardCards.sanitize(
                prefs[Keys.DASHBOARD_CARD_ORDER_JSON]?.let { json ->
                    runCatching {
                        gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
                    }.getOrNull()
                } ?: DashboardCards.DEFAULT_ORDER
            ),
            enabledAssistantIds = prefs[Keys.ENABLED_ASSISTANT_IDS] ?: MascotCharacter.ALL_IDS
        )
    }

    suspend fun currentSettings(): AppSettings = settingsFlow.first()

    suspend fun updateUserName(name: String) {
        context.dataStore.edit { it[Keys.USER_NAME] = name }
    }

    suspend fun updateCityCountry(city: String, country: String) {
        context.dataStore.edit { it[Keys.CITY] = city; it[Keys.COUNTRY] = country }
    }

    suspend fun updatePoliceSettings(municipalities: List<String>, enabled: Boolean, categories: Set<String>) {
        val cleaned = municipalities.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val primary = cleaned.firstOrNull() ?: "Ringerike"
        context.dataStore.edit {
            it[Keys.POLICE_MUNICIPALITY] = primary
            it[Keys.POLICE_MUNICIPALITIES] = cleaned.toSet()
            it[Keys.POLICE_ALERTS_ENABLED] = enabled
            it[Keys.POLICE_CATEGORIES] = categories
        }
    }

    suspend fun getPoliceSeenIds(): Set<String> =
        context.dataStore.data.map { it[Keys.POLICE_SEEN_IDS] ?: emptySet() }.first()

    suspend fun setPoliceSeenIds(ids: Set<String>) {
        context.dataStore.edit { it[Keys.POLICE_SEEN_IDS] = ids.toList().takeLast(200).toSet() }
    }

    suspend fun getPoliceDismissedIds(): Set<String> =
        context.dataStore.data.map { it[Keys.POLICE_DISMISSED_IDS] ?: emptySet() }.first()

    suspend fun setPoliceDismissedIds(ids: Set<String>) {
        context.dataStore.edit { it[Keys.POLICE_DISMISSED_IDS] = ids.toList().takeLast(300).toSet() }
    }

    suspend fun dismissPoliceIncident(id: String) {
        val current = getPoliceDismissedIds()
        setPoliceDismissedIds((current + id).toList().takeLast(300).toSet())
    }

    suspend fun updateWeatherApiKey(key: String) {
        context.dataStore.edit { it[Keys.WEATHER_KEY] = key }
    }

    suspend fun updateWakeTime(hour: Int, minute: Int) {
        context.dataStore.edit { it[Keys.WAKE_HOUR] = hour; it[Keys.WAKE_MINUTE] = minute }
    }

    suspend fun updateScheduleMode(mode: ScheduleMode, intervalHours: Int) {
        context.dataStore.edit {
            it[Keys.SCHEDULE_MODE] = mode.name
            it[Keys.INTERVAL_HOURS] = intervalHours
        }
    }

    suspend fun updateDarkMode(dark: Boolean, useSystem: Boolean) {
        context.dataStore.edit {
            it[Keys.DARK_MODE] = dark
            it[Keys.USE_SYSTEM_THEME] = useSystem
        }
    }

    suspend fun updateAutoSend(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SEND] = enabled }
    }

    suspend fun updateNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun updateCurrencyPair(base: String, target: String) {
        context.dataStore.edit {
            it[Keys.CURRENCY_BASE] = base
            it[Keys.CURRENCY_TARGET] = target
        }
    }

    /**
     * Saves the catalog picks plus the user's own added outlets. [customFeeds]
     * is unbounded - there's no limit on how many outlets a user can add here.
     */
    suspend fun updateNewsFeeds(selectedIds: Set<String>, customFeeds: List<CustomFeed>) {
        context.dataStore.edit {
            it[Keys.SELECTED_NEWS_FEEDS] = selectedIds
            it[Keys.CUSTOM_FEEDS_JSON] = gson.toJson(customFeeds)
        }
    }

    suspend fun updateWeatherAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WEATHER_ALERTS_ENABLED] = enabled }
    }

    suspend fun updatePoliticsNewsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.POLITICS_NEWS_ENABLED] = enabled }
    }

    suspend fun updateBusinessNewsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BUSINESS_NEWS_ENABLED] = enabled }
    }

    suspend fun updatePoliticsFeeds(selectedIds: Set<String>) {
        context.dataStore.edit { it[Keys.SELECTED_POLITICS_FEEDS] = selectedIds }
    }

    suspend fun updateBusinessFeeds(selectedIds: Set<String>) {
        context.dataStore.edit { it[Keys.SELECTED_BUSINESS_FEEDS] = selectedIds }
    }

    suspend fun updateCustomAlertRules(rules: CustomAlertRules) {
        context.dataStore.edit { it[Keys.CUSTOM_ALERT_RULES_JSON] = gson.toJson(rules) }
    }

    suspend fun updatePriceAlertRules(rules: PriceAlertRules) {
        context.dataStore.edit { it[Keys.PRICE_ALERT_RULES_JSON] = gson.toJson(rules) }
    }

    suspend fun updateYoutubeChannels(channels: List<YoutubeChannelConfig>) {
        context.dataStore.edit { it[Keys.YOUTUBE_CHANNELS_JSON] = gson.toJson(channels) }
    }

    suspend fun getPriceAlertArmedKeys(): Set<String> =
        context.dataStore.data.map { it[Keys.PRICE_ALERT_ARMED_KEYS] ?: emptySet() }.first()

    suspend fun setPriceAlertArmedKeys(keys: Set<String>) {
        context.dataStore.edit { it[Keys.PRICE_ALERT_ARMED_KEYS] = keys }
    }

    suspend fun getNotifiedAlertKeys(): Set<String> =
        context.dataStore.data.map { it[Keys.NOTIFIED_ALERT_KEYS] ?: emptySet() }.first()

    suspend fun setNotifiedAlertKeys(keys: Set<String>) {
        context.dataStore.edit { it[Keys.NOTIFIED_ALERT_KEYS] = keys }
    }

    suspend fun updateExtraCurrencyPairs(pairs: List<CurrencyPairConfig>) {
        context.dataStore.edit { it[Keys.EXTRA_CURRENCY_PAIRS_JSON] = gson.toJson(pairs) }
    }

    suspend fun updateDashboardCardOrder(order: List<String>) {
        context.dataStore.edit { it[Keys.DASHBOARD_CARD_ORDER_JSON] = gson.toJson(order) }
    }

    suspend fun updateEnabledAssistants(ids: Set<String>) {
        context.dataStore.edit { it[Keys.ENABLED_ASSISTANT_IDS] = ids }
    }
}
