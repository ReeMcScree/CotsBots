package com.example.robotcommunication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment

/**
 * The main container for the application. Handles navigation via a side drawer,
 * permission requests, and the bottom status bar.
 */
class MainActivity : AppCompatActivity() {

    // UI references
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tvDeviceName: TextView
    private lateinit var ivStatus: ImageView

    // Activity Result Launcher for enabling Bluetooth
    private val enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Just reload the screen; it will check state again
        loadDefaultScreen()
    }

    companion object {
        // Request code for permission dialogs
        private const val REQUEST_PERMISSIONS = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize BluetoothService with context to get BluetoothAdapter
        BluetoothService.init(this)

        // Set the main layout with DrawerLayout and FragmentContainer
        setContentView(R.layout.activity_main)

        // Initialize UI components
        drawerLayout = findViewById(R.id.drawerLayout)
        tvDeviceName = findViewById(R.id.tvDeviceName)
        ivStatus = findViewById(R.id.ivStatus)

        // --- Drawer Width Configuration ---
        // Dynamically set the navigation drawer to be 50% of the screen width
        val screenWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            metrics.widthPixels
        }
        
        val drawer = findViewById<LinearLayout>(R.id.navDrawer)
        drawer.layoutParams = drawer.layoutParams.also { it.width = screenWidth / 2 }

        // --- Hamburger Menu Button ---
        // Opens or closes the drawer when the menu icon is clicked
        findViewById<View>(R.id.btnMenu).setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START)
            else
                drawerLayout.openDrawer(GravityCompat.START)
        }

        // --- Navigation Drawer Item Click Listeners ---
        
        // Switch to the Bluetooth device list/connection screen
        findViewById<LinearLayout>(R.id.navConnect).setOnClickListener {
            navigateTo(BluetoothFragment())
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        
        // Switch to the Joystick control screen
        findViewById<LinearLayout>(R.id.navJoystick).setOnClickListener {
            navigateTo(JoystickFragment())
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        
        // Switch to the Serial Terminal screen
        findViewById<LinearLayout>(R.id.navTerminal).setOnClickListener {
            navigateTo(TerminalFragment())
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        
        // "Follow Ball" mode - sends a specific command to the robot immediately
        findViewById<LinearLayout>(R.id.navFollowBall).setOnClickListener {
            BluetoothService.sendCommand("MODE:FOLLOW")
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // --- Real-time Connection Status Updates ---
        // Listens to the BluetoothService and updates the bottom bar when state changes
        BluetoothService.onConnectionStateChanged = { connected ->
            runOnUiThread { updateStatusBar(connected) }
        }

        // Shortcut: tapping the status icon in the bottom bar opens the connect screen
        ivStatus.setOnClickListener { navigateTo(BluetoothFragment()) }

        // Start the permission request flow on app launch
        requestBluetoothPermissions()
    }

    /**
     * Intercept all generic motion events (joystick/gamepad sticks) at the highest level.
     * This prevents the OS from using them for focus navigation when the joystick screen is active.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)) {
            val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (fragment is JoystickFragment) {
                // Manually push the event to the fragment and consume it immediately
                fragment.handleGenericMotionEvent(event)
                return true 
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * Intercept key events (gamepad buttons/D-pad) to prevent them from moving focus
     * between buttons in the UI while we are in joystick mode.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Check if the event came from a gamepad or joystick (including D-pad)
        val source = event.source
        if ((source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
            (source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD) ||
            (source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)) {
            
            val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (fragment is JoystickFragment) {
                // You could handle button presses here (e.g., 'A' for boost)
                // For now, we just consume it to stop menu navigation.
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Checks and requests necessary permissions based on Android API level.
     */
    private fun requestBluetoothPermissions() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires Scan and Connect permissions
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Older versions require Bluetooth Admin and Location for scanning
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (needed.isNotEmpty())
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_PERMISSIONS)
        else
            loadDefaultScreen() // If permissions are already OK, load the first screen
    }

    /**
     * Replaces the content of the fragmentContainer with the provided Fragment.
     */
    fun navigateTo(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /**
     * Updates the text and color of the bottom status bar.
     * Green = Connected, Red = Disconnected.
     */
    private fun updateStatusBar(connected: Boolean) {
        tvDeviceName.text = if (connected) BluetoothService.connectedDeviceName else "Not Connected"
        val color = if (connected)
            ContextCompat.getColor(this, android.R.color.holo_green_dark)
        else
            ContextCompat.getColor(this, android.R.color.holo_red_dark)
        ivStatus.setColorFilter(color)
    }

    /**
     * Handles the result of the runtime permission request.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) loadDefaultScreen()
    }

    /**
     * Determines which screen to show first and ensures Bluetooth is turned on.
     */
    private fun loadDefaultScreen() {
        // If the device doesn't support Bluetooth, we can't do much
        if (BluetoothService.bluetoothAdapter == null) {
            navigateTo(BluetoothFragment())
            return
        }
        
        // If Bluetooth is off, ask the user to turn it on
        if (!BluetoothService.isBluetoothEnabled()) {
            // Check for BLUETOOTH_CONNECT permission on Android 12+ before requesting enable
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions()
                return
            }
            
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableIntent)
        }
        
        // Start on the connection screen by default if not already there
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            navigateTo(BluetoothFragment())
        }
    }
}
