package com.d.h.plugins.applogs.logcat

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LogcatLineFilterTest {
    private val filter = LogcatLineFilter(
        packageName = "com.example.app",
        sessionStartedAt = Instant.parse("2026-04-20T07:15:30Z"),
    )

    @Test
    fun `formats line when timestamp is inside session`() {
        val line = "2026-04-20 10:15:31.123  1234  1234 I MyApp: Started"
        val formatted = filter.formatForPersistence(
            line = line,
            processNames = mapOf(1234 to "com.example.app"),
        )

        assertNotNull(formatted)
        assertTrue(formatted == line)
    }

    @Test
    fun `rejects line before session start`() {
        val formatted = filter.formatForPersistence(
            line = "2026-04-20 10:15:29.123  1234  1234 I MyApp: Before session",
            processNames = mapOf(1234 to "com.example.app"),
        )

        assertNull(formatted)
    }

    @Test
    fun `rejects line from unrelated process`() {
        val formatted = filter.formatForPersistence(
            line = "2026-04-20 10:15:31.123  9876  9876 I SystemUI: Not ours",
            processNames = mapOf(9876 to "com.android.systemui"),
        )

        assertNull(formatted)
    }

    @Test
    fun `accepts app subprocess line`() {
        val formatted = filter.formatForPersistence(
            line = "2026-04-20 10:15:31.123  5678  5678 I Worker: Remote process",
            processNames = mapOf(5678 to "com.example.app:worker"),
        )

        assertNotNull(formatted)
    }

    @Test
    fun `accepts session line when adb already filters by package uid`() {
        val uidFiltered = LogcatLineFilter(
            packageName = "com.example.app",
            sessionStartedAt = Instant.parse("2026-04-20T07:15:30Z"),
            packageFiltered = true,
        )

        val formatted = uidFiltered.formatForPersistence(
            line = "2026-04-20 10:15:31.123  4321  8765 I Worker: Accepted without pid map",
            processNames = emptyMap(),
        )

        assertNotNull(formatted)
    }

    @Test
    fun `keeps continuation line after accepted structured line`() {
        assertNotNull(
            filter.formatForPersistence(
                line = "2026-04-20 10:15:31.123  1234  1234 E MyApp: java.lang.RuntimeException",
                processNames = mapOf(1234 to "com.example.app"),
            ),
        )

        val continuation = filter.formatForPersistence(
            line = "\tat com.example.app.MainActivity.onCreate(MainActivity.kt:42)",
            processNames = emptyMap(),
        )

        assertNotNull(continuation)
        assertTrue(continuation.contains("MainActivity.kt:42"))
    }

    @Test
    fun `parse extracts timestamp and pid`() {
        val parsed = filter.parse("2026-04-20 10:15:31.123  1234  1234 D MyApp: Parsed")

        assertNotNull(parsed)
        assertTrue(parsed.instant >= Instant.parse("2026-04-20T07:15:31Z"))
        assertTrue(parsed.pid == 1234)
        assertTrue(parsed.tid == 1234)
        assertTrue(parsed.tag == "MyApp")
        assertTrue(parsed.message == "Parsed")
    }

    @Test
    fun `parse supports timezone with colon`() {
        val parsed = filter.parse("2026-04-20 10:15:31.123+03:00  1234  1444 W MyApp: Zoned")

        assertNotNull(parsed)
        assertTrue(parsed.pid == 1234)
        assertTrue(parsed.tid == 1444)
        assertTrue(parsed.priority == "W")
    }
}
