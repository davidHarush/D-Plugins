package com.d.h.plugins.applogs.service

import com.d.h.plugins.applogs.model.ConnectedDeviceView

internal object ConnectedDevicesParser {
    fun parse(output: String): List<ConnectedDeviceView> {
        return output.lineSequence()
            .map(String::trim)
            .filter { line -> line.isNotBlank() && !line.startsWith("List of devices attached") }
            .mapNotNull(::parseDeviceLine)
            .sortedBy { device -> device.displayName.lowercase() }
            .toList()
    }

    private fun parseDeviceLine(line: String): ConnectedDeviceView? {
        val columns = line.split(Regex("\\s+"))
        if (columns.size < 2) {
            return null
        }

        val serialNumber = columns[0]
        val state = columns[1]
        if (state != "device") {
            return null
        }

        val metadata = columns
            .drop(2)
            .mapNotNull(::parseKeyValueToken)
            .toMap()

        val displayName = metadata["model"]
            ?.replace('_', ' ')
            ?.trim()
            ?.takeIf { value -> value.isNotBlank() }
            ?: serialNumber

        return ConnectedDeviceView(
            serialNumber = serialNumber,
            displayName = displayName,
        )
    }

    private fun parseKeyValueToken(token: String): Pair<String, String>? {
        val separatorIndex = token.indexOf(':')
        if (separatorIndex <= 0 || separatorIndex == token.lastIndex) {
            return null
        }

        return token.substring(0, separatorIndex) to token.substring(separatorIndex + 1)
    }
}
