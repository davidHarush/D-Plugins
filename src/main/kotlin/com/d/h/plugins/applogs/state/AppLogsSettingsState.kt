package com.d.h.plugins.applogs.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

@Service(Service.Level.PROJECT)
@State(
    name = "AppLogsSettingsState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class AppLogsSettingsState : PersistentStateComponent<AppLogsSettingsState.Data> {
    data class Data(var enabled: Boolean = false)

    private var data = Data()

    override fun getState(): Data = data

    override fun loadState(state: Data) {
        data = state
    }

    fun isEnabled(): Boolean = data.enabled

    fun setEnabled(enabled: Boolean) {
        data = data.copy(enabled = enabled)
    }
}
