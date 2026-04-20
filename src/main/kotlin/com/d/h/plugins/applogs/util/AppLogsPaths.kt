package com.d.h.plugins.applogs.util

import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.name

object AppLogsPaths {
    const val DIRECTORY_NAME: String = "AppLogs"

    private val fileNameFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    fun logsDirectory(projectBasePath: Path): Path = projectBasePath.resolve(DIRECTORY_NAME)

    fun ensureLogsDirectory(projectBasePath: Path): Path {
        val directory = logsDirectory(projectBasePath)
        Files.createDirectories(directory)
        return directory
    }

    fun sanitizeDeviceName(deviceName: String?): String {
        val sanitized = deviceName
            .orEmpty()
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.', '-')

        return sanitized.ifBlank { "device" }
    }

    fun createLogFilePath(projectBasePath: Path, deviceName: String, startedAt: ZonedDateTime): Path {
        val directory = ensureLogsDirectory(projectBasePath)
        val safeDeviceName = sanitizeDeviceName(deviceName)
        val timestamp = fileNameFormatter.format(startedAt.withZoneSameInstant(ZoneId.systemDefault()))
        return directory.resolve("${safeDeviceName}_${timestamp}.txt")
    }

    fun deleteLogContents(logsDirectory: Path) {
        if (!Files.exists(logsDirectory)) {
            Files.createDirectories(logsDirectory)
            return
        }

        Files.list(logsDirectory).use { entries ->
            entries.forEach { entry ->
                deleteRecursively(entry)
            }
        }
    }

    private fun deleteRecursively(path: Path) {
        if (Files.isDirectory(path)) {
            Files.list(path).use { children ->
                children.forEach { child ->
                    deleteRecursively(child)
                }
            }
        }

        if (path.name != DIRECTORY_NAME || !Files.isDirectory(path)) {
            Files.deleteIfExists(path)
        }
    }
}
