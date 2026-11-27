package org.sumidoku.android.feature.reader

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Keeps the screen awake while [enabled] (KMReader ReaderDisplayEffects parity).
 *
 * The flag is set on the host view rather than on the activity window so it
 * dies with the reader: leaving the reader can never leave the device awake.
 * Called ONCE, by ReaderScreen — never by the individual reader bodies, so a
 * body that uncomposes mid-chapter cannot clear the flag a sibling just set.
 */
@Composable
internal fun KeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Padding that keeps page content out of the display cutout when
 * [drawUnderCutout] is off (KMReader readerCutoutPadding parity).
 *
 * The reader is edge-to-edge, and Android 15+ forces the window into the
 * cutout regardless of `layoutInDisplayCutoutMode`, so "hide from the notch"
 * can only be expressed as content padding. Off gives a band of the reader's
 * own background; on gives the page the full screen.
 */
@Composable
internal fun readerCutoutPadding(drawUnderCutout: Boolean): PaddingValues {
    if (drawUnderCutout) return PaddingValues(0.dp)
    val density = LocalDensity.current
    // PaddingValues resolves start/end against the layout direction, while the
    // cutout is a physical edge: swap the two sides under RTL so a notch on the
    // left edge keeps padding the left edge instead of the mirrored one.
    val layoutDirection = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout
    val left = with(density) { cutout.getLeft(density, layoutDirection).toDp() }
    val right = with(density) { cutout.getRight(density, layoutDirection).toDp() }
    val rtl = layoutDirection == LayoutDirection.Rtl
    return PaddingValues(
        start = if (rtl) right else left,
        top = with(density) { cutout.getTop(density).toDp() },
        end = if (rtl) left else right,
        bottom = with(density) { cutout.getBottom(density).toDp() },
    )
}

/** True when the device actually has a notch / punch-hole on one of its edges.
 *  Used to grey out the cutout switch on devices where it would do nothing. */
@Composable
internal fun rememberHasDisplayCutout(): Boolean {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout
    return cutout.getTop(density) > 0 ||
        cutout.getBottom(density) > 0 ||
        cutout.getLeft(density, layoutDirection) > 0 ||
        cutout.getRight(density, layoutDirection) > 0
}
