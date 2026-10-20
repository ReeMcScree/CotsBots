package com.example.robotcommunication

import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.*

/**
 * Singleton object that manages Bluetooth Low Energy (BLE) communication.
 * Specifically tailored for DFRobot Romeo BLE / Bluno series boards.
 */
object BluetoothService {

    private const val TAG = "BluetoothService"
    
    // UUIDs specific to DFRobot Romeo BLE / Bluno for serial communication
    private val SERVICE_UUID = UUID.fromString("0000dfb0-0000-1000-8000-00805f9b34fb")
    private val CHARACTERISTIC_UUID = UUID.fromString("0000dfb1-0000-1000-8000-00805f9b34fb")
    // Client Characteristic Configuration Descriptor UUID
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // The system Bluetooth adapter
    internal var bluetoothAdapter: BluetoothAdapter? = null
    
    // The active GATT connection to the robot
    private var bluetoothGatt: BluetoothGatt? = null
    
    // The specific BLE characteristic used to send and receive serial data
    private var commandCharacteristic: BluetoothGattCharacteristic? = null

    // Tracks current connection state
    var isConnected = false
        private set

    // Name of the currently connected device
    var connectedDeviceName: String = "Not Connected"
        private set

    // Callback triggered when connection state changes (Connected/Disconnected)
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    
    // Callback triggered when new serial data is received from the robot
    var onDataReceived: ((String) -> Unit)? = null

    /**
     * Initializes the BluetoothAdapter using BluetoothManager (modern way).
     */
    fun init(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    /**
     * Attempts to connect to a specific Bluetooth device via GATT.
     */
    fun connectToDevice(context: Context, device: BluetoothDevice, onResult: (Boolean) -> Unit) {
        // Ensure any existing connection is closed
        disconnect()
        
        try {
            // Connect to the device's GATT server
            bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                
                // Called when the connection state changes
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(TAG, "Connected to GATT server.")
                        // Once connected, we must discover services to find the serial characteristic
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(TAG, "Disconnected from GATT server.")
                        handleDisconnect()
                        onResult(false)
                    }
                }

                // Called when GATT services are discovered
                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // Find the Romeo BLE serial service
                        val service = gatt.getService(SERVICE_UUID)
                        // Find the Romeo BLE serial characteristic
                        commandCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                        
                        if (commandCharacteristic != null) {
                            // Enable notifications so we get called when data is sent FROM the robot
                            gatt.setCharacteristicNotification(commandCharacteristic, true)
                            
                            // Configure the Client Characteristic Configuration Descriptor (CCCD) to enable notifications
                            val descriptor = commandCharacteristic?.getDescriptor(CCCD_UUID)
                            if (descriptor != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                } else {
                                    @Suppress("DEPRECATION")
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    @Suppress("DEPRECATION")
                                    gatt.writeDescriptor(descriptor)
                                }
                            }

                            // Update state and notify UI
                            isConnected = true
                            connectedDeviceName = try { 
                                device.name ?: "Romeo BLE" 
                            } catch (_: SecurityException) { 
                                "Romeo BLE" 
                            }
                            onResult(true)
                            onConnectionStateChanged?.invoke(true)
                        } else {
                            Log.e(TAG, "Romeo BLE service or characteristic not found")
                            disconnect()
                            onResult(false)
                        }
                    } else {
                        Log.w(TAG, "onServicesDiscovered received: $status")
                        onResult(false)
                    }
                }

                // Called when the robot sends data to the phone
                @Deprecated("Deprecated in Java")
                @Suppress("DEPRECATION")
                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    if (characteristic.uuid == CHARACTERISTIC_UUID) {
                        val data = String(characteristic.value)
                        onDataReceived?.invoke(data)
                    }
                }

                // Called when the robot sends data to the phone (Android 13+)
                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                    if (characteristic.uuid == CHARACTERISTIC_UUID) {
                        val data = String(value)
                        onDataReceived?.invoke(data)
                    }
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            onResult(false)
        }
    }

    /**
     * Cleans up internal state when disconnected.
     */
    private fun handleDisconnect() {
        isConnected = false
        connectedDeviceName = "Not Connected"
        commandCharacteristic = null
        onConnectionStateChanged?.invoke(false)
    }

    /**
     * Disconnects and closes the GATT connection.
     */
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during disconnect: ${e.message}")
        }
        handleDisconnect()
    }

    /**
     * Sends a string command to the robot over BLE.
     * Appends a newline as most serial protocols expect it.
     */
    fun sendCommand(command: String) {
        val characteristic = commandCharacteristic ?: return
        if (!isConnected) return

        try {
            val data = (command + "\n").toByteArray()
            // Support for different Android versions' BLE write APIs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeCharacteristic(characteristic)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during sendCommand: ${e.message}")
        }
    }

    /**
     * Returns a list of devices already paired with the phone.
     */
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * Checks if Bluetooth is currently turned on.
     */
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
}