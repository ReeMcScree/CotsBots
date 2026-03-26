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
import androidx.core.content.ContextCompat


class BluetoothFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var tvStatus: TextView
    private lateinit var btnRefresh: Button

    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.connect, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listView   = view.findViewById(R.id.listDevices)
        tvStatus   = view.findViewById(R.id.tvStatus)
        btnRefresh = view.findViewById(R.id.btnRefresh)

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, deviceNames)
        listView.adapter = adapter

        btnRefresh.setOnClickListener { refreshDeviceList() }

        listView.setOnItemClickListener { _, _, position, _ ->
            connectToDevice(pairedDevices[position])
        }

        refreshDeviceList()
        updateStatusText()
    }

    private fun refreshDeviceList() {
        pairedDevices.clear()
        deviceNames.clear()

        if (BluetoothService.bluetoothAdapter == null) {
            deviceNames.add("Bluetooth not available on this device")
            adapter.notifyDataSetChanged()
            return
        }

        // Check permission before accessing paired devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                deviceNames.add("Bluetooth permission denied — please grant it in Settings")
                adapter.notifyDataSetChanged()
                return
            }
        }

        val devices = BluetoothService.getPairedDevices()
        if (devices.isEmpty()) {
            deviceNames.add("No paired devices — pair in Android Settings first")
        } else {
            devices.forEach {
                pairedDevices.add(it)
                deviceNames.add("${it.name}   (${it.address})")
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        tvStatus.text = "Connecting to ${device.name}..."
        BluetoothService.connectToDevice(device) { success ->
            activity?.runOnUiThread {
                if (success) {
                    tvStatus.text = "Connected to ${device.name}!"
                    (activity as? MainActivity)?.navigateTo(JoystickFragment())
                } else {
                    tvStatus.text = "Failed to connect. Make sure the robot is on and try again."
                }
            }
        }
    }

    private fun updateStatusText() {
        tvStatus.text = if (BluetoothService.isConnected)
            "Connected to: ${BluetoothService.connectedDeviceName}"
        else
            "Not connected. Select a paired device below."
    }
}