package com.muvio.shared.player

import com.muvio.shared.domain.StreamSource
import com.muvio.shared.domain.Subtitle

/**
 * Android implementation — delegates to Media3 ExoPlayer.
 * The actual ExoPlayer instance is created and managed by the Compose
 * AndroidView in PlayerScreen to keep it on the main thread.
 * This class provides the bridge between shared logic and platform player.
 */
actual class PlayerEngine actual constructor() {

    private var listener: PlayerEngineListener? = null

    // These are set by the ExoPlayer wrapper in the Compose layer.
    @Volatile actual var currentPositionMs: Long = 0L
    @Volatile actual var durationMs: Long = 0L
    @Volatile actual var isPlaying: Boolean = false

    actual fun setListener(listener: PlayerEngineListener?) {
        this.listener = listener
    }

    actual fun prepare(stream: StreamSource, startPositionMs: Long) {
        // Forwarded to ExoPlayer via AndroidPlayerBridge (set up in PlayerScreen)
        pendingStream = stream
        pendingStartMs = startPositionMs
    }

    actual fun play() { pendingCommand = PlayerCommand.PLAY }
    actual fun pause() { pendingCommand = PlayerCommand.PAUSE }
    actual fun seekTo(positionMs: Long) { pendingSeekMs = positionMs }
    actual fun setSubtitle(subtitle: Subtitle?) { pendingSubtitle = subtitle }

    actual fun release() {
        listener = null
        pendingStream = null
        pendingCommand = null
    }

    // Called back by the ExoPlayer wrapper in the Compose layer
    fun dispatchProgress(positionMs: Long, durMs: Long) {
        currentPositionMs = positionMs
        durationMs = durMs
        listener?.onProgress(positionMs, durMs)
    }

    fun dispatchPlayState(playing: Boolean) {
        isPlaying = playing
        listener?.onPlayStateChanged(playing)
    }

    fun dispatchBuffering(buffering: Boolean) {
        listener?.onBufferingChanged(buffering)
    }

    fun dispatchError(message: String) {
        listener?.onError(message)
    }

    fun dispatchEnded() {
        listener?.onEnded()
    }

    // Pending operations consumed by the Compose ExoPlayer wrapper
    @Volatile var pendingStream: StreamSource? = null
    @Volatile var pendingStartMs: Long = 0L
    @Volatile var pendingCommand: PlayerCommand? = null
    @Volatile var pendingSeekMs: Long? = null
    @Volatile var pendingSubtitle: Subtitle? = null
}

enum class PlayerCommand { PLAY, PAUSE }
