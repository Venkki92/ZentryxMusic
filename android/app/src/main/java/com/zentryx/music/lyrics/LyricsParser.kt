package com.zentryx.music.lyrics

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class ParsedLyrics(
    val lines: List<LyricLine> = emptyList(),
    val isSynchronized: Boolean = false,
    val plainText: String = ""
)

/**
 * Feature 5: Fast LRC parser with millisecond precision for MBUX karaoke display.
 * Parses [mm:ss.xx] and [mm:ss.xxx] timestamps and sorts chronologically.
 */
object LyricsParser {

    private val LRC_REGEX = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")

    fun parse(lrcContent: String): ParsedLyrics {
        if (lrcContent.isBlank()) return ParsedLyrics()

        val parsedLines = mutableListOf<LyricLine>()
        val plainLines = mutableListOf<String>()

        lrcContent.lines().forEach { line ->
            val match = LRC_REGEX.find(line.trim())
            if (match != null) {
                val (minStr, secStr, fracStr, text) = match.destructured
                val minutes = minStr.toLongOrNull() ?: 0L
                val seconds = secStr.toLongOrNull() ?: 0L
                val frac = if (fracStr.length == 2) {
                    (fracStr.toLongOrNull() ?: 0L) * 10L
                } else {
                    fracStr.toLongOrNull() ?: 0L
                }
                val totalMs = (minutes * 60 * 1000L) + (seconds * 1000L) + frac
                parsedLines.add(LyricLine(totalMs, text.trim()))
            } else {
                val cleaned = line.replace(Regex("""\[.*?\]"""), "").trim()
                if (cleaned.isNotBlank()) {
                    plainLines.add(cleaned)
                }
            }
        }

        return if (parsedLines.isNotEmpty()) {
            ParsedLyrics(
                lines = parsedLines.sortedBy { it.timestampMs },
                isSynchronized = true
            )
        } else {
            ParsedLyrics(
                lines = plainLines.mapIndexed { idx, txt -> LyricLine(idx * 3000L, txt) },
                isSynchronized = false,
                plainText = plainLines.joinToString("\n")
            )
        }
    }

    fun findActiveLineIndex(lines: List<LyricLine>, currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        for (i in lines.indices.reversed()) {
            if (currentPositionMs >= lines[i].timestampMs) {
                return i
            }
        }
        return 0
    }
}
