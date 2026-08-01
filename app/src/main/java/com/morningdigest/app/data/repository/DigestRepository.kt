package com.morningdigest.app.data.repository

import com.morningdigest.app.data.alert.CustomWeatherAlertEngine
import com.morningdigest.app.data.facts.FactProvider
import com.morningdigest.app.data.local.AppDatabase
import com.morningdigest.app.data.local.ReportDao
import com.morningdigest.app.data.local.ReportMapper
import com.morningdigest.app.data.model.BitcoinInfo
import com.morningdigest.app.data.model.ChartPoint
import com.morningdigest.app.data.model.CurrencyInfo
import com.morningdigest.app.data.model.DayPartForecast
import com.morningdigest.app.data.model.DigestReport
import com.morningdigest.app.data.model.NewsHeadline
import com.morningdigest.app.data.model.NewsInfo
import com.morningdigest.app.data.model.OneCallHourly
import com.morningdigest.app.data.model.StockMover
import com.morningdigest.app.data.model.WeatherAlert
import com.morningdigest.app.data.model.WeatherAlertsInfo
import com.morningdigest.app.data.model.YoutubeVideoUpdate
import com.morningdigest.app.data.model.WeatherDayForecast
import com.morningdigest.app.data.model.WeatherToday
import com.morningdigest.app.data.model.WeatherTomorrow
import com.morningdigest.app.data.model.WatchlistEntry
import com.morningdigest.app.data.prefs.AppSettings
import com.morningdigest.app.data.prefs.AssetRef
import com.morningdigest.app.data.prefs.AssetType
import com.morningdigest.app.data.prefs.CurrencyPairConfig
import com.morningdigest.app.data.prefs.CustomAlertRules
import com.morningdigest.app.data.prefs.YoutubeChannelConfig
import com.morningdigest.app.data.remote.CoinGeckoApi
import com.morningdigest.app.data.remote.CryptoCatalog
import com.morningdigest.app.data.remote.FrankfurterApi
import com.morningdigest.app.data.remote.LiveCurrencyFetcher
import com.morningdigest.app.data.remote.BusinessFeedCatalog
import com.morningdigest.app.data.remote.NewsFeedCatalog
import com.morningdigest.app.data.remote.OpenWeatherApi
import com.morningdigest.app.data.remote.PoliticsFeedCatalog
import com.morningdigest.app.data.remote.RssFeedFetcher
import com.morningdigest.app.data.remote.StockMoversFetcher
import com.morningdigest.app.data.remote.YoutubeChannelFetcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

private const val TAG = "DigestRepository"
private const val HISTORY_LIMIT = 30
// How many of the most recently shown facts to avoid repeating - comfortably
// less than the pool size so there's always a real choice left.
private const val RECENT_FACTS_WINDOW = 10
// Each outlet is polled for a decent-sized pool (NEWS_PER_OUTLET_LIMIT) so
// there's enough to pick from; the combined, deduped result is then sorted
// newest-first and hard-capped at NEWS_TARGET_COUNT for the dashboard/notification.
private const val NEWS_PER_OUTLET_LIMIT = 30
private const val NEWS_TARGET_COUNT = 30
// Politics/Business are focused, single-topic cards - 30 headlines each,
// newest first, refreshed on every pull-to-refresh.
private const val TOPIC_NEWS_TARGET_COUNT = 30
// How many of today's biggest stock movers Bully/Beary each report on.
private const val STOCK_MOVERS_COUNT = 5

/**
 * Fetches weather (today + tomorrow), Bitcoin, EUR->NOK, and world news from
 * several outlets in parallel. Mirrors the original n8n workflow's "Continue
 * On Fail" behaviour: if one source fails, the others still complete and the
 * failing section is marked unavailable ("Unavailable") instead of aborting
 * the whole digest.
 */
class DigestRepository(
    private val weatherApi: OpenWeatherApi,
    private val coinGeckoApi: CoinGeckoApi,
    private val frankfurterApi: FrankfurterApi,
    private val rssFetcher: RssFeedFetcher,
    private val reportDao: ReportDao,
    private val stockMoversFetcher: StockMoversFetcher? = null,
    private val youtubeChannelFetcher: YoutubeChannelFetcher? = null,
    private val liveCurrencyFetcher: LiveCurrencyFetcher? = null
) {

    suspend fun buildFreshReport(settings: AppSettings): DigestReport = coroutineScope {
        val cityQuery = "${settings.city},${settings.country}"

        // Previously-seen headline keys, so a fresh fetch can push genuinely
        // new stories to the top of the list (see fetchNews below) instead of
        // relying purely on each outlet's own pubDate, which some feeds omit
        // or misreport. Tracked separately per section so Politics/Business
        // each get their own "new on top" ordering instead of sharing one.
        val previousReport = runCatching {
            reportDao.getLatest()?.let { ReportMapper.toDomain(it) }
        }.getOrNull()
        // Facts shown in the last several reports (not just the very last
        // one), so a small refresh-happy testing session doesn't keep
        // cycling through the same handful of facts - skip all of them
        // where possible when picking a new one below.
        val recentFactTexts = runCatching {
            reportDao.getRecent(RECENT_FACTS_WINDOW)
                .mapNotNull { ReportMapper.toDomain(it).dailyFact.text.takeIf(String::isNotBlank) }
                .toSet()
        }.getOrElse { emptySet() }
        fun keysOf(info: NewsInfo?) = info?.headlines?.map { it.title.trim().lowercase() }?.toSet() ?: emptySet()
        val previousHeadlineKeys = keysOf(previousReport?.news)
        val previousPoliticsKeys = keysOf(previousReport?.politicsNews)
        val previousBusinessKeys = keysOf(previousReport?.businessNews)

        val todayDeferred = async { fetchWeatherToday(cityQuery, settings.weatherApiKey) }
        val tomorrowBundleDeferred = async { fetchTomorrowAndUpcoming(cityQuery, settings.weatherApiKey) }
        val bitcoinDeferred = async { fetchBitcoin() }
        val currencyDeferred = async { fetchCurrency(settings.currencyBase, settings.currencyTarget) }
        val newsDeferred = async { fetchNews(settings, previousHeadlineKeys) }
        val politicsDeferred = async { fetchPoliticsNews(settings, previousPoliticsKeys) }
        val businessDeferred = async { fetchBusinessNews(settings, previousBusinessKeys) }
        val marketsNewsDeferred = async { runCatching { fetchMarketsNews() }.getOrElse { NewsInfo(available = false) } }
        val cryptoNewsDeferred = async { runCatching { fetchCryptoNews() }.getOrElse { NewsInfo(available = false) } }
        val stockGainersDeferred = async { fetchStockGainers() }
        val stockLosersDeferred = async { fetchStockLosers() }
        val youtubeUpdatesDeferred = async { fetchYoutubeUpdates(settings.youtubeChannels) }
        val alertsDeferred = async {
            if (settings.weatherAlertsEnabled || settings.customAlertRules.enabled)
                fetchWeatherAlerts(cityQuery, settings.weatherApiKey, settings.customAlertRules)
            else WeatherAlertsInfo(available = false)
        }
        val watchlistDeferred = async {
            if (settings.extraCurrencyPairs.isNotEmpty()) fetchExtraCurrencyPairs(settings.extraCurrencyPairs)
            else emptyList()
        }

        val (todayParts, tomorrow, upcomingDays) = tomorrowBundleDeferred.await()

        DigestReport(
            timestampMillis = System.currentTimeMillis(),
            weatherToday = todayDeferred.await().copy(parts = todayParts),
            weatherTomorrow = tomorrow,
            upcomingDays = upcomingDays,
            bitcoin = bitcoinDeferred.await(),
            currency = currencyDeferred.await(),
            news = newsDeferred.await(),
            // Re-picked on every refresh (manual or scheduled) so it changes
            // whenever the user pulls to refresh, not just once per day -
            // excluding whatever's shown up in the last several reports so
            // it doesn't keep cycling through the same few facts.
            dailyFact = FactProvider.randomFact(excludeTexts = recentFactTexts),
            weatherAlerts = alertsDeferred.await(),
            politicsNews = politicsDeferred.await(),
            businessNews = businessDeferred.await(),
            watchlist = watchlistDeferred.await(),
            marketsNews = marketsNewsDeferred.await(),
            cryptoNews = cryptoNewsDeferred.await(),
            stockGainers = stockGainersDeferred.await(),
            stockLosers = stockLosersDeferred.await(),
            youtubeUpdates = youtubeUpdatesDeferred.await()
        )
    }

    /**
     * Lighter-weight refresh for the pull-to-refresh gesture on the main
     * dashboard. The dashboard only shows weather, Bitcoin/currency, the
     * daily fact, and alerts now - Politics/Business/World News and stock
     * movers moved to the Assistants tab, so re-fetching ~90 headlines plus
     * two stock screener calls on every dashboard pull was pure wasted time
     * the user was sitting there waiting on. Those sections just carry over
     * from whatever's already saved (kept fresh by the background worker and
     * by each assistant's own refresh button) instead of being re-fetched
     * here. Falls back to [buildFreshReport] on the very first run when
     * there's nothing saved yet to carry over.
     */
    suspend fun refreshDashboardSection(settings: AppSettings): DigestReport = coroutineScope {
        val previous = runCatching { reportDao.getLatest()?.let { ReportMapper.toDomain(it) } }.getOrNull()
            ?: return@coroutineScope buildFreshReport(settings)
        val recentFactTexts = runCatching {
            reportDao.getRecent(RECENT_FACTS_WINDOW)
                .mapNotNull { ReportMapper.toDomain(it).dailyFact.text.takeIf(String::isNotBlank) }
                .toSet()
        }.getOrElse { emptySet() }
        val cityQuery = "${settings.city},${settings.country}"

        val todayDeferred = async { fetchWeatherToday(cityQuery, settings.weatherApiKey) }
        val tomorrowBundleDeferred = async { fetchTomorrowAndUpcoming(cityQuery, settings.weatherApiKey) }
        val bitcoinDeferred = async { fetchBitcoin() }
        val currencyDeferred = async { fetchCurrency(settings.currencyBase, settings.currencyTarget) }
        val alertsDeferred = async {
            if (settings.weatherAlertsEnabled || settings.customAlertRules.enabled)
                fetchWeatherAlerts(cityQuery, settings.weatherApiKey, settings.customAlertRules)
            else WeatherAlertsInfo(available = false)
        }
        val watchlistDeferred = async {
            if (settings.extraCurrencyPairs.isNotEmpty()) fetchExtraCurrencyPairs(settings.extraCurrencyPairs)
            else emptyList()
        }
        val youtubeUpdatesDeferred = async { fetchYoutubeUpdates(settings.youtubeChannels) }

        val (todayParts, freshTomorrow, freshUpcomingDays) = tomorrowBundleDeferred.await()
        val freshWeatherToday = todayDeferred.await().copy(parts = todayParts)
        val freshBitcoin = bitcoinDeferred.await()
        val freshCurrency = currencyDeferred.await()
        val freshAlerts = alertsDeferred.await()
        val freshWatchlist = watchlistDeferred.await()

        // A failed fetch for any of these sections returns an "unavailable"
        // placeholder (see each fetchXxx's runCatching/getOrElse) so refresh
        // itself never throws - but always swapping that placeholder straight
        // into the report meant one bad network call for, say, Bitcoin could
        // blank out a price that was showing fine ten seconds ago. Keep the
        // previous value instead whenever the fresh one failed AND the
        // previous one was itself a real success - only when neither the new
        // nor the last-known fetch ever actually succeeded does "unavailable"
        // show through to the UI.
        val weatherToday = if (freshWeatherToday.available) freshWeatherToday
            else previous.weatherToday.takeIf { it.available } ?: freshWeatherToday
        // Tomorrow's forecast and the extra upcoming days come from the same
        // underlying call, so they fall back together as one unit rather than
        // risking a tomorrow/upcomingDays pair from two different moments in time.
        val tomorrowBundleFresh = freshTomorrow.available
        val weatherTomorrow = if (tomorrowBundleFresh) freshTomorrow
            else previous.weatherTomorrow.takeIf { it.available } ?: freshTomorrow
        val upcomingDays = if (tomorrowBundleFresh) freshUpcomingDays
            else if (previous.weatherTomorrow.available) previous.upcomingDays else freshUpcomingDays
        val bitcoin = if (freshBitcoin.available) freshBitcoin
            else previous.bitcoin.takeIf { it.available } ?: freshBitcoin
        val currency = if (freshCurrency.available) freshCurrency
            else previous.currency.takeIf { it.available } ?: freshCurrency
        val weatherAlerts = if (freshAlerts.available) freshAlerts
            else previous.weatherAlerts.takeIf { it.available } ?: freshAlerts
        // Per-pair, keyed by id: a config change (added/removed/reordered
        // pairs) means the fresh and previous lists don't necessarily line up
        // position-for-position, so each entry falls back independently.
        val previousWatchlistById = previous.watchlist.associateBy { it.id }
        val watchlist = freshWatchlist.map { entry ->
            if (entry.available) entry
            else previousWatchlistById[entry.id]?.takeIf { it.available } ?: entry
        }

        val updated = previous.copy(
            timestampMillis = System.currentTimeMillis(),
            weatherToday = weatherToday,
            weatherTomorrow = weatherTomorrow,
            upcomingDays = upcomingDays,
            bitcoin = bitcoin,
            currency = currency,
            dailyFact = FactProvider.randomFact(excludeTexts = recentFactTexts),
            weatherAlerts = weatherAlerts,
            watchlist = watchlist,
            youtubeUpdates = youtubeUpdatesDeferred.await()
        )
        saveReport(updated)
    }

    /** Result of trying to add a channel in Settings. */
    sealed class AddYoutubeChannelResult {
        data class Success(val channel: YoutubeChannelConfig) : AddYoutubeChannelResult()
        data class Failure(val reason: String) : AddYoutubeChannelResult()
    }

    /**
     * Resolves whatever the user pasted (channel URL, @handle, or bare ID)
     * into a real channel, and establishes its baseline as its current
     * newest video - so adding a long-running channel doesn't immediately
     * dump years of old uploads onto the dashboard as "new".
     */
    suspend fun addYoutubeChannel(input: String): AddYoutubeChannelResult {
        val fetcher = youtubeChannelFetcher
            ?: return AddYoutubeChannelResult.Failure("YouTube channels aren't available right now")

        return when (val result = fetcher.resolveChannel(input)) {
            is YoutubeChannelFetcher.ResolveResult.Failure -> AddYoutubeChannelResult.Failure(result.reason)
            is YoutubeChannelFetcher.ResolveResult.Success -> {
                val resolved = result.channel
                val baseline = runCatching { fetcher.fetchLatestVideoId(resolved.channelId) }
                    .getOrNull().orEmpty()
                AddYoutubeChannelResult.Success(
                    YoutubeChannelConfig(
                        channelId = resolved.channelId,
                        name = resolved.name,
                        avatarUrl = resolved.avatarUrl,
                        baselineVideoId = baseline
                    )
                )
            }
        }
    }

    private suspend fun fetchStockGainers(): List<StockMover> =
        stockMoversFetcher?.let { runCatching { it.fetchTopGainers(STOCK_MOVERS_COUNT) }.getOrElse { emptyList() } } ?: emptyList()

    private suspend fun fetchStockLosers(): List<StockMover> =
        stockMoversFetcher?.let { runCatching { it.fetchTopLosers(STOCK_MOVERS_COUNT) }.getOrElse { emptyList() } } ?: emptyList()

    /**
     * Every not-yet-dismissed video, across every subscribed channel, posted
     * after that channel's baseline - i.e. everything that should currently
     * show a bubble on the dashboard. Channels are checked in parallel;
     * a single channel failing (deleted, offline, etc) just contributes
     * nothing rather than failing the whole batch.
     */
    /** Public entry point for [com.morningdigest.app.worker.YoutubeCheckWorker] - same lookup the dashboard uses, just without building/saving a whole report. */
    suspend fun checkYoutubeChannelsForNewVideos(channels: List<YoutubeChannelConfig>): List<YoutubeVideoUpdate> =
        fetchYoutubeUpdates(channels)

    private suspend fun fetchYoutubeUpdates(channels: List<YoutubeChannelConfig>): List<YoutubeVideoUpdate> = coroutineScope {
        val fetcher = youtubeChannelFetcher ?: return@coroutineScope emptyList()
        if (channels.isEmpty()) return@coroutineScope emptyList()

        channels.map { channel ->
            async {
                if (channel.channelId.isBlank()) return@async emptyList()
                val recent = runCatching { fetcher.fetchRecentVideos(channel.channelId) }.getOrElse { emptyList() }
                if (channel.baselineVideoId.isBlank()) return@async emptyList()

                recent
                    .takeWhile { it.videoId != channel.baselineVideoId }
                    .filter { it.videoId !in channel.dismissedVideoIds }
                    .map { entry ->
                        YoutubeVideoUpdate(
                            channelId = channel.channelId,
                            channelName = channel.name,
                            avatarUrl = channel.avatarUrl,
                            videoId = entry.videoId,
                            videoTitle = entry.title,
                            videoLink = entry.link,
                            publishedMillis = entry.publishedMillis
                        )
                    }
            }
        }.awaitAll().flatten()
    }

    private suspend fun fetchWeatherToday(cityQuery: String, apiKey: String): WeatherToday =
        runCatching {
            val r = weatherApi.getCurrentWeather(cityQuery, apiKey = apiKey)
            WeatherToday(
                temp = r.main?.temp,
                feelsLike = r.main?.feelsLike,
                humidity = r.main?.humidity,
                windSpeed = r.wind?.speed,
                pressure = r.main?.pressure,
                sunrise = r.sys?.sunrise?.let { TimeUnit.SECONDS.toMillis(it) },
                sunset = r.sys?.sunset?.let { TimeUnit.SECONDS.toMillis(it) },
                description = r.weather?.firstOrNull()?.description,
                icon = r.weather?.firstOrNull()?.icon,
                tempMin = r.main?.tempMin,
                tempMax = r.main?.tempMax,
                available = true
            )
        }.getOrElse { WeatherToday(available = false) }

    /**
     * Single forecast call, split into today's remaining morning/afternoon/
     * evening breakdown, "tomorrow" (detailed, as before), and every further
     * day the free 5-day/3-hour forecast endpoint actually returns (usually
     * 3-4 more) for the extended forecast view - reusing one API response
     * instead of calling the forecast endpoint again per extra day.
     *
     * Note: OpenWeather's free forecast endpoint only covers ~5 days total,
     * so [upcomingDays] tops out there - it does not extend to a full month.
     */
    private suspend fun fetchTomorrowAndUpcoming(cityQuery: String, apiKey: String): Triple<List<DayPartForecast>, WeatherTomorrow, List<WeatherDayForecast>> =
        runCatching {
            val forecast = weatherApi.getForecast(cityQuery, apiKey = apiKey)
            val list = forecast.list.orEmpty()
            if (list.isEmpty()) return@runCatching Triple(emptyList(), WeatherTomorrow(available = false), emptyList())

            val firstDate = list.first().dtTxt.substringBefore(" ")
            val todayItems = list.filter { it.dtTxt.substringBefore(" ") == firstDate }

            // Same morning/afternoon/evening sampling as tomorrow, applied to
            // whatever's left of today's forecast slots.
            fun closestToToday(hour: Int) = todayItems.minByOrNull { item ->
                val itemHour = item.dtTxt.substringAfter(" ").substringBefore(":").toIntOrNull() ?: 12
                kotlin.math.abs(itemHour - hour)
            }
            val todayParts = listOfNotNull(
                closestToToday(9)?.let { DayPartForecast("Morning", it.main.temp, it.weather.firstOrNull()?.description, it.weather.firstOrNull()?.icon) },
                closestToToday(15)?.let { DayPartForecast("Afternoon", it.main.temp, it.weather.firstOrNull()?.description, it.weather.firstOrNull()?.icon) },
                closestToToday(21)?.let { DayPartForecast("Evening", it.main.temp, it.weather.firstOrNull()?.description, it.weather.firstOrNull()?.icon) }
            )

            // Group every slot after today by calendar date, in order, so
            // "tomorrow", "day after", and every further available day can be
            // pulled out cleanly instead of relying on a fixed item-count slice.
            val futureByDate = list
                .filter { it.dtTxt.substringBefore(" ") != firstDate }
                .groupBy { it.dtTxt.substringBefore(" ") }
                .toSortedMap()
            val futureDates = futureByDate.keys.toList()

            var tomorrowItems = futureDates.getOrNull(0)?.let { futureByDate[it] }.orEmpty()
            if (tomorrowItems.isEmpty()) tomorrowItems = list.take(8)
            if (tomorrowItems.isEmpty()) return@runCatching Triple(todayParts, WeatherTomorrow(available = false), emptyList())

            val avgTemp = tomorrowItems.map { it.main.temp }.average()
            val midday = tomorrowItems.firstOrNull { it.dtTxt.contains("12:00:00") }
                ?: tomorrowItems[tomorrowItems.size / 2]
            val maxPop = tomorrowItems.mapNotNull { it.pop }.maxOrNull() ?: 0.0
            val minTemp = tomorrowItems.minOf { it.main.temp }
            val maxTemp = tomorrowItems.maxOf { it.main.temp }
            val avgHumidity = tomorrowItems.map { it.main.humidity }.average()

            // Morning/afternoon/evening breakdown: pick the 3h slot closest to
            // 09:00, 15:00 and 21:00 respectively, so the card can show more
            // than just a single midday snapshot.
            fun closestTo(hour: Int) = tomorrowItems.minByOrNull { item ->
                val itemHour = item.dtTxt.substringAfter(" ").substringBefore(":").toIntOrNull() ?: 12
                kotlin.math.abs(itemHour - hour)
            }
            val parts = listOfNotNull(
                closestTo(9)?.let { DayPartForecast("Morning", it.main.temp, it.weather.firstOrNull()?.description, it.weather.firstOrNull()?.icon) },
                closestTo(15)?.let { DayPartForecast("Afternoon", it.main.temp, it.weather.firstOrNull()?.description, it.weather.firstOrNull()?.icon) },
                closestTo(21)?.let { DayPartForecast("Evening", it.main.temp, it.weather.firstOrNull()?.description, it.weather.firstOrNull()?.icon) }
            )

            val tomorrow = WeatherTomorrow(
                avgTemp = Math.round(avgTemp * 10) / 10.0,
                minTemp = Math.round(minTemp * 10) / 10.0,
                maxTemp = Math.round(maxTemp * 10) / 10.0,
                humidity = Math.round(avgHumidity).toInt(),
                windSpeed = midday.wind?.speed,
                description = midday.weather.firstOrNull()?.description,
                icon = midday.weather.firstOrNull()?.icon,
                rainChancePercent = Math.round(maxPop * 100).toInt(),
                parts = parts,
                available = true
            )

            // Every further day the forecast endpoint returned beyond
            // tomorrow (day after tomorrow, day after that, etc.), each
            // condensed to a single high/low/icon summary. The compact "Next
            // 3 Days" strip on the dashboard only shows the first couple of
            // these; the extended forecast view shows all of them.
            val dayFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val weekdayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
            val dateLabelFormat = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            val upcoming = futureDates.drop(1).mapNotNull { dateKey ->
                val items = futureByDate[dateKey] ?: return@mapNotNull null
                if (items.isEmpty()) return@mapNotNull null
                val dayMidday = items.firstOrNull { it.dtTxt.contains("12:00:00") } ?: items[items.size / 2]
                val parsedDate = runCatching { dayFormat.parse(dateKey) }.getOrNull()
                val label = parsedDate?.let { weekdayFormat.format(it) } ?: dateKey
                val dateLabel = parsedDate?.let { dateLabelFormat.format(it) }
                WeatherDayForecast(
                    dayLabel = label,
                    minTemp = Math.round(items.minOf { it.main.temp } * 10) / 10.0,
                    maxTemp = Math.round(items.maxOf { it.main.temp } * 10) / 10.0,
                    description = dayMidday.weather.firstOrNull()?.description,
                    icon = dayMidday.weather.firstOrNull()?.icon,
                    rainChancePercent = items.mapNotNull { it.pop }.maxOrNull()?.let { Math.round(it * 100).toInt() } ?: 0,
                    dateLabel = dateLabel,
                    humidity = Math.round(items.map { it.main.humidity }.average()).toInt(),
                    windSpeed = dayMidday.wind?.speed
                )
            }

            Triple(todayParts, tomorrow, upcoming)
        }.getOrElse { Triple(emptyList(), WeatherTomorrow(available = false), emptyList()) }

    private suspend fun fetchBitcoin(): BitcoinInfo = runCatching {
        val r = coinGeckoApi.getBitcoinPrice()
        val prices = r.bitcoin ?: error("no bitcoin data")
        val currentEur = prices.eur ?: error("no EUR price")
        val todayChange = runCatching {
            val points = coinGeckoApi.getMarketChart(vsCurrency = "eur", days = 1).prices.orEmpty()
            val startOfDay = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            val opening = points.firstOrNull { it.size >= 2 && it[0] >= startOfDay }?.getOrNull(1)
                ?: points.firstOrNull { it.size >= 2 }?.getOrNull(1)
            if (opening != null && opening > 0.0) {
                ((currentEur - opening) / opening) * 100.0
            } else null
        }.getOrNull()
        BitcoinInfo(
            eur = prices.eur,
            usd = prices.usd,
            nok = prices.nok,
            change24hPercent = prices.eurChange24h,
            changeTodayPercent = todayChange,
            available = true,
            updatedAtMillis = System.currentTimeMillis()
        )
    }.getOrElse { BitcoinInfo(available = false) }

    private suspend fun fetchCurrency(base: String, target: String): CurrencyInfo = runCatching {
        val consensus = liveCurrencyFetcher?.fetchConsensus(base, target)
        if (consensus != null) {
            return@runCatching CurrencyInfo(
                rate = consensus.rate,
                change24hPercent = consensus.change24hPercent,
                baseCurrency = base,
                targetCurrency = target,
                available = true,
                updatedAtMillis = consensus.updatedAtMillis,
                changeTodayPercent = consensus.changeTodayPercent,
                consensusSources = consensus.sources,
                sourceSpreadPercent = consensus.sourceSpreadPercent
            )
        }

        // Last-resort reference-rate fallback if every live/reference source is
        // temporarily unreachable. This keeps the card useful offline-ish but
        // never labels a stale value as live data.
        val r = frankfurterApi.getRate(from = base, to = target)
        val rate = r.rates?.get(target) ?: error("no $target rate")
        CurrencyInfo(rate = rate, baseCurrency = base, targetCurrency = target, available = true)
    }.getOrElse { CurrencyInfo(baseCurrency = base, targetCurrency = target, available = false) }

    /**
     * Extra "From -> To" pairs from Settings > Currency Pair, on top of the
     * primary Currency Pair card. Each side can be a fiat currency or a
     * crypto coin in any combination (USD->BTC, ETH->EUR, BTC->ETH, ...).
     *
     * Rather than special-casing every combination, every asset's value is
     * first expressed in USD ("how much is 1 unit of this worth in USD"),
     * using at most one batched Frankfurter call for all the fiat codes
     * involved and one batched CoinGecko call for all the crypto ids
     * involved - regardless of how many pairs are configured. Each pair's
     * rate is then just usdValue(from) / usdValue(to).
     */
    private suspend fun fetchExtraCurrencyPairs(pairs: List<CurrencyPairConfig>): List<WatchlistEntry> {
        if (pairs.isEmpty()) return emptyList()

        val fiatCodes = pairs.flatMap { listOf(it.from, it.to) }
            .filter { it.type == AssetType.CURRENCY && !it.code.equals("USD", ignoreCase = true) }
            .map { it.code.uppercase() }
            .distinct()
        val cryptoIds = pairs.flatMap { listOf(it.from, it.to) }
            .filter { it.type == AssetType.CRYPTO }
            .map { it.code }
            .distinct()

        // usdValue(X) = how many USD is 1 unit of X worth.
        val fiatUsdValues: Map<String, Double> = if (fiatCodes.isEmpty()) emptyMap() else runCatching {
            // rates here are "units of code per 1 USD", so invert to get "USD per 1 unit of code".
            frankfurterApi.getRate(from = "USD", to = fiatCodes.joinToString(","))
                .rates.orEmpty()
                .mapNotNull { (code, unitsPerUsd) -> if (unitsPerUsd > 0) code to (1.0 / unitsPerUsd) else null }
                .toMap()
        }.getOrElse { emptyMap() }

        val cryptoUsdValues: Map<String, Double> = if (cryptoIds.isEmpty()) emptyMap() else runCatching {
            coinGeckoApi.getPrices(ids = cryptoIds.joinToString(","))
                .mapNotNull { (id, price) -> price.usd?.let { id to it } }
                .toMap()
        }.getOrElse { emptyMap() }

        fun usdValueOf(asset: AssetRef): Double? = when (asset.type) {
            AssetType.CURRENCY -> if (asset.code.equals("USD", ignoreCase = true)) 1.0 else fiatUsdValues[asset.code.uppercase()]
            AssetType.CRYPTO -> cryptoUsdValues[asset.code]
        }

        fun labelOf(asset: AssetRef): String = when (asset.type) {
            AssetType.CURRENCY -> asset.code.uppercase()
            AssetType.CRYPTO -> CryptoCatalog.ALL.firstOrNull { it.id == asset.code }?.symbol ?: asset.code
        }

        return pairs.map { pair ->
            val fromUsd = usdValueOf(pair.from)
            val toUsd = usdValueOf(pair.to)
            val rate = if (fromUsd != null && toUsd != null && toUsd != 0.0) fromUsd / toUsd else null
            WatchlistEntry(
                id = "${pair.from.type}:${pair.from.code}->${pair.to.type}:${pair.to.code}",
                label = "${labelOf(pair.from)} → ${labelOf(pair.to)}",
                isCrypto = pair.from.type == AssetType.CRYPTO || pair.to.type == AssetType.CRYPTO,
                value = rate,
                available = rate != null
            )
        }
    }

    /** Roughly a week of Bitcoin price history (in EUR), for the tap-to-see-chart on the Bitcoin card. */
    suspend fun fetchBitcoinHistory(days: Int = 7): List<ChartPoint> = runCatching {
        val r = coinGeckoApi.getMarketChart(vsCurrency = "eur", days = days)
        r.prices.orEmpty().mapNotNull { point ->
            if (point.size < 2) null else ChartPoint(timestampMillis = point[0].toLong(), value = point[1])
        }
    }.getOrElse { emptyList() }

    /** Roughly two weeks of daily rates for the configured currency pair, for the tap-to-see-chart on the currency card. */
    suspend fun fetchCurrencyHistory(base: String, target: String, days: Int = 14): List<ChartPoint> = runCatching {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val end = java.util.Date()
        val start = java.util.Date(end.time - TimeUnit.DAYS.toMillis(days.toLong()))
        val r = frankfurterApi.getTimeSeries(startDate = fmt.format(start), endDate = fmt.format(end), from = base, to = target)
        r.rates.orEmpty().mapNotNull { (dateStr, rates) ->
            val value = rates[target] ?: return@mapNotNull null
            val millis = runCatching { fmt.parse(dateStr)?.time }.getOrNull() ?: return@mapNotNull null
            ChartPoint(timestampMillis = millis, value = value)
        }.sortedBy { it.timestampMillis }
    }.getOrElse { emptyList() }

    /**
     * Severe weather alerts + custom alert rules for the configured city.
     *
     * Official severe alerts and the UV-index rule both require OpenWeather's
     * One Call 3.0 endpoint, which needs a *separate* paid subscription beyond
     * a normal free API key (the free key already used for /weather and
     * /forecast does NOT automatically include it). That call is therefore
     * fetched independently and allowed to fail on its own.
     *
     * The temp-above/below, wind, rain-probability, thunderstorm and snow
     * rules do NOT need One Call at all - they're evaluated from the same
     * free 3-hourly /forecast endpoint the "Tomorrow" card already uses, so
     * they keep working even when the API key has no One Call access (a
     * previous version of this incorrectly ran everything through the single
     * One Call call, so any 401 there silently zeroed out every custom rule,
     * not just the two that actually needed it).
     */
    private suspend fun fetchWeatherAlerts(
        cityQuery: String,
        apiKey: String,
        customAlertRules: CustomAlertRules = CustomAlertRules()
    ): WeatherAlertsInfo {
        val oneCall = runCatching {
            val geo = weatherApi.geocode(cityQuery, apiKey = apiKey).firstOrNull() ?: error("no geocoding match")
            val lat = geo.lat ?: error("no lat")
            val lon = geo.lon ?: error("no lon")
            weatherApi.getAlerts(lat = lat, lon = lon, apiKey = apiKey)
        }.getOrNull()

        val officialAlerts = oneCall?.alerts.orEmpty().map { a ->
            WeatherAlert(
                event = a.event ?: "Weather Alert",
                description = a.description ?: "",
                senderName = a.senderName ?: "",
                startMillis = a.start?.let { TimeUnit.SECONDS.toMillis(it) },
                endMillis = a.end?.let { TimeUnit.SECONDS.toMillis(it) }
            )
        }

        if (!customAlertRules.enabled) {
            return if (oneCall != null) WeatherAlertsInfo(alerts = officialAlerts, available = true)
            else WeatherAlertsInfo(available = false)
        }

        // Free-tier forecast (3-hourly), reused here purely to evaluate rules -
        // no One Call access required for temp/wind/rain/thunderstorm/snow.
        val forecastPoints = runCatching {
            weatherApi.getForecast(cityQuery, apiKey = apiKey).list.orEmpty().map { item ->
                OneCallHourly(
                    dt = item.dt,
                    temp = item.main.temp,
                    windSpeed = item.wind?.speed,
                    pop = item.pop,
                    uvi = null,
                    weather = item.weather
                )
            }
        }.getOrElse { emptyList() }

        // Best-effort UV enrichment: only filled in where the One Call hourly
        // block succeeded and has a reading within an hour of a forecast slot.
        // If One Call isn't available, uvi just stays null and the UV rule
        // simply never matches - it doesn't affect any other rule.
        val oneCallHourly = oneCall?.hourly.orEmpty()
        val evaluationPoints = if (oneCallHourly.isEmpty()) {
            forecastPoints
        } else {
            forecastPoints.map { point ->
                val nearest = oneCallHourly.minByOrNull { kotlin.math.abs(it.dt - point.dt) }
                if (nearest != null && kotlin.math.abs(nearest.dt - point.dt) <= TimeUnit.HOURS.toSeconds(1)) {
                    point.copy(uvi = nearest.uvi)
                } else point
            }
        }

        if (evaluationPoints.isEmpty() && oneCall == null) {
            // Both the free forecast and the One Call fetch failed - genuinely unavailable.
            return WeatherAlertsInfo(available = false)
        }

        val customAlerts = CustomWeatherAlertEngine.evaluate(evaluationPoints, officialAlerts, customAlertRules)
        return WeatherAlertsInfo(alerts = officialAlerts, available = true, customAlerts = customAlerts)
    }

    /**
     * Re-fetches just the weather-alerts section (official + custom rules) and
     * merges it into the latest saved report, used by the hourly background
     * alert-check worker so a fresh custom-alert match shows up on the
     * dashboard without waiting for the next full digest refresh.
     */
    suspend fun refreshWeatherAlertsSection(settings: AppSettings): DigestReport {
        val current = getLatestReport() ?: saveReport(buildFreshReport(settings))
        val cityQuery = "${settings.city},${settings.country}"
        val updatedAlerts = if (settings.weatherAlertsEnabled || settings.customAlertRules.enabled) {
            fetchWeatherAlerts(cityQuery, settings.weatherApiKey, settings.customAlertRules)
        } else {
            WeatherAlertsInfo(available = false)
        }
        val updated = current.copy(weatherAlerts = updatedAlerts)
        reportDao.insert(ReportMapper.toEntity(updated))
        return updated
    }

    private suspend fun fetchNews(settings: AppSettings, previousHeadlineKeys: Set<String> = emptySet()): NewsInfo {
        // Only the feeds the user picked in Settings, so the digest actually
        // reflects what they care about instead of one fixed source list.
        val selectedFeeds = NewsFeedCatalog.byIds(settings.selectedNewsFeedIds)
        // Every outlet the user has added themselves is fetched too - there's
        // no cap here, so adding 20 custom outlets pulls headlines from all 20.
        val feedsToFetch = selectedFeeds.map { it.label to it.url } +
            settings.customFeeds.filter { it.url.isNotBlank() }.map { it.label to it.url }
        return fetchNewsFromFeeds(feedsToFetch, NEWS_TARGET_COUNT, previousHeadlineKeys)
    }

    /** Optional dedicated US Politics card - only fetched when the user has turned it on in Settings. */
    private suspend fun fetchPoliticsNews(settings: AppSettings, previousHeadlineKeys: Set<String> = emptySet()): NewsInfo {
        if (!settings.politicsNewsEnabled) return NewsInfo(available = false)
        val feeds = PoliticsFeedCatalog.byIds(settings.selectedPoliticsFeedIds).map { it.label to it.url }
        return fetchNewsFromFeeds(feeds, TOPIC_NEWS_TARGET_COUNT, previousHeadlineKeys)
    }

    /** Optional dedicated Business News card - only fetched when the user has turned it on in Settings. */
    private suspend fun fetchBusinessNews(settings: AppSettings, previousHeadlineKeys: Set<String> = emptySet()): NewsInfo {
        if (!settings.businessNewsEnabled) return NewsInfo(available = false)
        val feeds = BusinessFeedCatalog.byIds(settings.selectedBusinessFeedIds).map { it.label to it.url }
        return fetchNewsFromFeeds(feeds, TOPIC_NEWS_TARGET_COUNT, previousHeadlineKeys)
    }

    // Hardcoded, always-on sources feeding just the analyst report cards
    // below (Bully/Beary/Satoshi) - not user-configurable like the main
    // News Sources settings, since these are small, single-purpose feeds
    // that specific cards need rather than a full topic section. Reuses the
    // exact same fetch/parse pipeline as every other news source in the app.
    private suspend fun fetchMarketsNews(): NewsInfo =
        fetchNewsFromFeeds(
            listOf("MarketWatch" to "http://feeds.marketwatch.com/marketwatch/topstories/"),
            TOPIC_NEWS_TARGET_COUNT,
            emptySet()
        )

    private suspend fun fetchCryptoNews(): NewsInfo =
        fetchNewsFromFeeds(
            listOf("CoinDesk" to "https://www.coindesk.com/arc/outboundfeeds/rss/"),
            TOPIC_NEWS_TARGET_COUNT,
            emptySet()
        )

    /**
     * Shared fetch/dedupe/sort pipeline used by the main World News feed and
     * by the optional single-topic Politics/Business cards - only the source
     * list and the resulting headline cap differ between them.
     */
    private suspend fun fetchNewsFromFeeds(
        feedsToFetch: List<Pair<String, String>>,
        targetCount: Int,
        previousHeadlineKeys: Set<String>
    ): NewsInfo = coroutineScope {
        if (feedsToFetch.isEmpty()) return@coroutineScope NewsInfo(available = false)

        // Fetch every outlet in parallel; a failing outlet just contributes an
        // empty list (via runCatching) instead of breaking the whole digest.
        val perOutlet = feedsToFetch.map { (source, url) ->
            async {
                runCatching { rssFetcher.fetchTopHeadlines(url, source = source, limit = NEWS_PER_OUTLET_LIMIT) }
                    .getOrElse { emptyList() }
            }
        }.awaitAll()

        // Dedupe near-identical headlines (wire stories often get reused
        // verbatim across outlets).
        val seenTitles = mutableSetOf<String>()
        val deduped = mutableListOf<NewsHeadline>()
        for (outlet in perOutlet) {
            for (headline in outlet) {
                val key = headline.title.trim().lowercase()
                if (seenTitles.add(key)) deduped.add(headline)
            }
        }

        // Fresh on top, older pushed down - straight newest-publish-time-first.
        // Headlines with no parseable pubDate (rare, but some outlets omit it)
        // sort to the bottom of their outlet's batch rather than jumping to
        // the top, so a missing date never masquerades as "newest".
        val combined = deduped
            .sortedByDescending { it.pubDateMillis ?: Long.MIN_VALUE }
            .take(targetCount)

        NewsInfo(headlines = combined, available = combined.isNotEmpty())
    }

    suspend fun saveReport(report: DigestReport): DigestReport {
        val id = reportDao.insert(ReportMapper.toEntity(report))
        reportDao.trimTo(HISTORY_LIMIT)
        return report.copy(id = id)
    }

    /**
     * Re-fetches just the World News section and merges it into the latest
     * saved report, leaving weather/bitcoin/currency/politics/business
     * untouched - used by the per-card refresh button so refreshing one
     * section doesn't re-fetch everything else too.
     */
    suspend fun refreshWorldNewsSection(settings: AppSettings): DigestReport {
        val current = getLatestReport() ?: saveReport(buildFreshReport(settings))
        val keys = current.news.headlines.map { it.title.trim().lowercase() }.toSet()
        val updated = current.copy(news = fetchNews(settings, keys))
        reportDao.insert(ReportMapper.toEntity(updated))
        return updated
    }

    /** Same as [refreshWorldNewsSection] but for Bitcoin + Currency + any extra pairs - lets the markets cards retry on their own after a transient failure (e.g. a rate-limited crypto API call) without waiting for the next full digest. */
    suspend fun refreshMarketsSection(settings: AppSettings): DigestReport {
        val current = getLatestReport() ?: saveReport(buildFreshReport(settings))
        val bitcoin = fetchBitcoin()
        val currency = fetchCurrency(settings.currencyBase, settings.currencyTarget)
        val watchlist = if (settings.extraCurrencyPairs.isNotEmpty()) fetchExtraCurrencyPairs(settings.extraCurrencyPairs) else emptyList()
        val marketsNews = runCatching { fetchMarketsNews() }.getOrElse { NewsInfo(available = false) }
        val cryptoNews = runCatching { fetchCryptoNews() }.getOrElse { NewsInfo(available = false) }
        val stockGainers = fetchStockGainers()
        val stockLosers = fetchStockLosers()
        val updated = current.copy(
            bitcoin = bitcoin,
            currency = currency,
            watchlist = watchlist,
            marketsNews = marketsNews,
            cryptoNews = cryptoNews,
            stockGainers = stockGainers,
            stockLosers = stockLosers
        )
        reportDao.insert(ReportMapper.toEntity(updated))
        return updated
    }

    /** Same as [refreshMarketsSection] but for Panda's weather briefing - today/tomorrow/upcoming outlook plus alerts. */
    suspend fun refreshWeatherSection(settings: AppSettings): DigestReport {
        val current = getLatestReport() ?: saveReport(buildFreshReport(settings))
        val cityQuery = "${settings.city},${settings.country}"
        val today = fetchWeatherToday(cityQuery, settings.weatherApiKey)
        val (todayParts, tomorrow, upcoming) = fetchTomorrowAndUpcoming(cityQuery, settings.weatherApiKey)
        val alerts = if (settings.weatherAlertsEnabled || settings.customAlertRules.enabled) {
            fetchWeatherAlerts(cityQuery, settings.weatherApiKey, settings.customAlertRules)
        } else {
            WeatherAlertsInfo(available = false)
        }
        val updated = current.copy(
            weatherToday = today.copy(parts = todayParts),
            weatherTomorrow = tomorrow,
            upcomingDays = upcoming,
            weatherAlerts = alerts
        )
        reportDao.insert(ReportMapper.toEntity(updated))
        return updated
    }

    /** Same as [refreshWorldNewsSection] but for the optional Politics card. */
    suspend fun refreshPoliticsSection(settings: AppSettings): DigestReport {
        val current = getLatestReport() ?: saveReport(buildFreshReport(settings))
        val keys = current.politicsNews.headlines.map { it.title.trim().lowercase() }.toSet()
        val updated = current.copy(politicsNews = fetchPoliticsNews(settings, keys))
        reportDao.insert(ReportMapper.toEntity(updated))
        return updated
    }

    /** Same as [refreshWorldNewsSection] but for the optional Business card. */
    suspend fun refreshBusinessSection(settings: AppSettings): DigestReport {
        val current = getLatestReport() ?: saveReport(buildFreshReport(settings))
        val keys = current.businessNews.headlines.map { it.title.trim().lowercase() }.toSet()
        val updated = current.copy(businessNews = fetchBusinessNews(settings, keys))
        reportDao.insert(ReportMapper.toEntity(updated))
        return updated
    }

    suspend fun updateSendResult(reportId: Long, report: DigestReport) {
        reportDao.insert(ReportMapper.toEntity(report.copy(id = reportId)))
    }

    suspend fun getLatestReport(): DigestReport? =
        reportDao.getLatest()?.let { ReportMapper.toDomain(it) }

    fun observeHistory(): Flow<List<DigestReport>> =
        reportDao.observeAll().map { list -> list.map { ReportMapper.toDomain(it) } }

    suspend fun getReportById(id: Long): DigestReport? =
        reportDao.getById(id)?.let { ReportMapper.toDomain(it) }

    suspend fun deleteReport(id: Long) = reportDao.deleteById(id)

    suspend fun clearHistory() = reportDao.deleteAll()

    companion object {
        fun create(
            database: AppDatabase,
            weatherApi: OpenWeatherApi,
            coinGeckoApi: CoinGeckoApi,
            frankfurterApi: FrankfurterApi,
            rssFetcher: RssFeedFetcher,
            stockMoversFetcher: StockMoversFetcher? = null,
            youtubeChannelFetcher: YoutubeChannelFetcher? = null,
            liveCurrencyFetcher: LiveCurrencyFetcher? = null
        ) =
            DigestRepository(weatherApi, coinGeckoApi, frankfurterApi, rssFetcher, database.reportDao(), stockMoversFetcher, youtubeChannelFetcher, liveCurrencyFetcher)
    }
}
