package com.example.robotcommunication

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

object BluetoothService {

    private val TAG = "BluetoothService"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    internal var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    var isConnected = false
        private set

    var connectedDeviceName: String = "Not Connected"
        private set

    var onConnectionStateChanged: ((Boolean) -> Unit)? = null

    fun connectToDevice(device: BluetoothDevice, onResult: (Boolean) -> Unit) {
        Thread {
            try {
                bluetoothSocket?.close()
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothAdapter?.cancelDiscovery()
                socket.connect()
                bluetoothSocket = socket
                outputStream = socket.outputStream
                isConnected = true
                connectedDeviceName = device.name ?: "Unknown"
                onResult(true)
                onConnectionStateChanged?.invoke(true)
            } catch (e: IOException) {
                Log.e(TAG, "Connection failed: ${e.message}")
                isConnected = false
                connectedDeviceName = "Not Connected"
                onResult(false)
                onConnectionStateChanged?.invoke(false)
            }
        }.start()
    }

    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Disconnect error: ${e.message}")
        }
        isConnected = false
        connectedDeviceName = "Not Connected"
        onConnectionStateChanged?.invoke(false)
    }

    fun sendCommand(command: String) {
        if (!isConnected) return
        try {
            outputStream?.write((command + "\n").toByteArray())
        } catch (e: IOException) {
            Log.e(TAG, "Send failed: ${e.message}")
            isConnected = false
            connectedDeviceName = "Not Connected"
            onConnectionStateChanged?.invoke(false)
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
}