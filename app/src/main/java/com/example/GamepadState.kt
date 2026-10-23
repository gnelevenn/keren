package com.example

import java.util.concurrent.atomic.AtomicBoolean

/**
 * GamepadState encodes the virtual DualSense controller state into standard
 * HID Input Report bytes.
 *
 * Performance optimization:
 * - Uses statically allocated byte buffers to prevent Garbage Collection (GC) pauses
 *   during fast competitive gaming input streams.
 * - Thread-safe state caching with fast atomic dirty flags.
 */
class GamepadState {

    // Action & Shoulder Buttons (Report Byte 0)
    @Volatile var square: Boolean = false
    @Volatile var cross: Boolean = false
    @Volatile var circle: Boolean = false
    @Volatile var triangle: Boolean = false
    @Volatile var l1: Boolean = false
    @Volatile var r1: Boolean = false
    @Volatile var l2Digital: Boolean = false
    @Volatile var r2Digital: Boolean = false

    // System & Stick Clicks (Report Byte 1)
    @Volatile var create: Boolean = false
    @Volatile var options: Boolean = false
    @Volatile var l3: Boolean = false
    @Volatile var r3: Boolean = false
    @Volatile var ps: Boolean = false
    @Volatile var touchpadClick: Boolean = false
    @Volatile var mute: Boolean = false

    // D-Pad / Hat Switch directions
    @Volatile var dpadUp: Boolean = false
    @Volatile var dpadDown: Boolean = false
    @Volatile var dpadLeft: Boolean = false
    @Volatile var dpadRight: Boolean = false

    // Dual Thumbstick axes: range -127 to 127 (centered at 0)
    @Volatile var leftStickX: Int = 0
    @Volatile var leftStickY: Int = 0
    @Volatile var rightStickX: Int = 0
    @Volatile var rightStickY: Int = 0

    // Analog Triggers: range 0 to 255 (0 = released, 255 = fully pressed)
    @Volatile var l2Analog: Int = 0
    @Volatile var r2Analog: Int = 0

    // Atomic dirty flag indicating new input is ready to be sent over Bluetooth HID
    val isDirty = AtomicBoolean(false)

    fun markDirty() {
        isDirty.set(true)
    }

    /**
     * Packs the current controller state into a pre-allocated 9-byte HID report buffer.
     * Report Structure:
     * - Byte 0: Face buttons (Square, Cross, Circle, Triangle) + L1, R1, L2, R2 digital
     * - Byte 1: Create, Options, L3, R3, PS, Touchpad Click, Mute
     * - Byte 2: D-Pad Hat Switch (1=Up, 2=Up-Right, ..., 8=Up-Left, 0=Neutral) + 4-bit padding
     * - Byte 3: Left Stick X (0..255, center 128)
     * - Byte 4: Left Stick Y (0..255, center 128)
     * - Byte 5: Right Stick X (Z) (0..255, center 128)
     * - Byte 6: Right Stick Y (Rz) (0..255, center 128)
     * - Byte 7: L2 Analog Trigger (0..255)
     * - Byte 8: R2 Analog Trigger (0..255)
     */
    @Synchronized
    fun packReport(output: ByteArray): Boolean {
        if (output.size < 9) return false

        // Byte 0: Action buttons & bumpers
        var b0 = 0
        if (square) b0 = b0 or 0x01
        if (cross) b0 = b0 or 0x02
        if (circle) b0 = b0 or 0x04
        if (triangle) b0 = b0 or 0x08
        if (l1) b0 = b0 or 0x10
        if (r1) b0 = b0 or 0x20
        if (l2Digital || l2Analog > 25) b0 = b0 or 0x40
        if (r2Digital || r2Analog > 25) b0 = b0 or 0x80
        output[0] = b0.toByte()

        // Byte 1: System & Stick clicks
        var b1 = 0
        if (create) b1 = b1 or 0x01
        if (options) b1 = b1 or 0x02
        if (l3) b1 = b1 or 0x04
        if (r3) b1 = b1 or 0x08
        if (ps) b1 = b1 or 0x10
        if (touchpadClick) b1 = b1 or 0x20
        if (mute) b1 = b1 or 0x40
        output[1] = b1.toByte()

        // Byte 2: D-Pad Hat Switch (4 bits) + 4 bits padding
        val hat = calculateHatSwitch(dpadUp, dpadDown, dpadLeft, dpadRight)
        output[2] = (hat and 0x0F).toByte()

        // Byte 3 & 4: Left Analog Stick (-127..127 mapped to 0..255, center 128)
        output[3] = (leftStickX.coerceIn(-127, 127) + 128).toByte()
        output[4] = (leftStickY.coerceIn(-127, 127) + 128).toByte()

        // Byte 5 & 6: Right Analog Stick (-127..127 mapped to 0..255, center 128)
        output[5] = (rightStickX.coerceIn(-127, 127) + 128).toByte()
        output[6] = (rightStickY.coerceIn(-127, 127) + 128).toByte()

        // Byte 7 & 8: Analog Triggers (0..255)
        val l2Val = if (l2Digital && l2Analog == 0) 255 else l2Analog
        val r2Val = if (r2Digital && r2Analog == 0) 255 else r2Analog
        output[7] = l2Val.coerceIn(0, 255).toByte()
        output[8] = r2Val.coerceIn(0, 255).toByte()

        isDirty.set(false)
        return true
    }

    /**
     * Maps standard 4-way D-Pad boolean flags into 8-way USB HID Hat switch position.
     * 1 = Up, 2 = Up-Right, 3 = Right, 4 = Down-Right,
     * 5 = Down, 6 = Down-Left, 7 = Left, 8 = Up-Left, 0 = Neutral.
     */
    private fun calculateHatSwitch(up: Boolean, down: Boolean, left: Boolean, right: Boolean): Int {
        return when {
            up && right -> 2
            up && left -> 8
            down && right -> 4
            down && left -> 6
            up -> 1
            right -> 3
            down -> 5
            left -> 7
            else -> 0
        }
    }

    /**
     * Resets all inputs to neutral/released state.
     */
    fun resetAll() {
        square = false
        cross = false
        circle = false
        triangle = false
        l1 = false
        r1 = false
        l2Digital = false
        r2Digital = false
        create = false
        options = false
        l3 = false
        r3 = false
        ps = false
        touchpadClick = false
        mute = false
        dpadUp = false
        dpadDown = false
        dpadLeft = false
        dpadRight = false
        leftStickX = 0
        leftStickY = 0
        rightStickX = 0
        rightStickY = 0
        l2Analog = 0
        r2Analog = 0
        markDirty()
    }
}
