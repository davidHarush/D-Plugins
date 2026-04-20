package com.d.h.plugins.applogs.service

import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.d.h.plugins.applogs.model.LogSessionDescriptor
import com.d.h.plugins.applogs.model.LogSessionsSnapshot
import com.d.h.plugins.applogs.model.StopReason
import com.d.h.plugins.applogs.state.AppLogsSettingsState
import com.d.h.plugins.applogs.util.AppLogsPaths
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.actions.ShowFilePathAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class LogSessionManager(
    private val project: Project,
) : com.intellij.openapi.Disposable {
    private val logger = Logger.getInstance(LogSessionManager::class.java)
    private val settings = project.service<AppLogsSettingsState>()
    private val activeSessions = ConcurrentHashMap<String, ActiveLogSession>()
    private val startingExecutions = ConcurrentHashMap.newKeySet<Long>()
    private val attachedHandlers = ConcurrentHashMap.newKeySet<Long>()
    private val initialized = AtomicBoolean(false)

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) {
            return
        }

        project.messageBus.connect(this).subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
            override fun processStarted(
                executorId: String,
                env: ExecutionEnvironment,
                handler: ProcessHandler,
            ) {
                handleProcessStarted(executorId, env, handler)
            }
        })

        publishSnapshot()
    }

    fun isEnabled(): Boolean = settings.isEnabled()

    fun setEnabled(enabled: Boolean) {
        settings.setEnabled(enabled)
        if (!enabled) {
            stopAll(StopReason.PLUGIN_DISABLED)
        }
        publishSnapshot()
    }

    fun snapshot(): LogSessionsSnapshot = LogSessionsSnapshot(
        enabled = settings.isEnabled(),
        activeSessions = activeSessions.values
            .map { it.toView() }
            .sortedBy { it.deviceName.lowercase() },
        logsDirectory = projectBasePath()?.let(AppLogsPaths::logsDirectory),
    )

    fun deleteAllLogs() {
        val logsDirectory = projectBasePath()?.let(AppLogsPaths::ensureLogsDirectory) ?: run {
            notify(
                title = "App Logs unavailable",
                content = "This project has no base path, so AppLogs cannot be created.",
                type = NotificationType.WARNING,
            )
            return
        }

        stopAll(StopReason.DELETE_ALL_LOGS)
        AppLogsPaths.deleteLogContents(logsDirectory)
        publishSnapshot()
    }

    fun openLogsDirectoryInFileManager() {
        val directory = projectBasePath()?.let(AppLogsPaths::ensureLogsDirectory) ?: run {
            notify(
                title = "App Logs unavailable",
                content = "This project has no base path, so AppLogs cannot be opened.",
                type = NotificationType.WARNING,
            )
            return
        }

        ShowFilePathAction.openFile(directory.toFile())
    }

    override fun dispose() {
        stopAll(StopReason.PROJECT_CLOSED)
    }

    private fun handleProcessStarted(
        executorId: String,
        environment: ExecutionEnvironment,
        handler: ProcessHandler,
    ) {
        if (!isEnabled()) {
            return
        }
        if (executorId != DefaultRunExecutor.EXECUTOR_ID && executorId != DefaultDebugExecutor.EXECUTOR_ID) {
            return
        }

        val runProfile = environment.runProfile as? AndroidRunConfigurationBase ?: return
        if (runProfile.isTestConfiguration) {
            return
        }

        val executionId = environment.executionId
        if (startingExecutions.contains(executionId) || activeSessions.values.any { it.descriptor.executionId == executionId }) {
            return
        }

        startingExecutions.add(executionId)
        attachProcessTerminationListener(executionId, handler)

        AppExecutorUtil.getAppExecutorService().execute {
            try {
                startSessions(environment)
            } finally {
                startingExecutions.remove(executionId)
            }
        }
    }

    private fun attachProcessTerminationListener(executionId: Long, handler: ProcessHandler) {
        if (!attachedHandlers.add(executionId)) {
            return
        }

        handler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                stopSessionsForExecution(executionId, StopReason.EXECUTION_TERMINATED)
                attachedHandlers.remove(executionId)
            }
        })
    }

    private fun startSessions(environment: ExecutionEnvironment) {
        if (!isEnabled()) {
            return
        }

        val basePath = projectBasePath() ?: run {
            notify(
                title = "App Logs unavailable",
                content = "This project has no base path, so AppLogs cannot be created.",
                type = NotificationType.WARNING,
            )
            return
        }

        val adbExecutable = AndroidSdkUtils.getAdb(project) ?: run {
            notify(
                title = "ADB not found",
                content = "App Logs Recorder could not locate adb in the current Android SDK.",
                type = NotificationType.ERROR,
            )
            return
        }

        val resolvedTarget = DeviceAndAppResolver.resolve(environment) ?: run {
            notify(
                title = "Run target not supported",
                content = "App Logs Recorder only starts for Android app Run/Debug sessions with a resolved package and device.",
                type = NotificationType.WARNING,
            )
            return
        }

        val startedAt = Instant.now()
        resolvedTarget.devices.forEach { device ->
            val logFile = AppLogsPaths.createLogFilePath(
                projectBasePath = basePath,
                deviceName = device.deviceName,
                startedAt = startedAt.atZone(ZoneId.systemDefault()),
            )
            val descriptor = LogSessionDescriptor(
                sessionId = "${environment.executionId}:${device.serialNumber}",
                executionId = environment.executionId,
                runConfigurationName = resolvedTarget.runConfigurationName,
                packageName = resolvedTarget.packageName,
                deviceName = device.deviceName,
                serialNumber = device.serialNumber,
                logFile = logFile,
                startedAt = startedAt,
            )

            val recorder = AdbLogcatRecorder(
                descriptor = descriptor,
                adbExecutable = adbExecutable,
            ) { reason, details ->
                onRecorderStopped(descriptor.sessionId, reason, details)
            }

            val session = ActiveLogSession(descriptor, recorder)
            activeSessions[descriptor.sessionId] = session
            publishSnapshot()

            runCatching {
                recorder.start()
            }.onFailure { throwable ->
                logger.warn("Failed to start App Logs recorder for ${descriptor.sessionId}", throwable)
                activeSessions.remove(descriptor.sessionId)
                notify(
                    title = "Failed to start recording",
                    content = "Could not start App Logs recording for ${descriptor.deviceName}: ${throwable.message.orEmpty()}",
                    type = NotificationType.ERROR,
                )
                publishSnapshot()
            }
        }
    }

    private fun stopSessionsForExecution(executionId: Long, reason: StopReason) {
        activeSessions.values
            .filter { it.descriptor.executionId == executionId }
            .forEach { it.stop(reason) }
    }

    private fun stopAll(reason: StopReason) {
        activeSessions.values.toList().forEach { session ->
            session.stop(reason)
        }
    }

    private fun onRecorderStopped(sessionId: String, reason: StopReason, details: String?) {
        activeSessions.remove(sessionId)
        publishSnapshot()

        if (reason == StopReason.DEVICE_DISCONNECTED && details != null) {
            notify(
                title = "Recording stopped",
                content = details,
                type = NotificationType.WARNING,
            )
        }
    }

    private fun publishSnapshot() {
        if (project.isDisposed) {
            return
        }
        project.messageBus.syncPublisher(APP_LOGS_STATE_TOPIC).stateChanged(snapshot())
    }

    private fun projectBasePath(): Path? = project.basePath?.let(Path::of)

    private fun notify(title: String, content: String, type: NotificationType) {
        Notifications.Bus.notify(
            Notification("App Logs Recorder", title, content, type),
            project,
        )
    }
}
