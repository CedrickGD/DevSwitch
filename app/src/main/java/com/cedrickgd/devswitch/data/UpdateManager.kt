package com.cedrickgd.devswitch.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.cedrickgd.devswitch.service.InstallReceiver
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    val sizeBytes: Long,
)

/** Checks GitHub Releases for a newer APK and installs it in-place. */
object UpdateManager {

    private const val REPO = "CedrickGD/DevSwitch"

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0.0.0"

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = (URL("https://api.github.com/repos/$REPO/releases/latest")
            .openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (connection.responseCode != 200) return@withContext null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val remote = json.getString("tag_name").removePrefix("v")
            if (!isNewer(remote, currentVersion(context))) return@withContext null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    return@withContext UpdateInfo(
                        versionName = remote,
                        apkUrl = asset.getString("browser_download_url"),
                        notes = json.optString("body").trim(),
                        sizeBytes = asset.optLong("size"),
                    )
                }
            }
            null
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "devswitch-${info.versionName}.apk")
        dir.listFiles()?.forEach { if (it != file) it.delete() } // drop stale downloads
        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            val total = if (info.sizeBytes > 0) info.sizeBytes else connection.contentLengthLong
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress(copied.toFloat() / total)
                    }
                }
            }
            file
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Installs the APK as a silent self-update via [PackageInstaller].
     * On Android 12+ an app that holds REQUEST_INSTALL_PACKAGES may update
     * itself with no confirmation dialog (USER_ACTION_NOT_REQUIRED); on
     * Android 14+ we also claim update-ownership so later updates stay silent.
     * If the system still insists on a prompt, [InstallReceiver] launches it.
     */
    fun installApk(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= 34) {
                runCatching { setRequestUpdateOwnership(true) }
            }
        }
        // Arm the accessibility auto-tapper (if the user enabled seamless mode)
        // so the system confirm dialog is dismissed without a manual tap.
        AutoInstall.arm()
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("devswitch.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val statusIntent = Intent(context, InstallReceiver::class.java)
                .setAction(InstallReceiver.ACTION_INSTALL_STATUS)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                statusIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(pending.intentSender)
        }
    }
}
