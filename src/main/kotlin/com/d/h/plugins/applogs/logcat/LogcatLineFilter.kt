package com.d.h.plugins.applogs.logcat

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LogcatLineFilter(
    private val packageName: String,
    private val sessionStartedAt: Instant,
    private val packageFiltered: Boolean = false,
) {
    private val linePattern = Regex(
        pattern = "^(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})(?:\\s*([+-]\\d{2}:?\\d{2}|Z))?\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEAF])\\s+(.+?):\\s?(.*)$",
    )
    private val timestampFormatterWithCompactZone = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private val timestampFormatterWithColonZone = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private val timestampFormatterWithoutZone = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

    private var lastAcceptedLine: Boolean = false

    fun formatForPersistence(line: String, processNames: Map<Int, String>): String? {
        val parsed = parse(line)
        if (parsed == null) {
            return line.takeIf { lastAcceptedLine }
        }

        val accepted = !parsed.instant.isBefore(sessionStartedAt) &&
            (packageFiltered || isAppProcess(processNames[parsed.pid]))
        lastAcceptedLine = accepted
        if (!accepted) {
            return null
        }

        return line
    }

    fun parse(line: String): ParsedLogcatLine? {
        val match = linePattern.matchEntire(line) ?: return null
        val date = match.groupValues[1]
        val time = match.groupValues[2]
        val zone = match.groupValues[3].ifBlank { null }
        val timestamp = parseTimestamp(date, time, zone)
        return ParsedLogcatLine(
            instant = timestamp.toInstant(),
            date = date,
            time = time,
            pid = match.groupValues[4].toInt(),
            tid = match.groupValues[5].toInt(),
            priority = match.groupValues[6],
            tag = match.groupValues[7],
            message = match.groupValues[8],
        )
    }

    private fun parseTimestamp(date: String, time: String, zone: String?): OffsetDateTime {
        val base = "${date}T${time}"
        return when {
            zone == null -> LocalDateTime.parse(base, timestampFormatterWithoutZone).atZone(ZoneId.systemDefault()).toOffsetDateTime()
            zone.contains(':') || zone == "Z" -> OffsetDateTime.parse("$base$zone", timestampFormatterWithColonZone)
            else -> OffsetDateTime.parse("$base$zone", timestampFormatterWithCompactZone)
        }
    }

    private fun isAppProcess(processName: String?): Boolean {
        if (processName.isNullOrBlank()) {
            return false
        }

        return processName == packageName || processName.startsWith("$packageName:")
    }
}

data class ParsedLogcatLine(
    val instant: Instant,
    val date: String,
    val time: String,
    val pid: Int,
    val tid: Int,
    val priority: String,
    val tag: String,
    val message: String,
)
