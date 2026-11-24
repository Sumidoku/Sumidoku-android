package org.sumidoku.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Baseline palette of the original design spec (docs/android-client.md §14):
// --bg #101014, --surface #16161c, --accent #A8B5FF. Light mode keeps
// the same accent on a light background.
private val SumidokuDark = darkColorScheme(
    primary = Color(0xFFA8B5FF),
    onPrimary = Color(0xFF1A1D33),
    primaryContainer = Color(0xFF2E3352),
    onPrimaryContainer = Color(0xFFDDE1FF),
    background = Color(0xFF101014),
    onBackground = Color(0xFFE6E6EB),
    surface = Color(0xFF16161C),
    onSurface = Color(0xFFE6E6EB),
    surfaceVariant = Color(0xFF1E1E26),
    onSurfaceVariant = Color(0xFF9A9AA8),
    outline = Color(0xFF3D415A),
    error = Color(0xFFF2B8B5),
)

private val SumidokuLight = lightColorScheme(
    primary = Color(0xFF4A55C4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF1A1D33),
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF1A1A1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1E),
    surfaceVariant = Color(0xFFEAEAF0),
    onSurfaceVariant = Color(0xFF5A5A66),
    outline = Color(0xFFC4C4D0),
    error = Color(0xFFBA1A1A),
)

// Design 37: AMOLED keeps the dark palette's text and accent but drops the
// background to true black, and separates cards/sheets/bars with three
// near-black surfaceContainer steps instead of one flat surface. Pure black
// everywhere except those steps would erase the layering.
private val SumidokuAmoled = darkColorScheme(
    primary = Color(0xFFA8B5FF),
    onPrimary = Color(0xFF1A1D33),
    primaryContainer = Color(0xFF2E3352),
    onPrimaryContainer = Color(0xFFDDE1FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE6E6EB),
    surface = Color(0xFF050507),
    onSurface = Color(0xFFE6E6EB),
    surfaceVariant = Color(0xFF101014),
    onSurfaceVariant = Color(0xFF9A9AA8),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF08080B),
    surfaceContainer = Color(0xFF0C0C10),
    surfaceContainerHigh = Color(0xFF131318),
    surfaceContainerHighest = Color(0xFF1A1A21),
    outline = Color(0xFF3D415A),
    outlineVariant = Color(0xFF23232C),
    error = Color(0xFFF2B8B5),
)

@Composable
fun SumidokuTheme(themePref: String = "system", content: @Composable () -> Unit) {
    // "system" resolves to dark unless the device is in light mode: the app is
    // dark-first, so this is the only case that needs the system signal.
    val dark = when (themePref) {
        "light" -> false
        "dark", "amoled" -> true
        else -> isSystemInDarkTheme()
    }
    // Monet: Android 12+ can pull the scheme from the wallpaper. Anything
    // older (or a restored backup naming "dynamic") falls back to the static
    // palette, behaving like "system".
    val dynamic = themePref == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        themePref == "amoled" -> SumidokuAmoled
        dynamic && dark -> dynamicDarkColorScheme(LocalContext.current)
        dynamic -> dynamicLightColorScheme(LocalContext.current)
        dark -> SumidokuDark
        else -> SumidokuLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
