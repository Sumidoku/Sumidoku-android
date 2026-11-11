package org.sumidoku.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette lifted from the design preview (design/preview/assets/mock.css):
// --bg #101014, --surface #16161c, --accent #A8B5FF.
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

@Composable
fun SumidokuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SumidokuDark,
        content = content,
    )
}
