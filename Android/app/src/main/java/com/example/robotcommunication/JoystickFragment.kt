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
    
    // To avoid spamming the same command repeatedly
    private var lastSentCommand = ""

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
        // This runs every 100ms (10Hz) to ensure smooth control
        sendRunnable = object : Runnable {
            override fun run() {
                sendJoystickData()
                // Schedule the next execution
                handler.postDelayed(this, 100)
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
     */
    private fun sendJoystickData() {
        if (!::joystickView.isInitialized) return

        val x = toMotorValue(joystickView.xValue)
        val y = toMotorValue(joystickView.yValue)
        
        // Update display to match what is being processed
        tvValues.text = "X: $x   Y: $y"

        if (BluetoothService.isConnected) {
            // Priority 1: Forward/Backward (Y-axis)
            // Priority 2: Spin Left/Right (X-axis)
            val command = when {
                y > 15 || y < -15 -> "$y,$y,100"      // Forward/Backward
                x > 15 || x < -15 -> "$x,${-x},100"   // Spin (X determines direction automatically)
                else              -> "0,0,0"           // Stop
            }

            // Only send if the command has changed or if it's an active movement command
            if (command != lastSentCommand || command != "0,0,0") {
                BluetoothService.sendCommand(command)
                lastSentCommand = command
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
