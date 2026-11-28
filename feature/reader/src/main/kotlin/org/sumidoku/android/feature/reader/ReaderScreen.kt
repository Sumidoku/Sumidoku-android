package org.sumidoku.android.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.model.ChapterWithDownload
import org.sumidoku.android.core.api.model.Page
import org.sumidoku.android.core.common.chapterLabel
import org.sumidoku.android.core.common.chapterNumberLabel
import org.sumidoku.android.core.image.upscale.DenoiseLevel
import org.sumidoku.android.core.image.upscale.DisplayScaleMode
import org.sumidoku.android.core.image.upscale.EnhancementCache
import org.sumidoku.android.core.image.upscale.EnhancementDecoder
import org.sumidoku.android.core.image.upscale.ImageEnhancer
import org.sumidoku.android.core.image.upscale.UpscaleConfig
import org.sumidoku.android.core.image.upscale.UpscaleMode
import org.sumidoku.android.core.image.upscale.UpscalePrecision
import org.sumidoku.android.core.image.upscale.UpscaleSettings
import org.sumidoku.android.core.ui.AppModalDialog
import org.sumidoku.android.core.ui.CoverSeedTheme
import org.sumidoku.android.core.ui.computeCoverTint

/** Effective typography for text pages — the reader half of the webui
 *  `typography.ts` contract (typeface, size and line-height multipliers)
 *  plus the ink colour that keeps prose legible on the chosen canvas. */
private data class ReaderTypography(
    val font: String = ReaderField.FONT.fallback,
    val size: Float = 1f,
    val lineHeight: Float = 1.8f,
    val color: Color = Color(0xFFE8E8E8),
) {
    val family: FontFamily
        get() = when (font) {
            "sans" -> FontFamily.SansSerif
            "serif" -> FontFamily.Serif
            "mono" -> FontFamily.Monospace
            else -> FontFamily.Default
        }
}

/** Provided once by [ReaderScreen]; every text page reads it, so the
 *  per-page call sites never have to thread the preferences through. */
private val LocalReaderTypography = staticCompositionLocalOf { ReaderTypography() }

/** Upscaling state for the open reader: the resolved config, the keys that already finished and
 *  the keys the engine still holds (queued or in flight). */
private class ReaderUpscaleContext(
    val config: UpscaleConfig,
    val readyKeys: Set<String>,
    val processingKeys: Set<String>,
)

/**
 * Provided once by [ReaderScreen]; every page image reads it, so the paged, spread and vertical
 * call sites never thread the config through their signatures. Enabled requests are marked for
 * [EnhancementDecoder] inside [ReaderImage], and [UpscaleBadge] turns the processing set into the
 * page-level "2x" hint.
 */
private val LocalReaderUpscale = staticCompositionLocalOf {
    ReaderUpscaleContext(UpscaleConfig(mode = UpscaleMode.OFF), emptySet(), emptySet())
}

/** Fields that pin to a single manga once edited inside the reader, and
 *  therefore earn the same override marker as mode/layout/direction. */
private val TYPOGRAPHY_FIELDS = setOf(ReaderField.FONT, ReaderField.FSIZE, ReaderField.FLINE)

/** Reader (design previews 07 paged / 08 webtoon / 24 settings /
 *  25 chapters / 39 spread / 40 transition / 41 quick panel).
 *
 *  Reading mode, direction and page layout resolve in three tiers —
 *  per-manga override › manga.viewer metadata › global default — the
 *  same chain the webui stores under `sumidoku.reader.<key>.<mangaId>`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Keys that finished enhancing on the native engine: a page whose key lands here is
    // re-requested and swaps from the already-shown source bitmap to the 2x result.
    val enhancedKeys by viewModel.enhancedKeys.collectAsState()
    // Keys still queued or inside the engine: the current page shows its "2x" chip as a spinner
    // until its key lands in enhancedKeys and the bitmap swaps in place.
    val processingKeys by viewModel.processingKeys.collectAsState()
    // Leaving the reader abandons queued pages (and the one inside the engine), so returning to
    // the library never leaves the GPU spinning on a book nobody is looking at.
    DisposableEffect(Unit) {
        onDispose { viewModel.onReaderClosed() }
    }
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val canvasColor = when (state.background) {
        "gray" -> Color(0xFF1E1E26)
        "light" -> Color(0xFFFFFFFF)
        "auto" -> MaterialTheme.colorScheme.surface
        else -> Color.Black
    }

    // Immersive full screen: bars hide with the overlay and come back
    // when it is shown (design 07/08 — no system chrome while reading).
    ImmersiveEffect(overlay = state.overlay || state.loading)

    // Seed the whole reader (transition pages, progress bar, loading
    // indicator, chrome capsules) with the cover colour — same materialkolor
    // scheme as the manga detail page. canvasColor stays computed from the
    // outer app theme so the page background itself is never tinted.
    val context = LocalContext.current
    var coverTint by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(state.coverUrl) {
        coverTint = null
        val url = state.coverUrl ?: return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).apply {
            state.profile?.apiKey?.let { key ->
                httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $key").build())
            }
        }.build()
        val image = (context.imageLoader.execute(request) as? SuccessResult)?.image
        image?.let { coverTint = runCatching { computeCoverTint(it.toBitmap()) }.getOrNull() }
    }

    // Prose ink follows the canvas: a light canvas needs dark text, the
    // dark/gray canvases a near-white one (the webui pairs the two the
    // same way, so the text pages never float white-on-white).
    val ink = when (state.background) {
        "light" -> Color(0xFF1B1B1F)
        "auto" -> MaterialTheme.colorScheme.onSurface
        else -> Color(0xFFE8E8E8)
    }
    val typography = remember(state.font, state.fontSize, state.lineHeight, ink) {
        ReaderTypography(state.font, state.fontSize, state.lineHeight, ink)
    }

    // Keep the screen awake while the reader is open (global-only switch);
    // the flag rides on the host view so leaving the reader always clears it.
    KeepScreenOnEffect(enabled = state.keepScreenAwake)

    // Content band: with "draw under cutout" off the pages/prose are
    // letterboxed out of the notch / punch-hole band (a band of the reader
    // background shows there); on they get the whole screen (KMReader parity).
    val contentInset = readerCutoutPadding(state.drawUnderCutout)

    val upscaleContext = remember(state.upscale, enhancedKeys, processingKeys) {
        ReaderUpscaleContext(state.upscale.toConfig(), enhancedKeys, processingKeys)
    }

    CoverSeedTheme(coverTint) {
    CompositionLocalProvider(
        LocalReaderTypography provides typography,
        LocalReaderUpscale provides upscaleContext,
    ) {
    Box(Modifier.fillMaxSize().background(canvasColor)) {
        Box(Modifier.fillMaxSize().padding(contentInset)) {
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxSize()
                    // Taps still toggle the chrome while loading so a stuck
                    // load never traps the user without a back/retry entry.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { viewModel.toggleOverlay() },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.openChapter(state.chapterId) }) { Text(stringResource(R.string.reader_retry)) }
                }
            }
            // Text chapters always read vertically: paging a book-like
            // chapter one screenful at a time is the behaviour the reader
            // is supposed to leave behind (the pager would also try to
            // request each text page as an image). On a wide window the
            // prose is instead split into viewport-wide columns and paged,
            // which is how the web reader presents a book.
            state.pagedText && state.textColumns && (landscape || configuration.screenWidthDp >= 600) ->
                PagedTextBody(state, viewModel)
            state.vertical || state.textChapter -> VerticalReaderBody(state, viewModel)
            else -> PagedReaderBody(state, viewModel, landscape)
        }
        }

        // Also drawn during loading: the overlay is the only way out (back /
        // chapters / settings) when a chapter load stalls.
        if (state.overlay) {
            ReaderOverlay(state, viewModel, onBack, landscape)
        }

        if (state.chapterSheet) {
            ChapterSheet(state, viewModel)
        }
    }
    }
    }
}

/** Hides the system bars while reading (previews 07/08 are edge-to-edge). */
@Composable
private fun ImmersiveEffect(overlay: Boolean) {
    val view = LocalView.current
    LaunchedEffect(overlay) {
        val activity = view.context.findActivity() ?: return@LaunchedEffect
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (overlay) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ---------------------------------------------------------------------------
// Paged reader
// ---------------------------------------------------------------------------

/** One display slot in paged mode: a whole page, the left/right half of a
 *  wide page when split-wide is on, or a spread of one or two pages.
 *  Progress and the slider always stay in real page units. */
private data class DisplayPage(val pages: List<Page>, val crop: Int) // 0 whole, 1 left, 2 right
{
    constructor(page: Page, crop: Int) : this(listOf(page), crop)
    val page: Page get() = pages.first()
}

private fun Page.isWide(): Boolean {
    val w = width ?: 0
    val h = height ?: 0
    return w > 0 && h > 0 && w > h
}

/** Spread builder (webui spreads.ts / Aidoku orderedViews parity):
 *  consecutive portrait pages pair in reading order, landscape pages get
 *  their own slot, and the cover can be singled out (doubleNoCover). */
private fun buildSpreads(
    pages: List<Page>,
    rtl: Boolean,
    coverFirst: Boolean,
): List<DisplayPage> {
    val spreads = mutableListOf<DisplayPage>()
    var i = 0
    var coverSingled = false
    while (i < pages.size) {
        val page = pages[i]
        if (page.isWide() || (coverFirst && !coverSingled)) {
            spreads.add(DisplayPage(page, 0))
            if (!page.isWide()) coverSingled = true
            i++
            continue
        }
        val next = pages.getOrNull(i + 1)
        if (next == null || next.isWide()) {
            spreads.add(DisplayPage(page, 0))
            i++
        } else {
            spreads.add(DisplayPage(if (rtl) listOf(next, page) else listOf(page, next), 0))
            i += 2
        }
    }
    return spreads
}

@Composable
private fun PagedReaderBody(state: ReaderUiState, viewModel: ReaderViewModel, landscape: Boolean) {
    val rtl = state.dir == "rtl"
    val double = state.doubleSpread(landscape)
    val displayPages = remember(state.pages, state.splitWide, state.layout, rtl, state.coverFirst, double) {
        if (double) {
            buildSpreads(state.pages, rtl, state.coverFirst)
        } else {
            buildList {
                state.pages.forEach { page ->
                    if (state.splitWide && page.isWide() && page.pageText() == null) {
                        add(DisplayPage(page, if (rtl) 2 else 1))
                        add(DisplayPage(page, if (rtl) 1 else 2))
                    } else {
                        add(DisplayPage(page, 0))
                    }
                }
            }
        }
    }
    val initialDisplay = displayPages
        .indexOfFirst { slot -> slot.pages.any { it.index.toInt() >= state.index } }
        .coerceAtLeast(0)
    val prevSlot = if (state.prevChapter != null) 1 else 0
    val pagerState = rememberPagerState(
        initialPage = initialDisplay + prevSlot,
        pageCount = { displayPages.size + prevSlot + if (state.nextChapter != null) 1 else 0 },
    )
    // While a page is under the finger, park the native tile loop so the compositor keeps the GPU.
    LaunchedEffect(pagerState.isScrollInProgress) {
        viewModel.setUpscalePaused(pagerState.isScrollInProgress)
    }
    LaunchedEffect(pagerState, displayPages) {
        snapshotFlow { pagerState.settledPage }.collect { slot ->
            displayPages.getOrNull(slot - prevSlot)?.let { viewModel.setIndex(it.pages.last().index.toInt()) }
        }
    }
    LaunchedEffect(state.chapterId) {
        if (state.index > 0) {
            val target = displayPages
                .indexOfFirst { slot -> slot.pages.any { it.index.toInt() >= state.index } } + prevSlot
            if (target > 0 && pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
        }
    }

    LaunchedEffect(state.seekRequest) {
        if (state.seekRequest > 0) {
            val target = displayPages
                .indexOfFirst { slot -> slot.pages.any { it.index.toInt() >= state.index } }
            if (target >= 0) pagerState.scrollToPage(target + prevSlot)
        }
    }

    val prefetchContext = LocalContext.current
    val prefetchUpscale = state.upscale.toConfig()
    val preloadAhead = state.upscale.preloadAhead
    LaunchedEffect(state.index, displayPages.size, prefetchUpscale, preloadAhead) {
        // With upscaling on, every prefetched page is a full 2x native run on one GPU queue: the
        // window is exactly what the user chose, and 0 means "do not preprocess anything".
        if (prefetchUpscale.enabled && preloadAhead == 0) return@LaunchedEffect
        val current = displayPages
            .indexOfFirst { slot -> slot.pages.any { it.index.toInt() >= state.index } }
            .takeIf { it >= 0 } ?: 0
        // Prefetch both neighbours: with RTL the "next" swipe is the slot
        // before the current one, so a one-sided window would miss it.
        // Plain prefetch keeps the pager's ±1..+2 window; with upscaling it
        // becomes the configured ahead depth (one slot behind always counts,
        // it is what an RTL swipe reveals).
        val ahead = if (prefetchUpscale.enabled) preloadAhead.coerceAtLeast(1) else 2
        for (i in (current - 1)..(current + ahead)) {
            if (i !in displayPages.indices) continue
            for (page in displayPages[i].pages) {
                if (page.pageText() != null) continue
                val url = viewModel.pageImageUrl(page)
                val builder = ImageRequest.Builder(prefetchContext).data(url).apply {
                    state.profile?.apiKey?.let {
                        httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $it").build())
                    }
                    if (prefetchUpscale.enabled) {
                        // Queue the page through the decoder at PRELOAD so the native work happens
                        // before the swipe; the 2x result lands in the enhancement cache and the
                        // display request then finds it on the first frame.
                        EnhancementDecoder.applyTo(
                            builder = this,
                            config = prefetchUpscale,
                            pageUrl = url,
                            priority = ImageEnhancer.PRIORITY_PRELOAD,
                            scaleMode = DisplayScaleMode.fromReaderScale(state.scale),
                            srcWidth = (page.width ?: 0L).toInt(),
                            srcHeight = (page.height ?: 0L).toInt(),
                        )
                        memoryCachePolicy(CachePolicy.DISABLED)
                    }
                }
                prefetchContext.imageLoader.enqueue(builder.build())
            }
        }
    }

    var zoomed by remember { mutableStateOf(false) }
    // A zoomed page keeps the pager locked; the flag must clear when the
    // settled slot changes (pinch state resets per page).
    LaunchedEffect(pagerState.settledPage) { zoomed = false }
    val pagerScope = rememberCoroutineScope()
    val pageBack: () -> Unit = {
        val slot = pagerState.currentPage
        if (slot - prevSlot < 0) {
            // Resting on the previous chapter's transition slot: a further
            // backward turn confirms it, exactly like that slot's tap zone.
            state.prevChapter?.let { viewModel.openChapter(it.id) }
        } else if (slot > 0) {
            pagerScope.launch { pagerState.animateScrollToPage(slot - 1) }
        }
    }
    val pageForward: () -> Unit = {
        val slot = pagerState.currentPage
        if (slot - prevSlot >= displayPages.size) {
            state.nextChapter?.let { viewModel.openChapter(it.id) }
        } else if (slot < pagerState.pageCount - 1) {
            pagerScope.launch { pagerState.animateScrollToPage(slot + 1) }
        }
    }
    // Volume rocker as a page turner (KMReader parity). The turn is the same
    // one the tap zones perform, so the chapter transition slots confirm on
    // the second press. Disabled while a sheet owns the screen.
    VolumeKeyPagingEffect(
        enabled = state.volumeKeyPaging && !state.overlay && !state.chapterSheet,
        rtl = rtl,
    ) { page ->
        when (page) {
            VolumeKeyPage.Next -> pageForward()
            VolumeKeyPage.Previous -> pageBack()
        }
    }
    // Second-turn confirm (webui pendingTurn / Komikku parity): while the
    // pager rests on a transition slot, a further drag past the edge arms a
    // pull; crossing the threshold opens the neighbouring chapter. The pull
    // is consumed in pre-scroll so the pager itself never moves past the
    // slot, and it decays quickly when abandoned.
    var edgePull by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val edgePullThreshold = with(density) { 120.dp.toPx() }
    val latestState by rememberUpdatedState(state)
    val latestPrevSlot by rememberUpdatedState(prevSlot)
    val latestDisplayCount by rememberUpdatedState(displayPages.size)
    LaunchedEffect(pagerState.settledPage) { edgePull = 0f }
    LaunchedEffect(edgePull) {
        if (edgePull != 0f) {
            delay(600)
            edgePull = 0f
        }
    }
    val transitionPull = remember(edgePullThreshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Gate on the *settled* slot, not currentPage: during one
                // continuous drag currentPage already flips to the transition
                // slot the moment the pager reaches it, and the remaining
                // deltas would arm the pull and skip the transition page
                // entirely. A new gesture starting on the settled transition
                // slot is what confirms the chapter turn.
                val slot = pagerState.settledPage
                val atEnd = slot - latestPrevSlot >= latestDisplayCount
                val atStart = slot < latestPrevSlot
                if (!atEnd && !atStart) return Offset.Zero
                // Direction the pager cannot move in from this edge slot
                // (reading-forward at the end, backward at the start).
                val furtherSign = if (atEnd) (if (rtl) 1f else -1f) else (if (rtl) -1f else 1f)
                if (available.x * furtherSign <= 0f) return Offset.Zero
                edgePull += available.x
                if (abs(edgePull) >= edgePullThreshold) {
                    edgePull = 0f
                    val target = if (atEnd) latestState.nextChapter else latestState.prevChapter
                    target?.let { viewModel.openChapter(it.id) }
                    return Offset.Zero
                }
                return available
            }
        }
    }
    CompositionLocalProvider(
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().nestedScroll(transitionPull),
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
        ) { slot ->
            val contentIndex = slot - prevSlot
            if (contentIndex < 0 || contentIndex >= displayPages.size) {
                val isNext = contentIndex >= displayPages.size
                val chapter = if (isNext) state.nextChapter else state.prevChapter
                if (chapter != null) {
                    Box(Modifier.fillMaxSize()) {
                        // The tap zones own the whole page (webui parity):
                        // zones sit under the content; a turn in the same
                        // direction confirms, a turn back cancels.
                        TapZoneOverlay(
                            layout = state.tapZones,
                            rtl = rtl,
                            onPrev = if (isNext) {
                                { pagerScope.launch { pagerState.animateScrollToPage(slot - 1) } }
                            } else {
                                { viewModel.openChapter(chapter.id) }
                            },
                            onMenu = { viewModel.toggleOverlay() },
                            onNext = if (isNext) {
                                { viewModel.openChapter(chapter.id) }
                            } else {
                                { pagerScope.launch { pagerState.animateScrollToPage(slot + 1) } }
                            },
                        )
                        TransitionPage(
                            chapter = chapter,
                            isNext = isNext,
                            currentChapter = state.currentChapter,
                        )
                    }
                }
                return@HorizontalPager
            }
            val display = displayPages[contentIndex]
            // Priority follows what the eye sees: the settled page outranks its neighbours.
            val pagePriority = if (display.pages.any { it.index.toInt() == state.index }) {
                ImageEnhancer.PRIORITY_VISIBLE
            } else {
                ImageEnhancer.PRIORITY_ADJACENT
            }
            if (display.pages.size == 2) {
                SpreadPage(
                    pages = display.pages,
                    urlFor = { viewModel.pageImageUrl(it) },
                    apiKey = state.profile?.apiKey,
                    tapZones = state.tapZones,
                    rtl = rtl,
                    colorFilter = readerColorFilter(state),
                    priority = pagePriority,
                    onTapLeft = pageBack,
                    onTapCenter = { viewModel.toggleOverlay() },
                    onTapRight = pageForward,
                )
            } else {
                ReaderPage(
                    page = display.page,
                    crop = display.crop,
                    url = viewModel.pageImageUrl(display.page),
                    apiKey = state.profile?.apiKey,
                    tapZones = state.tapZones,
                    rtl = rtl,
                    pageScale = state.scale,
                    doubleTapZoom = state.doubleTapZoom,
                    colorFilter = readerColorFilter(state),
                    priority = pagePriority,
                    onZoomChange = { zoomed = it },
                    onTapLeft = pageBack,
                    onTapCenter = { viewModel.toggleOverlay() },
                    onTapRight = pageForward,
                )
            }
        }
    }
}

/** Chapter transition slot (webui TransitionPage / Komikku ChapterTransition
 *  parity): "finished / next chapter" (or "previous / current") sections
 *  with a gap warning between them. There are no buttons — the tap zones
 *  own the page, so the confirm is another turn in the same direction and
 *  the cancel is a turn back. */
@Composable
private fun TransitionPage(
    chapter: ChapterWithDownload,
    isNext: Boolean,
    currentChapter: ChapterWithDownload?,
) {
    val skipped = missingBetween(
        if (isNext) currentChapter else chapter,
        if (isNext) chapter else currentChapter,
    )
    // The reader draws straight onto a black canvas: there is no Surface to
    // seed LocalContentColor, whose default is black (invisible text).
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (isNext) {
            TransitionSection(
                label = stringResource(R.string.reader_transition_finished),
                chapter = currentChapter,
            )
            TransitionGapWarning(skipped)
            TransitionSection(
                label = stringResource(R.string.reader_next_chapter),
                chapter = chapter,
            )
        } else {
            TransitionSection(
                label = stringResource(R.string.reader_prev_chapter),
                chapter = chapter,
            )
            TransitionGapWarning(skipped)
            TransitionSection(
                label = stringResource(R.string.reader_transition_current),
                chapter = currentChapter,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.reader_transition_turn_to_continue),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    }
}

/** Label + chapter title (+ first scanlator), Komikku ChapterText parity. */
@Composable
private fun TransitionSection(label: String, chapter: ChapterWithDownload?) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        chapter?.let { chapterCardLabel(it) } ?: stringResource(R.string.reader_chapter),
        style = MaterialTheme.typography.titleLarge,
        maxLines = 5,
        overflow = TextOverflow.Ellipsis,
    )
    chapter?.scanlators?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TransitionGapWarning(skipped: Int) {
    if (skipped <= 0) return
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.reader_chapters_missing, skipped),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(16.dp))
}

private fun chapterCardLabel(chapter: ChapterWithDownload): String =
    chapterLabel(chapter.volumeNumber, chapter.chapterNumber, chapter.title)

/** Number of chapters skipped between two neighbours (Aidoku
 *  SKIPPING_CHAPTERS parity); 0 when the numbers are consecutive or
 *  missing. */
private fun missingBetween(a: ChapterWithDownload?, b: ChapterWithDownload?): Int {
    val x = a?.chapterNumber ?: return 0
    val y = b?.chapterNumber ?: return 0
    val delta = y - x
    return if (delta > 1.01f) (delta - 1f).toInt() else 0
}

// ---------------------------------------------------------------------------
// Vertical reader (webtoon + continuous)
// ---------------------------------------------------------------------------

@Composable
private fun VerticalReaderBody(state: ReaderUiState, viewModel: ReaderViewModel) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.index)
    // Switching chapters reuses this list: jump to the resumed page.
    LaunchedEffect(state.chapterId) {
        if (state.index > 0 && state.index < state.pages.size) {
            listState.scrollToItem(state.index)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { viewModel.setIndex(it) }
    }
    // Vertical scrolling is the most GPU-hungry case (a strip can be several screens tall):
    // park native tile submission while the list moves.
    LaunchedEffect(listState.isScrollInProgress) {
        viewModel.setUpscalePaused(listState.isScrollInProgress)
    }
    // Prose has no page boundary to finish on, so reaching the end of the
    // scrolling column is the end-of-chapter signal (the paged column track
    // reports its own last spread).
    LaunchedEffect(listState, state.textChapter) {
        if (!state.textChapter) return@LaunchedEffect
        snapshotFlow { !listState.canScrollForward }
            .distinctUntilChanged()
            .collect { atEnd -> if (atEnd) viewModel.markTextChapterRead() }
    }
    LaunchedEffect(state.seekRequest) {
        if (state.seekRequest > 0 && state.index in 0 until state.pages.size) {
            listState.scrollToItem(state.index)
        }
    }
    // Continuous modes have no pages to turn: a volume-key press scrolls one
    // viewport instead, the amount a screen-sized step would reveal. Always
    // down for "next" — vertical flow does not mirror with the direction.
    val scrollScope = rememberCoroutineScope()
    VolumeKeyPagingEffect(
        enabled = state.volumeKeyPaging && !state.overlay && !state.chapterSheet,
        rtl = false,
    ) { page ->
        val step = listState.layoutInfo.viewportSize.height * 0.9f
        scrollScope.launch {
            listState.animateScrollBy(if (page == VolumeKeyPage.Next) step else -step)
        }
    }
    // webtoon gap 0 / continuous 12dp (Aidoku webtoon vs continuous).
    // Prose has no page seams, so a text chapter always reads flush.
    val gap = if (!state.textChapter && state.mode == ReadingMode.CONTINUOUS) 12.dp else 0.dp

    val density = LocalDensity.current
    val pullThreshold = with(density) { 120.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val nextChapter = state.nextChapter
    val nestedScroll = remember(nextChapter) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (nextChapter == null) return Offset.Zero
                if (!listState.canScrollForward && available.y < 0f) {
                    pull += available.y / 2.5f
                    return available
                }
                if (pull != 0f && available.y > 0f) {
                    pull = (pull + available.y / 2.5f).coerceAtMost(0f)
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= -pullThreshold) nextChapter?.let { viewModel.openChapter(it.id) }
                pull = 0f
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(pull) {
        if (pull != 0f) {
            delay(600)
            pull = 0f
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(nestedScroll),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(state.pages.size, key = { state.pages[it].index }) { position ->
                val page = state.pages[position]
                Box(
                    Modifier
                        .fillMaxWidth()
                        // No ripple: flashing the whole page on every tap is noise.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { viewModel.toggleOverlay() },
                ) {
                    val text = page.pageText()
                    if (text != null) {
                        TextPageContent(text, fillWidth = true, scrollable = false)
                    } else {
                        ReaderImage(
                            url = viewModel.pageImageUrl(page),
                            apiKey = state.profile?.apiKey,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                            colorFilter = readerColorFilter(state),
                            // A strip is width-bound: only the horizontal ratio says how much the
                            // screen enlarges it (its height is the whole scroll extent).
                            scaleMode = DisplayScaleMode.WIDTH,
                            priority = if (position == state.index) {
                                ImageEnhancer.PRIORITY_VISIBLE
                            } else {
                                ImageEnhancer.PRIORITY_ADJACENT
                            },
                            srcWidth = (page.width ?: 0L).toInt(),
                            srcHeight = (page.height ?: 0L).toInt(),
                            // The strip's own bitmap height is what sizes this LazyColumn item,
                            // so it must not collapse while the 2x request is decoding.
                            reserveHeightWhileLoading = true,
                        )
                        // A strip's box wraps its bitmap, so the chip rides the bottom of each
                        // page and scrolls with it instead of floating over the viewport.
                        UpscaleBadge(
                            url = viewModel.pageImageUrl(page),
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }
            if (nextChapter != null) {
                item(key = "next-chapter-footer") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            when {
                                pull <= -pullThreshold -> stringResource(R.string.reader_release_next_chapter)
                                pull < 0f -> stringResource(R.string.reader_keep_pulling_next)
                                else -> stringResource(R.string.reader_end_of_chapter)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { viewModel.openChapter(nextChapter.id) }) {
                            Text(stringResource(R.string.reader_next_chapter_label, chapterCardLabel(nextChapter)))
                        }
                    }
                }
            }
        }
        // The drag rail tracks image pages; prose flows, so it stays out.
        if (!state.textChapter) {
            VerticalScrollBar(
                state = state,
                listState = listState,
                onNextChapter = state.nextChapter?.let { ch -> { viewModel.openChapter(ch.id) } },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** Right-edge drag rail (preview 08, webui reader scrollbar / Aidoku
 *  webtoon scrubber parity): 24dp hot zone, a slim thumb, and a page
 *  bubble that follows the drag. */
@Composable
private fun VerticalScrollBar(
    state: ReaderUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onNextChapter: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val total = state.pages.size
    if (total <= 1) return
    val info = listState.layoutInfo
    var dragging by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    val liveFraction = remember(listState.firstVisibleItemIndex, info.visibleItemsInfo.size) {
        val first = listState.firstVisibleItemIndex
        (first.toFloat() / (total - 1).coerceAtLeast(1))
    }
    val shown = if (dragging) fraction else liveFraction
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxHeight().width(24.dp)) {
        val trackHeight = maxHeight
        val thumbHeight = (trackHeight * 0.22f).coerceAtLeast(36.dp)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(total) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; fraction = shown },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, dragAmount ->
                        change.consume()
                        val range = (trackHeight - thumbHeight).toPx().coerceAtLeast(1f)
                        fraction = (fraction + dragAmount / range).coerceIn(0f, 1f)
                        val target = (fraction * (total - 1)).toInt()
                        scope.launch { listState.scrollToItem(target.coerceIn(0, total - 1)) }
                    }
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .width(3.dp)
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 2.dp)
                    .offset(y = (trackHeight - thumbHeight) * shown)
                    .width(6.dp)
                    .height(thumbHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.55f)),
            )
            // Chapter anchors (preview 08): the rail spans one chapter, so
            // the top notch is this chapter's start and the bottom notch is
            // the next chapter — tapping it continues reading.
            ChapterAnchor(Alignment.TopEnd, null)
            ChapterAnchor(Alignment.BottomEnd, onNextChapter)
        }
        if (dragging) {
            Surface(
                color = Color(0xE6141418),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 40.dp),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(R.string.reader_page_progress, (shown * (total - 1)).toInt() + 1, total),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        state.chapterNumberLabel,
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/** Small notch on the scrolling rail. A clickable notch continues into the
 *  neighbouring chapter, so the rail doubles as a chapter affordance. */
@Composable
private fun BoxScope.ChapterAnchor(alignment: Alignment, onClick: (() -> Unit)?) {
    Box(
        Modifier
            .align(alignment)
            .size(18.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(9.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = if (onClick != null) 0.55f else 0.3f)),
        )
    }
}

// ---------------------------------------------------------------------------
// Pages, images, tap zones
// ---------------------------------------------------------------------------
private fun readerColorFilter(state: ReaderUiState): ColorFilter? {
    if (state.brightness == 1f && !state.grayscale && !state.sepia) return null
    val m = if (state.sepia) {
        val b = state.brightness
        ColorMatrix(
            floatArrayOf(
                0.393f * b, 0.769f * b, 0.189f * b, 0f, 0f,
                0.349f * b, 0.686f * b, 0.168f * b, 0f, 0f,
                0.272f * b, 0.534f * b, 0.131f * b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    } else {
        ColorMatrix().apply {
            if (state.grayscale) setToSaturation(0f)
            timesAssign(ColorMatrix().apply { setToScale(state.brightness, state.brightness, state.brightness, 1f) })
        }
    }
    return ColorFilter.colorMatrix(m)
}

/** A text page: selectable prose laid out with the reader typography.
 *  [scrollable] is off inside the continuous list — a nested scroll there
 *  would trap the gesture instead of scrolling the chapter. */
@Composable
private fun TextPageContent(text: String, fillWidth: Boolean, scrollable: Boolean = true) {
    val typography = LocalReaderTypography.current
    val base = MaterialTheme.typography.bodyLarge
    val size = typography.size
    SelectionContainer {
        Column(
            (if (fillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text,
                style = base.copy(
                    fontFamily = typography.family,
                    fontSize = (base.fontSize.value * size).sp,
                    lineHeight = (base.fontSize.value * size * typography.lineHeight).sp,
                    color = typography.color,
                ),
            )
        }
    }
}

/** Paged prose: the chapter text is cut into viewport-height columns and the
 *  reader turns a screenful at a time, the way the web client pages a book
 *  (`webui/src/pages/reader/TextColumns.tsx`). Two columns from 600dp, three
 *  from 840dp — narrower windows keep the single scrolling column. */
@Composable
private fun PagedTextBody(state: ReaderUiState, viewModel: ReaderViewModel) {
    val text = state.pages.firstOrNull()?.pageText().orEmpty()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val typography = LocalReaderTypography.current
    val base = MaterialTheme.typography.bodyLarge
    val style = base.copy(
        fontFamily = typography.family,
        fontSize = (base.fontSize.value * typography.size).sp,
        lineHeight = (base.fontSize.value * typography.size * typography.lineHeight).sp,
        color = typography.color,
    )
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { viewModel.toggleOverlay() },
    ) {
        val gap = 36.dp
        val padding = 24.dp
        val columns = if (maxWidth >= 840.dp) 3 else 2
        val columnWidth = ((maxWidth - padding * 2 - gap * (columns - 1)) / columns)
            .coerceAtLeast(160.dp)
        val columnHeight = (maxHeight - padding * 2).coerceAtLeast(120.dp)
        val columnTexts = remember(text, style, columnWidth, columnHeight, density) {
            paginateText(text, style, measurer, columnWidth, columnHeight, density)
        }
        val spreads = ((columnTexts.size + columns - 1) / columns).coerceAtLeast(1)
        val pager = rememberPagerState { spreads }
        LaunchedEffect(pager.settledPage, spreads) {
            when {
                pager.currentPage > spreads - 1 -> pager.scrollToPage(spreads - 1)
                else -> viewModel.setTextSpread(pager.settledPage, spreads)
            }
        }
        val pagerScope = rememberCoroutineScope()
        // Paged prose turns a screenful at a time with the volume keys too.
        // The column track never mirrors for RTL, so forward is always
        // spread + 1 whatever the chapter's reading direction.
        VolumeKeyPagingEffect(
            enabled = state.volumeKeyPaging && !state.overlay && !state.chapterSheet,
            rtl = false,
        ) { page ->
            val target = pager.currentPage + if (page == VolumeKeyPage.Next) 1 else -1
            if (target in 0 until spreads) {
                pagerScope.launch { pager.animateScrollToPage(target) }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { spread ->
            Row(
                Modifier.fillMaxSize().padding(horizontal = padding, vertical = padding),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (slot in 0 until columns) {
                    val columnText = columnTexts.getOrNull(spread * columns + slot)
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (columnText != null) {
                            SelectionContainer { Text(columnText, style = style) }
                        }
                    }
                }
            }
        }
    }
}

/** Splits [text] into columns of roughly [columnHeight]: measure once at the
 *  column width, then cut on line boundaries so every column wraps exactly
 *  like the single long page it came from. */
private fun paginateText(
    text: String,
    style: TextStyle,
    measurer: TextMeasurer,
    columnWidth: Dp,
    columnHeight: Dp,
    density: Density,
): List<String> {
    if (text.isEmpty()) return emptyList()
    val layout = measurer.measure(
        text = AnnotatedString(text),
        style = style,
        constraints = Constraints(maxWidth = with(density) { columnWidth.roundToPx() }),
    )
    if (layout.lineCount == 0) return listOf(text)
    val styleLinePx = with(density) { style.lineHeight.toPx() }
    val linePx = styleLinePx.takeIf { it > 0f }
        ?: (layout.getLineBottom(0) - layout.getLineTop(0)).coerceAtLeast(1f)
    val linesPerColumn = (with(density) { columnHeight.toPx() } / linePx).toInt().coerceAtLeast(1)
    val out = ArrayList<String>()
    var start = 0
    while (start < layout.lineCount) {
        val end = (start + linesPerColumn - 1).coerceAtMost(layout.lineCount - 1)
        val from = layout.getLineStart(start)
        val to = layout.getLineEnd(end, visibleEnd = true)
        if (to > from) out += text.substring(from, to)
        start = end + 1
    }
    return out
}

@Composable
private fun ReaderPage(
    page: Page,
    crop: Int,
    url: String,
    apiKey: String?,
    tapZones: String,
    rtl: Boolean,
    pageScale: String,
    doubleTapZoom: Float,
    colorFilter: ColorFilter?,
    priority: Int,
    onZoomChange: (Boolean) -> Unit,
    onTapLeft: () -> Unit,
    onTapCenter: () -> Unit,
    onTapRight: () -> Unit,
) {
    var scale by remember(page.index) { mutableFloatStateOf(1f) }
    var offset by remember(page.index) { mutableStateOf(Offset.Zero) }
    // Server-side page metadata, so the upscaling decision needs no bounds decode on the common path.
    val srcWidth = (page.width ?: 0L).toInt()
    val srcHeight = (page.height ?: 0L).toInt()

    Box(Modifier.fillMaxSize()) {
        val textPage = page.pageText()
        if (pageScale != "fit" && crop == 0) {
            val scrollModifier = when (pageScale) {
                "width" -> Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                "height" -> Modifier.fillMaxHeight()
                else -> Modifier.fillMaxSize()
            }
            val contentScale = when (pageScale) {
                "width" -> ContentScale.FillWidth
                "height" -> ContentScale.FillHeight
                else -> ContentScale.None
            }
            // Zones sit UNDER the content: plain pages don't consume taps so
            // they fall through, but a failed page's retry button stays
            // clickable instead of being covered by the zones.
            TapZoneOverlay(
                layout = tapZones,
                rtl = rtl,
                onPrev = onTapLeft,
                onMenu = onTapCenter,
                onNext = onTapRight,
            )
            Box(Modifier.fillMaxSize()) {
                if (textPage != null) {
                    TextPageContent(textPage, fillWidth = false)
                } else {
                    ReaderImage(
                        url = url,
                        apiKey = apiKey,
                        modifier = scrollModifier,
                        contentScale = contentScale,
                        colorFilter = colorFilter,
                        scaleMode = DisplayScaleMode.fromReaderScale(pageScale),
                        priority = priority,
                        srcWidth = srcWidth,
                        srcHeight = srcHeight,
                    )
                }
                // Pinned to the viewport, not the scrolled strip, so a width/height-fit page keeps
                // its chip visible while the user drags through it.
                if (textPage == null) {
                    UpscaleBadge(url, Modifier.align(Alignment.BottomEnd))
                }
            }
            return@Box
        }
        // KMReader parity: one modifier hosts both detectors — taps resolve
        // their zone from the touch position, a double tap toggles between 1x
        // and the configured multiplier, and pinch pans while zoomed. The
        // applied scale is animated so the double-tap glides instead of
        // snapping (pinch deltas are tiny, so the spring still tracks 1:1).
        val appliedScale by animateFloatAsState(
            targetValue = if (scale > 1.01f) scale else 1f,
            label = "readerZoom",
        )
        val imageModifier = if (crop == 0) {
            Modifier
                .fillMaxSize()
                .pointerInput(page.index, tapZones, rtl, doubleTapZoom) {
                    coroutineScope {
                        launch {
                            detectTapGestures(
                                onTap = { pos ->
                                    // A zoomed page is panned, never turned.
                                    if (scale > 1.01f) return@detectTapGestures
                                    when (tapZoneAction(tapZones, rtl, pos.x / size.width, pos.y / size.height)) {
                                        TapZoneAction.PREV -> onTapLeft()
                                        TapZoneAction.MENU -> onTapCenter()
                                        TapZoneAction.NEXT -> onTapRight()
                                        null -> Unit
                                    }
                                },
                                onDoubleTap = {
                                    scale = if (scale > 1.01f) {
                                        offset = Offset.Zero
                                        1f
                                    } else {
                                        doubleTapZoom.coerceIn(1f, 8f)
                                    }
                                    onZoomChange(scale > 1.01f)
                                },
                            )
                        }
                        launch {
                            // detectTransformGestures would consume every
                            // one-finger pan, so a horizontal swipe started on
                            // the page never reached the pager and only drags
                            // on the black margins turned pages. Handle pinch
                            // and zoomed-page panning here and leave plain
                            // 1x drags unconsumed for the pager.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.count { it.pressed }
                                    if (pressed >= 2) {
                                        // Pinch always belongs to the page.
                                        scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                        val pan = event.calculatePan()
                                        offset = if (scale > 1f) offset + pan else Offset.Zero
                                        onZoomChange(scale > 1.01f)
                                        event.changes.forEach { it.consume() }
                                    } else if (scale > 1.01f) {
                                        // Zoomed page: one-finger drag pans it
                                        // instead of turning the page.
                                        offset += event.calculatePan()
                                        event.changes.forEach { it.consume() }
                                    }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = appliedScale
                    scaleY = appliedScale
                    translationX = offset.x
                    translationY = offset.y
                }
        } else {
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    scaleX = 2f * scale
                    scaleY = scale
                    translationX = if (crop == 1) -size.width / 2f else size.width / 2f
                    translationY = 0f
                }
        }
        if (textPage != null) {
            TextPageContent(textPage, fillWidth = false)
            // Text pages never zoom, so they keep the simple zone overlay.
            TapZoneOverlay(
                layout = tapZones,
                rtl = rtl,
                onPrev = onTapLeft,
                onMenu = onTapCenter,
                onNext = onTapRight,
            )
        } else {
            ReaderImage(
                url = url,
                apiKey = apiKey,
                modifier = imageModifier,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                // A split wide page is painted stretched 2x horizontally before being clipped to
                // its visible half, so the trigger must see that doubled scale.
                scaleMode = if (crop == 0) DisplayScaleMode.fromReaderScale(pageScale) else DisplayScaleMode.SPLIT,
                priority = priority,
                srcWidth = srcWidth,
                srcHeight = srcHeight,
            )
            // A split page paints 2x-then-clipped, so the chip stays on the viewport corner
            // rather than inside the zoomed layer where it could be cropped out of sight.
            UpscaleBadge(url, Modifier.align(Alignment.BottomEnd))
        }
    }
}

private enum class TapZoneAction { PREV, MENU, NEXT }

/** Position-based resolution of the tap zones, mirroring TapZoneOverlay's
 *  geometry (single detector, so zone taps and double-tap zoom share one
 *  surface and never double-fire). */
private fun tapZoneAction(layout: String, rtl: Boolean, x: Float, y: Float): TapZoneAction? = when (layout) {
    "off" -> null
    "kindle" -> when {
        y < 0.25f -> TapZoneAction.MENU
        x < 0.25f -> if (rtl) TapZoneAction.NEXT else TapZoneAction.PREV
        else -> if (rtl) TapZoneAction.PREV else TapZoneAction.NEXT
    }
    "edge" -> {
        val leftIsPrev = !rtl
        when {
            x < 0.15f -> if (leftIsPrev) TapZoneAction.PREV else TapZoneAction.NEXT
            x > 0.85f -> if (leftIsPrev) TapZoneAction.NEXT else TapZoneAction.PREV
            else -> TapZoneAction.MENU
        }
    }
    else -> {
        val leftIsPrev = (layout != "rl") != rtl
        when {
            x < 0.3f -> if (leftIsPrev) TapZoneAction.PREV else TapZoneAction.NEXT
            x > 0.7f -> if (leftIsPrev) TapZoneAction.NEXT else TapZoneAction.PREV
            else -> TapZoneAction.MENU
        }
    }
}

/** Tap zones (webui TapZoneLayout parity): lr = prev/menu/next thirds,
 *  rl mirrored, edge narrows the sides to 15%, kindle reserves the top
 *  quarter for the menu, off disables. `rtl` mirrors left/right so prev
 *  always means "previous in reading order". */
@Composable
private fun TapZoneOverlay(
    layout: String,
    rtl: Boolean,
    onPrev: () -> Unit,
    onMenu: () -> Unit,
    onNext: () -> Unit,
) {
    if (layout == "off") return
    when (layout) {
        "kindle" -> Column(Modifier.fillMaxSize()) {
            ZoneBox(Modifier.weight(0.25f).fillMaxWidth(), onMenu)
            Row(Modifier.weight(0.75f).fillMaxSize()) {
                val leftWeight = if (rtl) 0.25f else 0.75f
                val rightWeight = if (rtl) 0.75f else 0.25f
                ZoneBox(Modifier.weight(leftWeight).fillMaxSize(), if (rtl) onNext else onPrev)
                ZoneBox(Modifier.weight(rightWeight).fillMaxSize(), if (rtl) onPrev else onNext)
            }
        }
        "edge" -> {
            val leftIsPrev = !rtl
            Row(Modifier.fillMaxSize()) {
                ZoneBox(Modifier.weight(0.15f).fillMaxSize(), if (leftIsPrev) onPrev else onNext)
                ZoneBox(Modifier.weight(0.7f).fillMaxSize(), onMenu)
                ZoneBox(Modifier.weight(0.15f).fillMaxSize(), if (leftIsPrev) onNext else onPrev)
            }
        }
        else -> {
            val baseLeftIsPrev = layout != "rl"
            val leftIsPrev = if (rtl) !baseLeftIsPrev else baseLeftIsPrev
            Row(Modifier.fillMaxSize()) {
                ZoneBox(Modifier.weight(0.3f).fillMaxSize(), if (leftIsPrev) onPrev else onNext)
                ZoneBox(Modifier.weight(0.4f).fillMaxSize(), onMenu)
                ZoneBox(Modifier.weight(0.3f).fillMaxSize(), if (leftIsPrev) onNext else onPrev)
            }
        }
    }
}

/** Tap-zone hit box without the default ripple: the bounded highlight
 *  flashed a translucent bar across the zone (most visible on the tall
 *  centre menu zone), so paging stays visually silent (KMReader parity). */
@Composable
private fun ZoneBox(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    )
}

@Composable
private fun TapZonePreview(layout: String, rtl: Boolean) {
    val prevColor = Color(0xFFA4706F)
    val nextColor = Color(0xFF6F9A80)
    val menuColor = Color(0xFF6F86A8)
    Box(
        Modifier
            .size(width = 84.dp, height = 120.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
    ) {
        when (layout) {
            "kindle" -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.25f).fillMaxWidth().background(menuColor))
                Row(Modifier.weight(0.75f).fillMaxSize()) {
                    val lw = if (rtl) 0.25f else 0.75f
                    val rw = if (rtl) 0.75f else 0.25f
                    Box(Modifier.weight(lw).fillMaxSize().background(if (rtl) nextColor else prevColor))
                    Box(Modifier.weight(rw).fillMaxSize().background(if (rtl) prevColor else nextColor))
                }
            }
            "edge" -> {
                val leftIsPrev = !rtl
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.15f).fillMaxSize().background(if (leftIsPrev) prevColor else nextColor))
                    Box(Modifier.weight(0.7f).fillMaxSize().background(menuColor))
                    Box(Modifier.weight(0.15f).fillMaxSize().background(if (leftIsPrev) nextColor else prevColor))
                }
            }
            else -> {
                val baseLeftIsPrev = layout != "rl"
                val leftIsPrev = if (rtl) !baseLeftIsPrev else baseLeftIsPrev
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.3f).fillMaxSize().background(if (leftIsPrev) prevColor else nextColor))
                    Box(Modifier.weight(0.4f).fillMaxSize().background(menuColor))
                    Box(Modifier.weight(0.3f).fillMaxSize().background(if (leftIsPrev) nextColor else prevColor))
                }
            }
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(stringResource(R.string.reader_legend_prev_page), color = prevColor, style = MaterialTheme.typography.labelSmall)
        Text(stringResource(R.string.reader_legend_menu), color = menuColor, style = MaterialTheme.typography.labelSmall)
        Text(stringResource(R.string.reader_legend_next_page), color = nextColor, style = MaterialTheme.typography.labelSmall)
    }
}

/** Double-page spread: two pages side by side already ordered for the
 *  reading direction; landscape pages never get here. */
@Composable
private fun SpreadPage(
    pages: List<Page>,
    urlFor: (Page) -> String,
    apiKey: String?,
    tapZones: String,
    rtl: Boolean,
    colorFilter: ColorFilter?,
    priority: Int,
    onTapLeft: () -> Unit,
    onTapCenter: () -> Unit,
    onTapRight: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Under the pages, for the same reason as the single-page path: a
        // failed page's retry button must win over the zone hit boxes.
        TapZoneOverlay(
            layout = tapZones,
            rtl = rtl,
            onPrev = onTapLeft,
            onMenu = onTapCenter,
            onNext = onTapRight,
        )
        Row(Modifier.fillMaxSize()) {
            pages.forEachIndexed { index, page ->
                // Push each page image towards the centre gutter of its
                // half so the two pages of a spread join in the middle
                // (KMReader does the same; RTL flips Start/End
                // automatically, keeping the seam in the middle).
                val edgeAlignment = if (index == 0) Alignment.CenterEnd else Alignment.CenterStart
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = edgeAlignment) {
                    val textPage = page.pageText()
                    if (textPage != null) {
                        TextPageContent(textPage, fillWidth = false)
                    } else {
                        ReaderImage(
                            url = urlFor(page),
                            apiKey = apiKey,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            alignment = edgeAlignment,
                            colorFilter = colorFilter,
                            scaleMode = DisplayScaleMode.FIT,
                            priority = priority,
                            srcWidth = (page.width ?: 0L).toInt(),
                            srcHeight = (page.height ?: 0L).toInt(),
                        )
                        // One chip per half, pushed to the outer edge (the gutter side is where
                        // the two pages join, so a chip there would look like it belongs to the
                        // other page). Start/End mirror with RTL along with edgeAlignment.
                        UpscaleBadge(
                            url = urlFor(page),
                            modifier = Modifier.align(
                                if (index == 0) Alignment.BottomStart else Alignment.BottomEnd,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** The enhancement-cache key for a page URL under the current config, or null when upscaling is
 *  off. Pure function of the URL and the denoise/precision variant, so callers only pass the URL. */
@Composable
private fun upscaleBadgeKey(url: String): String? {
    val upscale = LocalReaderUpscale.current
    val key = remember(url, upscale.config) {
        EnhancementCache.cacheKey(url, upscale.config.denoise, upscale.config.precision)
    }
    return if (upscale.config.enabled) key else null
}

/**
 * Page-level upscaling indicator: a "2x" chip with a spinner while the engine holds the page. It is
 * a one-way hint — it explains the wait, then fades out as the enhanced bitmap swaps in. Completed
 * pages carry no permanent label: `readyKeys` is intentionally not rendered, so an already
 * enhanced page looks like a normal page. Pages the decision matrix skipped, and pages under a
 * disabled config, never show it.
 */
@Composable
private fun UpscaleBadge(url: String, modifier: Modifier = Modifier) {
    val key = upscaleBadgeKey(url) ?: return
    val upscale = LocalReaderUpscale.current
    val processing = key in upscale.processingKeys
    // Target 0f as soon as the key leaves the queue/engine; 0-alpha chips draw nothing, so the
    // animation can finish before the surface is dropped from the composition.
    val alpha by animateFloatAsState(
        targetValue = if (processing) 1f else 0f,
        animationSpec = tween(durationMillis = if (processing) 120 else 260),
        label = "upscaleBadgeAlpha",
    )
    if (alpha <= 0f) return
    Surface(
        modifier = modifier.padding(10.dp).alpha(alpha),
        shape = RoundedCornerShape(6.dp),
        // inverseSurface/inverseOnSurface keeps the chip readable over white manga pages in the
        // light theme and over black ones in the dark theme.
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            Text(
                stringResource(R.string.reader_upscale_badge),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun ReaderImage(
    url: String,
    apiKey: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    alignment: Alignment = Alignment.Center,
    colorFilter: ColorFilter? = null,
    /** How the reader paints this page; decides the upscaling trigger's painted-scale axis. */
    scaleMode: DisplayScaleMode = DisplayScaleMode.FIT,
    priority: Int = ImageEnhancer.PRIORITY_VISIBLE,
    /** Page dimensions from the server metadata; 0 lets the decoder run a bounds pass. */
    srcWidth: Int = 0,
    srcHeight: Int = 0,
    /** Keeps the frame's height while the request is between states (see the heightIn note
     *  below); required wherever the image's own height is what sizes the parent, i.e. webtoon
     *  strips inside a LazyColumn. No-op in the fixed-size paged/spread slots. */
    reserveHeightWhileLoading: Boolean = false,
) {
    val context = LocalContext.current
    val upscale = LocalReaderUpscale.current
    val density = LocalDensity.current
    var retryKey by remember(url) { mutableIntStateOf(0) }
    // Height this page actually rendered at, in px. Coil swaps the painter state to Loading while it
    // decodes the enhanced request, and a Loading state has no intrinsic size: in a webtoon strip the
    // item would collapse to zero height for that frame, and the LazyColumn — anchored by visible
    // item + pixel offset — re-anchors, which is what reads as "the scroll position jumped after the
    // 2x image replaced the source". Reserving the last rendered height keeps the item's geometry
    // identical across the swap, so nothing above the viewport moves.
    var renderedHeight by remember(url) { mutableIntStateOf(0) }
    val heightReserve = if (reserveHeightWhileLoading && renderedHeight > 0) {
        Modifier.heightIn(min = with(density) { renderedHeight.toDp() })
    } else {
        Modifier
    }
    // The enhancement-cache key is a pure function of the URL and the variant config, so the
    // reader does not carry it around and the composable stays stateless across page identity.
    val enhancementKey = upscaleBadgeKey(url)
    val enhanced = enhancementKey != null && enhancementKey in upscale.readyKeys
    // Rebuilding the request on retryKey gives Coil a fresh request object,
    // so retry actually re-runs the fetch (error results are never cached).
    // `enhanced` is part of the request identity through memoryCacheKeyExtra,
    // so the finished page swaps in without a manual invalidation.
    val model = remember(url, retryKey, upscale.config, enhanced, scaleMode, priority, srcWidth, srcHeight) {
        ImageRequest.Builder(context).data(url).apply {
            apiKey?.let { httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $it").build()) }
            if (upscale.config.enabled && enhancementKey != null) {
                EnhancementDecoder.applyTo(
                    builder = this,
                    config = upscale.config,
                    pageUrl = url,
                    priority = priority,
                    scaleMode = scaleMode,
                    srcWidth = srcWidth,
                    srcHeight = srcHeight,
                )
                // Coil's memory cache would otherwise keep serving the source bitmap for the
                // request that is meant to show the enhanced one; 2x bitmaps belong in the
                // enhancement cache and its own memory LRU.
                memoryCacheKeyExtra("upscale", "${upscale.config.variant}:enhanced=$enhanced")
                memoryCachePolicy(CachePolicy.DISABLED)
            }
        }.build()
    }
    SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier
            .then(heightReserve)
            .onSizeChanged { if (it.height > 0) renderedHeight = it.height },
        contentScale = contentScale,
        alignment = alignment,
        colorFilter = colorFilter,
        loading = {
            // Komikku-style: centered, always-rotating progress indicator.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        },
        error = {
            // Komikku-style error page: icon + message + retry, centered.
            // Failed pages keep the slot's aspect box so the pager does not
            // jump; the retry button sits above the tap zones (preview 07).
            val errorDetail = (painter.state as? AsyncImagePainter.State.Error)
                ?.result?.throwable?.message
                ?.takeIf { it.isNotBlank() }
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.reader_page_load_failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (errorDetail != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            errorDetail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { retryKey++ }) { Text(stringResource(R.string.reader_retry)) }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Overlay: top bar, chapter navigator, bottom action bar
// ---------------------------------------------------------------------------

@Composable
private fun ReaderOverlay(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    landscape: Boolean,
) {
    var showQuickPanel by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // KMReader-style floating chrome, but Material You tinted: capsules use
    // the theme's surfaceContainerHigh / onSurface instead of the (usually
    // pure black) canvas colour, so they follow the app theme on any reader
    // background. A subtle outline keeps a capsule visible on same-coloured
    // pages, and the gaps between capsules stay clear so taps there still
    // turn pages.
    val chromeContent = MaterialTheme.colorScheme.onSurface
    val chromeScrim = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
    val capsuleShape = RoundedCornerShape(24.dp)
    val capsuleBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        // ---- top: left capsule (back + incognito + title) ----
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    // Hug the content, but shrink (title ellipsizes) when space is tight
                    .weight(1f, fill = false)
                    .clip(capsuleShape)
                    .background(chromeScrim)
                    .border(capsuleBorder, capsuleShape)
                    // Swallow stray taps inside the capsule; taps in the gap still turn pages
                    .pointerInput(Unit) { detectTapGestures { } }
                    // Asymmetric padding: the back IconButton already contributes its own
                    // internal padding on the left, so the trailing side gets 16 dp
                    .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.reader_back), tint = chromeContent)
                }
                if (state.incognito) {
                    Icon(
                        Icons.Outlined.VisibilityOff,
                        contentDescription = stringResource(R.string.reader_incognito),
                        tint = chromeContent.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Column(Modifier.padding(start = if (state.incognito) 8.dp else 0.dp)) {
                    Text(
                        if (state.chapterTitle.isNotEmpty()) state.chapterTitle
                        else stringResource(R.string.reader_chapter),
                        color = chromeContent,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.chapterNumberLabel.isNotEmpty()) {
                            Text(
                                state.chapterNumberLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = chromeContent.copy(alpha = 0.7f),
                            )
                            Text(" · ", style = MaterialTheme.typography.labelSmall, color = chromeContent.copy(alpha = 0.4f))
                        }
                        Text(
                            if (state.textChapter) {
                                stringResource(R.string.reader_text_progress, state.textProgress)
                            } else {
                                stringResource(R.string.reader_page_progress, state.index + 1, state.pages.size)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = chromeContent.copy(alpha = 0.7f),
                        )
                        if (!state.vertical && !state.textChapter) {
                            Text(" · ", style = MaterialTheme.typography.labelSmall, color = chromeContent.copy(alpha = 0.4f))
                            Text(
                                stringResource(
                                    if (!state.doubleSpread(landscape)) R.string.reader_layout_single
                                    else R.string.reader_layout_double,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = chromeContent.copy(alpha = 0.7f),
                            )
                        }
                        if (state.dir == "rtl") {
                            Text(" · ", style = MaterialTheme.typography.labelSmall, color = chromeContent.copy(alpha = 0.4f))
                            Text(
                                stringResource(R.string.reader_dir_rtl),
                                style = MaterialTheme.typography.labelSmall,
                                color = chromeContent.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }

        // ---- bottom: centred action capsule, progress capsule pinned last ----
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Row(
                    Modifier
                        .clip(capsuleShape)
                        .background(chromeScrim)
                        .border(capsuleBorder, capsuleShape)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlayAction(Icons.Outlined.FormatListNumbered, stringResource(R.string.reader_chapters), tint = chromeContent) {
                        viewModel.openChapterSheet(true)
                    }
                    OverlayAction(
                        if (state.vertical) Icons.Outlined.ViewCarousel else Icons.Outlined.ViewAgenda,
                        stringResource(R.string.reader_reading_mode),
                        tint = chromeContent,
                        accent = true,
                        dot = state.overridden.contains(ReaderField.MODE),
                        onLongClick = { viewModel.setMode(nextMode(state.mode)) },
                    ) { showQuickPanel = true }
                    OverlayAction(
                        when (state.layout) {
                            PageLayout.SINGLE -> Icons.Outlined.ViewAgenda
                            PageLayout.DOUBLE -> Icons.Outlined.ViewColumn
                            PageLayout.AUTO -> Icons.Outlined.ViewColumn
                        },
                        stringResource(R.string.reader_layout),
                        tint = chromeContent,
                        dot = state.overridden.contains(ReaderField.LAYOUT),
                    ) { viewModel.setLayout(nextLayout(state.layout)) }
                    OverlayAction(
                        Icons.Outlined.SwapHoriz,
                        stringResource(R.string.reader_direction),
                        tint = chromeContent,
                        dot = state.overridden.contains(ReaderField.DIR),
                    ) { viewModel.setDir(nextDir(state.dirPreference)) }
                    OverlayAction(Icons.Outlined.Tune, stringResource(R.string.reader_settings), tint = chromeContent) { showSettings = true }
                }
            }
            // Chapter progress (prev/next + slider) sits at the very bottom,
            // closest to the thumb (KMReader bottom overlay order).
            ChapterNavigator(
                state = state,
                viewModel = viewModel,
                contentColor = chromeContent,
                containerColor = chromeScrim,
                border = capsuleBorder,
            )
        }
    }

    if (showQuickPanel) {
        QuickPanelSheet(state, viewModel) { showQuickPanel = false }
    }
    if (showSettings) {
        ReaderSettingsSheet(state, viewModel) { showSettings = false }
    }
}

@Composable
private fun OverlayAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    accent: Boolean = false,
    dot: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(onClick, onLongClick) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onLongPress = { onLongClick?.invoke() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (accent) MaterialTheme.colorScheme.primary else tint,
                )
            }
            if (dot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

private fun nextLayout(layout: PageLayout): PageLayout = when (layout) {
    PageLayout.SINGLE -> PageLayout.DOUBLE
    PageLayout.DOUBLE -> PageLayout.AUTO
    PageLayout.AUTO -> PageLayout.SINGLE
}

private fun nextDir(pref: String): String = when (pref) {
    "ltr" -> "rtl"
    "rtl" -> "default"
    else -> "ltr"
}

/** Long-pressing the mode key flips paged ⇄ webtoon without opening the
 *  panel (preview 07: mode · long-press to switch). */
private fun nextMode(mode: String): String = when (mode) {
    ReadingMode.WEBTOON -> ReadingMode.PAGED
    else -> ReadingMode.WEBTOON
}

/** Chapter navigator (Komikku reader style): three capsules — previous
 *  chapter, a ticked progress slider, next chapter. The slider is reversed
 *  and the arrows swap under RTL. */
@Composable
private fun ChapterNavigator(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    contentColor: Color,
    containerColor: Color,
    border: BorderStroke,
) {
    val rtl = state.dir == "rtl"
    val range = 0f..(state.pages.size - 1).coerceAtLeast(0).toFloat()
    val sliderValue = if (rtl) (range.endInclusive - state.index) else state.index.toFloat()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = containerColor, shape = CircleShape, border = border) {
            IconButton(
                onClick = { state.prevChapter?.let { viewModel.openChapter(it.id) } },
                enabled = state.prevChapter != null,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (rtl) Icons.AutoMirrored.Outlined.NavigateNext else Icons.AutoMirrored.Outlined.NavigateBefore,
                    contentDescription = stringResource(R.string.reader_previous_chapter_desc),
                    tint = if (state.prevChapter != null) contentColor else contentColor.copy(alpha = 0.38f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Surface(
            color = containerColor,
            shape = RoundedCornerShape(24.dp),
            border = border,
            modifier = Modifier.weight(1f),
        ) {
            if (state.textChapter) {
                // Prose has no page granularity worth scrubbing: report how
                // far the reader has got and how many passages remain.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.reader_text_progress, state.textProgress),
                        color = contentColor,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.reader_text_pages, state.pages.size),
                        color = contentColor.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.doubleSpread(false) && !state.vertical) "${state.index + 1}–${(state.index + 2).coerceAtMost(state.pages.size)}"
                    else "${state.index + 1}",
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (state.pages.size > 1) {
                    // Komikku parity: M3 Slider with one step (tick dot) per
                    // page; dragging snaps to whole pages.
                    Slider(
                        value = sliderValue,
                        onValueChange = { raw ->
                            val target = if (rtl) range.endInclusive - raw else raw
                            viewModel.seekTo(target.toInt())
                        },
                        valueRange = range,
                        steps = (state.pages.size - 2).coerceAtLeast(0),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    "/ ${state.pages.size}",
                    color = contentColor.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Surface(color = containerColor, shape = CircleShape, border = border) {
            IconButton(
                onClick = { state.nextChapter?.let { viewModel.openChapter(it.id) } },
                enabled = state.nextChapter != null,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (rtl) Icons.AutoMirrored.Outlined.NavigateBefore else Icons.AutoMirrored.Outlined.NavigateNext,
                    contentDescription = stringResource(R.string.reader_next_chapter_desc),
                    tint = if (state.nextChapter != null) contentColor else contentColor.copy(alpha = 0.38f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chapter drawer (preview 25)
// ---------------------------------------------------------------------------

@Composable
private fun ChapterSheet(state: ReaderUiState, viewModel: ReaderViewModel) {
    var filter by remember { mutableStateOf("all") }
    // Shared per-manga chapter prefs (webui chapterPrefs parity): the
    // toggle persists back to the same entry the detail page reads.
    val descending = state.chapterPrefs.asc

    val unread = state.chapters.count { !it.isRead }
    val downloaded = state.chapters.count { it.isDownloaded || it.hasFinishedDownload }
    val visible = remember(state.chapters, filter, descending) {
        val filtered = when (filter) {
            "unread" -> state.chapters.filter { !it.isRead }
            "downloaded" -> state.chapters.filter { it.isDownloaded || it.hasFinishedDownload }
            else -> state.chapters
        }
        if (descending) filtered.reversed() else filtered
    }
    // Open at the current chapter (Aidoku ScrollViewReader /
    // Komikku rememberLazyListState(initial index) parity).
    val initial = remember(state.chapterId, visible) {
        visible.indexOfFirst { it.id == state.chapterId }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initial)

    AppModalDialog(onDismissRequest = { viewModel.openChapterSheet(false) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reader_chapters), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.reader_chapter_stats,
                            state.chapters.size,
                            state.chapters.count { it.isRead },
                            unread.coerceAtLeast(0),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.toggleChapterAsc() }) {
                    Icon(
                        Icons.Outlined.Sort,
                        contentDescription = stringResource(
                            if (descending) R.string.reader_sort_ascending else R.string.reader_sort_descending,
                        ),
                    )
                }
                IconButton(onClick = { viewModel.openChapterSheet(false) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.reader_close))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text(stringResource(R.string.reader_filter_all)) })
                FilterChip(
                    selected = filter == "unread",
                    onClick = { filter = "unread" },
                    label = { Text(stringResource(R.string.reader_filter_unread, unread)) },
                )
                FilterChip(
                    selected = filter == "downloaded",
                    onClick = { filter = "downloaded" },
                    label = { Text(stringResource(R.string.reader_filter_downloaded, downloaded)) },
                )
            }
            if (visible.any { it.id == state.chapterId }) {
                Text(
                    stringResource(R.string.reader_located_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            when {
                state.chaptersError != null -> {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.chaptersError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = { viewModel.retryChapters() }) { Text(stringResource(R.string.reader_retry)) }
                    }
                }
                visible.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        Text(
                            when (filter) {
                                "unread" -> stringResource(R.string.reader_no_unread_chapters)
                                "downloaded" -> stringResource(R.string.reader_no_downloaded_chapters)
                                else -> stringResource(R.string.reader_no_chapters)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        visible.forEachIndexed { index, chapter ->
                            val prev = visible.getOrNull(index - 1)
                            val gap = missingBetween(if (descending) chapter else prev, if (descending) prev else chapter)
                            if (gap > 0) {
                                item(key = "gap-${chapter.id}") { ChapterGapRow(gap) }
                            }
                            item(key = chapter.id) {
                                ChapterRow(
                                    chapter = chapter,
                                    current = chapter.id == state.chapterId,
                                    onClick = {
                                        viewModel.openChapter(chapter.id)
                                        viewModel.openChapterSheet(false)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChapterGapRow(missing: Int) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚠", color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.reader_chapters_missing, missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChapterRow(chapter: ChapterWithDownload, current: Boolean, onClick: () -> Unit) {
    // No number → no number column at all (detail-page/webui parity), the
    // title takes the full row width instead of squeezing beside an
    // "未命名章节" placeholder.
    val number = chapterNumberLabel(chapter.volumeNumber, chapter.chapterNumber)
    Surface(
        color = if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (number.isNotEmpty()) {
                Text(
                    number,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(52.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    chapter.title?.takeIf { it.isNotBlank() }
                        ?: if (number.isNotEmpty()) stringResource(R.string.reader_chapter_fallback, number)
                        else chapterLabel(chapter.volumeNumber, chapter.chapterNumber, null),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (chapter.isRead) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                val meta = buildList {
                    add(stringResource(R.string.reader_page_count, chapter.pageCount))
                    if (chapter.lastPageRead > 0) add(stringResource(R.string.reader_read_to_page, chapter.lastPageRead + 1))
                }.joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (chapter.isBookmarked) {
                Icon(
                    Icons.Outlined.BookmarkBorder,
                    contentDescription = stringResource(R.string.reader_bookmarked),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (chapter.locked) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.reader_locked),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (chapter.isDownloaded || chapter.hasFinishedDownload) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = stringResource(R.string.reader_downloaded_badge),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            } else if (chapter.isRead) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.reader_read_badge),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Quick panel (preview 41): mode / layout / direction, applied at once
// ---------------------------------------------------------------------------

@Composable
private fun QuickPanelSheet(state: ReaderUiState, viewModel: ReaderViewModel, onDismiss: () -> Unit) {
    AppModalDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_reading_settings), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                ChainTag(overridden = state.overridden.isNotEmpty())
            }
            ModeSection(state, viewModel)
            LayoutSection(state, viewModel)
            DirSection(state, viewModel)
            Text(
                stringResource(R.string.reader_overrides_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChainTag(overridden: Boolean) {
    Surface(
        color = if (overridden) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            stringResource(if (overridden) R.string.reader_chain_overridden else R.string.reader_chain_resolved),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeSection(state: ReaderUiState, viewModel: ReaderViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.reader_mode), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(
                ReadingMode.PAGED to stringResource(R.string.reader_mode_paged),
                ReadingMode.CONTINUOUS to stringResource(R.string.reader_mode_continuous),
                ReadingMode.WEBTOON to stringResource(R.string.reader_mode_webtoon),
            ).forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = state.mode == value,
                    onClick = { viewModel.setMode(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun LayoutSection(state: ReaderUiState, viewModel: ReaderViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.reader_double_page_layout), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PageLayout.entries.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = state.layout == value,
                    onClick = { viewModel.setLayout(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = PageLayout.entries.size),
                    enabled = !state.vertical,
                ) {
                    Text(
                        stringResource(
                            when (value) {
                                PageLayout.SINGLE -> R.string.reader_layout_single
                                PageLayout.DOUBLE -> R.string.reader_layout_double
                                PageLayout.AUTO -> R.string.reader_layout_auto
                            },
                        ),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.reader_layout_auto_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DirSection(state: ReaderUiState, viewModel: ReaderViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.reader_direction), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "default" to stringResource(R.string.reader_dir_follow_manga),
                "ltr" to stringResource(R.string.reader_dir_ltr),
                "rtl" to stringResource(R.string.reader_dir_rtl),
            ).forEach { (value, label) ->
                FilterChip(
                    selected = state.dirPreference == value,
                    onClick = { viewModel.setDir(value) },
                    label = { Text(label) },
                )
            }
        }
        Text(
            stringResource(R.string.reader_dir_rtl_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Full settings sheet (preview 24)
// ---------------------------------------------------------------------------

@Composable
private fun ReaderSettingsSheet(state: ReaderUiState, viewModel: ReaderViewModel, onDismiss: () -> Unit) {
    AppModalDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_reading_settings), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                ChainTag(overridden = state.overridden.isNotEmpty())
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.resetMangaOverrides() }) { Text(stringResource(R.string.reader_reset_defaults)) }
            }
            // Resolution chain block (preview 24): shows where the values
            // come from, so the user never wonders why a page paired up.
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.reader_resolution_chain),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            R.string.reader_mode_value,
                            stringResource(
                                when (state.mode) {
                                    ReadingMode.CONTINUOUS -> R.string.reader_mode_continuous
                                    ReadingMode.WEBTOON -> R.string.reader_mode_webtoon
                                    else -> R.string.reader_mode_paged
                                },
                            ),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(
                            R.string.reader_direction_value,
                            stringResource(
                                if (state.dir == "rtl") R.string.reader_dir_rtl else R.string.reader_dir_ltr,
                            ),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            ModeSection(state, viewModel)
            HorizontalDivider()
            LayoutSection(state, viewModel)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reader_cover_first), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.reader_cover_first_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.coverFirst,
                    onCheckedChange = { viewModel.setCoverFirst(it) },
                    enabled = !state.vertical,
                )
            }
            HorizontalDivider()
            DirSection(state, viewModel)

            HorizontalDivider()
            Text(stringResource(R.string.reader_tap_zones), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "lr" to stringResource(R.string.reader_tap_lr),
                    "rl" to stringResource(R.string.reader_tap_rl),
                    "edge" to stringResource(R.string.reader_tap_edge),
                    "kindle" to "Kindle",
                    "off" to stringResource(R.string.reader_tap_off),
                )
                    .forEach { (value, label) ->
                        FilterChip(
                            selected = state.tapZones == value,
                            onClick = { viewModel.setTapZones(value) },
                            label = { Text(label) },
                        )
                    }
            }
            if (state.tapZones != "off") {
                TapZonePreview(layout = state.tapZones, rtl = state.dir == "rtl")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.reader_volume_key_paging),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.reader_volume_key_paging_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.volumeKeyPaging,
                    onCheckedChange = { viewModel.setVolumeKeyPaging(it) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_double_tap_zoom),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "%.1fx".format(state.doubleTapZoom),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Slider(
                value = state.doubleTapZoom,
                onValueChange = { viewModel.setDoubleTapZoom(it) },
                valueRange = 1f..8f,
            )

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_typography_global),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (state.overridden.any { it in TYPOGRAPHY_FIELDS }) {
                    Text(
                        "\u2022",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                stringResource(R.string.reader_typography_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.reader_typography_scope),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.reader_font), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "default" to stringResource(R.string.reader_font_default),
                    "sans" to stringResource(R.string.reader_font_sans),
                    "serif" to stringResource(R.string.reader_font_serif),
                    "mono" to stringResource(R.string.reader_font_mono),
                )
                    .forEach { (value, label) ->
                        FilterChip(
                            selected = state.font == value,
                            onClick = { viewModel.setFont(value) },
                            label = { Text(label) },
                        )
                    }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_font_size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text("%.0f%%".format(state.fontSize * 100), style = MaterialTheme.typography.bodySmall)
            }
            Slider(
                value = state.fontSize,
                onValueChange = { viewModel.setFontSize(it) },
                valueRange = ReaderPrefs.FONT_SIZE_RANGE,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_line_height),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text("%.1f".format(state.lineHeight), style = MaterialTheme.typography.bodySmall)
            }
            Slider(
                value = state.lineHeight,
                onValueChange = { viewModel.setLineHeight(it) },
                valueRange = ReaderPrefs.LINE_HEIGHT_RANGE,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.reader_text_columns),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.reader_text_columns_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.textColumns,
                    onCheckedChange = { viewModel.setTextColumns(it) },
                )
            }

            HorizontalDivider()
            Text(stringResource(R.string.reader_page_scale), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "fit" to stringResource(R.string.reader_scale_fit),
                    "width" to stringResource(R.string.reader_scale_width),
                    "height" to stringResource(R.string.reader_scale_height),
                    "original" to stringResource(R.string.reader_scale_original),
                )
                    .forEach { (value, label) ->
                        FilterChip(
                            selected = state.scale == value,
                            onClick = { viewModel.setScale(value) },
                            label = { Text(label) },
                            enabled = !state.vertical && state.layout == PageLayout.SINGLE,
                        )
                    }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reader_split_wide), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.reader_split_wide_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.splitWide,
                    onCheckedChange = { viewModel.setSplitWide(it) },
                    enabled = !state.vertical,
                )
            }

            HorizontalDivider()
            Text(stringResource(R.string.reader_background), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "dark" to stringResource(R.string.reader_bg_dark),
                    "gray" to stringResource(R.string.reader_bg_gray),
                    "light" to stringResource(R.string.reader_bg_light),
                    "auto" to stringResource(R.string.reader_bg_auto),
                )
                    .forEach { (value, label) ->
                        FilterChip(
                            selected = state.background == value,
                            onClick = { viewModel.setBackground(value) },
                            label = { Text(label) },
                        )
                    }
            }

            HorizontalDivider()
            Text(stringResource(R.string.reader_filters_global), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_brightness),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(48.dp),
                )
                Slider(
                    value = state.brightness,
                    onValueChange = { viewModel.setBrightness(it) },
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(state.brightness * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_grayscale),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.grayscale, onCheckedChange = { viewModel.setGrayscale(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_sepia),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.sepia, onCheckedChange = { viewModel.setSepia(it) })
            }

            // Window behaviour shared by every reading mode (global-only,
            // KMReader reader settings parity).
            HorizontalDivider()
            Text(stringResource(R.string.reader_behavior_global), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_keep_screen_awake),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.keepScreenAwake,
                    onCheckedChange = { viewModel.setKeepScreenAwake(it) },
                )
            }
            val hasCutout = rememberHasDisplayCutout()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.reader_draw_under_cutout),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(
                            if (hasCutout) R.string.reader_draw_under_cutout_hint
                            else R.string.reader_no_display_cutout,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.drawUnderCutout,
                    onCheckedChange = { viewModel.setDrawUnderCutout(it) },
                    enabled = hasCutout,
                )
            }

            // Waifu2x upscaling (device-only, global layer): the same controls as the defaults
            // screen, kept compact for the sheet.
            HorizontalDivider()
            Text(stringResource(R.string.reader_upscale_title), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reader_upscale_enable),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.upscale.enabled,
                    onCheckedChange = { viewModel.setUpscaleEnabled(it) },
                )
            }
            if (state.upscale.enabled) {
                Text(
                    stringResource(R.string.reader_upscale_denoise),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Chips scroll horizontally so five denoise levels never squeeze into one row.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DenoiseLevel.entries.forEach { level ->
                        FilterChip(
                            selected = state.upscale.denoise == level,
                            onClick = { viewModel.setUpscaleDenoise(level) },
                            label = { Text(denoiseChipLabel(level)) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.reader_upscale_precision),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    UpscalePrecision.entries.forEach { precision ->
                        FilterChip(
                            selected = state.upscale.precision == precision,
                            onClick = { viewModel.setUpscalePrecision(precision) },
                            label = {
                                Text(
                                    stringResource(
                                        when (precision) {
                                            UpscalePrecision.AUTO -> R.string.reader_upscale_precision_auto
                                            UpscalePrecision.FP16 -> R.string.reader_upscale_precision_fp16
                                            UpscalePrecision.FP32 -> R.string.reader_upscale_precision_fp32
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.reader_upscale_preload),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (0..UpscaleSettings.MAX_PRELOAD_AHEAD).forEach { ahead ->
                        FilterChip(
                            selected = state.upscale.preloadAhead == ahead,
                            onClick = { viewModel.setUpscalePreload(ahead) },
                            label = {
                                Text(
                                    if (ahead == 0) {
                                        stringResource(R.string.reader_upscale_preload_off)
                                    } else {
                                        ahead.toString()
                                    },
                                )
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.reader_upscale_device_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Localized label of one waifu2x denoise level for the in-reader sheet chips. */
@Composable
private fun denoiseChipLabel(level: DenoiseLevel): String = stringResource(
    when (level) {
        DenoiseLevel.NONE -> R.string.reader_upscale_denoise_none
        DenoiseLevel.LOW -> R.string.reader_upscale_denoise_low
        DenoiseLevel.MEDIUM -> R.string.reader_upscale_denoise_medium
        DenoiseLevel.HIGH -> R.string.reader_upscale_denoise_high
        DenoiseLevel.MAX -> R.string.reader_upscale_denoise_max
    },
)
