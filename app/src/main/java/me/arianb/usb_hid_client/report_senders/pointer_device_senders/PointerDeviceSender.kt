package me.arianb.usb_hid_client.report_senders.pointer_device_senders

import me.arianb.usb_hid_client.hid_utils.TouchpadDevicePath
import me.arianb.usb_hid_client.report_senders.ReportSender
import me.arianb.usb_hid_client.report_senders.safeBitSetToByte
import java.util.BitSet

sealed class PointerDeviceSender(
    touchpadDevicePath: TouchpadDevicePath
) : ReportSender(
    touchpadDevicePath
) {
    fun sendRelativeMouseReport(x: Int, y: Int, buttons: TouchpadButtonState) {
        require(x in -127..127 && y in -127..127)
        addReportToChannel(byteArrayOf(1, buttons.toByte(), x.toByte(), y.toByte()) + ByteArray(5))
    }

    fun sendAbsoluteMouseReport(x: Int, y: Int, buttons: TouchpadButtonState) {
        val clampedX = x.coerceIn(0, 32767)
        val clampedY = y.coerceIn(0, 32767)
        addReportToChannel(byteArrayOf(
            8, buttons.toByte(),
            clampedX.toByte(), (clampedX ushr 8).toByte(),
            clampedY.toByte(), (clampedY ushr 8).toByte()
        ))
    }
    abstract fun send(
        contactID: Byte,
        tipSwitch: Boolean,
        x: Short,
        y: Short,
        scanTime: UShort,
        contactCount: Byte,
        touchpadButtonState: TouchpadButtonState
    )

    data class TouchpadButtonState(
        val isLeftButtonPressed: Boolean,
        val isRightButtonPressed: Boolean,
    ) {
        fun toByte(): Byte {
            val firstByteBitSet = BitSet(8).apply {
                set(0, isLeftButtonPressed)
                set(1, isRightButtonPressed)

                // Padding
                clear(2, 8)
            }

            val buttonByte = safeBitSetToByte(firstByteBitSet)

            return buttonByte
        }
    }
}
