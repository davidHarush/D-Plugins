package com.d.h.plugins.applogs.service

import com.android.ddmlib.IDevice
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.DeviceFutures
import com.d.h.plugins.applogs.model.ResolvedDevice
import com.d.h.plugins.applogs.model.ResolvedRunTarget
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.TimeUnit

object DeviceAndAppResolver {
    private val logger = Logger.getInstance(DeviceAndAppResolver::class.java)

    fun resolve(environment: ExecutionEnvironment): ResolvedRunTarget? {
        val runProfile = environment.runProfile as? AndroidRunConfigurationBase ?: return null
        if (runProfile.isTestConfiguration) {
            return null
        }

        val applicationIdProvider = runProfile.applicationIdProvider ?: return null
        val packageName = runCatching { applicationIdProvider.packageName }
            .onFailure { throwable ->
                logger.warn("Unable to resolve application id for ${runProfile.name}", throwable)
            }
            .getOrNull()
            ?: return null

        val deviceFutures = environment.getCopyableUserData(DeviceFutures.KEY) ?: return null
        val devices = deviceFutures.devices.mapNotNull { device ->
            resolveDevice(device)
        }.distinctBy { it.serialNumber }

        if (devices.isEmpty()) {
            return null
        }

        return ResolvedRunTarget(
            packageName = packageName,
            runConfigurationName = runProfile.name,
            devices = devices,
        )
    }

    private fun resolveDevice(device: com.android.tools.idea.run.AndroidDevice): ResolvedDevice? {
        val fallbackName = device.name
        val launchedDevice = runCatching {
            device.launchedDevice.get(90, TimeUnit.SECONDS)
        }.onFailure { throwable ->
            logger.warn("Unable to resolve target device for App Logs recording", throwable)
        }.getOrNull() ?: return null

        return ResolvedDevice(
            deviceName = friendlyDeviceName(launchedDevice, fallbackName),
            serialNumber = launchedDevice.serialNumber,
        )
    }

    private fun friendlyDeviceName(device: IDevice, fallbackName: String?): String {
        return sequenceOf(
            device.name,
            fallbackName,
            device.serialNumber,
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
    }
}
