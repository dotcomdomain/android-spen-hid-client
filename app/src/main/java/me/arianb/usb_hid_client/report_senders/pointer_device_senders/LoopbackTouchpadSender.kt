package me.arianb.usb_hid_client.report_senders.pointer_device_senders

import me.arianb.usb_hid_client.hid_utils.TouchpadDevicePath
import me.arianb.usb_hid_client.hid_utils.UHID

class LoopbackTouchpadSender(
    touchpadDevicePath: TouchpadDevicePath,
    private val application: android.app.Application,
) : TouchpadSender(
    touchpadDevicePath
) {
    override suspend fun sendReport(report: ByteArray) =
        UHID.sendHidEvent(application, report)
}
