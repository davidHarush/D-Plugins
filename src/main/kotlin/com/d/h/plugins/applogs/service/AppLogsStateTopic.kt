package com.d.h.plugins.applogs.service

import com.d.h.plugins.applogs.model.LogSessionsSnapshot
import com.intellij.util.messages.Topic

fun interface AppLogsStateListener {
    fun stateChanged(snapshot: LogSessionsSnapshot)
}

val APP_LOGS_STATE_TOPIC: Topic<AppLogsStateListener> = Topic.create(
    "App Logs State",
    AppLogsStateListener::class.java,
)
