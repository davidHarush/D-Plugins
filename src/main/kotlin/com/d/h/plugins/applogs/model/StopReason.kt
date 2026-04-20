package com.d.h.plugins.applogs.model

enum class StopReason(val displayName: String) {
    EXECUTION_TERMINATED("Execution terminated"),
    PLUGIN_DISABLED("Plugin disabled"),
    DEVICE_DISCONNECTED("Device disconnected"),
    PROJECT_CLOSED("Project closed"),
    DELETE_ALL_LOGS("Delete all logs"),
    STARTUP_FAILED("Startup failed"),
}
