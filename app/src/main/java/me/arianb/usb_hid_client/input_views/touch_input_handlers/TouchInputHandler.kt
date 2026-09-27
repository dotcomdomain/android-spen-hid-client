package me.arianb.usb_hid_client.input_views.touch_input_handlers

import android.view.MotionEvent
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.TouchpadSender
import timber.log.Timber
import kotlin.math.roundToInt

class TouchInputHandler(
    private val touchpadSender: TouchpadSender,
    private val sensitivity: Float = 1f
) : PointerDeviceInputHandler() {
    private var currentScanTime: UShort = getScanTime()
    private val lastRawPositions = mutableMapOf<Int, Pair<Int, Int>>()
    private val scaledPositions = mutableMapOf<Int, Pair<Float, Float>>()

    fun handleTouchMotionEvent(
        motionEvent: MotionEvent,
        surfaceWidth: Int,
        surfaceHeight: Int,
    ): Boolean {
        currentScanTime = getScanTime()
        val allPointerIndices = (0 until motionEvent.pointerCount).toList()
        if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
            lastRawPositions.clear()
            scaledPositions.clear()
        }

        when (val action = motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE -> sendFrame(
                motionEvent = motionEvent,
                pointerIndices = allPointerIndices,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
                tipSwitchForIndex = { true }
            )

            MotionEvent.ACTION_POINTER_UP -> {
                val liftedIndex = motionEvent.actionIndex
                sendFrame(
                    motionEvent = motionEvent,
                    pointerIndices = allPointerIndices,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                    tipSwitchForIndex = { it != liftedIndex }
                )

                // Android may not emit another event if the remaining fingers stay still. Send the
                // next complete frame now so Windows stops expecting the lifted contact.
                val remainingIndices = allPointerIndices.filter { it != liftedIndex }
                if (remainingIndices.isNotEmpty()) {
                    advanceScanTime()
                    sendFrame(
                        motionEvent = motionEvent,
                        pointerIndices = remainingIndices,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                        tipSwitchForIndex = { true }
                    )
                }
                val liftedPointerId = motionEvent.getPointerId(liftedIndex)
                lastRawPositions.remove(liftedPointerId)
                scaledPositions.remove(liftedPointerId)
            }

            MotionEvent.ACTION_UP -> sendFrame(
                motionEvent = motionEvent,
                pointerIndices = allPointerIndices,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
                tipSwitchForIndex = { false }
            )

            MotionEvent.ACTION_CANCEL -> sendFrame(
                motionEvent = motionEvent,
                pointerIndices = allPointerIndices,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
                tipSwitchForIndex = { false }
            )

            else -> {
                Timber.w("UNHANDLED ACTION CONSTANT: %s", action)
            }
        }

        if (motionEvent.actionMasked == MotionEvent.ACTION_UP ||
            motionEvent.actionMasked == MotionEvent.ACTION_CANCEL) {
            lastRawPositions.clear()
            scaledPositions.clear()
        }

        return true
    }

    private fun sendFrame(
        motionEvent: MotionEvent,
        pointerIndices: List<Int>,
        surfaceWidth: Int,
        surfaceHeight: Int,
        tipSwitchForIndex: (Int) -> Boolean,
    ) {
        pointerIndices.forEachIndexed { reportIndex, pointerIndex ->
            val (pointerID, rawPointerX, rawPointerY) = getPointerTriple(
                motionEvent,
                pointerIndex,
                surfaceWidth,
                surfaceHeight
            )
            val previousRaw = lastRawPositions[pointerID]
            val previousScaled = scaledPositions[pointerID]
            val (logicalMaxX, logicalMaxY) = logicalBounds()
            val scaledPosition = if (previousRaw == null || previousScaled == null) {
                Pair(rawPointerX.toFloat(), rawPointerY.toFloat())
            } else {
                Pair(
                    (previousScaled.first + (rawPointerX - previousRaw.first) * sensitivity)
                        .coerceIn(0f, logicalMaxX.toFloat()),
                    (previousScaled.second + (rawPointerY - previousRaw.second) * sensitivity)
                        .coerceIn(0f, logicalMaxY.toFloat())
                )
            }
            lastRawPositions[pointerID] = Pair(rawPointerX, rawPointerY)
            scaledPositions[pointerID] = scaledPosition
            val pointerX = scaledPosition.first.roundToInt()
            val pointerY = scaledPosition.second.roundToInt()

            // Single-finger hybrid reporting puts the total count in the first report in a frame.
            // Every following contact uses zero and shares the same scan time.
            val frameContactCount = if (reportIndex == 0) pointerIndices.size else 0
            touchpadSender.send(
                pointerID,
                tipSwitchForIndex(pointerIndex),
                pointerX,
                pointerY,
                currentScanTime,
                frameContactCount,
            )
        }
    }

    private fun advanceScanTime() {
        val nextScanTime = getScanTime()
        currentScanTime = if (nextScanTime == currentScanTime) {
            ((currentScanTime.toUInt() + 1u) and 0xffffu).toUShort()
        } else {
            nextScanTime
        }
    }

    private companion object {
        // The square descriptor leaves enough coordinate space for either screen orientation.
        private fun logicalBounds(): Pair<Int, Int> = Pair(12000, 12000)

        private fun getPointerTriple(
            motionEvent: MotionEvent,
            pointerIndex: Int,
            surfaceWidth: Int,
            surfaceHeight: Int
        ): Triple<Int, Int, Int> {
            val pointerID = motionEvent.getPointerId(pointerIndex)
            val localPointerX = motionEvent.getX(pointerIndex)
            val localPointerY = motionEvent.getY(pointerIndex)
            val logicalMax = logicalBounds()

            val (pointerX, pointerY) = adjustRange(
                point = Pair(localPointerX, localPointerY),
                surfaceMax = Pair(
                    (surfaceWidth - 1).coerceAtLeast(1).toFloat(),
                    (surfaceHeight - 1).coerceAtLeast(1).toFloat()
                ),
                logicalMax = logicalMax
            )

            val pointerTriple = Triple(pointerID, pointerX, pointerY)

            Timber.d("getPointerTriple() returning: $pointerTriple")

            return pointerTriple
        }

        // Maps surface coordinates into the ranges and physical units declared by the descriptor.
        private fun adjustRange(
            point: Pair<Float, Float>,
            surfaceMax: Pair<Float, Float>,
            logicalMax: Pair<Int, Int>
        ): Pair<Int, Int> {
            Timber.d("--- adjustRange ---")
            Timber.d("Input point: %s", point)
            Timber.d("SURFACE COORDINATE MAX = (%f, %f)", surfaceMax.first, surfaceMax.second)

            val (logicalMaxX, logicalMaxY) = logicalMax
            val shortSurfaceEdge = minOf(surfaceMax.first, surfaceMax.second)
            val physicalUnitsPerPixel = REFERENCE_SHORT_EDGE_PHYSICAL / shortSurfaceEdge
            val xRange = (physicalUnitsPerPixel * surfaceMax.first * logicalMaxX / PHYSICAL_MAX_X)
                .coerceAtMost(logicalMaxX.toFloat())
            val yRange = (physicalUnitsPerPixel * surfaceMax.second * logicalMaxY / PHYSICAL_MAX_Y)
                .coerceAtMost(logicalMaxY.toFloat())
            val xMin = (logicalMaxX - xRange) / 2f
            val yMin = (logicalMaxY - yRange) / 2f
            val xRatio = xRange / surfaceMax.first
            val yRatio = yRange / surfaceMax.second
            Timber.d("LOGICAL RANGE = (%f..%f, %f..%f)", xMin, xMin + xRange, yMin, yMin + yRange)

            val adjustedX = (xMin + point.first * xRatio).toInt()
            val adjustedY = (yMin + point.second * yRatio).toInt()

            // This will probably never actually be necessary, but might as well do it just in case.
            val finalX = adjustedX.coerceIn(0, logicalMaxX)
            val finalY = adjustedY.coerceIn(0, logicalMaxY)

            Timber.d("Final point: (%d, %d)", finalX, finalY)

            return Pair(finalX, finalY)
        }

        private fun getScanTime(): UShort {
            // Convert nanoseconds to microseconds
            val microTime = System.nanoTime() / 1000

            // Convert microseconds to 100s of microseconds
            val hundredMicroTime = microTime / 100

            return hundredMicroTime.toUShort()
        }

        private const val PHYSICAL_MAX_X = 12000f
        private const val PHYSICAL_MAX_Y = 12000f
        private const val REFERENCE_SHORT_EDGE_PHYSICAL = 5000f
    }
}
