package com.d.h.plugins.applogs.model

import java.nio.file.Path

data class AppScreenshotsSnapshot(
    val connectedDevices: List<ConnectedDeviceView>,
    val targetDirectory: Path?,
    val isLoadingDevices: Boolean,
    val adbAvailable: Boolean,
    val pluginScreenshotCount: Int,
)
