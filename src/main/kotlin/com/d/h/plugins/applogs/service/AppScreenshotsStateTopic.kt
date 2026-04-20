package com.d.h.plugins.applogs.service

import com.d.h.plugins.applogs.model.AppScreenshotsSnapshot
import com.intellij.util.messages.Topic

fun interface AppScreenshotsStateListener {
    fun stateChanged(snapshot: AppScreenshotsSnapshot)
}

val APP_SCREENSHOTS_STATE_TOPIC: Topic<AppScreenshotsStateListener> = Topic.create(
    "App Screenshots State",
    AppScreenshotsStateListener::class.java,
)
