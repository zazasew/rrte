package com.morningdigest.app.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.morningdigest.app.MorningDigestApp
import com.morningdigest.app.data.prefs.PairAlertRule
import com.morningdigest.app.data.prefs.PriceAlertRules
import com.morningdigest.app.data.prefs.MascotCharacter
import com.morningdigest.app.ui.mascot.AnimatedMascotIllustration
import com.morningdigest.app.worker.WorkScheduler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.morningdigest.app.data.model.WeatherDayForecast
import com.morningdigest.app.data.model.WeatherTomorrow
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.morningdigest.app.R
import com.morningdigest.app.data.facts.WeatherOutfitAdvisor
import com.morningdigest.app.data.model.ChartPoint
import com.morningdigest.app.data.model.DigestReport
import com.morningdigest.app.data.model.NewsHeadline
import com.morningdigest.app.data.remote.PoliceReportFetcher
import com.morningdigest.app.notification.DigestNotificationBuilder
import com.morningdigest.app.ui.theme.Elevation
import com.morningdigest.app.ui.theme.MorningDigestTheme
import com.morningdigest.app.ui.theme.NumericStyles
import com.morningdigest.app.ui.theme.Spacing
import com.morningdigest.app.ui.theme.extendedColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bitcoin/currency/fact cards keep a fixed light pastel background in both
 * light and dark mode (a deliberate "sunny" accent look), so their text must
 * use a fixed dark ink color too instead of the theme's onSurface - which
 * flips to a light color in dark mode and would otherwise vanish against
 * these light cards.
 */
// These small info/status cards use a light pastel tint in light mode
// (gold/mint/lavender containers) but a genuinely dark tint in dark mode -
// so the "ink" color used for their text needs to flip too, or dark-on-dark
// would be unreadable at night. Every call site below just reads
// `CardInkColor`; this one property adapting keeps them all correct.
private val CardInkColor: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF1EFFA) else Color(0xFF1C1B2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAssistants: () -> Unit,
    onOpenAssistantDetail: (String) -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Settings (city/name, schedule, and the Politics/Business toggles) are
    // edited on a separate screen - refresh them here whenever this screen
    // resumes (including navigating back from Settings), so a toggle flipped
    // there shows up immediately instead of only after an app restart.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSettingsState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Surface refresh/offline failures as a Snackbar - previously the pull-to-refresh
    // spinner just stopped with no explanation when a refresh failed.
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    // Ask for location permission once so the greeting can show the device's
    // real current location instead of the fixed configured city. If the
    // user declines, the greeting simply falls back to the configured city.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) viewModel.refreshDeviceLocation()
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            viewModel.refreshDeviceLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("The Brief", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenAssistants) {
                        Icon(
                            Icons.Filled.EmojiPeople,
                            contentDescription = "Assistants",
                            tint = Color(0xFFE53935)
                        )
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
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
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        // Whether there's anything for the Alerts card to show at all - used
        // both to decide whether to render the card and whether to show the
        // warning icon in the status bar (which stays visible even after the
        // card itself is dismissed with X, so it can be tapped to bring it back).
        val alerts = state.report?.weatherAlerts
        val hasAnyAlert = (alerts != null && alerts.available && alerts.alerts.isNotEmpty()) ||
            alerts?.customAlerts.orEmpty().isNotEmpty()

        // The card can be dismissed with its X button; tapping the warning
        // icon up in the status bar brings it back. Both transitions scale
        // the card in/out anchored on the icon's on-screen position, so it
        // visually shrinks into (and grows back out of) the icon instead of
        // just fading in place. Deliberately does NOT scroll the list - the
        // card sits right below the status bar already, so scrolling to it
        // would just push the "The Brief" title/top area out of view.
        var alertsCardVisible by remember { mutableStateOf(true) }
        var warningIconCenter by remember { mutableStateOf(Offset.Zero) }
        var alertsCardOrigin by remember { mutableStateOf(Offset.Zero) }
        var alertsCardSize by remember { mutableStateOf(IntSize.Zero) }

        val alertsPivot = remember(warningIconCenter, alertsCardOrigin, alertsCardSize) {
            if (alertsCardSize.width > 0 && alertsCardSize.height > 0) {
                TransformOrigin(
                    (warningIconCenter.x - alertsCardOrigin.x) / alertsCardSize.width,
                    (warningIconCenter.y - alertsCardOrigin.y) / alertsCardSize.height
                )
            } else {
                TransformOrigin(0.5f, 0f)
            }
        }

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
                        // Right-to-left swipe (finger moving left) on the main
                        // screen opens Settings, like swiping to the next
                        // "page". A left-to-right swipe does nothing here.
                        onDragEnd = { if (totalDragX < -swipeThresholdPx) onOpenSettings() },
                        onDragCancel = { totalDragX = 0f }
                    )
                }
        ) {
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.refreshNow() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "status_bar") {
                    StatusBar(
                        state = state,
                        onWarningIconClick = { alertsCardVisible = true },
                        onPoliceIconClick = {
                            viewModel.markPoliceIncidentsSeen()
                            onOpenAssistantDetail(MascotCharacter.MAX.id)
                        },
                        onIconPositioned = { coords ->
                            warningIconCenter = coords.positionInRoot() +
                                Offset(coords.size.width / 2f, coords.size.height / 2f)
                        }
                    )
                }
                item(key = "greeting") { GreetingText(state.userName, state.deviceLocationLabel ?: state.cityLabel) }
                item(key = "alerts") {
                    AnimatedVisibility(
                        visible = alertsCardVisible && hasAnyAlert,
                        enter = fadeIn(tween(480, easing = FastOutSlowInEasing)) +
                            scaleIn(animationSpec = tween(480, easing = FastOutSlowInEasing), transformOrigin = alertsPivot),
                        exit = fadeOut(tween(420, easing = FastOutSlowInEasing)) +
                            scaleOut(animationSpec = tween(420, easing = FastOutSlowInEasing), transformOrigin = alertsPivot)
                    ) {
                        AlertsCard(
                            report = state.report,
                            onDismiss = { alertsCardVisible = false },
                            modifier = Modifier.onGloballyPositioned { coords ->
                                alertsCardOrigin = coords.positionInRoot()
                                alertsCardSize = coords.size
                            }
                        )
                    }
                }
                state.dashboardCardOrder.forEach { cardKey ->
                    when (cardKey) {
                        com.morningdigest.app.data.prefs.DashboardCards.WEATHER ->
                            item(key = "header") { HeaderCard(state.report, state.cityLabel) }

                        com.morningdigest.app.data.prefs.DashboardCards.MARKETS -> {
                            item(key = "markets") {
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    BitcoinCard(viewModel, state.report, state.isRefreshingMarkets, Modifier.weight(1f))
                                    CurrencyCard(viewModel, state.report, state.isRefreshingMarkets, Modifier.weight(1f))
                                }
                            }
                            // Extra currency pairs the user added get the exact same
                            // card treatment as Bitcoin/the main pair (chart on tap,
                            // alert bell, same size) - shown two per row. Anything
                            // involving crypto stays in the compact chip row below,
                            // since it doesn't have chart/alert support (yet).
                            val allExtras = state.report?.watchlist.orEmpty()
                            val fullPairs = allExtras.filter { !it.isCrypto }
                            val chipEntries = allExtras.filter { it.isCrypto }
                            if (fullPairs.isNotEmpty()) {
                                fullPairs.chunked(2).forEachIndexed { idx, pairRow ->
                                    item(key = "extra_pair_row_$idx") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            pairRow.forEach { entry ->
                                                ExtraCurrencyPairCard(viewModel, entry, Modifier.weight(1f))
                                            }
                                            if (pairRow.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            if (chipEntries.isNotEmpty()) {
                                item(key = "watchlist") { WatchlistRow(chipEntries) }
                            }
                        }

                        com.morningdigest.app.data.prefs.DashboardCards.TOMORROW ->
                            item(key = "tomorrow") { TomorrowCard(state.report) }

                        com.morningdigest.app.data.prefs.DashboardCards.FACT ->
                            item(key = "fact") { DailyFactCard(state.report) }

                        // World News, US Politics, and Business no longer have
                        // dashboard cards - they're only shown in the Assistants
                        // tab now (Scoop / Anja / Panda) to avoid duplicating the
                        // same headlines in two places.
                    }
                }
                val youtubeUpdates = state.report?.youtubeUpdates.orEmpty()
                if (youtubeUpdates.isNotEmpty()) {
                    item(key = "youtube_updates") {
                        YoutubeUpdatesSection(
                            updates = youtubeUpdates,
                            onDismiss = { channelId, videoId -> viewModel.dismissYoutubeVideo(channelId, videoId) },
                            onClearAll = { viewModel.dismissAllYoutubeVideos() }
                        )
                    }
                }
                item(key = "actions") {
                    ActionRow(
                        viewModel = viewModel,
                        onScrollToTop = { scope.launch { listState.animateScrollToItem(0) } }
                    )
                }
                item(key = "goodbye") { GoodbyeText() }
                item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
            }
        }
        }
    }
}

@Composable
private fun AlertsCard(report: DigestReport?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val alerts = report?.weatherAlerts
    val hasOfficial = alerts != null && alerts.available && alerts.alerts.isNotEmpty()
    val customMatches = alerts?.customAlerts.orEmpty()
    if (!hasOfficial && customMatches.isEmpty()) return

    val ext = MaterialTheme.extendedColors

    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(if (hasOfficial) MaterialTheme.colorScheme.errorContainer else ext.warningContainer)
                .padding(Spacing.sm)
                // Leave room so the close button doesn't sit on top of the text.
                .padding(end = Spacing.xl)
        ) {
            if (hasOfficial) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Severe Weather Alert${if (alerts!!.alerts.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(8.dp))
                alerts!!.alerts.take(3).forEach { alert ->
                    Text(
                        "• ${alert.event}${if (alert.senderName.isNotBlank()) " — ${alert.senderName}" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (customMatches.isNotEmpty()) {
                if (hasOfficial) {
                    Spacer(Modifier.height(10.dp))
                    Divider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.15f))
                    Spacer(Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = ext.warning, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Your Custom Weather Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        color = ext.onWarningContainer
                    )
                }
                Spacer(Modifier.height(8.dp))
                customMatches.forEach { match ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            if (match.leadWarning) "⏰" else "•",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    match.dayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ext.onWarningContainer,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(ext.warning.copy(alpha = 0.25f))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    match.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (match.leadWarning) FontWeight.Bold else FontWeight.Normal,
                                    color = ext.onWarningContainer
                                )
                            }
                            Text(
                                match.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.onWarningContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(32.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Dismiss weather alerts",
                tint = if (hasOfficial) MaterialTheme.colorScheme.onErrorContainer else ext.onWarningContainer,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StatusBar(
    state: DashboardUiState,
    onWarningIconClick: () -> Unit,
    onPoliceIconClick: () -> Unit,
    onIconPositioned: (LayoutCoordinates) -> Unit
) {
    val ext = MaterialTheme.extendedColors
    val alerts = state.report?.weatherAlerts
    val hasOfficialAlert = alerts != null && alerts.available && alerts.alerts.isNotEmpty()
    val hasImminentCustomAlert = alerts?.customAlerts.orEmpty().any { it.leadWarning }
    val hasUpcomingCustomAlert = alerts?.customAlerts.orEmpty().isNotEmpty()
    val showWarningIcon = hasOfficialAlert || hasUpcomingCustomAlert

    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (state.isOnline) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                contentDescription = null,
                tint = if (state.isOnline) ext.success else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.isOnline) "Online" else "Offline — showing cached data",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (showWarningIcon) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = if (hasOfficialAlert || hasImminentCustomAlert) "Weather alert - tap to view" else "Weather alert coming up - tap to view",
                    tint = if (hasOfficialAlert || hasImminentCustomAlert) MaterialTheme.colorScheme.error else ext.warning,
                    modifier = Modifier
                        .size(18.dp)
                        .onGloballyPositioned(onIconPositioned)
                        .clickable(onClick = onWarningIconClick)
                )
            }
            if (state.hasNewPoliceIncidents) {
                Spacer(Modifier.width(10.dp))
                val shieldSpin = rememberInfiniteTransition(label = "shield_spin")
                val shieldSpinDeg by shieldSpin.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(animation = tween(3400, easing = LinearEasing)),
                    label = "shield_spin_deg"
                )
                Text(
                    "🛡️",
                    fontSize = 15.sp,
                    modifier = Modifier
                        .graphicsLayer(rotationZ = shieldSpinDeg)
                        .clickable(onClick = onPoliceIconClick)
                        .semantics { contentDescription = "New police report from Max - tap to view" }
                )
            }
        }
        state.nextScheduledMillis?.let { nextMillis ->
            Text(
                "Next: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextMillis))}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GreetingText(userName: String, cityLabel: String) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val (emoji, greeting) = when (hour) {
        in 5..11 -> "🌅" to "Good Morning"
        in 12..17 -> "☀️" to "Good Afternoon"
        else -> "🌙" to "Good Evening"
    }
    val name = userName.ifBlank { "there" }
    Column {
        Text(
            "$emoji $greeting, $name",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            dayOfWeekVibe(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "📍 $cityLabel",
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
    }
}

/** A short, varying line based on the day of the week, so the greeting doesn't read identically every day. */
private fun dayOfWeekVibe(): String {
    val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
    return when (day) {
        java.util.Calendar.MONDAY -> "Great start to the week"
        java.util.Calendar.TUESDAY -> "Keep the momentum going"
        java.util.Calendar.WEDNESDAY -> "Halfway through the week"
        java.util.Calendar.THURSDAY -> "Almost there"
        java.util.Calendar.FRIDAY -> "Almost the weekend"
        java.util.Calendar.SATURDAY -> "Enjoy your Saturday"
        else -> "Relax, it's Sunday"
    }
}

@Composable
private fun GoodbyeText() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            "Have a great day! ☀️",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HeaderCard(report: DigestReport?, cityLabel: String) {
    val w = report?.weatherToday
    val kind = weatherKindFor(w?.icon, w?.description)
    val isNight = isNightIcon(w?.icon)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(weatherGradient(kind, isNight))
    ) {
        // Decorative sun/clouds/rain/snow/etc., drawn behind the text so the
        // card's mood matches today's actual weather instead of a fixed color.
        WeatherDecoration(
            kind = kind,
            isNight = isNight,
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
        )
        // Actual falling rain/snow across the whole card when today's real
        // weather is rain/storm/snow - not just the small static icon above.
        WeatherPrecipitationOverlay(
            kind = kind,
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
        )
        if (kind != WeatherKind.SNOW && isWinterMonth()) {
            SeasonalWinterAccent(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
            )
        }

        Column(Modifier.padding(22.dp)) {
            Text(cityLabel, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            if (w != null && w.available) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${w.temp?.roundToInt() ?: "—"}°", color = Color.White, style = NumericStyles.heroValue)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            w.description?.replaceFirstChar { it.uppercase() } ?: "—",
                            color = Color.White, style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Feels like ${w.feelsLike?.roundToInt() ?: "—"}°",
                            color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium
                        )
                        if (w.tempMin != null && w.tempMax != null) {
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
                                Text("${w.tempMax.roundToInt()}°", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
                                Text("${w.tempMin.roundToInt()}°", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Fixed, non-scrolling row - everything fits on screen at once
                // instead of requiring a horizontal swipe to see it all.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniStat("Humidity", "${w.humidity ?: "—"}%")
                    MiniStat("Wind", "${w.windSpeed ?: "—"} m/s")
                    MiniStat("Pressure", "${w.pressure ?: "—"}")
                    MiniStat("🌅 Sunrise", w.sunrise?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: "—")
                    MiniStat("🌇 Sunset", w.sunset?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: "—")
                }
                if (w.parts.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Divider(color = Color.White.copy(alpha = 0.25f))
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        w.parts.forEach { part ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(part.label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(4.dp))
                                Text(weatherEmoji(part.icon, part.description), style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${part.temp?.roundToInt() ?: "—"}°",
                                    color = Color.White,
                                    style = NumericStyles.mediumValue
                                )
                            }
                        }
                    }
                }
                WeatherOutfitAdvisor.suggestionFor(w)?.let { suggestion ->
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(suggestion, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Text("Weather unavailable", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Broad weather categories used to pick the HeaderCard's background mood and decoration. */
private enum class WeatherKind { CLEAR, CLOUDS, RAIN, THUNDERSTORM, SNOW, MIST, UNKNOWN }

/** Maps an OpenWeather icon code (e.g. "10d") to a broad category, falling back to the text description. */
private fun weatherKindFor(icon: String?, description: String?): WeatherKind {
    when (icon?.take(2)) {
        "01" -> return WeatherKind.CLEAR
        "02", "03", "04" -> return WeatherKind.CLOUDS
        "09", "10" -> return WeatherKind.RAIN
        "11" -> return WeatherKind.THUNDERSTORM
        "13" -> return WeatherKind.SNOW
        "50" -> return WeatherKind.MIST
    }
    val d = description?.lowercase().orEmpty()
    return when {
        "thunder" in d || "storm" in d -> WeatherKind.THUNDERSTORM
        "snow" in d -> WeatherKind.SNOW
        "drizzle" in d || "rain" in d -> WeatherKind.RAIN
        "mist" in d || "fog" in d || "haze" in d -> WeatherKind.MIST
        "cloud" in d || "overcast" in d -> WeatherKind.CLOUDS
        "clear" in d || "sun" in d -> WeatherKind.CLEAR
        else -> WeatherKind.UNKNOWN
    }
}

/**
 * Real falling rain streaks or drifting snowflakes across the whole Today
 * card when today's actual weather is rain, thunderstorms, or snow. Driven
 * by a manual per-frame loop (see [AnimatedMascotIllustration] for why) so
 * it keeps animating even with "Remove animations" on.
 */
@Composable
private fun WeatherPrecipitationOverlay(kind: WeatherKind, modifier: Modifier = Modifier) {
    if (kind != WeatherKind.RAIN && kind != WeatherKind.THUNDERSTORM && kind != WeatherKind.SNOW) return
    val isSnow = kind == WeatherKind.SNOW

    val particles = remember(kind) {
        val rnd = Random(if (isSnow) 41 else 17)
        val count = if (isSnow) 22 else 30
        List(count) {
            PrecipParticle(
                x = rnd.nextFloat(),
                phase = rnd.nextFloat(),
                speed = 0.75f + rnd.nextFloat() * 0.5f,
                scale = 0.6f + rnd.nextFloat() * 0.8f
            )
        }
    }

    var elapsedMs by remember(kind) { mutableFloatStateOf(0f) }
    LaunchedEffect(kind) {
        var startNanos = -1L
        while (isActive) {
            withFrameNanos { now ->
                if (startNanos < 0L) startNanos = now
                elapsedMs = (now - startNanos) / 1_000_000f
            }
        }
    }

    val fallPeriodMs = if (isSnow) 4200f else 850f
    Canvas(modifier) {
        particles.forEach { p ->
            val t = ((elapsedMs / fallPeriodMs) * p.speed + p.phase) % 1f
            val y = size.height * t
            val x = size.width * p.x
            if (isSnow) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f),
                    radius = 2.2.dp.toPx() * p.scale,
                    center = Offset(x, y)
                )
            } else {
                val len = size.height * 0.11f * p.scale
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(x, y),
                    end = Offset(x - len * 0.3f, y + len),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private data class PrecipParticle(val x: Float, val phase: Float, val speed: Float, val scale: Float)

private fun isNightIcon(icon: String?) = icon?.endsWith("n") == true

/** Background gradient for each weather mood — e.g. warm orange/blue for sun, slate blue for rain. */
private fun weatherGradient(kind: WeatherKind, isNight: Boolean): Brush {
    val colors = when (kind) {
        WeatherKind.CLEAR -> if (isNight) listOf(Color(0xFF0F2027), Color(0xFF2C5364))
        else listOf(Color(0xFFFF9A44), Color(0xFF2C86D9))
        WeatherKind.CLOUDS -> if (isNight) listOf(Color(0xFF3A3F4C), Color(0xFF636B7E))
        else listOf(Color(0xFF7C8896), Color(0xFFB3BFCC))
        WeatherKind.RAIN -> listOf(Color(0xFF33475B), Color(0xFF5A7189))
        WeatherKind.THUNDERSTORM -> listOf(Color(0xFF232541), Color(0xFF4B2E52))
        WeatherKind.SNOW -> listOf(Color(0xFF7F9DC0), Color(0xFFD9E7F5))
        WeatherKind.MIST -> listOf(Color(0xFF8996A2), Color(0xFFC3CCD4))
        WeatherKind.UNKNOWN -> listOf(Color(0xFF4A3FCF), Color(0xFF7C6EF2))
    }
    return Brush.linearGradient(colors)
}

/** Draws a simple sun/moon/clouds/rain/snow/lightning/mist motif in the card's upper-right area. */
@Composable
private fun WeatherDecoration(kind: WeatherKind, isNight: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (kind) {
            WeatherKind.CLEAR -> if (isNight) drawMoonAndStars() else drawSun()
            WeatherKind.CLOUDS -> drawClouds()
            WeatherKind.RAIN -> { drawClouds(alpha = 0.85f); drawRain() }
            WeatherKind.THUNDERSTORM -> { drawClouds(alpha = 0.9f); drawLightningBolt() }
            WeatherKind.SNOW -> { drawClouds(alpha = 0.75f); drawSnow() }
            WeatherKind.MIST -> drawMistLines()
            WeatherKind.UNKNOWN -> {}
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSun() {
    val center = Offset(size.width * 0.83f, size.height * 0.24f)
    val radius = size.minDimension * 0.13f
    drawCircle(color = Color.White.copy(alpha = 0.9f), radius = radius, center = center)
    val rayColor = Color.White.copy(alpha = 0.55f)
    for (i in 0 until 8) {
        val angle = Math.toRadians((i * 45).toDouble())
        val inner = radius + 6.dp.toPx()
        val outer = radius + 16.dp.toPx()
        drawLine(
            color = rayColor,
            start = Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
            end = Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMoonAndStars() {
    val center = Offset(size.width * 0.83f, size.height * 0.24f)
    val radius = size.minDimension * 0.12f
    drawCircle(color = Color.White.copy(alpha = 0.9f), radius = radius, center = center)
    // A crescent: an offset circle in the card's own dark tone "erases" part of the moon.
    drawCircle(
        color = Color(0xFF0F2027).copy(alpha = 0.9f),
        radius = radius * 0.85f,
        center = Offset(center.x + radius * 0.45f, center.y - radius * 0.25f)
    )
    val starPositions = listOf(
        Offset(size.width * 0.62f, size.height * 0.18f),
        Offset(size.width * 0.70f, size.height * 0.42f),
        Offset(size.width * 0.92f, size.height * 0.15f),
        Offset(size.width * 0.55f, size.height * 0.55f)
    )
    starPositions.forEach { p ->
        drawCircle(color = Color.White.copy(alpha = 0.7f), radius = 2.5.dp.toPx(), center = p)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClouds(alpha: Float = 0.8f) {
    val cloudColor = Color.White.copy(alpha = alpha * 0.55f)
    fun puff(cx: Float, cy: Float, r: Float) = drawCircle(color = cloudColor, radius = r, center = Offset(size.width * cx, size.height * cy))
    puff(0.72f, 0.28f, size.minDimension * 0.11f)
    puff(0.82f, 0.24f, size.minDimension * 0.14f)
    puff(0.91f, 0.30f, size.minDimension * 0.10f)
    puff(0.80f, 0.34f, size.minDimension * 0.12f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRain() {
    val dropColor = Color.White.copy(alpha = 0.55f)
    val rnd = Random(7)
    repeat(14) {
        val x = size.width * (0.62f + rnd.nextFloat() * 0.36f)
        val yStart = size.height * (0.42f + rnd.nextFloat() * 0.15f)
        val len = size.height * 0.14f
        drawLine(
            color = dropColor,
            start = Offset(x, yStart),
            end = Offset(x - len * 0.35f, yStart + len),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * A subtle, slow-drifting snow overlay shown on the weather hero card during
 * winter months (Dec/Jan/Feb) - a small seasonal touch independent of the
 * *actual* forecast condition, so even a clear winter day gets a hint of the
 * season. Skipped when today's real weather is already snow, so it doesn't
 * double up with [drawSnow]'s condition-driven flakes.
 */
@Composable
private fun SeasonalWinterAccent(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "seasonalSnow")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(11000, easing = LinearEasing)),
        label = "seasonalSnowFall"
    )
    val flakes = remember { List(9) { Pair(Random(it * 31 + 7).nextFloat(), Random(it * 17 + 3).nextFloat()) } }
    Canvas(modifier) {
        flakes.forEach { (fx, fy0) ->
            val y = (fy0 + progress) % 1f
            drawCircle(
                color = Color.White.copy(alpha = 0.32f),
                radius = 1.6.dp.toPx(),
                center = Offset(size.width * fx, size.height * y)
            )
        }
    }
}

/** Winter by calendar month (Northern-hemisphere Dec/Jan/Feb) - a simple, low-effort seasonal cue rather than full hemisphere-aware season detection. */
private fun isWinterMonth(): Boolean {
    val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
    return month == java.util.Calendar.DECEMBER || month == java.util.Calendar.JANUARY || month == java.util.Calendar.FEBRUARY
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnow() {
    val flakeColor = Color.White.copy(alpha = 0.85f)
    val rnd = Random(11)
    repeat(16) {
        val x = size.width * (0.60f + rnd.nextFloat() * 0.38f)
        val y = size.height * (0.42f + rnd.nextFloat() * 0.45f)
        drawCircle(color = flakeColor, radius = 2.dp.toPx(), center = Offset(x, y))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLightningBolt() {
    val boltColor = Color(0xFFFFE066)
    val ox = size.width * 0.80f
    val oy = size.height * 0.42f
    val w = size.minDimension * 0.10f
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(ox + w * 0.5f, oy)
        lineTo(ox, oy + w * 1.1f)
        lineTo(ox + w * 0.32f, oy + w * 1.1f)
        lineTo(ox - w * 0.15f, oy + w * 2.2f)
        lineTo(ox + w * 0.75f, oy + w * 0.95f)
        lineTo(ox + w * 0.35f, oy + w * 0.95f)
        close()
    }
    drawPath(path, color = boltColor.copy(alpha = 0.9f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMistLines() {
    val lineColor = Color.White.copy(alpha = 0.45f)
    val ys = listOf(0.30f, 0.42f, 0.54f, 0.66f)
    ys.forEachIndexed { i, fy ->
        val startX = size.width * (0.55f + (i % 2) * 0.04f)
        drawLine(
            color = lineColor,
            start = Offset(startX, size.height * fy),
            end = Offset(size.width * 0.96f, size.height * fy),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BitcoinCard(viewModel: DashboardViewModel, report: DigestReport?, isRefreshingMarkets: Boolean, modifier: Modifier = Modifier) {
    val b = report?.bitcoin
    var showDetails by remember { mutableStateOf(false) }
    var showChart by remember { mutableStateOf(false) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var sparkline by remember { mutableStateOf<List<ChartPoint>>(emptyList()) }

    LaunchedEffect(showDetails) {
        if (showDetails && b?.available == true && sparkline.isEmpty()) {
            viewModel.loadBitcoinSparkline { sparkline = it }
        }
    }

    InfoCard(
        modifier,
        "₿ Bitcoin",
        MaterialTheme.colorScheme.surfaceContainerHigh,
        backgroundImage = R.drawable.bg_bitcoin,
        onClick = { if (b != null && b.available) showDetails = true else viewModel.refreshMarketsOnly() },
        onAlertClick = { showAlertDialog = true }
    ) {
        if (b != null && b.available) {
            Text("€${"%,.0f".format(b.eur ?: 0.0)}", style = NumericStyles.mediumValue, color = MaterialTheme.colorScheme.onSurface)
            val change = b.change24hPercent ?: 0.0
            val changeColor = if (change >= 0) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.error
            Text(
                "${if (change >= 0) "▲" else "▼"} ${"%.2f".format(kotlin.math.abs(change))}%",
                color = changeColor,
                style = MaterialTheme.typography.labelMedium
            )
            b.updatedAtMillis?.let { updatedAt ->
                Text(
                    "Updated ${SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("Tap for details & chart", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } else if (isRefreshingMarkets) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Unavailable", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tap to retry", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (showDetails && b != null && b.available) {
        MarketDetailsDialog(
            title = "₿ Bitcoin",
            headline = "€${"%,.2f".format(b.eur ?: 0.0)}",
            changeTodayPercent = b.changeTodayPercent,
            updatedAtMillis = b.updatedAtMillis,
            extraInfo = buildList {
                b.usd?.let { add("USD: $${"%,.2f".format(it)}") }
                b.nok?.let { add("NOK: ${"%,.2f".format(it)}") }
                add("Today's change: ${b.changeTodayPercent?.let { "%+.2f%%".format(it) } ?: "not available"}")
                add("Previous 24h change: ${b.change24hPercent?.let { "%+.2f%%".format(it) } ?: "not available"}")
                add("Live market value, refreshed when you manually refresh the dashboard.")
            },
            sparkline = sparkline,
            onDismiss = { showDetails = false },
            onFullChart = { showDetails = false; showChart = true }
        )
    }

    if (showChart) {
        HistoryChartDialog(
            title = "₿ Bitcoin — last 7 days (EUR)",
            accentColor = Color(0xFFF7931A),
            valueFormatter = { "€${"%,.0f".format(it)}" },
            onDismiss = { showChart = false },
            loadPoints = { onResult -> viewModel.loadBitcoinHistory(onResult = onResult) }
        )
    }

    if (showAlertDialog) {
        PriceAlertQuickDialog(
            assetLabel = "₿ Bitcoin",
            unitPrefix = "€",
            readRule = { it.bitcoin },
            writeRule = { rules, rule -> rules.copy(bitcoin = rule) },
            onDismiss = { showAlertDialog = false }
        )
    }
}

@Composable
private fun MaxPoliceReportCard(
    municipality: String,
    incidents: List<PoliceReportFetcher.Incident>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onDismissIncident: (String) -> Unit,
    onOpenFullReport: () -> Unit
) {
    var selected by remember { mutableStateOf<PoliceReportFetcher.Incident?>(null) }
    val accent = Color(MascotCharacter.MAX.accentColorArgb)
    val title = if (municipality.isNotBlank()) "$municipality Police Report" else "Nearby Police Report"

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onOpenFullReport),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedMascotIllustration(MascotCharacter.MAX, modifier = Modifier.size(32.dp), size = 32.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
                        Text("Max · Public incidents · English", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onRefresh, enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, contentDescription = "Refresh police incidents")
                }
            }
            incidents.take(5).forEach { incident ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { selected = incident }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${incident.categoryEn}${incident.area.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}", fontWeight = FontWeight.SemiBold)
                        Text(incident.englishText, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(formatIncidentTime(incident.createdMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onDismissIncident(incident.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss incident")
                    }
                }
            }
            TextButton(onClick = onOpenFullReport, contentPadding = PaddingValues(0.dp)) {
                Text(
                    if (incidents.size > 5) "View full report from Max · ${incidents.size} incidents" else "View full report from Max",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            val uriHandler = LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri("https://www.politiet.no/politiloggen") }, contentPadding = PaddingValues(0.dp)) {
                Text("Source: Norwegian Police · Politiloggen", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    selected?.let { incident ->
        val dialogUriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${incident.categoryEn}${incident.area.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(incident.englishText)
                    Text("${incident.municipality} · ${formatIncidentTime(incident.createdMillis)}", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = { runCatching { dialogUriHandler.openUri(incident.sourceUrl) } }) { Text("Open original report") }
            }
        )
    }
}

private fun formatIncidentTime(millis: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.ENGLISH).format(Date(millis))

@Composable
private fun MarketDetailsDialog(
    title: String,
    headline: String,
    changeTodayPercent: Double?,
    updatedAtMillis: Long?,
    extraInfo: List<String>,
    sparkline: List<ChartPoint>,
    onDismiss: () -> Unit,
    onFullChart: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(headline, style = NumericStyles.heroValue)
                changeTodayPercent?.let {
                    val positive = it >= 0
                    Text(
                        "${if (positive) "▲" else "▼"} ${"%.2f".format(kotlin.math.abs(it))}% today",
                        color = if (positive) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (sparkline.size >= 2) {
                    Sparkline(
                        sparkline,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                    )
                }
                Text(
                    "Updated ${formatDataAge(updatedAtMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                extraInfo.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFullChart) { Text("View full 2-week chart") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun formatDataAge(updatedAtMillis: Long?): String {
    if (updatedAtMillis == null) return "unknown"
    val age = (System.currentTimeMillis() - updatedAtMillis).coerceAtLeast(0L)
    val minutes = age / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ${minutes % 60}m ago"
        else -> "${minutes / (24 * 60)}d ago"
    } + " · " + SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(updatedAtMillis))
}

@Composable
private fun CurrencyCard(viewModel: DashboardViewModel, report: DigestReport?, isRefreshingMarkets: Boolean, modifier: Modifier = Modifier) {
    val c = report?.currency
    var showDetails by remember { mutableStateOf(false) }
    var showChart by remember { mutableStateOf(false) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var sparkline by remember { mutableStateOf<List<ChartPoint>>(emptyList()) }

    LaunchedEffect(showDetails) {
        if (showDetails && c?.available == true && sparkline.isEmpty()) {
            viewModel.loadCurrencySparkline { sparkline = it }
        }
    }

    InfoCard(
        modifier,
        "💱 ${c?.baseCurrency ?: "EUR"} → ${c?.targetCurrency ?: "NOK"}",
        MaterialTheme.colorScheme.surfaceContainerHigh,
        backgroundImage = R.drawable.bg_money,
        onClick = { if (c != null && c.available) showDetails = true else viewModel.refreshMarketsOnly() },
        onAlertClick = { showAlertDialog = true }
    ) {
        if (c != null && c.available) {
            Text("${"%.2f".format(c.rate ?: 0.0)} ${c.targetCurrency}", style = NumericStyles.mediumValue, color = MaterialTheme.colorScheme.onSurface)
            val change = c.change24hPercent ?: 0.0
            val changeColor = if (change >= 0) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.error
            Text(
                "${if (change >= 0) "▲" else "▼"} ${"%.2f".format(kotlin.math.abs(change))}%",
                color = changeColor,
                style = MaterialTheme.typography.labelMedium
            )
            c.updatedAtMillis?.let { updatedAt ->
                Text(
                    "Updated ${SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("Tap for details & chart", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } else if (isRefreshingMarkets) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Unavailable", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tap to retry", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (showDetails && c != null && c.available) {
        MarketDetailsDialog(
            title = "💱 ${c.baseCurrency} → ${c.targetCurrency}",
            headline = "${"%.4f".format(c.rate ?: 0.0)} ${c.targetCurrency}",
            changeTodayPercent = c.changeTodayPercent,
            updatedAtMillis = c.updatedAtMillis,
            extraInfo = buildList {
                add("${c.baseCurrency}: 1 ${c.baseCurrency} = ${"%.4f".format(c.rate ?: 0.0)} ${c.targetCurrency}")
                val inverse = (c.rate ?: 0.0).takeIf { it != 0.0 }?.let { 1.0 / it }
                add("${c.targetCurrency}: 1 ${c.targetCurrency} = ${inverse?.let { "%.4f".format(it) } ?: "—"} ${c.baseCurrency}")
                add("Today's change: ${c.changeTodayPercent?.let { "%+.2f%%".format(it) } ?: "not available"}")
                add("Previous 24h change: ${c.change24hPercent?.let { "%+.2f%%".format(it) } ?: "not available"}")
                if (c.consensusSources.isNotEmpty()) {
                    add("Sources: ${c.consensusSources.joinToString(", ")}")
                }
                c.sourceSpreadPercent?.let { add("Spread across sources: ±${"%.3f".format(it)}%") }
                add("Live exchange rate, refreshed when you manually refresh the dashboard.")
            },
            sparkline = sparkline,
            onDismiss = { showDetails = false },
            onFullChart = { showDetails = false; showChart = true }
        )
    }

    if (showChart) {
        HistoryChartDialog(
            title = "💱 ${c?.baseCurrency ?: "EUR"} → ${c?.targetCurrency ?: "NOK"} — last 2 weeks",
            accentColor = Color(0xFF1F9D55),
            valueFormatter = { "%.4f".format(it) },
            onDismiss = { showChart = false },
            loadPoints = { onResult -> viewModel.loadCurrencyHistory(onResult = onResult) }
        )
    }

    if (showAlertDialog) {
        PriceAlertQuickDialog(
            assetLabel = "💱 ${c?.baseCurrency ?: "EUR"} → ${c?.targetCurrency ?: "NOK"}",
            unitPrefix = "",
            readRule = { it.mainCurrency },
            writeRule = { rules, rule -> rules.copy(mainCurrency = rule) },
            onDismiss = { showAlertDialog = false }
        )
    }
}

/**
 * One extra currency pair the user added in Settings > Currency Pair,
 * rendered with the exact same size/style/behavior as the main Bitcoin and
 * Currency cards - tap for a 2-week history chart, bell icon for its own
 * price alert. Only used for pure currency-to-currency pairs; anything
 * involving crypto still shows in the compact watchlist chip row, since
 * chart/alert support there would need a different data source.
 */
@Composable
private fun ExtraCurrencyPairCard(viewModel: DashboardViewModel, entry: com.morningdigest.app.data.model.WatchlistEntry, modifier: Modifier = Modifier) {
    val parts = entry.label.split(" → ")
    val base = parts.getOrNull(0) ?: ""
    val target = parts.getOrNull(1) ?: entry.id
    val pairKey = "$base/$target"

    var showChart by remember { mutableStateOf(false) }
    var showAlertDialog by remember { mutableStateOf(false) }

    InfoCard(
        modifier,
        "💱 $base → $target",
        MaterialTheme.colorScheme.surfaceContainerHigh,
        backgroundImage = R.drawable.bg_money,
        onClick = { if (entry.available) showChart = true },
        onAlertClick = { showAlertDialog = true }
    ) {
        if (entry.available && entry.value != null) {
            Text("${"%.4f".format(entry.value)}", style = NumericStyles.mediumValue, color = MaterialTheme.colorScheme.onSurface)
            Text("1 $base", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tap for chart", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Unavailable", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showChart) {
        HistoryChartDialog(
            title = "💱 $base → $target — last 2 weeks",
            accentColor = Color(0xFF1F9D55),
            valueFormatter = { "%.4f".format(it) },
            onDismiss = { showChart = false },
            loadPoints = { onResult -> viewModel.loadPairHistory(base, target, days = 14, onResult = onResult) }
        )
    }

    if (showAlertDialog) {
        PriceAlertQuickDialog(
            assetLabel = "💱 $base → $target",
            unitPrefix = "",
            readRule = { it.extraPairs[pairKey] ?: PairAlertRule() },
            writeRule = { rules, rule -> rules.copy(extraPairs = rules.extraPairs + (pairKey to rule)) },
            onDismiss = { showAlertDialog = false }
        )
    }
}

/**
 * The user's extra crypto pairs (Settings > Watchlist) that involve crypto
 * on at least one side, shown as a horizontally scrollable row of small
 * chips beneath the primary Bitcoin and Currency cards - kept separate
 * since this list is always the user's own arbitrary pick, of any length.
 */
@Composable
private fun WatchlistRow(entries: List<com.morningdigest.app.data.model.WatchlistEntry>) {
    if (entries.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        entries.forEach { entry -> WatchlistChip(entry) }
    }
}

@Composable
private fun WatchlistChip(entry: com.morningdigest.app.data.model.WatchlistEntry) {
    val ext = MaterialTheme.extendedColors
    val accent = if (entry.isCrypto) ext.warning else ext.success
    Column(
        Modifier
            .width(120.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(5.dp))
            Text(entry.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        if (entry.available && entry.value != null) {
            Text(
                formatWatchlistRate(entry.value),
                style = NumericStyles.mediumValue,
                color = MaterialTheme.colorScheme.onSurface
            )
            entry.change24hPercent?.let { change ->
                Text(
                    "${if (change >= 0) "+" else "−"}${"%.2f".format(kotlin.math.abs(change))}% today",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (change >= 0) ext.success else MaterialTheme.colorScheme.error
                )
            }
        } else {
            Text("Unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Formats a "1 From = X To" rate with however many decimals it needs to
 * actually be readable - a crypto-to-fiat rate might be in the tens of
 * thousands (BTC->JPY) while a fiat-to-crypto rate might be a tiny fraction
 * (USD->BTC), so a single fixed decimal count would either look like "0.0000"
 * or lose all its precision depending on the pair.
 */
private fun formatWatchlistRate(value: Double): String = when {
    value == 0.0 -> "0"
    kotlin.math.abs(value) >= 1000 -> "%,.2f".format(value)
    kotlin.math.abs(value) >= 1 -> "%.4f".format(value)
    else -> "%.6f".format(value)
}

/**
 * A small info tile with a faint decorative watermark image in the
 * background (e.g. a bitcoin coin, a banknote) that sits behind the content
 * without interfering with readability, and an optional tap target.
 */
/**
 * A tiny inline trend line (no axes/labels) for embedding directly on a
 * card face - e.g. Bitcoin's last 24h or a currency pair's last 7 days -
 * as a quick-glance shape, distinct from the full tap-to-open chart dialog.
 */
@Composable
private fun Sparkline(points: List<ChartPoint>, color: Color, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val values = points.map { it.value }
    val minV = values.min()
    val maxV = values.max()
    val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0

    Canvas(modifier) {
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { index, point ->
            val x = size.width * (index.toFloat() / (points.size - 1))
            val y = size.height * (1f - ((point.value - minV) / range).toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier,
    title: String,
    bg: Color,
    backgroundImage: Int? = null,
    onClick: (() -> Unit)? = null,
    onAlertClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(bg)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        if (backgroundImage != null) {
            Image(
                painter = painterResource(id = backgroundImage),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(96.dp)
                    .alpha(0.16f),
                contentScale = ContentScale.Fit
            )
        }
        if (onAlertClick != null) {
            IconButton(
                onClick = onAlertClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Filled.NotificationsNone,
                    contentDescription = "Set a price alert",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(Modifier.padding(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Simple dialog that lazily loads and renders a history line chart for Bitcoin or EUR->NOK. */
@Composable
private fun HistoryChartDialog(
    title: String,
    accentColor: Color,
    valueFormatter: (Double) -> String,
    onDismiss: () -> Unit,
    loadPoints: ((List<ChartPoint>) -> Unit) -> Unit
) {
    var points by remember { mutableStateOf<List<ChartPoint>?>(null) }

    LaunchedEffect(Unit) {
        loadPoints { result -> points = result }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            val current = points
            when {
                current == null -> Box(
                    Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                current.isEmpty() -> Box(
                    Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Chart unavailable", color = Color.Gray) }

                else -> Column {
                    LineChart(
                        points = current,
                        color = accentColor,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Low ${valueFormatter(current.minOf { it.value })}",
                            style = MaterialTheme.typography.labelSmall, color = Color.Gray
                        )
                        Text(
                            "High ${valueFormatter(current.maxOf { it.value })}",
                            style = MaterialTheme.typography.labelSmall, color = Color.Gray
                        )
                    }
                }
            }
        }
    )
}

/**
 * Quick "set a price alert right from this card" dialog - the same alert
 * rules Settings > Price Alerts used to expose, just scoped to one asset at
 * a time and reachable with a single tap on the bell icon. Works for
 * Bitcoin, the main currency pair, or any extra pair via [readRule]/
 * [writeRule], so every asset on the dashboard gets the exact same
 * capability. Saves through the same repository/WorkManager path Settings
 * used to, so nothing about how it's stored or checked in the background changed.
 */
@Composable
private fun PriceAlertQuickDialog(
    assetLabel: String,
    unitPrefix: String,
    readRule: (PriceAlertRules) -> PairAlertRule,
    writeRule: (PriceAlertRules, PairAlertRule) -> PriceAlertRules,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val container = (context.applicationContext as MorningDigestApp).container
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(PriceAlertRules()) }
    var aboveEnabled by remember { mutableStateOf(false) }
    var aboveText by remember { mutableStateOf("") }
    var belowEnabled by remember { mutableStateOf(false) }
    var belowText by remember { mutableStateOf("") }

    fun Double.trimmed(): String =
        if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()

    LaunchedEffect(Unit) {
        val settings = container.settingsRepository.currentSettings()
        rules = settings.priceAlertRules
        val current = readRule(rules)
        aboveEnabled = current.aboveEnabled
        aboveText = current.aboveValue.trimmed()
        belowEnabled = current.belowEnabled
        belowText = current.belowValue.trimmed()
        loaded = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alert me — $assetLabel") },
        text = {
            if (!loaded) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Column {
                    Text(
                        "Get a phone notification the moment this crosses a price you set.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notify when above", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = aboveEnabled, onCheckedChange = { aboveEnabled = it })
                    }
                    if (aboveEnabled) {
                        OutlinedTextField(
                            value = aboveText, onValueChange = { aboveText = it },
                            label = { Text(if (unitPrefix.isNotEmpty()) "$unitPrefix above" else "Above") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notify when below", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = belowEnabled, onCheckedChange = { belowEnabled = it })
                    }
                    if (belowEnabled) {
                        OutlinedTextField(
                            value = belowText, onValueChange = { belowText = it },
                            label = { Text(if (unitPrefix.isNotEmpty()) "$unitPrefix below" else "Below") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val current = readRule(rules)
                val updatedRule = current.copy(
                    aboveEnabled = aboveEnabled,
                    aboveValue = aboveText.toDoubleOrNull() ?: current.aboveValue,
                    belowEnabled = belowEnabled,
                    belowValue = belowText.toDoubleOrNull() ?: current.belowValue
                )
                val updated = writeRule(rules, updatedRule).let {
                    it.copy(enabled = it.enabled || aboveEnabled || belowEnabled)
                }
                scope.launch {
                    container.settingsRepository.updatePriceAlertRules(updated)
                    WorkScheduler.applyPriceAlertCheckSchedule(context, updated.enabled)
                }
                onDismiss()
            }, enabled = loaded) { Text("Save Alert") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Minimal dependency-free line chart drawn straight onto a Canvas - no charting library needed. */
@Composable
private fun LineChart(points: List<ChartPoint>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val minV = points.minOf { it.value }
        val maxV = points.maxOf { it.value }
        val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val minT = points.first().timestampMillis.toDouble()
        val maxT = points.last().timestampMillis.toDouble()
        val tRange = (maxT - minT).takeIf { it > 0.0 } ?: 1.0

        fun xOf(t: Long) = (((t - minT) / tRange) * size.width).toFloat()
        fun yOf(v: Double) = (size.height - ((v - minV) / range) * size.height).toFloat()

        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { index, p ->
            val x = xOf(p.timestampMillis)
            val y = yOf(p.value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 5f))

        // Filled area under the line for a lighter "sparkline" feel.
        val fillPath = androidx.compose.ui.graphics.Path().apply {
            addPath(path)
            lineTo(xOf(points.last().timestampMillis), size.height)
            lineTo(xOf(points.first().timestampMillis), size.height)
            close()
        }
        drawPath(fillPath, color = color.copy(alpha = 0.12f))
    }
}

@Composable
private fun TomorrowCard(report: DigestReport?) {
    val t = report?.weatherTomorrow
    val upcoming = report?.upcomingDays.orEmpty()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var showExtended by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = t != null && t.available) { showExtended = true }
            .padding(Spacing.md)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌦 Tomorrow", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        if (t != null && t.available) {
            Text(
                "${t.avgTemp?.roundToInt() ?: "—"}° avg · ${t.description?.replaceFirstChar { it.uppercase() } ?: ""}",
                style = MaterialTheme.typography.bodyLarge
            )
            if (t.minTemp != null && t.maxTemp != null) {
                Text(
                    "${t.minTemp.roundToInt()}° – ${t.maxTemp.roundToInt()}°",
                    style = MaterialTheme.typography.bodyMedium, color = muted
                )
            }
            Text("☔ ${t.rainChancePercent ?: 0}% chance of rain", style = MaterialTheme.typography.bodyMedium, color = muted)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                t.humidity?.let { Text("💧 $it% humidity", style = MaterialTheme.typography.labelMedium, color = muted) }
                t.windSpeed?.let { Text("💨 ${"%.1f".format(it)} m/s wind", style = MaterialTheme.typography.labelMedium, color = muted) }
            }
            if (t.parts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    t.parts.forEach { part ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(part.label, style = MaterialTheme.typography.labelSmall, color = muted)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${part.temp?.roundToInt() ?: "—"}°",
                                style = NumericStyles.mediumValue
                            )
                            part.description?.let {
                                Text(
                                    it.replaceFirstChar { c -> c.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (upcoming.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(10.dp))
                Text("📅 Next 3 Days", style = MaterialTheme.typography.labelMedium, color = muted)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // "Tomorrow" itself is the first column, reusing t's own high/low
                    // so the strip reads as one continuous 3-day outlook.
                    UpcomingDayColumn(
                        label = "Tomorrow",
                        icon = t.icon,
                        description = t.description,
                        minTemp = t.minTemp,
                        maxTemp = t.maxTemp,
                        modifier = Modifier.weight(1f)
                    )
                    upcoming.take(2).forEach { day ->
                        UpcomingDayColumn(
                            label = day.dayLabel,
                            icon = day.icon,
                            description = day.description,
                            minTemp = day.minTemp,
                            maxTemp = day.maxTemp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            Text("Unavailable", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }

    if (showExtended && t != null && t.available) {
        ExtendedForecastDialog(tomorrow = t, upcoming = upcoming, onDismiss = { showExtended = false })
    }
}

/**
 * Full-screen day-by-day forecast, opened by tapping the Tomorrow card.
 * Styled as a scrollable grid of colorful day rows (icon, condition,
 * high/low, rain chance) similar to a typical extended-forecast page.
 *
 * Note: the free forecast API this app uses only covers a handful of days
 * ahead (tomorrow + whatever [upcoming] contains) - genuine day-by-day
 * accuracy doesn't exist a full month out, so this shows every real day the
 * API returned rather than inventing numbers for the rest of the month.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtendedForecastDialog(
    tomorrow: WeatherTomorrow,
    upcoming: List<WeatherDayForecast>,
    onDismiss: () -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val allDays = buildList {
        add(
            WeatherDayForecast(
                dayLabel = "Tomorrow",
                minTemp = tomorrow.minTemp,
                maxTemp = tomorrow.maxTemp,
                description = tomorrow.description,
                icon = tomorrow.icon,
                rainChancePercent = tomorrow.rainChancePercent,
                dateLabel = null,
                humidity = tomorrow.humidity,
                windSpeed = tomorrow.windSpeed
            )
        )
        addAll(upcoming)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Weather Outlook") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                )
                Text(
                    "${allDays.size}-day forecast · longer-range days aren't reliably predictable yet, so only the days the forecast provider actually covers are shown",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = 6.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allDays) { day ->
                        ExtendedForecastRow(day)
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

/** One colorful row in the extended forecast grid: date, icon, condition, high/low, and rain/wind detail. */
@Composable
private fun ExtendedForecastRow(day: WeatherDayForecast) {
    val kind = weatherKindFor(day.icon, day.description)
    val muted = Color.White.copy(alpha = 0.85f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(weatherGradient(kind, isNight = false))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(72.dp)) {
            Text(day.dayLabel, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            day.dateLabel?.let { Text(it, color = muted, style = MaterialTheme.typography.labelSmall) }
        }
        Text(weatherEmoji(day.icon, day.description), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            Text(
                day.description?.replaceFirstChar { it.uppercase() } ?: "—",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("☔ ${day.rainChancePercent ?: 0}%", color = muted, style = MaterialTheme.typography.labelSmall)
                day.windSpeed?.let { Text("💨 ${"%.1f".format(it)} m/s", color = muted, style = MaterialTheme.typography.labelSmall) }
                day.humidity?.let { Text("💧 $it%", color = muted, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${day.maxTemp?.roundToInt() ?: "—"}°", color = Color.White, style = NumericStyles.mediumValue, fontWeight = FontWeight.Bold)
            Text("${day.minTemp?.roundToInt() ?: "—"}°", color = muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** One compact day column (weekday, emoji, high/low) used in the Next 3 Days strip. */
@Composable
private fun UpcomingDayColumn(
    label: String,
    icon: String?,
    description: String?,
    minTemp: Double?,
    maxTemp: Double?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(vertical = Spacing.xs, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(weatherEmoji(icon, description), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "${maxTemp?.roundToInt() ?: "—"}° / ${minTemp?.roundToInt() ?: "—"}°",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Small emoji standing in for a weather icon/description, used in the compact Next 3 Days strip. */
private fun weatherEmoji(icon: String?, description: String?): String =
    when (weatherKindFor(icon, description)) {
        WeatherKind.CLEAR -> if (isNightIcon(icon)) "🌙" else "☀️"
        WeatherKind.CLOUDS -> "☁️"
        WeatherKind.RAIN -> "🌧️"
        WeatherKind.THUNDERSTORM -> "⛈️"
        WeatherKind.SNOW -> "❄️"
        WeatherKind.MIST -> "🌫️"
        WeatherKind.UNKNOWN -> "🌡️"
    }

/** "Fact of the day" - one short paragraph, re-picked on every refresh. Sits just above the news feed. */
@Composable
private fun DailyFactCard(report: DigestReport?) {
    val fact = report?.dailyFact
    if (fact == null || fact.text.isBlank()) return

    // Collapsed by default beyond the header, so the fact text only takes up
    // space once the user actually wants to read it.
    var expanded by remember(fact.text) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "factChevron"
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { expanded = !expanded }
            .padding(Spacing.md)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text("💡 Fact of the Day", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(fact.category, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).graphicsLayer(rotationZ = chevronRotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200)) + expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(160)) + shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing))
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                Text(fact.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * "New video" bubbles for subscribed YouTube channels, shown right under
 * Fact of the Day - styled after the Assistants list (colored tint,
 * circular avatar, rounded card) since it's the same "little bubble" visual
 * language. Each video is its own row with its own dismiss, so if a channel
 * posted two videos, both show stacked one under another.
 */
@Composable
private fun YoutubeUpdatesSection(
    updates: List<com.morningdigest.app.data.model.YoutubeVideoUpdate>,
    onDismiss: (channelId: String, videoId: String) -> Unit,
    onClearAll: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    // Never let this section grow unbounded - show at most the 30 most
    // recent updates even if more channels posted more than that.
    val visible = updates.take(30)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📺 New videos",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onClearAll) {
                Text("Clear all")
            }
        }
        visible.forEach { update ->
            val accent = youtubeChannelAccent(update.channelId)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.18f), MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    )
                    .clickable {
                        onDismiss(update.channelId, update.videoId)
                        runCatching { uriHandler.openUri(update.videoLink) }
                    }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (update.avatarUrl.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = update.avatarUrl,
                            contentDescription = update.channelName,
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text("▶", color = accent, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        update.channelName,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        update.videoTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (update.publishedMillis > 0L) {
                        Text(
                            formatIncidentTime(update.publishedMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = { onDismiss(update.channelId, update.videoId) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** A stable, muted-but-colorful accent per channel, derived from its ID so it's consistent every time without needing to store a color. */
private fun youtubeChannelAccent(channelId: String): Color {
    val palette = listOf(
        Color(0xFFE53935), // red (YouTube-ish, but muted via alpha when used)
        Color(0xFF3B5A9A), // blue
        Color(0xFF2ECC71), // green
        Color(0xFFCC6B1E), // orange
        Color(0xFF7B4FC9), // purple
        Color(0xFFB8860B)  // gold
    )
    val index = (channelId.hashCode().let { if (it < 0) -it else it }) % palette.size
    return palette[index]
}

@Composable
private fun ActionRow(viewModel: DashboardViewModel, onScrollToTop: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { viewModel.refreshNow() },
                modifier = Modifier.weight(1f),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh Now")
                }
            }
            OutlinedButton(onClick = onScrollToTop, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back to Top")
            }
        }
        state.report?.let { r ->
            val statusText = when {
                r.notificationSent -> "✅ Last notification sent"
                r.notificationError != null -> "❌ Last notification failed: ${r.notificationError}"
                else -> "No notification sent yet"
            }
            Text(statusText, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        }
    }
}
