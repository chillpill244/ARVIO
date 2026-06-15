package com.muvio.shared

/**
 * First platform seam for the muvio KMP module. Mirrors the expect/actual
 * pattern we'll use for the heavier seams (HTTP engine, player, downloads,
 * JS runtime, storage).
 */
interface Platform {
    val name: String
}

expect fun platform(): Platform
