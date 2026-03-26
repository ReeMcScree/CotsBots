package com.example.robotcommunication

object BluetoothService {
    var isConnected = false
    var connectedDeviceName: String = "Not Connected"
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    fun sendCommand(command: String) { }
}