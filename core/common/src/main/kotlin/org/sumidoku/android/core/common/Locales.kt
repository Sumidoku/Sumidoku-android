package org.sumidoku.android.core.common

import java.util.Locale

/**
 * Device-locale helpers for this pure Kotlin/JVM module (no Android
 * resources). Chinese locales keep Chinese wording; every other locale falls
 * back to English.
 */
internal val isZhLocale: Boolean
    get() = Locale.getDefault().language == Locale.CHINESE.language

/**
 * True for Traditional-Chinese locales: an explicit `Hant` script tag, or a
 * TW/HK/MO region when the locale carries no script information.
 */
internal val isHantLocale: Boolean
    get() {
        val locale = Locale.getDefault()
        if (locale.language != Locale.CHINESE.language) return false
        val script = locale.script
        return script.equals("Hant", ignoreCase = true) ||
            (script.isEmpty() && locale.country in setOf("TW", "HK", "MO"))
    }
