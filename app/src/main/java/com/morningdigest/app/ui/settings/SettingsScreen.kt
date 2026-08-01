package com.morningdigest.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.filled.Close
import com.morningdigest.app.data.prefs.AssetRef
import com.morningdigest.app.data.prefs.AssetType
import com.morningdigest.app.data.prefs.CountryCatalog
import com.morningdigest.app.data.prefs.CurrencyCatalog
import com.morningdigest.app.data.prefs.CurrencyPairConfig
import com.morningdigest.app.data.prefs.CustomFeed
import com.morningdigest.app.data.prefs.ScheduleMode
import com.morningdigest.app.ui.theme.Elevation
import com.morningdigest.app.ui.theme.Spacing
import com.morningdigest.app.data.remote.BusinessFeedCatalog
import com.morningdigest.app.data.remote.CryptoCatalog
import com.morningdigest.app.data.remote.NewsFeedCatalog
import com.morningdigest.app.data.remote.PoliticsFeedCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var userName by remember(state.settings.userName) { mutableStateOf(state.settings.userName) }
    var city by remember(state.settings.city) { mutableStateOf(state.settings.city) }
    var country by remember(state.settings.country) { mutableStateOf(state.settings.country) }
    var policeMunicipalities by remember(state.settings.policeMunicipalities) {
        mutableStateOf(state.settings.policeMunicipalities.ifEmpty { listOf(state.settings.policeMunicipality) })
    }
    var policeEnabled by remember(state.settings.policeAlertsEnabled) { mutableStateOf(state.settings.policeAlertsEnabled) }
    var policeCategories by remember(state.settings.policeCategories) { mutableStateOf(state.settings.policeCategories) }
    var apiKey by remember(state.settings.weatherApiKey) { mutableStateOf(state.settings.weatherApiKey) }
    var hourText by remember(state.settings.wakeHour) { mutableStateOf(state.settings.wakeHour.toString()) }
    var minuteText by remember(state.settings.wakeMinute) { mutableStateOf(state.settings.wakeMinute.toString().padStart(2, '0')) }
    var scheduleMode by remember(state.settings.scheduleMode) { mutableStateOf(state.settings.scheduleMode) }
    var intervalHours by remember(state.settings.intervalHours) { mutableStateOf(state.settings.intervalHours) }
    var currencyBase by remember(state.settings.currencyBase) { mutableStateOf(state.settings.currencyBase) }
    var currencyTarget by remember(state.settings.currencyTarget) { mutableStateOf(state.settings.currencyTarget) }
    var extraPairs by remember(state.settings.extraCurrencyPairs) { mutableStateOf(state.settings.extraCurrencyPairs) }
    var youtubeChannels by remember(state.settings.youtubeChannels) { mutableStateOf(state.settings.youtubeChannels) }
    var newChannelInput by remember { mutableStateOf("") }
    var cardOrder by remember(state.settings.dashboardCardOrder) { mutableStateOf(state.settings.dashboardCardOrder) }
    var enabledAssistantIds by remember(state.settings.enabledAssistantIds) {
        mutableStateOf(state.settings.enabledAssistantIds)
    }

    // Combined "From"/"To" picker options for additional pairs - every fiat
    // currency plus every catalog crypto coin, encoded as "CUR:USD" /
    // "CRY:bitcoin" so a single flat dropdown list can offer both at once.
    val assetPickerOptions = remember {
        CurrencyCatalog.ALL.map { "CUR:${it.code}" to it.label } +
            CryptoCatalog.ALL.map { "CRY:${it.id}" to "${it.displayName} (${it.symbol})" }
    }
    var newPairFromKey by remember { mutableStateOf(assetPickerOptions.first().first) }
    var newPairToKey by remember { mutableStateOf(assetPickerOptions.last().first) }

    fun decodeAssetKey(key: String): AssetRef {
        val code = key.substringAfter(":")
        return if (key.startsWith("CRY:")) AssetRef(AssetType.CRYPTO, code) else AssetRef(AssetType.CURRENCY, code)
    }
    fun encodeAssetKey(asset: AssetRef): String =
        if (asset.type == AssetType.CRYPTO) "CRY:${asset.code}" else "CUR:${asset.code}"
    fun assetLabel(asset: AssetRef): String {
        val key = encodeAssetKey(asset)
        return assetPickerOptions.firstOrNull { it.first == key }?.second ?: asset.code
    }
    var selectedFeedIds by remember(state.settings.selectedNewsFeedIds) { mutableStateOf(state.settings.selectedNewsFeedIds) }
    // The user's own added outlets (e.g. Yahoo News) - unlimited, not capped to one.
    var customFeeds by remember(state.settings.customFeeds) { mutableStateOf(state.settings.customFeeds) }
    var newFeedLabel by remember { mutableStateOf("") }
    var newFeedUrl by remember { mutableStateOf("") }
    var newFeedTopic by remember { mutableStateOf("World") }

    // Optional dedicated Politics/Business cards - each with its own on/off
    // switch and its own curated source list, same interaction pattern as
    // the main News Sources picker above.
    var politicsEnabled by remember(state.settings.politicsNewsEnabled) { mutableStateOf(state.settings.politicsNewsEnabled) }
    var selectedPoliticsIds by remember(state.settings.selectedPoliticsFeedIds) { mutableStateOf(state.settings.selectedPoliticsFeedIds) }
    var businessEnabled by remember(state.settings.businessNewsEnabled) { mutableStateOf(state.settings.businessNewsEnabled) }
    var selectedBusinessIds by remember(state.settings.selectedBusinessFeedIds) { mutableStateOf(state.settings.selectedBusinessFeedIds) }

    // Custom weather alert rules - one local var per field, all saved together
    // via a single "Save Alert Rules" button (same pattern as News Sources).
    var customAlertRules by remember(state.settings.customAlertRules) { mutableStateOf(state.settings.customAlertRules) }
    var tempAboveText by remember(state.settings.customAlertRules.tempAboveValue) { mutableStateOf(formatRuleNumber(state.settings.customAlertRules.tempAboveValue)) }
    var tempBelowText by remember(state.settings.customAlertRules.tempBelowValue) { mutableStateOf(formatRuleNumber(state.settings.customAlertRules.tempBelowValue)) }
    var uvIndexText by remember(state.settings.customAlertRules.uvIndexValue) { mutableStateOf(formatRuleNumber(state.settings.customAlertRules.uvIndexValue)) }
    var windSpeedText by remember(state.settings.customAlertRules.windSpeedValue) { mutableStateOf(formatRuleNumber(state.settings.customAlertRules.windSpeedValue)) }
    var rainProbText by remember(state.settings.customAlertRules.rainProbValue) { mutableStateOf(state.settings.customAlertRules.rainProbValue.toString()) }

    // Confirmed/failed status per news source, so it's obvious at a glance
    // whether an outlet is actually delivering headlines instead of silently
    // contributing nothing to the digest.
    val feedCheckResults by viewModel.feedCheckResults.collectAsState()
    val addChannelState by viewModel.addChannelState.collectAsState()
    LaunchedEffect(state.settings.customFeeds) {
        state.settings.customFeeds.forEach { feed -> viewModel.testFeed(feed.id, feed.url) }
    }
    // A half-typed/edited URL shouldn't keep showing a stale "confirmed" badge.
    LaunchedEffect(newFeedUrl) { viewModel.clearFeedCheck(NEW_FEED_TEST_KEY) }

    // Surface a quick "✓ Saved" Snackbar after each save action, so tapping a
    // Save button gives visible confirmation instead of silently doing nothing.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.saveEvents.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val density = LocalDensity.current
        val swipeThresholdPx = with(density) { 96.dp.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onHorizontalDrag = { _, dragAmount -> totalDragX += dragAmount },
                        // Left-to-right swipe (finger moving right) goes back
                        // to the main screen, mirroring the right-to-left
                        // swipe that opens Settings from there.
                        onDragEnd = { if (totalDragX > swipeThresholdPx) onBack() },
                        onDragCancel = { totalDragX = 0f }
                    )
                }
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {

            SettingsGroupHeader("Personal & Location")
            SectionCard(
                "👤 Personal & Location",
                subtitle = listOf(userName, city).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Not set" }
            ) { collapse ->
                OutlinedTextField(
                    value = userName, onValueChange = { userName = it },
                    label = { Text("Your name") }, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Used for \"Good morning/evening, $userName\" greetings") }
                )
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = city, onValueChange = { city = it },
                    label = { Text("City") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DropdownField(
                    label = "Country",
                    selectedValue = country,
                    options = CountryCatalog.ALL.map { it.code to it.label },
                    onSelected = { country = it }
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    viewModel.updateUserName(userName)
                    viewModel.updateCityCountry(city, country)
                    collapse()
                }) { Text("Save") }
            }

            SectionCard(
                "🚨 Nearby Police Incidents",
                subtitle = if (policeEnabled) "${policeCategories.size} categories · ${policeMunicipalities.size} municipalities" else "Off"
            ) { collapse ->
                // One save button for the complete police configuration.
                Button(
                    onClick = {
                        viewModel.updatePoliceSettings(policeMunicipalities, policeEnabled, policeCategories)
                        collapse()
                    },
                    enabled = policeMunicipalities.isNotEmpty()
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Police Settings")
                }
                Spacer(Modifier.height(8.dp))

                Text(
                    "Choose one or more municipalities. Max will keep their reports separated by municipality. Police messages are translated to English online.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow("Show nearby police incidents", policeEnabled) { policeEnabled = it }
                Spacer(Modifier.height(8.dp))

                Text(
                    "Monitored municipalities",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))

                if (policeMunicipalities.isEmpty()) {
                    Text(
                        "No municipalities selected yet.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        policeMunicipalities.forEach { selected ->
                            InputChip(
                                selected = true,
                                onClick = {},
                                label = { Text(selected) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            policeMunicipalities = policeMunicipalities.filterNot {
                                                it.equals(selected, ignoreCase = true)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Remove $selected")
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // District-first picker, like the official Politiloggen app:
                // pick a police district, then pick a municipality/city inside
                // it. This guarantees full coverage (e.g. Finnmark -> Kirkenes)
                // even for places that haven't had a recent incident logged.
                LaunchedEffect(Unit) {
                    viewModel.loadPoliceDistricts()
                }

                var districtMenuExpanded by remember { mutableStateOf(false) }
                var districtMunicipalityMenuExpanded by remember { mutableStateOf(false) }

                Text(
                    "Browse by police district",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = districtMenuExpanded && state.policeDistricts.isNotEmpty(),
                    onExpandedChange = { districtMenuExpanded = !districtMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = state.selectedPoliceDistrict?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Police district") },
                        placeholder = { Text(if (state.policeDistrictsLoading) "Loading districts…" else "Choose a district (e.g. Finnmark)") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = {
                            if (state.policeDistrictsLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtMenuExpanded)
                            }
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = districtMenuExpanded && state.policeDistricts.isNotEmpty(),
                        onDismissRequest = { districtMenuExpanded = false }
                    ) {
                        state.policeDistricts.forEach { district ->
                            DropdownMenuItem(
                                text = { Text(district.name) },
                                onClick = {
                                    districtMenuExpanded = false
                                    districtMunicipalityMenuExpanded = true
                                    viewModel.selectPoliceDistrict(district)
                                }
                            )
                        }
                    }
                }

                if (state.selectedPoliceDistrict != null) {
                    Spacer(Modifier.height(8.dp))
                    val districtMunicipalityMatches = remember(
                        state.policeDistrictMunicipalities,
                        policeMunicipalities
                    ) {
                        state.policeDistrictMunicipalities.filter { candidate ->
                            policeMunicipalities.none { it.equals(candidate, ignoreCase = true) }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = districtMunicipalityMenuExpanded && districtMunicipalityMatches.isNotEmpty(),
                        onExpandedChange = { districtMunicipalityMenuExpanded = !districtMunicipalityMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Municipality / city in ${state.selectedPoliceDistrict?.name}") },
                            placeholder = {
                                Text(
                                    if (state.policeDistrictMunicipalitiesLoading) "Loading municipalities…"
                                    else if (districtMunicipalityMatches.isEmpty()) "All added"
                                    else "Tap to add, e.g. Kirkenes"
                                )
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = {
                                if (state.policeDistrictMunicipalitiesLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtMunicipalityMenuExpanded)
                                }
                            }
                        )
                        ExposedDropdownMenu(
                            expanded = districtMunicipalityMenuExpanded && districtMunicipalityMatches.isNotEmpty(),
                            onDismissRequest = { districtMunicipalityMenuExpanded = false }
                        ) {
                            districtMunicipalityMatches.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate) },
                                    onClick = {
                                        policeMunicipalities = policeMunicipalities + candidate
                                        districtMunicipalityMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Incident categories", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                com.morningdigest.app.data.remote.PoliceReportFetcher.CATEGORY_TRANSLATIONS.forEach { (no, en) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            policeCategories = if (no in policeCategories) policeCategories - no else policeCategories + no
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = no in policeCategories,
                            onCheckedChange = { checked ->
                                policeCategories = if (checked) policeCategories + no else policeCategories - no
                            }
                        )
                        Column {
                            Text(en, fontWeight = FontWeight.Medium)
                            Text(no, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Saved municipalities: ${state.settings.policeMunicipalities.joinToString(", ").ifBlank { state.settings.policeMunicipality }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard("🌤 Weather API", subtitle = if (apiKey.isBlank()) "Not set" else "Key saved") { collapse ->
                Text(
                    "Powers the weather, tomorrow's outlook, and severe alerts below.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("OpenWeather API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                val uriHandler = LocalUriHandler.current
                TextButton(
                    onClick = { uriHandler.openUri("https://home.openweathermap.org/users/sign_up") },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Get a free OpenWeather API key")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.updateWeatherApiKey(apiKey); collapse() }) { Text("Save API Key") }
            }

            SettingsGroupHeader("Weather Alerts")
            SectionCard(
                "⚠️ Severe Weather Alerts",
                subtitle = if (state.settings.weatherAlertsEnabled) "On" else "Off"
            ) {
                Text(
                    "Warns you when a storm, flood, or other severe weather alert is active for your location.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                SwitchRow("Enable severe weather alerts", state.settings.weatherAlertsEnabled) {
                    viewModel.updateWeatherAlertsEnabled(it)
                }
            }

            SectionCard(
                "🎯 Custom Weather Alert Rules",
                subtitle = if (customAlertRules.enabled) "On" else "Off"
            ) { collapse ->
                Text(
                    "Set your own thresholds instead of relying only on the provider's severe alerts above - e.g. temperature above/below a number you pick, high UV, strong wind, high rain chance, thunderstorm or snow expected.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                SwitchRow("Enable custom alert rules", customAlertRules.enabled) {
                    customAlertRules = customAlertRules.copy(enabled = it)
                    viewModel.updateCustomAlertRules(customAlertRules)
                }

                AnimatedVisibility(visible = customAlertRules.enabled) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    customAlertRules = customAlertRules.copy(
                                        tempAboveEnabled = true,
                                        tempBelowEnabled = true,
                                        uvIndexEnabled = true,
                                        windSpeedEnabled = true,
                                        rainProbEnabled = true,
                                        thunderstormEnabled = true,
                                        snowEnabled = true,
                                        officialAlertEnabled = true
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Turn on all") }
                            OutlinedButton(
                                onClick = {
                                    customAlertRules = customAlertRules.copy(
                                        tempAboveEnabled = false,
                                        tempBelowEnabled = false,
                                        uvIndexEnabled = false,
                                        windSpeedEnabled = false,
                                        rainProbEnabled = false,
                                        thunderstormEnabled = false,
                                        snowEnabled = false,
                                        officialAlertEnabled = false
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Turn off all") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val resolved = customAlertRules.copy(
                                    tempAboveValue = tempAboveText.toDoubleOrNull() ?: customAlertRules.tempAboveValue,
                                    tempBelowValue = tempBelowText.toDoubleOrNull() ?: customAlertRules.tempBelowValue,
                                    uvIndexValue = uvIndexText.toDoubleOrNull() ?: customAlertRules.uvIndexValue,
                                    windSpeedValue = windSpeedText.toDoubleOrNull() ?: customAlertRules.windSpeedValue,
                                    rainProbValue = rainProbText.toIntOrNull()?.coerceIn(0, 100) ?: customAlertRules.rainProbValue
                                )
                                customAlertRules = resolved
                                viewModel.updateCustomAlertRules(resolved)
                                collapse()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save Alert Rules") }
                        Spacer(Modifier.height(16.dp))
                        Divider()
                        Spacer(Modifier.height(12.dp))

                        Text("Check the forecast this far ahead", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(12, 24, 48).forEach { h ->
                                IntervalChip(
                                    hours = h,
                                    selected = customAlertRules.horizonHours == h,
                                    onClick = { customAlertRules = customAlertRules.copy(horizonHours = h) }
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Text("Notify me this long before the limit is reached", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 6).forEach { h ->
                                IntervalChip(
                                    hours = h,
                                    selected = customAlertRules.leadTimeHours == h,
                                    onClick = { customAlertRules = customAlertRules.copy(leadTimeHours = h) }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Divider()
                        Spacer(Modifier.height(12.dp))

                        AlertRuleNumberRow(
                            label = "Temperature above",
                            unit = "°C",
                            enabled = customAlertRules.tempAboveEnabled,
                            onEnabledChange = { customAlertRules = customAlertRules.copy(tempAboveEnabled = it) },
                            valueText = tempAboveText,
                            onValueTextChange = { tempAboveText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } }
                        )
                        AlertRuleNumberRow(
                            label = "Temperature below",
                            unit = "°C",
                            enabled = customAlertRules.tempBelowEnabled,
                            onEnabledChange = { customAlertRules = customAlertRules.copy(tempBelowEnabled = it) },
                            valueText = tempBelowText,
                            onValueTextChange = { tempBelowText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } }
                        )
                        AlertRuleNumberRow(
                            label = "UV index above",
                            unit = "",
                            enabled = customAlertRules.uvIndexEnabled,
                            onEnabledChange = { customAlertRules = customAlertRules.copy(uvIndexEnabled = it) },
                            valueText = uvIndexText,
                            onValueTextChange = { uvIndexText = it.filter { c -> c.isDigit() || c == '.' } }
                        )
                        AlertRuleNumberRow(
                            label = "Wind speed above",
                            unit = "m/s",
                            enabled = customAlertRules.windSpeedEnabled,
                            onEnabledChange = { customAlertRules = customAlertRules.copy(windSpeedEnabled = it) },
                            valueText = windSpeedText,
                            onValueTextChange = { windSpeedText = it.filter { c -> c.isDigit() || c == '.' } }
                        )
                        AlertRuleNumberRow(
                            label = "Rain probability above",
                            unit = "%",
                            enabled = customAlertRules.rainProbEnabled,
                            onEnabledChange = { customAlertRules = customAlertRules.copy(rainProbEnabled = it) },
                            valueText = rainProbText,
                            onValueTextChange = { rainProbText = it.filter { c -> c.isDigit() } }
                        )

                        Spacer(Modifier.height(4.dp))
                        SwitchRow("Thunderstorm expected", customAlertRules.thunderstormEnabled) {
                            customAlertRules = customAlertRules.copy(thunderstormEnabled = it)
                        }
                        SwitchRow("Snow expected", customAlertRules.snowEnabled) {
                            customAlertRules = customAlertRules.copy(snowEnabled = it)
                        }
                        SwitchRow("Severe weather warning (any official alert)", customAlertRules.officialAlertEnabled) {
                            customAlertRules = customAlertRules.copy(officialAlertEnabled = it)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Temperature, wind, rain, thunderstorm and snow work with your normal OpenWeather key. UV index and \"any official alert\" need a One Call 3.0-enabled key (same as Severe Weather Alerts above) - if that's not available those two just won't trigger.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsGroupHeader("Currency")
            SectionCard(
                "💱 Currency Pair",
                subtitle = "$currencyBase → $currencyTarget" +
                    (extraPairs.size).let { n -> if (n > 0) " (+$n)" else "" }
            ) { collapse ->
                Text(
                    "Pick which currencies the exchange-rate card and notification track.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                DropdownField(
                    label = "From",
                    selectedValue = currencyBase,
                    options = CurrencyCatalog.ALL.map { it.code to it.label },
                    onSelected = { currencyBase = it }
                )
                Spacer(Modifier.height(8.dp))
                DropdownField(
                    label = "To",
                    selectedValue = currencyTarget,
                    options = CurrencyCatalog.ALL.map { it.code to it.label },
                    onSelected = { currencyTarget = it }
                )

                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(14.dp))

                Text(
                    "Additional pairs",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add more From → To pairs to show as small cards on the main screen - pick any currency or crypto for either side.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                DropdownField(
                    label = "From",
                    selectedValue = newPairFromKey,
                    options = assetPickerOptions,
                    onSelected = { newPairFromKey = it }
                )
                Spacer(Modifier.height(8.dp))
                DropdownField(
                    label = "To",
                    selectedValue = newPairToKey,
                    options = assetPickerOptions,
                    onSelected = { newPairToKey = it }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val from = decodeAssetKey(newPairFromKey)
                        val to = decodeAssetKey(newPairToKey)
                        val config = CurrencyPairConfig(from, to)
                        if (from != to && config !in extraPairs) {
                            extraPairs = extraPairs + config
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Pair")
                }

                if (extraPairs.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    extraPairs.forEachIndexed { index, pair ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${assetLabel(pair.from)} → ${assetLabel(pair.to)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(
                                onClick = { extraPairs = extraPairs.filterIndexed { i, _ -> i != index } },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove pair", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    viewModel.updateCurrencyPair(currencyBase, currencyTarget)
                    viewModel.updateExtraCurrencyPairs(extraPairs)
                    collapse()
                }) {
                    Text("Save Currency Pair")
                }
            }

            SettingsGroupHeader("YouTube")
            SectionCard(
                "📺 YouTube Channels",
                subtitle = if (youtubeChannels.isEmpty()) {
                    "None added"
                } else {
                    val suffix = if (youtubeChannels.size == 1) "" else "s"
                    "${youtubeChannels.size} channel$suffix"
                }
            ) { collapse ->
                Button(onClick = { viewModel.updateYoutubeChannels(youtubeChannels); collapse() }) {
                    Text("Save Channels")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Paste a channel link (or just \"@handlename\") and get a bubble on the main page whenever it posts a new video - no account or API key needed.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                LaunchedEffect(addChannelState) {
                    val result = addChannelState
                    if (result is AddChannelUiState.Success) {
                        if (youtubeChannels.none { it.channelId == result.channel.channelId }) {
                            youtubeChannels = youtubeChannels + result.channel
                        }
                        newChannelInput = ""
                        viewModel.resetAddChannelState()
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newChannelInput,
                        onValueChange = { newChannelInput = it },
                        label = { Text("Channel link or @handle") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = addChannelState !is AddChannelUiState.Loading
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.addYoutubeChannel(newChannelInput) },
                        enabled = newChannelInput.isNotBlank() && addChannelState !is AddChannelUiState.Loading
                    ) {
                        if (addChannelState is AddChannelUiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Add")
                        }
                    }
                }
                (addChannelState as? AddChannelUiState.Failure)?.let { failure ->
                    Spacer(Modifier.height(6.dp))
                    Text(failure.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }

                if (youtubeChannels.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Divider(color = Color.Gray.copy(alpha = 0.15f))
                    Spacer(Modifier.height(10.dp))
                    youtubeChannels.forEach { channel ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            coil.compose.AsyncImage(
                                model = channel.avatarUrl,
                                contentDescription = channel.name,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                channel.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { youtubeChannels = youtubeChannels.filterNot { it.channelId == channel.channelId } },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove channel", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            SettingsGroupHeader("Dashboard & Assistants")
            SectionCard(
                "🧩 Dashboard Layout",
                subtitle = "Reorder your cards"
            ) { collapse ->
                Text(
                    "Move cards up or down to change what shows first on your dashboard. Weather, alerts, and the greeting always stay at the top.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                cardOrder.forEachIndexed { index, key ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            com.morningdigest.app.data.prefs.DashboardCards.LABELS[key] ?: key,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                cardOrder = cardOrder.toMutableList().apply {
                                    val tmp = this[index - 1]; this[index - 1] = this[index]; this[index] = tmp
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp)) }
                        IconButton(
                            enabled = index < cardOrder.lastIndex,
                            onClick = {
                                cardOrder = cardOrder.toMutableList().apply {
                                    val tmp = this[index + 1]; this[index + 1] = this[index]; this[index] = tmp
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.updateDashboardCardOrder(cardOrder); collapse() }) {
                    Text("Save Layout")
                }
            }

            SectionCard(
                "🤖 Assistant Reports",
                subtitle = "${enabledAssistantIds.size} of ${com.morningdigest.app.data.prefs.MascotCharacter.entries.size} on"
            ) {
                Text(
                    "Six analysts, each covering their own topic and publishing an automatic daily briefing in the Assistants tab - no chat, just a quick summary. Turn on the ones you care about.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                com.morningdigest.app.data.prefs.MascotCharacter.entries.forEach { candidate ->
                    val isOn = candidate.id in enabledAssistantIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                enabledAssistantIds = if (isOn) enabledAssistantIds - candidate.id else enabledAssistantIds + candidate.id
                                viewModel.updateEnabledAssistants(enabledAssistantIds)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.morningdigest.app.ui.mascot.MascotIllustration(candidate, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${candidate.displayName} · ${candidate.role}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                candidate.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = isOn,
                            onCheckedChange = {
                                enabledAssistantIds = if (it) enabledAssistantIds + candidate.id else enabledAssistantIds - candidate.id
                                viewModel.updateEnabledAssistants(enabledAssistantIds)
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            SettingsGroupHeader("News Sources")
            SectionCard(
                "📰 News Sources",
                subtitle = (selectedFeedIds.size + customFeeds.size).let { n -> "$n source${if (n == 1) "" else "s"} selected" }
            ) { collapse ->
                Button(
                    onClick = { viewModel.updateNewsFeeds(selectedFeedIds, customFeeds); collapse() },
                    enabled = selectedFeedIds.isNotEmpty() || customFeeds.isNotEmpty()
                ) {
                    Text("Save News Sources")
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { selectedFeedIds = NewsFeedCatalog.ALL.map { it.id }.toSet() },
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) { Text("Select all") }
                    TextButton(
                        onClick = { selectedFeedIds = emptySet() },
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) { Text("Clear all") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Choose which sources/topics feed your digest, so it reflects what you actually care about.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${selectedFeedIds.size} of ${NewsFeedCatalog.ALL.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                NewsFeedCatalog.ALL.groupBy { it.topic }.forEach { (topic, feeds) ->
                    Text(
                        topic,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                    feeds.forEach { feed ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFeedIds = if (feed.id in selectedFeedIds) {
                                        selectedFeedIds - feed.id
                                    } else {
                                        selectedFeedIds + feed.id
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = feed.id in selectedFeedIds,
                                onCheckedChange = { checked ->
                                    selectedFeedIds = if (checked) selectedFeedIds + feed.id else selectedFeedIds - feed.id
                                }
                            )
                            Text(feed.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Divider(color = Color.Gray.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Add your own outlets",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Not on the list above? Add any RSS feed - Yahoo News, a blog, anything. No limit, add as many as you want.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // Already-added custom outlets, each removable and grouped by
                // its own topic - defaults to World, same as the catalog above.
                if (customFeeds.isNotEmpty()) {
                    customFeeds.forEach { feed ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(feed.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${feed.topic} · ${feed.url}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                FeedCheckBadge(feedCheckResults[feed.id])
                            }
                            IconButton(onClick = { viewModel.testFeed(feed.id, feed.url) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Re-test ${feed.label}")
                            }
                            IconButton(onClick = { customFeeds = customFeeds - feed }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove ${feed.label}")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = newFeedLabel, onValueChange = { newFeedLabel = it },
                    label = { Text("Source name (e.g. Yahoo News)") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newFeedUrl, onValueChange = { newFeedUrl = it },
                    label = { Text("RSS feed URL") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DropdownField(
                    label = "Section",
                    selectedValue = newFeedTopic,
                    options = listOf("World", "Business", "Technology", "Sport", "Science").map { it to it },
                    onSelected = { newFeedTopic = it }
                )
                Spacer(Modifier.height(8.dp))
                FeedCheckBadge(feedCheckResults[NEW_FEED_TEST_KEY])
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { viewModel.testFeed(NEW_FEED_TEST_KEY, newFeedUrl.trim()) },
                        enabled = newFeedUrl.isNotBlank() && feedCheckResults[NEW_FEED_TEST_KEY] !is FeedCheckUiState.Checking
                    ) {
                        Text("Test link")
                    }
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        onClick = {
                            if (newFeedLabel.isNotBlank() && newFeedUrl.isNotBlank()) {
                                customFeeds = customFeeds + CustomFeed(
                                    id = "custom_${System.currentTimeMillis()}",
                                    label = newFeedLabel.trim(),
                                    url = newFeedUrl.trim(),
                                    topic = newFeedTopic
                                )
                                newFeedLabel = ""
                                newFeedUrl = ""
                                newFeedTopic = "World"
                            }
                        },
                        enabled = newFeedLabel.isNotBlank() && newFeedUrl.isNotBlank()
                    ) {
                        Text("+ Add outlet")
                    }
                }
                Text(
                    "Tip: test the link first so you know it's confirmed before adding it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))
                if (selectedFeedIds.isEmpty() && customFeeds.isEmpty()) {
                    Text(
                        "Pick at least one source (or add your own outlet) so your digest has news to show.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            SectionCard(
                "🏛 US Politics",
                subtitle = if (politicsEnabled) "On · ${selectedPoliticsIds.size} of ${PoliticsFeedCatalog.ALL.size} sources" else "Off"
            ) { collapse ->
                Button(onClick = { viewModel.updatePoliticsFeeds(selectedPoliticsIds); collapse() }) {
                    Text("Save Politics Sources")
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { selectedPoliticsIds = PoliticsFeedCatalog.ALL.map { it.id }.toSet() },
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) { Text("Select all") }
                    TextButton(
                        onClick = { selectedPoliticsIds = emptySet() },
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) { Text("Clear all") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Powers Anja's US Politics briefing in the Assistants tab - the newest 30 headlines, mixed from every source you select below (e.g. Politico, The Hill, BBC, CNBC), freshest on top.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                SwitchRow("Enabled", politicsEnabled) {
                    politicsEnabled = it
                    viewModel.updatePoliticsNewsEnabled(it)
                }
                Spacer(Modifier.height(8.dp))
                Divider(color = Color.Gray.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))
                TopicSourcesPicker(
                    catalog = PoliticsFeedCatalog.ALL,
                    selectedIds = selectedPoliticsIds,
                    onSelectionChange = { selectedPoliticsIds = it },
                    showSelectionControls = false
                )
            }

            SectionCard(
                "💼 Business",
                subtitle = if (businessEnabled) "On · ${selectedBusinessIds.size} of ${BusinessFeedCatalog.ALL.size} sources" else "Off"
            ) { collapse ->
                Button(onClick = { viewModel.updateBusinessFeeds(selectedBusinessIds); collapse() }) {
                    Text("Save Business Sources")
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { selectedBusinessIds = BusinessFeedCatalog.ALL.map { it.id }.toSet() },
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) { Text("Select all") }
                    TextButton(
                        onClick = { selectedBusinessIds = emptySet() },
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) { Text("Clear all") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Powers Panda's Business briefing in the Assistants tab - the newest 30 headlines, freshest on top.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                SwitchRow("Enabled", businessEnabled) {
                    businessEnabled = it
                    viewModel.updateBusinessNewsEnabled(it)
                }
                Spacer(Modifier.height(8.dp))
                Divider(color = Color.Gray.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))
                TopicSourcesPicker(
                    catalog = BusinessFeedCatalog.ALL,
                    selectedIds = selectedBusinessIds,
                    onSelectionChange = { selectedBusinessIds = it },
                    showSelectionControls = false
                )
            }

            SettingsGroupHeader("Notifications")
            SectionCard(
                "🔔 Notification Schedule",
                subtitle = if (scheduleMode == ScheduleMode.DAILY)
                    "Daily at ${hourText.padStart(2, '0')}:${minuteText.padStart(2, '0')}"
                else
                    "Every ${intervalHours}h"
            ) { collapse ->
                Text(
                    "When should your morning brief notification arrive?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // Mode toggle: fixed daily time vs repeating interval.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScheduleModeChip(
                        label = "Once a day",
                        selected = scheduleMode == ScheduleMode.DAILY,
                        onClick = { scheduleMode = ScheduleMode.DAILY },
                        modifier = Modifier.weight(1f)
                    )
                    ScheduleModeChip(
                        label = "Every N hours",
                        selected = scheduleMode == ScheduleMode.INTERVAL,
                        onClick = { scheduleMode = ScheduleMode.INTERVAL },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (scheduleMode == ScheduleMode.DAILY) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = hourText, onValueChange = { hourText = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Hour") }, modifier = Modifier.width(100.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = minuteText, onValueChange = { minuteText = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Minute") }, modifier = Modifier.width(100.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        val h = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 7
                        val m = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        viewModel.updateWakeTime(h, m)
                        viewModel.updateSchedule(ScheduleMode.DAILY, intervalHours)
                        collapse()
                    }) { Text("Save Notification Time") }
                } else {
                    Text(
                        "Posts a fresh notification every ${intervalHours}h, around the clock.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 4, 6, 8, 12).forEach { hrs ->
                            IntervalChip(
                                hours = hrs,
                                selected = intervalHours == hrs,
                                onClick = { intervalHours = hrs }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        viewModel.updateSchedule(ScheduleMode.INTERVAL, intervalHours)
                        collapse()
                    }) { Text("Save Interval") }
                }
            }

            SettingsGroupHeader("Appearance")
            SectionCard(
                "🎨 Appearance & Behavior",
                subtitle = "${if (state.settings.useSystemTheme) "System theme" else if (state.settings.darkMode) "Dark" else "Light"} · Notifications ${if (state.settings.notificationsEnabled) "On" else "Off"}"
            ) {
                SwitchRow("Dark Mode", state.settings.darkMode && !state.settings.useSystemTheme) {
                    viewModel.updateDarkMode(it, false)
                }
                SwitchRow("Use system theme", state.settings.useSystemTheme) {
                    viewModel.updateDarkMode(state.settings.darkMode, it)
                }
                SwitchRow("Auto-send notifications", state.settings.autoSendEnabled) {
                    viewModel.updateAutoSend(it)
                }
                SwitchRow("Enable notifications", state.settings.notificationsEnabled) {
                    viewModel.updateNotifications(it)
                }
                Text(
                    "Turning notifications off still refreshes and saves your digest to History/widget — it just won't pop up a banner.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Currency rates: Yahoo Finance, Norges Bank, ECB, Frankfurter/ECB and ExchangeRate-API.\nExchangeRate-API Open Access attribution: https://www.exchangerate-api.com",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            )
        }
        }
    }
}

/**
 * Small inline status line for a news source: nothing until tested, a spinner
 * while checking, a green "Confirmed" line with the article count on success,
 * or a red line with a specific, actionable reason on failure - so a bad link
 * never just silently shows no news.
 */
@Composable
private fun FeedCheckBadge(state: FeedCheckUiState?) {
    when (state) {
        null, FeedCheckUiState.Idle -> {}
        FeedCheckUiState.Checking -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Checking feed…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        is FeedCheckUiState.Success -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle, contentDescription = null,
                    tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Confirmed - found ${state.headlineCount} article${if (state.headlineCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32)
                )
            }
        }
        is FeedCheckUiState.Failure -> {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Error, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    state.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Checkbox list of curated sources for a single-topic card (Politics or
 * Business), with Select all/Clear all - same interaction as the main News
 * Sources picker above, just reused for a smaller, focused catalog.
 */
@Composable
private fun TopicSourcesPicker(
    catalog: List<NewsFeedCatalog.Feed>,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    showSelectionControls: Boolean = true
) {
    if (showSelectionControls) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${selectedIds.size} of ${catalog.size} selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(
                    onClick = { onSelectionChange(catalog.map { it.id }.toSet()) },
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) { Text("Select all") }
                TextButton(
                    onClick = { onSelectionChange(emptySet()) },
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) { Text("Clear all") }
            }
        }
    } else {
        Text(
            "${selectedIds.size} of ${catalog.size} selected",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(4.dp))
    catalog.forEach { feed ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    onSelectionChange(if (feed.id in selectedIds) selectedIds - feed.id else selectedIds + feed.id)
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = feed.id in selectedIds,
                onCheckedChange = { checked ->
                    onSelectionChange(if (checked) selectedIds + feed.id else selectedIds - feed.id)
                }
            )
            Text(feed.label, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (selectedIds.isEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick at least one source or this card will show \"Unavailable\".",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == selectedValue }?.second ?: selectedValue

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (code, label2) ->
                DropdownMenuItem(
                    text = { Text(label2) },
                    onClick = {
                        onSelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Splits "🌤 Weather API" into ("🌤", "Weather API") - every SectionCard title in this screen is authored as "<glyph> <words>". */
private fun splitLeadingGlyph(title: String): Pair<String, String> {
    val spaceIndex = title.indexOf(' ')
    if (spaceIndex <= 0) return "" to title
    val prefix = title.substring(0, spaceIndex)
    val rest = title.substring(spaceIndex + 1)
    return if (prefix.length <= 4) prefix to rest else "" to title
}

@Composable
private fun SettingsGroupHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.(collapse: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "chevron"
    )
    // Titles are consistently authored as "🌤 Weather API" - split the
    // leading glyph out into its own tinted avatar rather than leaving it
    // inline with the text, so every settings row reads like a proper
    // grouped list (icon · title · chevron) instead of an emoji-prefixed string.
    val (glyph, label) = remember(title) { splitLeadingGlyph(title) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (glyph.isNotBlank()) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(glyph, fontSize = 18.sp)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    if (!expanded && subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer(rotationZ = chevronRotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(160)) + shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing))
            ) {
                Column(
                    Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md, top = Spacing.xxs)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(Spacing.sm))
                    content { expanded = false }
                }
            }
        }
    }
}

@Composable
private fun ScheduleModeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF4A3FCF) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun IntervalChip(hours: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF4A3FCF) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "${hours}h",
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * One row of a custom weather alert rule: an on/off switch plus, when
 * enabled, an inline numeric field for the user's own threshold (e.g.
 * "Temperature above [30] °C"). Used for every numeric rule in the Custom
 * Weather Alert Rules card.
 */
@Composable
private fun AlertRuleNumberRow(
    label: String,
    unit: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    valueText: String,
    onValueTextChange: (String) -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        AnimatedVisibility(visible = enabled) {
            OutlinedTextField(
                value = valueText,
                onValueChange = onValueTextChange,
                label = { Text(if (unit.isBlank()) "Value" else "Value ($unit)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp)
            )
        }
    }
}

/** Renders a threshold value without a trailing ".0" for whole numbers (e.g. "30" not "30.0"). */
private fun formatRuleNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
