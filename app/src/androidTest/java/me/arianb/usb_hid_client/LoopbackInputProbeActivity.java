package me.arianb.usb_hid_client;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.widget.TextView;

/** Manual receiver for checking real loopback output on a secondary display. */
public class LoopbackInputProbeActivity extends Activity {
    private TextView output;
    private void record(String message) {
        Log.i("LoopbackInputProbe", message);
        if (output != null) output.setText(message);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        output = new TextView(this);
        output.setText("Loopback input receiver");
        output.setTextSize(24);
        output.setPadding(32, 32, 32, 32);
        setContentView(output);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e) {
        record("motion action=" + e.getActionMasked() + " buttons=" + e.getButtonState()
            + " actionButton=" + e.getActionButton() + " x=" + e.getX() + " y=" + e.getY()
            + " scroll=" + e.getAxisValue(MotionEvent.AXIS_VSCROLL));
        return true;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        record("touch action=" + e.getActionMasked() + " buttons=" + e.getButtonState()
            + " x=" + e.getX() + " y=" + e.getY());
        return true;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        record("key action=" + e.getAction() + " code=" + e.getKeyCode() + " modifiers=" + e.getMetaState());
        return true;
    }
}
