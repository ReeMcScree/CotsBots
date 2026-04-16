package com.example.robotcommunication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class TiltControlFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private lateinit var ballView: View
    private lateinit var arenaView: FrameLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvCommand: TextView
    private lateinit var btnCalibrate: Button

    // Calibration baseline
    private var baseX = 0f
    private var baseY = 0f
    private var isCalibrated = false

    // Ball physics state
    private var ballX = 0f
    private var ballY = 0f
    private var velX = 0f
    private var velY = 0f

    // Raw accelerometer values
    private var accelX = 0f
    private var accelY = 0f

    // Physics constants
    private val DAMPING = 0.92f
    private val FORCE_SCALE = 1.8f
    private val BALL_RADIUS_DP = 28f

    // -------------------------------------------------------------------------
    // BLUETOOTH THROTTLE — stubbed for future use
    // Uncomment and adjust SEND_INTERVAL_MS when ready to wire up BluetoothService
    //
    // private val SEND_INTERVAL_MS = 100L
    // private var lastSendTime = 0L
    // -------------------------------------------------------------------------

    // Center deadzone — ball positions within this fraction of arena size
    // from center will output 0,0,0 and (eventually) send a stop command
    private val DEADZONE_FRACTION = 0.12f

    private var lastFrameTime = 0L
    private val physicsRunnable = object : Runnable {
        override fun run() {
            if (isCalibrated) {
                updatePhysics()
                updateCommandDisplay()

                // -----------------------------------------------------------------
                // BLUETOOTH SEND — stubbed for future use
                // Wire in throttle + BluetoothService.sendCommand() here when ready
                //
                // val now = System.currentTimeMillis()
                // if (now - lastSendTime >= SEND_INTERVAL_MS) {
                //     lastSendTime = now
                //     val (motorL, motorR) = computeMotorValues()
                //     BluetoothService.sendCommand("$motorL,$motorR,0")
                // }
                // -----------------------------------------------------------------
            }
            arenaView.postDelayed(this, 16)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_tilt_control, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        arenaView    = view.findViewById(R.id.arenaView)
        ballView     = view.findViewById(R.id.ballView)
        tvStatus     = view.findViewById(R.id.tvStatus)
        tvCommand    = view.findViewById(R.id.tvCommand)
        btnCalibrate = view.findViewById(R.id.btnCalibrate)

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        btnCalibrate.setOnClickListener {
            baseX = accelX
            baseY = accelY
            isCalibrated = true

            arenaView.post {
                ballX = arenaView.width / 2f
                ballY = arenaView.height / 2f
                velX = 0f
                velY = 0f
                ballView.visibility = View.VISIBLE
                tvStatus.text = "Calibrated!"
            }
        }

        ballView.visibility = View.INVISIBLE
        tvStatus.text = "Place phone on a flat surface, then tap Calibrate."
        tvCommand.text = "Command: --"

        arenaView.post { arenaView.postDelayed(physicsRunnable, 16) }
    }

    // Returns Pair(leftMotor, rightMotor) in range -100..100
    private fun computeMotorValues(): Pair<Int, Int> {
        val arenaW = arenaView.width.toFloat()
        val arenaH = arenaView.height.toFloat()
        if (arenaW == 0f || arenaH == 0f) return Pair(0, 0)

        val normX = ((ballX - arenaW / 2f) / (arenaW / 2f)).coerceIn(-1f, 1f)
        val normY = ((ballY - arenaH / 2f) / (arenaH / 2f)).coerceIn(-1f, 1f)

        // Deadzone check
        val deadzoneW = arenaW * DEADZONE_FRACTION
        val deadzoneH = arenaH * DEADZONE_FRACTION
        if (Math.abs(ballX - arenaW / 2f) < deadzoneW &&
            Math.abs(ballY - arenaH / 2f) < deadzoneH) {
            return Pair(0, 0)
        }

        val baseSpeed    = -normY * 100f
        val ySign        = if (normY <= 0f) 1f else -1f
        val differential = normX * ySign * 100f

        var leftMotor  = baseSpeed + differential
        var rightMotor = baseSpeed - differential

        // Proportional rescale only when over 100
        val maxVal = maxOf(Math.abs(leftMotor), Math.abs(rightMotor))
        if (maxVal > 100f) {
            leftMotor  = leftMotor  / maxVal * 100f
            rightMotor = rightMotor / maxVal * 100f
        }

        return Pair(leftMotor.toInt(), rightMotor.toInt())
    }

    private fun updateCommandDisplay() {
        val (motorL, motorR) = computeMotorValues()
        tvCommand.text = "Command: $motorL , $motorR , 0"
    }

    private fun updatePhysics() {
        val now = System.currentTimeMillis()
        val dt = if (lastFrameTime == 0L) 0.016f else ((now - lastFrameTime) / 1000f).coerceIn(0.005f, 0.05f)
        lastFrameTime = now

        val ballRadiusPx = dpToPx(BALL_RADIUS_DP)

        val tiltX = -(accelX - baseX)
        val tiltY =  (accelY - baseY)

        velX += tiltX * FORCE_SCALE * dt * 60f
        velY += tiltY * FORCE_SCALE * dt * 60f

        velX *= DAMPING
        velY *= DAMPING

        ballX += velX
        ballY += velY

        val minX = ballRadiusPx
        val maxX = arenaView.width - ballRadiusPx
        val minY = ballRadiusPx
        val maxY = arenaView.height - ballRadiusPx

        if (ballX < minX) { ballX = minX; velX = 0f }
        if (ballX > maxX) { ballX = maxX; velX = 0f }
        if (ballY < minY) { ballY = minY; velY = 0f }
        if (ballY > maxY) { ballY = maxY; velY = 0f }

        ballView.x = ballX - ballRadiusPx
        ballView.y = ballY - ballRadiusPx
    }

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        arenaView.removeCallbacks(physicsRunnable)
        lastFrameTime = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            accelX = event.values[0]
            accelY = event.values[1]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}