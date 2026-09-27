package me.arianb.usb_hid_client.input_views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.arianb.usb_hid_client.input_views.touch_input_handlers.MouseInputHandler
import me.arianb.usb_hid_client.input_views.touch_input_handlers.TouchInputHandler
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.MouseSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.PointerDeviceSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.TouchpadSender
import me.arianb.usb_hid_client.ui.theme.CornerExtraLarge
import me.arianb.usb_hid_client.ui.theme.ElevationLevel0
import me.arianb.usb_hid_client.ui.theme.ElevationLevel1
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class SpenMode { HOVER, MOUSE, HYBRID }

/** Tracks the digitizer's range separately from contact, so a new stroke cannot jump the cursor. */
internal class SpenInputController(
    private val sender: PointerDeviceSender,
    var mode: SpenMode,
    var sensitivity: Float,
    var hoverRange: Float,
    private val doubleTapSlop: Float
) {
    private var previousX: Float? = null
    private var previousY: Float? = null
    private var touching = false
    private var rightButton = false
    private var draggingLeft = false
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var movedBeyondTapSlop = false
    private var lastTapUpTime = Long.MIN_VALUE
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var lastAbsoluteX = 0
    private var lastAbsoluteY = 0
    private var hasAbsolutePosition = false
    private var xRemainder = 0f
    private var yRemainder = 0f
    var acceptsCurrentPosition = false
        private set

    val isTouching: Boolean get() = touching

    fun reset() {
        touching = false
        rightButton = false
        draggingLeft = false
        previousX = null
        previousY = null
        xRemainder = 0f
        yRemainder = 0f
        acceptsCurrentPosition = false
        if (mode == SpenMode.HOVER) {
            sender.sendAbsoluteMouseReport(lastAbsoluteX, lastAbsoluteY, buttons())
        } else {
            send(0, 0)
        }
    }

    fun handle(event: MotionEvent, width: Int, height: Int): Boolean {
        val tool = event.getToolType(event.actionIndex)
        if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return false
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_HOVER_EXIT -> {
                reset()
                return true
            }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> Unit
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_BUTTON_PRESS,
            MotionEvent.ACTION_BUTTON_RELEASE -> Unit
            else -> return false
        }
        rightButton = tool == MotionEvent.TOOL_TYPE_ERASER ||
            event.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
        if (action == MotionEvent.ACTION_BUTTON_PRESS) rightButton = true
        if (action == MotionEvent.ACTION_BUTTON_RELEASE) rightButton = false
        val x = event.x.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        val y = event.y.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
        acceptsCurrentPosition = true

        if (action == MotionEvent.ACTION_DOWN) {
            touching = true
            downTime = event.eventTime
            downX = x
            downY = y
            movedBeyondTapSlop = false
            if (mode == SpenMode.MOUSE) {
                xRemainder = 0f
                yRemainder = 0f
                val withinDoubleTapTime = lastTapUpTime != Long.MIN_VALUE &&
                    event.eventTime - lastTapUpTime in 0..ViewConfiguration.getDoubleTapTimeout().toLong()
                val withinDoubleTapSlop = squaredDistance(x, y, lastTapX, lastTapY) <= doubleTapSlop * doubleTapSlop
                draggingLeft = withinDoubleTapTime && withinDoubleTapSlop
                previousX = x
                previousY = y
                send(0, 0)
                return true
            }
        }

        if ((mode == SpenMode.HYBRID || mode == SpenMode.HOVER) && !touching && action != MotionEvent.ACTION_UP && !isWithinHoverRange(event)) {
            previousX = null
            previousY = null
            xRemainder = 0f
            yRemainder = 0f
            acceptsCurrentPosition = false
            return true
        }

        val eligible = mode != SpenMode.MOUSE || touching || action == MotionEvent.ACTION_UP
        if (eligible) {
            if (mode == SpenMode.HOVER) {
                val rawAbsoluteX = x / (width - 1).coerceAtLeast(1) * 32767
                val rawAbsoluteY = y / (height - 1).coerceAtLeast(1) * 32767
                if (sensitivity == 1f) {
                    lastAbsoluteX = rawAbsoluteX.roundToInt()
                    lastAbsoluteY = rawAbsoluteY.roundToInt()
                    hasAbsolutePosition = true
                } else if (!hasAbsolutePosition) {
                    lastAbsoluteX = rawAbsoluteX.roundToInt()
                    lastAbsoluteY = rawAbsoluteY.roundToInt()
                    hasAbsolutePosition = true
                } else if (previousX != null && previousY != null) {
                    val absoluteDx = (x - previousX!!) / (width - 1).coerceAtLeast(1) * 32767 * sensitivity
                    val absoluteDy = (y - previousY!!) / (height - 1).coerceAtLeast(1) * 32767 * sensitivity
                    lastAbsoluteX = (lastAbsoluteX + absoluteDx).roundToInt().coerceIn(0, 32767)
                    lastAbsoluteY = (lastAbsoluteY + absoluteDy).roundToInt().coerceIn(0, 32767)
                }
                sender.sendAbsoluteMouseReport(lastAbsoluteX, lastAbsoluteY, buttons())
            } else {
                // First event after entering range only establishes an anchor.
                val scaledDx = previousX?.let { (x - it) * sensitivity + xRemainder } ?: 0f
                val scaledDy = previousY?.let { (y - it) * sensitivity + yRemainder } ?: 0f
                val dx = scaledDx.toInt()
                val dy = scaledDy.toInt()
                xRemainder = scaledDx - dx
                yRemainder = scaledDy - dy
                if (touching && squaredDistance(x, y, downX, downY) > doubleTapSlop * doubleTapSlop) {
                    movedBeyondTapSlop = true
                }
                sendSplit(dx, dy)
            }
            previousX = x
            previousY = y
        }
        if (action == MotionEvent.ACTION_UP) {
            if (mode == SpenMode.MOUSE) {
                val wasDragging = draggingLeft
                touching = false
                draggingLeft = false
                send(0, 0)
                if (wasDragging) {
                    lastTapUpTime = Long.MIN_VALUE
                } else if (!movedBeyondTapSlop && event.eventTime - downTime <= ViewConfiguration.getLongPressTimeout()) {
                    sender.sendRelativeMouseReport(0, 0, PointerDeviceSender.TouchpadButtonState(true, false))
                    sender.sendRelativeMouseReport(0, 0, PointerDeviceSender.TouchpadButtonState(false, false))
                    lastTapUpTime = event.eventTime
                    lastTapX = x
                    lastTapY = y
                } else {
                    lastTapUpTime = Long.MIN_VALUE
                }
                previousX = null
                previousY = null
            } else {
                touching = false
                if (mode == SpenMode.HOVER) {
                    sender.sendAbsoluteMouseReport(lastAbsoluteX, lastAbsoluteY, buttons())
                } else {
                    send(0, 0)
                }
            }
        }
        return true
    }

    fun isWithinHoverRange(event: MotionEvent): Boolean {
        val range = event.device?.getMotionRange(MotionEvent.AXIS_DISTANCE, event.source)
            ?: event.device?.getMotionRange(MotionEvent.AXIS_DISTANCE)
            ?: return true
        if (range.range <= 0f) return true
        val usableRange = minOf(range.range, SAMSUNG_USABLE_HOVER_RANGE)
        val normalizedDistance = ((event.getAxisValue(MotionEvent.AXIS_DISTANCE) - range.min) / usableRange)
            .coerceIn(0f, 1f)
        return normalizedDistance <= hoverRange
    }

    private fun squaredDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    private fun buttons() = PointerDeviceSender.TouchpadButtonState(
        isLeftButtonPressed = (if (mode == SpenMode.MOUSE) draggingLeft else touching) && !rightButton,
        isRightButtonPressed = rightButton
    )

    private fun send(dx: Int, dy: Int) {
        if (mode == SpenMode.HOVER) return
        sender.sendRelativeMouseReport(dx, dy, buttons())
    }

    private fun sendSplit(dx: Int, dy: Int) {
        var remainingX = dx
        var remainingY = dy
        do {
            val chunkX = remainingX.coerceIn(-127, 127)
            val chunkY = remainingY.coerceIn(-127, 127)
            send(chunkX, chunkY)
            remainingX -= chunkX
            remainingY -= chunkY
        } while (remainingX != 0 || remainingY != 0)
    }

    companion object {
        private const val SAMSUNG_USABLE_HOVER_RANGE = 140f
    }
}

/**
 * Unified Surface View that seamlessly processes capacitive Finger Touch
 * and high-precision S-Pen digitizer input.
 * When the S-Pen button is held during hover, it presents a clean, minimalist
 * full-circle radial selector where the 3 options form a circle with elegant
 * margins between them and rounded container edges.
 */
private class UnifiedTabletTouchpadView(
    context: Context,
    private val controller: SpenInputController,
    private val touchInputHandler: TouchInputHandler?,
    private val mouseInputHandler: MouseInputHandler?,
    var deviceOrientation: Int,
    var currentMode: SpenMode,
    var onModeChange: (SpenMode) -> Unit,
    private var canvasBgColor: Int,
    private var primaryColor: Int,
    private var onPrimaryColor: Int,
    private var surfaceContainerHighColor: Int,
    private var surfaceContainerHighestColor: Int,
    private var surfaceContainerLowestColor: Int,
    private var outlineColor: Int,
    private var outlineVariantColor: Int,
    private var onSurfaceColor: Int,
    private var onSurfaceVariantColor: Int
) : View(context) {

    // Reticle cursor paints
    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val reticleDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.FILL
    }

    // Radial menu paints
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        style = Paint.Style.FILL
    }
    private val sectorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val sectorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val sectorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val itemTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val activeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val hubFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val hubStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val hubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val penDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val penHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // State tracking
    private var cursorX = 0f
    private var cursorY = 0f
    private var visible = false

    private var lastSpenEventTime = 0L
    private var isSpenHovering = false
    private var isRadialMenuOpen = false
    private var menuCenterX = 0f
    private var menuCenterY = 0f
    private var currentPenX = 0f
    private var currentPenY = 0f
    private var hoveredMode: SpenMode? = null

    // Smooth hover animation
    private var hoverAnim = 0f
    private var hybridAnim = 0f
    private var mouseAnim = 0f
    private var animator: ValueAnimator? = null

    init {
        setBackgroundColor(canvasBgColor)
    }

    fun updateThemeColors(
        bg: Int,
        primary: Int,
        onPrimary: Int,
        surfContainerHigh: Int,
        surfContainerHighest: Int,
        surfContainerLowest: Int,
        outline: Int,
        outlineVariant: Int,
        onSurface: Int,
        onSurfaceVar: Int
    ) {
        canvasBgColor = bg
        primaryColor = primary
        onPrimaryColor = onPrimary
        surfaceContainerHighColor = surfContainerHigh
        surfaceContainerHighestColor = surfContainerHighest
        surfaceContainerLowestColor = surfContainerLowest
        outlineColor = outline
        outlineVariantColor = outlineVariant
        onSurfaceColor = onSurface
        onSurfaceVariantColor = onSurfaceVar

        setBackgroundColor(bg)
        reticlePaint.color = primary
        reticleDotPaint.color = primary
        invalidate()
    }

    private fun updateHoverAnimations(newHovered: SpenMode?) {
        val targetHover = if (newHovered == SpenMode.HOVER) 1f else 0f
        val targetHybrid = if (newHovered == SpenMode.HYBRID) 1f else 0f
        val targetMouse = if (newHovered == SpenMode.MOUSE) 1f else 0f

        animator?.cancel()
        val startH = hoverAnim
        val startHy = hybridAnim
        val startM = mouseAnim

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 130
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val f = va.animatedFraction
                hoverAnim = startH + (targetHover - startH) * f
                hybridAnim = startHy + (targetHybrid - startHy) * f
                mouseAnim = startM + (targetMouse - startM) * f
                invalidate()
            }
            start()
        }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        lastSpenEventTime = SystemClock.uptimeMillis()
        val action = event.actionMasked
        isSpenHovering = action != MotionEvent.ACTION_HOVER_EXIT && action != MotionEvent.ACTION_CANCEL
        return processSpen(event) || super.onHoverEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val tool = event.getToolType(event.actionIndex)
        if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
            lastSpenEventTime = SystemClock.uptimeMillis()
            return processSpen(event) || super.onGenericMotionEvent(event)
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val isStylus = (0 until event.pointerCount).any { i ->
            val tool = event.getToolType(i)
            tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
        }
        if (isStylus) {
            lastSpenEventTime = SystemClock.uptimeMillis()
            cancelFingerTouches()
            return processSpen(event) || super.onTouchEvent(event)
        } else {
            // Palm rejection: if S-Pen is hovering or was active within 400ms, ignore finger contact
            if (isSpenHovering || (SystemClock.uptimeMillis() - lastSpenEventTime < 400L)) {
                return true
            }
            return processFinger(event) || super.onTouchEvent(event)
        }
    }

    private fun processSpen(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val tool = event.getToolType(event.actionIndex)
        if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return false

        val stylusButtonPressed = (event.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0
        val isButtonPressAction = action == MotionEvent.ACTION_BUTTON_PRESS &&
            (event.actionButton == MotionEvent.BUTTON_STYLUS_PRIMARY || event.actionButton == MotionEvent.BUTTON_STYLUS_SECONDARY)
        val isButtonReleaseAction = action == MotionEvent.ACTION_BUTTON_RELEASE &&
            (event.actionButton == MotionEvent.BUTTON_STYLUS_PRIMARY || event.actionButton == MotionEvent.BUTTON_STYLUS_SECONDARY)

        val buttonActive = stylusButtonPressed || isButtonPressAction

        // Hover exit or gesture cancel
        if (action == MotionEvent.ACTION_HOVER_EXIT || action == MotionEvent.ACTION_CANCEL) {
            if (isRadialMenuOpen) {
                isRadialMenuOpen = false
                hoveredMode = null
                animator?.cancel()
                hoverAnim = 0f; hybridAnim = 0f; mouseAnim = 0f
            }
            visible = false
            controller.reset()
            invalidate()
            return true
        }

        val isTouching = controller.isTouching || action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
        val density = resources.displayMetrics.density

        // --- CASE 1: Radial Menu is NOT open ---
        if (!isRadialMenuOpen) {
            // If button is pressed while hovering: OPEN RADIAL MENU!
            if (!isTouching && buttonActive) {
                isRadialMenuOpen = true
                val margin = 118f * density
                menuCenterX = event.x.coerceIn(margin, (width - margin).coerceAtLeast(margin))
                menuCenterY = event.y.coerceIn(margin, (height - margin).coerceAtLeast(margin))
                currentPenX = event.x
                currentPenY = event.y
                hoveredMode = null
                hoverAnim = 0f; hybridAnim = 0f; mouseAnim = 0f
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                controller.reset()
                invalidate()
                return true
            }

            // Normal S-Pen input handling
            val handled = controller.handle(event, width, height)
            visible = controller.acceptsCurrentPosition &&
                action != MotionEvent.ACTION_HOVER_EXIT &&
                action != MotionEvent.ACTION_CANCEL
            cursorX = event.x
            cursorY = event.y
            invalidate()
            return handled
        }

        // --- CASE 2: Radial Menu IS open ---
        currentPenX = event.x
        currentPenY = event.y

        // If pen touches the screen down on an option, commit selection and close
        if (action == MotionEvent.ACTION_DOWN) {
            if (hoveredMode != null) {
                val selected = hoveredMode!!
                currentMode = selected
                onModeChange(selected)
                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            isRadialMenuOpen = false
            hoveredMode = null
            animator?.cancel()
            hoverAnim = 0f; hybridAnim = 0f; mouseAnim = 0f
            controller.reset()
            invalidate()
            return true
        }

        // Check if button released
        val buttonReleased = isButtonReleaseAction || (!stylusButtonPressed && !isButtonPressAction)
        if (buttonReleased) {
            if (hoveredMode != null) {
                val selected = hoveredMode!!
                currentMode = selected
                onModeChange(selected)
                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            isRadialMenuOpen = false
            hoveredMode = null
            animator?.cancel()
            hoverAnim = 0f; hybridAnim = 0f; mouseAnim = 0f
            controller.reset()
            invalidate()
            return true
        }

        // Button is still held while hovering: calculate which wedge is hovered
        val dx = currentPenX - menuCenterX
        val dy = currentPenY - menuCenterY
        val dist = sqrt(dx * dx + dy * dy)
        val deadzone = 28f * density

        val newHovered: SpenMode? = if (dist < deadzone) {
            null
        } else {
            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
            if (deg < 0) deg += 360.0
            when {
                deg in 210.0..330.0 -> SpenMode.HOVER   // Top sector (centered at 270°)
                deg in 90.0..210.0 -> SpenMode.MOUSE    // Bottom-Left sector (centered at 150°)
                else -> SpenMode.HYBRID                // Bottom-Right sector (centered at 30°)
            }
        }

        if (newHovered != hoveredMode) {
            hoveredMode = newHovered
            updateHoverAnimations(newHovered)
            if (newHovered != null) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }

        invalidate()
        return true
    }

    private fun processFinger(event: MotionEvent): Boolean {
        if (visible) {
            visible = false
            invalidate()
        }
        return if (touchInputHandler != null) {
            touchInputHandler.handleTouchMotionEvent(
                motionEvent = event,
                surfaceWidth = width,
                surfaceHeight = height
            )
        } else if (mouseInputHandler != null) {
            mouseInputHandler.handleTouchEvent(event)
        } else {
            false
        }
    }

    private fun cancelFingerTouches() {
        val now = SystemClock.uptimeMillis()
        val cancelEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        touchInputHandler?.handleTouchMotionEvent(
            motionEvent = cancelEvent,
            surfaceWidth = width,
            surfaceHeight = height
        )
        mouseInputHandler?.handleTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isRadialMenuOpen) {
            drawRadialMenu(canvas)
        } else if (visible) {
            // Draw reticle outer circle
            canvas.drawCircle(cursorX, cursorY, 14f, reticlePaint)
            // Center precision dot
            canvas.drawCircle(cursorX, cursorY, 3.5f, reticleDotPaint)
            // Precision crosshair notches
            canvas.drawLine(cursorX - 22f, cursorY, cursorX - 16f, cursorY, reticlePaint)
            canvas.drawLine(cursorX + 16f, cursorY, cursorX + 22f, cursorY, reticlePaint)
            canvas.drawLine(cursorX, cursorY - 22f, cursorX, cursorY - 16f, reticlePaint)
            canvas.drawLine(cursorX, cursorY + 16f, cursorX, cursorY + 22f, reticlePaint)
        }
    }

    /**
     * Builds a continuous curved annular sector container with rounded corners.
     */
    private fun createRoundedWedgePath(
        cx: Float,
        cy: Float,
        rIn: Float,
        rOut: Float,
        startDeg: Float,
        sweepDeg: Float,
        cr: Float
    ): Path {
        val path = Path()
        val endDeg = startDeg + sweepDeg
        val radConv = (180f / Math.PI).toFloat()

        val dOut = (cr / rOut) * radConv
        val dIn = (cr / rIn) * radConv

        val a1Out = startDeg + dOut
        val a2Out = endDeg - dOut
        val a1In = startDeg + dIn
        val a2In = endDeg - dIn

        val outerRect = RectF(cx - rOut, cy - rOut, cx + rOut, cy + rOut)
        val innerRect = RectF(cx - rIn, cy - rIn, cx + rIn, cy + rIn)

        val radEnd = Math.toRadians(endDeg.toDouble())
        val radStart = Math.toRadians(startDeg.toDouble())

        // 1. Outer Arc
        path.arcTo(outerRect, a1Out, a2Out - a1Out)

        // 2. Corner 1: Outer Arc to Radial End
        val v1x = cx + rOut * cos(radEnd).toFloat()
        val v1y = cy + rOut * sin(radEnd).toFloat()
        val p2x = cx + (rOut - cr) * cos(radEnd).toFloat()
        val p2y = cy + (rOut - cr) * sin(radEnd).toFloat()
        path.quadTo(v1x, v1y, p2x, p2y)

        // 3. Radial End Line
        val p3x = cx + (rIn + cr) * cos(radEnd).toFloat()
        val p3y = cy + (rIn + cr) * sin(radEnd).toFloat()
        path.lineTo(p3x, p3y)

        // 4. Corner 2: Radial End to Inner Arc
        val v2x = cx + rIn * cos(radEnd).toFloat()
        val v2y = cy + rIn * sin(radEnd).toFloat()
        val p4x = cx + rIn * cos(Math.toRadians(a2In.toDouble())).toFloat()
        val p4y = cy + rIn * sin(Math.toRadians(a2In.toDouble())).toFloat()
        path.quadTo(v2x, v2y, p4x, p4y)

        // 5. Inner Arc (reversed)
        path.arcTo(innerRect, a2In, -(a2In - a1In))

        // 6. Corner 3: Inner Arc to Radial Start
        val v3x = cx + rIn * cos(radStart).toFloat()
        val v3y = cy + rIn * sin(radStart).toFloat()
        val p6x = cx + (rIn + cr) * cos(radStart).toFloat()
        val p6y = cy + (rIn + cr) * sin(radStart).toFloat()
        path.quadTo(v3x, v3y, p6x, p6y)

        // 7. Radial Start Line
        val p7x = cx + (rOut - cr) * cos(radStart).toFloat()
        val p7y = cy + (rOut - cr) * sin(radStart).toFloat()
        path.lineTo(p7x, p7y)

        // 8. Corner 4: Radial Start to Outer Arc
        val v4x = cx + rOut * cos(radStart).toFloat()
        val v4y = cy + rOut * sin(radStart).toFloat()
        val p8x = cx + rOut * cos(Math.toRadians(a1Out.toDouble())).toFloat()
        val p8y = cy + rOut * sin(Math.toRadians(a1Out.toDouble())).toFloat()
        path.quadTo(v4x, v4y, p8x, p8y)

        path.close()
        return path
    }

    private fun drawRadialMenu(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val baseRIn = 42f * density
        val baseROut = 96f * density
        val cornerRadius = 10f * density
        val sweepAngle = 112f // 8 degree clean margin between each of the 3 containers
        val hubRadius = 35f * density

        // 1. Soft minimalist backdrop scrim
        canvas.drawCircle(menuCenterX, menuCenterY, baseROut + 16f * density, scrimPaint)

        // 3 items forming the full circle:
        data class ItemConfig(
            val mode: SpenMode,
            val centerAngle: Float,
            val title: String,
            val anim: Float
        )

        val items = listOf(
            ItemConfig(SpenMode.HOVER, 270f, "Hover", hoverAnim),
            ItemConfig(SpenMode.HYBRID, 30f, "Hybrid", hybridAnim),
            ItemConfig(SpenMode.MOUSE, 150f, "Mouse", mouseAnim)
        )

        // 2. Draw the 3 rounded containers
        for (item in items) {
            val isHovered = (hoveredMode == item.mode)
            val isActive = (currentMode == item.mode)
            val anim = item.anim // 0f..1f

            val startAngle = item.centerAngle - (sweepAngle / 2f)

            // Outward shift when hovered
            val push = 5f * density * anim
            val radCenter = Math.toRadians(item.centerAngle.toDouble())
            val segCx = menuCenterX + (push * cos(radCenter)).toFloat()
            val segCy = menuCenterY + (push * sin(radCenter)).toFloat()

            val rIn = baseRIn
            val rOut = baseROut + (4f * density * anim)

            val wedgePath = createRoundedWedgePath(
                cx = segCx,
                cy = segCy,
                rIn = rIn,
                rOut = rOut,
                startDeg = startAngle,
                sweepDeg = sweepAngle,
                cr = cornerRadius
            )

            // Drop shadow for hovered item
            if (isHovered && anim > 0.05f) {
                sectorShadowPaint.color = primaryColor
                sectorShadowPaint.alpha = (75 * anim).toInt()
                canvas.drawPath(wedgePath, sectorShadowPaint)
            }

            // Fill container
            sectorFillPaint.color = when {
                isHovered -> primaryColor
                isActive -> surfaceContainerHighestColor
                else -> surfaceContainerHighColor
            }
            sectorFillPaint.alpha = if (isHovered) 255 else 235
            canvas.drawPath(wedgePath, sectorFillPaint)

            // Border
            sectorStrokePaint.color = when {
                isHovered -> onPrimaryColor
                isActive -> primaryColor
                else -> outlineVariantColor
            }
            sectorStrokePaint.alpha = if (isHovered) (230 * anim).toInt().coerceAtLeast(120) else if (isActive) 180 else 70
            sectorStrokePaint.strokeWidth = if (isHovered) 2f * density else if (isActive) 1.5f * density else 1f * density
            canvas.drawPath(wedgePath, sectorStrokePaint)

            // Center of container for Icon and Text
            val midRadius = (rIn + rOut) / 2f
            val itemMidX = segCx + (midRadius * cos(radCenter)).toFloat()
            val itemMidY = segCy + (midRadius * sin(radCenter)).toFloat()

            // Icon Color & Paint
            val contentColor = when {
                isHovered -> onPrimaryColor
                isActive -> primaryColor
                else -> onSurfaceColor
            }

            // Draw Icon (above center)
            drawModeIcon(canvas, item.mode, itemMidX, itemMidY - 9f * density, 11f * density, contentColor)

            // Draw Title (below icon)
            itemTitlePaint.color = contentColor
            itemTitlePaint.textSize = (13f + 0.8f * anim) * density
            canvas.drawText(item.title, itemMidX, itemMidY + 13f * density, itemTitlePaint)

            // Active Mode indicator dot
            if (isActive) {
                activeDotPaint.color = if (isHovered) onPrimaryColor else primaryColor
                canvas.drawCircle(itemMidX, itemMidY + 22f * density, 2.5f * density, activeDotPaint)
            }
        }

        // 3. Center Hub (Clean origin reticle)
        hubFillPaint.color = surfaceContainerLowestColor
        canvas.drawCircle(menuCenterX, menuCenterY, hubRadius, hubFillPaint)

        hubStrokePaint.color = if (hoveredMode != null) primaryColor else outlineVariantColor
        hubStrokePaint.strokeWidth = if (hoveredMode != null) 1.8f * density else 1f * density
        canvas.drawCircle(menuCenterX, menuCenterY, hubRadius, hubStrokePaint)

        // Center Hub Label
        if (hoveredMode != null) {
            hubTextPaint.color = primaryColor
            hubTextPaint.textSize = 11.5f * density
            canvas.drawText(hoveredMode!!.name, menuCenterX, menuCenterY + 4f * density, hubTextPaint)
        } else {
            hubTextPaint.color = onSurfaceVariantColor
            hubTextPaint.textSize = 10f * density
            canvas.drawText("S Pen", menuCenterX, menuCenterY + 3.5f * density, hubTextPaint)
        }

        // 4. Subtle pen cursor reticle
        val penDx = currentPenX - menuCenterX
        val penDy = currentPenY - menuCenterY
        val penDist = sqrt(penDx * penDx + penDy * penDy)

        if (penDist > 14f * density) {
            val cursorColor = if (hoveredMode != null) primaryColor else outlineColor
            penDotPaint.color = cursorColor
            canvas.drawCircle(currentPenX, currentPenY, 3.5f * density, penDotPaint)

            penHaloPaint.color = cursorColor
            penHaloPaint.alpha = 110
            penHaloPaint.strokeWidth = 1f * density
            canvas.drawCircle(currentPenX, currentPenY, 8f * density, penHaloPaint)
        }
    }

    private fun drawModeIcon(
        canvas: Canvas,
        mode: SpenMode,
        cx: Float,
        cy: Float,
        size: Float,
        color: Int
    ) {
        val density = resources.displayMetrics.density
        iconPaint.color = color
        iconPaint.strokeWidth = 2.2f * density
        iconPaint.style = Paint.Style.STROKE
        iconFillPaint.color = color
        iconFillPaint.style = Paint.Style.FILL

        when (mode) {
            SpenMode.HOVER -> {
                // Hover: Stylus pen angled at 45 deg + hover surface dot
                val d = size * 0.7f
                canvas.drawLine(cx + d * 0.7f, cy - d * 0.7f, cx - d * 0.3f, cy + d * 0.3f, iconPaint)
                canvas.drawLine(cx - d * 0.3f, cy + d * 0.3f, cx - d * 0.6f, cy + d * 0.6f, iconPaint)
                canvas.drawCircle(cx - d * 0.9f, cy + d * 0.9f, 2f * density, iconFillPaint)
            }
            SpenMode.HYBRID -> {
                // Hybrid: Precision Crosshair / Target with center dot
                val r = size * 0.65f
                canvas.drawCircle(cx, cy, r, iconPaint)
                val tick = r * 0.45f
                canvas.drawLine(cx - r - tick, cy, cx - r + tick, cy, iconPaint)
                canvas.drawLine(cx + r - tick, cy, cx + r + tick, cy, iconPaint)
                canvas.drawLine(cx, cy - r - tick, cx, cy - r + tick, iconPaint)
                canvas.drawLine(cx, cy + r - tick, cx, cy + r + tick, iconPaint)
                canvas.drawCircle(cx, cy, 2.2f * density, iconFillPaint)
            }
            SpenMode.MOUSE -> {
                // Mouse: Ergonomic mouse silhouette with scroll wheel notch
                val mw = size * 0.55f
                val mh = size * 0.85f
                val cr = mw
                val mouseRect = RectF(cx - mw, cy - mh, cx + mw, cy + mh)
                canvas.drawRoundRect(mouseRect, cr, cr, iconPaint)
                canvas.drawLine(cx, cy - mh, cx, cy - mh * 0.3f, iconPaint)
                canvas.drawCircle(cx, cy - mh * 0.15f, 1.8f * density, iconFillPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        controller.reset()
        super.onDetachedFromWindow()
    }
}

/**
 * Unified Tablet & Touchpad Surface Composable.
 * Accepts both touch and S-Pen input without requiring manual mode switching.
 */
@Composable
fun UnifiedTabletSurface(
    modifier: Modifier = Modifier,
    pointerDeviceSender: PointerDeviceSender,
    spenMode: SpenMode,
    spenSensitivity: Float,
    hoverRange: Float,
    mouseSensitivity: Float,
    precisionSensitivity: Float,
    deviceOrientation: Int,
    isFullScreen: Boolean = false,
    onSpenModeChange: (SpenMode) -> Unit
) {
    val context = LocalContext.current
    val spenController = remember(pointerDeviceSender, spenMode, spenSensitivity, hoverRange) {
        SpenInputController(
            sender = pointerDeviceSender,
            mode = spenMode,
            sensitivity = spenSensitivity,
            hoverRange = hoverRange,
            doubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat()
        )
    }

    val touchInputHandler = remember(pointerDeviceSender, precisionSensitivity) {
        if (pointerDeviceSender is TouchpadSender) {
            TouchInputHandler(pointerDeviceSender, precisionSensitivity)
        } else null
    }

    val mouseInputHandler = remember(pointerDeviceSender, mouseSensitivity) {
        if (pointerDeviceSender is MouseSender) {
            MouseInputHandler(pointerDeviceSender, mouseSensitivity)
        } else null
    }

    val canvasBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val surfaceContainerHighColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    val surfaceContainerHighestColor = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()
    val surfaceContainerLowestColor = MaterialTheme.colorScheme.surfaceContainerLowest.toArgb()
    val outlineColor = MaterialTheme.colorScheme.outline.toArgb()
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    val cardShape = RoundedCornerShape(CornerExtraLarge)
    val cardBorder = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val cardElevation = ElevationLevel1

    Card(
        modifier = modifier.fillMaxSize(),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape)
        ) {
            key(pointerDeviceSender, spenMode, spenSensitivity, hoverRange, mouseSensitivity, precisionSensitivity) {
                AndroidView(
                    factory = { ctx ->
                        UnifiedTabletTouchpadView(
                            context = ctx,
                            controller = spenController,
                            touchInputHandler = touchInputHandler,
                            mouseInputHandler = mouseInputHandler,
                            deviceOrientation = deviceOrientation,
                            currentMode = spenMode,
                            onModeChange = onSpenModeChange,
                            canvasBgColor = canvasBackgroundColor,
                            primaryColor = primaryColor,
                            onPrimaryColor = onPrimaryColor,
                            surfaceContainerHighColor = surfaceContainerHighColor,
                            surfaceContainerHighestColor = surfaceContainerHighestColor,
                            surfaceContainerLowestColor = surfaceContainerLowestColor,
                            outlineColor = outlineColor,
                            outlineVariantColor = outlineVariantColor,
                            onSurfaceColor = onSurfaceColor,
                            onSurfaceVariantColor = onSurfaceVariantColor
                        )
                    },
                    update = { view ->
                        view.deviceOrientation = deviceOrientation
                        view.currentMode = spenMode
                        view.onModeChange = onSpenModeChange
                        view.updateThemeColors(
                            bg = canvasBackgroundColor,
                            primary = primaryColor,
                            onPrimary = onPrimaryColor,
                            surfContainerHigh = surfaceContainerHighColor,
                            surfContainerHighest = surfaceContainerHighestColor,
                            surfContainerLowest = surfaceContainerLowestColor,
                            outline = outlineColor,
                            outlineVariant = outlineVariantColor,
                            onSurface = onSurfaceColor,
                            onSurfaceVar = onSurfaceVariantColor
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Backward compatibility alias for SpenSurface
 */
@Composable
fun SpenSurface(
    sender: PointerDeviceSender,
    mode: SpenMode,
    sensitivity: Float,
    hoverRange: Float,
    onModeChange: (SpenMode) -> Unit
) {
    UnifiedTabletSurface(
        modifier = Modifier.fillMaxSize(),
        pointerDeviceSender = sender,
        spenMode = mode,
        spenSensitivity = sensitivity,
        hoverRange = hoverRange,
        mouseSensitivity = 1f,
        precisionSensitivity = 1f,
        deviceOrientation = 0,
        onSpenModeChange = onModeChange
    )
}
