package org.sumidoku.android.feature.trackers

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.sumidoku.android.core.api.model.TrackerProvider

/** Provider display names, shared by the account screen and the manga
 *  detail pill (same strings as webui TRACKER_PROVIDER_NAMES). */
fun trackerProviderLabel(provider: TrackerProvider): String = when (provider) {
    TrackerProvider.ANILIST -> "AniList"
    TrackerProvider.MAL -> "MyAnimeList"
    TrackerProvider.BANGUMI -> "Bangumi"
    TrackerProvider.MANGABAKA -> "MangaBaka"
}

/** Brand artwork per provider (task ⑧): AniList/MangaBaka ship as PNG in
 *  res/drawable-nodpi, Bangumi/MAL were hand-converted from the webui SVGs
 *  (G:\Github\Sumidoku\webui\src\assets\trackers) into vector drawables. */
internal fun trackerLogoRes(provider: TrackerProvider): Int = when (provider) {
    TrackerProvider.ANILIST -> R.drawable.tracker_anilist
    TrackerProvider.MAL -> R.drawable.tracker_mal
    TrackerProvider.BANGUMI -> R.drawable.tracker_bangumi
    TrackerProvider.MANGABAKA -> R.drawable.tracker_mangabaka
}

/** Monogram + brand colour, kept as the fallback when a logo resource
 *  cannot be loaded and for the tinted dot on tracker pills. */
internal data class TrackerBrand(val monogram: String, val color: Color)

internal fun trackerBrand(provider: TrackerProvider): TrackerBrand = when (provider) {
    TrackerProvider.ANILIST -> TrackerBrand("AL", Color(0xFF02A9FF))
    TrackerProvider.MAL -> TrackerBrand("MAL", Color(0xFF2E51A2))
    TrackerProvider.BANGUMI -> TrackerBrand("BGM", Color(0xFFF0913A))
    TrackerProvider.MANGABAKA -> TrackerBrand("MB", Color(0xFF6E56CF))
}

/** The provider's brand logo at [size] dp. Falls back to the old lettered
 *  square when the drawable is missing or cannot be inflated, so a broken
 *  asset never leaves an empty row. */
@Composable
fun TrackerLogo(
    provider: TrackerProvider,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val context = LocalContext.current
    val resId = trackerLogoRes(provider)
    val loadable = remember(resId) {
        runCatching { context.resources.getDrawable(resId, context.theme) }.isSuccess
    }
    if (!loadable) {
        TrackerLetterBadge(provider, modifier, size)
        return
    }
    // Bangumi is a pink line glyph on transparency; a white tile keeps it
    // visible on dark surfaces (webui .tracker-logo--bangumi does the same).
    val background = if (provider == TrackerProvider.BANGUMI) Color.White else Color.Transparent
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.18f))
            .background(background),
    ) {
        Image(
            painter = painterResource(resId),
            contentDescription = trackerProviderLabel(provider),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun TrackerLetterBadge(
    provider: TrackerProvider,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val brand = trackerBrand(provider)
    // Tint the square and lift the glyph towards the foreground colour so
    // the same brand colour stays legible in light and dark themes.
    val glyph = lerp(brand.color, MaterialTheme.colorScheme.onSurface, 0.35f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(brand.color.copy(alpha = 0.18f)),
    ) {
        Text(
            brand.monogram,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = glyph,
        )
    }
}
