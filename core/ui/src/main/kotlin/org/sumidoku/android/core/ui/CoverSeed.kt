package org.sumidoku.android.core.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
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

/** komikku-style cover seed colour: androidx Palette swatch analysis picking
 *  the most characteristic colour of the cover (the same logic as komikku's
 *  Palette.getBestColor in data/coil/Utils.kt), so the page theme and tinted
 *  controls (tags, FAB, buttons) follow the cover's vibrant hue instead of a
 *  muted average. */
fun computeCoverTint(bitmap: Bitmap): Color? {
    val software = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        bitmap
    }
    return Palette.from(software).generate().bestColor()?.let(::Color)
}

/** Port of komikku's Palette.getBestColor (Jays2Kings): prefer a colourful,
 *  mid-brightness dominant swatch, fall back to vibrant, then muted when it
 *  is big and colourful enough, finally the strongest vibrant variant. */
private fun Palette.bestColor(): Int? {
    // How big is the vibrant color
    val vibPopulation = vibrantSwatch?.population ?: -1
    // Saturation of the most dominant color
    val domSat = dominantSwatch?.hsl?.get(1) ?: 0f
    // Brightness of the most dominant color
    val domLum = dominantSwatch?.hsl?.get(2) ?: -1f
    // How big is the muted color
    val mutedPopulation = mutedSwatch?.population ?: -1
    // Saturation of the muted color
    val mutedSat = mutedSwatch?.hsl?.get(1) ?: 0f
    // If muted color is 3 times bigger than vibrant color then minimum acceptable saturation
    // for muted color is lower (more likely to use it even if it's not colorful)
    val mutedSatMinAcceptable = if (mutedPopulation > vibPopulation * 3f) 0.1f else 0.25f

    val dominantIsColorful = domSat >= .25f
    val dominantBrightnessJustRight = domLum <= .8f && domLum > .2f
    val vibrantIsConsiderableBigEnough = vibPopulation >= mutedPopulation * 0.75f
    val mutedIsBig = mutedPopulation > vibPopulation * 1.5f
    val mutedIsNotTooBoring = mutedSat > mutedSatMinAcceptable

    return when {
        dominantIsColorful && dominantBrightnessJustRight -> dominantSwatch
        // use vibrant color even if it's only 0.75 times smaller than muted color
        vibrantIsConsiderableBigEnough -> vibrantSwatch
        // use muted color if it's 1.5 times bigger than vibrant color and colorful enough (above the limit)
        mutedIsBig && mutedIsNotTooBoring -> mutedSwatch
        // return major vibrant color variant with more favor of vibrant color (size x3)
        else -> listOfNotNull(vibrantSwatch, lightVibrantSwatch, darkVibrantSwatch)
            .maxByOrNull {
                if (it === vibrantSwatch) vibPopulation * 3 else it.population
            }
    }?.rgb
}
