package com.muvio.shared.player

import com.muvio.shared.domain.StreamSource
import com.muvio.shared.domain.Subtitle

/**
 * iOS implementation — delegates to libmpv via MPVPlayerBridge.swift.
 * The Swift bridge is called through the Kotlin/Native cinterop layer
 * (configured in iosApp/muvio.xcodeproj once the Swift bridge is added).
 *
 * For now this stub satisfies the expect contract so :shared compiles.
 * Wire [MpvBridgeCompat] from the Xcode target in Phase 7.
 */
actual class PlayerEngine actual constructor() {

    private var listener: PlayerEngineListener? = null
    actual var currentPositionMs: Long = 0L
    actual var durationMs: Long = 0L
    actual var isPlaying: Boolean = false

    actual fun setListener(listener: PlayerEngineListener?) {
        this.listener = listener
    }

    actual fun prepare(stream: StreamSource, startPositionMs: Long) {
        // Will call MPVPlayerBridge.play(url:headers:startPosition:) via cinterop
        currentPositionMs = startPositionMs
    }

    actual fun play() {
        // mpvBridge?.play()
        isPlaying = true
        listener?.onPlayStateChanged(true)
    }

    actual fun pause() {
        // mpvBridge?.pause()
        isPlaying = false
        listener?.onPlayStateChanged(false)
    }

    actual fun seekTo(positionMs: Long) {
        // mpvBridge?.seek(toMs: positionMs)
        currentPositionMs = positionMs
    }

    actual fun setSubtitle(subtitle: Subtitle?) {
        // mpvBridge?.selectSubtitle(url: subtitle?.url)
    }

    actual fun release() {
        // mpvBridge?.release()
        listener = null
        isPlaying = false
    }
}
