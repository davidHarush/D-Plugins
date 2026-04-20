package com.d.h.plugins.applogs.util

import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object AppScreenshotsPaths {
    const val FILE_PREFIX: String = "dplugins_screenshot_"

    private val fileNameFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    fun sanitizeSegment(value: String?, fallback: String): String {
        val sanitized = value
            .orEmpty()
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.', '-')

        return sanitized.ifBlank { fallback }
    }

    fun createScreenshotFilePath(
        targetDirectory: Path,
        deviceName: String,
        serialNumber: String,
        capturedAt: ZonedDateTime,
    ): Path {
        val safeDeviceName = sanitizeSegment(deviceName, fallback = "device")
        val safeSerialNumber = sanitizeSegment(serialNumber, fallback = "serial")
        val timestamp = fileNameFormatter.format(capturedAt.withZoneSameInstant(ZoneId.systemDefault()))
        return targetDirectory.resolve("${safeDeviceName}_${timestamp}.png")
    }

    fun countPluginScreenshots(targetDirectory: Path): Int {
        if (!Files.isDirectory(targetDirectory)) {
            return 0
        }

        return Files.list(targetDirectory).use { entries ->
            entries.filter { entry -> isPluginScreenshot(entry) }.count().toInt()
        }
    }

    fun deletePluginScreenshots(targetDirectory: Path): Int {
        if (!Files.isDirectory(targetDirectory)) {
            return 0
        }

        var deletedCount = 0
        Files.list(targetDirectory).use { entries ->
            entries.filter(::isPluginScreenshot).forEach { entry ->
                if (Files.deleteIfExists(entry)) {
                    deletedCount += 1
                }
            }
        }
        return deletedCount
    }

    fun isPluginScreenshot(path: Path): Boolean {
        if (Files.isDirectory(path)) {
            return false
        }

        val fileName = path.fileName?.toString().orEmpty()
        return fileName.startsWith(FILE_PREFIX) && fileName.endsWith(".png")
    }
}
