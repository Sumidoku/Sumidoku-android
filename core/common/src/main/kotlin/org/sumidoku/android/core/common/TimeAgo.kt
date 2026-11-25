package org.sumidoku.android.core.common

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Server timestamps are a mix of Unix seconds (history/backup/createdAt) and
 * Unix milliseconds (sync lastModifiedAt). Same heuristic as the web UI
 * (webui/src/lib/format.ts): below 1e12 it must be seconds.
 */
fun normalizeEpochMs(v: Long): Long = if (v < 1_000_000_000_000L) v * 1000 else v

/** Server timestamps are epoch milliseconds. */
fun formatEpochMs(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(normalizeEpochMs(ms)))

fun relativeEpochMs(ms: Long, now: Long = System.currentTimeMillis()): String {
    val delta = now - normalizeEpochMs(ms)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    val minutes = delta / minute
    val hours = delta / hour
    val days = delta / day
    return when {
        // 天前/今天/昨天 are written identically in Simplified and Traditional.
        delta < minute -> if (isHantLocale) "剛剛" else if (isZhLocale) "刚刚" else "just now"
        delta < hour -> if (isHantLocale) "$minutes 分鐘前" else if (isZhLocale) "$minutes 分钟前" else "$minutes min ago"
        delta < day -> if (isHantLocale) "$hours 小時前" else if (isZhLocale) "$hours 小时前" else "$hours hr ago"
        delta < 30 * day -> if (isZhLocale) "$days 天前" else "$days d ago"
        else -> formatEpochMs(ms)
    }
}

/** Local calendar day of a server timestamp — the bucket key feeds are grouped by. */
fun dayBucket(ms: Long): LocalDate =
    Instant.ofEpochMilli(normalizeEpochMs(ms)).atZone(ZoneId.systemDefault()).toLocalDate()

/** `今天`/`昨天`/`11 月 9 日` (zh) or `Today`/`Yesterday`/`Nov 9` (else), matching
 *  the preview 10/11 section headers. */
fun dayLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
    val day = dayBucket(ms)
    val today = dayBucket(now)
    return when (day) {
        today -> if (isZhLocale) "今天" else "Today"
        today.minusDays(1) -> if (isZhLocale) "昨天" else "Yesterday"
        else -> shortDate(day)
    }
}

/** `今天 · 11 月 11 日` (zh) / `Today · Nov 11` (else) — today/yesterday keep
 *  the date for orientation. */
fun dayHeader(ms: Long, now: Long = System.currentTimeMillis()): String {
    val day = dayBucket(ms)
    val today = dayBucket(now)
    val label = dayLabel(ms, now)
    return if (day == today || day == today.minusDays(1)) "$label · ${shortDate(day)}" else label
}

/** Month-day date without year: `11 月 9 日` for zh, `Nov 9` otherwise. */
private fun shortDate(day: LocalDate): String =
    if (isZhLocale) {
        "${day.monthValue} 月 ${day.dayOfMonth} 日"
    } else {
        day.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }

/** `21:12` — wall clock inside a day group. */
fun clockEpochMs(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(normalizeEpochMs(ms)))
