package me.arianb.usb_hid_client

import android.app.Application
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import me.arianb.usb_hid_client.dictation.LoopbackKeyboard
import me.arianb.usb_hid_client.hid_utils.UHID
import me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation
import me.arianb.usb_hid_client.input_views.DirectInputKeyboardView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires the rooted phone with the manual input receiver focused on the external display. */
@RunWith(AndroidJUnit4::class)
class LoopbackRegressionTest {
    @Test fun localOutputWithoutRecordingAudio() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val app = instrument.targetContext.applicationContext as Application
        instrument.runOnMainSync {
            val view = DirectInputKeyboardView(instrument.targetContext)
            assertFalse(view.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
        }
        val started = SystemClock.elapsedRealtime()
        UHID.prepare(app)
        assertTrue("Loopback startup took ${SystemClock.elapsedRealtime()-started} ms", SystemClock.elapsedRealtime()-started < 10000)
        val keyboard = LoopbackKeyboard.open(app)
        try {
            for (char in "Loopback test") {
                val codes = requireNotNull(KeyCodeTranslation.keyCharToScanCodes(char))
                keyboard.send(byteArrayOf(1, codes.first, 0, codes.second, 0)); delay(30)
                keyboard.send(byteArrayOf(1, 0, 0, 0, 0)); delay(30)
            }
        } finally { keyboard.close() }
        UHID.sendHidEvent(app, byteArrayOf(1,0,40,-40,0,0,0,0,0)); delay(200)
        for ((x,y) in listOf(5000 to 5000, 27000 to 27000, 16000 to 12000)) {
            UHID.sendHidEvent(app, byteArrayOf(8,0,x.toByte(),(x ushr 8).toByte(),y.toByte(),(y ushr 8).toByte()))
            delay(300)
        }
        UHID.destroy()
    }
}
