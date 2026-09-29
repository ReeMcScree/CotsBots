package com.cotsbots.navigation;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * BallFollower — detects a bright orange/red ball in a camera frame and works out
 * how the robot should move to keep the ball centered and at a comfortable distance.
 *
 * This runs entirely on-device with OpenCV. No network, no Gemini. It is meant to be
 * called on every analyzed camera frame for smooth, continuous tracking.
 *
 * Output is a motor command in the firmware's protocol: "left,right,duration".
 * We use duration 0 (continuous) so motors keep running between frames; the next
 * frame sends a fresh command. When tracking stops we send "0,0,0" to halt.
 */
public class BallFollower {

    /** Result of analyzing one frame. */
    public static class Result {
        public boolean ballFound;
        public String command;      // "left,right,duration" for the firmware
        public double ballX;        // ball center x in the frame (pixels)
        public double ballY;        // ball center y in the frame (pixels)
        public double ballRadius;   // approximate ball radius (pixels)
        public String status;       // human-readable, for the on-screen label
    }

    // ── Tuning knobs ─────────────────────────────────────────────────────────

    // Base motor speed (0-255). Turns scale from this.
    private int baseSpeed = 160;

    // How much of the frame width counts as "centered" (dead zone).
    // 0.20 means the middle 20% of the frame = go straight, no turn.
    private double centerDeadZone = 0.20;

    // Ball radius (as a fraction of frame width) that means "close enough — stop".
    // Bigger ball = closer. 0.25 = stop when ball spans ~25% of the width.
    private double stopRadiusFraction = 0.25;

    // Minimum blob area (pixels) to count as a real ball, filters out noise.
    private double minBallArea = 300;

    // HSV color range for a bright orange/red ball.
    // Red wraps around the hue circle, so we check two ranges and combine them.
    private final Scalar lowerRed1 = new Scalar(0, 120, 100);
    private final Scalar upperRed1 = new Scalar(12, 255, 255);
    private final Scalar lowerRed2 = new Scalar(168, 120, 100);
    private final Scalar upperRed2 = new Scalar(180, 255, 255);

    // Reusable Mats (avoid allocating every frame)
    // Created lazily on first analyze() call, AFTER OpenCV's native lib is loaded.
    // Creating a Mat before OpenCVLoader runs throws UnsatisfiedLinkError.
    private Mat hsv, mask1, mask2, mask, hierarchy;

    // ── Setters so the UI or constants can tune behavior ─────────────────────

    public void setBaseSpeed(int s) { baseSpeed = clamp(s, 0, 255); }
    public void setCenterDeadZone(double d) { centerDeadZone = d; }
    public void setStopRadiusFraction(double f) { stopRadiusFraction = f; }

    /**
     * Analyze one BGR frame and decide how to move.
     *
     * @param frameBgr an OpenCV Mat in BGR color (as produced by Utils.bitmapToMat
     *                 after the usual conversion). This method does not modify it.
     * @return a Result with the movement command and detection info.
     */
    public Result analyze(Mat frameBgr) {
        Result r = new Result();

        // Lazily create reusable Mats now that OpenCV is loaded.
        if (hsv == null) {
            hsv = new Mat();
            mask1 = new Mat();
            mask2 = new Mat();
            mask = new Mat();
            hierarchy = new Mat();
        }

        int width = frameBgr.cols();
        int height = frameBgr.rows();

        // 1. Convert to HSV — much more robust for color detection than RGB/BGR.
        Imgproc.cvtColor(frameBgr, hsv, Imgproc.COLOR_BGR2HSV);

        // 2. Threshold for red/orange in both hue ranges, then OR them together.
        Core.inRange(hsv, lowerRed1, upperRed1, mask1);
        Core.inRange(hsv, lowerRed2, upperRed2, mask2);
        Core.add(mask1, mask2, mask);

        // 3. Clean up the mask — blur + morphology removes speckle noise.
        Imgproc.medianBlur(mask, mask, 5);
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new org.opencv.core.Size(7, 7));
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);

        // 4. Find contours (connected colored regions).
        List<MatOfPoint> contours = new ArrayList<>();
        hierarchy.release();
        Imgproc.findContours(mask, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // 5. Pick the largest contour above the noise threshold — that's our ball.
        double bestArea = 0;
        MatOfPoint bestContour = null;
        for (MatOfPoint c : contours) {
            double area = polygonArea(c.toArray());
            if (area > bestArea) {
                bestArea = area;
                bestContour = c;
            }
        }

        if (bestContour == null || bestArea < minBallArea) {
            // No ball found → stop and report.
            r.ballFound = false;
            r.command = "0,0,0";
            r.status = "No ball — stopped";
            return r;
        }

        // 6. Get the ball's center and size by scanning the contour points directly.
        // (Avoids Imgproc geometry helpers whose signatures differ across OpenCV versions.)
        org.opencv.core.Point[] pts = bestContour.toArray();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (org.opencv.core.Point p : pts) {
            if (p.x < minX) minX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.x > maxX) maxX = p.x;
            if (p.y > maxY) maxY = p.y;
        }
        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;
        double radius = Math.sqrt(bestArea / Math.PI); // area of a circle -> radius

        r.ballFound = true;
        r.ballX = cx;
        r.ballY = cy;
        r.ballRadius = radius;

        // 7. Decide the move.

        // (a) Close enough? Stop.
        if (radius > stopRadiusFraction * width) {
            r.command = "0,0,0";
            r.status = "Ball close — stopped";
            return r;
        }

        // (b) Where is the ball horizontally, relative to center?
        double frameCenter = width / 2.0;
        double offset = cx - frameCenter;                 // negative = left, positive = right
        double normalizedOffset = offset / frameCenter;   // -1 .. +1

        // (c) Inside the dead zone → drive straight forward.
        if (Math.abs(normalizedOffset) < centerDeadZone) {
            r.command = baseSpeed + "," + baseSpeed + ",0";
            r.status = "Ball centered — forward";
            return r;
        }

        // (d) Otherwise steer proportionally: the further off-center, the sharper the turn.
        // Slow the inside wheel proportional to how far the ball is off-center.
        double turnStrength = Math.min(1.0, Math.abs(normalizedOffset)); // 0..1
        int fast = baseSpeed;
        int slow = (int) (baseSpeed * (1.0 - turnStrength));             // inside wheel slows
        slow = clamp(slow, -baseSpeed, baseSpeed);

        int left, right;
        if (normalizedOffset < 0) {
            // Ball is to the LEFT → slow the LEFT wheel so robot curves left.
            left = slow;
            right = fast;
            r.status = "Ball left — turning left";
        } else {
            // Ball is to the RIGHT → slow the RIGHT wheel.
            left = fast;
            right = slow;
            r.status = "Ball right — turning right";
        }

        r.command = left + "," + right + ",0";
        return r;
    }

    /** Bounding box of the detected ball, for drawing an overlay (optional). */
    public Rect ballBox(Result r) {
        if (!r.ballFound) return null;
        int rad = (int) r.ballRadius;
        return new Rect(
                (int) (r.ballX - rad), (int) (r.ballY - rad),
                rad * 2, rad * 2);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // Computes polygon area from its points (shoelace formula). Pure Java —
    // avoids Imgproc.contourArea(), which changed signature in OpenCV 5.0.
    private static double polygonArea(org.opencv.core.Point[] pts) {
        double area = 0;
        int n = pts.length;
        for (int i = 0; i < n; i++) {
            org.opencv.core.Point a = pts[i];
            org.opencv.core.Point b = pts[(i + 1) % n];
            area += (a.x * b.y) - (b.x * a.y);
        }
        return Math.abs(area) / 2.0;
    }
}