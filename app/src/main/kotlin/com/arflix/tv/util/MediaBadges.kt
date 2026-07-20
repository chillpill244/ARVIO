package com.arflix.tv.util

import kotlinx.datetime.todayIn

import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import java.util.Locale

private val genreWordStartRegex = Regex("(^|[\\s/&-])([\\p{L}])")
private val tvWordRegex = Regex("\\bTv\\b")

fun isInCinema(item: MediaItem): Boolean {
    if (item.mediaType != MediaType.MOVIE) return false
    val releaseDate = item.releaseDate?.takeIf { it.isNotBlank() } ?: return false
    val parsedDate = kotlin.runCatching {
        kotlinx.datetime.LocalDate.parse(releaseDate)
    }.getOrNull() ?: return false

    val now = kotlinx.datetime.Clock.System.todayIn(kotlinx.datetime.TimeZone.UTC)
    if (parsedDate > now) return false
    return (now.toEpochDays() - parsedDate.toEpochDays()) < 60
}

fun parseRatingValue(raw: String): Float {
    if (raw.isBlank()) return 0f
    return raw.trim().replace(',', '.').toFloatOrNull() ?: 0f
}

fun formatGenreName(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    val titled = trimmed.lowercase(Locale.ROOT).replace(genreWordStartRegex) { match ->
        match.groupValues[1] + match.groupValues[2].replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
    return titled.replace(tvWordRegex, "TV")
}
