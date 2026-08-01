package com.morningdigest.app.data.model

import com.google.gson.annotations.SerializedName

/* ---------- OpenWeather raw DTOs ---------- */

data class CurrentWeatherResponse(
    val main: MainInfo?,
    val wind: WindInfo?,
    val weather: List<WeatherDesc>?,
    val sys: SysInfo?,
    val name: String?
)

data class MainInfo(
    val temp: Double,
    @SerializedName("feels_like") val feelsLike: Double,
    val humidity: Int,
    val pressure: Int,
    @SerializedName("temp_min") val tempMin: Double? = null,
    @SerializedName("temp_max") val tempMax: Double? = null
)

data class WindInfo(val speed: Double)

data class WeatherDesc(val description: String, val icon: String, val main: String)

data class SysInfo(val sunrise: Long, val sunset: Long)

data class ForecastResponse(val list: List<ForecastItem>?)

data class ForecastItem(
    val dt: Long,
    @SerializedName("dt_txt") val dtTxt: String,
    val main: MainInfo,
    val weather: List<WeatherDesc>,
    val wind: WindInfo? = null,
    val pop: Double?
)

/* ---------- CoinGecko raw DTO ---------- */

data class CoinGeckoResponse(
    val bitcoin: BitcoinPrices?
)

data class BitcoinPrices(
    val eur: Double?,
    val usd: Double?,
    val nok: Double?,
    @SerializedName("eur_24h_change") val eurChange24h: Double?,
    @SerializedName("usd_24h_change") val usdChange24h: Double?,
    @SerializedName("nok_24h_change") val nokChange24h: Double?
)

/** Generic single-coin price (always vs USD), used for the user's own watchlist entries below - keeps things simple across any coin id, unlike [BitcoinPrices] which is hardcoded to the primary Bitcoin card's fixed eur/usd/nok columns. */
data class GenericCoinPrice(
    val usd: Double? = null,
    @SerializedName("usd_24h_change") val usdChange24h: Double? = null
)

/* ---------- OpenWeather One Call (alerts) + Geocoding raw DTOs ---------- */

/** One entry from OpenWeather's geocoding endpoint - used to turn "city,country" into lat/lon for the alerts call. */
data class GeoLocationResponse(
    val name: String?,
    val lat: Double?,
    val lon: Double?,
    val country: String?
)

data class OneCallResponse(
    val alerts: List<OneCallAlert>?,
    // Hourly forecast (up to 48h), only present when the "hourly" block isn't
    // excluded from the request. This is what powers the custom weather alert
    // rules below - it's the only OpenWeather endpoint that exposes UV index
    // alongside temp/wind/pop/condition on an hourly (rather than 3-hourly) grid.
    val hourly: List<OneCallHourly>? = null
)

data class OneCallAlert(
    @SerializedName("sender_name") val senderName: String?,
    val event: String?,
    val start: Long?,
    val end: Long?,
    val description: String?
)

/** One hourly slot from OpenWeather's One Call 3.0 "hourly" block. */
data class OneCallHourly(
    val dt: Long,
    val temp: Double? = null,
    @SerializedName("wind_speed") val windSpeed: Double? = null,
    /** Probability of precipitation, 0.0-1.0. */
    val pop: Double? = null,
    /** UV index. */
    val uvi: Double? = null,
    val weather: List<WeatherDesc>? = null
)

/* ---------- Frankfurter raw DTO ---------- */

data class FrankfurterResponse(
    val amount: Double?,
    val base: String?,
    val date: String?,
    val rates: Map<String, Double>?
)

/** Frankfurter's time-series endpoint: date string -> { "NOK" -> rate }. Used for the EUR/NOK chart. */
data class FrankfurterTimeSeriesResponse(
    val amount: Double?,
    val base: String?,
    @SerializedName("start_date") val startDate: String?,
    @SerializedName("end_date") val endDate: String?,
    val rates: Map<String, Map<String, Double>>?
)

/* ---------- CoinGecko market_chart raw DTO ---------- */

/** Each entry is [epochMillis, price] - CoinGecko returns raw two-element arrays, not objects. */
data class MarketChartResponse(
    val prices: List<List<Double>>?
)

/* ---------- Clean, UI-ready domain models ---------- */

data class WeatherToday(
    val temp: Double? = null,
    val feelsLike: Double? = null,
    val humidity: Int? = null,
    val windSpeed: Double? = null,
    val pressure: Int? = null,
    val sunrise: Long? = null,
    val sunset: Long? = null,
    val description: String? = null,
    val icon: String? = null,
    // Today's forecast high/low, shown alongside the current reading like most weather apps.
    val tempMin: Double? = null,
    val tempMax: Double? = null,
    /** Morning / afternoon / evening breakdown for the rest of today, each sampled from the nearest 3h forecast slot. */
    val parts: List<DayPartForecast> = emptyList(),
    val available: Boolean = false
)

data class WeatherTomorrow(
    val avgTemp: Double? = null,
    val minTemp: Double? = null,
    val maxTemp: Double? = null,
    val humidity: Int? = null,
    val windSpeed: Double? = null,
    val description: String? = null,
    val icon: String? = null,
    val rainChancePercent: Int? = null,
    /** Morning / afternoon / evening breakdown for tomorrow, each sampled from the nearest 3h forecast slot. */
    val parts: List<DayPartForecast> = emptyList(),
    val available: Boolean = false
)

/** One part of tomorrow's day (morning/afternoon/evening) shown in the Tomorrow card's breakdown row. */
data class DayPartForecast(
    val label: String,
    val temp: Double? = null,
    val description: String? = null,
    val icon: String? = null
)

/** One extra day (day-after-tomorrow, etc.) shown in the compact "Next 3 Days" strip and the extended forecast view. */
data class WeatherDayForecast(
    /** Short weekday label, e.g. "Wed". */
    val dayLabel: String,
    val minTemp: Double? = null,
    val maxTemp: Double? = null,
    val description: String? = null,
    val icon: String? = null,
    val rainChancePercent: Int? = null,
    /** Short calendar date label, e.g. "Jul 28" - only used by the extended forecast view. */
    val dateLabel: String? = null,
    val humidity: Int? = null,
    val windSpeed: Double? = null
)

data class BitcoinInfo(
    val eur: Double? = null,
    val usd: Double? = null,
    val nok: Double? = null,
    val change24hPercent: Double? = null,
    val changeTodayPercent: Double? = null,
    val available: Boolean = false,
    val updatedAtMillis: Long? = null
)

data class CurrencyInfo(
    val rate: Double? = null,
    val change24hPercent: Double? = null,
    /** Change from the start of the current UTC day to the latest fetched rate. */
    val changeTodayPercent: Double? = null,
    /** e.g. "EUR" / "NOK" — configurable in Settings, so the UI/notification labels stay in sync with whatever pair was actually fetched. */
    val baseCurrency: String = "EUR",
    val targetCurrency: String = "NOK",
    val available: Boolean = false,
    val updatedAtMillis: Long? = null,
    val consensusSources: List<String> = emptyList(),
    val sourceSpreadPercent: Double? = null
)

/**
 * One extra currency or crypto pair the user added to their watchlist in
 * Settings (beyond the single primary Bitcoin/Currency cards). Crypto
 * entries are always priced in USD for simplicity; currency entries are
 * priced against the user's configured base currency (Settings > Currency Pair).
 */
data class WatchlistEntry(
    /** CoinGecko id for crypto (e.g. "ethereum"), or the 3-letter code for currency (e.g. "GBP"). */
    val id: String = "",
    /** e.g. "Ethereum (ETH)" or "EUR → GBP". */
    val label: String = "",
    val isCrypto: Boolean = true,
    val value: Double? = null,
    /** Crypto only - Frankfurter has no built-in 24h change for arbitrary pairs, so extra currency entries just show the current rate. */
    val change24hPercent: Double? = null,
    val available: Boolean = false
)

/** A single severe weather alert (storm, flood, extreme heat, etc.) from the national weather service covering the configured location. */
data class WeatherAlert(
    val event: String = "",
    val description: String = "",
    val senderName: String = "",
    val startMillis: Long? = null,
    val endMillis: Long? = null
)

data class WeatherAlertsInfo(
    val alerts: List<WeatherAlert> = emptyList(),
    val available: Boolean = false,
    // User-defined threshold rules (temperature/UV/wind/rain/thunderstorm/snow/
    // official-alert), evaluated against the next 12/24/48h forecast - separate
    // from [alerts] above, which only ever holds the provider's own official
    // severe weather warnings.
    val customAlerts: List<CustomAlertMatch> = emptyList()
)

/** Which custom rule (configured in Settings) a [CustomAlertMatch] came from. */
enum class AlertRuleType {
    TEMP_ABOVE, TEMP_BELOW, UV_INDEX, WIND_SPEED, RAIN_PROBABILITY, THUNDERSTORM, SNOW, OFFICIAL_SEVERE
}

/**
 * One user-defined weather alert rule that matched somewhere in the forecast
 * horizon the user configured (12/24/48h). [leadWarning] is true once the
 * matched time falls inside the "notify me before it happens" lead window
 * (also user-configurable), which is what triggers the actual push
 * notification - matches further out just show up quietly in the UI as a
 * heads-up of what's coming.
 */
data class CustomAlertMatch(
    val type: AlertRuleType = AlertRuleType.TEMP_ABOVE,
    /** Short label, e.g. "Temperature above 30°C". */
    val label: String = "",
    /** e.g. "Today, 14:00 — Forecast 32.4°C". */
    val detail: String = "",
    /** "Today" / "Tomorrow" / "In 2 days" / etc. - shown separately in the UI so the day is easy to scan at a glance. */
    val dayLabel: String = "",
    val triggerAtMillis: Long = 0L,
    val leadWarning: Boolean = false
)

/** One Bitcoin/currency threshold that just got crossed - powers the price alert notification. */
data class PriceAlertHit(
    /** Stable id for dedup/re-arming, e.g. "btc_above", "fx_below". */
    val ruleId: String,
    /** Short label, e.g. "Bitcoin above €70,000". */
    val label: String,
    /** e.g. "Now at €71,240". */
    val detail: String
)

data class NewsHeadline(
    val title: String,
    val link: String,
    val source: String = "",
    /** Publish time from the feed's <pubDate>, in epoch millis. Null if the feed omitted it or it couldn't be parsed. */
    val pubDateMillis: Long? = null
)

data class NewsInfo(
    val headlines: List<NewsHeadline> = emptyList(),
    val available: Boolean = false
)

/**
 * One stock that's among today's biggest movers (either gainers or losers,
 * depending on which list it's in) - powers Bully's "biggest winners" and
 * Beary's "biggest losers" briefings.
 */
data class StockMover(
    val symbol: String = "",
    val name: String = "",
    val price: Double? = null,
    val changePercent: Double? = null
)

/**
 * "This channel posted a new video since you last checked" - one bubble on
 * the dashboard, shown under the Fact of the Day card. Only present in the
 * report while the video is genuinely new (i.e. newer than that channel's
 * dismissed/seen video, tracked in Settings) - dismissing it or opening the
 * video both mark it as seen, so it won't reappear until that channel posts
 * something newer still.
 */
data class YoutubeVideoUpdate(
    val channelId: String = "",
    val channelName: String = "",
    val avatarUrl: String = "",
    val videoId: String = "",
    val videoTitle: String = "",
    val videoLink: String = "",
    val publishedMillis: Long = 0L
)

/** One "fact of the day" shown above the news feed. Re-picked on every refresh. */
data class DailyFact(
    val category: String = "",
    val text: String = ""
)

/** A single (time, value) sample used to draw the Bitcoin / EUR-NOK history charts. */
data class ChartPoint(
    val timestampMillis: Long,
    val value: Double
)

/**
 * The full combined digest for one morning run - what gets rendered into the
 * dashboard, the email body, and stored in history.
 */
data class DigestReport(
    val id: Long = 0L,
    val timestampMillis: Long,
    val weatherToday: WeatherToday,
    val weatherTomorrow: WeatherTomorrow,
    /** Day-after-tomorrow and the day after that - the rest of the "next 3 days" strip beyond weatherTomorrow. */
    val upcomingDays: List<WeatherDayForecast> = emptyList(),
    val bitcoin: BitcoinInfo,
    val currency: CurrencyInfo,
    val news: NewsInfo,
    val dailyFact: DailyFact = DailyFact(),
    val weatherAlerts: WeatherAlertsInfo = WeatherAlertsInfo(),
    // Optional dedicated sections, only populated when the user has turned
    // them on in Settings - each capped to its own top-10 headlines.
    val politicsNews: NewsInfo = NewsInfo(),
    val businessNews: NewsInfo = NewsInfo(),
    // Extra currency/crypto pairs the user added in Settings > Watchlist,
    // beyond the primary Bitcoin/Currency cards above.
    val watchlist: List<WatchlistEntry> = emptyList(),
    // Small, always-fetched news slices purely for the analyst report cards
    // (Bully/Beary share marketsNews, Satoshi uses cryptoNews) - separate
    // from the user-configurable World/Politics/Business sections above.
    val marketsNews: NewsInfo = NewsInfo(),
    val cryptoNews: NewsInfo = NewsInfo(),
    // Today's top 5 biggest stock-market gainers/losers, for Bully/Beary's
    // "biggest winners"/"biggest losers" briefings.
    val stockGainers: List<StockMover> = emptyList(),
    val stockLosers: List<StockMover> = emptyList(),
    // Any subscribed YouTube channel that's posted a new video since it was
    // last dismissed/opened - powers the bubble row under Fact of the Day.
    val youtubeUpdates: List<YoutubeVideoUpdate> = emptyList(),
    val notificationSent: Boolean = false,
    val notificationError: String? = null
)

enum class SendStatus { IDLE, RUNNING, SUCCESS, FAILED }
