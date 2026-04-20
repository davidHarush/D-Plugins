package com.d.h.plugins.applogs.model

import java.nio.file.Path
import java.time.Instant

data class LogSessionDescriptor(
    val sessionId: String,
    val executionId: Long,
    val runConfigurationName: String,
    val packageName: String,
    val deviceName: String,
    val serialNumber: String,
    val logFile: Path,
    val startedAt: Instant,
)
