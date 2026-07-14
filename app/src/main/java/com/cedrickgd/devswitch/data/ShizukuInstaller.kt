package com.cedrickgd.devswitch.data

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Installs updates through Shizuku's privileged (shell UID) process, which
 * makes the install fully silent — no confirmation dialog and no Play Protect
 * prompt, exactly like `adb install`. Falls back to the normal installer when
 * Shizuku isn't installed, running, or authorized.
 */
object ShizukuInstaller {

    const val PERMISSION_REQUEST_CODE = 4711
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    enum class Status {
        NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY,
    }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun binderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        binderAlive() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun status(context: Context): Status = when {
        !isInstalled(context) -> Status.NOT_INSTALLED
        !binderAlive() -> Status.NOT_RUNNING
        !hasPermission() -> Status.NEEDS_PERMISSION
        else -> Status.READY
    }

    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (!binderAlive()) {
            onResult(false)
            return
        }
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != PERMISSION_REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (_: Throwable) {
            Shizuku.removeRequestPermissionResultListener(listener)
            onResult(false)
        }
    }

    private fun newProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as Process
    }

    /** Streams the APK through `pm install` running as the shell UID. */
    suspend fun install(context: Context, file: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(hasPermission()) { "Shizuku is not authorized" }
                val pkg = context.packageName
                val size = file.length()

                val create = newProcess(
                    arrayOf("sh", "-c", "pm install-create -r -i $pkg -S $size"),
                )
                val createOut = create.inputStream.bufferedReader().use { it.readText() }
                create.waitFor()
                val sessionId = Regex("\\[(\\d+)]").find(createOut)?.groupValues?.get(1)
                    ?: error("install-create failed: ${createOut.trim()}")

                val write = newProcess(
                    arrayOf("sh", "-c", "pm install-write -S $size $sessionId base -"),
                )
                file.inputStream().use { input -> write.outputStream.use { input.copyTo(it) } }
                check(write.waitFor() == 0) { "install-write failed" }

                val commit = newProcess(arrayOf("sh", "-c", "pm install-commit $sessionId"))
                val commitOut = (
                    commit.inputStream.bufferedReader().use { it.readText() } +
                        commit.errorStream.bufferedReader().use { it.readText() }
                    ).trim()
                check(commit.waitFor() == 0 && commitOut.contains("Success")) {
                    "install-commit failed: $commitOut"
                }
            }
        }
}
