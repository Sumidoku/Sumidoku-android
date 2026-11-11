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
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
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

@Composable
private fun PagedBody(state: ReaderUiState, viewModel: ReaderViewModel) {
    val pagerState = rememberPagerState(
        initialPage = state.index,
        pageCount = { state.pages.size },
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.setIndex(it) }
    }
    LaunchedEffect(state.chapterId) {
        if (state.index > 0 && pagerState.currentPage != state.index) {
            pagerState.scrollToPage(state.index)
        }
    }

    var zoomed by remember { mutableStateOf(false) }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !zoomed,
        beyondViewportPageCount = 1,
    ) { pageIndex ->
        ReaderPage(
            page = state.pages[pageIndex],
            url = viewModel.pageImageUrl(state.pages[pageIndex]),
            apiKey = state.profile?.apiKey,
            onZoomChange = { zoomed = it },
            onTapLeft = { if (pagerState.currentPage > 0) viewModel.setIndex(pagerState.currentPage - 1) },
            onTapCenter = { viewModel.toggleOverlay() },
            onTapRight = { viewModel.setIndex(pagerState.currentPage + 1) },
        )
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
        ReaderImage(
            url = url,
            apiKey = apiKey,
            modifier = Modifier
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
                },
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
