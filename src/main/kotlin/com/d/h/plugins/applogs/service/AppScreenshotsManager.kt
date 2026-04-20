package com.d.h.plugins.applogs.service

import com.d.h.plugins.applogs.model.AppScreenshotsSnapshot
import com.d.h.plugins.applogs.model.ConnectedDeviceView
import com.d.h.plugins.applogs.state.AppScreenshotsSettingsState
import com.d.h.plugins.applogs.util.AppScreenshotsPaths
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class AppScreenshotsManager(
    private val project: Project,
) {
    private val logger = Logger.getInstance(AppScreenshotsManager::class.java)
    private val settings = ApplicationManager.getApplication().service<AppScreenshotsSettingsState>()
    private val connectedDevices = AtomicReference<List<ConnectedDeviceView>>(emptyList())
    private val loadingDevices = AtomicBoolean(false)

    fun snapshot(): AppScreenshotsSnapshot = AppScreenshotsSnapshot(
        connectedDevices = connectedDevices.get(),
        targetDirectory = settings.getTargetDirectory(),
        isLoadingDevices = loadingDevices.get(),
        adbAvailable = AndroidSdkUtils.getAdb(project) != null,
        pluginScreenshotCount = settings.getTargetDirectory()?.let(::countPluginScreenshotsSafely) ?: 0,
    )

    fun setTargetDirectory(targetDirectory: Path) {
        settings.setTargetDirectory(targetDirectory)
        publishSnapshot()
    }

    fun refreshDevices() {
        if (!loadingDevices.compareAndSet(false, true)) {
            return
        }

        publishSnapshot()
        AppExecutorUtil.getAppExecutorService().execute {
            try {
                connectedDevices.set(loadConnectedDevices())
            } finally {
                loadingDevices.set(false)
                publishSnapshot()
            }
        }
    }

    fun captureScreenshot(
        preferredSerialNumber: String?,
        onFinished: () -> Unit = {},
    ) {
        AppExecutorUtil.getAppExecutorService().execute {
            try {
                val adbExecutable = AndroidSdkUtils.getAdb(project) ?: run {
                    connectedDevices.set(emptyList())
                    notify(
                        title = "ADB not found",
                        content = "App Screenshots could not locate adb in the current Android SDK.",
                        type = NotificationType.ERROR,
                    )
                    return@execute
                }

                val latestDevices = loadConnectedDevices(adbExecutable)
                connectedDevices.set(latestDevices)

                val targetDirectory = settings.getTargetDirectory() ?: run {
                    notify(
                        title = "Target folder not set",
                        content = "Choose a target folder before capturing screenshots.",
                        type = NotificationType.WARNING,
                    )
                    return@execute
                }

                val device = latestDevices.firstOrNull { it.serialNumber == preferredSerialNumber } ?: latestDevices.firstOrNull() ?: run {
                    notify(
                        title = "No connected devices",
                        content = "Connect an Android device before capturing screenshots.",
                        type = NotificationType.WARNING,
                    )
                    return@execute
                }

                Files.createDirectories(targetDirectory)
                val screenshotFile = AppScreenshotsPaths.createScreenshotFilePath(
                    targetDirectory = targetDirectory,
                    deviceName = device.displayName,
                    serialNumber = device.serialNumber,
                    capturedAt = ZonedDateTime.now(),
                )

                saveScreenshot(adbExecutable, device.serialNumber, screenshotFile)
                notify(
                    title = "Screenshot saved",
                    content = "Saved ${screenshotFile.fileName} to $targetDirectory.",
                    type = NotificationType.INFORMATION,
                )
            } catch (throwable: Throwable) {
                logger.warn("Failed to capture Android screenshot", throwable)
                notify(
                    title = "Screenshot failed",
                    content = throwable.message ?: "App Screenshots failed to capture the device screen.",
                    type = NotificationType.ERROR,
                )
            } finally {
                publishSnapshot()
                finishOnUiThread(onFinished)
            }
        }
    }

    fun deletePluginScreenshots(onFinished: () -> Unit = {}) {
        AppExecutorUtil.getAppExecutorService().execute {
            try {
                val targetDirectory = settings.getTargetDirectory() ?: run {
                    notify(
                        title = "Target folder not set",
                        content = "Choose a target folder before deleting screenshots.",
                        type = NotificationType.WARNING,
                    )
                    return@execute
                }

                val deletedCount = AppScreenshotsPaths.deletePluginScreenshots(targetDirectory)
                val content = if (deletedCount == 0) {
                    "No App Screenshots files were found in $targetDirectory."
                } else {
                    "Deleted $deletedCount App Screenshots file(s) from $targetDirectory."
                }

                notify(
                    title = "Screenshots cleaned",
                    content = content,
                    type = NotificationType.INFORMATION,
                )
            } catch (throwable: Throwable) {
                logger.warn("Failed to delete App Screenshots files", throwable)
                notify(
                    title = "Delete failed",
                    content = throwable.message ?: "App Screenshots could not delete files from the target folder.",
                    type = NotificationType.ERROR,
                )
            } finally {
                publishSnapshot()
                finishOnUiThread(onFinished)
            }
        }
    }

    private fun loadConnectedDevices(adbExecutable: File? = AndroidSdkUtils.getAdb(project)): List<ConnectedDeviceView> {
        adbExecutable ?: return emptyList()
        val result = runAdbCommand(adbExecutable, "devices", "-l")
        if (result.exitCode != 0) {
            logger.info("adb devices -l failed: ${result.stderr.ifBlank { "unknown error" }}")
            return emptyList()
        }

        return ConnectedDevicesParser.parse(result.stdout)
    }

    private fun saveScreenshot(adbExecutable: File, serialNumber: String, targetFile: Path) {
        val process = ProcessBuilder(
            listOf(adbExecutable.absolutePath, "-s", serialNumber, "exec-out", "screencap", "-p"),
        ).start()

        val stderrFuture = AppExecutorUtil.getAppExecutorService().submit<String> {
            process.errorStream.bufferedReader().use { reader -> reader.readText() }
        }
        val stdoutFuture = AppExecutorUtil.getAppExecutorService().submit {
            Files.newOutputStream(
                targetFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { output ->
                process.inputStream.use { input -> input.copyTo(output) }
            }
        }

        val completed = process.waitFor(20, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            stdoutFuture.cancel(true)
            stderrFuture.cancel(true)
            Files.deleteIfExists(targetFile)
            error("adb screencap timed out for $serialNumber")
        }

        runCatching { stdoutFuture.get(5, TimeUnit.SECONDS) }
            .onFailure {
                Files.deleteIfExists(targetFile)
                throw IllegalStateException("Failed to read screenshot bytes for $serialNumber", it)
            }

        val stderr = runCatching { stderrFuture.get(5, TimeUnit.SECONDS) }.getOrDefault("")
        if (process.exitValue() != 0) {
            Files.deleteIfExists(targetFile)
            error(stderr.ifBlank { "adb screencap exited with code ${process.exitValue()} for $serialNumber" })
        }

        if (!Files.exists(targetFile) || Files.size(targetFile) == 0L) {
            Files.deleteIfExists(targetFile)
            error("adb returned an empty screenshot for $serialNumber")
        }
    }

    private fun runAdbCommand(adbExecutable: File, vararg args: String): CommandResult {
        val process = ProcessBuilder(listOf(adbExecutable.absolutePath, *args)).start()
        val completed = process.waitFor(10, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return CommandResult(stdout = "", stderr = "Timed out", exitCode = -1)
        }

        val stdout = ByteArrayOutputStream().use { output ->
            process.inputStream.use { input -> input.copyTo(output) }
            output.toString(Charsets.UTF_8)
        }
        val stderr = ByteArrayOutputStream().use { output ->
            process.errorStream.use { input -> input.copyTo(output) }
            output.toString(Charsets.UTF_8)
        }
        return CommandResult(stdout = stdout, stderr = stderr, exitCode = process.exitValue())
    }

    private fun countPluginScreenshotsSafely(targetDirectory: Path): Int {
        return runCatching {
            AppScreenshotsPaths.countPluginScreenshots(targetDirectory)
        }.getOrDefault(0)
    }

    private fun publishSnapshot() {
        if (project.isDisposed) {
            return
        }

        project.messageBus.syncPublisher(APP_SCREENSHOTS_STATE_TOPIC).stateChanged(snapshot())
    }

    private fun finishOnUiThread(onFinished: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(
            { onFinished() },
            { project.isDisposed },
        )
    }

    private fun notify(title: String, content: String, type: NotificationType) {
        Notifications.Bus.notify(
            Notification("App Logs Recorder", title, content, type),
            project,
        )
    }

    private data class CommandResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
    )
}
