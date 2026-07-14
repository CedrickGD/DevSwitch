package com.cedrickgd.devswitch.ui

import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cedrickgd.devswitch.data.DevSetting
import com.cedrickgd.devswitch.data.DevSettingsController
import com.cedrickgd.devswitch.data.Prefs
import com.cedrickgd.devswitch.data.SettingsRegistry
import com.cedrickgd.devswitch.service.MonitorService
import kotlin.math.abs
import kotlinx.coroutines.launch

const val GRANT_COMMAND =
    "adb shell pm grant com.cedrickgd.devswitch android.permission.WRITE_SECURE_SETTINGS"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { DevSettingsController(context) }
    val prefs = remember { Prefs(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val watched by prefs.watched.collectAsState(initial = emptySet())

    val supported = remember { SettingsRegistry.all.filter(controller::isSupported) }

    var values by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var animScale by remember { mutableStateOf(1f) }
    var hasAccess by remember { mutableStateOf(controller.hasSecureSettingsAccess()) }
    var confirmSetting by remember { mutableStateOf<DevSetting?>(null) }

    fun refresh() {
        hasAccess = controller.hasSecureSettingsAccess()
        values = supported.associate { it.key to controller.isOn(it) }
        animScale = controller.animationScale()
    }

    DisposableEffect(Unit) {
        refresh()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        supported.forEach {
            context.contentResolver.registerContentObserver(controller.uriFor(it), false, observer)
        }
        controller.animationKeys.forEach {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(it), false, observer,
            )
        }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    fun toggle(setting: DevSetting, on: Boolean) {
        val result = controller.setEnabled(setting, on)
        refresh()
        result.onFailure { error ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (error is SecurityException) {
                        "Missing access — run the ADB grant command shown above"
                    } else {
                        "Couldn't change ${setting.title}"
                    }
                )
            }
        }
    }

    fun requestToggle(setting: DevSetting, on: Boolean) {
        if (!on && setting.confirmOffMessage != null) {
            confirmSetting = setting
        } else {
            toggle(setting, on)
        }
    }

    fun toggleWatch(setting: DevSetting) {
        scope.launch {
            val newSet = prefs.toggleWatched(setting.key)
            if (newSet.isNotEmpty()) MonitorService.ensureRunning(context)
        }
    }

    fun selectAnimationScale(scale: Float) {
        val result = controller.setAnimationScale(scale)
        refresh()
        result.onFailure {
            scope.launch { snackbarHostState.showSnackbar("Couldn't change animation scale") }
        }
    }

    val updates = rememberUpdateController()
    LaunchedEffect(Unit) { updates.check(silent = true) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text("DevSwitch", fontWeight = FontWeight.SemiBold) },
                actions = {
                    AccessBadge(hasAccess)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Palette, contentDescription = "Appearance & settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (!hasAccess) {
                AccessCard()
                Spacer(Modifier.height(20.dp))
            }

            if (updates.state is UpdateState.Available || updates.state is UpdateState.Downloading) {
                UpdateBanner(updates)
                Spacer(Modifier.height(20.dp))
            }

            QuickActions()
            Spacer(Modifier.height(24.dp))

            SettingsGroup(
                label = "Debugging",
                settings = SettingsRegistry.debugging.filter { it in supported },
                values = values,
                watched = watched,
                enabled = hasAccess,
                onToggle = ::requestToggle,
                onWatch = ::toggleWatch,
            )

            SettingsGroup(
                label = "Display",
                settings = SettingsRegistry.displayInput.filter { it in supported },
                values = values,
                watched = watched,
                enabled = hasAccess,
                onToggle = ::requestToggle,
                onWatch = ::toggleWatch,
            )

            SettingsGroup(
                label = "System",
                settings = SettingsRegistry.system.filter { it in supported },
                values = values,
                watched = watched,
                enabled = hasAccess,
                onToggle = ::requestToggle,
                onWatch = ::toggleWatch,
            )

            AnimationsCard(
                animScale = animScale,
                enabled = hasAccess,
                onSelect = ::selectAnimationScale,
            )

            Spacer(Modifier.height(40.dp))
        }
    }

    confirmSetting?.let { setting ->
        AlertDialog(
            onDismissRequest = { confirmSetting = null },
            title = { Text("Turn off ${setting.title}?") },
            text = { Text(setting.confirmOffMessage.orEmpty()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        toggle(setting, false)
                        confirmSetting = null
                    },
                ) { Text("Turn off") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSetting = null }) { Text("Cancel") }
            },
        )
    }
}

private fun iconFor(key: String): ImageVector = when (key) {
    "adb_enabled" -> Icons.Outlined.Usb
    "adb_wifi_enabled" -> Icons.Outlined.Wifi
    "adb_allowed_connection_time" -> Icons.Outlined.Key
    "stay_on_while_plugged_in" -> Icons.Outlined.Coffee
    "development_settings_enabled" -> Icons.Outlined.DeveloperMode
    "always_finish_activities" -> Icons.Outlined.LayersClear
    "mobile_data_always_on" -> Icons.Outlined.SignalCellularAlt
    else -> Icons.Outlined.Tune
}

private fun groupShape(index: Int, count: Int): Shape {
    val big = 20.dp
    val small = 6.dp
    val top = if (index == 0) big else small
    val bottom = if (index == count - 1) big else small
    return RoundedCornerShape(top, top, bottom, bottom)
}

@Composable
private fun AccessBadge(hasAccess: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (hasAccess) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (hasAccess) Icons.Outlined.VerifiedUser else Icons.Outlined.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (hasAccess) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (hasAccess) "Full access" else "Limited",
                style = MaterialTheme.typography.labelSmall,
                color = if (hasAccess) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
        }
    }
}

@Composable
private fun QuickActions() {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.DeveloperMode, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Developer options")
        }
        FilledTonalButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            },
        ) {
            Icon(Icons.Outlined.NotificationsNone, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Alerts")
        }
    }
}

@Composable
private fun AccessCard() {
    val clipboard = LocalClipboardManager.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "One-time setup needed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "DevSwitch changes protected settings, which Android only allows after a " +
                    "single ADB grant from a computer:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        GRANT_COMMAND,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { clipboard.setText(AnnotatedString(GRANT_COMMAND)) }) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Copy command",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroup(
    label: String,
    settings: List<DevSetting>,
    values: Map<String, Boolean>,
    watched: Set<String>,
    enabled: Boolean,
    onToggle: (DevSetting, Boolean) -> Unit,
    onWatch: (DevSetting) -> Unit,
) {
    if (settings.isEmpty()) return
    SectionLabel(label)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        settings.forEachIndexed { index, setting ->
            SettingRow(
                setting = setting,
                checked = values[setting.key] == true,
                watched = setting.key in watched,
                enabled = enabled,
                shape = groupShape(index, settings.size),
                onToggle = { onToggle(setting, it) },
                onWatch = { onWatch(setting) },
            )
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SettingRow(
    setting: DevSetting,
    checked: Boolean,
    watched: Boolean,
    enabled: Boolean,
    shape: Shape,
    onToggle: (Boolean) -> Unit,
    onWatch: () -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (checked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconFor(setting.key),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (checked) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(setting.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    setting.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            IconButton(onClick = onWatch) {
                Icon(
                    if (watched) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                    contentDescription = if (watched) "Stop watching" else "Watch for changes",
                    tint = if (watched) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
            Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
        }
    }
}

@Composable
private fun AnimationsCard(
    animScale: Float,
    enabled: Boolean,
    onSelect: (Float) -> Unit,
) {
    SectionLabel("Animations")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Animation speed", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Window, transition & animator scales",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            val options = listOf(0f to "Off", 0.5f to "0.5×", 1f to "1×", 2f to "2×", 5f to "5×")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = abs(animScale - value) < 0.01f,
                        onClick = { onSelect(value) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}
