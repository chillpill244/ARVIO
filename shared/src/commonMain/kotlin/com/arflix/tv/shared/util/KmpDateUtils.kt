package com.arflix.tv.shared.util

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

object KmpDateUtils {
    // "yyyy-MM-dd" -> epoch millis
    fun parseIsoDate(dateStr: String): Long {
        return try {
            LocalDate.parse(dateStr).let { LocalDateTime(it, LocalTime(0, 0)).toInstant(TimeZone.UTC).toEpochMilliseconds() }
        } catch(e: Exception) { 0L }
    }
    
    // "yyyy-MM-dd HH:mm:ss" -> epoch millis
    fun parseSqlDate(dateStr: String): Long {
        return try {
            LocalDateTime.parse(dateStr.replace(" ", "T")).toInstant(TimeZone.UTC).toEpochMilliseconds()
        } catch(e: Exception) { 0L }
    }
    
    // "yyyy-MM-dd" -> "d MMM yyyy"
    fun formatMediumDate(dateStr: String): String {
        return try {
            val d = LocalDate.parse(dateStr)
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            "${d.dayOfMonth} ${months[d.monthNumber-1]} ${d.year}"
        } catch(e: Exception) { dateStr }
    }
    
    // "yyyy-MM-dd" -> "MMMM d, yyyy"
    fun formatLongDate(dateStr: String): String {
        return try {
            val d = LocalDate.parse(dateStr)
            val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
            "${months[d.monthNumber-1]} ${d.dayOfMonth}, ${d.year}"
        } catch(e: Exception) { dateStr }
    }
    
    // epoch millis -> "MMM dd, yyyy 'at' h:mm a"
    fun formatSyncTime(ms: Long): String {
        return try {
            val local = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
            val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val amPm = if (local.hour >= 12) "PM" else "AM"
            val hour12 = if (local.hour % 12 == 0) 12 else local.hour % 12
            val min = local.minute.toString().padStart(2, '0')
            val day = local.dayOfMonth.toString().padStart(2, '0')
            "${monthNames[local.monthNumber - 1]} $day, ${local.year} at $hour12:$min $amPm"
        } catch (e: Exception) { "" }
    }
    
    // epoch millis -> "HH:mm" (24 hr)
    fun formatTime24h(ms: Long): String {
        return try {
            val local = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
            val h = local.hour.toString().padStart(2, '0')
            val m = local.minute.toString().padStart(2, '0')
            "$h:$m"
        } catch(e: Exception) { "" }
    }

    // Math: Get ISO date string X days ago
    fun getIsoDateDaysAgo(days: Int): String {
        return Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(DatePeriod(days = days)).toString()
    }
    
    // Math: Get ISO date string X months ago
    fun getIsoDateMonthsAgo(months: Int): String {
        return Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(DatePeriod(months = months)).toString()
    }
    
    // Math: Get ISO date string X years ago
    fun getIsoDateYearsAgo(years: Int): String {
        return Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(DatePeriod(years = years)).toString()
    }
    
    // Current time
    fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
    fun nowIsoString(): String = Clock.System.now().toString()
}
