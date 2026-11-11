package org.sumidoku.android.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import org.sumidoku.android.core.api.model.TextPage

/** Reader MVP: paged (HorizontalPager + pinch zoom + tap zones) and webtoon
 *  (continuous LazyColumn) modes; debounced progress PATCH on settle.
 *  Previews 07-reader-paged / 08-reader-webtoon. */
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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

/** One display slot in paged mode: a whole page, or the left/right
 *  half of a wide page when split-wide is on (design §12: ltr reads
 *  left half first, rtl reads right half first; progress and the
 *  slider stay in real page units). */
private data class DisplayPage(val page: Page, val crop: Int) // 0 whole, 1 left, 2 right

@Composable
private fun PagedBody(state: ReaderUiState, viewModel: ReaderViewModel) {
    val rtl = state.dir == "rtl"
    val displayPages = remember(state.pages, state.splitWide, rtl) {
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
    val initialDisplay = displayPages.indexOfFirst { it.page.index.toInt() >= state.index }
        .coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialDisplay,
        pageCount = { displayPages.size },
    )
    LaunchedEffect(pagerState, displayPages) {
        snapshotFlow { pagerState.settledPage }.collect { slot ->
            displayPages.getOrNull(slot)?.let { viewModel.setIndex(it.page.index.toInt()) }
        }
    }
    LaunchedEffect(state.chapterId) {
        if (state.index > 0) {
            val target = displayPages.indexOfFirst { it.page.index.toInt() >= state.index }
            if (target > 0 && pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
        }
    }

    // M2: prefetch the next two display slots so swipes hit the disk
    // cache instead of the network (design §12).
    val prefetchContext = LocalContext.current
    LaunchedEffect(state.index, displayPages.size) {
        val currentSlot = displayPages.indexOfFirst { it.page.index.toInt() >= state.index }
            .takeIf { it >= 0 } ?: 0
        for (i in (currentSlot + 1) until (currentSlot + 3).coerceAtMost(displayPages.size)) {
            val page = displayPages[i].page
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
            ReaderPage(
                page = display.page,
                crop = display.crop,
                url = viewModel.pageImageUrl(display.page),
                apiKey = state.profile?.apiKey,
                onZoomChange = { zoomed = it },
                onTapLeft = {
                    if (pagerState.currentPage > 0) {
                        displayPages.getOrNull(pagerState.currentPage - 1)?.let {
                            viewModel.setIndex(it.page.index.toInt())
                        }
                    }
                },
                onTapCenter = { viewModel.toggleOverlay() },
                onTapRight = {
                    displayPages.getOrNull(pagerState.currentPage + 1)?.let {
                        viewModel.setIndex(it.page.index.toInt())
                    }
                },
            )
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
                ReaderImage(
                    url = viewModel.pageImageUrl(page),
                    apiKey = state.profile?.apiKey,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
}

@Composable
private fun ReaderPage(
    page: Page,
    crop: Int,
    url: String,
    apiKey: String?,
    onZoomChange: (Boolean) -> Unit,
    onTapLeft: () -> Unit,
    onTapCenter: () -> Unit,
    onTapRight: () -> Unit,
) {
    var scale by remember(page.index) { mutableFloatStateOf(1f) }
    var offset by remember(page.index) { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize()) {
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
        ReaderImage(
            url = url,
            apiKey = apiKey,
            modifier = imageModifier,
            contentScale = ContentScale.Fit,
        )
        if (scale <= 1.01f) {
            // Tap zones: prev / menu / next thirds — see preview 24 zone map.
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.3f).fillMaxSize().clickable(onClick = onTapLeft))
                Box(Modifier.weight(0.4f).fillMaxSize().clickable(onClick = onTapCenter))
                Box(Modifier.weight(0.3f).fillMaxSize().clickable(onClick = onTapRight))
            }
        }
    }
}

@Composable
private fun ReaderImage(
    url: String,
    apiKey: String?,
    modifier: Modifier,
    contentScale: ContentScale,
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
