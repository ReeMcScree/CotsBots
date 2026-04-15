package com.example.robotcommunication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlin.math.roundToInt

/**
 * Fragment that displays a virtual joystick to control the robot's movement.
 * It sends X and Y motor values to the robot at a fixed frequency.
 */
class JoystickFragment : Fragment() {

    // UI references
    private lateinit var joystickView: JoystickView
    private lateinit var tvValues: TextView

    // Handler and Runnable used to create a timed loop for sending data
    private val handler = Handler(Looper.getMainLooper())
    private var sendRunnable: Runnable? = null

    // Inflate the joystick layout
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.joystick, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Bind UI elements
        joystickView = view.findViewById(R.id.joystickView)
        tvValues     = view.findViewById(R.id.tvValues)

        // Set a listener on the custom JoystickView to update the on-screen X/Y readout
        joystickView.onJoystickMoved = { x, y ->
            // Convert normalized -1.0..1.0 values to -255..255 for the motor display
            val xi = toMotorValue(x)
            val yi = toMotorValue(y)
            tvValues.text = "X: $xi   Y: $yi"
        }

        // Initialize the repeating task that sends joystick data to the robot
        // This runs every 50ms (20Hz) to ensure smooth control without overwhelming the serial buffer
        sendRunnable = object : Runnable {
            override fun run() {
                sendJoystickData()
                // Schedule the next execution in 50ms
                handler.postDelayed(this, 50)
            }
        }
        // Start the loop
        handler.post(sendRunnable!!)
    }

    /**
     * Handles generic motion events (like hardware joystick movement) dispatched from the Activity.
     */
    fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        return if (::joystickView.isInitialized) {
            joystickView.onGenericMotionEvent(event)
        } else {
            false
        }
    }

    /**
     * Maps a normalized float (-1.0 to 1.0) to an integer motor speed (-255 to 255).
     */
    private fun toMotorValue(normalized: Float): Int =
        (normalized * 255).roundToInt().coerceIn(-255, 255)

    /**
     * Formats and sends the current joystick position to the robot via BluetoothService.
     * Command format: "J:X_VALUE,Y_VALUE" (e.g., "J:150,-200")
     */
    private fun sendJoystickData() {
        val x = toMotorValue(joystickView.xValue)
        val y = toMotorValue(joystickView.yValue)
        
        // If connected, send the formatted command string
        if (BluetoothService.isConnected) {
            if (y > 50) {
                BluetoothService.sendCommand("w100")
            }
            else if (y < -50) {
                BluetoothService.sendCommand("s100")
            }
            else if (x > 50) {
                BluetoothService.sendCommand("d100")
            }
            else if (x < -50) {
                BluetoothService.sendCommand("a100")
            }
        }
    }

    /**
     * Clean up: Stop the data-sending loop when the user leaves this screen.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        sendRunnable?.let { handler.removeCallbacks(it) }
    }
}
