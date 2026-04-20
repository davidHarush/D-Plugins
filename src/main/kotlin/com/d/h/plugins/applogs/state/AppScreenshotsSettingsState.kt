package com.d.h.plugins.applogs.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.nio.file.Path

@Service(Service.Level.APP)
@State(
    name = "AppScreenshotsSettingsState",
    storages = [Storage(value = "dplugins.app.screenshots.xml", roamingType = RoamingType.DISABLED)],
)
class AppScreenshotsSettingsState : PersistentStateComponent<AppScreenshotsSettingsState.Data> {
    data class Data(var targetDirectory: String? = null)

    private var data = Data()

    override fun getState(): Data = data

    override fun loadState(state: Data) {
        data = state
    }

    fun getTargetDirectory(): Path? = data.targetDirectory
        ?.takeIf { it.isNotBlank() }
        ?.let { rawPath -> runCatching { Path.of(rawPath) }.getOrNull() }

    fun setTargetDirectory(targetDirectory: Path?) {
        data = data.copy(targetDirectory = targetDirectory?.toAbsolutePath()?.normalize()?.toString())
    }
}
