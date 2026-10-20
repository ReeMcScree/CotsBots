package com.example.robotcommunication

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Fragment responsible for scanning (listing paired devices) and connecting to the robot.
 */
class BluetoothFragment : Fragment() {

    // UI elements for device listing and status
    private lateinit var listView: ListView
    private lateinit var tvStatus: TextView
    private lateinit var btnRefresh: Button

    // Data lists for the ListView adapter
    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    // Register a launcher for requesting Bluetooth/Location permissions at runtime
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Check if all requested permissions were granted
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                // If granted, refresh the list of paired devices
                refreshDeviceList()
            } else {
                // If denied, inform the user
                Toast.makeText(requireContext(), "Permissions required for Bluetooth functionality", Toast.LENGTH_SHORT).show()
            }
        }

    // Standard Fragment lifecycle: Inflate the layout
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.connect, container, false)
    }

    // Standard Fragment lifecycle: Initialize UI and listeners after the view is created
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listView   = view.findViewById(R.id.listDevices)
        tvStatus   = view.findViewById(R.id.tvStatus)
        btnRefresh = view.findViewById(R.id.btnRefresh)

        // Setup the adapter to display device names in the list
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, deviceNames)
        listView.adapter = adapter

        // Refresh button logic: checks permissions before updating the list
        btnRefresh.setOnClickListener { 
            if (hasPermissions()) {
                refreshDeviceList()
            } else {
                requestPermissions()
            }
        }

        // Clicking an item in the list attempts to connect to that device
        listView.setOnItemClickListener { _, _, position, _ ->
            connectToDevice(pairedDevices[position])
        }

        // Initial check: if permissions are already granted, show the devices
        if (hasPermissions()) {
            refreshDeviceList()
        } else {
            requestPermissions()
        }
        
        // Update the initial status text based on current connection state
        updateStatusText()
    }

    /**
     * Helper to check if the required Bluetooth/Location permissions are granted.
     */
    private fun hasPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Launches the system permission dialog for the required permissions.
     */
    private fun requestPermissions() {
        requestPermissionLauncher.launch(getRequiredPermissions())
    }

    /**
     * Returns the array of permissions needed based on the Android version.
     * Android 12 (API 31) and above use specific Bluetooth permissions.
     */
    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    /**
     * Retrieves the list of paired devices from the Bluetooth adapter and updates the UI.
     */
    private fun refreshDeviceList() {
        pairedDevices.clear()
        deviceNames.clear()

        // Check if Bluetooth is even supported on the hardware
        if (BluetoothService.bluetoothAdapter == null) {
            deviceNames.add("Bluetooth not available on this device")
            adapter.notifyDataSetChanged()
            return
        }

        // Ensure we have permissions before calling getPairedDevices
        if (!hasPermissions()) {
            deviceNames.add("Bluetooth permission denied")
            adapter.notifyDataSetChanged()
            return
        }

        // Get the list of bonded (paired) devices from the BluetoothService
        val devices = BluetoothService.getPairedDevices()
        if (devices.isEmpty()) {
            deviceNames.add("No paired devices — pair in Android Settings first")
        } else {
            // Populate our local lists for the ListView
            devices.forEach {
                try {
                    pairedDevices.add(it)
                    deviceNames.add("${it.name ?: "Unknown Device"}   (${it.address})")
                } catch (e: SecurityException) {
                    deviceNames.add("Security Exception: Permission missing")
                }
            }
        }
        // Notify the adapter to redraw the list
        adapter.notifyDataSetChanged()
    }

    /**
     * Calls the BluetoothService to start a GATT connection to the selected device.
     */
    private fun connectToDevice(device: BluetoothDevice) {
        if (!hasPermissions()) {
            requestPermissions()
            return
        }
        
        try {
            tvStatus.text = "Connecting to ${device.name}..."
            BluetoothService.connectToDevice(requireContext(), device) { success ->
                // Use runOnUiThread because the GATT callback happens on a background thread
                activity?.runOnUiThread {
                    if (success) {
                        tvStatus.text = "Connected to ${device.name}!"
                        // On success, automatically switch to the Joystick control screen
                        (activity as? MainActivity)?.navigateTo(JoystickFragment())
                    } else {
                        tvStatus.text = "Failed to connect. Make sure the robot is on and try again."
                    }
                }
            }
        } catch (e: SecurityException) {
            tvStatus.text = "Permission error during connection"
        }
    }

    /**
     * Updates the status TextView at the top of the fragment.
     */
    private fun updateStatusText() {
        tvStatus.text = if (BluetoothService.isConnected)
            "Connected to: ${BluetoothService.connectedDeviceName}"
        else
            "Not connected. Select a paired device below."
    }
}