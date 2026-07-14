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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
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
import com.cedrickgd.devswitch.data.ShizukuInstaller
import com.cedrickgd.devswitch.data.UpdateInfo
import com.cedrickgd.devswitch.data.UpdateManager
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
                // Prefer a fully silent Shizuku install; fall back to the
                // system installer (which prompts on some OEMs) otherwise.
                val installed = ShizukuInstaller.hasPermission() &&
                    ShizukuInstaller.install(context, file).isSuccess
                if (!installed) UpdateManager.installApk(context, file)
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

/** Shizuku status + setup for fully silent installs. */
@Composable
fun ShizukuSection() {
    val context = LocalContext.current
    var status by remember { mutableStateOf(ShizukuInstaller.status(context)) }

    // Re-check periodically so the card reflects Shizuku being started/authorized.
    LaunchedEffect(Unit) {
        while (true) {
            status = ShizukuInstaller.status(context)
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
                            if (status == ShizukuInstaller.Status.READY) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (status == ShizukuInstaller.Status.READY) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Silent updates (Shizuku)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (status) {
                            ShizukuInstaller.Status.READY ->
                                "Connected — updates install with no prompts"
                            ShizukuInstaller.Status.NEEDS_PERMISSION ->
                                "Running — grant DevSwitch access to enable"
                            ShizukuInstaller.Status.NOT_RUNNING ->
                                "Installed but not started — open Shizuku and start the service"
                            ShizukuInstaller.Status.NOT_INSTALLED ->
                                "Optional. Install Shizuku for one-tap silent updates on Samsung."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when (status) {
                ShizukuInstaller.Status.NEEDS_PERMISSION -> Button(
                    onClick = {
                        ShizukuInstaller.requestPermission { granted ->
                            if (granted) status = ShizukuInstaller.Status.READY
                        }
                    },
                ) { Text("Grant access") }
                ShizukuInstaller.Status.NOT_RUNNING -> FilledTonalButton(
                    onClick = {
                        runCatching {
                            context.packageManager
                                .getLaunchIntentForPackage(ShizukuInstaller.SHIZUKU_PACKAGE)
                                ?.let { context.startActivity(it) }
                        }
                    },
                ) { Text("Open Shizuku") }
                ShizukuInstaller.Status.NOT_INSTALLED -> FilledTonalButton(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(
                                "https://github.com/RikkaApps/Shizuku/releases/latest",
                            ),
                        )
                        runCatching { context.startActivity(intent) }
                    },
                ) { Text("Get Shizuku") }
                ShizukuInstaller.Status.READY -> Unit
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
