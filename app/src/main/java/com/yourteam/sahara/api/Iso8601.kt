package com.yourteam.sahara.api

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Epoch-millis <-> ISO-8601 UTC string, matching what FastAPI/Pydantic emits and
 * accepts for `datetime` fields. Hand-rolled rather than `java.time` because this
 * project's minSdk (24) has no `java.time` without core library desugaring, which
 * isn't enabled. Not thread-shared: [SimpleDateFormat] is not thread-safe, so each
 * call gets its own instance. */
object Iso8601 {
    private const val PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    private fun formatter() = SimpleDateFormat(PATTERN, Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun fromMillis(millis: Long): String = formatter().format(Date(millis))

    /** Parses either the format this class writes, or the second-precision variant
     * FastAPI/Pydantic can also emit (no milliseconds). Falls back to `System.currentTimeMillis()`
     * only if the string is unparseable, since a game result timestamp is never itself
     * safety-critical -- but never silently drops a result over a formatting mismatch. */
    fun toMillis(iso: String): Long {
        val candidates = listOf(PATTERN, "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (pattern in candidates) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
                return parser.parse(iso)?.time ?: continue
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return System.currentTimeMillis()
    }
}
