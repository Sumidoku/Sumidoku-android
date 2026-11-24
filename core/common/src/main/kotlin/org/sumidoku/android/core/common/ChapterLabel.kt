package org.sumidoku.android.core.common

import java.util.Locale

/** Mirrors webui lib/format.ts `hasChapterNumber`: a chapter number counts as
 *  set only when it is a finite, non-zero number (some sources use 0 as a
 *  sentinel for "no number"). */
fun hasChapterNumber(n: Float?): Boolean = n != null && n.isFinite() && n != 0f

private fun fmtChapterNumber(n: Float): String =
    if (n % 1f == 0f) n.toLong().toString() else n.toString()

/** True when the device locale is Chinese. This module is pure Kotlin/JVM (no
 *  Android resources), so wording switches on the default locale: Chinese keeps
 *  the original text, every other locale gets English. */
private val isZhLocale: Boolean
    get() = Locale.getDefault().language == Locale.CHINESE.language

/** Mirrors webui lib/format.ts `chapterLabel`:
 *  `Vol. X · Ch. X · title` → parts only → title only → "Untitled chapter". */
fun chapterLabel(volumeNumber: Float?, chapterNumber: Float?, title: String?): String {
    val zh = isZhLocale
    val parts = buildList {
        if (volumeNumber != null) {
            val number = fmtChapterNumber(volumeNumber)
            add(if (zh) "第 $number 卷" else "Vol. $number")
        }
        if (hasChapterNumber(chapterNumber)) {
            val number = fmtChapterNumber(chapterNumber!!)
            add(if (zh) "第 $number 话" else "Ch. $number")
        }
    }
    val trimmed = title?.trim().orEmpty()
    return when {
        parts.isNotEmpty() && trimmed.isNotEmpty() -> "${parts.joinToString(" · ")} · $trimmed"
        parts.isNotEmpty() -> parts.joinToString(" · ")
        trimmed.isNotEmpty() -> trimmed
        else -> if (zh) "未命名章节" else "Untitled chapter"
    }
}
