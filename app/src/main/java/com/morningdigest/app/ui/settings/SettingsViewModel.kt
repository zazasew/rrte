package com.morningdigest.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morningdigest.app.MorningDigestApp
import com.morningdigest.app.data.prefs.AppSettings
import com.morningdigest.app.data.prefs.CurrencyPairConfig
import com.morningdigest.app.data.prefs.CustomAlertRules
import com.morningdigest.app.data.prefs.CustomFeed
import com.morningdigest.app.data.prefs.ScheduleMode
import com.morningdigest.app.data.remote.RssFeedFetcher
import com.morningdigest.app.worker.WorkScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** The 12 police districts, for the "pick a district first" step of the municipality picker - like the official Politiloggen app. */
    val policeDistricts: List<com.morningdigest.app.data.remote.PoliceDistricts.DistrictItem> = emptyList(),
    val policeDistrictsLoading: Boolean = false,
    val selectedPoliceDistrict: com.morningdigest.app.data.remote.PoliceDistricts.DistrictItem? = null,
    /** Municipalities/cities inside [selectedPoliceDistrict], e.g. Kirkenes inside Finnmark. */
    val policeDistrictMunicipalities: List<String> = emptyList(),
    val policeDistrictMunicipalitiesLoading: Boolean = false
)

/** Result of actively testing one news-source URL, shown as a badge in Settings. */
sealed class FeedCheckUiState {
    object Idle : FeedCheckUiState()
    object Checking : FeedCheckUiState()
    data class Success(val headlineCount: Int, val sampleTitle: String?) : FeedCheckUiState()
    data class Failure(val reason: String) : FeedCheckUiState()
}

/** Result of trying to resolve/add a YouTube channel in Settings. */
sealed class AddChannelUiState {
    object Idle : AddChannelUiState()
    object Loading : AddChannelUiState()
    data class Success(val channel: com.morningdigest.app.data.prefs.YoutubeChannelConfig) : AddChannelUiState()
    data class Failure(val reason: String) : AddChannelUiState()
}

/** Key used for the "add new outlet" form before it has a real CustomFeed id yet. */
const val NEW_FEED_TEST_KEY = "__new_feed_test__"

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container get() = (getApplication<Application>() as MorningDigestApp).container

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // One-shot "✓ Saved" confirmations for the Settings screen to surface as a Snackbar.
    // A save button tap gives no visible feedback otherwise, so the screen collects
    // this flow and shows each message once.
    private val _saveEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saveEvents: SharedFlow<String> = _saveEvents.asSharedFlow()

    // Per-outlet test results, keyed by CustomFeed.id (or NEW_FEED_TEST_KEY for
    // the not-yet-saved "add outlet" form), so Settings can show a confirmed/
    // failed badge for every news source instead of it silently showing no news.
    private val _feedCheckResults = MutableStateFlow<Map<String, FeedCheckUiState>>(emptyMap())
    val feedCheckResults: StateFlow<Map<String, FeedCheckUiState>> = _feedCheckResults.asStateFlow()

    fun testFeed(key: String, url: String) = viewModelScope.launch {
        _feedCheckResults.value = _feedCheckResults.value + (key to FeedCheckUiState.Checking)
        val result = container.rssFeedFetcher.checkFeed(url)
        _feedCheckResults.value = _feedCheckResults.value + (key to when (result) {
            is RssFeedFetcher.FeedCheckResult.Success ->
                FeedCheckUiState.Success(result.headlineCount, result.sampleTitle)
            is RssFeedFetcher.FeedCheckResult.Failure ->
                FeedCheckUiState.Failure(result.reason)
        })
    }

    fun clearFeedCheck(key: String) {
        _feedCheckResults.value = _feedCheckResults.value - key
    }

    private val _addChannelState = MutableStateFlow<AddChannelUiState>(AddChannelUiState.Idle)
    val addChannelState: StateFlow<AddChannelUiState> = _addChannelState.asStateFlow()

    /** Resolves a pasted channel URL/handle into a real channel with its avatar and current baseline - the Composable appends it to the (not-yet-saved) local list on success. */
    fun addYoutubeChannel(input: String) = viewModelScope.launch {
        _addChannelState.value = AddChannelUiState.Loading
        _addChannelState.value = when (val result = container.digestRepository.addYoutubeChannel(input)) {
            is com.morningdigest.app.data.repository.DigestRepository.AddYoutubeChannelResult.Success ->
                AddChannelUiState.Success(result.channel)
            is com.morningdigest.app.data.repository.DigestRepository.AddYoutubeChannelResult.Failure ->
                AddChannelUiState.Failure(result.reason)
        }
    }

    fun resetAddChannelState() {
        _addChannelState.value = AddChannelUiState.Idle
    }

    init {
        viewModelScope.launch {
            container.settingsRepository.settingsFlow.collect { s ->
                _uiState.value = _uiState.value.copy(settings = s)
            }
        }
    }

    private fun confirm(message: String) {
        _saveEvents.tryEmit("✓ $message")
    }

    fun updateCityCountry(city: String, country: String) = viewModelScope.launch {
        container.settingsRepository.updateCityCountry(city, country)
        confirm("Location saved")
    }

    /** Loads the 12 police districts for the "District" dropdown - the first step of the two-level picker. */
    fun loadPoliceDistricts() = viewModelScope.launch {
        if (_uiState.value.policeDistricts.isNotEmpty()) return@launch
        _uiState.value = _uiState.value.copy(policeDistrictsLoading = true)
        val districts = container.policeReportFetcher.fetchDistricts()
        _uiState.value = _uiState.value.copy(policeDistricts = districts, policeDistrictsLoading = false)
    }

    /** Picking a district loads the municipalities/cities inside it (e.g. Kirkenes inside Finnmark) for the second dropdown. */
    fun selectPoliceDistrict(district: com.morningdigest.app.data.remote.PoliceDistricts.DistrictItem) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(
            selectedPoliceDistrict = district,
            policeDistrictMunicipalities = emptyList(),
            policeDistrictMunicipalitiesLoading = true
        )
        val municipalities = container.policeReportFetcher.fetchMunicipalitiesForDistrict(district)
        _uiState.value = _uiState.value.copy(
            policeDistrictMunicipalities = municipalities,
            policeDistrictMunicipalitiesLoading = false
        )
    }

    fun clearPoliceDistrictSelection() {
        _uiState.value = _uiState.value.copy(selectedPoliceDistrict = null, policeDistrictMunicipalities = emptyList())
    }

    fun updatePoliceSettings(municipalities: List<String>, enabled: Boolean, categories: Set<String>) = viewModelScope.launch {
        val cleaned = municipalities.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        container.settingsRepository.updatePoliceSettings(cleaned, enabled, categories)
        val primary = cleaned.firstOrNull() ?: "Ringerike"
        WorkScheduler.applySchedule(getApplication(), _uiState.value.settings.copy(
            policeMunicipality = primary,
            policeMunicipalities = cleaned.ifEmpty { listOf(primary) },
            policeAlertsEnabled = enabled,
            policeCategories = categories
        ))
        confirm("Police alerts saved")
    }

    fun updateWeatherApiKey(key: String) = viewModelScope.launch {
        container.settingsRepository.updateWeatherApiKey(key)
        confirm("API key saved")
    }

    fun updateUserName(name: String) = viewModelScope.launch {
        container.settingsRepository.updateUserName(name)
        confirm("Name saved")
    }

    fun updateWakeTime(hour: Int, minute: Int) = viewModelScope.launch {
        container.settingsRepository.updateWakeTime(hour, minute)
        val s = _uiState.value.settings
        if (s.autoSendEnabled && s.scheduleMode == ScheduleMode.DAILY) {
            WorkScheduler.scheduleDaily(getApplication(), hour, minute)
        }
        confirm("Notification time saved")
    }

    fun updateSchedule(mode: ScheduleMode, intervalHours: Int) = viewModelScope.launch {
        container.settingsRepository.updateScheduleMode(mode, intervalHours)
        // Saving a schedule here is an explicit "turn this on" action. Previously,
        // if "Auto-send notifications" happened to be off, WorkManager was never
        // asked to schedule anything at all - the schedule looked "saved" in the
        // UI but nothing was actually running in the background. Force it on so
        // saving a schedule always does what it visibly promises.
        if (!_uiState.value.settings.autoSendEnabled) {
            container.settingsRepository.updateAutoSend(true)
        }
        val updated = _uiState.value.settings.copy(
            scheduleMode = mode, intervalHours = intervalHours, autoSendEnabled = true
        )
        WorkScheduler.applySchedule(getApplication(), updated)
        if (mode == ScheduleMode.INTERVAL) {
            // An interval schedule's first run doesn't fire until a full
            // interval has passed (e.g. up to 4h for "every 4h"), which reads
            // as "nothing happens" if you're checking right after saving. Fire
            // one immediate confirmation notification now; the periodic job
            // then continues on the chosen interval from here on.
            WorkScheduler.runNow(getApplication(), sendNotification = true)
            confirm("Interval saved - sending a confirmation notification now")
        }
    }

    fun updateDarkMode(dark: Boolean, useSystem: Boolean) = viewModelScope.launch {
        container.settingsRepository.updateDarkMode(dark, useSystem)
    }

    fun updateAutoSend(enabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.updateAutoSend(enabled)
        val s = _uiState.value.settings
        if (enabled) {
            WorkScheduler.applySchedule(getApplication(), s.copy(autoSendEnabled = true))
        } else {
            WorkScheduler.cancelDaily(getApplication())
        }
    }

    fun updateNotifications(enabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.updateNotifications(enabled)
    }

    fun updateCurrencyPair(base: String, target: String) = viewModelScope.launch {
        container.settingsRepository.updateCurrencyPair(base, target)
        confirm("Currency pair saved")
    }

    fun updateNewsFeeds(selectedIds: Set<String>, customFeeds: List<CustomFeed>) = viewModelScope.launch {
        container.settingsRepository.updateNewsFeeds(selectedIds, customFeeds)
        confirm("News sources saved")
    }

    fun updateWeatherAlertsEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.updateWeatherAlertsEnabled(enabled)
    }

    fun updatePoliticsNewsEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.updatePoliticsNewsEnabled(enabled)
        confirm(if (enabled) "US Politics card turned on" else "US Politics card turned off")
    }

    fun updateBusinessNewsEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settingsRepository.updateBusinessNewsEnabled(enabled)
        confirm(if (enabled) "Business card turned on" else "Business card turned off")
    }

    fun updatePoliticsFeeds(selectedIds: Set<String>) = viewModelScope.launch {
        container.settingsRepository.updatePoliticsFeeds(selectedIds)
        confirm("Politics sources saved")
    }

    fun updateBusinessFeeds(selectedIds: Set<String>) = viewModelScope.launch {
        container.settingsRepository.updateBusinessFeeds(selectedIds)
        confirm("Business sources saved")
    }

    fun updateCustomAlertRules(rules: CustomAlertRules) = viewModelScope.launch {
        container.settingsRepository.updateCustomAlertRules(rules)
        WorkScheduler.applyWeatherAlertCheckSchedule(getApplication(), rules.enabled)
        confirm(if (rules.enabled) "Custom weather alert rules saved" else "Custom weather alert rules turned off")
    }

    fun updateExtraCurrencyPairs(pairs: List<CurrencyPairConfig>) = viewModelScope.launch {
        container.settingsRepository.updateExtraCurrencyPairs(pairs)
        confirm("Currency pairs saved")
    }

    fun updateYoutubeChannels(channels: List<com.morningdigest.app.data.prefs.YoutubeChannelConfig>) = viewModelScope.launch {
        container.settingsRepository.updateYoutubeChannels(channels)
        WorkScheduler.applyYoutubeCheckSchedule(getApplication(), channels.isNotEmpty())
        confirm("YouTube channels saved")
    }

    fun updateDashboardCardOrder(order: List<String>) = viewModelScope.launch {
        container.settingsRepository.updateDashboardCardOrder(order)
        confirm("Dashboard layout saved")
    }

    fun updateEnabledAssistants(ids: Set<String>) = viewModelScope.launch {
        container.settingsRepository.updateEnabledAssistants(ids)
        confirm("Assistant reports updated")
    }
}
