package com.muvio.shared.player

import com.muvio.shared.domain.StreamSource
import com.muvio.shared.domain.Subtitle

/** Callback contract from the platform player to the ViewModel layer. */
interface PlayerEngineListener {
    fun onProgress(progressMs: Long, durationMs: Long)
    fun onPlayStateChanged(isPlaying: Boolean)
    fun onBufferingChanged(isBuffering: Boolean)
    fun onError(message: String)
    fun onEnded()
}

/**
 * Platform-provided player engine. On Android this wraps Media3/ExoPlayer;
 * on iOS it wraps libmpv via MPVPlayerBridge.
 *
 * Lifecycle: create once per player screen, call [release] when the screen exits.
 */
expect class PlayerEngine() {
    fun prepare(stream: StreamSource, startPositionMs: Long)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSubtitle(subtitle: Subtitle?)
    fun release()
    fun setListener(listener: PlayerEngineListener?)
    var currentPositionMs: Long
    var durationMs: Long
    var isPlaying: Boolean
}
