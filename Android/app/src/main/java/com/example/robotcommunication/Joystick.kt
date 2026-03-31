package com.example.robotcommunication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A custom UI component that renders a virtual joystick.
 * It provides normalized X and Y values (-1.0 to 1.0) based on the thumb position.
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Drawing Styles (Paints) ---

    // Style for the circular border of the joystick
    private val outerRingPaint = Paint().apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    // Semi-transparent fill for the background of the joystick area
    private val outerFillPaint = Paint().apply {
        color = Color.parseColor("#1A2196F3") // Very transparent blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Solid blue style for the movable joystick "thumb"
    private val thumbPaint = Paint().apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Style for the subtle crosshair lines in the center
    private val crosshairPaint = Paint().apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    // --- Geometry properties (calculated based on view size) ---
    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var thumbRadius = 0f

    // Current coordinates of the thumb (defaults to center)
    private var thumbX = 0f
    private var thumbY = 0f

    // Normalized horizontal value (-1.0 for left, 1.0 for right)
    var xValue = 0f
        private set
        
    // Normalized vertical value (1.0 for forward/up, -1.0 for back/down)
    var yValue = 0f
        private set

    // Callback that triggers whenever the joystick position changes
    var onJoystickMoved: ((x: Float, y: Float) -> Unit)? = null

    /**
     * Called whenever the view size changes (e.g., on rotation or initial layout).
     * We use this to calculate the center and radii.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centerX = w / 2f
        centerY = h / 2f
        // Make the joystick slightly smaller than the available space
        outerRadius = min(w, h) / 2f * 0.85f
        // Set thumb size to roughly 1/3 of the outer radius
        thumbRadius = outerRadius * 0.32f
        // Place thumb at the center initially
        thumbX = centerX
        thumbY = centerY
    }

    /**
     * Renders the joystick components onto the screen.
     */
    override fun onDraw(canvas: Canvas) {
        // 1. Draw the background fill
        canvas.drawCircle(centerX, centerY, outerRadius, outerFillPaint)
        
        // 2. Draw the outer circular border
        canvas.drawCircle(centerX, centerY, outerRadius, outerRingPaint)
        
        // 3. Draw horizontal and vertical crosshair lines for reference
        canvas.drawLine(centerX - outerRadius, centerY, centerX + outerRadius, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - outerRadius, centerX, centerY + outerRadius, crosshairPaint)
        
        // 4. Draw the movable thumb at its current calculated position
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
    }

    /**
     * Processes touch interactions to move the joystick thumb.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Calculate distance from the center of the joystick to the touch point
                val dx = event.x - centerX
                val dy = event.y - centerY
                val distance = sqrt(dx * dx + dy * dy)

                if (distance <= outerRadius) {
                    // Finger is within bounds: thumb follows finger exactly
                    thumbX = event.x
                    thumbY = event.y
                } else {
                    // Finger is outside: clamp the thumb to the edge of the circle
                    val ratio = outerRadius / distance
                    thumbX = centerX + dx * ratio
                    thumbY = centerY + dy * ratio
                }

                // Convert pixel position to normalized -1.0..1.0 range
                xValue = (thumbX - centerX) / outerRadius
                // In Android, Y increases downwards. We invert it so "up" is positive 1.0.
                yValue = -((thumbY - centerY) / outerRadius)
                
                // Notify any listeners of the new position
                onJoystickMoved?.invoke(xValue, yValue)
            }
            
            MotionEvent.ACTION_UP -> {
                // When finger is lifted, snap the thumb back to the center position
                thumbX = centerX
                thumbY = centerY
                xValue = 0f
                yValue = 0f
                onJoystickMoved?.invoke(0f, 0f)
            }
        }
        
        // Request a redraw because the thumb coordinates changed
        invalidate()
        return true
    }
}