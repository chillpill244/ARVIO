package com.arflix.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.arflix.tv.network.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.data.model.Subtitle
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.TextSecondary
import com.arflix.tv.util.HlsDownloadSelection
import com.arflix.tv.util.HlsDownloadUtil
import com.arflix.tv.util.HlsInspection
import com.arflix.tv.util.HlsVariantOption
import com.arflix.tv.util.filterSubtitlesByLanguage
import com.arflix.tv.util.isSubtitleLangDisabled
import com.arflix.tv.util.normalizeSubtitleLang
import com.arflix.tv.util.subtitleMatchesLanguage

private fun StreamSource.isDirectDownloadable(): Boolean {
    val u = url ?: return false
    if (u.startsWith("magnet:", ignoreCase = true)) return false
    if (u.contains(".mpd", ignoreCase = true)) return false
    return u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true)
}

private fun StreamSource.isHls(): Boolean =
    url?.contains(".m3u8", ignoreCase = true) == true

private fun StreamSource.displayTitle(): String =
    behaviorHints?.filename?.takeIf { it.isNotBlank() } ?: source

private fun StreamSource.addonLabel(): String =
    addonName.split(" - ").firstOrNull()?.trim() ?: addonName

/** Merges stream-bundled subtitles with externally-fetched ones, deduped by URL. */
private fun mergeSubtitles(streamSubs: List<Subtitle>, externalSubs: List<Subtitle>): List<Subtitle> {
    val seen = mutableSetOf<String>()
    return (streamSubs + externalSubs)
        .filter { !it.isEmbedded && it.url.isNotBlank() }
        .filter { seen.add(it.url) }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DownloadSheet(
    isVisible: Boolean,
    title: String,
    streams: List<StreamSource>,
    subtitles: List<Subtitle> = emptyList(),
    isLoadingStreams: Boolean,
    isLoadingSubtitles: Boolean = false,
    preferredSubtitleLang: String = "",
    secondarySubtitleLang: String = "",
    onConfirm: (stream: StreamSource, subtitle: Subtitle?, hlsSelection: HlsDownloadSelection?) -> Unit,
    onDismiss: () -> Unit
) {
    val downloadable = remember(streams) {
        sortStreams(streams.filter { it.isDirectDownloadable() })
    }
    var selectedStream by remember(isVisible) { mutableStateOf<StreamSource?>(null) }

    // Auto-select the first subtitle matching preferred language when a stream is picked
    var selectedSubtitle by remember(selectedStream) {
        val stream = selectedStream
        val allSubs = if (stream != null) mergeSubtitles(stream.subtitles, subtitles) else emptyList()
        val preferredNorm = normalizeSubtitleLang(preferredSubtitleLang)
        val autoSelect = if (!isSubtitleLangDisabled(preferredSubtitleLang))
            allSubs.firstOrNull { subtitleMatchesLanguage(it, preferredNorm) }
        else null
        mutableStateOf(autoSelect)
    }
    var resolvedSizeBytes by remember(selectedStream) { mutableStateOf<Long?>(null) }
    // true = HEAD response set cookies the worker's cookie-less client can't maintain
    var sessionCookieDetected by remember(selectedStream) { mutableStateOf(false) }
    var hlsInspection by remember(selectedStream) { mutableStateOf<HlsInspection?>(null) }
    var selectedVariant by remember(selectedStream) { mutableStateOf<HlsVariantOption?>(null) }

    LaunchedEffect(selectedStream) {
        val stream = selectedStream ?: return@LaunchedEffect
        val url = stream.url ?: return@LaunchedEffect
        if (stream.isHls()) {
            hlsInspection = HlsDownloadUtil.inspect(
                url,
                stream.behaviorHints?.proxyHeaders?.request.orEmpty()
            )
            return@LaunchedEffect
        }
        val needsSize = stream.size.isBlank() && stream.sizeBytes == null
        if (!needsSize) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                val streamRequestHeaders = stream.behaviorHints?.proxyHeaders?.request.orEmpty()
                fun Request.Builder.applyStreamHeaders() = apply {
                    header("User-Agent", OkHttpProvider.userAgent)
                    streamRequestHeaders.forEach { (k, v) -> if (k.isNotBlank()) header(k, v) }
                }

                // 1. Try HEAD — fast and zero-bandwidth
                val headResp = runCatching {
                    OkHttpProvider.playbackClient.newCall(
                        Request.Builder().url(url).head().applyStreamHeaders().build()
                    ).execute()
                }.getOrNull()

                val headContentLength = headResp?.use { resp ->
                    sessionCookieDetected = resp.headers("Set-Cookie").isNotEmpty()
                    if (resp.isSuccessful) resp.header("Content-Length")?.toLongOrNull()
                        ?.takeIf { it > 0L } else null
                }

                if (headContentLength != null) {
                    resolvedSizeBytes = headContentLength
                    return@withContext
                }

                // 2. HEAD missing/zero Content-Length (common for IPTV/Xtream Codes panels).
                //    Range GET bytes=0-0: server replies 206 with Content-Range: bytes 0-0/<total>
                //    giving the full file size. If server ignores Range and returns 200, we
                //    read Content-Length from the response body length instead.
                val rangeResp = runCatching {
                    OkHttpProvider.playbackClient.newCall(
                        Request.Builder().url(url).get().applyStreamHeaders()
                            .header("Range", "bytes=0-0").build()
                    ).execute()
                }.getOrNull()

                rangeResp?.use { resp ->
                    if (!sessionCookieDetected) {
                        sessionCookieDetected = resp.headers("Set-Cookie").isNotEmpty()
                    }
                    resolvedSizeBytes = when {
                        // 206: Content-Range: bytes 0-0/<total> — total after the last '/'
                        resp.code == 206 -> resp.header("Content-Range")
                            ?.substringAfterLast('/')?.trimEnd()?.toLongOrNull()
                            ?.takeIf { it > 0L }
                        // 200: server ignored Range; Content-Length is the full file size
                        resp.isSuccessful -> resp.body?.contentLength()?.takeIf { it > 0L }
                            ?: resp.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
                        else -> null
                    }
                }
            }
        }
    }

    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = isVisible
    if (transitionState.currentState || transitionState.targetState) {
        Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visibleState = transitionState,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(onClick = onDismiss)
                            .zIndex(20f)
                    )
                }

                AnimatedVisibility(
                    visibleState = transitionState,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(
            modifier = Modifier.fillMaxSize().zIndex(21f),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color(0xFF1A1A1A))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp)
            ) {
                // Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )

                // HLS streams pause at a quality/diagnostics step until a variant is chosen
                // (or the playlist turns out to be a single VOD media playlist).
                val hlsStepActive = selectedStream?.isHls() == true && selectedVariant == null &&
                    hlsInspection !is HlsInspection.MediaPlaylistVod

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                selectedStream == null -> "Download"
                                hlsStepActive -> "Choose quality"
                                else -> "Choose subtitles"
                            },
                            style = ArflixTypography.sectionTitle,
                            color = Color.White
                        )
                        Text(
                            text = title,
                            style = ArflixTypography.caption,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        when {
                            selectedVariant != null -> selectedVariant = null
                            selectedStream != null -> selectedStream = null
                            else -> onDismiss()
                        }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                when {
                    isLoadingStreams -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    downloadable.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No direct download sources available",
                                style = ArflixTypography.body,
                                color = TextSecondary
                            )
                        }
                    }
                    selectedStream == null -> {
                        Text(
                            text = "Choose a source",
                            style = ArflixTypography.label,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(downloadable) { stream ->
                                StreamDownloadRow(
                                    stream = stream,
                                    onClick = { selectedStream = stream }
                                )
                            }
                        }
                    }
                    hlsStepActive -> {
                        when (val inspection = hlsInspection) {
                            null -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                                }
                            }
                            is HlsInspection.MasterPlaylist -> {
                                Text(
                                    text = "Available qualities",
                                    style = ArflixTypography.label,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                LazyColumn(
                                    modifier = Modifier.weight(1f, fill = false),
                                    contentPadding = PaddingValues(bottom = 8.dp)
                                ) {
                                    items(inspection.variants) { variant ->
                                        SubtitleRow(
                                            label = variant.label,
                                            isSelected = false,
                                            onClick = { selectedVariant = variant }
                                        )
                                    }
                                }
                            }
                            else -> DownloadUnavailableNotice(
                                message = when (inspection) {
                                    is HlsInspection.Live -> "Live streams can't be downloaded."
                                    is HlsInspection.UnsupportedEncryption ->
                                        "This stream's encryption isn't supported offline."
                                    else -> "Couldn't read this stream's playlist."
                                }
                            )
                        }
                    }
                    else -> {
                        val stream = selectedStream!!
                        val allSubtitles = filterSubtitlesByLanguage(
                            mergeSubtitles(stream.subtitles, subtitles),
                            preferredSubtitleLang,
                            secondarySubtitleLang
                        )

                        // Subtitle count / loading hint
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when {
                                    isLoadingSubtitles -> "Loading subtitles…"
                                    allSubtitles.isEmpty() -> "No subtitles found"
                                    else -> "${allSubtitles.size} subtitle tracks available"
                                },
                                style = ArflixTypography.label,
                                color = TextSecondary
                            )
                            if (isLoadingSubtitles) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            item {
                                SubtitleRow(
                                    label = "No subtitles",
                                    isSelected = selectedSubtitle == null,
                                    onClick = { selectedSubtitle = null }
                                )
                            }
                            items(allSubtitles) { sub ->
                                SubtitleRow(
                                    label = sub.label.ifBlank { sub.lang }.ifBlank { sub.url.substringAfterLast('/').take(30) },
                                    isSelected = selectedSubtitle == sub,
                                    onClick = { selectedSubtitle = sub }
                                )
                            }
                        }

                        val sizeLabel = when {
                            stream.isHls() -> selectedVariant?.label
                            else -> stream.size.takeIf { it.isNotBlank() }
                                ?: (stream.sizeBytes ?: resolvedSizeBytes)?.let { bytes ->
                                    when {
                                        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
                                        bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
                                        else -> null
                                    }
                                }
                        }

                        if (sessionCookieDetected) {
                            DownloadUnavailableNotice(
                                message = "This source uses session cookies that can't be saved offline."
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .clickable {
                                        val hlsSelection = if (stream.isHls()) {
                                            HlsDownloadSelection(
                                                streamKeys = selectedVariant?.streamKeys.orEmpty(),
                                                qualityLabel = selectedVariant?.label
                                            )
                                        } else null
                                        onConfirm(stream, selectedSubtitle, hlsSelection)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                                    Text(
                                        text = if (sizeLabel != null) "Download ($sizeLabel)" else "Download",
                                        style = ArflixTypography.button,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
                    }
                }
            }
        }
    }
}

/** Disabled-state notice shown in place of the download button when a source can't be saved offline. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DownloadUnavailableNotice(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Not available for download",
                style = ArflixTypography.button,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
        Text(
            text = message,
            style = ArflixTypography.caption,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StreamDownloadRow(stream: StreamSource, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stream.displayTitle(),
                style = ArflixTypography.body.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stream.addonLabel(),
                    style = ArflixTypography.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (stream.quality.isNotBlank()) {
                    QualityChip(stream.quality)
                }
                if (stream.isHls()) {
                    QualityChip("HLS")
                }
                if (stream.size.isNotBlank()) {
                    Text(
                        text = stream.size,
                        style = ArflixTypography.caption,
                        color = TextSecondary
                    )
                }
            }
        }
        Icon(
            Icons.Default.Download,
            contentDescription = "Download",
            tint = Color.White,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(20.dp)
        )
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QualityChip(quality: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = quality,
            style = ArflixTypography.caption,
            color = Color.White,
            fontSize = 10.sp
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubtitleRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = ArflixTypography.body,
            color = if (isSelected) Color.White else TextSecondary
        )
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
}
