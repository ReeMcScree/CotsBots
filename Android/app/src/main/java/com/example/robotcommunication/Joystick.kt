package com.example.robotcommunication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A custom UI component that renders a virtual joystick.
 * It provides normalized X and Y values (-1.0 to 1.0) based on the thumb position.
 * It also supports hardware game controllers via onGenericMotionEvent.
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        // Ensure the view can receive focus to capture motion events
        isFocusable = true
        isFocusableInTouchMode = true
    }

    // --- Drawing Styles (Paints) ---

    private val outerRingPaint = Paint().apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val outerFillPaint = Paint().apply {
        color = Color.parseColor("#1A2196F3")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val thumbPaint = Paint().apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val crosshairPaint = Paint().apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    // --- Geometry properties ---
    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var thumbRadius = 0f

    private var thumbX = 0f
    private var thumbY = 0f

    var xValue = 0f
        private set
        
    var yValue = 0f
        private set

    var onJoystickMoved: ((x: Float, y: Float) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centerX = w / 2f
        centerY = h / 2f
        outerRadius = min(w, h) / 2f * 0.85f
        thumbRadius = outerRadius * 0.32f
        updateThumbFromValues()
    }

    private fun updateThumbFromValues() {
        thumbX = centerX + xValue * outerRadius
        thumbY = centerY - yValue * outerRadius
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(centerX, centerY, outerRadius, outerFillPaint)
        canvas.drawCircle(centerX, centerY, outerRadius, outerRingPaint)
        canvas.drawLine(centerX - outerRadius, centerY, centerX + outerRadius, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - outerRadius, centerX, centerY + outerRadius, crosshairPaint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val distance = sqrt(dx * dx + dy * dy)

                if (distance <= outerRadius) {
                    thumbX = event.x
                    thumbY = event.y
                } else {
                    val ratio = outerRadius / distance
                    thumbX = centerX + dx * ratio
                    thumbY = centerY + dy * ratio
                }

                xValue = (thumbX - centerX) / outerRadius
                yValue = -((thumbY - centerY) / outerRadius)
                onJoystickMoved?.invoke(xValue, yValue)
            }
            
            MotionEvent.ACTION_UP -> {
                resetJoystick()
            }
        }
        invalidate()
        return true
    }

    fun resetJoystick() {
        thumbX = centerX
        thumbY = centerY
        xValue = 0f
        yValue = 0f
        onJoystickMoved?.invoke(0f, 0f)
        invalidate()
    }

    /**
     * Processes hardware joystick events. 
     * returns true if it consumed the event, which prevents focus navigation.
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // If it's a joystick or gamepad axis movement
        val source = event.source
        if (source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        ) {
            if (event.action == MotionEvent.ACTION_MOVE) {
                // Process current and historical samples
                val historySize = event.historySize
                for (i in 0 until historySize) {
                    processJoystickInput(event, i)
                }
                processJoystickInput(event, -1)
                return true // Consume the event!
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun processJoystickInput(event: MotionEvent, historyPos: Int) {
        val device = event.device ?: return

        // Try getting values from primary axes (Left Stick usually)
        var x = getCenteredAxis(event, device, MotionEvent.AXIS_Z, historyPos)
        var y = getCenteredAxis(event, device, MotionEvent.AXIS_Y, historyPos)

        xValue = x
        yValue = -y // Invert Y for robot "forward"

        updateThumbFromValues()
        onJoystickMoved?.invoke(xValue, yValue)
        invalidate()
    }

    private fun getCenteredAxis(
        event: MotionEvent,
        device: InputDevice,
        axis: Int,
        historyPos: Int
    ): Float {
        val range = device.getMotionRange(axis, event.source) ?: return 0f
        val value = if (historyPos < 0) {
            event.getAxisValue(axis)
        } else {
            event.getHistoricalAxisValue(axis, historyPos)
        }

        // Deadzone handling
        return if (abs(value) > range.flat) value else 0f
    }
}
