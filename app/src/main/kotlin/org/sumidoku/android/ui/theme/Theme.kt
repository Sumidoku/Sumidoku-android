package org.sumidoku.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette lifted from the design preview (design/preview/assets/mock.css):
// --bg #101014, --surface #16161c, --accent #A8B5FF. Light mode keeps
// the same accent on a light background (design §14 M9 theming).
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

@Composable
fun SumidokuTheme(themePref: String = "system", content: @Composable () -> Unit) {
    val dark = when (themePref) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) SumidokuDark else SumidokuLight,
        content = content,
    )
}
