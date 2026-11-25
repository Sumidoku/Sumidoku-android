package org.sumidoku.android.core.common

/** Mirrors webui lib/format.ts `hasChapterNumber`: a chapter number counts as
 *  set only when it is a finite, non-zero number (some sources use 0 as a
 *  sentinel for "no number"). */
fun hasChapterNumber(n: Float?): Boolean = n != null && n.isFinite() && n != 0f

private fun fmtChapterNumber(n: Float): String =
    if (n % 1f == 0f) n.toLong().toString() else n.toString()

/** Number-only label: `Vol. X · Ch. X` → single part → EMPTY when the chapter
 *  has neither number. Unlike [chapterLabel] there is no "untitled chapter"
 *  fallback — secondary labels (reader chrome, drawer rows) simply hide when
 *  empty, mirroring the detail page and webui. */
fun chapterNumberLabel(volumeNumber: Float?, chapterNumber: Float?): String {
    val zh = isZhLocale
    val hant = isHantLocale
    return buildList {
        if (volumeNumber != null) {
            val number = fmtChapterNumber(volumeNumber)
            // 卷 is written identically in Simplified and Traditional.
            add(if (zh) "第 $number 卷" else "Vol. $number")
        }
        if (hasChapterNumber(chapterNumber)) {
            val number = fmtChapterNumber(chapterNumber!!)
            add(if (hant) "第 $number 話" else if (zh) "第 $number 话" else "Ch. $number")
        }
    }.joinToString(" · ")
}

/** Mirrors webui lib/format.ts `chapterLabel`:
 *  `Vol. X · Ch. X · title` → parts only → title only → "Untitled chapter". */
fun chapterLabel(volumeNumber: Float?, chapterNumber: Float?, title: String?): String {
    val parts = chapterNumberLabel(volumeNumber, chapterNumber)
    val trimmed = title?.trim().orEmpty()
    return when {
        parts.isNotEmpty() && trimmed.isNotEmpty() -> "$parts · $trimmed"
        parts.isNotEmpty() -> parts
        trimmed.isNotEmpty() -> trimmed
        else -> if (isHantLocale) "未命名章節" else if (isZhLocale) "未命名章节" else "Untitled chapter"
    }
}
