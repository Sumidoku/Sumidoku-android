package org.sumidoku.android.feature.trackers

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import org.sumidoku.android.core.api.model.TrackerProvider
import org.sumidoku.android.core.model.ServerProfile

private fun providerLabel(provider: TrackerProvider): String = trackerProviderLabel(provider)

/** Tracker account bindings: OAuth status per provider, bind via an
 *  embedded WebView that intercepts the callback navigation and hands
 *  code/state to the bearer-authenticated callback endpoint — the
 *  Android answer to the webui's manual paste flow (design doc §8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackersScreen(
    onBack: () -> Unit = {},
    viewModel: TrackersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var oauthProvider by remember { mutableStateOf<TrackerProvider?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trackers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.trackers_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TrackerProvider.entries.size) { i ->
                    val provider = TrackerProvider.entries[i]
                    val status = state.statuses[provider]
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp),
                        ) {
                            TrackerLogo(provider)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(providerLabel(provider), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when {
                                        status?.loggedIn == true -> {
                                            val username = status.username
                                                ?: stringResource(R.string.trackers_unknown_account)
                                            stringResource(R.string.trackers_status_linked, username)
                                        }
                                        else -> stringResource(R.string.trackers_status_unlinked)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (status?.loggedIn == true) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            if (state.busyProvider == provider) {
                                CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            }
                            if (status?.loggedIn == true) {
                                TextButton(
                                    enabled = state.busyProvider == null,
                                    onClick = { viewModel.logout(provider) },
                                ) { Text(stringResource(R.string.trackers_action_unlink)) }
                            } else {
                                TextButton(
                                    enabled = state.busyProvider == null,
                                    onClick = { oauthProvider = provider },
                                ) { Text(stringResource(R.string.trackers_action_link)) }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.trackers_sync_strategy_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    TrackerStrategyRow(
                        title = stringResource(R.string.trackers_strategy_push_title),
                        detail = stringResource(R.string.trackers_strategy_push_detail),
                    )
                    TrackerStrategyRow(
                        title = stringResource(R.string.trackers_strategy_pull_title),
                        detail = stringResource(R.string.trackers_strategy_pull_detail),
                    )
                    TrackerStrategyRow(
                        title = stringResource(R.string.trackers_strategy_binding_title),
                        detail = stringResource(R.string.trackers_strategy_binding_detail),
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    var showDefaultMenu by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.trackers_default_label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.trackers_default_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            OutlinedButton(onClick = { showDefaultMenu = true }) {
                                Text(
                                    state.defaultTracker?.let { providerLabel(it) }
                                        ?: stringResource(R.string.trackers_default_none),
                                )
                            }
                            DropdownMenu(expanded = showDefaultMenu, onDismissRequest = { showDefaultMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.trackers_default_none)) },
                                    onClick = {
                                        showDefaultMenu = false
                                        viewModel.setDefault(null)
                                    },
                                )
                                TrackerProvider.entries.forEach { provider ->
                                    DropdownMenuItem(text = { Text(providerLabel(provider)) }, onClick = {
                                        showDefaultMenu = false
                                        viewModel.setDefault(provider)
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val provider = oauthProvider
    val profile = state.profile
    if (provider != null && profile != null) {
        TrackerOauthSheet(
            provider = provider,
            profile = profile,
            busy = state.busyProvider == provider,
            onComplete = { code, oauthState ->
                viewModel.completeOauth(provider, code, oauthState)
                oauthProvider = null
            },
            onDismiss = { oauthProvider = null },
        )
    }
}

/** Embedded OAuth flow: loads the server's start endpoint with the
 *  bearer header, follows the provider authorize pages inside the
 *  WebView, and intercepts the instance callback URL to extract
 *  code/state instead of navigating (the callback needs auth the
 *  WebView doesn't carry, so completion happens through Retrofit). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerOauthSheet(
    provider: TrackerProvider,
    profile: ServerProfile,
    busy: Boolean,
    onComplete: (code: String, state: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val startUrl = "${profile.baseUrl.trimEnd('/')}/api/v1/trackers/oauth/${provider.value}/start"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().height(560.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    stringResource(R.string.tracker_oauth_sheet_title, providerLabel(provider)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.trackers_action_cancel)) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val url = request.url
                                if (url.path?.contains("/api/v1/trackers/oauth/") == true &&
                                    url.path?.endsWith("/callback") == true
                                ) {
                                    val code = url.getQueryParameter("code")
                                    val state = url.getQueryParameter("state")
                                    if (code != null && state != null) {
                                        onComplete(code, state)
                                        return true
                                    }
                                }
                                return false
                            }
                        }
                        loadUrl(
                            startUrl,
                            mapOf("Authorization" to "Bearer ${profile.apiKey}"),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Read-only explanation rows for server-side tracker sync — the API exposes
 *  only bind/unbind + default tracker, so the strategy is documented, not toggled. */
@androidx.compose.runtime.Composable
private fun TrackerStrategyRow(title: String, detail: String) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}