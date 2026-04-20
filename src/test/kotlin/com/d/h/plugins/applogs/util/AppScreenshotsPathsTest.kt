package com.d.h.plugins.applogs.util

import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppScreenshotsPathsTest {
    @Test
    fun `create screenshot path uses plugin prefix and png extension`() {
        val targetDirectory = Files.createTempDirectory("app-screenshots-target")
        val capturedAt = ZonedDateTime.of(2026, 4, 20, 10, 15, 30, 0, ZoneId.of("Asia/Jerusalem"))

        val screenshotFile = AppScreenshotsPaths.createScreenshotFilePath(
            targetDirectory = targetDirectory,
            deviceName = "Pixel 8 Pro / API 35",
            serialNumber = "emulator-5554",
            capturedAt = capturedAt,
        )

        assertEquals(
            "dplugins_screenshot_Pixel_8_Pro_API_35_emulator-5554_2026-04-20_10-15-30.png",
            screenshotFile.fileName.toString(),
        )
        assertEquals(targetDirectory, screenshotFile.parent)
    }

    @Test
    fun `sanitize segment removes invalid filename characters`() {
        val sanitized = AppScreenshotsPaths.sanitizeSegment("Pixel 8 / API 35", fallback = "device")

        assertEquals("Pixel_8_API_35", sanitized)
    }

    @Test
    fun `delete plugin screenshots removes only plugin png files in target folder`() {
        val targetDirectory = Files.createTempDirectory("app-screenshots-delete")
        val nestedDirectory = targetDirectory.resolve("nested").createDirectories()
        val screenshotFile = targetDirectory.resolve("dplugins_screenshot_pixel_8_emulator-5554_2026-04-20_10-15-30.png")
        val unrelatedFile = targetDirectory.resolve("notes.txt")
        val nestedScreenshot = nestedDirectory.resolve("dplugins_screenshot_nested.png")

        Files.writeString(screenshotFile, "delete")
        Files.writeString(unrelatedFile, "keep")
        Files.writeString(nestedScreenshot, "keep")

        val deletedCount = AppScreenshotsPaths.deletePluginScreenshots(targetDirectory)

        assertEquals(1, deletedCount)
        assertFalse(screenshotFile.exists())
        assertTrue(unrelatedFile.exists())
        assertTrue(nestedScreenshot.exists())
        assertEquals(listOf("nested", "notes.txt"), targetDirectory.listDirectoryEntries().map { it.fileName.toString() }.sorted())
    }
}
