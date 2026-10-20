package com.example.robotcommunication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * Fragment that provides a serial terminal interface to send and receive 
 * raw text data to/from the robot.
 */
class TerminalFragment : Fragment() {

    // UI references
    private lateinit var tvLog: TextView
    private lateinit var etCommand: EditText
    private lateinit var btnSend: Button
    private lateinit var scrollLog: ScrollView
    private lateinit var tvStatus: TextView

    // Inflate the terminal layout
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.terminal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Bind UI elements from the XML layout
        tvLog = view.findViewById(R.id.tvLog)
        etCommand = view.findViewById(R.id.etCommand)
        btnSend = view.findViewById(R.id.btnSend)
        scrollLog = view.findViewById(R.id.scrollLog)
        tvStatus = view.findViewById(R.id.tvTerminalStatus)

        // Show current connection status at the top
        updateStatus()

        // Handle the explicit "Send" button click
        btnSend.setOnClickListener { sendCommand() }

        // Handle the "Send" action from the software keyboard (Enter key)
        etCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCommand()
                true
            } else {
                false
            }
        }

        // Set up the listener for incoming data from the robot via BluetoothService
        BluetoothService.onDataReceived = { data ->
            // UI updates must happen on the main thread
            activity?.runOnUiThread {
                appendToLog("ROBOT: $data")
            }
        }

        // Update the terminal's status indicator if the connection drops or reconnects
        BluetoothService.onConnectionStateChanged = { _ ->
            activity?.runOnUiThread {
                updateStatus()
            }
        }
    }

    /**
     * Updates the connection status header in the terminal.
     */
    private fun updateStatus() {
        if (BluetoothService.isConnected) {
            tvStatus.text = "Connected to ${BluetoothService.connectedDeviceName}"
            tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_light, null))
        } else {
            tvStatus.text = "Not connected"
            tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_light, null))
        }
    }

    /**
     * Reads text from the EditText, sends it via Bluetooth, and logs it.
     */
    private fun sendCommand() {
        val cmd = etCommand.text.toString()
        if (cmd.isNotEmpty()) {
            if (BluetoothService.isConnected) {
                // Send the command string to the robot
                BluetoothService.sendCommand(cmd)
                // Log our sent message
                appendToLog("YOU: $cmd")
                // Clear the input field for the next command
                etCommand.setText("")
            } else {
                appendToLog("ERROR: Not connected")
            }
        }
    }

    /**
     * Appends a line of text to the terminal log and auto-scrolls to the bottom.
     */
    private fun appendToLog(text: String) {
        tvLog.append("\n$text")
        // Post a scroll action to the end of the message loop to ensure it scrolls after the text is added
        scrollLog.post {
            scrollLog.fullScroll(View.FOCUS_DOWN)
        }
    }

    /**
     * Clean up: remove the data listener when the fragment is destroyed
     * to avoid memory leaks or updating a dead UI.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        BluetoothService.onDataReceived = null
    }
}