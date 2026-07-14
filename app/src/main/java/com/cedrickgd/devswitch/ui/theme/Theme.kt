package com.cedrickgd.devswitch.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.cedrickgd.devswitch.data.ThemeMode

data class AccentOption(val id: String, val label: String, val seed: Color)

const val ACCENT_DYNAMIC = "dynamic"

val accentOptions = listOf(
    AccentOption(ACCENT_DYNAMIC, "Dynamic", Color(0xFF7C4DFF)),
    AccentOption("indigo", "Indigo", Color(0xFF4F46E5)),
    AccentOption("sky", "Sky", Color(0xFF0284C7)),
    AccentOption("teal", "Teal", Color(0xFF0D9488)),
    AccentOption("emerald", "Emerald", Color(0xFF059669)),
    AccentOption("amber", "Amber", Color(0xFFD97706)),
    AccentOption("coral", "Coral", Color(0xFFEA580C)),
    AccentOption("rose", "Rose", Color(0xFFDB2777)),
    AccentOption("crimson", "Crimson", Color(0xFFDC2626)),
    AccentOption("violet", "Violet", Color(0xFF7C3AED)),
)

fun dynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= 31

private fun Color.mix(other: Color, ratio: Float): Color = Color(
    red = red * (1f - ratio) + other.red * ratio,
    green = green * (1f - ratio) + other.green * ratio,
    blue = blue * (1f - ratio) + other.blue * ratio,
)

private fun seedScheme(seed: Color, dark: Boolean): ColorScheme = if (dark) {
    val background = Color(0xFF0F1013)
    darkColorScheme(
        primary = seed.mix(Color.White, 0.38f),
        onPrimary = seed.mix(Color.Black, 0.72f),
        primaryContainer = seed.mix(Color.Black, 0.58f),
        onPrimaryContainer = seed.mix(Color.White, 0.82f),
        secondary = seed.mix(Color(0xFFA6A9B3), 0.55f),
        onSecondary = Color(0xFF17181D),
        secondaryContainer = seed.mix(Color(0xFF1D1F25), 0.68f),
        onSecondaryContainer = seed.mix(Color.White, 0.85f),
        tertiary = seed.mix(Color.White, 0.5f),
        background = background,
        onBackground = Color(0xFFE5E6EB),
        surface = background,
        onSurface = Color(0xFFE5E6EB),
        surfaceVariant = Color(0xFF23252C),
        onSurfaceVariant = Color(0xFFA6A9B3),
        surfaceContainerLowest = Color(0xFF0B0C0E),
        surfaceContainerLow = Color(0xFF141519),
        surfaceContainer = Color(0xFF17181D),
        surfaceContainerHigh = Color(0xFF1D1F25),
        surfaceContainerHighest = Color(0xFF23252C),
        outline = Color(0xFF5D616D),
        outlineVariant = Color(0xFF2E313A),
    )
} else {
    val background = Color(0xFFFAFAFD)
    lightColorScheme(
        primary = seed,
        onPrimary = Color.White,
        primaryContainer = seed.mix(Color.White, 0.85f),
        onPrimaryContainer = seed.mix(Color.Black, 0.45f),
        secondary = seed.mix(Color(0xFF5A5D66), 0.45f),
        onSecondary = Color.White,
        secondaryContainer = seed.mix(Color.White, 0.80f),
        onSecondaryContainer = seed.mix(Color.Black, 0.55f),
        tertiary = seed.mix(Color.Black, 0.2f),
        background = background,
        onBackground = Color(0xFF191B20),
        surface = background,
        onSurface = Color(0xFF191B20),
        surfaceVariant = Color(0xFFE2E4EA),
        onSurfaceVariant = Color(0xFF454852),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF3F4F8),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFEEF0F4),
        surfaceContainerHighest = Color(0xFFE8EAEF),
        outline = Color(0xFF757986),
        outlineVariant = Color(0xFFC6C9D2),
    )
}

@Composable
fun DevSwitchTheme(
    themeMode: ThemeMode,
    accentId: String,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = if (accentId == ACCENT_DYNAMIC && dynamicColorSupported()) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val seed = accentOptions.firstOrNull { it.id == accentId }?.seed
            ?: accentOptions[1].seed
        seedScheme(seed, dark)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
