package com.example.robotcommunication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlin.math.roundToInt

// JoystickFragment shows the virtual joystick and sends its values to the robot over Bluetooth.
// Commands are sent at 20 Hz (every 50 ms) rather than on every touch event —
// this keeps the data rate manageable for the Romeo board over BT serial.
//
// The Romeo firmware receives lines like: "J:120,-85\n"
//   - First number = left/right (X), range -255 to 255
//   - Second number = forward/back (Y), range -255 to 255
class JoystickFragment : Fragment() {

    private lateinit var joystickView: JoystickView
    private lateinit var tvValues: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var sendRunnable: Runnable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.joystick, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        joystickView = view.findViewById(R.id.joystickView)
        tvValues     = view.findViewById(R.id.tvValues)

        // Update the on-screen readout whenever the thumb moves
        joystickView.onJoystickMoved = { x, y ->
            val xi = toMotorValue(x)
            val yi = toMotorValue(y)
            tvValues.text = "X: $xi   Y: $yi"
        }

        // Send loop: fires every 50 ms regardless of whether the thumb moved.
        // This means the robot always gets fresh data and won't keep moving
        // if it missed a "stop" command.
        sendRunnable = object : Runnable {
            override fun run() {
                sendJoystickData()
                handler.postDelayed(this, 50)
            }
        }
        handler.post(sendRunnable!!)
    }

    // Map the normalized -1..1 float to an integer motor value -255..255
    private fun toMotorValue(normalized: Float): Int =
        (normalized * 255).roundToInt().coerceIn(-255, 255)

    private fun sendJoystickData() {
        val x = toMotorValue(joystickView.xValue)
        val y = toMotorValue(joystickView.yValue)
        // Only send if connected; BluetoothService.sendCommand silently ignores if not connected
        BluetoothService.sendCommand("J:$x,$y")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel the send loop when navigating away — no need to keep sending
        sendRunnable?.let { handler.removeCallbacks(it) }
    }
}