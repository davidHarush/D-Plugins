package com.d.h.plugins.applogs.model

import java.nio.file.Path

data class LogSessionsSnapshot(
    val enabled: Boolean,
    val activeSessions: List<ActiveLogSessionView>,
    val logsDirectory: Path?,
)
