package com.cedrickgd.devswitch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import com.cedrickgd.devswitch.data.Prefs
import com.cedrickgd.devswitch.data.ThemeMode
import com.cedrickgd.devswitch.service.MonitorService
import com.cedrickgd.devswitch.ui.AppSettingsScreen
import com.cedrickgd.devswitch.ui.HomeScreen
import com.cedrickgd.devswitch.ui.OnboardingScreen
import com.cedrickgd.devswitch.ui.theme.DevSwitchTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context.applicationContext) }
    val scope = rememberCoroutineScope()

    // The monitor service dies with the process (app update, force stop);
    // revive it whenever the app opens and something is still watched.
    LaunchedEffect(Unit) {
        if (prefs.watched.first().isNotEmpty()) {
            MonitorService.ensureRunning(context)
        }
    }

    val themeMode by prefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val accent by prefs.accent.collectAsState(initial = Prefs.DEFAULT_ACCENT)
    val onboarded by prefs.onboarded.collectAsState<Boolean, Boolean?>(initial = null)

    DevSwitchTheme(themeMode = themeMode, accentId = accent) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (onboarded) {
                null -> Unit // brief flash while DataStore loads
                false -> OnboardingScreen(
                    onDone = { scope.launch { prefs.setOnboarded() } },
                )
                true -> {
                    var showSettings by rememberSaveable { mutableStateOf(false) }
                    if (showSettings) {
                        BackHandler { showSettings = false }
                        AppSettingsScreen(onBack = { showSettings = false })
                    } else {
                        HomeScreen(onOpenSettings = { showSettings = true })
                    }
                }
            }
        }
    }
}
