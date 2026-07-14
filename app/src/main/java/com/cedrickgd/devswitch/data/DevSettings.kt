package com.cedrickgd.devswitch.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "DevSwitch"

enum class SettingNamespace { GLOBAL, SYSTEM }

data class DevSetting(
    val key: String,
    val namespace: SettingNamespace,
    val title: String,
    val description: String,
    val onValue: String = "1",
    val offValue: String = "0",
    val minApi: Int = 0,
    val confirmOffMessage: String? = null,
)

object SettingsRegistry {

    val debugging = listOf(
        DevSetting(
            key = "adb_enabled",
            namespace = SettingNamespace.GLOBAL,
            title = "USB debugging",
            description = "Allow ADB debugging over USB",
        ),
        DevSetting(
            key = "adb_wifi_enabled",
            namespace = SettingNamespace.GLOBAL,
            title = "Wireless debugging",
            description = "Allow ADB debugging over Wi-Fi",
            minApi = 30,
            confirmOffMessage = "Active wireless ADB connections will be dropped immediately.",
        ),
        DevSetting(
            key = "adb_allowed_connection_time",
            namespace = SettingNamespace.GLOBAL,
            title = "Keep ADB authorized",
            description = "Never auto-revoke debugging authorizations (prevents stale pairings)",
            onValue = "0",
            offValue = "604800000",
        ),
    )

    val displayInput = listOf(
        DevSetting(
            key = "stay_on_while_plugged_in",
            namespace = SettingNamespace.GLOBAL,
            title = "Stay awake",
            description = "Screen never sleeps while charging",
            onValue = "7",
        ),
    )

    val system = listOf(
        DevSetting(
            key = "development_settings_enabled",
            namespace = SettingNamespace.GLOBAL,
            title = "Developer options",
            description = "Master switch for developer options",
        ),
        DevSetting(
            key = "always_finish_activities",
            namespace = SettingNamespace.GLOBAL,
            title = "Don't keep activities",
            description = "Destroy every activity as soon as you leave it",
        ),
        DevSetting(
            key = "mobile_data_always_on",
            namespace = SettingNamespace.GLOBAL,
            title = "Mobile data always active",
            description = "Keep mobile data on, even when Wi-Fi is connected",
        ),
    )

    val all: List<DevSetting> = debugging + displayInput + system

    fun byKey(key: String): DevSetting? = all.firstOrNull { it.key == key }
}

/**
 * Records writes made by DevSwitch itself so the monitor can tell
 * self-made changes apart from external ones.
 */
object SelfChangeTracker {
    private val lastWrite = ConcurrentHashMap<String, Long>()

    fun recordWrite(key: String) {
        lastWrite[key] = SystemClock.elapsedRealtime()
    }

    fun wasRecentSelfChange(key: String, windowMs: Long = 4000L): Boolean {
        val at = lastWrite[key] ?: return false
        return SystemClock.elapsedRealtime() - at < windowMs
    }
}

class DevSettingsController(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun hasSecureSettingsAccess(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isSupported(setting: DevSetting): Boolean = Build.VERSION.SDK_INT >= setting.minApi

    fun rawValue(setting: DevSetting): String? = try {
        when (setting.namespace) {
            SettingNamespace.GLOBAL -> Settings.Global.getString(resolver, setting.key)
            SettingNamespace.SYSTEM -> Settings.System.getString(resolver, setting.key)
        }
    } catch (_: Exception) {
        null
    }

    fun isOn(setting: DevSetting): Boolean {
        val value = rawValue(setting) ?: return false
        return value.isNotEmpty() && value != setting.offValue && value != "null"
    }

    fun setEnabled(setting: DevSetting, on: Boolean): Result<Unit> = runCatching {
        SelfChangeTracker.recordWrite(setting.key)
        val value = if (on) setting.onValue else setting.offValue
        val ok = when (setting.namespace) {
            SettingNamespace.GLOBAL -> Settings.Global.putString(resolver, setting.key, value)
            SettingNamespace.SYSTEM -> Settings.System.putString(resolver, setting.key, value)
        }
        check(ok) { "The system rejected the settings write" }
    }.onFailure {
        Log.w(TAG, "Failed to write ${setting.namespace}/${setting.key}", it)
    }

    fun uriFor(setting: DevSetting): Uri = when (setting.namespace) {
        SettingNamespace.GLOBAL -> Settings.Global.getUriFor(setting.key)
        SettingNamespace.SYSTEM -> Settings.System.getUriFor(setting.key)
    }

    // --- animation scales -------------------------------------------------

    val animationKeys = listOf(
        "window_animation_scale",
        "transition_animation_scale",
        "animator_duration_scale",
    )

    fun animationScale(): Float = try {
        Settings.Global.getString(resolver, "animator_duration_scale")?.toFloatOrNull() ?: 1f
    } catch (_: Exception) {
        1f
    }

    fun setAnimationScale(scale: Float): Result<Unit> = runCatching {
        animationKeys.forEach { key ->
            SelfChangeTracker.recordWrite(key)
            check(Settings.Global.putString(resolver, key, scale.toString())) {
                "The system rejected the settings write"
            }
        }
    }

    // --- Play Protect install verification ---------------------------------
    // These globals gate the "Play Protect doesn't recognize this app — scan?"
    // dialog that GMS shows on every sideloaded install. Writable with
    // WRITE_SECURE_SETTINGS; disabling them stops the prompt during updates.

    fun isPlayProtectScanEnabled(): Boolean {
        val enable = readGlobalInt("package_verifier_enable", 1)
        val consent = readGlobalInt("package_verifier_user_consent", 1)
        return enable != 0 && consent != -1
    }

    fun setPlayProtectScan(enabled: Boolean): Result<Unit> = runCatching {
        if (enabled) {
            putGlobal("package_verifier_enable", "1")
            putGlobal("package_verifier_user_consent", "1")
            putGlobal("upload_apk_enable", "1")
        } else {
            putGlobal("package_verifier_enable", "0")
            putGlobal("package_verifier_user_consent", "-1")
            putGlobal("verifier_verify_adb_installs", "0")
            putGlobal("upload_apk_enable", "0")
        }
    }

    private fun readGlobalInt(key: String, default: Int): Int = try {
        Settings.Global.getInt(resolver, key, default)
    } catch (_: Exception) {
        default
    }

    private fun putGlobal(key: String, value: String) {
        SelfChangeTracker.recordWrite(key)
        // Best-effort: some keys may be absent/rejected on a given OEM build;
        // don't let one failure abort the rest.
        runCatching { Settings.Global.putString(resolver, key, value) }
    }
}
