package org.sumidoku.android.feature.reader

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/** Which way a volume-key press turns the page. */
enum class VolumeKeyPage { Next, Previous }

/**
 * Volume-up turns forward, volume-down turns back (KMReader
 * `VolumeKeyPaging.kt` parity). A right-to-left chapter mirrors the pair with
 * the rest of the reader: volume-up keeps pointing at the physically right
 * page, which is the *previous* one when pages run right to left.
 */
fun volumeKeyPage(volumeUp: Boolean, rtl: Boolean): VolumeKeyPage =
    if (volumeUp != rtl) VolumeKeyPage.Next else VolumeKeyPage.Previous

/**
 * Window-level volume-key bridge between [MainActivity] and the reader body
 * that is on screen.
 *
 * The reader is one Compose tree with no focusable node, and the pager, the
 * scroll lists and the overlay controls all compete for focus, so a
 * `Modifier.onPreviewKeyEvent` hook would miss presses as soon as any of them
 * took key focus. The activity-level dispatch sees every key before the
 * system does — the press is consumed here, so the system volume stays
 * untouched and no volume HUD appears (KMReader `ReaderVolumeKeyDispatcher`
 * behaviour).
 */
object ReaderVolumeKeyDispatcher {
    private var handler: ((KeyEvent) -> Boolean)? = null

    /**
     * Registers the active reader body. The most recent registration wins;
     * the returned detach only clears the handler it installed, so a
     * replaced body cannot unhook its successor.
     */
    fun attach(handler: (KeyEvent) -> Boolean): () -> Unit {
        this.handler = handler
        return { if (this.handler === handler) this.handler = null }
    }

    /** Returns true when the event was a volume key owned by the reader. */
    fun dispatch(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            return false
        }
        return handler?.invoke(event) == true
    }
}

/**
 * Arms the volume keys for as long as this composable is in the tree and
 * [enabled]. Only the initial press turns a page; repeats and the key-up are
 * swallowed so holding the rocker does not race through the chapter and the
 * system never sees a volume change.
 */
@Composable
fun VolumeKeyPagingEffect(
    enabled: Boolean,
    rtl: Boolean,
    onTurn: (VolumeKeyPage) -> Unit,
) {
    val latestRtl by rememberUpdatedState(rtl)
    val latestOnTurn by rememberUpdatedState(onTurn)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val detach = ReaderVolumeKeyDispatcher.attach { event ->
            val volumeUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                latestOnTurn(volumeKeyPage(volumeUp = volumeUp, rtl = latestRtl))
            }
            true
        }
        onDispose { detach() }
    }
}
