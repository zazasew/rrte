package com.morningdigest.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.morningdigest.app.ui.navigation.AppNavGraph
import com.morningdigest.app.ui.theme.MorningDigestTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val container = (application as MorningDigestApp).container
            val settings by container.settingsRepository.settingsFlow.collectAsState(
                initial = com.morningdigest.app.data.prefs.AppSettings()
            )
            val darkTheme = if (settings.useSystemTheme) isSystemInDarkTheme() else settings.darkMode

            MorningDigestTheme(darkTheme = darkTheme) {
                AppNavGraph()
            }
        }
    }
}
