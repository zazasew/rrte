package com.morningdigest.app.ui.dashboard

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morningdigest.app.MorningDigestApp
import com.morningdigest.app.data.model.ChartPoint
import com.morningdigest.app.data.model.DigestReport
import com.morningdigest.app.data.remote.PoliceReportFetcher
import com.morningdigest.app.data.prefs.AppSettings
import com.morningdigest.app.location.LocationHelper
import com.morningdigest.app.worker.WorkScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

data class DashboardUiState(
    val report: DigestReport? = null,
    val isLoading: Boolean = false,
    val isOnline: Boolean = true,
    val lastRefreshMillis: Long? = null,
    val nextScheduledMillis: Long? = null,
    val cityLabel: String = "Tyristrand",
    val userName: String = "Sasa",
    // The device's real current location (reverse-geocoded from GPS), shown
    // under the greeting. Null until resolved, or if location permission was
    // never granted - the greeting falls back to cityLabel in that case.
    val deviceLocationLabel: String? = null,
    // Per-card refresh spinners, separate from the full-page isLoading, so
    // tapping refresh on just the Markets card doesn't spin every card.
    val isRefreshingMarkets: Boolean = false,
    // Order of the reorderable content cards, mirrors Settings > Dashboard Layout.
    val dashboardCardOrder: List<String> = com.morningdigest.app.data.prefs.DashboardCards.DEFAULT_ORDER,
    val enabledAssistantIds: Set<String> = com.morningdigest.app.data.prefs.MascotCharacter.ALL_IDS,
    val isRefreshingWeather: Boolean = false,
    val policeIncidents: List<PoliceReportFetcher.Incident> = emptyList(),
    val policeError: String? = null,
    val isRefreshingPolice: Boolean = false,
    val policeMunicipality: String = "",
    val hasNewPoliceIncidents: Boolean = false
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /**
         * Hard ceiling for any single refresh, on top of every individual
         * network call already having its own (shorter) timeout. This is a
         * last line of defense so an unexpected hang somewhere - a slow DNS
         * lookup, a misbehaving provider, anything - can never leave a
         * refresh spinner stuck on screen forever; it just fails with a
         * message after 45s and lets the user try again.
         */
        private const val REFRESH_TIMEOUT_MILLIS = 45_000L
    }

    private val container get() = (getApplication<Application>() as MorningDigestApp).container

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // One-shot error messages (failed refresh, offline, etc.) for the Dashboard
    // to surface as a Snackbar - a silently-stopping spinner leaves the user guessing.
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private val connectivityManager =
        application.getSystemService(ConnectivityManager::class.java)

    init {
        observeConnectivity()
        loadCachedThenRefresh()
    }

    private fun observeConnectivity() {
        val request = NetworkRequest.Builder().build()
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _uiState.value = _uiState.value.copy(isOnline = true)
            }
            override fun onLost(network: Network) {
                _uiState.value = _uiState.value.copy(isOnline = isCurrentlyOnline())
            }
        })
        _uiState.value = _uiState.value.copy(isOnline = isCurrentlyOnline())
    }

    private fun isCurrentlyOnline(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadCachedThenRefresh() {
        viewModelScope.launch {
            val cached = container.digestRepository.getLatestReport()
            _uiState.value = _uiState.value.copy(report = cached)
            refreshSettingsState()
        }
    }

    /**
     * Reloads just the settings-derived parts of the UI state (city/name,
     * next-scheduled time, and the Politics/Business card toggles) without
     * touching the report or loading spinner. Previously this only ran once
     * at app start, so changes made in Settings - like turning on the new
     * Politics/Business cards - didn't actually appear on the dashboard until
     * the app was restarted. Now also called when the screen resumes (e.g.
     * navigating back from Settings), so toggles take effect immediately.
     */
    fun refreshSettingsState() {
        viewModelScope.launch {
            val settings = container.settingsRepository.currentSettings()
            _uiState.value = _uiState.value.copy(
                cityLabel = settings.city,
                userName = settings.userName,
                nextScheduledMillis = if (settings.autoSendEnabled)
                    WorkScheduler.nextScheduledMillis(settings, _uiState.value.report?.timestampMillis) else null,
                dashboardCardOrder = settings.dashboardCardOrder,
                enabledAssistantIds = settings.enabledAssistantIds,
                policeMunicipality = settings.policeMunicipality
            )
            refreshPolice()
        }
    }

    fun dismissPoliceIncident(id: String) {
        viewModelScope.launch {
            container.settingsRepository.dismissPoliceIncident(id)
            _uiState.value = _uiState.value.copy(
                policeIncidents = _uiState.value.policeIncidents.filter { it.id != id }
            )
        }
    }

    /**
     * Marks the currently-loaded police incidents as seen (same "seen ids"
     * set the background worker uses to decide what's push-notification-worthy),
     * clearing the dashboard's "new police report" icon. Called when the user
     * taps that icon to open Max's full report.
     */
    fun markPoliceIncidentsSeen() {
        val ids = _uiState.value.policeIncidents.map { it.id }.toSet()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val seen = container.settingsRepository.getPoliceSeenIds()
            // Capped the same way PoliceIncidentCheckWorker already caps its
            // own writes to this same set - old incident ids are never
            // checked against again once Politiloggen's own feed has moved
            // past them, so there's no reason to keep them forever.
            container.settingsRepository.setPoliceSeenIds((seen + ids).toList().takeLast(200).toSet())
            _uiState.value = _uiState.value.copy(hasNewPoliceIncidents = false)
        }
    }

    fun refreshPolice() {
        viewModelScope.launch {
            val settings = container.settingsRepository.currentSettings()
            if (!settings.policeAlertsEnabled) {
                _uiState.value = _uiState.value.copy(policeIncidents = emptyList(), hasNewPoliceIncidents = false)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isRefreshingPolice = true, policeMunicipality = settings.policeMunicipality, policeError = null)
            try {
                val dismissed = container.settingsRepository.getPoliceDismissedIds()
                val fetched = withTimeoutOrNull(REFRESH_TIMEOUT_MILLIS) {
                    container.policeReportFetcher.fetch(settings.policeMunicipalities, settings.policeCategories)
                }
                val incidents = fetched.orEmpty()
                    .filter { it.id !in dismissed }
                    .sortedByDescending { it.createdMillis }
                // No extra global cap: fetch() already bounds this fairly at
                // PER_MUNICIPALITY_LIMIT per municipality. A flat top-20 cut
                // here could crowd out a quiet municipality's incidents from
                // both the card and the "new incident" shield-icon check below.
                val seen = container.settingsRepository.getPoliceSeenIds()
                val hasNew = seen.isNotEmpty() && incidents.any { it.id !in seen }
                _uiState.value = _uiState.value.copy(
                    policeIncidents = incidents,
                    isRefreshingPolice = false,
                    hasNewPoliceIncidents = hasNew,
                    policeError = if (fetched == null) "Timed out reaching the police report service" else null
                )
            } catch (e: PoliceReportFetcher.PoliceReportException) {
                _uiState.value = _uiState.value.copy(isRefreshingPolice = false, policeError = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshingPolice = false, policeError = "Couldn't refresh the police report")
            }
        }
    }

    /**
     * A manual refresh (pull-to-refresh, or tapping the Bitcoin/Currency
     * card) already hands the user live price data right now - so the next
     * background price-alert check shouldn't fire on whatever fraction of
     * the old 3-hour window happened to be left. This restarts that
     * countdown fresh from this moment instead, same idea as snoozing an
     * alarm you just silenced by hand.
     */
    private fun restartPriceAlertCountdownIfEnabled() {
        viewModelScope.launch {
            val settings = container.settingsRepository.currentSettings()
            if (settings.priceAlertRules.enabled) {
                WorkScheduler.restartPriceAlertCountdown(getApplication())
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            if (!isCurrentlyOnline()) {
                _uiState.value = _uiState.value.copy(isOnline = false)
                _errorEvents.tryEmit("Couldn't refresh — check your connection")
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val settings = container.settingsRepository.currentSettings()
                // Every individual network call already has its own timeout, but
                // this is a hard ceiling on the refresh as a whole - so a slow
                // connection or an unexpected hang can never leave the dashboard
                // stuck spinning; it just fails with a message and lets the
                // user try again instead.
                val saved = withTimeoutOrNull(REFRESH_TIMEOUT_MILLIS) {
                    container.digestRepository.refreshDashboardSection(settings)
                }
                if (saved == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _errorEvents.tryEmit("Refresh is taking too long — please try again")
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    report = saved,
                    isLoading = false,
                    lastRefreshMillis = System.currentTimeMillis()
                )
                // A manual pull-to-refresh always refreshes the police scanner too.
                refreshPolice()
                // ...and delivers live price data, so push the next background
                // price-alert check out to a fresh window from now.
                restartPriceAlertCountdownIfEnabled()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _errorEvents.tryEmit(refreshErrorMessage(e))
            }
        }
    }

    /** Turns a raw refresh exception into a short, actionable message for the Snackbar. */
    private fun refreshErrorMessage(e: Exception): String {
        val text = e.message?.lowercase().orEmpty()
        return when {
            text.contains("401") || text.contains("unauthorized") || text.contains("invalid api key") ->
                "Couldn't refresh — check your Weather API key in Settings"
            text.contains("timeout") || text.contains("unable to resolve host") || text.contains("failed to connect") ->
                "Couldn't refresh — check your connection"
            else -> "Couldn't refresh — please try again"
        }
    }

    /** Refreshes just Bitcoin/Currency/watchlist, e.g. after a transient failure, without waiting for the next full digest. */
    fun refreshMarketsOnly() = refreshSection(
        setLoading = { _uiState.value = _uiState.value.copy(isRefreshingMarkets = it) },
        fetch = { settings -> container.digestRepository.refreshMarketsSection(settings) },
        errorLabel = "Markets",
        onSuccess = { restartPriceAlertCountdownIfEnabled() }
    )

    /** Refreshes just Panda's weather briefing (today/tomorrow/outlook/alerts). */
    fun refreshWeatherOnly() = refreshSection(
        setLoading = { _uiState.value = _uiState.value.copy(isRefreshingWeather = it) },
        fetch = { settings -> container.digestRepository.refreshWeatherSection(settings) },
        errorLabel = "Weather"
    )

    /**
     * Dismisses one video bubble - either the user tapped its X, or opened
     * the video (both count as "seen"). Removes it from the screen right
     * away rather than waiting for the next refresh, and persists the
     * dismissal so it doesn't come back on the next check either.
     */
    fun dismissYoutubeVideo(channelId: String, videoId: String) {
        val current = _uiState.value.report ?: return
        _uiState.value = _uiState.value.copy(
            report = current.copy(
                youtubeUpdates = current.youtubeUpdates.filterNot { it.channelId == channelId && it.videoId == videoId }
            )
        )
        viewModelScope.launch {
            val settings = container.settingsRepository.currentSettings()
            val updatedChannels = settings.youtubeChannels.map { channel ->
                if (channel.channelId == channelId) channel.copy(dismissedVideoIds = (channel.dismissedVideoIds + videoId).toList().takeLast(200).toSet())
                else channel
            }
            container.settingsRepository.updateYoutubeChannels(updatedChannels)
        }
    }

    /**
     * "Clear all" for the New videos section - dismisses every currently
     * shown update at once, same persistence as a single dismiss so none of
     * them come back on the next check.
     */
    fun dismissAllYoutubeVideos() {
        val current = _uiState.value.report ?: return
        val toDismiss = current.youtubeUpdates
        if (toDismiss.isEmpty()) return
        _uiState.value = _uiState.value.copy(report = current.copy(youtubeUpdates = emptyList()))
        viewModelScope.launch {
            val settings = container.settingsRepository.currentSettings()
            val dismissedByChannel = toDismiss.groupBy({ it.channelId }, { it.videoId })
            val updatedChannels = settings.youtubeChannels.map { channel ->
                val newlyDismissed = dismissedByChannel[channel.channelId].orEmpty()
                if (newlyDismissed.isEmpty()) channel
                else channel.copy(dismissedVideoIds = (channel.dismissedVideoIds + newlyDismissed).toList().takeLast(200).toSet())
            }
            container.settingsRepository.updateYoutubeChannels(updatedChannels)
        }
    }

    private fun refreshSection(
        setLoading: (Boolean) -> Unit,
        fetch: suspend (AppSettings) -> DigestReport,
        errorLabel: String,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            if (!isCurrentlyOnline()) {
                _errorEvents.tryEmit("Couldn't refresh $errorLabel — check your connection")
                return@launch
            }
            setLoading(true)
            runCatching {
                val settings = container.settingsRepository.currentSettings()
                withTimeoutOrNull(REFRESH_TIMEOUT_MILLIS) { fetch(settings) }
                    ?: error("Refresh timed out")
            }.onSuccess { updated ->
                _uiState.value = _uiState.value.copy(report = updated)
                onSuccess?.invoke()
            }.onFailure {
                _errorEvents.tryEmit("Couldn't refresh $errorLabel — please try again")
            }
            setLoading(false)
        }
    }

    fun notifyNow() {
        WorkScheduler.runNow(getApplication(), sendNotification = true)
        // Poll for the freshly-saved report a moment later so the dashboard
        // reflects the new notification status without requiring a manual pull.
        viewModelScope.launch {
            kotlinx.coroutines.delay(4000)
            val latest = container.digestRepository.getLatestReport()
            _uiState.value = _uiState.value.copy(report = latest, lastRefreshMillis = System.currentTimeMillis())
        }
    }

    /** Resolves the device's current GPS location for the greeting. Safe to call before/without permission - just no-ops. */
    fun refreshDeviceLocation() {
        viewModelScope.launch {
            val label = runCatching { LocationHelper.getCurrentLocationLabel(getApplication()) }.getOrNull()
            if (!label.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(deviceLocationLabel = label)
            }
        }
    }

    /** Loads Bitcoin price history for the Bitcoin card's chart (~7 days by default) or the inline sparkline (1 day). */
    fun loadBitcoinHistory(days: Int = 7, onResult: (List<ChartPoint>) -> Unit) {
        viewModelScope.launch {
            val points = runCatching { container.digestRepository.fetchBitcoinHistory(days = days) }.getOrElse { emptyList() }
            onResult(points)
        }
    }

    private var cachedBitcoinSparkline: Pair<Long, List<ChartPoint>>? = null

    /**
     * Cached/throttled version used by the card's inline sparkline, refetched
     * at most every 30 minutes regardless of how often the digest itself
     * refreshes. CoinGecko's free/keyless API (no key configured here) has a
     * very low rate limit and explicitly isn't meant for frequent polling -
     * calling this on every refresh in addition to the main price fetch was
     * enough to occasionally starve the price fetch itself of its own quota.
     */
    fun loadBitcoinSparkline(onResult: (List<ChartPoint>) -> Unit) {
        val cached = cachedBitcoinSparkline
        if (cached != null && System.currentTimeMillis() - cached.first < TimeUnit.MINUTES.toMillis(30)) {
            onResult(cached.second)
            return
        }
        viewModelScope.launch {
            val points = runCatching { container.digestRepository.fetchBitcoinHistory(days = 1) }.getOrElse { emptyList() }
            if (points.isNotEmpty()) cachedBitcoinSparkline = System.currentTimeMillis() to points
            onResult(points)
        }
    }

    /** Loads history for the configured currency pair (~2 weeks by default for the dialog, shorter for the inline sparkline). */
    fun loadCurrencyHistory(days: Int = 14, onResult: (List<ChartPoint>) -> Unit) {
        viewModelScope.launch {
            val settings = container.settingsRepository.currentSettings()
            val points = runCatching {
                container.digestRepository.fetchCurrencyHistory(settings.currencyBase, settings.currencyTarget, days = days)
            }.getOrElse { emptyList() }
            onResult(points)
        }
    }

    private var cachedCurrencySparkline: Pair<Long, List<ChartPoint>>? = null

    /** Cached/throttled version used by the card's inline sparkline - currency rates are daily-resolution anyway, so there's no value in refetching more than a couple of times an hour. */
    fun loadCurrencySparkline(onResult: (List<ChartPoint>) -> Unit) {
        val cached = cachedCurrencySparkline
        if (cached != null && System.currentTimeMillis() - cached.first < TimeUnit.MINUTES.toMillis(30)) {
            onResult(cached.second)
            return
        }
        viewModelScope.launch {
            val settings = container.settingsRepository.currentSettings()
            val points = runCatching {
                container.digestRepository.fetchCurrencyHistory(settings.currencyBase, settings.currencyTarget, days = 7)
            }.getOrElse { emptyList() }
            if (points.isNotEmpty()) cachedCurrencySparkline = System.currentTimeMillis() to points
            onResult(points)
        }
    }

    /** Same as [loadCurrencyHistory] but for any base/target pair - powers the chart on an extra currency pair's card, same as the main pair. */
    fun loadPairHistory(base: String, target: String, days: Int = 14, onResult: (List<ChartPoint>) -> Unit) {
        viewModelScope.launch {
            val points = runCatching {
                container.digestRepository.fetchCurrencyHistory(base, target, days = days)
            }.getOrElse { emptyList() }
            onResult(points)
        }
    }

    fun applySettings(settings: AppSettings) = refreshSettingsState()
}
