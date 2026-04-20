package com.d.h.plugins.applogs.service

import com.d.h.plugins.applogs.model.ActiveLogSessionView
import com.d.h.plugins.applogs.model.LogSessionDescriptor
import com.d.h.plugins.applogs.model.StopReason
import java.util.concurrent.atomic.AtomicBoolean

internal class ActiveLogSession(
    val descriptor: LogSessionDescriptor,
    private val recorder: AdbLogcatRecorder,
) {
    private val stopRequested = AtomicBoolean(false)

    fun stop(reason: StopReason) {
        if (stopRequested.compareAndSet(false, true)) {
            recorder.stop(reason)
        }
    }

    fun toView(): ActiveLogSessionView = ActiveLogSessionView(
        sessionId = descriptor.sessionId,
        deviceName = descriptor.deviceName,
        packageName = descriptor.packageName,
        logFile = descriptor.logFile,
        startedAt = descriptor.startedAt,
    )
}
