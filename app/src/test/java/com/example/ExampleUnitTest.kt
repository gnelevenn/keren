package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testGamepadStateEncoding() {
    val state = GamepadState()
    val buffer = ByteArray(9)

    // Neutral state
    state.packReport(buffer)
    assertEquals(0, buffer[0].toInt()) // No buttons
    assertEquals(0, buffer[1].toInt()) // No system buttons
    assertEquals(0, buffer[2].toInt()) // D-pad neutral
    assertEquals(128, buffer[3].toInt() and 0xFF) // Left X centered
    assertEquals(128, buffer[4].toInt() and 0xFF) // Left Y centered
    assertEquals(128, buffer[5].toInt() and 0xFF) // Right X centered
    assertEquals(128, buffer[6].toInt() and 0xFF) // Right Y centered
    assertEquals(0, buffer[7].toInt() and 0xFF)   // L2 released
    assertEquals(0, buffer[8].toInt() and 0xFF)   // R2 released

    // Press Cross (✕), L1, D-Pad Up, Left Stick X Max, R2 Trigger Max
    state.cross = true
    state.l1 = true
    state.dpadUp = true
    state.leftStickX = 127
    state.r2Analog = 255

    state.packReport(buffer)
    // Cross = 0x02, L1 = 0x10, R2 (Analog > 25) = 0x80 -> 0x92
    assertEquals(0x92, buffer[0].toInt() and 0xFF)
    // D-Pad Up = 1
    assertEquals(1, buffer[2].toInt() and 0xFF)
    // Left X = 127 + 128 = 255
    assertEquals(255, buffer[3].toInt() and 0xFF)
    // R2 Analog = 255
    assertEquals(255, buffer[8].toInt() and 0xFF)
  }
}
