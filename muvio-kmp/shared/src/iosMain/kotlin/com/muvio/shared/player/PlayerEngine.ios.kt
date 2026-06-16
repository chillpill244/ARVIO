package com.muvio.shared.player

import com.muvio.shared.domain.StreamSource
import com.muvio.shared.domain.Subtitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual class PlayerEngine actual constructor() {

    private var listener: PlayerEngineListener? = null
    actual var currentPositionMs: Long = 0L
    actual var durationMs: Long = 0L
    actual var isPlaying: Boolean = false

    private var bridge: MuvioPlayerBridge? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pollJob: Job? = null

    actual fun setListener(listener: PlayerEngineListener?) {
        this.listener = listener
    }

    actual fun prepare(stream: StreamSource, startPositionMs: Long) {
        bridge?.destroy()

        val newBridge = MuvioPlayerBridgeFactory.create() ?: return
        bridge = newBridge

        val requestHeaders = stream.behaviorHints?.proxyHeaders?.request.orEmpty()
        val headersJson = requestHeaders
            .takeIf { it.isNotEmpty() }
            ?.let { runCatching { Json.encodeToString(it) }.getOrNull() }

        newBridge.loadFileWithAudio(
            videoUrl = stream.url ?: return,
            audioUrl = null,
            headersJson = headersJson,
        )
        if (startPositionMs > 0L) {
            newBridge.seekTo(startPositionMs)
        }
        newBridge.play()
        startPolling(newBridge)
    }

    actual fun play() {
        bridge?.play()
        isPlaying = true
        listener?.onPlayStateChanged(true)
    }

    actual fun pause() {
        bridge?.pause()
        isPlaying = false
        listener?.onPlayStateChanged(false)
    }

    actual fun seekTo(positionMs: Long) {
        bridge?.seekTo(positionMs)
        currentPositionMs = positionMs
    }

    actual fun setSubtitle(subtitle: Subtitle?) {
        val url = subtitle?.url ?: return
        bridge?.loadFileWithAudio(url, null, null)
    }

    actual fun release() {
        stopPolling()
        bridge?.destroy()
        bridge = null
        listener = null
        isPlaying = false
    }

    private fun startPolling(activeBridge: MuvioPlayerBridge) {
        stopPolling()
        pollJob = scope.launch {
            var lastError: String? = null
            while (isActive) {
                val pos = activeBridge.getPositionMs()
                val dur = activeBridge.getDurationMs()
                val playing = activeBridge.getIsPlaying()
                val loading = activeBridge.getIsLoading()
                val ended = activeBridge.getIsEnded()
                val errorMsg = activeBridge.getErrorMessage().ifBlank { null }

                currentPositionMs = pos
                durationMs = dur

                if (playing != isPlaying) {
                    isPlaying = playing
                    listener?.onPlayStateChanged(playing)
                }
                listener?.onProgress(pos, dur)
                listener?.onBufferingChanged(loading)
                if (ended) listener?.onEnded()
                if (errorMsg != lastError) {
                    lastError = errorMsg
                    if (errorMsg != null) listener?.onError(errorMsg)
                }
                delay(250L)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }
}
