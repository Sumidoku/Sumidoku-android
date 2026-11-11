package org.sumidoku.android.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import org.sumidoku.android.core.api.model.Page

/** Reader MVP: paged (HorizontalPager + pinch zoom + tap zones) and webtoon
 *  (continuous LazyColumn) modes; debounced progress PATCH on settle.
 *  Previews 07-reader-paged / 08-reader-webtoon. */
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Canvas background (design §12, webui sumidoku.reader.bg parity):
    // dark/gray/light are fixed, auto follows the app theme surface.
    val canvasColor = when (state.background) {
        "gray" -> Color(0xFF1E1E26)
        "light" -> Color(0xFFFFFFFF)
        "auto" -> MaterialTheme.colorScheme.surface
        else -> Color.Black
    }

    Box(Modifier.fillMaxSize().background(canvasColor)) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.openChapter(state.chapterId) }) { Text("重试") }
                }
            }
            else -> {
                if (state.webtoon) {
                    WebtoonBody(state, viewModel)
                } else {
                    PagedBody(state, viewModel)
                }
            }
        }

        if (state.overlay && !state.loading) {
            ReaderOverlay(state, viewModel, onBack)
        }
    }
}

/** One display slot in paged mode: a whole page, the left/right half
 *  of a wide page when split-wide is on, or a spread of one or two
 *  pages when double-page is on (design §12: ltr reads left first,
 *  rtl reads right first; landscape pages always get their own
 *  spread; progress and the slider stay in real page units). */
private data class DisplayPage(val pages: List<Page>, val crop: Int) // 0 whole, 1 left, 2 right
{
    constructor(page: Page, crop: Int) : this(listOf(page), crop)
    val page: Page get() = pages.first()
}

@Composable
private fun PagedBody(state: ReaderUiState, viewModel: ReaderViewModel) {
    val rtl = state.dir == "rtl"
    val double = state.doublePage > 0 && !state.splitWide
    val displayPages = remember(state.pages, state.splitWide, state.doublePage, rtl) {
        if (double) {
            // Double-page spreads (design §12): consecutive portrait
            // pages pair up in reading order; landscape pages stand
            // alone; doubleNoCover singles out the cover page first.
            val spreads = mutableListOf<DisplayPage>()
            var i = 0
            val pages = state.pages
            var coverSingled = false
            while (i < pages.size) {
                val p = pages[i]
                val wide = (p.width ?: 0) > 0 && (p.height ?: 0) > 0 && p.width!! > p.height!!
                if (wide || (state.doublePage == 2 && !coverSingled)) {
                    spreads.add(DisplayPage(p, 0))
                    if (!wide) coverSingled = true
                    i++
                } else if (i + 1 < pages.size) {
                    val q = pages[i + 1]
                    val qWide = (q.width ?: 0) > 0 && (q.height ?: 0) > 0 && q.width!! > q.height!!
                    if (qWide) {
                        spreads.add(DisplayPage(p, 0))
                        i++
                    } else {
                        spreads.add(DisplayPage(if (rtl) listOf(q, p) else listOf(p, q), 0))
                        i += 2
                    }
                } else {
                    spreads.add(DisplayPage(p, 0))
                    i++
                }
            }
            spreads
        } else {
            buildList {
                state.pages.forEach { page ->
                    val wide = state.splitWide && !page.hasDescription &&
                        (page.width ?: 0) > 0 && (page.height ?: 0) > 0 && page.width!! > page.height!!
                    if (wide) {
                        val first = if (rtl) 2 else 1
                        val second = if (rtl) 1 else 2
                        add(DisplayPage(page, first))
                        add(DisplayPage(page, second))
                    } else {
                        add(DisplayPage(page, 0))
                    }
                }
            }
        }
    }
    val initialDisplay = displayPages.indexOfFirst { slot -> slot.pages.any { it.index.toInt() >= state.index } }
        .coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialDisplay,
        pageCount = { displayPages.size },
    )
    LaunchedEffect(pagerState, displayPages) {
        snapshotFlow { pagerState.settledPage }.collect { slot ->
            displayPages.getOrNull(slot)?.let { viewModel.setIndex(it.pages.last().index.toInt()) }
        }
    }
    LaunchedEffect(state.chapterId) {
        if (state.index > 0) {
            val target = displayPages.indexOfFirst { slot -> slot.pages.any { it.index.toInt() >= state.index } }
            if (target > 0 && pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
        }
    }

    // M2: prefetch the next two display slots so swipes hit the disk
    // cache instead of the network (design §12).
    val prefetchContext = LocalContext.current
    LaunchedEffect(state.index, displayPages.size) {
        val currentSlot = displayPages.indexOfFirst { slot -> slot.pages.any { it.index.toInt() >= state.index } }
            .takeIf { it >= 0 } ?: 0
        for (i in (currentSlot + 1) until (currentSlot + 3).coerceAtMost(displayPages.size)) {
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
    CompositionLocalProvider(
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
        ) { slot ->
            val display = displayPages[slot]
            if (display.pages.size == 2) {
                // Double-page spread: two fit-height pages side by
                // side in reading order (design §12). Zoom stays off
                // for spreads; tap zones still navigate.
                SpreadPage(
                    pages = display.pages,
                    urlFor = { viewModel.pageImageUrl(it) },
                    apiKey = state.profile?.apiKey,
                    tapZones = state.tapZones,
                    rtl = rtl,
                    colorFilter = readerColorFilter(state),
                    onTapLeft = {
                        if (pagerState.currentPage > 0) {
                            displayPages.getOrNull(pagerState.currentPage - 1)?.let {
                                viewModel.setIndex(it.pages.last().index.toInt())
                            }
                        }
                    },
                    onTapCenter = { viewModel.toggleOverlay() },
                    onTapRight = {
                        displayPages.getOrNull(pagerState.currentPage + 1)?.let {
                            viewModel.setIndex(it.pages.last().index.toInt())
                        }
                    },
                )
            } else {
                ReaderPage(
                    page = display.page,
                    crop = display.crop,
                    url = viewModel.pageImageUrl(display.page),
                    apiKey = state.profile?.apiKey,
                    tapZones = state.tapZones,
                    rtl = rtl,
                    pageScale = state.pageScale,
                    colorFilter = readerColorFilter(state),
                    onZoomChange = { zoomed = it },
                    onTapLeft = {
                        if (pagerState.currentPage > 0) {
                            displayPages.getOrNull(pagerState.currentPage - 1)?.let {
                                viewModel.setIndex(it.pages.last().index.toInt())
                            }
                        }
                    },
                    onTapCenter = { viewModel.toggleOverlay() },
                    onTapRight = {
                        displayPages.getOrNull(pagerState.currentPage + 1)?.let {
                            viewModel.setIndex(it.pages.last().index.toInt())
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WebtoonBody(state: ReaderUiState, viewModel: ReaderViewModel) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.index)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { viewModel.setIndex(it) }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.pages.size, key = { state.pages[it].index }) { position ->
            val page = state.pages[position]
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleOverlay() },
            ) {
                val textPage = page.content.text
                if (textPage != null) {
                    TextPageContent(textPage, fillWidth = true)
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
    }
}

/** Custom reader filters (design §12, webui filters parity simplified):
 *  brightness factor plus grayscale/sepia, rendered as a ColorMatrix. */
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

/** Text chapters: the novel/Novel-style content the server embeds in
 *  the pages response renders as scrollable, selectable text instead
 *  of an image (design §12, text page support). */
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
            // Page-scale modes (design §12, webui sumidoku.reader.scale
            // parity): width fills width and scrolls vertically, height
            // fills height and scrolls horizontally, original renders
            // unscaled in both directions. Zoom stays off; tap zones
            // overlay the scroller.
            val scrollModifier = when (pageScale) {
                "width" -> Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                "height" -> Modifier.fillMaxHeight().horizontalScroll(rememberScrollState())
                else -> Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
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
        val imageModifier = if (crop == 0) {
            Modifier
                .fillMaxSize()
                .pointerInput(page.index) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                        scale = newScale
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                        onZoomChange(scale > 1.01f)
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        } else {
            // Split-wide half: draw the page scaled 2x horizontally and
            // translated so the requested half fills the slot (the
            // container clips the rest). Zoom stays available per slot.
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
        } else {
            ReaderImage(
                url = url,
                apiKey = apiKey,
                modifier = imageModifier,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
            )
        }
        if (scale <= 1.01f) {
            TapZoneOverlay(
                layout = tapZones,
                rtl = rtl,
                onPrev = onTapLeft,
                onMenu = onTapCenter,
                onNext = onTapRight,
            )
        }
    }
}

/** Tap zones (design §12, webui TapZoneLayout parity): lr = prev/menu/
 *  next thirds, rl mirrors, edge narrows the side zones to 15%, kindle
 *  reserves the top quarter for the menu and splits the rest 75/25,
 *  off disables zone taps. `rtl` mirrors left/right actions so prev
 *  always means "previous in reading order". Preview 24 zone map. */
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
            // "lr": left prev, right next; "rl": mirrored before the
            // reading-direction mirror applies.
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

/** Graphical tap-zone mini-map for the settings sheet (webui
 *  TapZonePreview parity): prev = #A4706F, next = #6F9A80,
 *  menu = #6F86A8, none = surfaceVariant. */
@Composable
private fun TapZonePreview(layout: String, rtl: Boolean) {
    val prevColor = Color(0xFFA4706F)
    val nextColor = Color(0xFF6F9A80)
    val menuColor = Color(0xFF6F86A8)
    val noneColor = MaterialTheme.colorScheme.surfaceVariant
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
        Text("◼ 上一页", color = prevColor, style = MaterialTheme.typography.labelSmall)
        Text("◼ 菜单", color = menuColor, style = MaterialTheme.typography.labelSmall)
        Text("◼ 下一页", color = nextColor, style = MaterialTheme.typography.labelSmall)
    }
}

/** Double-page spread: two pages side by side in reading order
 *  (already ordered ltr/rtl by the spread builder). Zoom is disabled
 *  for spreads; the prev/menu/next tap thirds still work. */
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
            pages.forEach { page ->
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    val textPage = page.content.text
                    if (textPage != null) {
                        TextPageContent(textPage, fillWidth = false)
                    } else {
                        ReaderImage(
                            url = urlFor(page),
                            apiKey = apiKey,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
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
    colorFilter: ColorFilter? = null,
) {
    val context = LocalContext.current
    val model = ImageRequest.Builder(context).data(url).apply {
        apiKey?.let { httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $it").build()) }
    }.build()
    var failed by remember(url) { mutableStateOf(false) }
    var retryKey by remember(url) { mutableStateOf(0) }
    if (failed) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("页面加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { failed = false; retryKey++ }) { Text("重试") }
            }
        }
    } else {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
            colorFilter = colorFilter,
            onError = { failed = true },
        )
    }
}

@Composable
private fun ReaderOverlay(state: ReaderUiState, viewModel: ReaderViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Surface(color = Color.Black.copy(alpha = 0.82f)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(state.chapterTitle, color = Color.White, maxLines = 1)
                    Text(
                        "${state.index + 1} / ${state.pages.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
        Surface(color = Color.Black.copy(alpha = 0.82f)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Slider(
                    value = state.index.toFloat(),
                    onValueChange = { viewModel.setIndex(it.toInt()) },
                    valueRange = 0f..(state.pages.size - 1).coerceAtLeast(0).toFloat(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { state.prevChapter?.let { viewModel.openChapter(it.id) } },
                        enabled = state.prevChapter != null,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.NavigateBefore, contentDescription = "上一章", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.setWebtoon(!state.webtoon) }) {
                        Icon(
                            if (state.webtoon) Icons.Outlined.ViewCarousel else Icons.Outlined.ViewAgenda,
                            contentDescription = "切换模式",
                            tint = Color.White,
                        )
                    }
                    var showSettings by remember { mutableStateOf(false) }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "阅读设置", tint = Color.White)
                    }
                    if (showSettings) {
                        ReaderSettingsSheet(state, viewModel) { showSettings = false }
                    }
                    IconButton(
                        onClick = { state.nextChapter?.let { viewModel.openChapter(it.id) } },
                        enabled = state.nextChapter != null,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = "下一章", tint = Color.White)
                    }
                }
            }
        }
    }
}

/** Reader settings sheet (design §12): reading mode, direction
 *  (mirrors tap zones and the pager), and split-wide-page in paged
 *  mode. Preview 24-reader-settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleMedium)

            Text("阅读模式", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !state.webtoon,
                    onClick = { viewModel.setWebtoon(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("分页") }
                SegmentedButton(
                    selected = state.webtoon,
                    onClick = { viewModel.setWebtoon(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("条漫（连续）") }
            }

            HorizontalDivider()
            Text("阅读方向", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("default" to "跟随漫画", "ltr" to "从左到右", "rtl" to "从右到左").forEach { (value, label) ->
                    FilterChip(
                        selected = state.dir == value || (value == "default" && state.dir !in listOf("ltr", "rtl")),
                        onClick = { viewModel.setDir(if (value == "default") "ltr" else value) },
                        label = { Text(label) },
                    )
                }
            }

            HorizontalDivider()
            Text("点按区域", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("lr" to "左右", "rl" to "右左", "edge" to "边缘", "kindle" to "Kindle", "off" to "关").forEach { (value, label) ->
                    FilterChip(
                        selected = state.tapZones == value,
                        onClick = { viewModel.setTapZones(value) },
                        label = { Text(label) },
                    )
                }
            }
            // Graphical mini-map of the selected layout (webui
            // TapZonePreview parity: prev/next/menu colours).
            if (state.tapZones != "off") {
                TapZonePreview(layout = state.tapZones, rtl = state.dir == "rtl")
            }

            HorizontalDivider()
            Text("双页布局", style = MaterialTheme.typography.titleSmall)
            Text(
                "连续两页并排显示；横页始终独占整版（与拆分宽页互斥，仅分页模式）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "关", 1 to "双页", 2 to "双页（不含封面）").forEach { (value, label) ->
                    FilterChip(
                        selected = state.doublePage == value,
                        onClick = { viewModel.setDoublePage(value) },
                        enabled = !state.webtoon,
                        label = { Text(label) },
                    )
                }
            }

            HorizontalDivider()
            Text("缩放", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "fit" to "适应",
                    "width" to "宽度",
                    "height" to "高度",
                    "original" to "原始",
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = state.pageScale == value,
                        onClick = { viewModel.setPageScale(value) },
                        label = { Text(label) },
                        enabled = !state.webtoon && state.doublePage == 0,
                    )
                }
            }
            Text(
                "宽度/高度/原始模式可拖动查看；条漫恒为填充宽度，双页固定适应",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Text("背景", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "dark" to "纯黑",
                    "gray" to "深灰",
                    "light" to "白色",
                    "auto" to "跟随主题",
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = state.background == value,
                        onClick = { viewModel.setBackground(value) },
                        label = { Text(label) },
                    )
                }
            }

            HorizontalDivider()
            Text("滤镜", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("亮度", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Slider(
                    value = state.brightness,
                    onValueChange = { viewModel.setBrightness(it) },
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier.weight(3f),
                )
                Text("${(state.brightness * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("灰度", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = state.grayscale, onCheckedChange = { viewModel.setGrayscale(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("棕褐", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = state.sepia, onCheckedChange = { viewModel.setSepia(it) })
            }

            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("拆分宽页", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "横页按阅读方向拆成两半（仅分页模式；进度仍按真实页码记录）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.splitWide,
                    onCheckedChange = { viewModel.setSplitWide(it) },
                    enabled = !state.webtoon,
                )
            }
        }
    }
}
