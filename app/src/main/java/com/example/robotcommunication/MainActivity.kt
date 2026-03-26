package com.example.robotcommunication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tvDeviceName: TextView
    private lateinit var ivStatus: ImageView

    companion object {
        private const val REQUEST_PERMISSIONS = 1
        private const val REQUEST_ENABLE_BT = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        tvDeviceName = findViewById(R.id.tvDeviceName)
        ivStatus = findViewById(R.id.ivStatus)

        // --- Make the drawer exactly half the screen width ---
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val drawer = findViewById<LinearLayout>(R.id.navDrawer)
        drawer.layoutParams = drawer.layoutParams.also { it.width = metrics.widthPixels / 2 }

        // --- Hamburger button ---
        findViewById<android.view.View>(R.id.btnMenu).setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START)
            else
                drawerLayout.openDrawer(GravityCompat.START)
        }

        // --- Drawer nav items ---
        findViewById<LinearLayout>(R.id.navConnect).setOnClickListener {
            navigateTo(BluetoothFragment())
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<LinearLayout>(R.id.navJoystick).setOnClickListener {
            navigateTo(JoystickFragment())
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<LinearLayout>(R.id.navFollowBall).setOnClickListener {
            // "Follow Ball" just fires a single command to the robot, no screen change needed.
            // Your Romeo sketch should listen for "MODE:FOLLOW\n" on Serial and start the behavior.
            BluetoothService.sendCommand("MODE:FOLLOW")
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // --- Status bar updates (runs on UI thread) ---
        BluetoothService.onConnectionStateChanged = { connected ->
            runOnUiThread { updateStatusBar(connected) }
        }

        // Tapping the controller icon takes you to the connect screen
        ivStatus.setOnClickListener { navigateTo(BluetoothFragment()) }

        requestBluetoothPermissions()
    }

    private fun requestBluetoothPermissions() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (needed.isNotEmpty())
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_PERMISSIONS)
        else
            loadDefaultScreen()
    }

    // Swap out whichever Fragment is in the container
    fun navigateTo(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun updateStatusBar(connected: Boolean) {
        tvDeviceName.text = if (connected) BluetoothService.connectedDeviceName else "Not Connected"
        val color = if (connected)
            ContextCompat.getColor(this, android.R.color.holo_green_dark)
        else
            ContextCompat.getColor(this, android.R.color.holo_red_dark)
        ivStatus.setColorFilter(color)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) loadDefaultScreen()
    }

    private fun loadDefaultScreen() {
        if (BluetoothService.bluetoothAdapter == null) {
            navigateTo(BluetoothFragment())
            return
        }
        if (!BluetoothService.isBluetoothEnabled()) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
        }
        navigateTo(BluetoothFragment())
    }
}