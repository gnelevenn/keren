package com.example

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * PS5 DualSense Virtual Controller Layout.
 * Optimized for low-latency multi-touch rendering and native Canvas math.
 */
@Composable
fun DualSenseControllerView(
    modifier: Modifier = Modifier,
    gamepadState: GamepadState,
    onStateChanged: () -> Unit,
    onHapticFeedback: () -> Unit
) {
    // DualSense Classic Colors
    val chassisGripColor = Color(0xFFE3E7ED)
    val centerCradleColor = Color(0xFF111419)
    val buttonSurfaceColor = Color(0xFF22262E)
    val lightbarGlowColor = Color(0xFF00E5FF)
    val lightbarDarkColor = Color(0xFF0077B6)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1B202A), Color(0xFF0C0E12)),
                    radius = 1200f
                )
            )
    ) {
        // Decorative DualSense Curved Body Outline in Background
        DualSenseChassisBackground(
            modifier = Modifier.fillMaxSize(),
            gripColor = chassisGripColor,
            cradleColor = centerCradleColor,
            lightbarGlow = lightbarGlowColor
        )

        // Main Controller Interactive Grid
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP ROW: Bumpers & Analog Triggers (L2/L1 and R1/R2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Triggers Cluster
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TriggerButton(
                        label = "L2",
                        testTag = "l2_trigger",
                        onPressureChanged = { pressure ->
                            gamepadState.l2Analog = pressure
                            gamepadState.l2Digital = pressure > 25
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )
                    BumperButton(
                        label = "L1",
                        testTag = "l1_bumper",
                        onPressedChanged = { pressed ->
                            gamepadState.l1 = pressed
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )
                }

                // Center Top: Create & Options Buttons flanking the Touchpad
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallSystemButton(
                        label = "CREATE",
                        testTag = "create_button",
                        onPressedChanged = { pressed ->
                            gamepadState.create = pressed
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )

                    // Clickable DualSense Touchpad Bar with Glowing Lightbar
                    TouchpadBar(
                        testTag = "touchpad_surface",
                        glowColor = lightbarGlowColor,
                        onPressedChanged = { pressed ->
                            gamepadState.touchpadClick = pressed
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )

                    SmallSystemButton(
                        label = "OPTIONS",
                        testTag = "options_button",
                        onPressedChanged = { pressed ->
                            gamepadState.options = pressed
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )
                }

                // Right Triggers Cluster
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BumperButton(
                        label = "R1",
                        testTag = "r1_bumper",
                        onPressedChanged = { pressed ->
                            gamepadState.r1 = pressed
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )
                    TriggerButton(
                        label = "R2",
                        testTag = "r2_trigger",
                        onPressureChanged = { pressure ->
                            gamepadState.r2Analog = pressure
                            gamepadState.r2Digital = pressure > 25
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )
                }
            }

            // MIDDLE & BOTTOM: Dual Sections (Left: D-Pad + Left Stick, Center: PS Button, Right: Action Buttons + Right Stick)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT SIDE: D-Pad & Left Thumbstick
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DPadView(
                        testTag = "dpad_cluster",
                        onDirectionChanged = { up, down, left, right ->
                            gamepadState.dpadUp = up
                            gamepadState.dpadDown = down
                            gamepadState.dpadLeft = left
                            gamepadState.dpadRight = right
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )

                    // Left Analog Joystick with L3 Click Button
                    AnalogThumbstick(
                        label = "L3",
                        testTag = "left_stick",
                        onCoordinateChanged = { x, y ->
                            gamepadState.leftStickX = x
                            gamepadState.leftStickY = y
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onClickChanged = { clicked ->
                            gamepadState.l3 = clicked
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )
                }

                // CENTER COLUMN: PS Button & Mic Mute Toggle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PSButton(
                        testTag = "ps_button",
                        onPressedChanged = { pressed ->
                            gamepadState.ps = pressed
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )

                    MuteButton(
                        testTag = "mute_button",
                        onToggled = { isMuted ->
                            gamepadState.mute = isMuted
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )
                }

                // RIGHT SIDE: Right Thumbstick & Action Buttons (Square, Cross, Circle, Triangle)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Right Analog Joystick with R3 Click Button
                    AnalogThumbstick(
                        label = "R3",
                        testTag = "right_stick",
                        onCoordinateChanged = { x, y ->
                            gamepadState.rightStickX = x
                            gamepadState.rightStickY = y
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onClickChanged = { clicked ->
                            gamepadState.r3 = clicked
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )

                    ActionButtonsCluster(
                        testTag = "action_buttons_cluster",
                        onButtonSquare = { pressed ->
                            gamepadState.square = pressed
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onButtonCross = { pressed ->
                            gamepadState.cross = pressed
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onButtonCircle = { pressed ->
                            gamepadState.circle = pressed
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onButtonTriangle = { pressed ->
                            gamepadState.triangle = pressed
                            gamepadState.markDirty()
                            onStateChanged()
                        },
                        onHapticFeedback = onHapticFeedback
                    )
                }
            }
        }
    }
}

/**
 * Decorative background layer illustrating the iconic DualSense matte grips and dark center cradle.
 */
@Composable
private fun DualSenseChassisBackground(
    modifier: Modifier = Modifier,
    gripColor: Color,
    cradleColor: Color,
    lightbarGlow: Color
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle Left & Right Grip accents
        val gripWidth = w * 0.22f
        val leftGripPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(gripWidth, 0f)
            cubicTo(gripWidth * 0.7f, h * 0.5f, gripWidth * 0.9f, h * 0.8f, gripWidth * 0.5f, h)
            lineTo(0f, h)
            close()
        }
        drawPath(leftGripPath, color = gripColor.copy(alpha = 0.08f))

        val rightGripPath = Path().apply {
            moveTo(w, 0f)
            lineTo(w - gripWidth, 0f)
            cubicTo(w - (gripWidth * 0.7f), h * 0.5f, w - (gripWidth * 0.9f), h * 0.8f, w - (gripWidth * 0.5f), h)
            lineTo(w, h)
            close()
        }
        drawPath(rightGripPath, color = gripColor.copy(alpha = 0.08f))

        // Center ambient glow behind touchpad
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(lightbarGlow.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.15f),
                radius = w * 0.25f
            )
        )
    }
}

/**
 * PS5 Style D-Pad with 4 directional buttons and diagonal multi-direction support.
 */
@Composable
fun DPadView(
    modifier: Modifier = Modifier,
    testTag: String,
    onDirectionChanged: (up: Boolean, down: Boolean, left: Boolean, right: Boolean) -> Unit,
    onHapticFeedback: () -> Unit
) {
    var up by remember { mutableStateOf(false) }
    var down by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(140.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // Base plate
        Box(
            modifier = Modifier
                .size(136.dp)
                .background(Color(0xFF14181F), CircleShape)
                .border(1.5.dp, Color(0xFF2C323D), CircleShape)
        )

        // UP
        DPadArrowButton(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 4.dp),
            isPressed = up,
            label = "▲",
            onPressed = { pressed ->
                if (pressed != up) {
                    up = pressed
                    if (pressed) onHapticFeedback()
                    onDirectionChanged(up, down, left, right)
                }
            }
        )

        // DOWN
        DPadArrowButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp),
            isPressed = down,
            label = "▼",
            onPressed = { pressed ->
                if (pressed != down) {
                    down = pressed
                    if (pressed) onHapticFeedback()
                    onDirectionChanged(up, down, left, right)
                }
            }
        )

        // LEFT
        DPadArrowButton(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 4.dp),
            isPressed = left,
            label = "◀",
            onPressed = { pressed ->
                if (pressed != left) {
                    left = pressed
                    if (pressed) onHapticFeedback()
                    onDirectionChanged(up, down, left, right)
                }
            }
        )

        // RIGHT
        DPadArrowButton(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-4).dp),
            isPressed = right,
            label = "▶",
            onPressed = { pressed ->
                if (pressed != right) {
                    right = pressed
                    if (pressed) onHapticFeedback()
                    onDirectionChanged(up, down, left, right)
                }
            }
        )
    }
}

@Composable
private fun DPadArrowButton(
    modifier: Modifier = Modifier,
    isPressed: Boolean,
    label: String,
    onPressed: (Boolean) -> Unit
) {
    val bgColor = if (isPressed) Color(0xFF00E5FF).copy(alpha = 0.35f) else Color(0xFF242831)
    val borderColor = if (isPressed) Color(0xFF00E5FF) else Color(0xFF38404E)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressed(true)
                        tryAwaitRelease()
                        onPressed(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) Color(0xFF00E5FF) else Color(0xFFCBD2DC),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * PS5 Action Buttons Cluster: Triangle (△), Circle (◯), Cross (✕), Square (◻).
 */
@Composable
fun ActionButtonsCluster(
    modifier: Modifier = Modifier,
    testTag: String,
    onButtonSquare: (Boolean) -> Unit,
    onButtonCross: (Boolean) -> Unit,
    onButtonCircle: (Boolean) -> Unit,
    onButtonTriangle: (Boolean) -> Unit,
    onHapticFeedback: () -> Unit
) {
    Box(
        modifier = modifier
            .size(140.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // Base plate
        Box(
            modifier = Modifier
                .size(136.dp)
                .background(Color(0xFF14181F), CircleShape)
                .border(1.5.dp, Color(0xFF2C323D), CircleShape)
        )

        // TRIANGLE (Top - Green)
        ActionSymbolButton(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 4.dp),
            symbol = "△",
            symbolColor = Color(0xFF38EF7D),
            testTag = "triangle_button",
            onPressedChanged = onButtonTriangle,
            onHapticFeedback = onHapticFeedback
        )

        // CIRCLE (Right - Red/Coral)
        ActionSymbolButton(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-4).dp),
            symbol = "◯",
            symbolColor = Color(0xFFFF5252),
            testTag = "circle_button",
            onPressedChanged = onButtonCircle,
            onHapticFeedback = onHapticFeedback
        )

        // CROSS (Bottom - Cyan/Blue)
        ActionSymbolButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp),
            symbol = "✕",
            symbolColor = Color(0xFF40C4FF),
            testTag = "cross_button",
            onPressedChanged = onButtonCross,
            onHapticFeedback = onHapticFeedback
        )

        // SQUARE (Left - Pink/Magenta)
        ActionSymbolButton(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 4.dp),
            symbol = "◻",
            symbolColor = Color(0xFFFF4081),
            testTag = "square_button",
            onPressedChanged = onButtonSquare,
            onHapticFeedback = onHapticFeedback
        )
    }
}

@Composable
private fun ActionSymbolButton(
    modifier: Modifier = Modifier,
    symbol: String,
    symbolColor: Color,
    testTag: String,
    onPressedChanged: (Boolean) -> Unit,
    onHapticFeedback: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val bgColor = if (isPressed) symbolColor.copy(alpha = 0.35f) else Color(0xFF242831)
    val borderColor = if (isPressed) symbolColor else Color(0xFF38404E)

    Box(
        modifier = modifier
            .size(44.dp)
            .testTag(testTag)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.5.dp, borderColor, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onHapticFeedback()
                        onPressedChanged(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressedChanged(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = if (isPressed) Color.White else symbolColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * High precision Analog Thumbstick with dead-zone calculation and L3/R3 click support.
 */
@Composable
fun AnalogThumbstick(
    modifier: Modifier = Modifier,
    label: String,
    testTag: String,
    onCoordinateChanged: (x: Int, y: Int) -> Unit,
    onClickChanged: (Boolean) -> Unit,
    onHapticFeedback: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isClickActive by remember { mutableStateOf(false) }

    val baseRadius = 60f // dp to pixel equivalent logic inside Canvas
    val maxDragRadius = 40f // maximum displacement in dp

    Box(
        modifier = modifier
            .size(124.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // Base cup of Joystick
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 4f

            // Outer ring
            drawCircle(
                color = Color(0xFF14171E),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color(0xFF2A2E38),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            // Inner recess
            drawCircle(
                color = Color(0xFF0F1116),
                radius = radius * 0.75f,
                center = center
            )
        }

        // Draggable Stick Cap
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF333842), Color(0xFF1C1F26))
                    )
                )
                .border(
                    2.dp,
                    if (isClickActive) Color(0xFF00E5FF) else Color(0xFF454B57),
                    CircleShape
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            onHapticFeedback()
                        },
                        onDragEnd = {
                            offsetX = 0f
                            offsetY = 0f
                            onCoordinateChanged(0, 0)
                        },
                        onDragCancel = {
                            offsetX = 0f
                            offsetY = 0f
                            onCoordinateChanged(0, 0)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = offsetX + dragAmount.x
                            val newY = offsetY + dragAmount.y
                            val distance = hypot(newX, newY)
                            val maxDistancePx = maxDragRadius * density

                            if (distance <= maxDistancePx) {
                                offsetX = newX
                                offsetY = newY
                            } else {
                                val angle = atan2(newY, newX)
                                offsetX = cos(angle) * maxDistancePx
                                offsetY = sin(angle) * maxDistancePx
                            }

                            // Normalize coordinates to -127..127 range with dead-zone
                            val normDist = (hypot(offsetX, offsetY) / maxDistancePx).coerceIn(0f, 1f)
                            if (normDist < 0.05f) {
                                onCoordinateChanged(0, 0)
                            } else {
                                val angle = atan2(offsetY, offsetX)
                                val stickX = (cos(angle) * normDist * 127f).roundToInt()
                                val stickY = (sin(angle) * normDist * 127f).roundToInt()
                                onCoordinateChanged(stickX, stickY)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Textured rings & Click indicator
            Text(
                text = label,
                color = if (isClickActive) Color(0xFF00E5FF) else Color(0xFF8E95A3),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Small dedicated Click button badge for L3/R3 below joystick
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isClickActive) Color(0xFF00E5FF) else Color(0xFF282D36))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isClickActive = true
                            onHapticFeedback()
                            onClickChanged(true)
                            tryAwaitRelease()
                            isClickActive = false
                            onClickChanged(false)
                        }
                    )
                }
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "CLICK $label",
                color = if (isClickActive) Color.Black else Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Bumper button (L1 / R1)
 */
@Composable
fun BumperButton(
    modifier: Modifier = Modifier,
    label: String,
    testTag: String,
    onPressedChanged: (Boolean) -> Unit,
    onHapticFeedback: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(72.dp)
            .height(38.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) Color(0xFF00E5FF).copy(alpha = 0.35f) else Color(0xFF22262E))
            .border(
                1.5.dp,
                if (isPressed) Color(0xFF00E5FF) else Color(0xFF38404E),
                RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onHapticFeedback()
                        onPressedChanged(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressedChanged(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) Color(0xFF00E5FF) else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Analog Trigger button (L2 / R2) with pressure sensitivity simulation.
 * Holding or dragging vertically modulates pressure level 0 to 255.
 */
@Composable
fun TriggerButton(
    modifier: Modifier = Modifier,
    label: String,
    testTag: String,
    onPressureChanged: (pressure: Int) -> Unit,
    onHapticFeedback: () -> Unit
) {
    var currentPressure by remember { mutableIntStateOf(0) }
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(80.dp)
            .height(38.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E222A))
            .border(
                1.5.dp,
                if (isPressed) Color(0xFF00E5FF) else Color(0xFF38404E),
                RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        currentPressure = 255
                        onHapticFeedback()
                        onPressureChanged(255)
                        tryAwaitRelease()
                        isPressed = false
                        currentPressure = 0
                        onPressureChanged(0)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Visual Pressure Bar inside trigger
        if (isPressed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF00E5FF).copy(alpha = 0.35f))
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = if (isPressed) Color(0xFF00E5FF) else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (isPressed) {
                Text(
                    text = "MAX",
                    color = Color(0xFF00E5FF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * PS5 DualSense Touchpad Bar with authentic cyan glow outline and clickable surface.
 */
@Composable
fun TouchpadBar(
    modifier: Modifier = Modifier,
    testTag: String,
    glowColor: Color,
    onPressedChanged: (Boolean) -> Unit,
    onHapticFeedback: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(180.dp)
            .height(44.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp, topStart = 6.dp, topEnd = 6.dp))
            .background(if (isPressed) Color(0xFF1C222C) else Color(0xFF13161C))
            .border(
                2.dp,
                Brush.horizontalGradient(
                    colors = listOf(glowColor, glowColor.copy(alpha = 0.3f), glowColor)
                ),
                RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp, topStart = 6.dp, topEnd = 6.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onHapticFeedback()
                        onPressedChanged(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressedChanged(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Subtle dot texture
        Text(
            text = "TOUCHPAD",
            color = if (isPressed) glowColor else Color(0xFF747D8C),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

/**
 * Small pill-shaped system button (Create / Options).
 */
@Composable
fun SmallSystemButton(
    modifier: Modifier = Modifier,
    label: String,
    testTag: String,
    onPressedChanged: (Boolean) -> Unit,
    onHapticFeedback: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPressed) Color(0xFF00E5FF).copy(alpha = 0.35f) else Color(0xFF22262E))
            .border(1.dp, if (isPressed) Color(0xFF00E5FF) else Color(0xFF38404E), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onHapticFeedback()
                        onPressedChanged(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressedChanged(false)
                    }
                )
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) Color(0xFF00E5FF) else Color(0xFFA4B0BE),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * PlayStation PS Logo Button.
 */
@Composable
fun PSButton(
    modifier: Modifier = Modifier,
    testTag: String,
    onPressedChanged: (Boolean) -> Unit,
    onHapticFeedback: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(46.dp)
            .testTag(testTag)
            .clip(CircleShape)
            .background(if (isPressed) Color(0xFF00E5FF).copy(alpha = 0.4f) else Color(0xFF1B1E26))
            .border(2.dp, if (isPressed) Color(0xFF00E5FF) else Color(0xFF3A4150), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onHapticFeedback()
                        onPressedChanged(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressedChanged(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "PS",
            color = if (isPressed) Color.White else Color(0xFFE4E9F2),
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * Microphone Mute toggle button with amber LED.
 */
@Composable
fun MuteButton(
    modifier: Modifier = Modifier,
    testTag: String,
    onToggled: (Boolean) -> Unit,
    onHapticFeedback: () -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E222A))
            .border(1.dp, if (isMuted) Color(0xFFFF9F1C) else Color(0xFF38404E), RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        isMuted = !isMuted
                        onHapticFeedback()
                        onToggled(isMuted)
                    }
                )
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Indicator LED
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isMuted) Color(0xFFFF9F1C) else Color(0xFF4A5568))
            )
            Icon(
                imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = "Mute",
                tint = if (isMuted) Color(0xFFFF9F1C) else Color(0xFF8E95A3),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
