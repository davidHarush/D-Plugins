package com.d.h.plugins.applogs.util

import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLogsPathsTest {
    @Test
    fun `sanitize device name removes invalid characters`() {
        val sanitized = AppLogsPaths.sanitizeDeviceName("Pixel 8 Pro/API 35")

        assertEquals("Pixel_8_Pro_API_35", sanitized)
    }

    @Test
    fun `ensure logs directory creates AppLogs under project root`() {
        val projectRoot = Files.createTempDirectory("app-logs-paths")

        val logsDirectory = AppLogsPaths.ensureLogsDirectory(projectRoot)

        assertTrue(logsDirectory.exists())
        assertEquals(AppLogsPaths.DIRECTORY_NAME, logsDirectory.name)
    }

    @Test
    fun `create log file path uses sanitized device name and timestamp`() {
        val projectRoot = Files.createTempDirectory("app-logs-file-name")
        val startedAt = ZonedDateTime.of(2026, 4, 20, 10, 15, 30, 0, ZoneId.of("Asia/Jerusalem"))

        val logFile = AppLogsPaths.createLogFilePath(projectRoot, "Pixel 8 / API 35", startedAt)

        assertEquals("Pixel_8_API_35_2026-04-20_10-15-30.txt", logFile.fileName.toString())
        assertTrue(logFile.parent.exists())
    }

    @Test
    fun `delete log contents removes only children inside AppLogs`() {
        val projectRoot = Files.createTempDirectory("app-logs-delete")
        val logsDirectory = AppLogsPaths.ensureLogsDirectory(projectRoot)
        val nestedDirectory = logsDirectory.resolve("nested").createDirectories()
        val rootFile = projectRoot.resolve("keep.txt")
        val logFile = logsDirectory.resolve("session.txt")
        val nestedLogFile = nestedDirectory.resolve("session-2.txt")

        Files.writeString(rootFile, "keep")
        Files.writeString(logFile, "delete")
        Files.writeString(nestedLogFile, "delete")

        AppLogsPaths.deleteLogContents(logsDirectory)

        assertTrue(rootFile.exists())
        assertTrue(logsDirectory.exists())
        assertTrue(logsDirectory.listDirectoryEntries().isEmpty())
        assertFalse(logFile.exists())
        assertFalse(nestedLogFile.exists())
    }
}
