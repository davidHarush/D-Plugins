package com.d.h.plugins.applogs.service

import com.d.h.plugins.applogs.logcat.LogcatLineFilter
import com.d.h.plugins.applogs.model.LogSessionDescriptor
import com.d.h.plugins.applogs.model.StopReason
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.BufferedWriter
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class AdbLogcatRecorder(
    private val descriptor: LogSessionDescriptor,
    private val adbExecutable: File,
    private val onStopped: (StopReason, String?) -> Unit,
) {
    private val logger = Logger.getInstance(AdbLogcatRecorder::class.java)
    private val executor: ExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor(
        "App Logs Recorder ${descriptor.sessionId}",
        4,
    )
    private val writeLock = Any()
    private val stopped = AtomicBoolean(false)
    private val processNames = AtomicReference(emptyMap<Int, String>())
    private val logFilter = AtomicReference<LogcatLineFilter?>()

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var logcatProcess: Process? = null

    @Volatile
    private var stdoutFuture: Future<*>? = null

    @Volatile
    private var stderrFuture: Future<*>? = null

    @Volatile
    private var processNamesFuture: Future<*>? = null

    @Volatile
    private var processMonitorFuture: Future<*>? = null

    fun start() {
        Files.createDirectories(descriptor.logFile.parent)
        writer = Files.newBufferedWriter(
            descriptor.logFile,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )

        val packageUid = queryPackageUid()
        if (packageUid != null) {
            logger.info("Resolved UID $packageUid for ${descriptor.packageName}; starting logcat with UID filtering for ${descriptor.sessionId}")
        } else {
            logger.info("Unable to resolve UID for ${descriptor.packageName}; falling back to process-name filtering for ${descriptor.sessionId}")
        }

        logFilter.set(
            LogcatLineFilter(
                packageName = descriptor.packageName,
                sessionStartedAt = queryDeviceTime() ?: descriptor.startedAt,
                packageFiltered = packageUid != null,
            ),
        )
        processNames.set(if (packageUid == null) waitForRelevantProcesses() else emptyMap())

        val process = ProcessBuilder(buildLogcatCommand(packageUid)).start()

        logcatProcess = process
        stdoutFuture = executor.submit { consumeStdout(process) }
        stderrFuture = executor.submit { consumeStderr(process) }
        processNamesFuture = if (packageUid == null) {
            executor.submit { pollProcessNames() }
        } else {
            null
        }
        processMonitorFuture = executor.submit { monitorLogcatProcess(process) }
    }

    fun stop(reason: StopReason) {
        stopInternal(reason, null)
    }

    private fun stopInternal(reason: StopReason, details: String?) {
        if (!stopped.compareAndSet(false, true)) {
            return
        }

        processNamesFuture?.cancel(true)
        logcatProcess?.destroy()
        waitFor(stdoutFuture)
        waitFor(stderrFuture)
        waitForProcessExit()

        closeWriter()
        executor.shutdownNow()
        onStopped(reason, details)
    }

    private fun consumeStdout(process: Process) {
        val filter = logFilter.get() ?: return
        process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (stopped.get()) {
                    return@forEach
                }
                filter.formatForPersistence(line, processNames.get())?.let(::appendLine)
            }
        }
    }

    private fun consumeStderr(process: Process) {
        process.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (!stopped.get()) {
                    logger.warn("adb logcat stderr for ${descriptor.sessionId}: $line")
                }
            }
        }
    }

    private fun pollProcessNames() {
        while (!stopped.get()) {
            processNames.set(queryProcessNames())
            try {
                TimeUnit.SECONDS.sleep(1)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    private fun monitorLogcatProcess(process: Process) {
        val exitCode = process.waitFor()
        if (!stopped.get()) {
            stopInternal(
                StopReason.DEVICE_DISCONNECTED,
                "adb logcat exited unexpectedly with code $exitCode",
            )
        }
    }

    private fun waitForRelevantProcesses(): Map<Int, String> {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var latestProcessNames = queryProcessNames()

        while (!stopped.get()) {
            if (latestProcessNames.values.any(::isRelevantProcessName)) {
                return latestProcessNames
            }
            if (System.nanoTime() >= deadlineNanos) {
                return latestProcessNames
            }

            try {
                TimeUnit.MILLISECONDS.sleep(250)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return latestProcessNames
            }

            latestProcessNames = queryProcessNames()
        }

        return latestProcessNames
    }

    private fun buildLogcatCommand(packageUid: Int?): List<String> {
        return buildList {
            add(adbExecutable.absolutePath)
            add("-s")
            add("logcat")
            packageUid?.let { add("--uid=$it") }
            add("-v")
            add("threadtime")
            add("-v")
            add("year")
        }
    }

    private fun queryDeviceTime(): Instant? {
        val result = runAdbCommand("shell", "date", "+%Y-%m-%dT%H:%M:%S%z")
        if (result.exitCode != 0) {
            logger.info("Falling back to host time for ${descriptor.sessionId}: ${result.stderr.ifBlank { "date command failed" }}")
            return null
        }

        val rawTimestamp = result.stdout.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (rawTimestamp.isBlank()) {
            return null
        }

        return runCatching {
            OffsetDateTime.parse(rawTimestamp, deviceTimeFormatter).toInstant()
        }.onFailure { throwable ->
            logger.info("Unable to parse device time '$rawTimestamp' for ${descriptor.sessionId}", throwable)
        }.getOrNull()
    }

    private fun queryPackageUid(): Int? {
        parseUidFromPackageList(
            runAdbCommand(
                "shell",
                "cmd",
                "package",
                "list",
                "packages",
                "-U",
                descriptor.packageName,
            ).stdout,
        )?.let { return it }

        return parseUidFromPackageDump(
            runAdbCommand(
                "shell",
                "dumpsys",
                "package",
                descriptor.packageName,
            ).stdout,
        )
    }

    private fun parseUidFromPackageList(output: String): Int? {
        val packagePattern = Regex("^package:${Regex.escape(descriptor.packageName)}(?:\\s|$).*\\buid:(\\d+)\\b")
        return output.lineSequence()
            .mapNotNull { line ->
                packagePattern.find(line.trim())?.groupValues?.get(1)?.toIntOrNull()
            }
            .firstOrNull()
    }

    private fun parseUidFromPackageDump(output: String): Int? {
        val patterns = listOf(
            Regex("\\buserId=(\\d+)\\b"),
            Regex("\\bappId=(\\d+)\\b"),
        )

        return patterns.asSequence()
            .mapNotNull { pattern ->
                pattern.find(output)?.groupValues?.get(1)?.toIntOrNull()
            }
            .firstOrNull()
    }

    private fun queryProcessNames(): Map<Int, String> {
        val psAllOutput = runAdbCommand("shell", "ps", "-A")
        val processNames = parseProcessNames(psAllOutput.stdout)
        if (processNames.isNotEmpty()) {
            return processNames
        }

        val psOutput = runAdbCommand("shell", "ps")
        return parseProcessNames(psOutput.stdout)
    }

    private fun parseProcessNames(output: String): Map<Int, String> {
        return output
            .lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val columns = line.trim().split(Regex("\\s+"))
                if (columns.isEmpty()) {
                    return@mapNotNull null
                }

                val processName = columns.lastOrNull().orEmpty()
                val pid = columns.firstOrNull { column -> column.toIntOrNull() != null }?.toIntOrNull()
                if (pid == null || processName.isBlank()) {
                    null
                } else {
                    pid to processName
                }
            }
            .toMap()
    }

    private fun isRelevantProcessName(processName: String?): Boolean {
        if (processName.isNullOrBlank()) {
            return false
        }

        return processName == descriptor.packageName || processName.startsWith("${descriptor.packageName}:")
    }

    private fun runAdbCommand(vararg args: String): CommandResult {
        val process = ProcessBuilder(listOf(adbExecutable.absolutePath, "-s", descriptor.serialNumber, *args)).start()
        val completed = process.waitFor(10, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return CommandResult(stdout = "", stderr = "Timed out", exitCode = -1)
        }

        val stdout = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val stderr = process.errorStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return CommandResult(stdout = stdout, stderr = stderr, exitCode = process.exitValue())
    }

    private fun appendLine(line: String) {
        synchronized(writeLock) {
            writer?.apply {
                write(line)
                newLine()
                flush()
            }
        }
    }

    private fun closeWriter() {
        synchronized(writeLock) {
            writer?.close()
            writer = null
        }
    }

    private fun waitFor(task: Future<*>?) {
        if (task == null) {
            return
        }

        runCatching {
            task.get(5, TimeUnit.SECONDS)
        }.onFailure { throwable ->
            logger.debug("Ignoring recorder task shutdown failure for ${descriptor.sessionId}", throwable)
        }
    }

    private fun waitForProcessExit() {
        val process = logcatProcess ?: return
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
    }

    private data class CommandResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
    )

    companion object {
        private val deviceTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
    }
}
