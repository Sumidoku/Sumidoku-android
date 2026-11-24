package org.sumidoku.android.core.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.DynamicMaterialTheme

/** komikku "theme based on cover" parity: once the cover colour is known,
 *  generate a full dynamic colour scheme from it (materialkolor, the same
 *  library komikku uses) so every control on the page follows the cover;
 *  without a cover colour the app theme passes through untouched. */
@Composable
fun CoverSeedTheme(seedColor: Color?, content: @Composable () -> Unit) {
    if (seedColor == null) {
        content()
    } else {
        DynamicMaterialTheme(
            seedColor = seedColor,
            // The app theme may force dark/light, so read the applied scheme
            // instead of asking the system.
            isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f,
            animate = true,
            content = content,
        )
    }
}

/** KMReader-style cover tint (ui/components computeCoverTint): the 8×8
 *  average colour of the cover with HSV saturation capped at 0.8 and value
 *  clamped to 0.2–0.45, so surfaces seeded from it stay readable in both
 *  light and dark themes. */
fun computeCoverTint(bitmap: Bitmap): Color? {
    val software = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        bitmap
    }
    val scaled = Bitmap.createScaledBitmap(software, 8, 8, true)
    var r = 0L
    var g = 0L
    var b = 0L
    for (y in 0 until 8) {
        for (x in 0 until 8) {
            val pixel = scaled.getPixel(x, y)
            r += (pixel shr 16) and 0xFF
            g += (pixel shr 8) and 0xFF
            b += pixel and 0xFF
        }
    }
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (r / 64f).toInt().coerceIn(0, 255),
        (g / 64f).toInt().coerceIn(0, 255),
        (b / 64f).toInt().coerceIn(0, 255),
        hsv,
    )
    hsv[1] = hsv[1].coerceAtMost(0.8f)
    hsv[2] = hsv[2].coerceIn(0.2f, 0.45f)
    return Color.hsv(hsv[0], hsv[1], hsv[2])
}
