package com.example.robotcommunication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
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
 * Fragment that uses OpenCV to detect a white ball from the camera stream
 * and sends movement commands to the robot via Bluetooth.
 */
class FollowBallFragment : Fragment(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var cameraView: CameraBridgeViewBase
    private lateinit var tvTrackingStatus: TextView

    private lateinit var rgbaMat: Mat
    private lateinit var hsvMat: Mat
    private lateinit var maskMat: Mat

    private var lastSendTime = 0L

    private val requestCameraLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                tvTrackingStatus.text = "Camera permission granted. Starting camera..."
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

        if (OpenCVLoader.initDebug()) {
            tvTrackingStatus.text = "OpenCV loaded. Checking camera permission..."
        } else {
            tvTrackingStatus.text = "Error initializing OpenCV."
        }
    }

    override fun onResume() {
        super.onResume()
        if (OpenCVLoader.initDebug()) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                tvTrackingStatus.text = "Starting camera preview..."
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
        hsvMat = Mat()
        maskMat = Mat()
    }

    override fun onCameraViewStopped() {
        rgbaMat.release()
        hsvMat.release()
        maskMat.release()
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        rgbaMat = inputFrame.rgba()

        // Convert RGBA to HSV for color filtering
        Imgproc.cvtColor(rgbaMat, hsvMat, Imgproc.COLOR_RGB2HSV)

        // Define HSV color range for WHITE object detection (low saturation, high brightness)
        val lowerBound = Scalar(0.0, 0.0, 240.0)
        val upperBound = Scalar(180.0, 20.0, 255.0)

        Core.inRange(hsvMat, lowerBound, upperBound, maskMat)

        // Noise reduction (morphology)
        Imgproc.erode(maskMat, maskMat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
        Imgproc.dilate(maskMat, maskMat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0)))

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

        val frameWidth = rgbaMat.cols().toDouble()
        val frameHeight = rgbaMat.rows().toDouble()
        val centerX = frameWidth / 2.0
        val centerY = frameHeight / 2.0

        for (contour in contours) {
            val area = Geometry.contourArea(contour)
            if (area in 100.0..10000.0) {
                val contour2f = MatOfPoint2f()
                contour.convertTo(contour2f, CvType.CV_32FC2)

                val center = Point()
                val radius = FloatArray(1)
                Geometry.minEnclosingCircle(contour2f, center, radius)

                // Ignore objects in the upper 30% of the screen (ceiling lights, wall reflections)
                if (center.y < frameHeight * 0.3) {
                    contour2f.release()
                    continue
                }

                val perimeter = Geometry.arcLength(contour2f, true)
                val circularity = if (perimeter > 0) (4.0 * Math.PI * area) / (perimeter * perimeter) else 0.0

                val circleArea = Math.PI * radius[0] * radius[0]
                val fillRatio = if (circleArea > 0) area / circleArea else 0.0

                val rect = Geometry.boundingRect(contour)
                val aspectRatio = if (rect.height > 0) rect.width.toDouble() / rect.height.toDouble() else 0.0

                if (circularity > 0.35 && aspectRatio in 0.6..1.4) {
                    val distanceFromCenterX = Math.abs(center.x - centerX)
                    // Prioritize circularity heavily (weight = 100), then size, penalizing distance from center X
                    val score = (circularity * 100.0) + (radius[0] * 0.5) - (distanceFromCenterX * 0.3)

                    if (score > bestScore) {
                        bestScore = score
                        bestCircleCenter = center
                        bestRadius = radius[0]
                        bestArea = area
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

        // Sample HSV color values at the detected ball center (or center of frame if no ball)
        var sampledH = 0.0
        var sampledS = 0.0
        var sampledV = 0.0

        val samplePoint = if (bestCircleCenter != null && bestRadius > 0f) {
            bestCircleCenter
        } else {
            Point(centerX, centerY)
        }
        val sx = samplePoint.x.toInt().coerceIn(0, rgbaMat.cols() - 1)
        val sy = samplePoint.y.toInt().coerceIn(0, rgbaMat.rows() - 1)
        val hsvPixel = hsvMat.get(sy, sx)
        if (hsvPixel != null && hsvPixel.size >= 3) {
            sampledH = hsvPixel[0]
            sampledS = hsvPixel[1]
            sampledV = hsvPixel[2]
        }

        // Draw targeting crosshair at the center of the frame
        Imgproc.line(rgbaMat, Point(centerX - 30.0, centerY), Point(centerX + 30.0, centerY), Scalar(255.0, 255.0, 255.0), 2)
        Imgproc.line(rgbaMat, Point(centerX, centerY - 30.0), Point(centerX, centerY + 30.0), Scalar(255.0, 255.0, 255.0), 2)
        Imgproc.circle(rgbaMat, Point(centerX, centerY), 15, Scalar(255.0, 255.0, 255.0), 1)

        val isValidMatch = bestCircleCenter != null && bestRadius > 0f && bestCircularity > 0.45 && bestFillRatio > 0.45

        val currentTime = System.currentTimeMillis()
        val shouldSendCommand = currentTime - lastSendTime >= 100L

        if (isValidMatch) {
            val center = bestCircleCenter
            // Draw circle around detected ball
            Imgproc.circle(rgbaMat, center, bestRadius.toInt(), Scalar(0.0, 255.0, 0.0), 3)
            Imgproc.circle(rgbaMat, center, 5, Scalar(0.0, 0.0, 255.0), -1)

            // Draw line connecting center crosshair to detected ball center
            Imgproc.line(rgbaMat, Point(centerX, centerY), center, Scalar(255.0, 255.0, 0.0), 2)

            val ballX = center.x
            val errorX = ballX - centerX
            val normalizedError = errorX / (frameWidth / 2.0)

            // Drive forward if ball is far (radius < 50), stop forward motion when close (radius >= 50)
            val baseForward = if (bestRadius < 100.0) 180 else 0

            // Steering: ensure minimum turn power of 100 so motors overcome friction and don't stall
            val rawTurn = (normalizedError * 180.0).roundToInt()
            val turnPower = when {
                rawTurn > 15 -> rawTurn.coerceIn(100, 255)
                rawTurn < -15 -> rawTurn.coerceIn(-255, -100)
                else -> 0
            }

            val leftMotor = (baseForward - turnPower).coerceIn(-255, 255)
            val rightMotor = (baseForward + turnPower).coerceIn(-255, 255)

            // Format: L,R,D (Duration = 0 for continuous)
            val command = "$leftMotor,$rightMotor,0"
            if (shouldSendCommand) {
                lastSendTime = currentTime
                BluetoothService.sendCommand(command)
            }

            activity?.runOnUiThread {
                tvTrackingStatus.text = "MATCH! HSV(${sampledH.toInt()},${sampledS.toInt()},${sampledV.toInt()}) Area:${bestArea.toInt()} Circ:${String.format(Locale.US, "%.2f", bestCircularity)} | Fill:${String.format(Locale.US, "%.2f", bestFillRatio)}"
            }
        } else {
            if (shouldSendCommand) {
                lastSendTime = currentTime
                // Format: L,R,D (Stop command 0,0,0 with duration 0)
                BluetoothService.sendCommand("0,0,0")
            }
            activity?.runOnUiThread {
                tvTrackingStatus.text = "Searching... HSV(${sampledH.toInt()},${sampledS.toInt()},${sampledV.toInt()}) Area:${bestArea.toInt()} Circ:${String.format(Locale.US, "%.2f", bestCircularity)}"
            }
        }

        // Draw debug overlay text directly on the camera frame
        val debugText = "HSV(${sampledH.toInt()},${sampledS.toInt()},${sampledV.toInt()}) Area:${bestArea.toInt()} Circ:${String.format(Locale.US, "%.2f", bestCircularity)}"
        Imgproc.putText(rgbaMat, debugText, Point(30.0, 50.0), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, Scalar(255.0, 255.0, 0.0), 2)

        return rgbaMat
    }
}
