package com.d.h.plugins.applogs.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectedDevicesParserTest {
    @Test
    fun `parse keeps connected devices with model names`() {
        val devices = ConnectedDevicesParser.parse(
            """
            List of devices attached
            emulator-5554          device product:sdk_gphone64_arm64 model:Pixel_8_Pro device:emu64 transport_id:1
            """
                .trimIndent(),
        )

        assertEquals(1, devices.size)
        assertEquals("emulator-5554", devices.first().serialNumber)
        assertEquals("Pixel 8 Pro", devices.first().displayName)
    }

    @Test
    fun `parse ignores unauthorized and offline devices`() {
        val devices = ConnectedDevicesParser.parse(
            """
            List of devices attached
            R5CW1234567            unauthorized usb:336592896X transport_id:3
            emulator-5554          offline transport_id:1
            ZY22ABCDEF             device usb:1-1 product:panther model:Pixel_7 device:panther transport_id:2
            """
                .trimIndent(),
        )

        assertEquals(1, devices.size)
        assertEquals("ZY22ABCDEF", devices.first().serialNumber)
        assertEquals("Pixel 7", devices.first().displayName)
    }

    @Test
    fun `parse falls back to serial when model is missing`() {
        val devices = ConnectedDevicesParser.parse(
            """
            List of devices attached
            emulator-5554          device transport_id:1
            ZY22ABCDEF             device product:panther device:panther transport_id:2
            """
                .trimIndent(),
        )

        assertEquals(2, devices.size)
        assertTrue(devices.any { device -> device.serialNumber == "emulator-5554" && device.displayName == "emulator-5554" })
        assertTrue(devices.any { device -> device.serialNumber == "ZY22ABCDEF" && device.displayName == "ZY22ABCDEF" })
    }
}
