package org.sumidoku.android.core.common

import java.text.SimpleDateFormat
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
    return when {
        delta < minute -> "刚刚"
        delta < hour -> "${delta / minute} 分钟前"
        delta < day -> "${delta / hour} 小时前"
        delta < 30 * day -> "${delta / day} 天前"
        else -> formatEpochMs(ms)
    }
}
