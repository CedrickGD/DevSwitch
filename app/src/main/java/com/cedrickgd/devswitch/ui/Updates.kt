package com.cedrickgd.devswitch.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cedrickgd.devswitch.data.UpdateInfo
import com.cedrickgd.devswitch.data.UpdateManager
import com.cedrickgd.devswitch.service.AutoInstallService
import com.cedrickgd.devswitch.service.UpdateNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    fun check(silent: Boolean = false) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        if (!silent) state = UpdateState.Checking
        scope.launch {
            runCatching { UpdateManager.checkForUpdate(context) }
                .onSuccess { info ->
                    state = when {
                        info != null -> UpdateState.Available(info)
                        silent -> UpdateState.Idle
                        else -> UpdateState.UpToDate
                    }
                    if (info != null && silent) UpdateNotifier.notifyAvailable(context, info)
                }
                .onFailure {
                    state = if (silent) UpdateState.Idle else UpdateState.Failed("Couldn't reach GitHub")
                }
        }
    }

    fun downloadAndInstall() {
        val info = (state as? UpdateState.Available)?.info ?: return
        scope.launch {
            state = UpdateState.Downloading(info, 0f)
            runCatching {
                val file = UpdateManager.downloadApk(context, info) { progress ->
                    state = UpdateState.Downloading(info, progress)
                }
                UpdateManager.installApk(context, file)
            }
                .onSuccess { state = UpdateState.Available(info) }
                .onFailure { state = UpdateState.Failed("Download failed") }
        }
    }
}

@Composable
fun rememberUpdateController(): UpdateController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember { UpdateController(context, scope) }
}

/** Prominent banner shown on the home screen when an update is available. */
@Composable
fun UpdateBanner(controller: UpdateController) {
    val state = controller.state
    if (state !is UpdateState.Available && state !is UpdateState.Downloading) return
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.RocketLaunch,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                when (state) {
                    is UpdateState.Available -> {
                        Text(
                            "Update ${state.info.versionName} available",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "Tap to download & install",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    is UpdateState.Downloading -> {
                        Text(
                            "Downloading ${state.info.versionName}…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> Unit
                }
            }
            if (state is UpdateState.Available) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = controller::downloadAndInstall) { Text("Update") }
            }
        }
    }
}

private fun isAutoInstallEnabled(context: Context): Boolean {
    val expected = ComponentName(context, AutoInstallService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}

/** Opt-in "seamless mode": auto-confirm the system update dialog via Accessibility. */
@Composable
fun SeamlessUpdatesSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isAutoInstallEnabled(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            enabled = isAutoInstallEnabled(context)
            delay(1500)
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (enabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Seamless updates", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (enabled) {
                            "On — updates install with no taps at all"
                        } else {
                            "Off — one confirm tap per update. Turn on to skip it."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Uses an accessibility helper to tap the system \"Update this app?\" dialog " +
                    "for you. It only acts while DevSwitch is installing an update it " +
                    "downloaded. On Android 13+ you may first need App info → ⋮ → " +
                    "Allow restricted settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (enabled) {
                FilledTonalButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                            )
                        }
                    },
                ) { Text("Manage") }
            } else {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                            )
                        }
                    },
                ) { Text("Turn on") }
            }
        }
    }
}

/** Full update section for the settings screen. */
@Composable
fun UpdateSection(controller: UpdateController) {
    val context = LocalContext.current
    val current = remember { UpdateManager.currentVersion(context) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Version $current", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (val s = controller.state) {
                            is UpdateState.UpToDate -> "You're on the latest version"
                            is UpdateState.Available -> "Version ${s.info.versionName} is available"
                            is UpdateState.Checking -> "Checking for updates…"
                            is UpdateState.Downloading -> "Downloading update…"
                            is UpdateState.Failed -> s.message
                            else -> "Updates are pulled from GitHub releases"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (controller.state) {
                    is UpdateState.Checking -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp,
                    )
                    is UpdateState.Available -> Button(onClick = controller::downloadAndInstall) {
                        Text("Install")
                    }
                    is UpdateState.Downloading -> Unit
                    else -> TextButton(onClick = { controller.check() }) { Text("Check") }
                }
            }
            val s = controller.state
            if (s is UpdateState.Downloading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (s is UpdateState.Available && s.info.notes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    s.info.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                )
            }
        }
    }
}
