package com.muvio.shared.util

import kotlin.math.absoluteValue

/** Format a Double to [decimals] decimal places without using String.format (KMP-safe). */
fun Double.toDecStr(decimals: Int): String {
    if (decimals <= 0) return toLong().toString()
    val factor = when (decimals) {
        1 -> 10L; 2 -> 100L; 3 -> 1000L; else -> 10L
    }
    val scaled = (this * factor + 0.5).toLong()
    val intPart = scaled / factor
    val fracPart = (scaled % factor).absoluteValue
    return "$intPart.${fracPart.toString().padStart(decimals, '0')}"
}

/** Format a Long as bytes into human-readable size string. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000_000L -> "${(bytes.toDouble() / 1_000_000_000_000.0).toDecStr(2)} TB"
    bytes >= 1_000_000_000L     -> "${(bytes.toDouble() / 1_000_000_000.0).toDecStr(2)} GB"
    bytes >= 1_000_000L         -> "${(bytes.toDouble() / 1_000_000.0).toDecStr(1)} MB"
    else                         -> "$bytes B"
}

/** Format milliseconds as M:SS or H:MM:SS (KMP-safe, no String.format). */
fun Long.toTimeString(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
