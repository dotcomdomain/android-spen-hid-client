package me.arianb.usb_hid_client.report_senders

import me.arianb.usb_hid_client.hid_utils.KeyboardDevicePath

class KeySender(
    keyboardDevicePath: KeyboardDevicePath
) : ReportSender(
    keyboardDevicePath
) {
    fun addStandardKey(modifier: Byte, key: Byte) {
        super.addReportToChannel(byteArrayOf(STANDARD_KEY, modifier, 0, key, 0))
    }

    fun setStandardKeyState(modifier: Byte, firstKey: Byte, secondKey: Byte = 0) {
        super.addReportToChannel(
            byteArrayOf(STANDARD_KEY, modifier, 0, firstKey, secondKey, STATE_REPORT_MARKER)
        )
    }

    fun addMediaKey(key: Byte) {
        super.addReportToChannel(byteArrayOf(MEDIA_KEY, key, 0))
    }

    fun setMediaKeyState(key: Byte) {
        super.addReportToChannel(byteArrayOf(MEDIA_KEY, key, 0, STATE_REPORT_MARKER))
    }

    // Every time we send a report, we only send the "key-down" event. This method will automatically send the "key-up"
    // event right afterward
    override fun sendReport(report: ByteArray) {
        if (report.lastOrNull() == STATE_REPORT_MARKER) {
            writeBytes(report.copyOf(report.size - 1))
            return
        }

        // Send "key-down" report
        writeBytes(report)

        // Send "key-up" report of all zeroes (preserving report ID) to release
        val releaseReport = ByteArray(report.size)
        releaseReport[0] = report[0]
        writeBytes(releaseReport)
    }

    companion object {
        // Report IDs
        private const val STANDARD_KEY: Byte = 0x01
        private const val MEDIA_KEY: Byte = 0x02
        private const val STATE_REPORT_MARKER: Byte = 0x7f
    }
}
