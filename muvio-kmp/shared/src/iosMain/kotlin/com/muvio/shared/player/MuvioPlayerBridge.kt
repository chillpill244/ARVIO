package com.muvio.shared.player

import platform.UIKit.UIViewController

interface MuvioPlayerBridge {
    fun createPlayerViewController(): UIViewController
    fun loadFileWithAudio(videoUrl: String, audioUrl: String?, headersJson: String?)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun destroy()
    fun getIsLoading(): Boolean
    fun getIsPlaying(): Boolean
    fun getIsEnded(): Boolean
    fun getDurationMs(): Long
    fun getPositionMs(): Long
    fun getBufferedMs(): Long
    fun getErrorMessage(): String
}

interface MuvioPlayerBridgeCreator {
    fun createBridge(): MuvioPlayerBridge
}

object MuvioPlayerBridgeFactory {
    private var creator: MuvioPlayerBridgeCreator? = null

    fun registerCreator(creator: MuvioPlayerBridgeCreator) {
        this.creator = creator
    }

    fun create(): MuvioPlayerBridge? = creator?.createBridge()

    val isRegistered: Boolean get() = creator != null
}
