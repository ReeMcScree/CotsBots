package com.example.robotcommunication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Fragment that uses OpenCV to detect any colored ball selected via touch input
 * from the camera stream and sends movement commands to the robot via Bluetooth.
 */
class FollowBallFragment : Fragment(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var cameraView: CameraBridgeViewBase
    private lateinit var tvTrackingStatus: TextView

    private lateinit var rgbaMat: Mat
    private lateinit var smallMat: Mat
    private lateinit var blurred: Mat
    private lateinit var hsvMat: Mat
    private lateinit var maskMat: Mat

    private var lastSendTime = 0L
    private var lastDetectedTime = System.currentTimeMillis()

    // Tunable OpenCV area thresholds for distance control (adjust via experimentation)
    private var stopAreaThreshold = 75000.0   // Area above which the robot stops in place
    private var backupAreaThreshold = 120000.0 // Area above which the robot backs up

    // Selected color tracking state
    private var selectedH = 0.0
    private var selectedS = 0.0
    private var selectedV = 0.0
    private var hasSelectedColor = false

    private val requestCameraLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                tvTrackingStatus.text = "Tap any ball on screen to select color..."
                cameraView.setCameraPermissionGranted()
                cameraView.enableFpsMeter()
                cameraView.enableView()
            } else {
                Toast.makeText(requireContext(), "Camera permission is required for ball tracking", Toast.LENGTH_SHORT).show()
                tvTrackingStatus.text = "Error: Camera permission denied."
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.follow_ball, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cameraView = view.findViewById(R.id.javaCameraView)
        tvTrackingStatus = view.findViewById(R.id.tvTrackingStatus)

        cameraView.visibility = SurfaceView.VISIBLE
        cameraView.setCvCameraViewListener(this)
        cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK)

        // Touch listener to select color from camera preview
        cameraView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                v.performClick()
                val touchX = event.x
                val touchY = event.y
                if (cameraView.width > 0 && cameraView.height > 0 && ::hsvMat.isInitialized && !hsvMat.empty()) {
                    // Map touch coordinates to smallMat coordinates (50% scale)
                    val matX = (touchX / cameraView.width * hsvMat.cols()).toInt().coerceIn(0, hsvMat.cols() - 1)
                    val matY = (touchY / cameraView.height * hsvMat.rows()).toInt().coerceIn(0, hsvMat.rows() - 1)
                    val hsvPixel = hsvMat.get(matY, matX)
                    if (hsvPixel != null && hsvPixel.size >= 3) {
                        selectedH = hsvPixel[0]
                        selectedS = hsvPixel[1]
                        selectedV = hsvPixel[2]
                        hasSelectedColor = true
                        lastDetectedTime = System.currentTimeMillis()
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Tracking Color Set! HSV(${selectedH.toInt()}, ${selectedS.toInt()}, ${selectedV.toInt()})", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                true
            } else {
                false
            }
        }

        if (OpenCVLoader.initDebug()) {
            tvTrackingStatus.text = "Tap any ball on screen to select color..."
        } else {
            tvTrackingStatus.text = "Error initializing OpenCV."
        }
    }

    override fun onResume() {
        super.onResume()
        if (OpenCVLoader.initDebug()) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                tvTrackingStatus.text = "Tap any ball on screen to select color..."
                cameraView.setCameraPermissionGranted()
                cameraView.enableFpsMeter()
                cameraView.enableView()
            } else {
                tvTrackingStatus.text = "Requesting camera permission..."
                requestCameraLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        cameraView.disableView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraView.disableView()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        rgbaMat = Mat()
        smallMat = Mat()
        blurred = Mat()
        hsvMat = Mat()
        maskMat = Mat()
    }

    override fun onCameraViewStopped() {
        rgbaMat.release()
        smallMat.release()
        blurred.release()
        hsvMat.release()
        maskMat.release()
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        rgbaMat = inputFrame.rgba()

        // Downsize by 50% for high performance on weaker devices (75% fewer pixels to process)
        val scale = 0.5
        Imgproc.resize(rgbaMat, smallMat, Size(), scale, scale, Imgproc.INTER_LINEAR)

        val smallWidth = smallMat.cols().toDouble()
        val smallHeight = smallMat.rows().toDouble()

        // Blur the downsized frame to reduce high-frequency noise
        Imgproc.GaussianBlur(smallMat, blurred, Size(11.0, 11.0), 0.0)

        // Convert RGBA to HSV for color filtering
        Imgproc.cvtColor(blurred, hsvMat, Imgproc.COLOR_RGB2HSV)

        // Dynamic HSV range with Hue wrap-around (0..180) for colors like red
        if (hasSelectedColor) {
            val hRange = 8.0
            val sRange = 60.0
            val vRange = 125.0

            val hMin = selectedH - hRange
            val hMax = selectedH + hRange

            val sMin = if (selectedS < 50.0) 0.0 else (selectedS - sRange).coerceAtLeast(30.0)
            val sMax = if (selectedS < 50.0) 60.0 else 255.0

            val vMin = (selectedV - vRange).coerceAtLeast(30.0)
            val vMax = 255.0

            if (hMin < 0.0) {
                val mask1 = Mat()
                val mask2 = Mat()
                Core.inRange(hsvMat, Scalar(0.0, sMin, vMin), Scalar(hMax, sMax, vMax), mask1)
                Core.inRange(hsvMat, Scalar(180.0 + hMin, sMin, vMin), Scalar(180.0, sMax, vMax), mask2)
                Core.bitwise_or(mask1, mask2, maskMat)
                mask1.release()
                mask2.release()
            } else if (hMax > 180.0) {
                val mask1 = Mat()
                val mask2 = Mat()
                Core.inRange(hsvMat, Scalar(hMin, sMin, vMin), Scalar(180.0, sMax, vMax), mask1)
                Core.inRange(hsvMat, Scalar(0.0, sMin, vMin), Scalar(hMax - 180.0, sMax, vMax), mask2)
                Core.bitwise_or(mask1, mask2, maskMat)
                mask1.release()
                mask2.release()
            } else {
                Core.inRange(hsvMat, Scalar(hMin, sMin, vMin), Scalar(hMax, sMax, vMax), maskMat)
            }
        } else {
            // Default fallback before any color is tapped (white ball range)
            Core.inRange(hsvMat, Scalar(0.0, 0.0, 240.0), Scalar(180.0, 20.0, 255.0), maskMat)
        }

        // Noise reduction (morphology) - increased dilation to fill holes and solidify ball edges
        Imgproc.erode(maskMat, maskMat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
        Imgproc.dilate(maskMat, maskMat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0)))

        // Find contours
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(maskMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        var bestScore = -1.0
        var bestCircleCenter: Point? = null
        var bestRadius = 0f
        var bestArea = 0.0
        var bestCircularity = 0.0
        var bestFillRatio = 0.0

        for (contour in contours) {
            val area = Geometry.contourArea(contour)
            // Scaled down area range (corresponds to 200 to 40000 on full frame)
            if (area in 200.0.. backupAreaThreshold * 1.2) {
                val contour2f = MatOfPoint2f()
                contour.convertTo(contour2f, CvType.CV_32FC2)

                val center = Point()
                val radius = FloatArray(1)
                Geometry.minEnclosingCircle(contour2f, center, radius)

                val perimeter = Geometry.arcLength(contour2f, true)
                val circularity = if (perimeter > 0) (4.0 * Math.PI * area) / (perimeter * perimeter) else 0.0

                val circleArea = Math.PI * radius[0] * radius[0]
                val fillRatio = if (circleArea > 0) area / circleArea else 0.0

                if (circularity > 0.35) {
                    // Prioritize larger size (radius) as primary factor, with circularity as lesser factor
                    val score = (radius[0] * 5.0) + (circularity * 20.0)

                    if (score > bestScore) {
                        bestScore = score
                        bestCircleCenter = center
                        bestRadius = radius[0]
                        bestArea = area * 4.0 // Scale area back up to full frame for display
                        bestCircularity = circularity
                        bestFillRatio = fillRatio
                    }
                }
                contour2f.release()
            }
        }

        hierarchy.release()
        for (c in contours) {
            c.release()
        }

        // Sample HSV color values at the detected ball center
        var sampledH = 0.0
        var sampledS = 0.0
        var sampledV = 0.0

        if (bestCircleCenter != null && bestRadius > 0f) {
            val sx = bestCircleCenter.x.toInt().coerceIn(0, hsvMat.cols() - 1)
            val sy = bestCircleCenter.y.toInt().coerceIn(0, hsvMat.rows() - 1)
            val hsvPixel = hsvMat.get(sy, sx)
            if (hsvPixel != null && hsvPixel.size >= 3) {
                sampledH = hsvPixel[0]
                sampledS = hsvPixel[1]
                sampledV = hsvPixel[2]
            }
        }

        // Sample HSV color values at the center reticle crosshair
        var reticleH = 0.0
        var reticleS = 0.0
        var reticleV = 0.0

        val rx = (smallWidth / 2.0).toInt().coerceIn(0, hsvMat.cols() - 1)
        val ry = (smallHeight / 2.0).toInt().coerceIn(0, hsvMat.rows() - 1)
        val reticlePixel = hsvMat.get(ry, rx)
        if (reticlePixel != null && reticlePixel.size >= 3) {
            reticleH = reticlePixel[0]
            reticleS = reticlePixel[1]
            reticleV = reticlePixel[2]
        }

        // Scale center and radius back up to full rgbaMat resolution for drawing and motor control
        val fullCircleCenter = if (bestCircleCenter != null) Point(bestCircleCenter.x / scale, bestCircleCenter.y / scale) else null
        val fullRadius = bestRadius / scale.toFloat()

        val frameWidth = rgbaMat.cols().toDouble()
        val frameHeight = rgbaMat.rows().toDouble()
        val centerX = frameWidth / 2.0
        val centerY = frameHeight / 2.0

        // Draw targeting crosshair at the center of the frame
        Imgproc.line(rgbaMat, Point(centerX - 30.0, centerY), Point(centerX + 30.0, centerY), Scalar(255.0, 255.0, 255.0), 2)
        Imgproc.line(rgbaMat, Point(centerX, centerY - 30.0), Point(centerX, centerY + 30.0), Scalar(255.0, 255.0, 255.0), 2)
        Imgproc.circle(rgbaMat, Point(centerX, centerY), 15, Scalar(255.0, 255.0, 255.0), 1)

        val isValidMatch = hasSelectedColor && fullCircleCenter != null && fullRadius > 0f && bestFillRatio > 0.4

        val currentTime = System.currentTimeMillis()
        val shouldSendCommand = currentTime - lastSendTime >= 100L

        if (isValidMatch) {
            lastDetectedTime = currentTime // Reset search timer upon successful detection

            val center = fullCircleCenter
            // Draw circle around detected ball on full-res rgbaMat
            Imgproc.circle(rgbaMat, center, fullRadius.toInt(), Scalar(0.0, 255.0, 0.0), 3)
            Imgproc.circle(rgbaMat, center, 5, Scalar(0.0, 0.0, 255.0), -1)

            // Draw line connecting center crosshair to detected ball center
            Imgproc.line(rgbaMat, Point(centerX, centerY), center, Scalar(255.0, 255.0, 0.0), 2)

            val ballX = center.x
            val errorX = ballX - centerX
            val normalizedError = errorX / (frameWidth / 2.0)

            // Distance control based on OpenCV area with smooth proportional slowdown near stopAreaThreshold:
            // - If area >= backupAreaThreshold: Back up (negative speed)
            // - If area >= stopAreaThreshold: Stop in place (0 speed)
            // - If area is close to stopAreaThreshold (within 50%): Smoothly scale down speed from 255 to min friction speed 100
            // - Else (area well below stopAreaThreshold): Full speed (255)
            val baseForward = when {
                bestArea >= backupAreaThreshold -> -120
                bestArea >= stopAreaThreshold -> 0
                else -> {
                    if (bestArea > stopAreaThreshold * 0.75) {
                        100
                    } else if (bestArea > stopAreaThreshold * 0.5) {
                        150
                    }
                    else {
                        255
                    }
                }
            }

            val rawTurn = (normalizedError * 255.0).roundToInt()
            val turnPower = when {
                rawTurn > 15 -> rawTurn.coerceIn(100, 255)
                rawTurn < -15 -> rawTurn.coerceIn(-255, -100)
                else -> 0
            }

            val leftMotor = (baseForward - turnPower).coerceIn(-255, 255)
            val rightMotor = (baseForward + turnPower).coerceIn(-255, 255)

            val command = "$leftMotor,$rightMotor,0"
            if (shouldSendCommand) {
                lastSendTime = currentTime
                BluetoothService.sendCommand(command)
            }

            activity?.runOnUiThread {
                tvTrackingStatus.text = "MATCH! Area:${bestArea.toInt()} | Circ:${String.format(Locale.US, "%.2f", bestCircularity)}"
            }
        } else {
            val timeSinceLastDetection = currentTime - lastDetectedTime
            // If lost for >= 1s and < 6s (searching for 5s), spin in place to search. Otherwise stop.
            val command = when {
                hasSelectedColor && timeSinceLastDetection in 1000L..6000L -> "255,-255,0" // Rotate in place (search)
                else -> "0,0,0" // Stop
            }

            if (shouldSendCommand) {
                lastSendTime = currentTime
                BluetoothService.sendCommand(command)
            }

            activity?.runOnUiThread {
                tvTrackingStatus.text = when {
                    !hasSelectedColor -> "Tap any ball on screen to select color..."
                    timeSinceLastDetection in 1000L..6000L -> "Searching... (Rotating in circle)"
                    timeSinceLastDetection > 6000L -> "Search timeout - Stopped"
                    else -> "Lost ball briefly..."
                }
            }
        }

        // Draw debug overlay text directly on the camera frame
        val debugText1 = "Det:(${sampledH.toInt()},${sampledS.toInt()},${sampledV.toInt()}) Area:${bestArea.toInt()}"
        val debugText2 = "Reticle:(${reticleH.toInt()},${reticleS.toInt()},${reticleV.toInt()})"
        val debugText3 = "Circ:${"%.2f".format(bestCircularity)} Fill:${"%.2f".format(bestFillRatio)}"
        Imgproc.putText(rgbaMat, debugText1, Point(30.0, 140.0), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, Scalar(255.0, 255.0, 0.0), 2)
        Imgproc.putText(rgbaMat, debugText2, Point(30.0, 180.0), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, Scalar(0.0, 255.0, 255.0), 2)
        Imgproc.putText(rgbaMat, debugText3, Point(30.0, 220.0), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, Scalar(255.0, 255.0, 0.0), 2)

        // --- Picture-in-Picture Filtered Mask Preview ---
        val thumbWidth = 200
        val thumbHeight = (frameHeight / frameWidth * thumbWidth).toInt()
        val smallMaskThumb = Mat()
        Imgproc.resize(maskMat, smallMaskThumb, Size(thumbWidth.toDouble(), thumbHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_NEAREST)

        val thumbRgba = Mat()
        Imgproc.cvtColor(smallMaskThumb, thumbRgba, Imgproc.COLOR_GRAY2RGBA)

        val xOffset = (frameWidth - thumbWidth - 20).toInt().coerceAtLeast(0)
        val yOffset = 20
        if (yOffset + thumbHeight <= rgbaMat.rows() && xOffset + thumbWidth <= rgbaMat.cols()) {
            val roi = rgbaMat.submat(yOffset, yOffset + thumbHeight, xOffset, xOffset + thumbWidth)
            thumbRgba.copyTo(roi)
            roi.release()
        }
        smallMaskThumb.release()
        thumbRgba.release()

        return rgbaMat
    }
}
