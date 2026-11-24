package org.sumidoku.android.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.sumidoku.android.core.api.model.ChapterWithDownload
import org.sumidoku.android.core.api.model.Page
import org.sumidoku.android.core.common.chapterLabel
import org.sumidoku.android.core.ui.AppModalDialog
import org.sumidoku.android.core.ui.CoverSeedTheme
import org.sumidoku.android.core.ui.computeCoverTint

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

    CoverSeedTheme(coverTint) {
    Box(Modifier.fillMaxSize().background(canvasColor)) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.openChapter(state.chapterId) }) { Text(stringResource(R.string.reader_retry)) }
                }
            }
            state.vertical -> VerticalReaderBody(state, viewModel)
            else -> PagedReaderBody(state, viewModel, landscape)
        }

        if (state.overlay && !state.loading) {
            ReaderOverlay(state, viewModel, onBack, landscape)
        }

        if (state.chapterSheet) {
            ChapterSheet(state, viewModel)
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
                    if (state.splitWide && page.isWide() && page.content.text == null) {
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
    LaunchedEffect(state.index, displayPages.size) {
        val current = displayPages
            .indexOfFirst { slot -> slot.pages.any { it.index.toInt() >= state.index } }
            .takeIf { it >= 0 } ?: 0
        // Prefetch both neighbours: with RTL the "next" swipe is the slot
        // before the current one, so a one-sided window would miss it.
        for (i in (current - 1)..(current + 2)) {
            if (i !in displayPages.indices) continue
            for (page in displayPages[i].pages) {
                if (page.content.text != null) continue
                prefetchContext.imageLoader.enqueue(
                    ImageRequest.Builder(prefetchContext)
                        .data(viewModel.pageImageUrl(page))
                        .apply {
                            state.profile?.apiKey?.let {
                                httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $it").build())
                            }
                        }
                        .build(),
                )
            }
        }
    }

    var zoomed by remember { mutableStateOf(false) }
    // A zoomed page keeps the pager locked; the flag must clear when the
    // settled slot changes (pinch state resets per page).
    LaunchedEffect(pagerState.settledPage) { zoomed = false }
    val pagerScope = rememberCoroutineScope()
    val pageBack: () -> Unit = {
        if (pagerState.currentPage > 0) {
            pagerScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }
    val pageForward: () -> Unit = {
        if (pagerState.currentPage < pagerState.pageCount - 1) {
            pagerScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }
    CompositionLocalProvider(
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
        ) { slot ->
            val contentIndex = slot - prevSlot
            if (contentIndex < 0 || contentIndex >= displayPages.size) {
                val isNext = contentIndex >= displayPages.size
                val chapter = if (isNext) state.nextChapter else state.prevChapter
                if (chapter != null) {
                    TransitionPage(
                        chapter = chapter,
                        isNext = isNext,
                        currentChapter = state.currentChapter,
                        onConfirm = { viewModel.openChapter(chapter.id) },
                        onBack = {
                            val back = if (isNext) slot - 1 else slot + 1
                            pagerScope.launch { pagerState.animateScrollToPage(back) }
                        },
                    )
                }
                return@HorizontalPager
            }
            val display = displayPages[contentIndex]
            if (display.pages.size == 2) {
                SpreadPage(
                    pages = display.pages,
                    urlFor = { viewModel.pageImageUrl(it) },
                    apiKey = state.profile?.apiKey,
                    tapZones = state.tapZones,
                    rtl = rtl,
                    colorFilter = readerColorFilter(state),
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
                    onZoomChange = { zoomed = it },
                    onTapLeft = pageBack,
                    onTapCenter = { viewModel.toggleOverlay() },
                    onTapRight = pageForward,
                )
            }
        }
    }
}

/** Chapter transition slot (preview 40, webui TransitionPage / Aidoku
 *  ReaderInfoPageView parity): shows the neighbouring chapter, a gap
 *  warning when chapter numbers skip, and an explicit continue action —
 *  landing on the slot never advances on its own. */
@Composable
private fun TransitionPage(
    chapter: ChapterWithDownload,
    isNext: Boolean,
    currentChapter: ChapterWithDownload?,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val skipped = missingBetween(currentChapter, chapter)
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (isNext) R.string.reader_next_chapter else R.string.reader_prev_chapter),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            chapterCardLabel(chapter),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        val meta = buildList {
            add(stringResource(R.string.reader_page_count, chapter.pageCount))
            if (chapter.isRead) add(stringResource(R.string.reader_read_badge))
            if (chapter.isDownloaded || chapter.hasFinishedDownload) add(stringResource(R.string.reader_downloaded_badge))
        }.joinToString(" · ")
        Spacer(Modifier.size(6.dp))
        Text(
            meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (skipped > 0) {
            Spacer(Modifier.size(14.dp))
            Text(
                stringResource(R.string.reader_chapters_missing, skipped),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.size(24.dp))
        Button(onClick = onConfirm) {
            Text(stringResource(if (isNext) R.string.reader_continue_reading else R.string.reader_back_to_previous))
        }
        Spacer(Modifier.size(4.dp))
        TextButton(onClick = onBack) { Text(stringResource(R.string.reader_stay_here)) }
    }
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
    LaunchedEffect(state.seekRequest) {
        if (state.seekRequest > 0 && state.index in 0 until state.pages.size) {
            listState.scrollToItem(state.index)
        }
    }
    // webtoon gap 0 / continuous 12dp (Aidoku webtoon vs continuous).
    val gap = if (state.mode == ReadingMode.CONTINUOUS) 12.dp else 0.dp

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
                Box(Modifier.fillMaxWidth().clickable { viewModel.toggleOverlay() }) {
                    val text = page.content.text
                    if (text != null) {
                        TextPageContent(text, fillWidth = true)
                    } else {
                        ReaderImage(
                            url = viewModel.pageImageUrl(page),
                            apiKey = state.profile?.apiKey,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                            colorFilter = readerColorFilter(state),
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
        VerticalScrollBar(
            state = state,
            listState = listState,
            onNextChapter = state.nextChapter?.let { ch -> { viewModel.openChapter(ch.id) } },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
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

@Composable
private fun TextPageContent(text: String, fillWidth: Boolean) {
    SelectionContainer {
        Column(
            (if (fillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5,
            )
        }
    }
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
    onZoomChange: (Boolean) -> Unit,
    onTapLeft: () -> Unit,
    onTapCenter: () -> Unit,
    onTapRight: () -> Unit,
) {
    var scale by remember(page.index) { mutableFloatStateOf(1f) }
    var offset by remember(page.index) { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize()) {
        val textPage = page.content.text
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
                    )
                }
            }
            TapZoneOverlay(
                layout = tapZones,
                rtl = rtl,
                onPrev = onTapLeft,
                onMenu = onTapCenter,
                onNext = onTapRight,
            )
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
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = newScale
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                                onZoomChange(scale > 1.01f)
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
            )
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
            Box(Modifier.weight(0.25f).fillMaxWidth().clickable(onClick = onMenu))
            Row(Modifier.weight(0.75f).fillMaxSize()) {
                val leftWeight = if (rtl) 0.25f else 0.75f
                val rightWeight = if (rtl) 0.75f else 0.25f
                Box(Modifier.weight(leftWeight).fillMaxSize().clickable(onClick = if (rtl) onNext else onPrev))
                Box(Modifier.weight(rightWeight).fillMaxSize().clickable(onClick = if (rtl) onPrev else onNext))
            }
        }
        "edge" -> {
            val leftIsPrev = !rtl
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.15f).fillMaxSize().clickable(onClick = if (leftIsPrev) onPrev else onNext))
                Box(Modifier.weight(0.7f).fillMaxSize().clickable(onClick = onMenu))
                Box(Modifier.weight(0.15f).fillMaxSize().clickable(onClick = if (leftIsPrev) onNext else onPrev))
            }
        }
        else -> {
            val baseLeftIsPrev = layout != "rl"
            val leftIsPrev = if (rtl) !baseLeftIsPrev else baseLeftIsPrev
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.3f).fillMaxSize().clickable(onClick = if (leftIsPrev) onPrev else onNext))
                Box(Modifier.weight(0.4f).fillMaxSize().clickable(onClick = onMenu))
                Box(Modifier.weight(0.3f).fillMaxSize().clickable(onClick = if (leftIsPrev) onNext else onPrev))
            }
        }
    }
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
    onTapLeft: () -> Unit,
    onTapCenter: () -> Unit,
    onTapRight: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            pages.forEachIndexed { index, page ->
                // Push each page image towards the centre gutter of its
                // half so the two pages of a spread join in the middle
                // (KMReader does the same; RTL flips Start/End
                // automatically, keeping the seam in the middle).
                val edgeAlignment = if (index == 0) Alignment.CenterEnd else Alignment.CenterStart
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = edgeAlignment) {
                    val textPage = page.content.text
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
                        )
                    }
                }
            }
        }
        TapZoneOverlay(
            layout = tapZones,
            rtl = rtl,
            onPrev = onTapLeft,
            onMenu = onTapCenter,
            onNext = onTapRight,
        )
    }
}

@Composable
private fun ReaderImage(
    url: String,
    apiKey: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    alignment: Alignment = Alignment.Center,
    colorFilter: ColorFilter? = null,
) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }
    var retryKey by remember(url) { mutableIntStateOf(0) }
    if (failed) {
        // Failed pages keep the slot's aspect box so the pager does not
        // jump; the retry button sits above the tap zones (preview 07).
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.reader_page_load_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { failed = false; retryKey++ }) { Text(stringResource(R.string.reader_retry)) }
            }
        }
        return
    }
    val model = ImageRequest.Builder(context).data(url).apply {
        apiKey?.let { httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $it").build()) }
    }.build()
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        colorFilter = colorFilter,
        onError = { failed = true },
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
                            stringResource(R.string.reader_page_progress, state.index + 1, state.pages.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = chromeContent.copy(alpha = 0.7f),
                        )
                        if (!state.vertical) {
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

/** Chapter navigator pill (previews 07/39, webui ReaderPager /
 *  Aidoku bottom slider parity): previous chapter, current page over
 *  total, reversed slider and swapped arrows under RTL. */
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
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(24.dp),
        border = border,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { state.prevChapter?.let { viewModel.openChapter(it.id) } },
                enabled = state.prevChapter != null,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (rtl) Icons.AutoMirrored.Outlined.NavigateNext else Icons.AutoMirrored.Outlined.NavigateBefore,
                    contentDescription = stringResource(R.string.reader_previous_chapter_desc),
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                if (state.doubleSpread(false) && !state.vertical) "${state.index + 1}–${(state.index + 2).coerceAtMost(state.pages.size)}"
                else "${state.index + 1}",
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = sliderValue,
                onValueChange = { raw ->
                    val target = if (rtl) range.endInclusive - raw else raw
                    viewModel.seekTo(target.toInt())
                },
                valueRange = range,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            Text(
                "/ ${state.pages.size}",
                color = contentColor.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(
                onClick = { state.nextChapter?.let { viewModel.openChapter(it.id) } },
                enabled = state.nextChapter != null,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (rtl) Icons.AutoMirrored.Outlined.NavigateBefore else Icons.AutoMirrored.Outlined.NavigateNext,
                    contentDescription = stringResource(R.string.reader_next_chapter_desc),
                    tint = contentColor,
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
    var descending by remember { mutableStateOf(false) }

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
                IconButton(onClick = { descending = !descending }) {
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
    val number = chapterLabel(chapter.volumeNumber, chapter.chapterNumber, null)
    Surface(
        color = if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                number,
                style = MaterialTheme.typography.labelMedium,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    chapter.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.reader_chapter_fallback, number),
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
        }
    }
}

