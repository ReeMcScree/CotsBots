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

// JoystickView is a custom View that draws a movable thumb inside an outer circle.
// It reports normalized X/Y values from -1.0 (full left/down) to +1.0 (full right/up).
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Paints (drawing styles) ---

    private val outerRingPaint = Paint().apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val outerFillPaint = Paint().apply {
        color = Color.parseColor("#1A2196F3") // very transparent blue tint
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val thumbPaint = Paint().apply {
        color = Color.parseColor("#2196F3")   // solid blue thumb
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val crosshairPaint = Paint().apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    // --- Geometry (set once the View knows its size) ---
    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var thumbRadius = 0f

    // Current thumb position (starts at center)
    private var thumbX = 0f
    private var thumbY = 0f

    // Normalized output values (-1 to 1)
    var xValue = 0f
        private set
    var yValue = 0f
        private set

    // Set this lambda in your Fragment to receive movement updates
    var onJoystickMoved: ((x: Float, y: Float) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centerX = w / 2f
        centerY = h / 2f
        outerRadius = min(w, h) / 2f * 0.85f
        thumbRadius = outerRadius * 0.32f
        thumbX = centerX
        thumbY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        // Outer fill
        canvas.drawCircle(centerX, centerY, outerRadius, outerFillPaint)
        // Outer ring
        canvas.drawCircle(centerX, centerY, outerRadius, outerRingPaint)
        // Crosshair lines
        canvas.drawLine(centerX - outerRadius, centerY, centerX + outerRadius, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - outerRadius, centerX, centerY + outerRadius, crosshairPaint)
        // Thumb
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val distance = sqrt(dx * dx + dy * dy)

                if (distance <= outerRadius) {
                    // Finger is inside the circle — follow it directly
                    thumbX = event.x
                    thumbY = event.y
                } else {
                    // Clamp to the edge of the outer circle
                    val ratio = outerRadius / distance
                    thumbX = centerX + dx * ratio
                    thumbY = centerY + dy * ratio
                }

                xValue = (thumbX - centerX) / outerRadius
                // Y is inverted: up on screen = negative pixel, but we want +1 = forward
                yValue = -((thumbY - centerY) / outerRadius)
                onJoystickMoved?.invoke(xValue, yValue)
            }
            MotionEvent.ACTION_UP -> {
                // Spring back to center when finger lifts
                thumbX = centerX
                thumbY = centerY
                xValue = 0f
                yValue = 0f
                onJoystickMoved?.invoke(0f, 0f)
            }
        }
        invalidate() // redraw
        return true
    }
}