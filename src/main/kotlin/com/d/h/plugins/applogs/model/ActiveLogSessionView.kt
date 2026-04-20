package com.d.h.plugins.applogs.model

import java.nio.file.Path
import java.time.Instant

data class ActiveLogSessionView(
    val sessionId: String,
    val deviceName: String,
    val packageName: String,
    val logFile: Path,
    val startedAt: Instant,
)
