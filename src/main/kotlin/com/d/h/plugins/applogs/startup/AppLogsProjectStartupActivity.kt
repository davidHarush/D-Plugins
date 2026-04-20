package com.d.h.plugins.applogs.startup

import com.d.h.plugins.applogs.service.LogSessionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class AppLogsProjectStartupActivity : StartupActivity.RequiredForSmartMode {
    override fun runActivity(project: Project) {
        project.service<LogSessionManager>().initialize()
    }
}
