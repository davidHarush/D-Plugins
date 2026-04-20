package com.d.h.plugins.applogs.model

data class ResolvedRunTarget(
    val packageName: String,
    val runConfigurationName: String,
    val devices: List<ResolvedDevice>,
)

data class ResolvedDevice(
    val deviceName: String,
    val serialNumber: String,
)
